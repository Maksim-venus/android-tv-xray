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
import com.passwall.data.log.RuntimeLog
import com.passwall.tv.ui.PasswallNav
import com.passwall.tv.ui.PasswallViewModel
import com.passwall.tv.ui.theme.PasswallTheme
import com.passwall.tv.vpn.ProxyRuntime
import com.passwall.tv.vpn.VpnPersist
import com.passwall.tv.vpn.VpnRestarter

class MainActivity : ComponentActivity() {
    /** True only for the Start button's consent dialog. Restore denial must not clear "wanted". */
    private var userConsentLaunch = false

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val fromUserButton = userConsentLaunch
        userConsentLaunch = false
        if (result.resultCode == Activity.RESULT_OK) {
            if (!fromUserButton && !VpnPersist.isWanted(this)) {
                RuntimeLog.info("已停止，忽略迟到的 VPN 授权", "vpn")
                return@registerForActivityResult
            }
            RuntimeLog.info("VPN 权限已授予", "vpn")
            VpnPersist.prepareUserStart(this, null)
            ProxyRuntime.startService(this, restore = false)
        } else if (fromUserButton || !VpnPersist.isWanted(this)) {
            val msg = "未授予 VPN 权限，无法启动。请再按「启动」并选择允许。"
            ProxyRuntime.markError(msg)
            ProxyRuntime.toast(this, msg)
        } else {
            VpnRestarter.onConsentDenied(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Theme.Passwall.Splash paints logo + "Passwall TV" as the window
        // background. It stays up until this Compose tree draws its first frame.
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
                    onStop = { ProxyRuntime.stopFromUserAction(this) },
                    onTest = { vm.testSelected() },
                    onSelectNode = vm::selectNode,
                    onToggleInsecure = vm::setAllowInsecure,
                    onToggleHttp = vm::setHttpEdit,
                )
            }
        }
        consumeRestoreIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeRestoreIntent(intent)
    }

    private fun consumeRestoreIntent(intent: Intent?) {
        if (!VpnPersist.isWanted(this)) return
        val explicit = intent?.action == VpnRestarter.ACTION_REQUEST_VPN_CONSENT
        window.decorView.post {
            if (isFinishing) return@post
            if (ProxyRuntime.isRunning.value || ProxyRuntime.serviceAcceptedStart) return@post
            if (VpnPersist.isExhausted(this)) {
                val msg = "自动重连已停止，请手动点「启动」"
                ProxyRuntime.markError(msg)
                ProxyRuntime.toast(this, msg)
                return@post
            }
            VpnRestarter.tryRestore(
                this,
                reason = if (explicit) "consent-ui" else "activity",
                consentLauncher = { prepare ->
                    userConsentLaunch = false
                    vpnPermissionLauncher.launch(prepare)
                },
            )
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
                userConsentLaunch = true
                RuntimeLog.info("已弹出系统 VPN 授权", "vpn")
                ProxyRuntime.markMessage("请允许 VPN 权限", autoClear = true)
                ProxyRuntime.toast(this, "请允许 VPN 权限")
                vpnPermissionLauncher.launch(prepare)
            } else {
                userConsentLaunch = false
                VpnPersist.prepareUserStart(this, vm.settings.value.selectedNodeId)
                ProxyRuntime.startService(this, restore = false)
            }
        } catch (t: Throwable) {
            val msg = "启动失败：${t.message ?: t.javaClass.simpleName}"
            ProxyRuntime.markError(msg)
            ProxyRuntime.toast(this, msg)
        }
    }
}
