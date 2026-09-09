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
    ): GeneratedConfig {
        val notes = mutableListOf<String>()
        if (node.protocol == Protocol.SSR) {
            notes += "TODO(ssr): SSR outbound is not mapped. Config uses a placeholder vless outbound."
        }
        val allowInsecure = settings.allowInsecureSsl || node.allowInsecure
        val root = buildJsonObject {
            put("log", buildJsonObject {
                put("loglevel", JsonPrimitive("warning"))
            })
            put("dns", chinaDns(enableIpv6))
            put("inbounds", buildJsonArray {
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
            put("routing", chinaRouting())
        }
        return GeneratedConfig(
            json = pretty.encodeToString(JsonObject.serializer(), root),
            socksPort = socksPort,
            notes = notes,
        )
    }

    private fun chinaDns(enableIpv6: Boolean): JsonObject = buildJsonObject {
        put("queryStrategy", JsonPrimitive(if (enableIpv6) "UseIP" else "UseIPv4"))
        put("servers", buildJsonArray {
            add(buildJsonObject {
                put("address", JsonPrimitive("223.5.5.5"))
                put("domains", jsonStrings("geosite:cn"))
                put("expectIPs", jsonStrings("geoip:cn"))
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
            add(JsonPrimitive("localhost"))
        })
    }

    private fun chinaRouting(): JsonObject = buildJsonObject {
        put("domainStrategy", JsonPrimitive("IPIfNonMatch"))
        put("rules", buildJsonArray {
            add(rule(outbound = "direct", ip = listOf("geoip:private")))
            add(rule(outbound = "direct", domain = listOf("geosite:cn")))
            add(rule(outbound = "direct", ip = listOf("geoip:cn")))
            add(rule(outbound = "block", domain = listOf("geosite:category-ads-all")))
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
