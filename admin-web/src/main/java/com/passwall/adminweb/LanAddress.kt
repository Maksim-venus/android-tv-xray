package com.passwall.adminweb

import java.net.Inet4Address
import java.net.NetworkInterface

object LanAddress {
    fun ipv4(): String? {
        val candidates = mutableListOf<String>()
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        for (nic in interfaces) {
            if (!nic.isUp || nic.isLoopback) continue
            val addrs = nic.inetAddresses
            while (addrs.hasMoreElements()) {
                val addr = addrs.nextElement()
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    val host = addr.hostAddress ?: continue
                    if (host.startsWith("192.168.") || host.startsWith("10.") ||
                        host.matches(Regex("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*"))
                    ) {
                        return host
                    }
                    candidates += host
                }
            }
        }
        return candidates.firstOrNull()
    }

    fun httpUrl(port: Int): String {
        val ip = ipv4() ?: "127.0.0.1"
        return "http://$ip:$port"
    }
}
