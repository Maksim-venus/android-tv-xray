package com.passwall.data.parser

import com.passwall.data.model.NodeSource
import com.passwall.data.model.Protocol
import com.passwall.data.model.ProxyNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

data class ParseResult(
    val nodes: List<ProxyNode>,
    val errors: List<String> = emptyList(),
)

object ShareLinkParser {

    fun parseBatch(text: String, source: NodeSource = NodeSource.MANUAL): ParseResult {
        val nodes = mutableListOf<ProxyNode>()
        val errors = mutableListOf<String>()
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { line ->
                runCatching { parseOne(line, source) }
                    .onSuccess { node ->
                        if (node != null) nodes += node
                        else errors += "未识别的链接: ${line.take(48)}"
                    }
                    .onFailure { errors += (it.message ?: it.toString()) }
            }
        return ParseResult(nodes, errors)
    }

    fun parseOne(link: String, source: NodeSource = NodeSource.MANUAL): ProxyNode? {
        val trimmed = link.trim()
        return when {
            trimmed.startsWith("vless://", ignoreCase = true) -> VlessParser.parse(trimmed, source)
            trimmed.startsWith("vmess://", ignoreCase = true) -> VmessParser.parse(trimmed, source)
            trimmed.startsWith("ss://", ignoreCase = true) ->
                throw UnsupportedOperationException("Shadowsocks 解析尚未实现: $trimmed")
            trimmed.startsWith("trojan://", ignoreCase = true) ->
                throw UnsupportedOperationException("Trojan 解析尚未实现: $trimmed")
            trimmed.startsWith("hysteria2://", ignoreCase = true) ||
                trimmed.startsWith("hy2://", ignoreCase = true) ->
                throw UnsupportedOperationException("Hysteria2 解析尚未实现: $trimmed")
            trimmed.startsWith("ssr://", ignoreCase = true) -> SsrParser.parse(trimmed, source)
            else -> null
        }
    }
}

object VlessParser {
    fun parse(link: String, source: NodeSource = NodeSource.MANUAL): ProxyNode {
        val raw = link.removePrefix("vless://")
        val hashIndex = raw.indexOf('#')
        val namePart = if (hashIndex >= 0) raw.substring(hashIndex + 1) else ""
        val body = if (hashIndex >= 0) raw.substring(0, hashIndex) else raw
        val queryIndex = body.indexOf('?')
        val userHost = if (queryIndex >= 0) body.substring(0, queryIndex) else body
        val query = if (queryIndex >= 0) body.substring(queryIndex + 1) else ""
        val at = userHost.lastIndexOf('@')
        require(at > 0) { "无效的 vless:// 链接：缺少 uuid@host" }
        val uuid = decode(userHost.substring(0, at))
        val hostPort = userHost.substring(at + 1)
        val (host, port) = splitHostPort(hostPort, 443)
        val params = parseQuery(query)
        val name = decode(namePart).ifBlank { host }
        return ProxyNode(
            name = name,
            protocol = Protocol.VLESS,
            host = host,
            port = port,
            uuid = uuid,
            security = params["security"] ?: "none",
            network = params["type"] ?: "tcp",
            path = params["path"],
            hostHeader = params["host"],
            sni = params["sni"] ?: params["host"],
            flow = params["flow"],
            encryption = params["encryption"] ?: "none",
            fingerprint = params["fp"],
            publicKey = params["pbk"],
            shortId = params["sid"],
            spiderX = params["spx"],
            allowInsecure = params["allowInsecure"] == "1" || params["allowInsecure"] == "true",
            rawLink = link,
            source = source,
        )
    }
}

object VmessParser {
    fun parse(link: String, source: NodeSource = NodeSource.MANUAL): ProxyNode {
        val payload = link.removePrefix("vmess://").trim()
        val json = decodeBase64Json(payload)
        val host = json.str("add").ifBlank { json.str("host") }
        require(host.isNotBlank()) { "无效的 vmess:// 链接：缺少地址" }
        val port = json.str("port").toIntOrNull() ?: 443
        val name = json.str("ps").ifBlank { host }
        val tls = json.str("tls")
        val security = when {
            tls.equals("tls", true) || tls == "1" -> "tls"
            tls.equals("reality", true) -> "reality"
            else -> "none"
        }
        return ProxyNode(
            name = name,
            protocol = Protocol.VMESS,
            host = host,
            port = port,
            uuid = json.str("id").ifBlank { null },
            alterId = json.str("aid").toIntOrNull() ?: 0,
            security = security,
            network = json.str("net").ifBlank { "tcp" },
            path = json.str("path").ifBlank { null },
            hostHeader = json.str("host").ifBlank { null },
            sni = json.str("sni").ifBlank { json.str("host").ifBlank { null } },
            encryption = json.str("scy").ifBlank { "auto" },
            fingerprint = json.str("fp").ifBlank { null },
            rawLink = link,
            source = source,
        )
    }
}

/**
 * SSR is intentionally stubbed. Share links are accepted so the UI can show a badge,
 * but outbound mapping in core-xray is a TODO and will not start a real proxy.
 */
object SsrParser {
    fun parse(link: String, source: NodeSource = NodeSource.MANUAL): ProxyNode {
        val payload = link.removePrefix("ssr://").trim()
        val decoded = runCatching {
            String(decodeBase64(payload), StandardCharsets.UTF_8)
        }.getOrElse {
            throw IllegalArgumentException("无效的 ssr:// 链接（无法 Base64 解码）")
        }
        val main = decoded.substringBefore("/?")
        val parts = main.split(":")
        // host:port:protocol:method:obfs:password_base64
        val host = parts.getOrNull(0).orEmpty()
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return ProxyNode(
            name = host.ifBlank { "SSR 节点" },
            protocol = Protocol.SSR,
            host = host.ifBlank { "0.0.0.0" },
            port = if (port > 0) port else 1,
            password = parts.getOrNull(5),
            rawLink = link,
            source = source,
        ).also {
            // TODO(ssr): implement full SSR field mapping (protocol/obfs/params)
            // and a matching Xray/SSR outbound. This is a recognition stub only.
        }
    }
}

internal fun parseQuery(query: String): Map<String, String> {
    if (query.isBlank()) return emptyMap()
    return query.split("&").mapNotNull { pair ->
        val eq = pair.indexOf('=')
        if (eq <= 0) null
        else decode(pair.substring(0, eq)) to decode(pair.substring(eq + 1))
    }.toMap()
}

internal fun splitHostPort(hostPort: String, defaultPort: Int): Pair<String, Int> {
    val value = hostPort.trim().trim('[', ']')
    return if (value.startsWith("[")) {
        val end = value.indexOf(']')
        val host = value.substring(1, end)
        val port = value.substringAfter("]:", "").toIntOrNull() ?: defaultPort
        host to port
    } else {
        val idx = value.lastIndexOf(':')
        if (idx > 0) value.substring(0, idx) to (value.substring(idx + 1).toIntOrNull() ?: defaultPort)
        else value to defaultPort
    }
}

internal fun decode(value: String): String =
    URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())

internal fun decodeBase64(value: String): ByteArray {
    val padded = value.replace('-', '+').replace('_', '/')
        .let { raw ->
            val rem = raw.length % 4
            if (rem == 0) raw else raw + "=".repeat(4 - rem)
        }
    return Base64.getDecoder().decode(padded)
}

internal fun decodeBase64Json(value: String): JsonObject {
    val bytes = decodeBase64(value)
    return Json.parseToJsonElement(String(bytes, StandardCharsets.UTF_8)).let { it as JsonObject }
}

private fun JsonObject.str(key: String): String {
    val el = this[key] ?: return ""
    return el.jsonPrimitive.contentOrNull ?: el.toString().trim('"')
}
