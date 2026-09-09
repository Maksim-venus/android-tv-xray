package com.passwall.adminweb

fun interface ProxyController {
    fun setRunning(running: Boolean)
}

data class AdminRuntime(
    val isRunning: () -> Boolean,
    val statusMessage: () -> String,
    val usingStub: () -> Boolean,
    val startProxy: () -> Unit,
    val stopProxy: () -> Unit,
    val probeProxy: () -> ProxyProbeResult = { ProxyProbeResult(false, message = "探测未接线") },
)

data class ProxyProbeResult(
    val ok: Boolean,
    val latencyMs: Long? = null,
    val httpStatus: Int? = null,
    val url: String? = null,
    val error: String? = null,
    val message: String = "",
)
