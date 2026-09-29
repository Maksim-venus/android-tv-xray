package com.passwall.tv.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.passwall.corexray.GeodataHealth
import com.passwall.corexray.XrayConfigGenerator
import com.passwall.data.log.RuntimeLog
import com.passwall.tv.BuildConfig
import com.passwall.tv.MainActivity
import com.passwall.tv.PasswallApp
import com.passwall.tv.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ProxyVpnService : VpnService() {
    private var scope = newScope()
    private var tun: ParcelFileDescriptor? = null
    private val tornDown = AtomicBoolean(false)
    private val explicitStop = AtomicBoolean(false)
    private val session = AtomicInteger(0)
    private var acceptedStart = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            explicitStop.set(true)
            VpnPersist.clearWanted(this)
            VpnWatchdog.cancel(this)
            tearDown("已停止")
            return START_NOT_STICKY
        }
        val userStart = intent?.action == ACTION_START
        if (!userStart && intent == null) {
            RuntimeLog.warn("系统重新拉起 VpnService（START_STICKY flags=$flags）", "vpn")
        }
        if (!userStart && !VpnPersist.isWanted(this)) {
            return stopWithoutRestart("已停止")
        }
        if (!userStart && VpnPersist.isExhausted(this)) {
            return stopWithoutRestart("自动重连已停止")
        }
        // A second sticky/watchdog start must not tear down a tunnel that is already coming up.
        if (!tornDown.get() && session.get() > 0) {
            RuntimeLog.info("VpnService 已在运行，忽略重复拉起", "vpn")
            VpnWatchdog.scheduleHealthy(this)
            return START_STICKY
        }
        val gate = VpnPersist.beginAttempt(this, userStart = userStart)
        if (gate != AttemptGate.PROCEED || (!userStart && !VpnPersist.isWanted(this))) {
            return stopWithoutRestart(if (gate == AttemptGate.EXHAUSTED) "自动重连已停止" else "已停止")
        }
        explicitStop.set(false)
        tornDown.set(false)
        acceptedStart = true
        ProxyRuntime.noteServiceStarted()
        val mySession = session.incrementAndGet()
        runCatching { startForeground(NOTIFICATION_ID, buildNotification()) }
            .onFailure { RuntimeLog.warn("前台通知失败：${it.message}", "vpn") }
        VpnWatchdog.scheduleHealthy(this)
        liveScope().launch { startProxy(mySession) }
        // API 25: the process killer does not deliver a new Intent. START_STICKY asks the
        // system to recreate this service with a null intent. The alarm is the backup.
        return START_STICKY
    }

    private fun stopWithoutRestart(message: String): Int {
        explicitStop.set(true)
        VpnWatchdog.cancel(this)
        runCatching { startForeground(NOTIFICATION_ID, buildNotification()) }
        tearDown(message)
        return START_NOT_STICKY
    }

    private fun liveScope(): CoroutineScope {
        if (!scope.isActive) scope = newScope()
        return scope
    }

    private suspend fun startProxy(mySession: Int) {
        // An older session must not call tearDown: that would clear tornDown on the new session.
        if (abandoned(mySession)) return
        if (!VpnPersist.isWanted(this)) {
            tearDown("已停止")
            return
        }
        val app = application as PasswallApp
        val node = resolveNode(app)
        if (node == null) {
            failStart(mySession, getString(R.string.no_node), retryable = false)
            return
        }
        RuntimeLog.info("VPN 节点：${node.name}", "vpn")
        val settings = app.repository.getSettings()
        app.routingAssets.installBundledDefaults()
        var health = app.routingAssets.health()
        if (!health.geositeOk || !health.geoipOk) {
            RuntimeLog.warn(
                "分流数据不可用：geosite=${health.geositeError ?: "ok"} geoip=${health.geoipError ?: "ok"}",
                "geo",
            )
            health = runCatching { app.assetUpdater.repairInvalid(health) }.getOrDefault(health)
        }
        val extraIps = if (!health.geoipOk) app.routingAssets.extraDirectIps() else emptyList()
        var generated = XrayConfigGenerator.generate(
            node = node,
            settings = settings,
            enableIpv6 = BuildConfig.ENABLE_IPV6,
            health = health,
            extraDirectIps = extraIps,
        )
        val configFile = app.routingAssets.configFile()
        try {
            if (abandoned(mySession)) return
            val prepare = VpnService.prepare(this)
            if (prepare != null) {
                if (abandoned(mySession)) return
                ProxyRuntime.markError("需要重新允许 VPN")
                VpnRestarter.onConsentNeeded(this, prepare, consentLauncher = null)
                tearDown("需要重新允许 VPN")
                return
            }
            val builder = Builder()
                .setSession("Passwall")
                .addAddress("10.0.85.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("223.5.5.5")
                .setMtu(1500)
                .setBlocking(false)
            // Keep Xray's own sockets off the TUN so the node connection cannot loop.
            runCatching { builder.addDisallowedApplication(packageName) }
            if (BuildConfig.ENABLE_IPV6) {
                builder.addAddress("fd00:85::2", 128)
                builder.addRoute("::", 0)
            }
            // Avoid API 29+ Builder helpers (setMetered, excludeRoute) so this path stays
            // valid on Android 7.1 (API 25) / kernel 4.x. setBlocking and
            // addDisallowedApplication are API 21.
            tun?.close()
            tun = builder.establish()
            if (tun == null) {
                failStart(mySession, "系统未能建立 VPN 通道（TUN 为空）", retryable = true)
                return
            }
            if (abandoned(mySession)) {
                runCatching { tun?.close() }
                tun = null
                return
            }
            generated.notes.forEach { RuntimeLog.warn("配置：$it", "xray") }
            if (abandoned(mySession)) {
                runCatching { app.engine.stop() }
                return
            }
            try {
                app.engine.start(generated, configFile, tun)
            } catch (t: Throwable) {
                if (isGeodataFailure(t) && generated.usedGeosite) {
                    RuntimeLog.warn(
                        "Xray 拒绝 geosite.dat（${t.message}），改用 geoip/IP 分流重试",
                        "xray",
                    )
                    val fallbackHealth = GeodataHealth(
                        geositeOk = false,
                        geoipOk = health.geoipOk,
                        geositeError = t.message,
                        geoipError = health.geoipError,
                    )
                    generated = XrayConfigGenerator.generate(
                        node = node,
                        settings = settings,
                        enableIpv6 = BuildConfig.ENABLE_IPV6,
                        health = fallbackHealth,
                        extraDirectIps = extraIps,
                    )
                    generated.notes.forEach { RuntimeLog.warn("配置：$it", "xray") }
                    if (abandoned(mySession)) return
                    app.engine.start(generated, configFile, tun)
                } else {
                    throw t
                }
            }
            if (abandoned(mySession) || !VpnPersist.isWanted(this)) {
                runCatching { app.engine.stop() }
                return
            }
            VpnPersist.ackRunning(this, node.id)
            VpnWatchdog.scheduleHealthy(this)
            RuntimeLog.info("已记住自动重连，节点 ${node.name}", "vpn")
            ProxyRuntime.markStarted("已启动，请点测试检查外网")
            VpnRestarter.onServiceSettled()
            // Never block start: refresh geoip/geosite in the background.
            scope.launch {
                val geo = runCatching { app.assetUpdater.refreshAfterSuccessfulStart() }
                    .getOrElse {
                        RuntimeLog.warn("分流规则更新异常：${it.message}", "geo")
                        return@launch
                    }
                when {
                    !geo.attempted -> RuntimeLog.info("分流规则未到期，跳过更新", "geo")
                    geo.success -> RuntimeLog.info("分流规则已更新：${geo.updatedFiles.joinToString()}", "geo")
                    else -> RuntimeLog.warn("分流规则更新失败，沿用上次文件：${geo.error}", "geo")
                }
            }
        } catch (t: Throwable) {
            if (abandoned(mySession)) return
            if (t is SecurityException) {
                val prepare = runCatching { VpnService.prepare(this) }.getOrNull()
                if (prepare != null) {
                    ProxyRuntime.markError("需要重新允许 VPN")
                    VpnRestarter.onConsentNeeded(this, prepare, consentLauncher = null)
                    tearDown("需要重新允许 VPN")
                    return
                }
            }
            val detail = t.message ?: t.javaClass.simpleName
            failStart(mySession, "配置或引擎启动失败：$detail", retryable = true)
        }
    }

    private suspend fun resolveNode(app: PasswallApp): com.passwall.data.model.ProxyNode? {
        val preferred = VpnPersist.nodeId(this)
        val persisted = if (preferred > 0) app.repository.findNode(preferred) else null
        if (persisted != null) {
            if (app.repository.getSettings().selectedNodeId != persisted.id) {
                app.repository.selectNode(persisted.id)
            }
            return persisted
        }
        if (preferred > 0) {
            RuntimeLog.warn("上次节点已不在列表中，改用当前选中节点", "vpn")
        }
        val node = app.repository.getSelectedNode() ?: return null
        VpnPersist.setNodeId(this, node.id)
        return node
    }

    private fun failStart(mySession: Int, message: String, retryable: Boolean) {
        if (abandoned(mySession)) return
        ProxyRuntime.markError(message)
        if (VpnPersist.isWanted(this) && !explicitStop.get()) {
            VpnRestarter.planRetry(this, message, retryable)
        }
        tearDown(message)
    }

    private fun abandoned(mySession: Int): Boolean =
        explicitStop.get() || tornDown.get() || mySession != session.get() || !scope.isActive

    private fun tearDown(message: String) {
        if (!tornDown.compareAndSet(false, true)) return
        session.incrementAndGet()
        runCatching { (application as PasswallApp).engine.stop() }
        runCatching { tun?.close() }
        tun = null
        runCatching {
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
        ProxyRuntime.markStopped(message.ifBlank { "已停止" })
        stopSelf()
    }

    override fun onDestroy() {
        val explicit = explicitStop.get()
        if (!tornDown.get()) {
            tearDown(if (explicit) "已停止" else "服务已结束")
        }
        scope.cancel()
        if (acceptedStart) {
            acceptedStart = false
            ProxyRuntime.noteServiceStopped()
        }
        VpnRestarter.onServiceDestroyed(this, explicit)
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (VpnPersist.isWanted(this) && !explicitStop.get() && !VpnPersist.isExhausted(this)) {
            VpnWatchdog.scheduleIfSooner(this, VpnRetryPolicy.DESTROY_RESTART_MS, "任务被移除")
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onRevoke() {
        RuntimeLog.warn("系统已撤销 VPN", "vpn")
        if (VpnPersist.isWanted(this) && !explicitStop.get()) {
            VpnPersist.recordHandledFailure(this, System.currentTimeMillis())
        }
        tearDown("系统已撤销 VPN")
        super.onRevoke()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.vpn_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(getString(R.string.vpn_notification_title))
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(launch)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.passwall.tv.vpn.STOP"
        const val ACTION_START = "com.passwall.tv.vpn.START"
        const val ACTION_RESTORE = "com.passwall.tv.vpn.RESTORE"
        private const val CHANNEL_ID = "passwall_vpn"
        private const val NOTIFICATION_ID = 41

        private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun requestStop(context: Context) {
            val appCtx = context.applicationContext
            val stop = Intent(appCtx, ProxyVpnService::class.java).setAction(ACTION_STOP)
            try {
                appCtx.startService(stop)
            } catch (t: Throwable) {
                RuntimeLog.warn("无法向 VpnService 发送停止：${t.message}", "vpn")
            }
            try {
                appCtx.stopService(Intent(appCtx, ProxyVpnService::class.java))
            } catch (t: Throwable) {
                RuntimeLog.warn("stopService 失败：${t.message}", "vpn")
            }
        }

        internal fun isGeodataFailure(t: Throwable): Boolean {
            val msg = (t.message ?: "") + (t.cause?.message ?: "")
            return msg.contains("geosite", ignoreCase = true) ||
                msg.contains("geoip", ignoreCase = true) ||
                msg.contains("geodata", ignoreCase = true) ||
                (msg.contains("EOF") && msg.contains("cn", ignoreCase = true))
        }
    }
}
