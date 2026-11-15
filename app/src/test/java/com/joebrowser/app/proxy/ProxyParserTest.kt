package com.joebrowser.app.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The proxy parser has to read what people actually paste.
 */
class ProxyParserTest {

    @Test
    fun `a plain address`() {
        val p = ProxyParser.parseLine("1.2.3.4:8080")
        assertEquals("http", p.type)
        assertEquals("1.2.3.4", p.host)
        assertEquals(8080, p.port)
        assertTrue(p.enabled)
    }

    @Test
    fun `an address with a user and a password`() {
        val p = ProxyParser.parseLine("1.2.3.4:8080:myuser:mypass")
        assertEquals("myuser", p.user)
        assertEquals("mypass", p.password)
        assertEquals(8080, p.port)
    }

    @Test
    fun `a full link`() {
        val p = ProxyParser.parseLine("socks5://myuser:mypass@5.6.7.8:1080")
        assertEquals("socks5", p.type)
        assertEquals("5.6.7.8", p.host)
        assertEquals(1080, p.port)
        assertEquals("myuser", p.user)
        assertEquals("mypass", p.password)
    }

    @Test
    fun `a link with a path`() {
        val p = ProxyParser.parseLine("http://u:p@1.2.3.4:8080/path")
        assertEquals("1.2.3.4", p.host)
        assertEquals("u", p.user)
    }

    @Test
    fun `a password may hold a colon`() {
        val p = ProxyParser.parseLine("http://u:pa:ss@1.2.3.4:8080")
        assertEquals("u", p.user)
        assertEquals("pa:ss", p.password)
    }

    @Test
    fun `a bracketed ipv6 address`() {
        val p = ProxyParser.parseLine("[2001:db8::1]:8080")
        assertEquals("2001:db8::1", p.host)
        assertEquals(8080, p.port)
    }

    @Test
    fun `a full width colon`() {
        val p = ProxyParser.parseLine("host ： 8080")
        assertEquals("host", p.host)
        assertEquals(8080, p.port)
    }

    @Test
    fun `socks4 is spoken as socks5`() {
        assertEquals("socks5", ProxyParser.parseLine("socks4://1.2.3.4:1080").type)
    }

    @Test
    fun `what cannot be a proxy is refused`() {
        assertFalse(ProxyParser.parseLine("1.2.3.4").enabled)
        assertFalse(ProxyParser.parseLine("1.2.3.4:0").enabled)
        assertFalse(ProxyParser.parseLine("1.2.3.4:65536").enabled)
        assertFalse(ProxyParser.parseLine("1.2.3.4:abc").enabled)
        assertFalse(ProxyParser.parseLine(":8080").enabled)
        assertFalse(ProxyParser.parseLine("").enabled)
        assertFalse(ProxyParser.parseLine(null).enabled)
    }

    @Test
    fun `a list is read one line at a time`() {
        val list = ProxyParser.parseMany(
            """
            1.2.3.4:8080
            socks5://u:p@5.6.7.8:1080
            # a comment
            nonsense
            9.9.9.9:3128
            """.trimIndent()
        )
        assertEquals(3, list.size)
        assertEquals("1.2.3.4", list[0].host)
        assertEquals("socks5", list[1].type)
        assertEquals("9.9.9.9", list[2].host)
    }

    @Test
    fun `the full address keeps the credentials and the copy does not need them`() {
        val p = ProxyParser.parseLine("socks5://u:p@1.2.3.4:1080")
        assertEquals("socks5://u:p@1.2.3.4:1080", ProxyParser.fullAddress(p))
        assertEquals("1.2.3.4:1080", ProxyParser.shortAddress(p))
    }
}
