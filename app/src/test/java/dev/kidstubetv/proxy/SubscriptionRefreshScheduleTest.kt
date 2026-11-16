package dev.kidstubetv.proxy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionRefreshScheduleTest {
    @Test fun periodicCheckFitsInsideShortestNonzeroConfiguredPeriod() {
        assertTrue(SubscriptionRefreshSchedule.POLL_INTERVAL_MS > 0)
        assertTrue(SubscriptionRefreshSchedule.POLL_INTERVAL_MS < 6 * 60 * 60 * 1000L)
    }
    @Test fun stopsPollingWhenVpnIsNotActive() {
        assertFalse(SubscriptionRefreshSchedule.shouldPoll(false, true))
        assertFalse(SubscriptionRefreshSchedule.shouldPoll(true, false))
        assertTrue(SubscriptionRefreshSchedule.shouldPoll(true, true))
    }
}
