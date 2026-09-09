package com.passwall.corexray

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingAssetPolicyTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_700_000_000_000L

    @Test
    fun refreshWhenNeverUpdated() {
        assertTrue(RoutingAssetPolicy.shouldRefresh(null, null, now))
    }

    @Test
    fun skipWhenUpdatedYesterday() {
        assertFalse(RoutingAssetPolicy.shouldRefresh(now - day, now - day, now))
    }

    @Test
    fun refreshWhenOlderThanSevenDays() {
        assertTrue(RoutingAssetPolicy.shouldRefresh(now - 8 * day, now - 8 * day, now))
    }

    @Test
    fun throttleFailedRetryForSixHours() {
        assertFalse(
            RoutingAssetPolicy.shouldRefresh(
                lastSuccessMs = null,
                lastAttemptMs = now - 2 * 60 * 60 * 1000,
                nowMs = now,
            ),
        )
        assertTrue(
            RoutingAssetPolicy.shouldRefresh(
                lastSuccessMs = null,
                lastAttemptMs = now - 7 * 60 * 60 * 1000,
                nowMs = now,
            ),
        )
    }
}
