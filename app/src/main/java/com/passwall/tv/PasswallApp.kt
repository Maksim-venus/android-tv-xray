package com.passwall.tv

import android.app.Application
import com.passwall.adminweb.AdminRuntime
import com.passwall.adminweb.AdminServer
import com.passwall.adminweb.AssetRefreshOutcome
import com.passwall.corexray.Libv2rayEngine
import com.passwall.corexray.RoutingAssetStore
import com.passwall.corexray.RoutingAssetUpdater
import com.passwall.corexray.XrayEngine
import com.passwall.corexray.XrayPorts
import com.passwall.data.db.AppDatabase
import com.passwall.data.log.RuntimeLog
import com.passwall.data.repo.PasswallRepository
import com.passwall.tv.vpn.ProxyRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PasswallApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var repository: PasswallRepository
        private set
    lateinit var engine: XrayEngine
        private set
    lateinit var adminServer: AdminServer
        private set
    lateinit var routingAssets: RoutingAssetStore
        private set
    lateinit var assetUpdater: RoutingAssetUpdater
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        val db = AppDatabase.create(this)
        repository = PasswallRepository(db)
        routingAssets = RoutingAssetStore(this)
        assetUpdater = RoutingAssetUpdater(
            store = routingAssets,
            repository = repository,
            socksPort = {
                if (this::engine.isInitialized && engine.isRunning()) {
                    XrayPorts.DEFAULT_SOCKS_PORT
                } else {
                    null
                }
            },
        )
        engine = Libv2rayEngine()
        adminServer = AdminServer(
            context = this,
            repository = repository,
            runtime = AdminRuntime(
                isRunning = { ProxyRuntime.isRunning.value },
                statusMessage = { ProxyRuntime.statusMessage.value },
                usingStub = { engine.status.value.usingStub },
                startProxy = { ProxyRuntime.requestStartFromApp() },
                stopProxy = { ProxyRuntime.stopFromUserAction(this) },
                refreshRoutingAssets = {
                    val result = assetUpdater.refreshAfterSuccessfulStart(force = true)
                    val updatedAt = repository.getSettings().routingAssetsUpdatedAt
                    val message = when {
                        result.success && result.attempted ->
                            "分流规则已更新：${result.updatedFiles.joinToString()}"
                        result.attempted -> result.error ?: "分流规则更新失败，继续使用 APK 内置"
                        else -> result.error ?: "分流规则更新未执行"
                    }
                    AssetRefreshOutcome(
                        success = result.success && result.attempted,
                        message = message,
                        updatedAt = updatedAt,
                    )
                },
                probeProxy = {
                    val result = if (!ProxyRuntime.isRunning.value) {
                        com.passwall.corexray.ReachabilityResult(
                            ok = false,
                            error = "代理未运行",
                            message = "请先启动代理后再测外网",
                        )
                    } else {
                        com.passwall.corexray.ProxyReachability.probe()
                    }
                    if (result.ok) {
                        ProxyRuntime.markTest(true, result.message)
                    } else {
                        ProxyRuntime.markTest(false, result.message)
                    }
                    com.passwall.adminweb.ProxyProbeResult(
                        ok = result.ok,
                        latencyMs = result.latencyMs,
                        httpStatus = result.httpStatus,
                        url = result.url,
                        error = result.error,
                        message = result.message,
                        exitIp = result.exitIp,
                        country = result.country,
                        flag = result.flag,
                    )
                },
            ),
        )
        RuntimeLog.info("Passwall 已启动", "app")
        // geosite.dat is ~11MB. Validating it (and copying it on first launch)
        // used to run here on the main thread, so the window stayed black until
        // it finished. The VPN start path installs the same files before Xray.
        appScope.launch(Dispatchers.IO) { routingAssets.installBundledDefaults() }
        appScope.launch { repository.ensureSettings() }
        appScope.launch {
            while (isActive) {
                RuntimeLog.prune()
                delay(60 * 60 * 1000L)
            }
        }
    }

    companion object {
        lateinit var instance: PasswallApp
            private set
    }
}
