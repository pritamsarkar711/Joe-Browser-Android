package com.joebrowser.app.fingerprint

import com.joebrowser.app.data.model.FingerprintConfig

/**
 * The user agent and the client hints a profile reports.
 *
 * These two have to agree with each other and with everything else the profile claims.
 * A user agent that says Windows while the client hints say Android, or a brand list with
 * four entries where a browser sends three, is a contradiction that costs nothing for a
 * scanner to find. Everything here is therefore derived from one object.
 */
object UserAgentBuilder {

    /** The engines a profile can claim. */
    const val ENGINE_CHROME = "chrome"
    const val ENGINE_CHROMIUM = "chromium"
    const val ENGINE_BRAVE = "brave"
    const val ENGINE_EDGE = "edge"
    const val ENGINE_FIREFOX = "firefox"

    val ENGINES = listOf(ENGINE_CHROME, ENGINE_CHROMIUM, ENGINE_BRAVE, ENGINE_EDGE, ENGINE_FIREFOX)

    fun prettyEngine(engine: String): String = when (engine) {
        ENGINE_CHROME -> "Chrome"
        ENGINE_CHROMIUM -> "Chromium"
        ENGINE_BRAVE -> "Brave"
        ENGINE_EDGE -> "Edge"
        ENGINE_FIREFOX -> "Firefox"
        else -> engine.replaceFirstChar { it.uppercase() }
    }

    data class Resolved(
        val userAgent: String,
        val platform: String,
        val brand: String,
        val brands: List<Pair<String, String>>,
        val fullVersionList: List<Pair<String, String>>,
        val fullVersion: String,
        val platformVersion: String,
        val architecture: String,
        val bitness: String,
        val model: String,
        val mobile: Boolean,
        val wow64: Boolean,
    )

    /**
     * Resolves everything the page runtime needs to say about the browser.
     *
     * @param fp the profile's fingerprint.
     * @param engine the browser engine the profile claims.
     * @param reportedVersion the four-part build the profile reports, if any.
     */
    fun resolve(fp: FingerprintConfig, engine: String, reportedVersion: String? = null): Resolved {
        val major = BrowserVersions.majorOf(reportedVersion).takeIf { it > 0 }
            ?: BrowserVersions.majorOf(fp.browserVersion).takeIf { it > 0 }
            ?: 131

        val full = BrowserVersions.fullChromiumVersion(reportedVersion, major)
        val fullMajor = BrowserVersions.majorOf(full).takeIf { it > 0 } ?: major

        // A hand-written user agent always wins: the user asked for exactly that string.
        if (!fp.autoUserAgent && fp.userAgentOverride.isNotBlank()) {
            return fromOverride(fp, fp.userAgentOverride.trim(), full)
        }

        // iOS browsers are all WebKit. A profile that claims an iPhone has to answer with
        // the Safari string, whatever engine it was created with: a Chrome user agent on
        // an iPhone is a contradiction before the first pixel is drawn.
        if (FingerprintCatalog.isIos(fp.os)) {
            return safari(fp, full)
        }

        return when {
            engine == ENGINE_FIREFOX -> firefox(fp)
            engine == ENGINE_EDGE -> chromium(fp, fullMajor, full, edge = true)
            else -> chromium(fp, fullMajor, full, edge = false)
        }
    }

    /**
     * The Safari user agent for an iPhone or an iPad.
     *
     * The model and the OS build come from the advertised viewport, so the user agent, the
     * screen and the pixel density all describe the same device. iOS never sends client
     * hints at all, so there are none to build here.
     */
    private fun safari(fp: FingerprintConfig, full: String): Resolved {
        val pad = fp.os.startsWith("iPad", ignoreCase = true)
        val token = FingerprintFactory.iosVersionToken(fp)
        val ver = FingerprintFactory.iosVersionFor(fp)
        val head = if (pad) "iPad; CPU OS $token" else "iPhone; CPU iPhone OS $token"
        val ua = "Mozilla/5.0 ($head like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) " +
            "Version/$ver Mobile/15E148 Safari/604.1"
        return Resolved(
            userAgent = ua,
            platform = if (pad) "iPad" else "iPhone",
            brand = "Safari",
            brands = emptyList(),
            fullVersionList = emptyList(),
            fullVersion = full,
            platformVersion = "",
            architecture = "",
            bitness = "",
            model = "",
            mobile = true,
            wow64 = false,
        )
    }

    // -----------------------------------------------------------------------------------------

    private fun chromium(
        fp: FingerprintConfig,
        major: Int,
        full: String,
        edge: Boolean,
    ): Resolved {
        val platToken = platformToken(fp)
        val ua = StringBuilder("Mozilla/5.0 ($platToken) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0")
        // Chrome has shipped the reduced user agent since M113: the string carries the
        // major version and zeros for the rest. A four-part build here would be a
        // self-inflicted "wrong browser version" flag.
        ua.append(" Safari/537.36")
        if (FingerprintCatalog.isMobileOs(fp.os)) {
            ua.setLength(ua.length - "Safari/537.36".length)
            ua.append(if (fp.deviceType == "tablet") "Safari/537.36" else "Mobile Safari/537.36")
        }
        if (edge) ua.append(" Edg/$major.0.0.0")

        val mobile = fp.deviceType != "desktop"
        val platform = platformString(fp)

        // The client hints use the same platform token the user agent does.
        val chPlatform = when {
            FingerprintCatalog.isMac(fp.os) -> "macOS"
            FingerprintCatalog.isLinux(fp.os) -> "Linux"
            FingerprintCatalog.isAndroid(fp.os) -> "Android"
            FingerprintCatalog.isIos(fp.os) -> "iOS"
            else -> "Windows"
        }
        val platformVersion = when {
            FingerprintCatalog.isMac(fp.os) -> "15.1.0"
            FingerprintCatalog.isAndroid(fp.os) -> "${FingerprintFactory.androidVersionFor(fp)}.0.0"
            FingerprintCatalog.isLinux(fp.os) -> "6.11.0"
            FingerprintCatalog.isWindows11(fp.os) -> "15.0.0"
            else -> "10.0.0"
        }
        val architecture = if (FingerprintCatalog.isAndroid(fp.os)) "arm" else "x86"
        val bitness = if (FingerprintCatalog.isAndroid(fp.os)) "" else "64"
        val model = if (FingerprintCatalog.isAndroid(fp.os)) androidModel(fp) else ""

        val brandName = if (edge) "Microsoft Edge" else "Google Chrome"
        val grease = listOf("Not_A Brand" to "8")
        val reduced = listOf(
            grease[0],
            "Chromium" to major.toString(),
            brandName to major.toString(),
        )
        val fullList = listOf(
            grease[0].first to fullVersionOf(grease[0].second, full),
            "Chromium" to full,
            brandName to full,
        )

        return Resolved(
            userAgent = ua.toString(),
            platform = platform,
            brand = if (edge) "Edge" else "Chrome",
            brands = reduced,
            fullVersionList = fullList,
            fullVersion = full,
            platformVersion = platformVersion,
            architecture = architecture,
            bitness = bitness,
            model = model,
            mobile = mobile,
            wow64 = false,
        )
    }

    private fun firefox(fp: FingerprintConfig): Resolved {
        val fmajor = 128
        val platToken = firefoxToken(fp)
        val ua = "Mozilla/5.0 ($platToken; rv:$fmajor.0) Gecko/20100101 Firefox/$fmajor.0"
        return Resolved(
            userAgent = ua,
            platform = when {
                FingerprintCatalog.isMac(fp.os) -> "MacIntel"
                FingerprintCatalog.isLinux(fp.os) -> "Linux x86_64"
                // Firefox on Android reports its own platform token, never a desktop one.
                FingerprintCatalog.isAndroid(fp.os) -> "Android"
                else -> "Win32"
            },
            brand = "Firefox",
            brands = emptyList(),
            fullVersionList = emptyList(),
            fullVersion = "$fmajor.0",
            platformVersion = if (FingerprintCatalog.isAndroid(fp.os)) {
                "${FingerprintFactory.androidVersionFor(fp)}.0.0"
            } else {
                ""
            },
            architecture = if (FingerprintCatalog.isAndroid(fp.os)) "arm" else "x86",
            bitness = if (FingerprintCatalog.isAndroid(fp.os)) "" else "64",
            model = if (FingerprintCatalog.isAndroid(fp.os)) androidModel(fp) else "",
            mobile = fp.deviceType != "desktop",
            wow64 = false,
        )
    }

    private fun fromOverride(fp: FingerprintConfig, ua: String, full: String): Resolved {
        val platform = when {
            ua.contains("iPhone") -> "iPhone"
            ua.contains("iPad") -> "iPad"
            ua.contains("Android") -> "Linux armv8l"
            ua.contains("Mac OS X") -> "MacIntel"
            ua.contains("Linux") -> "Linux x86_64"
            else -> "Win32"
        }
        val brand = when {
            ua.contains("Firefox/") -> "Firefox"
            platform == "iPhone" || platform == "iPad" -> "Safari"
            else -> "Chrome"
        }
        val major = BrowserVersions.majorFromUserAgent(ua).takeIf { it > 0 } ?: 131
        val chPlatform = when (platform) {
            "Win32" -> "Windows"
            "MacIntel" -> "macOS"
            "Linux x86_64" -> "Linux"
            "iPhone", "iPad" -> "iOS"
            else -> "Android"
        }
        return Resolved(
            userAgent = ua,
            platform = platform,
            brand = brand,
            brands = if (brand == "Safari" || brand == "Firefox") emptyList() else listOf(
                "Not_A Brand" to "8",
                "Chromium" to major.toString(),
                "Google Chrome" to major.toString(),
            ),
            fullVersionList = emptyList(),
            fullVersion = full,
            platformVersion = "",
            architecture = "x86",
            bitness = "64",
            model = "",
            mobile = fp.deviceType != "desktop",
            wow64 = false,
        )
    }

    // -----------------------------------------------------------------------------------------

    private fun platformToken(fp: FingerprintConfig): String = when {
        FingerprintCatalog.isMac(fp.os) -> "Macintosh; Intel Mac OS X 10_15_7"
        FingerprintCatalog.isLinux(fp.os) -> "X11; Linux x86_64"
        FingerprintCatalog.isAndroid(fp.os) -> {
            val model = androidModel(fp)
            "Linux; Android ${FingerprintFactory.androidVersionFor(fp)}; $model"
        }
        else -> "Windows NT 10.0; Win64; x64"
    }

    private fun platformString(fp: FingerprintConfig): String = when {
        FingerprintCatalog.isMac(fp.os) -> "MacIntel"
        FingerprintCatalog.isLinux(fp.os) -> "Linux x86_64"
        FingerprintCatalog.isAndroid(fp.os) -> "Linux armv8l"
        FingerprintCatalog.isIphoneOnly(fp.os) -> "iPhone"
        fp.os.startsWith("iPad", ignoreCase = true) -> "iPad"
        else -> "Win32"
    }

    private fun firefoxToken(fp: FingerprintConfig): String = when {
        FingerprintCatalog.isMac(fp.os) -> "Macintosh; Intel Mac OS X 10.15"
        FingerprintCatalog.isLinux(fp.os) -> "X11; Linux x86_64"
        FingerprintCatalog.isAndroid(fp.os) ->
            "Android ${FingerprintFactory.androidVersionFor(fp)}; ${if (fp.deviceType == "tablet") "Tablet" else "Mobile"}"
        else -> "Windows NT 10.0; Win64; x64"
    }

    /**
     * The handset model named in the user agent.
     *
     * When the viewport does not belong to a known handset the unified token `K` is used,
     * which is what Chrome itself reports for a device it cannot identify.
     */
    private fun androidModel(fp: FingerprintConfig): String {
        val device = FingerprintFactory.deviceIdentity(fp)
        return if (device.isNotEmpty()) device[0] else "K"
    }

    private fun fullVersionOf(greaseVersion: String, full: String): String {
        if (greaseVersion.contains('.')) return greaseVersion
        return if (full.contains('.')) full else "8.0.0.0"
    }
}
