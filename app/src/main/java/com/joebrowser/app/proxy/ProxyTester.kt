package com.joebrowser.app.proxy

import com.joebrowser.app.data.model.ProxyConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/**
 * The result of reading a connection.
 */
data class ProxyCheck(
    val ok: Boolean,
    val ip: String = "",
    val city: String = "",
    val country: String = "",
    val countryCode: String = "",
    val timezone: String = "",
    val latencyMs: Int = 0,
    val error: String = "",
    val source: String = "",
)

/**
 * Reads the public address of a connection.
 *
 * The one rule that matters here: a test never falls back to the device's own connection.
 * A test that quietly succeeds over the device's internet reports the device's address and
 * calls a dead proxy "working", which is worse than no test at all.
 */
object ProxyTester {

    private const val UA = "JoeBrowser/1.0"

    /** The services a check asks, in order. The first that answers wins. */
    private val ENDPOINTS = listOf(
        "https://ipinfo.io/json" to "ipinfo.io",
        "https://ipapi.co/json/" to "ipapi.co",
    )

    /**
     * Checks a proxy.
     *
     * @param cfg the proxy to check.
     * @param fallbackDirect must be false for a check. It exists so a live session can be
     *        told to keep working when the proxy dies, which is a different question.
     */
    suspend fun test(cfg: ProxyConfig?, fallbackDirect: Boolean = false): ProxyCheck =
        withContext(Dispatchers.IO) {
            if (cfg == null || !cfg.enabled) {
                return@withContext ProxyCheck(ok = false, error = "Add a proxy first")
            }
            val strict = cfg.copy(fallbackDirect = false)
            val relay = ProxyRelay({ strict })
            try {
                relay.start()
                fetch(strict, relay.port, 20_000)
            } finally {
                relay.close()
            }
        }

    /** Checks the device's own connection. */
    suspend fun testSystem(): ProxyCheck = withContext(Dispatchers.IO) {
        fetch(null, 0, 9_000)
    }

    /**
     * Asks the services for the address.
     *
     * @param proxyPort the local relay port, or zero for a direct connection.
     */
    private fun fetch(cfg: ProxyConfig?, proxyPort: Int, timeoutMs: Int): ProxyCheck {
        val started = System.currentTimeMillis()
        var lastError = "No internet connection"

        for ((url, source) in ENDPOINTS) {
            val connection = try {
                val target = URL(url)
                val proxy = if (proxyPort > 0) {
                    Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxyPort))
                } else {
                    // A direct connection that ignores any system proxy.
                    Proxy.NO_PROXY
                }
                val conn = target.openConnection(proxy) as HttpURLConnection
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", UA)
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("Cache-Control", "no-cache")
                conn.instanceFollowRedirects = true
                conn
            } catch (e: Exception) {
                lastError = friendly(e)
                null
            }

            if (connection == null) continue
            try {
                val code = connection.responseCode
                if (code != 200) {
                    lastError = "Service returned HTTP $code"
                    continue
                }
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val ip = json.optString("ip")
                if (ip.isNullOrBlank()) {
                    lastError = "Could not read IP"
                    continue
                }
                var countryCode = json.optString("country_code")
                if (countryCode.isNullOrBlank()) countryCode = json.optString("country")
                var country = json.optString("country_name")
                if (country.isNullOrBlank()) country = json.optString("country")
                var timezone = json.optString("timezone")
                if (timezone.isNullOrBlank()) timezone = json.optString("time_zone")
                return ProxyCheck(
                    ok = true,
                    ip = ip,
                    city = json.optString("city"),
                    country = country,
                    countryCode = countryCode,
                    timezone = timezone,
                    latencyMs = (System.currentTimeMillis() - started).toInt(),
                    source = source,
                )
            } catch (e: Exception) {
                lastError = friendly(e)
            } finally {
                try { connection.disconnect() } catch (e: Exception) {}
            }
        }
        return ProxyCheck(ok = false, error = lastError, latencyMs = (System.currentTimeMillis() - started).toInt())
    }

    /** A message a person can act on. */
    fun friendly(e: Exception): String {
        val text = e.message ?: ""
        return when {
            e is java.net.SocketTimeoutException -> "Connection timed out"
            e is java.net.UnknownHostException -> "Proxy host not found"
            e is java.net.ConnectException -> "Could not connect to proxy"
            text.contains("407") -> "Proxy authentication failed"
            text.contains("502") -> "Proxy is unreachable - it did not answer the connection"
            text.isNotBlank() -> text
            else -> "Proxy test failed"
        }
    }

    /**
     * Waits for the public address, giving up after the timeout.
     *
     * Used by the live watcher, which must not pile up when a proxy is dead.
     */
    suspend fun testWithTimeout(cfg: ProxyConfig?, timeoutMs: Long = 25_000): ProxyCheck =
        withTimeoutOrNull(timeoutMs) { test(cfg) }
            ?: ProxyCheck(ok = false, error = "Connection timed out")
}
