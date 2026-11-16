package com.joebrowser.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Proxy settings for a profile.
 *
 * Mirrors the desktop Joe Browser model so a profile exported from either build reads the
 * same way: the type, the endpoint, the credentials and the result of the last successful
 * public-IP lookup, which is what lets a profile line its clock and language up with the
 * exit node without asking again.
 */
@Serializable
data class ProxyConfig(
    /** "none" | "http" | "https" | "socks5" */
    val type: String = "none",
    val host: String = "",
    val port: Int = 0,
    val user: String = "",
    val password: String = "",
    /** Cached result of the last successful check, used for automatic timezone matching. */
    val lastIp: String = "",
    val lastCountry: String = "",
    val lastCountryCode: String = "",
    val lastTimezone: String = "",
    val lastCity: String = "",
    /** Keep the session alive on a direct connection when the proxy stops answering. */
    val fallbackDirect: Boolean = true,
    /** Unix millis of the last successful check; 0 means never checked. */
    val lastCheckedAt: Long = 0L,
    /** "unknown" | "online" | "offline" */
    val lastStatus: String = "unknown",
    /** Rotations seen on this proxy since it was added. */
    val rotationCount: Int = 0,
    /** Label shown in the list, e.g. "Proxy US" or a user supplied name. */
    val label: String = "",
) {
    val enabled: Boolean
        get() = type.isNotNone() && host.isNotBlank() && port > 0

    val detail: String
        get() = if (!enabled) "No proxy" else "${type.uppercase()} $host:$port"

    /** Short status word for the list row. */
    val statusLabel: String
        get() = when (lastStatus) {
            "online" -> "Online"
            "offline" -> "Offline"
            else -> "Not checked"
        }
}

fun String.isNotNone(): Boolean = isNotBlank() && !equals("none", ignoreCase = true)

/**
 * Per-profile built-in browser tools. These are injected by the app itself through the
 * page runtime, not installed from a store.
 */
@Serializable
data class ToolConfig(
    /** Type clipboard content one character at a time with human-like timing. */
    val humanPaste: Boolean = true,
    /** "relaxed" | "natural" | "fast" | "instant" */
    val humanPasteSpeed: String = "natural",
    /** Notify when the public IP rotates or the proxy goes offline. */
    val proxyWatch: Boolean = true,
    /** Seconds between checks (30 ... 600). */
    val proxyWatchInterval: Int = 60,
    /** Restore the native right-click menu and copy on pages that block them. */
    val enableRightClick: Boolean = true,
    /** Also remove selection / drag blockers installed by the page. */
    val unlockSelection: Boolean = true,
    /** Extension ids that are auto-installed whenever this profile is opened. */
    val autoInstallExtensions: Boolean = true,
)

/**
 * The fingerprint a profile reports. Every field is independent of the device the app
 * runs on: the point of an anti-detect browser is that a profile looks like a machine
 * that has nothing to do with the phone it is currently running on.
 */
@Serializable
data class FingerprintConfig(
    var seed: Int = 0,
    /** One of the canonical OS labels, e.g. "Windows 11", "Android", "iPhone". */
    var os: String = "Windows 11",
    /** "desktop" | "mobile" | "tablet" */
    var deviceType: String = "desktop",
    var timezone: String = "America/New_York",
    var language: String = "en-US",
    var acceptLanguage: String = "en-US,en;q=0.9",
    var screenW: Int = 1920,
    var screenH: Int = 1080,
    var availW: Int = 1920,
    var availH: Int = 1032,
    var colorDepth: Int = 24,
    var pixelRatio: Double = 1.0,
    var webGlVendor: String = "Google Inc. (NVIDIA)",
    var webGlRenderer: String = "ANGLE (NVIDIA, NVIDIA GeForce RTX 3060 Direct3D11 vs_5_0 ps_5_0, D3D11)",
    var cores: Int = 8,
    /** navigator.deviceMemory: Chromium only reports 0.25/0.5/1/2/4/8. */
    var memory: Int = 8,
    var touchPoints: Int = 0,
    var canvasNoise: Boolean = true,
    var audioNoise: Boolean = true,
    var fontsNoise: Boolean = true,
    var doNotTrack: Boolean = false,
    var spoofBattery: Boolean = true,
    var spoofMac: Boolean = true,
    var webRtcProtect: Boolean = true,
    /** "off" | "proxy" | "public" */
    var webRtcMode: String = "proxy",
    /** "proxy" | "system" | "custom" | "block" */
    var geoMode: String = "proxy",
    var geoLat: String = "",
    var geoLng: String = "",
    var autoMatchProxy: Boolean = true,
    var autoUserAgent: Boolean = true,
    /** "auto" or an Android version such as "15". */
    var androidVersion: String = "auto",
    /** "auto" or an iOS version such as "17.5". */
    var iosVersion: String = "auto",
    /** "auto" | "windows" | "macos" | "linux" | "minimal" */
    var fontSet: String = "auto",
    /** The build the profile reports, mirrored here because the runtime reads the fingerprint. */
    var browserVersion: String = "",
    /** Set by the user; overrides the generated user agent when autoUserAgent is off. */
    var userAgentOverride: String = "",
    /** Resolved at launch time; not persisted with the rest of the identity. */
    @Transient val userAgent: String = "",
    @Transient val platform: String = "Win32",
    @Transient val brand: String = "Chrome",
) {
    val timezoneShort: String
        get() {
            if (timezone.isEmpty()) return ""
            val i = timezone.lastIndexOf('/')
            return if (i >= 0) timezone.substring(i + 1).replace('_', ' ') else timezone
        }

    /**
     * The languages the browser reports.
     *
     * A browser reports the language and then its base, in that order, and nothing else.
     */
    val languages: List<String>
        get() {
            val dash = language.indexOf('-')
            return if (dash > 0) {
                listOf(language, language.substring(0, dash))
            } else {
                listOf(language)
            }
        }
}

/** A browser profile: an independent identity with its own storage, proxy and tools. */
@Serializable
data class Profile(
    val id: String = "",
    val name: String = "",
    val notes: String = "",
    /** The page a profile opens on. Blank means the built-in fingerprint test page. */
    val startUrl: String = DEFAULT_START_URL,
    /** "chrome" | "chromium" | "brave" | "edge" | "firefox" | "custom" */
    val browserKind: String = "chrome",
    val browserPath: String = "",
    /** Pinned engine build ("kernel"); empty means the system browser engine. */
    val kernelEngine: String = "",
    val kernelVersion: String = "",
    /** The four-part browser build this profile reports. */
    val browserVersion: String = "",
    /** Clear cookies, site data, storage and cache when the browser closes. */
    val freshSession: Boolean = false,
    /** Folder-style grouping, as in AdsPower / Dolphin Anty. */
    val group: String = "",
    /** Comma separated labels, searchable and shown on the card. */
    val tags: String = "",
    /** Free-form account login / status marker. */
    val status: String = "",
    val openCount: Int = 0,
    val created: Long = 0L,
    val lastOpened: Long = 0L,
    /** Set once the profile has been opened, so the first launch can run the audit. */
    val launchedOnce: Boolean = false,
    val fp: FingerprintConfig = FingerprintConfig(),
    val proxy: ProxyConfig = ProxyConfig(),
    val tools: ToolConfig = ToolConfig(),
    /** Extension ids this profile installs automatically when it opens. */
    val extensionIds: List<String> = emptyList(),
) {
    val groupOrUngrouped: String get() = group.ifBlank { "Ungrouped" }

    val initials: String
        get() {
            val n = name.trim()
            if (n.isEmpty()) return "JB"
            val parts = n.split(' ', '-', '_').filter { it.isNotEmpty() }
            if (parts.size >= 2) return ("" + parts[0][0] + parts[1][0]).uppercase()
            if (n.length >= 2) return n.substring(0, 2).uppercase()
            return n.uppercase()
        }

    companion object {
        const val DEFAULT_START_URL = "https://ipfighter.com/browser-fingerprint"
    }
}

/** Application-wide settings. */
@Serializable
data class AppSettings(
    val defaultStartUrl: String = Profile.DEFAULT_START_URL,
    /** 0 = follow the system, 1 = dark, 2 = light. */
    val themeMode: Int = 0,
    /** 0 recent, 1 name, 2 created, 3 running first, 4 operating system, 5 group. */
    val sortBy: Int = 0,
    val uiLanguage: String = "en",
    val defaultGroup: String = "",
    val enableExtensions: Boolean = true,
    val enableCustomExtensions: Boolean = true,
    /** Record the pages each profile visits. */
    val historyEnabled: Boolean = true,
    /** Ask before a profile closes so nothing is lost by accident. */
    val confirmBeforeClosing: Boolean = false,
    /** Keep deleted profile JSON in a recycle bin instead of erasing it. */
    val useTrash: Boolean = true,
    /** Show the proxy status bar on the browser screen. */
    val showProxyBadge: Boolean = true,
    /** Notifications for proxy outages and IP rotations. */
    val proxyNotifications: Boolean = true,
    /**
     * Lets the browser engine be inspected with the platform's own inspector.
     *
     * It is off by default because an inspector that is open is the one thing a page can
     * find out about, and it is the reason a fingerprint scan reads "tampering".
     */
    val developerMode: Boolean = false,
    /** Optional Play Store style licence acceptance, kept for parity with the desktop build. */
    val acceptedTerms: Boolean = false,
)

/** A proxy the user added to the vault, reusable across profiles. */
@Serializable
data class ProxyRecord(
    val id: String = "",
    val name: String = "",
    val type: String = "http",
    val host: String = "",
    val port: Int = 0,
    val user: String = "",
    val password: String = "",
    val lastIp: String = "",
    val lastCountry: String = "",
    val lastCountryCode: String = "",
    val lastTimezone: String = "",
    val lastCity: String = "",
    val lastStatus: String = "unknown",
    val lastCheckedAt: Long = 0L,
    val rotationCount: Int = 0,
    val created: Long = 0L,
    val note: String = "",
) {
    val display: String get() = if (name.isNotBlank()) name else "$host:$port"
}

/** An extension the user added, from the Chrome Web Store or a local folder. */
@Serializable
data class ExtensionRecord(
    val id: String = "",
    val name: String = "",
    val version: String = "",
    val description: String = "",
    val publisher: String = "",
    val iconUrl: String = "",
    /** Chrome Web Store extension id, or a local identifier for a unpacked folder. */
    val storeId: String = "",
    val url: String = "",
    val homepage: String = "",
    val enabled: Boolean = true,
    val installed: Boolean = false,
    val folder: String = "",
    val installedAt: Long = 0L,
    val sizeBytes: Long = 0L,
    val source: String = "store",
    @SerialName("rating") val ratingText: String = "",
)

/** One visited page, kept per profile so a URL that was passed through can be found again. */
@Serializable
data class HistoryEntry(
    val id: String = "",
    val profileId: String = "",
    val url: String = "",
    val title: String = "",
    val visitedAt: Long = 0L,
)
