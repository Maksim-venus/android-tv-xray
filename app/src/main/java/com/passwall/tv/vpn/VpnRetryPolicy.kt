package com.passwall.tv.vpn

/**
 * Backoff for failed VPN starts. A kill after the tunnel is up does not use this
 * ladder — see [VpnAttemptMachine].
 */
object VpnRetryPolicy {
    const val MAX_FAILURES = 6
    const val DESTROY_RESTART_MS = 2_000L
    const val HEALTHY_INTERVAL_MS = 15_000L
    const val CONSENT_UI_COOLDOWN_MS = 3 * 60_000L
    const val TOAST_COOLDOWN_MS = 60_000L

    private val BACKOFF_MS = longArrayOf(
        2_000L,
        5_000L,
        15_000L,
        30_000L,
        60_000L,
        120_000L,
    )

    /** [consecutiveFailures] is the count after a failed attempt. Zero means "service died while healthy". */
    fun delayFor(consecutiveFailures: Int): Long {
        if (consecutiveFailures <= 0) return DESTROY_RESTART_MS
        val index = (consecutiveFailures - 1).coerceAtMost(BACKOFF_MS.lastIndex)
        return BACKOFF_MS[index]
    }

    fun exhausted(consecutiveFailures: Int): Boolean = consecutiveFailures >= MAX_FAILURES
}
