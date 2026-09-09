package com.passwall.tv

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.passwall.tv.ui.PasswallNav
import com.passwall.tv.ui.PasswallViewModel
import com.passwall.tv.ui.theme.PasswallTheme
import com.passwall.tv.vpn.ProxyRuntime
import com.passwall.tv.vpn.ProxyVpnService

class MainActivity : ComponentActivity() {
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ProxyRuntime.startService(this)
        } else {
            val msg = "未授予 VPN 权限，无法启动。请再按「启动」并选择允许。"
            ProxyRuntime.markError(msg)
            ProxyRuntime.toast(this, msg)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: PasswallViewModel = viewModel()
            val home by vm.home.collectAsStateWithLifecycle()
            val settings by vm.settings.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) {
                ProxyRuntime.startRequests.collect { startVpnFromUserAction(vm) }
            }
            PasswallTheme {
                PasswallNav(
                    home = home,
                    settings = settings,
                    onStart = { startVpnFromUserAction(vm) },
                    onStop = { stopService(Intent(this, ProxyVpnService::class.java)) },
                    onTest = { vm.testSelected() },
                    onSelectNode = vm::selectNode,
                    onToggleInsecure = vm::setAllowInsecure,
                    onToggleHttp = vm::setHttpEdit,
                )
            }
        }
    }

    /**
     * Must run from a key/click callback (or shortly after). Launching
     * VpnService.prepare() from a delayed Compose effect is ignored on many TVs.
     */
    private fun startVpnFromUserAction(vm: PasswallViewModel) {
        if (!vm.home.value.hasNode) {
            val msg = "请先在设置或网页导入节点"
            ProxyRuntime.markError(msg)
            ProxyRuntime.toast(this, msg)
            return
        }
        try {
            val prepare = VpnService.prepare(this)
            if (prepare != null) {
                ProxyRuntime.markMessage("请允许 VPN 权限")
                ProxyRuntime.toast(this, "请允许 VPN 权限")
                vpnPermissionLauncher.launch(prepare)
            } else {
                ProxyRuntime.startService(this)
            }
        } catch (t: Throwable) {
            val msg = "启动失败：${t.message ?: t.javaClass.simpleName}"
            ProxyRuntime.markError(msg)
            ProxyRuntime.toast(this, msg)
        }
    }
}
