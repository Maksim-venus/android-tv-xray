package com.passwall.tv.vpn

/**
 * Whether Passwall should bring the VPN back after the process or [android.net.VpnService]
 * is destroyed.
 *
 * Persisted by [VpnPersist]. Explicit Stop clears [VpnAttemptState.wanted]. A start that
 * never reaches [ackRunning] (crash, SIGKILL mid-start, establish failure) increments
 * [VpnAttemptState.failures] and eventually sets [VpnAttemptState.exhausted] so a broken
 * config cannot loop forever. A kill *after* [ackRunning] does not increment failures:
 * 当贝 and similar boxes kill a working process, and that should keep reconnecting.
 */
data class VpnAttemptState(
    val wanted: Boolean = false,
    val nodeId: Long = 0,
    val failures: Int = 0,
    val exhausted: Boolean = false,
    val attemptOpen: Boolean = false,
    val lastFailureAtMs: Long = 0,
)

enum class AttemptGate {
    PROCEED,
    EXHAUSTED,
    NOT_WANTED,
}

object VpnAttemptMachine {
    const val FAILURE_DEDUPE_MS = 1_500L

    fun prepareUserStart(state: VpnAttemptState, nodeId: Long?): VpnAttemptState {
        return state.copy(
            wanted = true,
            nodeId = nodeId?.takeIf { it > 0 } ?: state.nodeId,
            failures = 0,
            exhausted = false,
            // Leave the attempt closed until VpnService actually begins. Otherwise a
            // restore that wins the race with ACTION_START counts this click as a failure.
            attemptOpen = false,
        )
    }

    fun clearWanted(state: VpnAttemptState): VpnAttemptState {
        return state.copy(
            wanted = false,
            failures = 0,
            exhausted = false,
            attemptOpen = false,
        )
    }

    fun setNodeId(state: VpnAttemptState, nodeId: Long): VpnAttemptState {
        if (nodeId <= 0) return state
        return state.copy(nodeId = nodeId)
    }

    fun beginAttempt(state: VpnAttemptState, userStart: Boolean): Pair<VpnAttemptState, AttemptGate> {
        if (userStart) {
            return state.copy(
                wanted = true,
                failures = 0,
                exhausted = false,
                attemptOpen = true,
            ) to AttemptGate.PROCEED
        }
        if (!state.wanted) return state to AttemptGate.NOT_WANTED
        if (state.exhausted || state.failures >= VpnRetryPolicy.MAX_FAILURES) {
            return state.copy(exhausted = true, attemptOpen = false) to AttemptGate.EXHAUSTED
        }
        var failures = state.failures
        if (state.attemptOpen) failures += 1
        if (failures >= VpnRetryPolicy.MAX_FAILURES) {
            return state.copy(
                failures = failures,
                exhausted = true,
                attemptOpen = false,
            ) to AttemptGate.EXHAUSTED
        }
        return state.copy(
            failures = failures,
            attemptOpen = true,
            exhausted = false,
        ) to AttemptGate.PROCEED
    }

    /**
     * Tunnel is up. Clears the open attempt so a later process kill is not a failed start.
     * Does not set [VpnAttemptState.wanted] back to true after Stop.
     * Keeps a node id already stored (the user may have picked another node mid-start).
     */
    fun ackRunning(state: VpnAttemptState, startedNodeId: Long): VpnAttemptState {
        if (!state.wanted) return state
        val id = if (state.nodeId > 0) state.nodeId else startedNodeId
        return state.copy(
            attemptOpen = false,
            failures = 0,
            exhausted = false,
            nodeId = id,
        )
    }

    fun recordHandledFailure(state: VpnAttemptState, nowMs: Long): VpnAttemptState {
        if (!state.wanted) return state
        if (state.lastFailureAtMs != 0L && nowMs - state.lastFailureAtMs < FAILURE_DEDUPE_MS) {
            return state
        }
        val failures = state.failures + 1
        return state.copy(
            failures = failures,
            exhausted = failures >= VpnRetryPolicy.MAX_FAILURES,
            attemptOpen = false,
            lastFailureAtMs = nowMs,
        )
    }

    fun exhaust(state: VpnAttemptState): VpnAttemptState {
        return state.copy(exhausted = true, attemptOpen = false)
    }

    fun shouldAutoRestart(state: VpnAttemptState, explicitStop: Boolean): Boolean {
        return state.wanted && !explicitStop && !state.exhausted
    }
}
