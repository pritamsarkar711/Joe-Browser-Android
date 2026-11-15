package com.joebrowser.app.proxy

import com.joebrowser.app.data.model.ProxyConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger

/**
 * A local HTTP proxy that bridges to the real upstream.
 *
 * The browser is pointed at this relay, never at the upstream directly. The reason is
 * credentials: a Chromium proxy configuration carries no user name and no password, so a
 * proxy that needs them cannot be used from the browser alone. The relay keeps the
 * credentials, performs the upstream handshake itself and presents the browser with a
 * plain local endpoint that needs none.
 *
 * It also speaks SOCKS5 upstream, which Chromium's own proxy configuration cannot do with
 * credentials either.
 *
 * The relay re-reads the upstream on every connection, so changing a profile's proxy takes
 * effect without restarting the browser. Every connection carries the epoch it was opened
 * with and is torn down when the upstream changes, because a socket opened through the old
 * proxy would otherwise keep talking through it long after the user asked for a different
 * exit.
 */
class ProxyRelay(
    private val provider: () -> ProxyConfig?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : AutoCloseable {

    private var server: ServerSocket? = null
    private var acceptJob: Job? = null
    private val epoch = AtomicInteger(0)
    @Volatile private var epochKey = ""
    @Volatile private var fellBack = false
    @Volatile private var offline = false

    /** Called the first time an outage makes the relay fall back to a direct connection. */
    @Volatile var onFallback: ((String) -> Unit)? = null

    /** Called the first time both the proxy and the direct route fail. */
    @Volatile var onOffline: ((String) -> Unit)? = null

    val port: Int get() = server?.localPort ?: -1

    val isRunning: Boolean get() = server?.isClosed == false

    fun start() {
        if (server != null) return
        val s = ServerSocket(0, 64, java.net.InetAddress.getByName("127.0.0.1"))
        s.reuseAddress = true
        server = s
        acceptJob = scope.launch {
            while (isActive) {
                val client = try {
                    s.accept()
                } catch (e: IOException) {
                    if (isActive) delay(50)
                    continue
                }
                launch { handle(client) }
            }
        }
    }

    override fun close() {
        try { server?.close() } catch (e: IOException) {}
        acceptJob?.cancel()
        scope.cancel()
    }

    // -----------------------------------------------------------------------------------------
    // Upstream
    // -----------------------------------------------------------------------------------------

    /** The upstream, and the epoch that goes with it. */
    private fun upstream(): Pair<ProxyConfig?, Int> {
        val cfg = provider()
        val key = if (cfg == null || !cfg.enabled) {
            "direct"
        } else {
            "${cfg.type}|${cfg.host}|${cfg.port}|${cfg.user}|${cfg.password}|${cfg.fallbackDirect}"
        }
        if (key != epochKey) {
            epochKey = key
            epoch.incrementAndGet()
        }
        return cfg to epoch.get()
    }

    // -----------------------------------------------------------------------------------------
    // One client
    // -----------------------------------------------------------------------------------------

    private suspend fun handle(client: Socket) {
        try {
            client.tcpNoDelay = true
            client.soTimeout = 60_000
            val input = client.getInputStream()
            val output = client.getOutputStream()

            val head = readHead(input) ?: return
            val lines = head.split("\r\n")
            val first = lines.firstOrNull() ?: return
            val parts = first.split(' ')
            if (parts.size < 2) return

            val (cfg, myEpoch) = upstream()
            val target: Pair<String, Int>
            val isConnect = parts[0].equals("CONNECT", ignoreCase = true)
            if (isConnect) {
                target = parseHostPort(parts[1]) ?: return
            } else {
                target = parseAbsolute(parts[1]) ?: return
            }

            val upstreamSocket = connectUpstream(cfg, target.first, target.second)
                ?: run {
                    // The upstream could not be opened. The browser gets a plain 502 and the
                    // connection ends, which is what a dead proxy looks like from outside.
                    try {
                        output.write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\n\r\n".toByteArray())
                        output.flush()
                    } catch (e: IOException) {}
                    return
                }

            try {
                if (isConnect) {
                    output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                    output.flush()
                } else {
                    val request = rewriteForOrigin(lines, cfg)
                    output.write(request.toByteArray(Charsets.UTF_8))
                    output.flush()
                }
                pump(client, upstreamSocket, myEpoch)
            } finally {
                try { upstreamSocket.close() } catch (e: IOException) {}
            }
        } catch (e: Exception) {
            // A client that goes away mid-request is normal; nothing to report.
        } finally {
            try { client.close() } catch (e: IOException) {}
        }
    }

    /** Reads the request head, capped so a hostile client cannot exhaust memory. */
    private suspend fun readHead(input: InputStream): String? = withContext(Dispatchers.IO) {
        val buffer = ByteArray(4096)
        val out = java.io.ByteArrayOutputStream()
        var matched = 0
        val terminator = byteArrayOf(0x0D, 0x0A, 0x0D, 0x0A)
        while (out.size() < 131_072) {
            val n = try { input.read(buffer) } catch (e: IOException) { return@withContext null }
            if (n <= 0) return@withContext null
            for (i in 0 until n) {
                val b = buffer[i]
                out.write(b.toInt())
                if (b == terminator[matched]) {
                    matched++
                    if (matched == terminator.size) {
                        return@withContext out.toString(Charsets.UTF_8.name())
                    }
                } else {
                    matched = if (b == terminator[0]) 1 else 0
                }
            }
        }
        null
    }

    private fun parseHostPort(text: String): Pair<String, Int>? {
        val idx = text.lastIndexOf(':')
        if (idx <= 0) return null
        var host = text.substring(0, idx)
        if (host.startsWith("[")) host = host.removePrefix("[").removeSuffix("]")
        val port = text.substring(idx + 1).toIntOrNull() ?: return null
        if (port <= 0) return null
        return host to port
    }

    private fun parseAbsolute(text: String): Pair<String, Int>? = try {
        val uri = java.net.URI(text)
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else if (uri.scheme == "https") 443 else 80
        host to port
    } catch (e: Exception) {
        null
    }

    /**
     * Rewrites a plain HTTP request into origin form.
     *
     * The relay adds its own authorisation header and drops the client's, so a stale
     * credential in the browser cannot authenticate as the wrong account.
     */
    private fun rewriteForOrigin(lines: List<String>, cfg: ProxyConfig?): String {
        val sb = StringBuilder()
        val fl = lines.first()
        val sp1 = fl.indexOf(' ')
        val sp2 = fl.lastIndexOf(' ')
        if (sp1 > 0 && sp2 > sp1) {
            val path = try {
                val uri = java.net.URI(lines[1])
                if (uri.rawPath.isNullOrEmpty()) "/" else uri.rawPath + (uri.rawQuery?.let { "?$it" } ?: "")
            } catch (e: Exception) {
                "/"
            }
            sb.append(fl.substring(0, sp1)).append(' ').append(path).append(' ')
                .append(fl.substring(sp2 + 1)).append("\r\n")
        } else {
            sb.append(fl).append("\r\n")
        }

        val relayAuth = if (cfg != null && cfg.enabled && cfg.type != "socks5" && cfg.user.isNotEmpty()) {
            "Proxy-Authorization: Basic " +
                Base64.getEncoder().encodeToString("${cfg.user}:${cfg.password}".toByteArray()) + "\r\n"
        } else null

        for (i in 1 until lines.size) {
            val l = lines[i]
            if (l.startsWith("Proxy-Connection:", ignoreCase = true)) continue
            if (l.startsWith("Connection:", ignoreCase = true)) continue
            if (l.startsWith("Proxy-Authorization:", ignoreCase = true)) {
                // Keep the client's header only when the relay has no credentials of its own.
                if (relayAuth == null) sb.append(l).append("\r\n")
                continue
            }
            sb.append(l).append("\r\n")
        }
        if (relayAuth != null) sb.append(relayAuth)
        sb.append("Connection: close\r\n\r\n")
        return sb.toString()
    }

    // -----------------------------------------------------------------------------------------
    // Upstream connection
    // -----------------------------------------------------------------------------------------

    private suspend fun connectUpstream(cfg: ProxyConfig?, host: String, port: Int): Socket? {
        if (cfg == null || !cfg.enabled) {
            val direct = connectTcp(host, port)
            if (direct != null) return direct
            reportOffline(cfg)
            return null
        }

        val socket = if (cfg.type.equals("socks5", ignoreCase = true)) {
            socks5Connect(cfg, host, port)
        } else {
            httpConnect(cfg, host, port)
        }
        if (socket != null) {
            // A successful proxied connection re-arms the fallback notice, so the next
            // outage is allowed to speak again.
            fellBack = false
            return socket
        }

        if (cfg.fallbackDirect) {
            val direct = connectTcp(host, port)
            if (direct != null) {
                if (!fellBack) {
                    fellBack = true
                    onFallback?.invoke(ProxyParser.shortAddress(cfg))
                }
                return direct
            }
        }
        reportOffline(cfg)
        return null
    }

    private fun reportOffline(cfg: ProxyConfig?) {
        if (!offline) {
            offline = true
            val endpoint = cfg?.let { ProxyParser.shortAddress(it) } ?: "the network"
            onOffline?.invoke(endpoint)
        }
    }

    private suspend fun connectTcp(host: String, port: Int): Socket? = withContext(Dispatchers.IO) {
        try {
            val s = Socket()
            s.connect(InetSocketAddress(host, port), 20_000)
            s.soTimeout = 60_000
            s
        } catch (e: Exception) {
            null
        }
    }

    /** The HTTP CONNECT handshake, with the credentials the browser never sees. */
    private suspend fun httpConnect(cfg: ProxyConfig, host: String, port: Int): Socket? =
        withContext(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(cfg.host, cfg.port), 20_000)
                socket.soTimeout = 15_000
                val out = socket.getOutputStream()
                val inp = socket.getInputStream()

                val sb = StringBuilder()
                sb.append("CONNECT ").append(host).append(':').append(port).append(" HTTP/1.1\r\n")
                sb.append("Host: ").append(host).append(':').append(port).append("\r\n")
                sb.append("Proxy-Connection: Keep-Alive\r\n")
                if (cfg.user.isNotEmpty()) {
                    val token = Base64.getEncoder().encodeToString("${cfg.user}:${cfg.password}".toByteArray())
                    sb.append("Proxy-Authorization: Basic ").append(token).append("\r\n")
                }
                sb.append("\r\n")
                out.write(sb.toString().toByteArray(Charsets.UTF_8))
                out.flush()

                val status = readStatusLine(inp) ?: return@withContext null
                if (!status.startsWith(" 200") && !status.contains(" 200 ")) {
                    return@withContext null
                }
                socket.soTimeout = 60_000
                socket
            } catch (e: Exception) {
                try { socket?.close() } catch (e2: IOException) {}
                null
            }
        }

    private fun readStatusLine(input: InputStream): String? {
        val sb = StringBuilder()
        var b = input.read()
        if (b < 0) return null
        while (b >= 0 && b != '\n'.code) {
            if (b != '\r'.code) sb.append(b.toChar())
            b = input.read()
            if (sb.length > 512) return null
        }
        return sb.toString().trim()
    }

    /** The SOCKS5 handshake, including the username and password form. */
    private suspend fun socks5Connect(cfg: ProxyConfig, host: String, port: Int): Socket? =
        withContext(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(cfg.host, cfg.port), 20_000)
                socket.soTimeout = 15_000
                val out = socket.getOutputStream()
                val inp = socket.getInputStream()

                if (cfg.user.isNotEmpty()) {
                    out.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
                } else {
                    out.write(byteArrayOf(0x05, 0x01, 0x00))
                }
                out.flush()

                val sel = ByteArray(2)
                if (!readFully(inp, sel)) return@withContext null
                if (sel[0].toInt() != 5) return@withContext null
                if (sel[1].toInt() == 2) {
                    val user = cfg.user.toByteArray(Charsets.UTF_8)
                    val pass = cfg.password.toByteArray(Charsets.UTF_8)
                    val auth = ByteArray(3 + user.size + pass.size)
                    auth[0] = 0x01
                    auth[1] = user.size.toByte()
                    System.arraycopy(user, 0, auth, 2, user.size)
                    auth[2 + user.size] = pass.size.toByte()
                    System.arraycopy(pass, 0, auth, 3 + user.size, pass.size)
                    out.write(auth)
                    out.flush()
                    val ok = ByteArray(2)
                    if (!readFully(inp, ok)) return@withContext null
                    if (ok[1].toInt() != 0) return@withContext null
                } else if (sel[1].toInt() != 0) {
                    return@withContext null
                }

                // The host is always sent unresolved, so the name is resolved by the proxy.
                val nameBytes = host.toByteArray(Charsets.US_ASCII)
                val req = ByteArray(5 + nameBytes.size + 2)
                req[0] = 0x05; req[1] = 0x01; req[2] = 0x00; req[3] = 0x03
                req[4] = nameBytes.size.toByte()
                System.arraycopy(nameBytes, 0, req, 5, nameBytes.size)
                req[5 + nameBytes.size] = ((port shr 8) and 0xFF).toByte()
                req[6 + nameBytes.size] = (port and 0xFF).toByte()
                out.write(req)
                out.flush()

                val head = ByteArray(4)
                if (!readFully(inp, head)) return@withContext null
                if (head[0].toInt() != 5 || head[1].toInt() != 0) return@withContext null
                when (head[3].toInt()) {
                    1 -> { val b = ByteArray(4 + 2); readFully(inp, b) }
                    4 -> { val b = ByteArray(16 + 2); readFully(inp, b) }
                    3 -> {
                        val len = ByteArray(1)
                        if (!readFully(inp, len)) return@withContext null
                        val b = ByteArray(len[0].toInt() + 2)
                        readFully(inp, b)
                    }
                    else -> return@withContext null
                }
                socket.soTimeout = 60_000
                socket
            } catch (e: Exception) {
                try { socket?.close() } catch (e2: IOException) {}
                null
            }
        }

    private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
        var read = 0
        while (read < buffer.size) {
            val n = try { input.read(buffer, read, buffer.size - read) } catch (e: SocketTimeoutException) {
                return false
            } catch (e: IOException) {
                return false
            }
            if (n <= 0) return false
            read += n
        }
        return true
    }

    // -----------------------------------------------------------------------------------------
    // Pumping
    // -----------------------------------------------------------------------------------------

    /** Copies bytes in both directions until either side stops. */
    private suspend fun pump(client: Socket, upstream: Socket, myEpoch: Int) {
        val scope = CoroutineScope(Dispatchers.IO)
        val jobA = scope.launch {
            copy(client.getInputStream(), upstream.getOutputStream())
        }
        val jobB = scope.launch {
            copy(upstream.getInputStream(), client.getOutputStream())
        }
        try {
            jobA.join()
        } finally {
            jobB.cancel()
            try { upstream.close() } catch (e: IOException) {}
            try { client.close() } catch (e: IOException) {}
        }
    }

    private suspend fun copy(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(16_384)
        try {
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                output.write(buffer, 0, n)
                output.flush()
            }
        } catch (e: IOException) {
            // The other end went away.
        }
    }
}
