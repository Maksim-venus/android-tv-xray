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
import com.passwall.corexray.XrayConfigGenerator
import com.passwall.tv.BuildConfig
import com.passwall.tv.MainActivity
import com.passwall.tv.PasswallApp
import com.passwall.tv.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

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
        val generated = XrayConfigGenerator.generate(
            node = node,
            settings = settings,
            enableIpv6 = BuildConfig.ENABLE_IPV6,
        )
        val configFile = File(filesDir, "xray-config.json")
        try {
            val builder = Builder()
                .setSession("Passwall")
                .addAddress("10.0.85.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("223.5.5.5")
                .setMtu(1500)
                .setBlocking(false)
            if (BuildConfig.ENABLE_IPV6) {
                builder.addAddress("fd00:85::2", 128)
                builder.addRoute("::", 0)
            }
            // Avoid API 29+ Builder helpers so the same path stays valid on Android 9 / kernel 4.x.
            tun?.close()
            tun = builder.establish()
            app.engine.start(generated, configFile, tun)
            ProxyRuntime.markStarted(getString(R.string.proxy_ok))
        } catch (t: Throwable) {
            ProxyRuntime.markError(t.message ?: t.javaClass.simpleName)
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
    }
}
