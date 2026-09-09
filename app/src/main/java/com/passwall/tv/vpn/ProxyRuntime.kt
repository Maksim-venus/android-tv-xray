package com.passwall.tv.vpn

import android.content.Context
import android.widget.Toast
import com.passwall.data.log.RuntimeLog
import com.passwall.tv.PasswallApp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

object ProxyRuntime {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isStopping = MutableStateFlow(false)
    val isStopping: StateFlow<Boolean> = _isStopping.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _statusOk = MutableStateFlow(false)
    val statusOk: StateFlow<Boolean> = _statusOk.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    var pendingStart: Boolean = false

    private val stopInFlight = AtomicBoolean(false)

    private val _startRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val startRequests: SharedFlow<Unit> = _startRequests.asSharedFlow()

    fun markStarted(message: String) {
        pendingStart = false
        stopInFlight.set(false)
        _isStopping.value = false
        _isRunning.value = true
        // Starting the VPN is not a reachability test.
        _statusOk.value = false
        _statusMessage.value = message.ifBlank { "已启动，请点测试检查外网" }
        _lastError.value = null
        RuntimeLog.info("VPN 已启动：$message", "vpn")
    }

    fun markStopped(message: String = "已停止") {
        pendingStart = false
        stopInFlight.set(false)
        _isStopping.value = false
        _isRunning.value = false
        _statusOk.value = false
        _statusMessage.value = message
        RuntimeLog.info("VPN 已停止" + if (message.isBlank()) "" else "：$message", "vpn")
    }

    fun markMessage(message: String) {
        _statusMessage.value = message
        RuntimeLog.info(message, "vpn")
    }

    fun markError(message: String) {
        pendingStart = false
        stopInFlight.set(false)
        _isStopping.value = false
        _isRunning.value = false
        _statusMessage.value = message
        _statusOk.value = false
        _lastError.value = message
        RuntimeLog.error(message, "vpn")
    }

    fun markTestProgress(message: String) {
        _statusOk.value = false
        _statusMessage.value = message
        RuntimeLog.info("连通性测试：$message", "test")
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

    /**
     * Must run from a click/Enter handler (or the admin stop API). Tears down
     * Xray immediately, then asks VpnService to close TUN. Never a silent no-op.
     */
    fun stopFromUserAction(context: Context) {
        val app = context.applicationContext as? PasswallApp
        val engineRunning = app?.engine?.isRunning() == true
        if (stopInFlight.get() || _isStopping.value) {
            markMessage("正在停止…")
            toast(context, "正在停止…")
            RuntimeLog.info("停止：已在停止中", "vpn")
            return
        }
        if (!_isRunning.value && !engineRunning) {
            try {
                stopInFlight.set(true)
                _isStopping.value = true
                _statusMessage.value = "正在停止…"
                app?.engine?.stop()
                ProxyVpnService.requestStop(context)
                markStopped("已停止")
                toast(context, "代理未在运行")
                RuntimeLog.info("停止：代理未在运行，已再次请求拆掉服务", "vpn")
            } catch (t: Throwable) {
                val msg = "停止失败：${t.message ?: t.javaClass.simpleName}"
                markError(msg)
                toast(context, msg)
            }
            return
        }
        try {
            stopInFlight.set(true)
            _isStopping.value = true
            _isRunning.value = false
            _statusOk.value = false
            _statusMessage.value = "正在停止…"
            RuntimeLog.info("正在停止 VPN / Xray", "vpn")
            app?.engine?.stop()
            ProxyVpnService.requestStop(context)
        } catch (t: Throwable) {
            val msg = "停止失败：${t.message ?: t.javaClass.simpleName}"
            markError(msg)
            toast(context, msg)
        }
    }

    @Deprecated("Use stopFromUserAction", ReplaceWith("stopFromUserAction(context)"))
    fun requestStopFromApp(context: Context) {
        stopFromUserAction(context)
    }

    fun startService(context: Context) {
        try {
            val intent = android.content.Intent(context, ProxyVpnService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
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
