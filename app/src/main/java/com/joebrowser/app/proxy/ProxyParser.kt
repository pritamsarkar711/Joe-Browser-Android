package com.joebrowser.app.proxy

import com.joebrowser.app.data.model.ProxyConfig

/**
 * Reads a proxy out of whatever the user pasted.
 *
 * Proxy lists arrive in half a dozen shapes and the user should never have to think about
 * which one they have. This parser accepts every shape that is in common use and rejects
 * only what cannot be a proxy at all. It never throws: a line it cannot read comes back
 * as a configuration that is simply not enabled.
 */
object ProxyParser {

    private val PORT_PATTERN = Regex("[+-]?[0-9]+")

    /**
     * Parses one line.
     *
     * Accepted: `host:port`, `host:port:user:pass`, `scheme://host:port`,
     * `scheme://user:pass@host:port`, `user:pass@host:port`, bracketed IPv6,
     * `host:port` with a full-width colon, and a path after the address, which is dropped.
     */
    fun parseLine(line: String?): ProxyConfig {
        var s = (line ?: "").trim().replace('：', ':')
        if (s.isEmpty()) return ProxyConfig()

        var type = "http"
        val scheme = s.indexOf("://")
        if (scheme > 0) {
            val head = s.substring(0, scheme).lowercase()
            type = when (head) {
                "socks5", "socks5h" -> "socks5"
                "https" -> "https"
                // SOCKS4 is mapped to SOCKS5: Chrome does not speak SOCKS4 any more, and
                // pretending otherwise would fail at connect time with a worse message.
                "socks4", "socks4a" -> "socks5"
                else -> "http"
            }
            s = s.substring(scheme + 3)
        }

        val slash = s.indexOf('/')
        if (slash > 0) s = s.substring(0, slash)

        var user = ""
        var pass = ""
        val at = s.lastIndexOf('@')
        if (at > 0) {
            val cred = s.substring(0, at)
            s = s.substring(at + 1)
            val colon = cred.indexOf(':')
            if (colon >= 0) {
                user = cred.substring(0, colon)
                pass = cred.substring(colon + 1)
            } else {
                user = cred
            }
        }

        val host: String
        val port: Int
        if (s.isNotEmpty() && s[0] == '[') {
            val close = s.indexOf(']')
            if (close < 0) return ProxyConfig()
            host = s.substring(1, close)
            val rest = s.substring(close + 1)
            if (!rest.startsWith(":")) return ProxyConfig()
            port = parsePort(rest.substring(1)) ?: return ProxyConfig()
        } else {
            val bits = s.split(':')
            if (bits.size < 2 || bits[0].isBlank()) return ProxyConfig()
            port = parsePort(bits[1]) ?: return ProxyConfig()
            host = bits[0].trim()
            if (bits.size >= 4) {
                user = bits[2]
                pass = bits[3]
            }
        }

        return ProxyConfig(
            type = type,
            host = host.trim(),
            port = port,
            user = user,
            password = pass,
        )
    }

    /** A strict integer parse that tolerates surrounding space but nothing else. */
    private fun parsePort(text: String): Int? {
        val t = text.trim()
        if (!PORT_PATTERN.matches(t)) return null
        val v = t.toIntOrNull() ?: return null
        if (v <= 0 || v > 65535) return null
        return v
    }

    /**
     * Reads a block of pasted text.
     *
     * One proxy per line. Lines that cannot be read are skipped rather than reported as
     * failures, because a list that arrived with a header line in it is still a list.
     */
    fun parseMany(text: String?): List<ProxyConfig> {
        if (text.isNullOrBlank()) return emptyList()
        return text.split('\n', '\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { parseLine(it) }
            .filter { it.enabled }
    }

    /** The full address, with the credentials, for copying to the clipboard. */
    fun fullAddress(cfg: ProxyConfig): String {
        val scheme = when (cfg.type.lowercase()) {
            "socks5" -> "socks5"
            "https" -> "https"
            else -> "http"
        }
        val cred = if (cfg.user.isNotEmpty()) {
            cfg.user + (if (cfg.password.isNotEmpty()) ":" + cfg.password else "") + "@"
        } else ""
        return "$scheme://$cred${cfg.host}:${cfg.port}"
    }

    /** The address without the credentials, for a log line or a label. */
    fun shortAddress(cfg: ProxyConfig): String = "${cfg.host}:${cfg.port}"
}
