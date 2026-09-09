package com.passwall.corexray

object RoutingAssetPolicy {
    const val UPDATE_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
    const val RETRY_AFTER_FAILURE_MS = 6L * 60 * 60 * 1000

    /**
     * Refresh when there has never been a successful update, or the last
     * success is older than 7 days. Failed attempts are retried at most
     * once every 6 hours so a firewalled box does not re-download 30MB
     * on every VPN start.
     */
    fun shouldRefresh(
        lastSuccessMs: Long?,
        lastAttemptMs: Long?,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val stale = lastSuccessMs == null || nowMs - lastSuccessMs >= UPDATE_INTERVAL_MS
        if (!stale) return false
        if (lastAttemptMs != null && nowMs - lastAttemptMs < RETRY_AFTER_FAILURE_MS) return false
        return true
    }
}
