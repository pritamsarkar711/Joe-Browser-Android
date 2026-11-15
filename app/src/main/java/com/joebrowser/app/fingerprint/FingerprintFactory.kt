package com.joebrowser.app.fingerprint

import com.joebrowser.app.data.model.FingerprintConfig
import com.joebrowser.app.data.model.ProxyConfig
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Builds and repairs the fingerprint a profile reports.
 *
 * The engine has one job: make the numbers a profile advertises describe a machine that
 * could exist. A phone that claims a 24-core desktop GPU, eight gigabytes of memory and a
 * Windows build of Chrome is not a fingerprint, it is a contradiction, and contradiction is
 * what every scanner looks for first.
 *
 * The rules that keep it honest live in [coerce]. The rules that make it varied live in
 * [regenerate].
 */
object FingerprintFactory {

    // -----------------------------------------------------------------------------------------
    // Seeding
    // -----------------------------------------------------------------------------------------

    /**
     * A fresh, non-guessable seed.
     *
     * Deliberately not clock-derived: two profiles created in the same second used to draw
     * the same hardware, which is a far worse leak than a repeating seed.
     */
    fun newSeed(): Int {
        val b = UUID.randomUUID().toString().replace("-", "")
        var acc = 0
        for (i in 0 until 8) {
            acc = acc xor b.substring(i * 2, i * 2 + 2).toInt(16)
            acc = acc * 31 + i
        }
        return if (acc == 0) 1 else acc
    }

    // -----------------------------------------------------------------------------------------
    // Screen
    // -----------------------------------------------------------------------------------------

    /**
     * Fills the screen fields from a viewport triple.
     *
     * The available height is smaller than the screen on a desktop because a real desktop
     * browser window has a title bar and a task bar. A profile that reports an available
     * height equal to its screen height is claiming to be fullscreen, and no scanner is
     * fooled by that.
     */
    fun applyScreen(fp: FingerprintConfig, os: String, screen: DoubleArray) {
        val w = screen.getOrNull(0)?.roundToInt() ?: 1920
        val h = screen.getOrNull(1)?.roundToInt() ?: 1080
        fp.screenW = w
        fp.screenH = h
        fp.availW = w
        fp.availH = when {
            FingerprintCatalog.isMobileOs(os) -> h
            FingerprintCatalog.isMac(os) -> h - 25
            FingerprintCatalog.isLinux(os) -> h - 27
            else -> h - 40
        }
        fp.pixelRatio = when {
            screen.size > 2 && screen[2] > 0 -> screen[2]
            FingerprintCatalog.isMac(os) -> 2.0
            FingerprintCatalog.isIphoneOnly(os) -> 3.0
            os.startsWith("iPad", ignoreCase = true) -> 2.0
            FingerprintCatalog.isAndroid(os) -> 2.625
            else -> 1.0
        }
        fp.touchPoints = if (FingerprintCatalog.isMobileOs(os)) 5 else 0
        fp.deviceType = FingerprintCatalog.deviceTypeFor(os)
    }

    /** The viewport height a mobile page actually gets, after the browser chrome. */
    fun mobileViewHeight(fp: FingerprintConfig): Int {
        if (fp.screenH <= 0 || !FingerprintCatalog.isMobileOs(fp.os)) return 0
        val pad = fp.deviceType == "tablet"
        val bars = when {
            FingerprintCatalog.isIos(fp.os) -> if (pad) 92 else 124
            else -> if (pad) 96 else 120
        }
        val h = fp.screenH - bars
        val floor = (fp.screenH * 0.7).roundToInt()
        return max(h, floor)
    }

    /** The scrollbar width the profile claims: 0 on mobile, 15 on macOS, 17 elsewhere. */
    fun scrollbarWidthFor(fp: FingerprintConfig): Int = when {
        FingerprintCatalog.isMobileOs(fp.os) -> 0
        FingerprintCatalog.isMac(fp.os) -> 15
        else -> 17
    }

    // -----------------------------------------------------------------------------------------
    // Devices
    // -----------------------------------------------------------------------------------------

    /** The handset that owns a viewport, or an empty pair when the OS is not a mobile one. */
    fun deviceIdentity(fp: FingerprintConfig): List<String> {
        if (fp.os.isEmpty()) return emptyList()
        val key = "${fp.screenW}x${fp.screenH}"
        val table = when {
            FingerprintCatalog.isIos(fp.os) -> FingerprintCatalog.IOS_DEVICES
            fp.deviceType == "tablet" -> FingerprintCatalog.ANDROID_TABLETS
            FingerprintCatalog.isAndroid(fp.os) -> FingerprintCatalog.ANDROID_DEVICES
            else -> return emptyList()
        }
        return table[key] ?: emptyList()
    }

    fun androidVersionFor(fp: FingerprintConfig): String {
        if (fp.androidVersion.isNotBlank() && fp.androidVersion != "auto") return fp.androidVersion.trim()
        val device = deviceIdentity(fp)
        return if (device.size >= 2 && device[1].isNotBlank()) device[1] else "16"
    }

    fun iosVersionFor(fp: FingerprintConfig): String {
        if (fp.iosVersion.isNotBlank() && fp.iosVersion != "auto") return fp.iosVersion.trim()
        val device = deviceIdentity(fp)
        return if (device.size >= 3 && device[2].isNotBlank()) device[2] else "26.1"
    }

    fun iosVersionToken(fp: FingerprintConfig): String = iosVersionFor(fp).replace('.', '_')

    fun osVersionLabel(fp: FingerprintConfig): String = when {
        FingerprintCatalog.isAndroid(fp.os) -> "Android " + androidVersionFor(fp)
        FingerprintCatalog.isIos(fp.os) -> "iOS " + iosVersionFor(fp)
        else -> ""
    }

    // -----------------------------------------------------------------------------------------
    // Generation
    // -----------------------------------------------------------------------------------------

    /**
     * Picks a hardware tier from the GPU pool.
     *
     * Two uniforms multiplied: the result clusters near index 0, so the newest hardware is
     * the most common answer, but the tail is never unreachable. A profile claiming a
     * 2020 laptop GPU is rare; a profile claiming it should be rare too.
     */
    private fun pickTier(count: Int, r: Random): Int {
        if (count <= 1) return 0
        val bias = (r.nextDouble() * r.nextDouble() * count).toInt()
        return if (bias < count) bias else count - 1
    }

    /**
     * Rolls a whole fingerprint.
     *
     * @param os the operating system to describe.
     * @param proxy the profile's proxy, used to line the clock and language up with the exit node.
     * @param widePool when true the newest-hardware bias is dropped.
     * @param hardwareOnly when true the privacy defaults are left untouched and only the
     *        hardware is re-rolled. The uniqueness loop needs this: re-rolling the privacy
     *        settings would silently undo the user's choices.
     */
    fun regenerate(
        fp: FingerprintConfig,
        os: String,
        proxy: ProxyConfig?,
        widePool: Boolean = false,
        hardwareOnly: Boolean = false,
    ): FingerprintConfig {
        val target = os.takeIf { it.isNotEmpty() && it != FingerprintCatalog.OS_SYSTEM_DEFAULT }
            ?: FingerprintCatalog.systemOsName()

        val out = fp.copy(seed = newSeed())
        val r = Random(out.seed)

        out.os = target
        out.deviceType = FingerprintCatalog.deviceTypeFor(target)

        val screens = FingerprintCatalog.SCREENS[target] ?: FingerprintCatalog.SCREENS[FingerprintCatalog.OS_WINDOWS_11]!!
        val screen = screens[r.nextInt(screens.size)]
        applyScreen(out, target, screen)
        out.colorDepth = 24

        val gpus = FingerprintCatalog.GPUS[FingerprintCatalog.gpuKey(target)] ?: emptyList()
        var tier = if (gpus.isEmpty()) 0 else if (widePool) r.nextInt(gpus.size) else pickTier(gpus.size, r)

        val device = deviceIdentity(out)
        var pair: Pair<String, String>? = null
        if (device.size >= 4 && device[3].isNotEmpty()) {
            pair = device[2] to device[3]
            // The handset's own silicon decides the tier, so the cores and the memory that
            // follow describe the same machine as the GPU.
            val idx = gpus.indexOfFirst { it.second == device[3] }
            if (idx >= 0) tier = idx
        }
        if (pair == null && gpus.isNotEmpty()) pair = gpus[tier]

        if (pair != null) {
            out.webGlVendor = pair.first
            out.webGlRenderer = pair.second
        }

        if (!hardwareOnly) {
            out.canvasNoise = false
            out.audioNoise = true
            out.fontsNoise = true
            out.webRtcProtect = true
            out.webRtcMode = "proxy"
            out.spoofBattery = true
            out.spoofMac = true
            out.doNotTrack = false
            out.geoMode = "proxy"
            out.autoUserAgent = true
            out.userAgentOverride = ""
        }

        if (out.androidVersion.isEmpty()) out.androidVersion = "auto"
        if (out.iosVersion.isEmpty()) out.iosVersion = "auto"
        if (out.fontSet.isEmpty()) out.fontSet = "auto"

        applyLocale(out, proxy, r)
        return out
    }

    /**
     * Lines the clock and the language up with the exit node.
     *
     * The rule that matters is the last one: a profile whose language names a different
     * country than its clock names is the single most common giveaway in a fingerprint set.
     * `en-US` over `Europe/London` is rejected for exactly that reason.
     */
    fun applyLocale(fp: FingerprintConfig, proxy: ProxyConfig?, r: Random? = null) {
        if (proxy != null && fp.autoMatchProxy) {
            if (proxy.enabled && proxy.lastTimezone.isNotEmpty()) {
                fp.timezone = proxy.lastTimezone
                fp.language = FingerprintCatalog.languageForTimezone(fp.timezone)
                fp.acceptLanguage = FingerprintCatalog.acceptLanguageFor(fp.language)
                return
            }
            val cc = proxy.lastCountryCode
            if (cc.isNotEmpty() && (proxy.enabled || proxy.lastIp.isNotEmpty())) {
                val entry = FingerprintCatalog.COUNTRY_LOCALE[cc.uppercase()]
                if (entry != null) {
                    fp.timezone = entry.first
                    fp.language = entry.second
                    fp.acceptLanguage = FingerprintCatalog.acceptLanguageFor(fp.language)
                    return
                }
            }
        }
        fp.timezone = systemTimezone()
        val natural = FingerprintCatalog.languageForTimezone(fp.timezone)
        var local = systemLanguage()
        val known = FingerprintCatalog.LANGUAGES.contains(local)
        if (!known || FingerprintCatalog.countryForLanguage(local) != FingerprintCatalog.countryForLanguage(natural)) {
            local = natural
        }
        fp.language = local
        fp.acceptLanguage = FingerprintCatalog.acceptLanguageFor(fp.language)
    }

    /** The same idea as [applyLocale], but the country leads and the timezone follows. */
    fun applyProxyLocale(fp: FingerprintConfig, proxy: ProxyConfig?) {
        if (proxy == null) return
        val cc = proxy.lastCountryCode.uppercase()
        val entry = FingerprintCatalog.COUNTRY_LOCALE[cc]
        if (entry != null) {
            fp.timezone = entry.first
            fp.language = entry.second
        } else if (proxy.lastTimezone.isNotEmpty()) {
            fp.timezone = proxy.lastTimezone
            fp.language = FingerprintCatalog.languageForTimezone(fp.timezone)
        } else {
            return
        }
        fp.acceptLanguage = FingerprintCatalog.acceptLanguageFor(fp.language)
    }

    // -----------------------------------------------------------------------------------------
    // Repair
    // -----------------------------------------------------------------------------------------

    /**
     * Repairs a fingerprint so that it describes a machine that could exist.
     *
     * Returns the number of repairs made, so the editor can tell the user when a hand-typed
     * value was quietly corrected.
     */
    fun coerce(fp: FingerprintConfig): Int {
        var repaired = 0

        val gpus = FingerprintCatalog.GPUS[FingerprintCatalog.gpuKey(fp.os)] ?: emptyList()

        // The WebGL pair belongs to the platform: a Direct3D renderer on a Mac profile is
        // impossible, and a mismatched vendor is a free tell.
        val idx = gpus.indexOfFirst { it.second == fp.webGlRenderer }
        if (idx >= 0) {
            if (fp.webGlVendor != gpus[idx].first) {
                fp.webGlVendor = gpus[idx].first
                repaired++
            }
        } else if (gpus.isNotEmpty() && !FingerprintCatalog.isWindows(fp.os)) {
            // The adapter is not one this platform can have. On Windows a card that is
            // newer than the table is still a card that exists, so it is kept; on every
            // other platform the table is closed, and an adapter outside it is a
            // contradiction rather than a new piece of hardware.
            val byVendor = gpus.firstOrNull { it.first == fp.webGlVendor }
            val replacement = byVendor ?: gpus.first()
            fp.webGlRenderer = replacement.second
            fp.webGlVendor = replacement.first
            repaired++
        } else {
            val vendor = FingerprintCatalog.vendorForRenderer(fp.webGlRenderer)
            if (vendor != null && fp.webGlVendor != vendor) {
                fp.webGlVendor = vendor
                repaired++
            }
        }

        // deviceMemory is a power of two, capped at 8: that is what Chromium reports.
        if (fp.memory != 2 && fp.memory != 4 && fp.memory != 8) {
            fp.memory = when {
                fp.memory >= 8 -> 8
                fp.memory >= 4 -> 4
                else -> 2
            }
            repaired++
        }

        // The core count has to be one a browser can actually report.
        if (FingerprintCatalog.CORE_OPTIONS.none { it == fp.cores }) {
            fp.cores = FingerprintCatalog.CORE_OPTIONS.minByOrNull { kotlin.math.abs(it - fp.cores) } ?: 8
            repaired++
        }

        // Android is always octa-core in the table; a phone that claims sixteen cores is
        // not a phone any more.
        if (FingerprintCatalog.isAndroid(fp.os) && fp.cores != 8) {
            fp.cores = 8
            repaired++
        }

        // Touch points follow the device type.
        val touch = if (FingerprintCatalog.isMobileOs(fp.os)) 5 else 0
        if (fp.touchPoints != touch) {
            fp.touchPoints = touch
            repaired++
        }

        // Repair the derived screen fields when they are missing or impossible.
        val isSystemTarget = fp.os.isEmpty() ||
            fp.os == FingerprintCatalog.OS_SYSTEM_DEFAULT ||
            fp.os == FingerprintCatalog.systemOsName()
        if (fp.screenW > 0 && fp.screenH > 0 && !isSystemTarget) {
            if (fp.pixelRatio <= 0 || fp.availW <= 0 || fp.availH <= 0 ||
                fp.availW > fp.screenW || fp.availH > fp.screenH
            ) {
                applyScreen(fp, fp.os, doubleArrayOf(fp.screenW.toDouble(), fp.screenH.toDouble(), fp.pixelRatio))
                repaired++
            }
        }

        if (fp.timezone.isEmpty()) {
            fp.timezone = "America/New_York"
            repaired++
        }
        if (fp.language.isEmpty()) {
            fp.language = FingerprintCatalog.languageForTimezone(fp.timezone)
            repaired++
        }
        if (fp.acceptLanguage.isEmpty()) {
            fp.acceptLanguage = FingerprintCatalog.acceptLanguageFor(fp.language)
            repaired++
        }
        if (fp.webRtcMode !in setOf("off", "proxy", "public")) {
            fp.webRtcMode = if (fp.webRtcProtect) "proxy" else "off"
            repaired++
        }
        if (fp.geoMode !in setOf("proxy", "system", "custom", "block")) {
            fp.geoMode = "proxy"
            repaired++
        }
        val expectedType = FingerprintCatalog.deviceTypeFor(fp.os)
        if (fp.deviceType != expectedType) {
            fp.deviceType = expectedType
            repaired++
        }
        return repaired
    }

    // -----------------------------------------------------------------------------------------
    // Device locale
    // -----------------------------------------------------------------------------------------

    /** The IANA timezone of this device. */
    fun systemTimezone(): String = java.util.TimeZone.getDefault().id

    /** The locale of this device, written the way a browser writes it. */
    fun systemLanguage(): String {
        val raw = java.util.Locale.getDefault().toString()
        if (raw.isBlank()) return "en-US"
        // Android writes "en_US"; a browser writes "en-US".
        return raw.replace('_', '-')
    }

    // -----------------------------------------------------------------------------------------
    // Display
    // -----------------------------------------------------------------------------------------

    /**
     * A short, human label for the GPU.
     *
     * `ANGLE (NVIDIA, NVIDIA GeForce RTX 3060 ...)` reads as noise in a list, so the part
     * that matters is pulled out and the rest is dropped.
     */
    fun gpuLabel(renderer: String): String {
        if (renderer.startsWith("ANGLE (")) {
            val inner = renderer.substring(7)
            val parts = inner.split(", ")
            if (parts.size >= 2) {
                var gpu = parts[1]
                for (stop in listOf(" Direct3D", " (", " Metal")) {
                    val i = gpu.indexOf(stop)
                    if (i > 0) gpu = gpu.substring(0, i)
                }
                if (gpu.contains(") V3")) gpu = gpu.replace(") V3", ")")
                if (gpu.length > 44) gpu = gpu.substring(0, 41) + "..."
                val vendor = parts[0].replace("ANGLE (", "").trim()
                return if (gpu.contains(vendor)) gpu else "$vendor, $gpu"
            }
        }
        return if (renderer.length > 48) renderer.substring(0, 45) + "..." else renderer
    }

    /** The human-readable name of the GPU vendor. */
    fun gpuVendorLabel(vendor: String): String = vendor
        .removePrefix("Google Inc. ")
        .removeSurrounding("(", ")")
        .ifBlank { "Unknown" }
}
