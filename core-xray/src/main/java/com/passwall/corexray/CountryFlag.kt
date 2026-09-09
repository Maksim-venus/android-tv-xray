package com.passwall.corexray

/** ISO 3166-1 alpha-2 → regional-indicator flag emoji (JP → 🇯🇵). */
object CountryFlag {
    const val UNKNOWN = "🌐"
    private const val REGIONAL_A = 0x1F1E6

    fun fromCountryCode(code: String?): String {
        val cc = code?.trim()?.uppercase().orEmpty()
        if (cc.length != 2) return UNKNOWN
        val a = cc[0]
        val b = cc[1]
        if (a !in 'A'..'Z' || b !in 'A'..'Z') return UNKNOWN
        return buildString {
            appendCodePoint(REGIONAL_A + (a - 'A'))
            appendCodePoint(REGIONAL_A + (b - 'A'))
        }
    }
}
