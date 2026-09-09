package com.passwall.corexray

import com.passwall.data.model.AppSettings
import com.passwall.data.model.ProxyNode

/**
 * TLS fields for the pinned Xray-core (v1.260113.0 / AndroidLibXrayLite v26.1.13).
 *
 * That build still honors `allowInsecure` with no date-based removal.
 * Do not emit `verifyPeerCertByName` — this core uses `verifyPeerCertInNames`
 * and newer cores renamed/removed allowInsecure.
 */
data class TlsPolicy(
    val serverName: String,
    val allowInsecure: Boolean = false,
    val pinnedPeerCertSha256: String? = null,
    val note: String? = null,
) {
    companion object {
        fun from(node: ProxyNode, settings: AppSettings): TlsPolicy {
            val serverName = node.sni?.takeIf { it.isNotBlank() } ?: node.host
            val pin = normalizePin(node.pinnedPeerCertSha256)
            val insecureRequested = settings.allowInsecureSsl || node.allowInsecure
            val note = when {
                insecureRequested -> "已写入 allowInsecure（当前核心 Xray 26.1.13 仍支持跳过证书校验）。"
                else -> null
            }
            return TlsPolicy(
                serverName = serverName,
                allowInsecure = insecureRequested,
                pinnedPeerCertSha256 = pin,
                note = note,
            )
        }

        fun normalizePin(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split(',')
                .map { it.replace(":", "").replace(" ", "").trim() }
                .filter { it.matches(HEX_SHA256) }
            return parts.joinToString(",").ifBlank { null }
        }

        private val HEX_SHA256 = Regex("[0-9a-fA-F]{64}")
    }
}
