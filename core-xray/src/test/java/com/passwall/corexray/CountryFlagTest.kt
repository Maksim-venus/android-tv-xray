package com.passwall.corexray

import org.junit.Assert.assertEquals
import org.junit.Test

class CountryFlagTest {
    @Test
    fun japanIsRegionalIndicators() {
        assertEquals(jpFlag(), CountryFlag.fromCountryCode("JP"))
        assertEquals(jpFlag(), CountryFlag.fromCountryCode("jp"))
        assertEquals(jpFlag(), CountryFlag.fromCountryCode(" jp "))
    }

    @Test
    fun unitedStates() {
        assertEquals(usFlag(), CountryFlag.fromCountryCode("US"))
    }

    @Test
    fun unknownIsGlobe() {
        assertEquals(CountryFlag.UNKNOWN, CountryFlag.fromCountryCode(null))
        assertEquals(CountryFlag.UNKNOWN, CountryFlag.fromCountryCode(""))
        assertEquals(CountryFlag.UNKNOWN, CountryFlag.fromCountryCode("J"))
        assertEquals(CountryFlag.UNKNOWN, CountryFlag.fromCountryCode("JPN"))
        assertEquals(CountryFlag.UNKNOWN, CountryFlag.fromCountryCode("1A"))
    }

    private fun jpFlag() = flag('J', 'P')
    private fun usFlag() = flag('U', 'S')

    private fun flag(a: Char, b: Char): String = buildString {
        appendCodePoint(0x1F1E6 + (a - 'A'))
        appendCodePoint(0x1F1E6 + (b - 'A'))
    }
}
