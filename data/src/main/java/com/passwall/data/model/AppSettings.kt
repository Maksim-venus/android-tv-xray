package com.passwall.data.model

data class AppSettings(
    val selectedNodeId: Long? = null,
    val allowInsecureSsl: Boolean = false,
    val httpEditEnabled: Boolean = false,
    val httpPort: Int = DEFAULT_HTTP_PORT,
    val routingAssetsUpdatedAt: Long? = null,
    val routingAssetsAttemptedAt: Long? = null,
) {
    companion object {
        const val DEFAULT_HTTP_PORT = 8787
    }
}
