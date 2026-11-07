package dev.kidstubetv.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestResultGateTest {
    @Test fun lastRequestWinsWhenResultsArriveOutOfOrder() {
        val gate = LatestResultGate()
        val first = gate.begin()
        val second = gate.begin()
        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }

    @Test fun signOutAndDestroyInvalidatePendingResult() {
        val gate = LatestResultGate()
        val request = gate.begin()
        gate.invalidate()
        assertFalse(gate.isCurrent(request))
    }
}
