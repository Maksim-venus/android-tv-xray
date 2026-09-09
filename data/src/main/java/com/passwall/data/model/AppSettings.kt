package com.passwall.data.model

data class AppSettings(
    val selectedNodeId: Long? = null,
    val allowInsecureSsl: Boolean = false,
    val httpEditEnabled: Boolean = false,
    val httpPort: Int = DEFAULT_HTTP_PORT,
) {
    companion object {
        const val DEFAULT_HTTP_PORT = 8787
    }
}
