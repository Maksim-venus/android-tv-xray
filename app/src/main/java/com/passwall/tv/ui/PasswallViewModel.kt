package com.passwall.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.passwall.adminweb.LanAddress
import com.passwall.adminweb.TcpPinger
import com.passwall.data.model.AppSettings
import com.passwall.data.model.ProxyNode
import com.passwall.tv.PasswallApp
import com.passwall.tv.vpn.ProxyRuntime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val running: Boolean = false,
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
        ProxyRuntime.isRunning,
        ProxyRuntime.statusOk,
        ProxyRuntime.statusMessage,
        ProxyRuntime.lastError,
        app.repository.nodesFlow,
    ) { running, ok, message, error, nodes ->
        HomeUiState(
            running = running,
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
            if (app.engine.isRunning()) {
                val throughProxy = app.engine.measureProxyDelay()
                if (throughProxy.ok) {
                    app.repository.updateLatency(node.id, throughProxy.latencyMs)
                    ProxyRuntime.markTest(true, "代理正常 ${throughProxy.latencyMs} ms")
                    return@launch
                }
                ProxyRuntime.markTest(false, throughProxy.error ?: "代理异常")
                return@launch
            }
            val result = TcpPinger.ping(node)
            if (result.ok) {
                app.repository.updateLatency(node.id, result.latencyMs)
                ProxyRuntime.markTest(false, "节点可达，请先启动代理")
            } else {
                ProxyRuntime.markTest(false, result.error ?: "节点不可达")
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
