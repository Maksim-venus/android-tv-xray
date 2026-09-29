package com.passwall.tv.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnAttemptMachineTest {
    @Test
    fun explicitStopDoesNotAutoRestart() {
        val running = VpnAttemptState(wanted = true, nodeId = 3)
        val stopped = VpnAttemptMachine.clearWanted(running)
        assertFalse(stopped.wanted)
        assertFalse(stopped.attemptOpen)
        assertFalse(VpnAttemptMachine.shouldAutoRestart(stopped, explicitStop = true))
        assertFalse(VpnAttemptMachine.shouldAutoRestart(stopped, explicitStop = false))
        val (_, gate) = VpnAttemptMachine.beginAttempt(stopped, userStart = false)
        assertEquals(AttemptGate.NOT_WANTED, gate)
    }

    @Test
    fun killAfterHealthyStartKeepsReconnectingWithoutCountingFailures() {
        var state = VpnAttemptState(wanted = true, nodeId = 7)
        repeat(10) {
            val (begun, gate) = VpnAttemptMachine.beginAttempt(state, userStart = false)
            assertEquals(AttemptGate.PROCEED, gate)
            assertEquals(0, begun.failures)
            state = VpnAttemptMachine.ackRunning(begun, 7)
            assertFalse(state.attemptOpen)
            assertEquals(0, state.failures)
            assertTrue(VpnAttemptMachine.shouldAutoRestart(state, explicitStop = false))
        }
    }

    @Test
    fun sixUnackedKillsThenStop() {
        var state = VpnAttemptState(wanted = true, nodeId = 1)
        var proceeds = 0
        repeat(8) {
            val (next, gate) = VpnAttemptMachine.beginAttempt(state, userStart = false)
            state = next
            if (gate == AttemptGate.PROCEED) {
                proceeds += 1
            } else {
                assertEquals(AttemptGate.EXHAUSTED, gate)
            }
        }
        assertEquals(VpnRetryPolicy.MAX_FAILURES, proceeds)
        assertTrue(state.exhausted)
        assertFalse(state.attemptOpen)
        assertFalse(VpnAttemptMachine.shouldAutoRestart(state, explicitStop = false))
    }

    @Test
    fun handledFailureDoesNotDoubleCountOnNextBegin() {
        var state = VpnAttemptState(wanted = true, nodeId = 2)
        var now = 10_000L
        repeat(VpnRetryPolicy.MAX_FAILURES) { index ->
            val (begun, gate) = VpnAttemptMachine.beginAttempt(state, userStart = false)
            assertEquals(AttemptGate.PROCEED, gate)
            now += 5_000L
            state = VpnAttemptMachine.recordHandledFailure(begun, now)
            assertEquals(index + 1, state.failures)
            assertFalse(state.attemptOpen)
        }
        assertTrue(state.exhausted)
        val (_, gate) = VpnAttemptMachine.beginAttempt(state, userStart = false)
        assertEquals(AttemptGate.EXHAUSTED, gate)
    }

    @Test
    fun revokeAndStartFailureInsideTheWindowCountOnce() {
        val open = VpnAttemptState(wanted = true, attemptOpen = true)
        val once = VpnAttemptMachine.recordHandledFailure(open, 1_000L)
        val again = VpnAttemptMachine.recordHandledFailure(once, 1_000L + 500L)
        assertEquals(1, again.failures)
        val later = VpnAttemptMachine.recordHandledFailure(again, 1_000L + VpnAttemptMachine.FAILURE_DEDUPE_MS)
        assertEquals(2, later.failures)
    }

    @Test
    fun userStartClearsExhaustion() {
        val exhausted = VpnAttemptState(
            wanted = false,
            failures = VpnRetryPolicy.MAX_FAILURES,
            exhausted = true,
            attemptOpen = false,
            nodeId = 9,
        )
        val prepared = VpnAttemptMachine.prepareUserStart(exhausted, nodeId = null)
        assertFalse(prepared.attemptOpen)
        assertEquals(0, prepared.failures)
        val (begun, gate) = VpnAttemptMachine.beginAttempt(prepared, userStart = true)
        assertEquals(AttemptGate.PROCEED, gate)
        assertTrue(begun.wanted)
        assertEquals(0, begun.failures)
        assertFalse(begun.exhausted)
        assertEquals(9L, begun.nodeId)
    }

    @Test
    fun ackDoesNotResurrectAfterStop() {
        val stopped = VpnAttemptMachine.clearWanted(VpnAttemptState(wanted = true, nodeId = 4, attemptOpen = true))
        val acked = VpnAttemptMachine.ackRunning(stopped, startedNodeId = 4)
        assertFalse(acked.wanted)
        assertFalse(VpnAttemptMachine.shouldAutoRestart(acked, explicitStop = false))
    }

    @Test
    fun ackKeepsNewerSelection() {
        val state = VpnAttemptState(wanted = true, nodeId = 9, attemptOpen = true, failures = 2)
        val acked = VpnAttemptMachine.ackRunning(state, startedNodeId = 3)
        assertEquals(9L, acked.nodeId)
        assertEquals(0, acked.failures)
        val fresh = VpnAttemptMachine.ackRunning(state.copy(nodeId = 0), startedNodeId = 3)
        assertEquals(3L, fresh.nodeId)
    }

    @Test
    fun nonRetryableExhaustStopsEvenWithFewFailures() {
        val failed = VpnAttemptMachine.recordHandledFailure(VpnAttemptState(wanted = true), nowMs = 50)
        val stopped = VpnAttemptMachine.exhaust(failed)
        assertEquals(1, stopped.failures)
        assertTrue(stopped.exhausted)
        assertFalse(VpnAttemptMachine.shouldAutoRestart(stopped, explicitStop = false))
    }

    @Test
    fun backoffLadderThenCap() {
        assertEquals(VpnRetryPolicy.DESTROY_RESTART_MS, VpnRetryPolicy.delayFor(0))
        assertEquals(2_000L, VpnRetryPolicy.delayFor(1))
        assertEquals(5_000L, VpnRetryPolicy.delayFor(2))
        assertEquals(15_000L, VpnRetryPolicy.delayFor(3))
        assertEquals(30_000L, VpnRetryPolicy.delayFor(4))
        assertEquals(60_000L, VpnRetryPolicy.delayFor(5))
        assertEquals(120_000L, VpnRetryPolicy.delayFor(6))
        assertEquals(120_000L, VpnRetryPolicy.delayFor(99))
        assertFalse(VpnRetryPolicy.exhausted(5))
        assertTrue(VpnRetryPolicy.exhausted(6))
    }
}
