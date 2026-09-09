package com.passwall.corexray

import com.passwall.data.model.AppSettings
import com.passwall.data.model.Protocol
import com.passwall.data.model.ProxyNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

data class GeneratedConfig(
    val json: String,
    val socksPort: Int = DEFAULT_SOCKS_PORT,
    val inboundTag: String = "socks-in",
    val notes: List<String> = emptyList(),
    val usedGeosite: Boolean = true,
    val usedGeoip: Boolean = true,
)

object XrayPorts {
    const val DEFAULT_SOCKS_PORT = 10808
}

const val DEFAULT_SOCKS_PORT = XrayPorts.DEFAULT_SOCKS_PORT

/**
 * Builds an Xray-core JSON config with ChinaDNS-style split tunneling:
 * geosite:cn / geoip:cn / private → freedom (direct); everything else → proxy.
 */
object XrayConfigGenerator {
    private val pretty = Json { prettyPrint = true }

    fun generate(
        node: ProxyNode,
        settings: AppSettings,
        socksPort: Int = DEFAULT_SOCKS_PORT,
        enableIpv6: Boolean = false,
        health: GeodataHealth = GeodataHealth(geositeOk = true, geoipOk = true),
        extraDirectIps: List<String> = emptyList(),
    ): GeneratedConfig {
        val notes = mutableListOf<String>()
        if (node.protocol == Protocol.SSR) {
            notes += "TODO(ssr): SSR outbound is not mapped. Config uses a placeholder vless outbound."
        }
        if (!health.geositeOk) {
            notes += "geosite.dat 无效（${health.geositeError}），已改为 IP 分流，不用 geosite:cn。"
        }
        if (!health.geoipOk) {
            notes += "geoip.dat 无效（${health.geoipError}），直连改用 cn-cidr 列表。"
        }
        val allowInsecure = settings.allowInsecureSsl || node.allowInsecure
        val root = buildJsonObject {
            put("log", buildJsonObject {
                put("loglevel", JsonPrimitive("warning"))
            })
            put("dns", chinaDns(enableIpv6, health.geositeOk, health.geoipOk))
            put("inbounds", buildJsonArray {
                add(tunInbound())
                add(socksInbound(socksPort))
            })
            put("outbounds", buildJsonArray {
                add(proxyOutbound(node, allowInsecure, notes))
                add(buildJsonObject {
                    put("tag", JsonPrimitive("direct"))
                    put("protocol", JsonPrimitive("freedom"))
                    put("settings", buildJsonObject {
                        put("domainStrategy", JsonPrimitive("UseIP"))
                    })
                })
                add(buildJsonObject {
                    put("tag", JsonPrimitive("block"))
                    put("protocol", JsonPrimitive("blackhole"))
                    put("settings", buildJsonObject {
                        put("response", buildJsonObject {
                            put("type", JsonPrimitive("http"))
                        })
                    })
                })
            })
            put("routing", chinaRouting(health, extraDirectIps))
        }
        return GeneratedConfig(
            json = pretty.encodeToString(JsonObject.serializer(), root),
            socksPort = socksPort,
            notes = notes,
            usedGeosite = health.geositeOk,
            usedGeoip = health.geoipOk,
        )
    }

    /** Stamp VpnService TUN fd into the Xray JSON `env` object (also set by startLoop). */
    fun injectTunFd(json: String, fd: Int): String {
        val parsed = Json.parseToJsonElement(json)
        val root = parsed as? JsonObject ?: return json
        val env = buildJsonObject {
            (root["env"] as? JsonObject)?.forEach { (k, v) -> put(k, v) }
            put("xray.tun.fd", JsonPrimitive(fd.toString()))
        }
        return JsonObject(root + ("env" to env)).toString()
    }

    private fun chinaDns(enableIpv6: Boolean, geositeOk: Boolean, geoipOk: Boolean): JsonObject = buildJsonObject {
        put("queryStrategy", JsonPrimitive(if (enableIpv6) "UseIP" else "UseIPv4"))
        put("servers", buildJsonArray {
            if (geositeOk) {
                add(buildJsonObject {
                    put("address", JsonPrimitive("223.5.5.5"))
                    put("domains", jsonStrings("geosite:cn"))
                    if (geoipOk) put("expectIPs", jsonStrings("geoip:cn"))
                })
                add(buildJsonObject {
                    put("address", JsonPrimitive("119.29.29.29"))
                    put("domains", jsonStrings("geosite:cn"))
                })
                add(buildJsonObject {
                    put("address", JsonPrimitive("8.8.8.8"))
                    put("domains", jsonStrings("geosite:geolocation-!cn"))
                })
                add(buildJsonObject {
                    put("address", JsonPrimitive("1.1.1.1"))
                    put("domains", jsonStrings("geosite:geolocation-!cn"))
                })
            } else {
                add(JsonPrimitive("223.5.5.5"))
                add(JsonPrimitive("8.8.8.8"))
            }
            add(JsonPrimitive("localhost"))
        })
    }

    private fun chinaRouting(health: GeodataHealth, extraDirectIps: List<String>): JsonObject = buildJsonObject {
        put("domainStrategy", JsonPrimitive("IPIfNonMatch"))
        put("rules", buildJsonArray {
            if (health.geoipOk) {
                add(rule(outbound = "direct", ip = listOf("geoip:private")))
            } else {
                add(rule(outbound = "direct", ip = listOf("geoip:private", "127.0.0.0/8", "10.0.0.0/8", "192.168.0.0/16")))
            }
            if (health.geositeOk) {
                add(rule(outbound = "direct", domain = listOf("geosite:cn")))
            }
            if (health.geoipOk) {
                add(rule(outbound = "direct", ip = listOf("geoip:cn")))
            } else if (extraDirectIps.isNotEmpty()) {
                extraDirectIps.chunked(500).forEach { chunk ->
                    add(rule(outbound = "direct", ip = chunk))
                }
            }
            if (health.geositeOk) {
                add(rule(outbound = "block", domain = listOf("geosite:category-ads-all")))
            }
            add(rule(outbound = "proxy", network = "tcp,udp"))
        })
    }

    private fun rule(
        outbound: String,
        domain: List<String>? = null,
        ip: List<String>? = null,
        network: String? = null,
    ): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("field"))
        put("outboundTag", JsonPrimitive(outbound))
        if (domain != null) put("domain", jsonStrings(*domain.toTypedArray()))
        if (ip != null) put("ip", jsonStrings(*ip.toTypedArray()))
        if (network != null) put("network", JsonPrimitive(network))
    }

    private fun tunInbound(): JsonObject = buildJsonObject {
        put("tag", JsonPrimitive("tun-in"))
        put("port", JsonPrimitive(0))
        put("protocol", JsonPrimitive("tun"))
        put("settings", buildJsonObject {
            put("name", JsonPrimitive("passwall0"))
            put("mtu", JsonPrimitive(1500))
        })
        put("sniffing", buildJsonObject {
            put("enabled", JsonPrimitive(true))
            put("destOverride", jsonStrings("http", "tls", "quic"))
            put("routeOnly", JsonPrimitive(true))
        })
    }

    private fun socksInbound(port: Int): JsonObject = buildJsonObject {
        put("tag", JsonPrimitive("socks-in"))
        put("listen", JsonPrimitive("127.0.0.1"))
        put("port", JsonPrimitive(port))
        put("protocol", JsonPrimitive("socks"))
        put("settings", buildJsonObject {
            put("udp", JsonPrimitive(true))
            put("auth", JsonPrimitive("noauth"))
        })
        put("sniffing", buildJsonObject {
            put("enabled", JsonPrimitive(true))
            put("destOverride", jsonStrings("http", "tls", "quic"))
            put("routeOnly", JsonPrimitive(true))
        })
    }

    private fun proxyOutbound(
        node: ProxyNode,
        allowInsecure: Boolean,
        notes: MutableList<String>,
    ): JsonObject {
        val protocol = when (node.protocol) {
            Protocol.VLESS -> "vless"
            Protocol.VMESS -> "vmess"
            Protocol.TROJAN -> "trojan"
            Protocol.SHADOWSOCKS -> "shadowsocks"
            Protocol.SSR, Protocol.HYSTERIA2, Protocol.UNKNOWN -> {
                notes += "Protocol ${node.protocol.badge} is stubbed; emitting vless placeholder outbound."
                "vless"
            }
        }
        return buildJsonObject {
            put("tag", JsonPrimitive("proxy"))
            put("protocol", JsonPrimitive(protocol))
            put("settings", when (protocol) {
                "vmess", "vless" -> buildJsonObject {
                    put("vnext", buildJsonArray {
                        add(buildJsonObject {
                            put("address", JsonPrimitive(node.host))
                            put("port", JsonPrimitive(node.port))
                            put("users", buildJsonArray {
                                add(buildJsonObject {
                                    put("id", JsonPrimitive(node.uuid ?: "00000000-0000-0000-0000-000000000000"))
                                    if (protocol == "vless") {
                                        put("encryption", JsonPrimitive(node.encryption ?: "none"))
                                        node.flow?.let { put("flow", JsonPrimitive(it)) }
                                    } else {
                                        put("alterId", JsonPrimitive(node.alterId))
                                        put("security", JsonPrimitive(node.encryption ?: "auto"))
                                    }
                                })
                            })
                        })
                    })
                }
                else -> buildJsonObject {}
            })
            put("streamSettings", streamSettings(node, allowInsecure))
        }
    }

    private fun streamSettings(node: ProxyNode, allowInsecure: Boolean): JsonObject = buildJsonObject {
        put("network", JsonPrimitive(node.network.ifBlank { "tcp" }))
        put("security", JsonPrimitive(node.security.ifBlank { "none" }))
        if (node.security.equals("tls", true)) {
            put("tlsSettings", buildJsonObject {
                put("serverName", JsonPrimitive(node.sni ?: node.host))
                put("allowInsecure", JsonPrimitive(allowInsecure))
                node.fingerprint?.let { put("fingerprint", JsonPrimitive(it)) }
            })
        }
        if (node.security.equals("reality", true)) {
            put("realitySettings", buildJsonObject {
                put("serverName", JsonPrimitive(node.sni ?: node.host))
                put("fingerprint", JsonPrimitive(node.fingerprint ?: "chrome"))
                put("publicKey", JsonPrimitive(node.publicKey ?: ""))
                put("shortId", JsonPrimitive(node.shortId ?: ""))
                put("spiderX", JsonPrimitive(node.spiderX ?: "/"))
            })
        }
        if (node.network.equals("ws", true)) {
            put("wsSettings", buildJsonObject {
                put("path", JsonPrimitive(node.path ?: "/"))
                put("headers", buildJsonObject {
                    put("Host", JsonPrimitive(node.hostHeader ?: node.sni ?: node.host))
                })
            })
        }
        if (node.network.equals("grpc", true)) {
            put("grpcSettings", buildJsonObject {
                put("serviceName", JsonPrimitive(node.path ?: ""))
            })
        }
    }

    private fun jsonStrings(vararg values: String): JsonArray = buildJsonArray {
        values.forEach { add(JsonPrimitive(it)) }
    }
}
