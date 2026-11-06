package dev.kidstubetv.proxy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionUpdateGateTest {
    @Test fun onlyOneUpdaterCanRunUntilRelease() {
        SubscriptionUpdateGate.release()
        try {
            assertTrue(SubscriptionUpdateGate.tryAcquire())
            assertFalse(SubscriptionUpdateGate.tryAcquire())
        } finally {
            SubscriptionUpdateGate.release()
        }
        assertTrue(SubscriptionUpdateGate.tryAcquire())
        SubscriptionUpdateGate.release()
    }
}
