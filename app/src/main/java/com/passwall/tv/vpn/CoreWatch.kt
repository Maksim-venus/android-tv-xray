package com.passwall.tv.vpn

/**
 * When to restart Xray while VpnService is still alive.
 *
 * libv2ray v26.1.13's startLoop returns after core.Start() and does not call
 * shutdown() if the instance later dies. [engineRunning] is CoreController.isRunning.
 * [consecutiveSocksFailures] covers a wedged core whose flag stays true while
 * 127.0.0.1:10808 stops accepting connections.
 */
object CoreWatch {
    const val INTERVAL_MS = 3_000L
    const val SOCKS_FAILS_BEFORE_RESTART = 2
    const val STABLE_RESET_MS = 60_000L
    const val SOCKS_TIMEOUT_MS = 800

    fun shouldRestart(
        wanted: Boolean,
        explicitStop: Boolean,
        watchArmed: Boolean,
        restartInFlight: Boolean,
        engineRunning: Boolean,
        consecutiveSocksFailures: Int,
        coreExhausted: Boolean,
    ): Boolean {
        if (!wanted || explicitStop || !watchArmed || restartInFlight || coreExhausted) return false
        if (!engineRunning) return true
        return consecutiveSocksFailures >= SOCKS_FAILS_BEFORE_RESTART
    }
}
