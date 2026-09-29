package com.passwall.tv.vpn

import android.content.Context
import com.passwall.data.log.RuntimeLog

/**
 * Durable "user wants the VPN on" flag plus the last selected node id.
 * Uses commit(), not apply(), so a kill immediately after Start/Stop still sees the flag.
 */
internal object VpnPersist {
    private const val PREF = "passwall_vpn_persist"
    private const val KEY_WANTED = "wanted"
    private const val KEY_NODE_ID = "node_id"
    private const val KEY_FAILURES = "failures"
    private const val KEY_EXHAUSTED = "exhausted"
    private const val KEY_ATTEMPT_OPEN = "attempt_open"
    private const val KEY_LAST_FAILURE_AT = "last_failure_at"
    private const val KEY_NEXT_ALARM = "next_alarm_elapsed"
    private const val KEY_CONSENT_UI_AT = "consent_ui_elapsed"
    private const val KEY_TOAST_AT = "toast_elapsed"

    private val lock = Any()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun read(context: Context): VpnAttemptState = synchronized(lock) {
        readLocked(prefs(context))
    }

    fun isWanted(context: Context): Boolean = read(context).wanted

    fun isExhausted(context: Context): Boolean = read(context).exhausted

    fun nodeId(context: Context): Long = read(context).nodeId

    fun prepareUserStart(context: Context, nodeId: Long?) {
        update(context) { VpnAttemptMachine.prepareUserStart(it, nodeId) }
    }

    /** @return true when the VPN had been wanted before this Stop. */
    fun clearWanted(context: Context): Boolean = synchronized(lock) {
        val p = prefs(context)
        val before = readLocked(p)
        writeLocked(p, VpnAttemptMachine.clearWanted(before))
        before.wanted
    }

    fun setNodeId(context: Context, nodeId: Long) {
        update(context) { VpnAttemptMachine.setNodeId(it, nodeId) }
    }

    fun beginAttempt(context: Context, userStart: Boolean): AttemptGate = synchronized(lock) {
        val p = prefs(context)
        val before = readLocked(p)
        val (next, gate) = VpnAttemptMachine.beginAttempt(before, userStart)
        writeLocked(p, next)
        if (!userStart && next.failures > before.failures) {
            RuntimeLog.warn(
                "上次启动未完成就被系统结束，累计失败 ${next.failures} 次",
                "vpn",
            )
        }
        if (gate == AttemptGate.EXHAUSTED) {
            RuntimeLog.error(
                "自动重连已停止：连续 ${next.failures} 次未能拉起 VPN。请手动点「启动」。",
                "vpn",
            )
        }
        gate
    }

    fun ackRunning(context: Context, startedNodeId: Long) {
        update(context) { VpnAttemptMachine.ackRunning(it, startedNodeId) }
    }

    /** @return the new state and whether this call incremented the failure count. */
    fun recordHandledFailure(context: Context, nowMs: Long): Pair<VpnAttemptState, Boolean> =
        synchronized(lock) {
            val p = prefs(context)
            val before = readLocked(p)
            val after = VpnAttemptMachine.recordHandledFailure(before, nowMs)
            writeLocked(p, after)
            after to (after.failures != before.failures)
        }

    fun exhaust(context: Context) {
        update(context) { VpnAttemptMachine.exhaust(it) }
    }

    fun nextAlarmElapsed(context: Context): Long = synchronized(lock) {
        prefs(context).getLong(KEY_NEXT_ALARM, 0L)
    }

    fun setNextAlarmElapsed(context: Context, elapsed: Long) = synchronized(lock) {
        prefs(context).edit().putLong(KEY_NEXT_ALARM, elapsed).commit()
    }

    fun shouldPromptConsentUi(context: Context, nowElapsed: Long): Boolean = synchronized(lock) {
        val last = prefs(context).getLong(KEY_CONSENT_UI_AT, 0L)
        last == 0L || nowElapsed - last >= VpnRetryPolicy.CONSENT_UI_COOLDOWN_MS
    }

    fun markConsentUiPrompted(context: Context, nowElapsed: Long) = synchronized(lock) {
        prefs(context).edit().putLong(KEY_CONSENT_UI_AT, nowElapsed).commit()
    }

    fun shouldToast(context: Context, nowElapsed: Long): Boolean = synchronized(lock) {
        val last = prefs(context).getLong(KEY_TOAST_AT, 0L)
        last == 0L || nowElapsed - last >= VpnRetryPolicy.TOAST_COOLDOWN_MS
    }

    fun markToasted(context: Context, nowElapsed: Long) = synchronized(lock) {
        prefs(context).edit().putLong(KEY_TOAST_AT, nowElapsed).commit()
    }

    private inline fun update(context: Context, block: (VpnAttemptState) -> VpnAttemptState) {
        synchronized(lock) {
            val p = prefs(context)
            writeLocked(p, block(readLocked(p)))
        }
    }

    private fun readLocked(p: android.content.SharedPreferences): VpnAttemptState {
        return VpnAttemptState(
            wanted = p.getBoolean(KEY_WANTED, false),
            nodeId = p.getLong(KEY_NODE_ID, 0L),
            failures = p.getInt(KEY_FAILURES, 0),
            exhausted = p.getBoolean(KEY_EXHAUSTED, false),
            attemptOpen = p.getBoolean(KEY_ATTEMPT_OPEN, false),
            lastFailureAtMs = p.getLong(KEY_LAST_FAILURE_AT, 0L),
        )
    }

    private fun writeLocked(p: android.content.SharedPreferences, state: VpnAttemptState) {
        p.edit()
            .putBoolean(KEY_WANTED, state.wanted)
            .putLong(KEY_NODE_ID, state.nodeId)
            .putInt(KEY_FAILURES, state.failures)
            .putBoolean(KEY_EXHAUSTED, state.exhausted)
            .putBoolean(KEY_ATTEMPT_OPEN, state.attemptOpen)
            .putLong(KEY_LAST_FAILURE_AT, state.lastFailureAtMs)
            .commit()
    }
}
