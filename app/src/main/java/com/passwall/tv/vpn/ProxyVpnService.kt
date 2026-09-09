package com.passwall.tv.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import kotlinx.coroutines.launch

class ProxyVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        scope.launch { startProxy() }
        return START_STICKY
    }

    private suspend fun startProxy() {
        val app = application as PasswallApp
        val node = app.repository.getSelectedNode()
        if (node == null) {
            ProxyRuntime.markError(getString(R.string.no_node))
            stopSelf()
            return
        }
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
            // Avoid API 29+ Builder helpers so the same path stays valid on Android 9 / kernel 4.x.
            tun?.close()
            tun = builder.establish()
            if (tun == null) {
                ProxyRuntime.markError("系统未能建立 VPN 通道（TUN 为空）")
                stopSelf()
                return
            }
            generated.notes.forEach { RuntimeLog.warn("配置：$it", "xray") }
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
                    app.engine.start(generated, configFile, tun)
                } else {
                    throw t
                }
            }
            ProxyRuntime.markStarted(getString(R.string.proxy_ok))
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
            ProxyRuntime.markError("配置或引擎启动失败：${t.message ?: t.javaClass.simpleName}")
            stopSelf()
        }
    }

    override fun onDestroy() {
        runCatching { (application as PasswallApp).engine.stop() }
        runCatching { tun?.close() }
        tun = null
        ProxyRuntime.markStopped()
        scope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopSelf()
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
            .setContentText(getString(R.string.proxy_ok))
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(launch)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "passwall_vpn"
        private const val NOTIFICATION_ID = 41

        internal fun isGeodataFailure(t: Throwable): Boolean {
            val msg = (t.message ?: "") + (t.cause?.message ?: "")
            return msg.contains("geosite", ignoreCase = true) ||
                msg.contains("geoip", ignoreCase = true) ||
                msg.contains("geodata", ignoreCase = true) ||
                (msg.contains("EOF") && msg.contains("cn", ignoreCase = true))
        }
    }
}
