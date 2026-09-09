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
)
