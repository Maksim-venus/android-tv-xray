package com.passwall.tv.vpn

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.passwall.data.log.RuntimeLog

/**
 * One-shot elapsed-realtime alarm. While the VPN is up it is pushed out every
 * [VpnRetryPolicy.HEALTHY_INTERVAL_MS]. Process death does not run [android.app.Service.onDestroy]
 * on API 25, so this alarm is what brings the process back when a box ignores START_STICKY.
 * Reboot clears alarms; [VpnRestoreReceiver] schedules a fresh restore on boot.
 */
internal object VpnWatchdog {
    private const val REQUEST = 8801

    fun schedule(context: Context, delayMs: Long, reason: String) {
        val app = context.applicationContext
        val delay = delayMs.coerceAtLeast(1_000L)
        val trigger = SystemClock.elapsedRealtime() + delay
        VpnPersist.setNextAlarmElapsed(app, trigger)
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(app)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
        } catch (t: SecurityException) {
            RuntimeLog.warn("精确闹钟不可用，改用闲时闹钟：${t.message}", "vpn")
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
        }
        if (reason.isNotEmpty()) {
            RuntimeLog.info("已安排 ${delay / 1000} 秒后检查 VPN（$reason）", "vpn")
        }
    }

    /** Keeps an earlier alarm. Returns true when a new alarm was set. */
    fun scheduleIfSooner(context: Context, delayMs: Long, reason: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        val next = VpnPersist.nextAlarmElapsed(context)
        val want = now + delayMs
        if (next > now + 250 && next <= want + 250) return false
        schedule(context, delayMs, reason)
        return true
    }

    fun scheduleHealthy(context: Context) {
        schedule(context, VpnRetryPolicy.HEALTHY_INTERVAL_MS, reason = "")
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(app))
        VpnPersist.setNextAlarmElapsed(app, 0L)
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, VpnRestoreReceiver::class.java)
            .setAction(ProxyVpnService.ACTION_RESTORE)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST, intent, flags)
    }
}
