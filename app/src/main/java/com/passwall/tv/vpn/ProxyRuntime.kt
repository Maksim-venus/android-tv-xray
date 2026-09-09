package com.passwall.tv.vpn

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.passwall.data.log.RuntimeLog
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private val _startRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val startRequests: SharedFlow<Unit> = _startRequests.asSharedFlow()

    fun markStarted(message: String) {
        _isRunning.value = true
        _statusMessage.value = message
        _statusOk.value = true
        _lastError.value = null
        pendingStart = false
        RuntimeLog.info("VPN 已启动：$message", "vpn")
    }

    fun markStopped(message: String = "") {
        _isRunning.value = false
        _statusMessage.value = message
        _statusOk.value = false
        RuntimeLog.info("VPN 已停止" + if (message.isBlank()) "" else "：$message", "vpn")
    }

    fun markMessage(message: String) {
        _statusMessage.value = message
        RuntimeLog.info(message, "vpn")
    }

    fun markError(message: String) {
        _isRunning.value = false
        _statusMessage.value = message
        _statusOk.value = false
        _lastError.value = message
        RuntimeLog.error(message, "vpn")
    }

    fun markTest(ok: Boolean, message: String) {
        _statusOk.value = ok
        _statusMessage.value = message
        if (!ok) {
            _lastError.value = message
            RuntimeLog.warn("连通性测试：$message", "test")
        } else {
            _lastError.value = null
            RuntimeLog.info("连通性测试：$message", "test")
        }
    }

    fun toast(context: Context, message: String, long: Boolean = true) {
        Toast.makeText(
            context.applicationContext,
            message,
            if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
        ).show()
    }

    fun requestStartFromApp() {
        pendingStart = true
        _startRequests.tryEmit(Unit)
    }

    fun requestStopFromApp(context: Context) {
        context.stopService(Intent(context, ProxyVpnService::class.java))
    }

    fun startService(context: Context) {
        try {
            val intent = Intent(context, ProxyVpnService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            RuntimeLog.info("正在启动 VpnService", "vpn")
            markMessage("正在启动代理…")
        } catch (t: Throwable) {
            val msg = "无法启动服务：${t.message ?: t.javaClass.simpleName}"
            markError(msg)
            toast(context, msg)
        }
    }
}
