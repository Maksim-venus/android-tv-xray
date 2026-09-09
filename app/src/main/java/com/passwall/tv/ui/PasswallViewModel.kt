package com.passwall.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.passwall.adminweb.LanAddress
import com.passwall.adminweb.TcpPinger
import com.passwall.corexray.ProxyReachability
import com.passwall.data.model.AppSettings
import com.passwall.data.model.ProxyNode
import com.passwall.tv.PasswallApp
import com.passwall.tv.vpn.ProxyRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val running: Boolean = false,
    val stopping: Boolean = false,
    val statusOk: Boolean = false,
    val statusText: String = "",
    val hasNode: Boolean = false,
    val error: String? = null,
)

data class SettingsUiState(
    val nodes: List<ProxyNode> = emptyList(),
    val selectedNodeId: Long? = null,
    val allowInsecure: Boolean = false,
    val httpEditEnabled: Boolean = false,
    val httpUrl: String? = null,
    val loading: Boolean = true,
)

class PasswallViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PasswallApp

    val home: StateFlow<HomeUiState> = combine(
        combine(
            ProxyRuntime.isRunning,
            ProxyRuntime.isStopping,
            ProxyRuntime.statusOk,
        ) { running, stopping, ok -> Triple(running, stopping, ok) },
        combine(
            ProxyRuntime.statusMessage,
            ProxyRuntime.lastError,
            app.repository.nodesFlow,
        ) { message, error, nodes -> Triple(message, error, nodes) },
    ) { run, extra ->
        val (running, stopping, ok) = run
        val (message, error, nodes) = extra
        HomeUiState(
            running = running || stopping,
            stopping = stopping,
            statusOk = ok,
            statusText = message,
            hasNode = nodes.isNotEmpty(),
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    val settings: StateFlow<SettingsUiState> = combine(
        app.repository.nodesFlow,
        app.repository.settingsFlow,
    ) { nodes, prefs ->
        syncAdmin(prefs)
        SettingsUiState(
            nodes = nodes,
            selectedNodeId = prefs.selectedNodeId,
            allowInsecure = prefs.allowInsecureSsl,
            httpEditEnabled = prefs.httpEditEnabled,
            httpUrl = if (prefs.httpEditEnabled) LanAddress.httpUrl(prefs.httpPort) else null,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun selectNode(id: Long) {
        viewModelScope.launch { app.repository.selectNode(id) }
    }

    fun setAllowInsecure(enabled: Boolean) {
        viewModelScope.launch { app.repository.setAllowInsecure(enabled) }
    }

    fun setHttpEdit(enabled: Boolean) {
        viewModelScope.launch { app.repository.setHttpEditEnabled(enabled) }
    }

    fun testSelected() {
        viewModelScope.launch {
            val node = app.repository.getSelectedNode()
            if (node == null) {
                ProxyRuntime.markTest(false, "请先在设置或网页导入节点")
                return@launch
            }
            if (!app.engine.isRunning() && !ProxyRuntime.isRunning.value) {
                val result = withContext(Dispatchers.IO) { TcpPinger.ping(node) }
                if (result.ok) {
                    app.repository.updateLatency(node.id, result.latencyMs)
                    ProxyRuntime.markTest(false, "节点端口可达，请先启动代理后再测外网")
                } else {
                    ProxyRuntime.markTest(false, result.error ?: "节点不可达")
                }
                return@launch
            }
            ProxyRuntime.markTestProgress("正在查询出口 IP…")
            val http = withContext(Dispatchers.IO) { ProxyReachability.probe() }
            if (http.ok) {
                app.repository.updateLatency(node.id, http.latencyMs)
                val delay = withContext(Dispatchers.IO) { app.engine.measureProxyDelay() }
                val extra = if (delay.ok && delay.latencyMs != null) " · 链路 ${delay.latencyMs} ms" else ""
                ProxyRuntime.markTest(true, http.message + extra)
            } else {
                ProxyRuntime.markTest(false, http.message)
            }
        }
    }

    private fun syncAdmin(prefs: AppSettings) {
        if (prefs.httpEditEnabled && !app.adminServer.isRunning) {
            app.adminServer.start(prefs.httpPort)
        } else if (!prefs.httpEditEnabled && app.adminServer.isRunning) {
            app.adminServer.stop()
        }
    }
}
