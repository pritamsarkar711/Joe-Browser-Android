package com.joebrowser.app.fingerprint

import com.joebrowser.app.data.model.FingerprintConfig
import java.util.Locale

/**
 * The identity of a fingerprint.
 *
 * A fingerprint is not one value, it is twelve: the user agent, the platform, the
 * navigator, the screen, the hardware, the GPU, the canvas, the audio, the fonts, the
 * clock, the language and the WebRTC policy. Two profiles can agree on the user agent and
 * still be obviously the same machine if their canvas noise comes from the same seed, and
 * they can be completely different machines that happen to share a screen size.
 *
 * Each surface is reduced to a short hash, and the surfaces are then compared with weights
 * that reflect how much each one actually identifies a person. The GPU and the canvas count
 * for the most; the language and the navigator count for the least.
 */
object FingerprintIdentity {

    const val USER_AGENT = "userAgent"
    const val NAVIGATOR = "navigator"
    const val SCREEN = "screen"
    const val HARDWARE = "hardware"
    const val WEBGL = "webgl"
    const val CANVAS = "canvas"
    const val AUDIO = "audio"
    const val FONTS = "fonts"
    const val TIMEZONE = "timezone"
    const val LANGUAGE = "language"
    const val WEBRTC = "webrtc"
    const val PLATFORM = "platform"

    /** The surfaces in display order. */
    val ALL = listOf(
        USER_AGENT, PLATFORM, NAVIGATOR, SCREEN, HARDWARE, WEBGL,
        CANVAS, AUDIO, FONTS, TIMEZONE, LANGUAGE, WEBRTC,
    )

    val LABELS = mapOf(
        USER_AGENT to "User agent",
        PLATFORM to "OS / platform",
        NAVIGATOR to "Navigator",
        SCREEN to "Screen",
        HARDWARE to "CPU / memory",
        WEBGL to "WebGL",
        CANVAS to "Canvas",
        AUDIO to "Audio",
        FONTS to "Fonts",
        TIMEZONE to "Timezone",
        LANGUAGE to "Language",
        WEBRTC to "WebRTC",
    )

    /** How much each surface contributes to the score. */
    val WEIGHTS = mapOf(
        WEBGL to 5, CANVAS to 5, USER_AGENT to 4, AUDIO to 4, FONTS to 4, TIMEZONE to 4,
        PLATFORM to 3, SCREEN to 3, HARDWARE to 3, WEBRTC to 3, NAVIGATOR to 2, LANGUAGE to 2,
    )

    private val TOTAL_WEIGHT = WEIGHTS.values.sum()

    /** Surfaces that cannot on their own make two profiles "the same machine". */
    private fun isDerived(surface: String): Boolean =
        surface == USER_AGENT || surface == TIMEZONE || surface == LANGUAGE

    enum class Relation(val rank: Int) {
        Distinct(0), Remote(1), Related(2), Linked(3), Identical(4)
    }

    data class Comparison(
        val relation: Relation,
        val score: Int,
        val shared: List<String>,
        val summary: String,
    )

    // -----------------------------------------------------------------------------------------
    // Hashing
    // -----------------------------------------------------------------------------------------

    /**
     * A short, stable hash of a set of parts.
     *
     * Two independent FNV-1a passes are mixed and truncated to ten hex characters. One
     * pass would be enough for a display token, but a profile set can reach thousands of
     * entries and a 32-bit hash collides at that scale; two passes make the collision
     * probability negligible for a few lines of code.
     */
    fun hash(vararg parts: String?): String {
        val sb = StringBuilder()
        for (i in parts.indices) {
            if (i > 0) sb.append('')
            sb.append(parts[i] ?: "")
        }
        return fnvHex(sb.toString())
    }

    private fun fnvHex(text: String): String {
        val p1 = 16777619L
        val p2 = 3266489917L
        var h1 = 2166136261L
        var h2 = 2166136261L xor 0x9E3779B9L
        for (b in text.toByteArray(Charsets.UTF_8)) {
            h1 = (h1 xor (b.toLong() and 0xFF)) * p1
            h2 = (h2 xor ((b + 0x9E).toLong() and 0xFF)) * p2
        }
        val s = (h1.toUInt().toString(16).padStart(8, '0') + h2.toUInt().toString(16).padStart(8, '0'))
        return s.substring(0, 10)
    }

    private fun lower(v: String?): String = (v ?: "").trim().lowercase(Locale.ROOT)

    // -----------------------------------------------------------------------------------------
    // Surfaces
    // -----------------------------------------------------------------------------------------

    /** The value that identifies a profile on each surface. */
    fun surfaces(fp: FingerprintConfig): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        add(map, USER_AGENT, listOf(if (fp.userAgentOverride.isEmpty()) "automatic" else fp.userAgentOverride))
        add(map, PLATFORM, listOf(lower(fp.os), lower(fp.deviceType), lower(fp.fontSet)))
        add(
            map, NAVIGATOR, listOf(
                "hw=${fp.cores}", "mem=${fp.memory}", "touch=${fp.touchPoints}",
                "dnt=${if (fp.doNotTrack) 1 else 0}", "ua=${if (fp.autoUserAgent) "auto" else "manual"}",
            )
        )
        add(
            map, SCREEN, listOf(
                "${fp.screenW}x${fp.screenH}", "avail=${fp.availW}x${fp.availH}",
                "depth=${fp.colorDepth}", "dpr=${trimNumber(fp.pixelRatio)}",
            )
        )
        add(
            map, HARDWARE, listOf(
                "cores=${fp.cores}", "mem=${fp.memory}",
                "noise=${if (fp.canvasNoise) 1 else 0}", "seed=${fp.seed}",
            )
        )
        add(map, WEBGL, listOf(lower(fp.webGlVendor), lower(fp.webGlRenderer)))
        add(map, CANVAS, listOf(if (fp.canvasNoise) "noise:${fp.seed}" else "off"))
        add(map, AUDIO, listOf(if (fp.audioNoise) "noise:${fp.seed}" else "off"))
        add(map, FONTS, listOf(if (fp.fontsNoise) "noise:${fp.seed}" else "off:${fp.fontSet.ifEmpty { "auto" }}"))
        add(map, TIMEZONE, listOf(lower(fp.timezone)))
        add(map, LANGUAGE, listOf(lower(fp.language), lower(fp.acceptLanguage)))
        add(
            map, WEBRTC, listOf(
                if (fp.webRtcProtect) "protect:${fp.webRtcMode.ifEmpty { "proxy" }}" else "off",
                "geo=${fp.geoMode.ifEmpty { "proxy" }}",
            )
        )
        return map
    }

    private fun trimNumber(v: Double): String {
        val s = String.format(Locale.ROOT, "%.3f", v)
        return s.trimEnd('0').trimEnd('.')
    }

    private fun add(map: MutableMap<String, String>, surface: String, parts: List<String>) {
        val display = parts.joinToString(" · ")
        map[surface] = hash(*(listOf(surface) + parts).toTypedArray())
        // The display value is kept alongside so the UI can explain a clash in words.
        map["display:$surface"] = display
    }

    /** One token for the whole fingerprint. */
    fun overallHash(fp: FingerprintConfig): String {
        val map = surfaces(fp)
        return hash(*ALL.map { map[it] ?: "" }.toTypedArray())
    }

    // -----------------------------------------------------------------------------------------
    // Comparison
    // -----------------------------------------------------------------------------------------

    /**
     * Compares two fingerprints.
     *
     * The score is the weighted share of surfaces the two share. The relation is not the
     * score: three heavily weighted surfaces in common is a "Related" pair even when the
     * score is low, because a shared GPU and a shared canvas is a shared machine.
     */
    fun compare(a: FingerprintConfig, b: FingerprintConfig): Comparison {
        val ma = surfaces(a)
        val mb = surfaces(b)
        val shared = ArrayList<String>()
        var sharedWeight = 0
        var strongShared = 0

        for (surface in ALL) {
            val ha = ma[surface] ?: continue
            val hb = mb[surface] ?: continue
            if (ha == hb) {
                shared.add(surface)
                val w = WEIGHTS[surface] ?: 1
                sharedWeight += w
                if (w >= 4 && !isDerived(surface)) strongShared++
            }
        }

        val score = Math.round(100.0 * sharedWeight / TOTAL_WEIGHT).toInt()

        val relation = when {
            shared.size == ALL.size -> Relation.Identical
            score >= 65 -> Relation.Linked
            score >= 40 || strongShared >= 3 -> Relation.Related
            shared.isNotEmpty() -> Relation.Remote
            else -> Relation.Distinct
        }

        return Comparison(relation, score, shared, summarise(relation, shared))
    }

    private fun summarise(relation: Relation, shared: List<String>): String {
        val names = shared.map { (LABELS[it] ?: it).lowercase(Locale.ROOT) }
        val joined = when {
            names.isEmpty() -> ""
            names.size <= 3 -> names.joinToString(", ")
            else -> names.take(2).joinToString(", ") + " and " + (names.size - 2) + " more"
        }
        return when (relation) {
            Relation.Identical -> "Identical fingerprint"
            Relation.Linked -> "Linked, shares $joined"
            Relation.Related -> "Related, shares $joined"
            Relation.Remote -> "Barely related, shares $joined"
            Relation.Distinct -> "Independent"
        }
    }
}
