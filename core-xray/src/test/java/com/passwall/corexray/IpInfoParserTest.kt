package com.passwall.corexray

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpInfoParserTest {
    @Test
    fun parsesIpinfoJson() {
        val body = """{"ip":"203.0.113.10","hostname":"x","country":"JP","city":"Tokyo"}"""
        val info = IpInfoParser.parse(body)!!
        assertEquals("203.0.113.10", info.ip)
        assertEquals("JP", info.country)
        assertEquals(CountryFlag.fromCountryCode("JP"), info.flag)
    }

    @Test
    fun parsesBareIp() {
        val info = IpInfoParser.parse("1.2.3.4\n")!!
        assertEquals("1.2.3.4", info.ip)
        assertNull(info.country)
        assertEquals(CountryFlag.UNKNOWN, info.flag)
    }

    @Test
    fun parsesCountryBody() {
        assertEquals("US", IpInfoParser.parseCountry("us\n"))
        assertEquals("DE", IpInfoParser.parseCountry("""{"country":"de"}"""))
        assertNull(IpInfoParser.parseCountry("not-a-country"))
    }

    @Test
    fun rejectsGarbage() {
        assertNull(IpInfoParser.parse(""))
        assertNull(IpInfoParser.parse("not json"))
        assertTrue(IpInfoParser.looksLikeIp("2001:db8::1"))
        assertTrue(!IpInfoParser.looksLikeIp("999.1.1.1"))
    }
}
