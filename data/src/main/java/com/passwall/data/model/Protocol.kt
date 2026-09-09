package com.passwall.data.model

enum class Protocol(val wireName: String, val badge: String) {
    VLESS("vless", "VLESS"),
    VMESS("vmess", "VMess"),
    SHADOWSOCKS("ss", "SS"),
    TROJAN("trojan", "Trojan"),
    HYSTERIA2("hysteria2", "Hysteria2"),
    SSR("ssr", "SSR"),
    UNKNOWN("unknown", "?");

    companion object {
        fun fromWire(value: String): Protocol =
            entries.firstOrNull { it.wireName.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}
