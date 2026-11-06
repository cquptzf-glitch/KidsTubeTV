package dev.kidstubetv.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerVisibilityPolicyTest {
    @Test fun onlyVisibleApprovedVideosOnVpnCanPlay() {
        assertTrue(PlayerVisibilityPolicy.canPlay(true, true, true, true))
        assertFalse(PlayerVisibilityPolicy.canPlay(false, true, true, true))
        assertFalse(PlayerVisibilityPolicy.canPlay(true, false, true, true))
        assertFalse(PlayerVisibilityPolicy.canPlay(true, true, false, true))
        assertFalse(PlayerVisibilityPolicy.canPlay(true, true, true, false))
    }
}
