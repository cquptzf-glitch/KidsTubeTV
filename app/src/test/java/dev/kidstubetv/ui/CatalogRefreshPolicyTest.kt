package dev.kidstubetv.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRefreshPolicyTest {
    @Test fun refreshesOnFirstLoadAndAfterTtl() {
        var time = 100L
        val policy = CatalogRefreshPolicy(1_000L) { time }
        assertTrue(policy.shouldRefresh())
        policy.onSuccessfulRefresh()
        assertFalse(policy.shouldRefresh())
        time = 1_099L
        assertFalse(policy.shouldRefresh())
        time = 1_100L
        assertTrue(policy.shouldRefresh())
    }

    @Test fun invalidatesAfterSettingsChangeAndCounterReset() {
        var time = 2_000L
        val policy = CatalogRefreshPolicy(1_000L) { time }
        policy.onSuccessfulRefresh()
        policy.invalidate()
        assertTrue(policy.shouldRefresh())
        policy.onSuccessfulRefresh()
        time = 1_500L
        assertTrue(policy.shouldRefresh())
    }
}
