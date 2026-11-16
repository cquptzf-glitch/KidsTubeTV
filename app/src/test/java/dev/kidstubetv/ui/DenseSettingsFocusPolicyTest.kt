package dev.kidstubetv.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DenseSettingsFocusPolicyTest {
    @Test fun leftPaneCanNavigateToSidebar() {
        assertTrue(DenseSettingsFocusPolicy.forceRailOnLeft(false))
    }

    @Test fun rightPaneRetainsNormalLeftwardRemoteNavigation() {
        assertFalse(DenseSettingsFocusPolicy.forceRailOnLeft(true))
    }
}
