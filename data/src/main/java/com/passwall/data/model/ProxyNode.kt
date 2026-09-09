package com.passwall.data.model

data class ProxyNode(
    val id: Long = 0,
    val name: String,
    val protocol: Protocol,
    val host: String,
    val port: Int,
    val uuid: String? = null,
    val password: String? = null,
    val alterId: Int = 0,
    val security: String = "tls",
    val network: String = "tcp",
    val path: String? = null,
    val hostHeader: String? = null,
    val sni: String? = null,
    val flow: String? = null,
    val encryption: String? = "none",
    val fingerprint: String? = null,
    val publicKey: String? = null,
    val shortId: String? = null,
    val spiderX: String? = null,
    val allowInsecure: Boolean = false,
    val pinnedPeerCertSha256: String? = null,
    val verifyPeerCertByName: String? = null,
    val rawLink: String = "",
    val source: NodeSource = NodeSource.MANUAL,
    val subscriptionId: Long? = null,
    val online: Boolean = true,
    val latencyMs: Long? = null,
    val createdAt: Long = 0,
) {
    val endpoint: String get() = "$host:$port"
}

enum class NodeSource { MANUAL, SUBSCRIPTION, SEED }
