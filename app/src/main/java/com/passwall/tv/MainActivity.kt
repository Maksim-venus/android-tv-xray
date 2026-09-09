package com.passwall.tv

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: PasswallViewModel = viewModel()
            val home by vm.home.collectAsStateWithLifecycle()
            val settings by vm.settings.collectAsStateWithLifecycle()
            val vpnLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    ProxyRuntime.startService(this)
                }
            }
            LaunchedEffect(home.pendingVpnPrepare) {
                if (home.pendingVpnPrepare) {
                    vm.consumeVpnPrepare()
                    val prepare = VpnService.prepare(this@MainActivity)
                    if (prepare != null) vpnLauncher.launch(prepare)
                    else ProxyRuntime.startService(this@MainActivity)
                }
            }
            LaunchedEffect(Unit) {
                ProxyRuntime.isRunning.collect { /* keep collectors hot via viewModel */ }
            }
            PasswallTheme {
                PasswallNav(
                    home = home,
                    settings = settings,
                    onStart = { vm.requestStart() },
                    onStop = { stopService(android.content.Intent(this, ProxyVpnService::class.java)) },
                    onTest = { vm.testSelected() },
                    onSelectNode = vm::selectNode,
                    onToggleInsecure = vm::setAllowInsecure,
                    onToggleHttp = vm::setHttpEdit,
                )
            }
        }
    }
}
