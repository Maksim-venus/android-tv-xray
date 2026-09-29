package com.passwall.tv.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreWatchTest {
    @Test
    fun explicitStopDoesNotRestartCore() {
        assertFalse(
            CoreWatch.shouldRestart(
                wanted = true,
                explicitStop = true,
                watchArmed = true,
                restartInFlight = false,
                engineRunning = false,
                consecutiveSocksFailures = 0,
                coreExhausted = false,
            ),
        )
    }

    @Test
    fun deadEngineRestartsWhenWanted() {
        assertTrue(
            CoreWatch.shouldRestart(
                wanted = true,
                explicitStop = false,
                watchArmed = true,
                restartInFlight = false,
                engineRunning = false,
                consecutiveSocksFailures = 0,
                coreExhausted = false,
            ),
        )
    }

    @Test
    fun wedgedCoreNeedsTwoSocksFailures() {
        assertFalse(
            CoreWatch.shouldRestart(
                wanted = true,
                explicitStop = false,
                watchArmed = true,
                restartInFlight = false,
                engineRunning = true,
                consecutiveSocksFailures = 1,
                coreExhausted = false,
            ),
        )
        assertTrue(
            CoreWatch.shouldRestart(
                wanted = true,
                explicitStop = false,
                watchArmed = true,
                restartInFlight = false,
                engineRunning = true,
                consecutiveSocksFailures = CoreWatch.SOCKS_FAILS_BEFORE_RESTART,
                coreExhausted = false,
            ),
        )
    }

    @Test
    fun inFlightExhaustedOrDisarmedDoesNotRestart() {
        assertFalse(
            CoreWatch.shouldRestart(
                wanted = true,
                explicitStop = false,
                watchArmed = true,
                restartInFlight = true,
                engineRunning = false,
                consecutiveSocksFailures = 0,
                coreExhausted = false,
            ),
        )
        assertFalse(
            CoreWatch.shouldRestart(
                wanted = true,
                explicitStop = false,
                watchArmed = false,
                restartInFlight = false,
                engineRunning = false,
                consecutiveSocksFailures = 0,
                coreExhausted = false,
            ),
        )
        assertFalse(
            CoreWatch.shouldRestart(
                wanted = true,
                explicitStop = false,
                watchArmed = true,
                restartInFlight = false,
                engineRunning = false,
                consecutiveSocksFailures = 0,
                coreExhausted = true,
            ),
        )
        assertFalse(
            CoreWatch.shouldRestart(
                wanted = false,
                explicitStop = false,
                watchArmed = true,
                restartInFlight = false,
                engineRunning = false,
                consecutiveSocksFailures = 0,
                coreExhausted = false,
            ),
        )
    }
}
