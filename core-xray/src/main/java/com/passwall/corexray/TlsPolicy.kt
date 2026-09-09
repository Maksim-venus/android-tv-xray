package com.passwall.corexray

import com.passwall.data.model.AppSettings
import com.passwall.data.model.ProxyNode

/**
 * Maps the app's 「允许不安全 SSL」 toggle onto the TLS fields this Xray-core
 * still accepts. `allowInsecure` must never appear in generated JSON.
 *
 * Closest supported behavior:
 * - `pinnedPeerCertSha256` (pcs) when the share link provides a SHA-256 pin
 * - `verifyPeerCertByName` (vcn) to verify the cert name (SNI / host / explicit vcn)
 * Skip-verify is no longer possible on this core.
 */
data class TlsPolicy(
    val serverName: String,
    val verifyPeerCertByName: String? = null,
    val pinnedPeerCertSha256: String? = null,
    val note: String? = null,
) {
    companion object {
        const val SKIP_VERIFY_REMOVED_NOTE =
            "当前 Xray 已取消 allowInsecure（跳过证书校验）。" +
                "已改为按证书名验证（verifyPeerCertByName / vcn）。" +
                "自签或私有证书请在分享链接中提供 pcs（证书 SHA-256）。"

        fun from(node: ProxyNode, settings: AppSettings): TlsPolicy {
            val serverName = node.sni?.takeIf { it.isNotBlank() } ?: node.host
            val pin = normalizePin(node.pinnedPeerCertSha256)
            val explicitVcn = node.verifyPeerCertByName?.trim()?.takeIf { it.isNotEmpty() }
            val insecureRequested = settings.allowInsecureSsl || node.allowInsecure
            val vcn = when {
                explicitVcn != null -> explicitVcn
                insecureRequested -> serverName
                else -> null
            }
            val note = when {
                !insecureRequested -> null
                pin != null -> "已使用链接中的证书指纹（pcs）代替已移除的跳过校验。"
                else -> SKIP_VERIFY_REMOVED_NOTE
            }
            return TlsPolicy(
                serverName = serverName,
                verifyPeerCertByName = vcn,
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
