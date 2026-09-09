package com.passwall.tv.vpn

import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ProxyRuntime {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _statusOk = MutableStateFlow(false)
    val statusOk: StateFlow<Boolean> = _statusOk.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    var pendingStart: Boolean = false

    fun markStarted(message: String) {
        _isRunning.value = true
        _statusMessage.value = message
        _statusOk.value = true
        _lastError.value = null
        pendingStart = false
    }

    fun markStopped(message: String = "") {
        _isRunning.value = false
        _statusMessage.value = message
        _statusOk.value = false
    }

    fun markError(message: String) {
        _isRunning.value = false
        _statusMessage.value = message
        _statusOk.value = false
        _lastError.value = message
    }

    fun markTest(ok: Boolean, message: String) {
        _statusOk.value = ok
        _statusMessage.value = message
    }

    fun requestStartFromApp() {
        pendingStart = true
    }

    fun requestStopFromApp(context: Context) {
        context.stopService(Intent(context, ProxyVpnService::class.java))
    }

    fun startService(context: Context) {
        val intent = Intent(context, ProxyVpnService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
