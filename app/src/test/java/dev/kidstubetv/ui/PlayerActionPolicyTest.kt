package dev.kidstubetv.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerActionPolicyTest {
    @Test fun remoteAndTouchCannotPlayBeforeVerifiedIframeId() {
        assertFalse(PlayerActionPolicy.canControl(true, true, false, true, true))
        assertFalse(PlayerActionPolicy.canControl(true, false, true, true, true))
        assertTrue(PlayerActionPolicy.canControl(true, true, true, true, true))
    }

    @Test fun noPlaybackOnBackgroundVpnLossOrExpiredDailyLimit() {
        assertFalse(PlayerActionPolicy.canControl(false, true, true, true, true))
        assertFalse(PlayerActionPolicy.canControl(true, true, true, false, true))
        assertFalse(PlayerActionPolicy.canControl(true, true, true, true, false))
    }

    @Test fun staleIframeReportCannotBeManuallyResumed() {
        val gate = IframeVideoGate()
        gate.approve("abcdefghijk")
        gate.onVideoId("abcdefghijk")
        assertTrue(PlayerActionPolicy.canControl(true, true, gate.isCurrent(), true, true))
        gate.approve("123456789ab")
        assertFalse(PlayerActionPolicy.canControl(true, true, gate.isCurrent(), true, true))
        gate.onVideoId("abcdefghijk")
        assertFalse(PlayerActionPolicy.canControl(true, true, gate.isCurrent(), true, true))
    }
}
