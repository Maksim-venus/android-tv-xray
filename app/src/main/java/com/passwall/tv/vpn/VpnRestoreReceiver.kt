package com.passwall.tv.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.passwall.data.log.RuntimeLog

/**
 * Boot, package-replaced, and the watchdog alarm. Explicit component delivery so it
 * still runs on API 25 without an implicit-broadcast exemption.
 */
class VpnRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        val reason = when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            QUICKBOOT,
            HTC_QUICKBOOT,
            -> "boot"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "updated"
            else -> "watchdog"
        }
        val lock = runCatching {
            val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "passwall:vpn-restore").apply {
                setReferenceCounted(false)
                acquire(10_000L)
            }
        }.getOrNull()
        try {
            VpnRestarter.onWake(app, reason)
        } catch (t: Throwable) {
            RuntimeLog.error("自动重连入口失败：${t.message ?: t.javaClass.simpleName}", "vpn")
        } finally {
            runCatching {
                if (lock?.isHeld == true) lock.release()
            }
        }
    }

    companion object {
        private const val QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
        private const val HTC_QUICKBOOT = "com.htc.intent.action.QUICKBOOT_POWERON"
    }
}
