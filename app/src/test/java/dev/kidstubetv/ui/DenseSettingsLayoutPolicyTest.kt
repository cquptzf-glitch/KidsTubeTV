package dev.kidstubetv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DenseSettingsLayoutPolicyTest {
    @Test fun tvLandscapeUsesTwoFunctionalPanes() {
        assertTrue(DenseSettingsLayoutPolicy.twoColumns(false, 1280))
        assertTrue(DenseSettingsLayoutPolicy.twoColumns(false, 960))
        assertEquals(4, DenseSettingsLayoutPolicy.statsPerRow(false))
    }

    @Test fun phonesUseTopNavigationAndStackedPanes() {
        assertFalse(DenseSettingsLayoutPolicy.twoColumns(true, 360))
        assertFalse(DenseSettingsLayoutPolicy.twoColumns(true, 800))
        assertEquals(2, DenseSettingsLayoutPolicy.statsPerRow(true))
    }

    @Test fun constrainedTvWidthAvoidsUnreadableSideBySideCards() {
        assertFalse(DenseSettingsLayoutPolicy.twoColumns(false, 600))
        assertTrue(DenseSettingsLayoutPolicy.twoColumns(false, -1))
    }
}
