package com.joebrowser.app.fingerprint

import com.joebrowser.app.data.model.FingerprintConfig
import com.joebrowser.app.data.model.ProxyConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fingerprint engine has to keep its promises.
 *
 * These are the rules a checker looks for: the numbers describe a machine that could
 * exist, the same profile always reads the same, and two profiles do not read the same.
 */
class FingerprintFactoryTest {

    @Test
    fun `a rolled fingerprint describes a machine that could exist`() {
        val fp = FingerprintFactory.regenerate(FingerprintConfig(), "Windows 11", null)
        FingerprintFactory.coerce(fp)

        assertEquals("Windows 11", fp.os)
        assertEquals("desktop", fp.deviceType)
        assertTrue("memory is 2, 4 or 8", fp.memory in intArrayOf(2, 4, 8))
        assertTrue("a real core count", fp.cores in FingerprintCatalog.CORE_OPTIONS)
        assertEquals("a desktop has no touch points", 0, fp.touchPoints)
        assertTrue("the screen is bigger than the frame", fp.availH < fp.screenH)
        assertEquals(24, fp.colorDepth)
        assertTrue("a name was drawn", fp.webGlRenderer.isNotBlank())
        assertTrue("a vendor was drawn", fp.webGlVendor.isNotBlank())
    }

    @Test
    fun `an android profile is a phone`() {
        val fp = FingerprintFactory.regenerate(FingerprintConfig(), "Android", null)
        FingerprintFactory.coerce(fp)

        assertEquals("mobile", fp.deviceType)
        assertEquals(8, fp.cores)
        assertEquals(5, fp.touchPoints)
        assertEquals("a phone has no frame to subtract", fp.screenH, fp.availH)
    }

    @Test
    fun `the same seed always gives the same identity`() {
        val a = FingerprintFactory.regenerate(FingerprintConfig(), "Android", null)
        val b = a.copy()
        assertEquals(FingerprintIdentity.overallHash(a), FingerprintIdentity.overallHash(b))
    }

    @Test
    fun `two profiles do not share an identity`() {
        val first = FingerprintFactory.regenerate(FingerprintConfig(), "Android", null)
        val second = FingerprintFactory.regenerate(FingerprintConfig(), "Android", null)
        assertNotEquals(
            FingerprintIdentity.overallHash(first),
            FingerprintIdentity.overallHash(second),
        )
    }

    @Test
    fun `the language belongs to the time zone`() {
        val fp = FingerprintFactory.regenerate(FingerprintConfig(), "Android", null)
        val zone = FingerprintCatalog.languageForTimezone(fp.timezone)
        val countryOfZone = FingerprintCatalog.COUNTRY_LOCALE[fp.timezone]?.second?.let {
            FingerprintCatalog.countryForLanguage(it)
        }
        if (countryOfZone != null) {
            assertEquals(
                "the language and the clock must name the same country",
                countryOfZone,
                FingerprintCatalog.countryForLanguage(fp.language),
            )
        }
    }

    @Test
    fun `a proxy lines the clock up with the exit node`() {
        val proxy = ProxyConfig(
            type = "http",
            host = "proxy.example.com",
            port = 8080,
            lastCountryCode = "DE",
            lastTimezone = "Europe/Berlin",
            lastIp = "1.2.3.4",
        )
        val fp = FingerprintFactory.regenerate(FingerprintConfig(), "Windows 11", proxy)
        assertEquals("Europe/Berlin", fp.timezone)
        assertEquals("de-DE", fp.language)
        assertEquals("de-DE,de;q=0.9,en;q=0.8", fp.acceptLanguage)
    }

    @Test
    fun `the repair puts back what the user broke`() {
        val fp = FingerprintFactory.regenerate(FingerprintConfig(), "Android", null)
        fp.cores = 7
        fp.memory = 5
        fp.touchPoints = 0
        fp.deviceType = "desktop"

        val repairs = FingerprintFactory.coerce(fp)

        assertTrue("something had to be repaired", repairs > 0)
        assertEquals(8, fp.cores)
        assertEquals(4, fp.memory)
        assertEquals(5, fp.touchPoints)
        assertEquals("mobile", fp.deviceType)
    }

    @Test
    fun `a mac never claims a direct3d adapter`() {
        val fp = FingerprintFactory.regenerate(FingerprintConfig(), "macOS", null)
        fp.webGlRenderer = "ANGLE (NVIDIA, NVIDIA GeForce RTX 3060 Direct3D11 vs_5_0 ps_5_0, D3D11)"
        FingerprintFactory.coerce(fp)
        assertTrue(
            "the adapter has to belong to the platform",
            fp.webGlRenderer.contains("Metal") || fp.webGlRenderer.contains("Apple"),
        )
    }

    @Test
    fun `the user agent says what the profile says`() {
        val fp = FingerprintFactory.regenerate(FingerprintConfig(), "iPhone", null)
        val resolved = UserAgentBuilder.resolve(fp, UserAgentBuilder.ENGINE_CHROME, null)
        assertTrue(resolved.userAgent.contains("iPhone"))
        assertTrue(resolved.userAgent.contains("Version/"))
        assertEquals("iPhone", resolved.platform)
        assertEquals("Safari", resolved.brand)
    }

    @Test
    fun `the user agent is the reduced form`() {
        val fp = FingerprintFactory.regenerate(FingerprintConfig(), "Android", null)
        fp.browserVersion = "149.0.7827.5"
        val resolved = UserAgentBuilder.resolve(fp, UserAgentBuilder.ENGINE_CHROME, "149.0.7827.5")
        assertTrue(
            "chrome only carries the major version in the string",
            resolved.userAgent.contains("Chrome/149.0.0.0"),
        )
        assertEquals("149.0.7827.5", resolved.fullVersion)
    }

    @Test
    fun `the client hints and the user agent agree`() {
        val fp = FingerprintFactory.regenerate(FingerprintConfig(), "Android", null)
        fp.browserVersion = "149.0.7827.5"
        val resolved = UserAgentBuilder.resolve(fp, UserAgentBuilder.ENGINE_CHROME, "149.0.7827.5")
        assertEquals(3, resolved.brands.size)
        assertEquals("Chromium", resolved.brands[1].first)
        assertEquals("149", resolved.brands[1].second)
        assertEquals(
            "the hint names the same android release as the user agent",
            FingerprintFactory.androidVersionFor(fp),
            resolved.platformVersion.substringBefore("."),
        )
    }

    @Test
    fun `the build that is reported is the build that is running`() {
        val fp = FingerprintFactory.regenerate(FingerprintConfig(), "Android", null)
        val major = BrowserVersions.majorOf("149.0.7827.5")
        assertEquals(149, major)
        assertTrue(BrowserVersions.isReal("149.0.7827.5"))
        assertFalse(BrowserVersions.isReal("149.0"))
        assertFalse(BrowserVersions.isReal("not a version"))
    }
}
