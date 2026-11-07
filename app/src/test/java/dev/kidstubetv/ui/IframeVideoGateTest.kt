package dev.kidstubetv.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IframeVideoGateTest {
    private val a = "abcdefghijk"
    private val b = "123456789ab"

    @Test fun oldVideoCannotAdvanceOrRecordHistoryOfNewVideo() {
        val gate = IframeVideoGate()
        gate.approve(a)
        assertFalse(gate.isCurrent())
        assertTrue(gate.onVideoId(a))
        assertTrue(gate.isCurrent())
        gate.approve(b)
        assertFalse(gate.isCurrent())
        assertFalse(gate.onVideoId(a))
        assertTrue(gate.hasUnapprovedReportedVideo())
        assertTrue(gate.onVideoId(b))
        assertTrue(gate.isCurrent())
    }

    @Test fun backgroundOrVpnLossInvalidatesPreviouslyApprovedFrame() {
        val gate = IframeVideoGate()
        gate.approve(a)
        gate.onVideoId(a)
        gate.revoke()
        assertFalse(gate.isCurrent())
        assertFalse(gate.onVideoId(a))
    }
}
