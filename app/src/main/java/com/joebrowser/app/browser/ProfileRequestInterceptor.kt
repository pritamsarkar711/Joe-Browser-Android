package com.joebrowser.app.browser

import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.joebrowser.app.data.model.Profile
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

/**
 * The network layer of a profile.
 *
 * A browser engine on a phone sends two headers that no desktop browser sends: the name
 * of the app it lives in, and the language of the device rather than the language of the
 * profile. Either one on its own gives the profile away, and the engine offers no way to
 * turn them off.
 *
 * So the requests are made here instead. The profile's own user agent and language go
 * out, the app name does not, and the cookies still come from the browser's own store so
 * that the session behaves exactly as the page expects.
 *
 * Only requests without a body are handled this way, because the body of a request is
 * not readable from the engine. Anything else is handed straight back to the engine,
 * which is slower but never wrong.
 */
class ProfileRequestInterceptor(
    private val profile: Profile,
    /** The local relay the requests go through, or zero when there is no proxy. */
    private val relayPort: () -> Int,
) {

    /** The headers that must never reach a server from a profile. */
    private val dropped = setOf(
        "x-requested-with",
        "host",
        "connection",
        "content-length",
        "accept-encoding",
        "sec-fetch-site",
        "sec-fetch-mode",
        "sec-fetch-dest",
        "sec-fetch-user",
    )

    /**
     * Makes the request, or returns null to let the engine make it.
     */
    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        val method = request.method ?: "GET"
        if (!method.equals("GET", true) && !method.equals("HEAD", true)) return null
        val url = request.url?.toString() ?: return null
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null

        return try {
            perform(url, method, request)
        } catch (e: Exception) {
            // Any doubt and the engine does the work. A page that loads is worth more
            // than a page that loads with the right headers.
            null
        }
    }

    private fun perform(url: String, method: String, request: WebResourceRequest): WebResourceResponse? {
        val target = URL(url)
        val connection = (target.openConnection(proxy()) as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("User-Agent", profile.fp.userAgentOverride.takeIf {
                !profile.fp.autoUserAgent && it.isNotBlank()
            } ?: WebEngine.resolvedUserAgent(profile))
            setRequestProperty("Accept-Language", profile.fp.acceptLanguage)
        }

        for ((key, value) in request.requestHeaders) {
            val name = key.lowercase()
            if (name in dropped) continue
            if (name == "user-agent" || name == "accept-language") continue
            if (name == "accept-encoding") continue
            runCatching { connection.setRequestProperty(key, value) }
        }
        // The encoding is asked for explicitly so the answer is known: a compressed
        // body that is handed over as if it were plain is a body the page cannot read.
        connection.setRequestProperty("Accept-Encoding", "gzip, deflate")

        // The cookies come from the browser's own store, so a login made in the page is
        // sent on the next request exactly as it would be.
        try {
            val cookie = CookieManager.getInstance().getCookie(url)
            if (!cookie.isNullOrBlank()) connection.setRequestProperty("Cookie", cookie)
        } catch (e: Exception) {}

        val code = connection.responseCode
        if (code == -1) return null

        val headers = HashMap<String, String>()
        var contentEncoding = ""
        val raw = connection.headerFields
        if (com.joebrowser.app.BuildConfig.DEBUG) {
            android.util.Log.d("JoeNet", "$url -> ${connection.responseCode} " +
                raw.entries.joinToString("; ") { "${it.key}=${it.value}" })
        }
        for ((key, values) in raw) {
            if (key == null) continue
            val name = key.lowercase()
            if (name == "content-encoding") {
                contentEncoding = (connection.getHeaderField(key) ?: values.joinToString(",")).lowercase()
                continue
            }
            if (name == "transfer-encoding") continue
            if (name == "set-cookie") {
                // Hand the cookie back to the browser so the session keeps working.
                values.forEach { value ->
                    runCatching { CookieManager.getInstance().setCookie(url, value) }
                }
                continue
            }
            // A header that arrives twice carries the answer twice, and the second copy
            // is the one the connection actually used. Joining them would give the page
            // a media type that is not a media type.
            headers[key] = connection.getHeaderField(key) ?: values.joinToString(", ")
        }

        val body: InputStream? = if (method.equals("HEAD", true)) {
            ByteArrayInputStream(ByteArray(0))
        } else {
            val stream = try { connection.inputStream } catch (e: Exception) { connection.errorStream }
            when {
                stream == null -> ByteArrayInputStream(ByteArray(0))
                contentEncoding.contains("gzip") -> GZIPInputStream(stream)
                contentEncoding.contains("deflate") -> InflaterInputStream(stream)
                else -> stream
            }
        }

        val contentType = headers.entries.firstOrNull {
            it.key.equals("Content-Type", ignoreCase = true)
        }?.value ?: ""
        val mime = contentType.substringBefore(";").trim().ifBlank { guessMime(url) }
        val encoding = contentType.substringAfter("charset=", "").trim().trim('"')
            .takeIf { contentType.contains("charset=", ignoreCase = true) }

        // A body that was compressed on the way here is plain now, so the length and the
        // encoding that describe it no longer apply.
        if (contentEncoding.isNotEmpty()) {
            headers.remove("Content-Encoding")
            headers.remove("content-encoding")
            headers.remove("Content-Length")
            headers.remove("content-length")
        }

        // The media type and the length are carried by the response object itself. Left
        // in the header map as well they arrive twice, and a header that arrives twice
        // is a header the page cannot make sense of.
        headers.keys.filter {
            it.equals("Content-Type", true) || it.equals("Content-Length", true)
        }.forEach { headers.remove(it) }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                WebResourceResponse(mime, encoding, code, connection.responseMessage ?: "", headers, body)
            } else {
                WebResourceResponse(mime, encoding, body)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** The proxy the request goes through. */
    private fun proxy(): Proxy {
        val port = relayPort()
        return if (port > 0) {
            Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port))
        } else {
            Proxy.NO_PROXY
        }
    }

    private fun guessMime(url: String): String {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".html") || path.endsWith(".htm") -> "text/html"
            path.endsWith(".js") || path.endsWith(".mjs") -> "text/javascript"
            path.endsWith(".css") -> "text/css"
            path.endsWith(".json") -> "application/json"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".gif") -> "image/gif"
            path.endsWith(".webp") -> "image/webp"
            path.endsWith(".svg") -> "image/svg+xml"
            path.endsWith(".woff2") -> "font/woff2"
            path.endsWith(".woff") -> "font/woff"
            path.endsWith(".txt") -> "text/plain"
            path.endsWith(".xml") -> "text/xml"
            else -> "text/plain"
        }
    }
}
