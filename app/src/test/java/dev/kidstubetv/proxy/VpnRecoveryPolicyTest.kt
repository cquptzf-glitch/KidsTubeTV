package dev.kidstubetv.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnRecoveryPolicyTest {
    @Test fun retriesAreBoundedAndBackOff() {
        val policy = VpnRecoveryPolicy()
        assertEquals(1500L, policy.nextDelay())
        assertEquals(5000L, policy.nextDelay())
        assertEquals(15000L, policy.nextDelay())
        assertNull(policy.nextDelay())
        assertTrue(policy.exhausted)
    }

    @Test fun healthyAndManualRetryResetBudget() {
        val policy = VpnRecoveryPolicy()
        policy.nextDelay()
        policy.onHealthy()
        assertEquals(1500L, policy.nextDelay())
        policy.nextDelay()
        policy.nextDelay()
        assertNull(policy.nextDelay())
        policy.onManualRetry()
        assertEquals(1500L, policy.nextDelay())
    }
}
