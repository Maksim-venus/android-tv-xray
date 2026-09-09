package com.passwall.corexray

data class IpInfo(
    val ip: String,
    val country: String? = null,
) {
    val flag: String get() = CountryFlag.fromCountryCode(country)
}

/** Parses ipinfo.io JSON, `/ip` text, or a 2-letter `/country` body. */
object IpInfoParser {
    private val IP_JSON = Regex("\"ip\"\\s*:\\s*\"([^\"]+)\"")
    private val COUNTRY_JSON = Regex("\"country\"\\s*:\\s*\"([A-Za-z]{2})\"")

    fun parse(body: String): IpInfo? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null
        val jsonIp = IP_JSON.find(trimmed)?.groupValues?.get(1)?.trim()
        val jsonCountry = COUNTRY_JSON.find(trimmed)?.groupValues?.get(1)?.uppercase()
        if (!jsonIp.isNullOrBlank() && looksLikeIp(jsonIp)) {
            return IpInfo(jsonIp, jsonCountry)
        }
        val first = trimmed.lineSequence().firstOrNull()?.trim().orEmpty()
        if (looksLikeIp(first)) return IpInfo(first, jsonCountry)
        return null
    }

    fun parseCountry(body: String): String? {
        val jsonCountry = COUNTRY_JSON.find(body)?.groupValues?.get(1)?.uppercase()
        if (jsonCountry != null) return jsonCountry
        val first = body.trim().lineSequence().firstOrNull()?.trim()?.uppercase().orEmpty()
        return first.takeIf { it.length == 2 && it.all { ch -> ch in 'A'..'Z' } }
    }

    fun looksLikeIp(value: String): Boolean {
        val s = value.trim()
        if (s.isEmpty() || s.length > 45) return false
        if (s.contains('.')) {
            val parts = s.split('.')
            return parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }
        }
        if (!s.contains(':')) return false
        return s.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' }
    }
}
