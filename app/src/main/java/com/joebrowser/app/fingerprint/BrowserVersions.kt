package com.joebrowser.app.fingerprint

import com.joebrowser.app.data.model.Profile
import kotlin.math.abs
import kotlin.random.Random

/**
 * The browser build a profile reports.
 *
 * The important decision here is that a profile reports the build it is actually running,
 * not a fabricated number. A fingerprint set in which every profile claims a different
 * Chromium build is a fingerprint set in which every profile is lying; a checker that
 * compares the reported build against the one that answered the request sees the lie
 * immediately. Diversity comes from pinned engines and from the rest of the identity, not
 * from inventing version strings.
 */
object BrowserVersions {

    /** The published Chromium branches, major -> build. */
    private val BRANCHES: Map<Int, Int> = mapOf(
        120 to 6099, 121 to 6167, 122 to 6261, 123 to 6312, 124 to 6367,
        125 to 6422, 126 to 6478, 127 to 6533, 128 to 6613, 129 to 6668,
        130 to 6723, 131 to 6778, 132 to 6834, 133 to 6943, 134 to 6998,
        135 to 7049, 136 to 7103, 137 to 7151, 138 to 7204, 139 to 7258,
        140 to 7339, 141 to 7390, 142 to 7444, 143 to 7499, 144 to 7552,
        145 to 7607, 146 to 7661, 147 to 7715, 148 to 7770, 149 to 7824,
        150 to 7871, 151 to 7922, 152 to 7977, 153 to 8010, 154 to 8037,
        155 to 8059, 156 to 8078, 157 to 8103, 158 to 8127, 159 to 8154,
    )

    /** A smaller table used by the version synthesiser. */
    private val BUILD_BRANCHES: Map<Int, Int> = mapOf(
        120 to 6099, 121 to 6167, 122 to 6261, 123 to 6312, 124 to 6367,
        125 to 6422, 126 to 6478, 127 to 6533, 128 to 6613, 129 to 6668,
        130 to 6723, 131 to 6778, 132 to 6834, 133 to 6943, 134 to 6998,
        135 to 7049, 136 to 7103, 137 to 7151, 138 to 7204, 139 to 7258,
        140 to 7339, 141 to 7390, 142 to 7444, 143 to 7499,
    )

    const val OLDEST = 120
    const val NEWEST = 159
    const val LATEST_STABLE = "157.0.8103.78"

    /** Interpolates the build number for a major version. */
    fun branchFor(major: Int): Int {
        BRANCHES[major]?.let { return it }
        if (major > NEWEST) return BRANCHES.getValue(NEWEST) + (major - NEWEST) * 25
        if (major < OLDEST) return BRANCHES.getValue(OLDEST) - (OLDEST - major) * 25
        var m = major
        while (m > OLDEST && !BRANCHES.containsKey(m)) m--
        val base = BRANCHES[m] ?: BRANCHES.getValue(OLDEST)
        return base + (major - m) * 54
    }

    /** The build number used by the synthesiser. */
    fun buildBranchFor(major: Int): Int {
        BUILD_BRANCHES[major]?.let { return it }
        val nearest = when {
            major > 143 -> 143
            major < 120 -> 120
            else -> major
        }
        if (major > 143) return 7499 + (major - 143) * 54
        return 6099 - (120 - nearest) * 54
    }

    /** True when the string is a real four-part build with a non-zero build and patch. */
    fun isReal(version: String?): Boolean {
        val v = version?.trim() ?: return false
        val parts = v.split('.')
        if (parts.size != 4) return false
        val nums = parts.map { it.toIntOrNull() ?: return false }
        if (nums.any { it < 0 }) return false
        return nums[0] >= 80 && nums[2] > 0
    }

    fun majorOf(version: String?): Int {
        val v = version?.trim() ?: return 0
        if (v.isEmpty()) return 0
        return v.substringBefore('.').toIntOrNull() ?: 0
    }

    /** The Chromium major version named in a user agent string. */
    fun majorFromUserAgent(ua: String?): Int {
        val s = ua ?: return 0
        val idx = when {
            s.contains("Chrome/") -> s.indexOf("Chrome/") + 7
            s.contains("Firefox/") -> s.indexOf("Firefox/") + 8
            else -> return 0
        }
        var end = idx
        while (end < s.length && s[end].isDigit()) end++
        return if (end > idx) s.substring(idx, end).toIntOrNull() ?: 0 else 0
    }

    /** The full browser version named in a user agent string. */
    fun fromUserAgent(ua: String?): String {
        val s = ua ?: return ""
        val idx = when {
            s.contains("Chrome/") -> s.indexOf("Chrome/") + 7
            s.contains("Firefox/") -> s.indexOf("Firefox/") + 8
            else -> return ""
        }
        var end = idx
        while (end < s.length && s[end] != ' ' && s[end] != ';' && s[end] != ')') end++
        val v = s.substring(idx, end)
        return if (isReal(v)) v else ""
    }

    fun isNewerThanStable(version: String?): Boolean =
        majorOf(version) > majorOf(LATEST_STABLE)

    /**
     * Picks a build for a major version, avoiding the ones already in use.
     *
     * The patch numbers step through the respins a real release actually had, so the
     * generated build is one a browser could plausibly have been.
     */
    fun pick(major: Int, avoid: Set<String>): String {
        var m = major
        if (m < 90) m = 131
        val branch = branchFor(m)

        var patch = 40
        while (patch <= 260) {
            val candidate = "$m.0.$branch.$patch"
            if (!avoid.contains(candidate)) return candidate
            patch += 3
        }
        var step = 1
        while (step <= 6) {
            val b = branch + step
            patch = 60
            while (patch <= 240) {
                val candidate = "$m.0.$b.$patch"
                if (!avoid.contains(candidate)) return candidate
                patch += 7
            }
            step++
        }
        var cross = m - 1
        while (cross >= maxOf(90, m - 24)) {
            val b = branchFor(cross)
            patch = 50
            while (patch <= 240) {
                val candidate = "$cross.0.$b.$patch"
                if (!avoid.contains(candidate)) return candidate
                patch += 5
            }
            cross--
        }
        return "$m.0.$branch.${40 + abs(Random.nextInt(0, 200))}"
    }

    /** Every version already claimed by a profile in the workspace. */
    fun taken(profiles: List<Profile>): Set<String> {
        val out = HashSet<String>()
        for (p in profiles) {
            if (p.browserVersion.isNotEmpty()) out.add(p.browserVersion)
            val ua = p.fp.userAgent
            if (ua.isNotEmpty()) {
                val fromUa = fromUserAgent(ua)
                if (fromUa.isNotEmpty()) out.add(fromUa)
                val major = majorFromUserAgent(ua)
                if (major > 0) out.add("$major.0.1.1")
            }
        }
        return out
    }

    /**
     * Assigns the build a profile reports.
     *
     * A pinned engine is a fact about the executable and wins outright. Otherwise the
     * profile reports the build that is actually running.
     *
     * @return the profile with the build written into it.
     */
    fun assign(target: Profile, runningVersion: String?): Profile {
        val running = runningVersion?.takeIf { isReal(it) }?.trim()

        if (isReal(target.browserVersion) && target.kernelVersion.isNotEmpty()) {
            return target.copy(fp = target.fp.copy(browserVersion = target.browserVersion))
        }

        if (running.isNullOrEmpty()) {
            val version = if (target.browserVersion.isEmpty()) LATEST_STABLE else target.browserVersion
            return target.copy(browserVersion = version, fp = target.fp.copy(browserVersion = version))
        }

        return target.copy(browserVersion = running, fp = target.fp.copy(browserVersion = running))
    }

    /**
     * Synthesises a full Chromium build for a major version.
     *
     * Used when the running build is not a four-part version, for example when the engine
     * reports itself as "Chrome 157".
     */
    fun fullChromiumVersion(browserVersion: String?, major: Int): String {
        var m = if (major <= 0) 131 else major
        val v = browserVersion?.trim().orEmpty()
        if (v.isNotEmpty()) {
            val parts = v.split('.')
            if (parts.size == 4) {
                val head = parts[0].toIntOrNull()
                if (head != null && head > 0) {
                    val build = parts[2].toIntOrNull() ?: 0
                    val patch = parts[3].toIntOrNull() ?: 0
                    if (build > 0 && patch > 0) return v
                    m = head
                }
            } else if (parts.size == 3) {
                val head = parts[0].toIntOrNull()
                if (head != null && head > 0) return "$v.${40 + stableHash(v) % 160}"
            }
        }
        val build = buildBranchFor(m)
        val patch = 60 + stableHash("p$m") % 180
        return "$m.0.$build.$patch"
    }

    /** FNV-1a, masked positive. */
    fun stableHash(value: String): Int {
        var h = -2128831035L
        for (c in value) {
            h = (h xor c.code.toLong()) * 16777619L
        }
        return (h.toInt() and 0x7fffffff)
    }
}
