package dev.kidstubetv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveScreenPolicyTest {
    @Test fun phonePortraitUsesTwoComfortableCardsAndScrollableDrawer() {
        val spec = ResponsiveScreenPolicy.forWidth(393, false)
        assertTrue(spec.handheld)
        assertTrue(spec.compactHero)
        assertEquals(14, spec.horizontalPaddingDp)
        val grid = TvGridMetrics.calculate(393, spec.horizontalPaddingDp * 2,
            spec.cardGapDp, spec.minimumCardWidthDp)
        assertEquals(2, grid.columns)
        assertTrue(spec.drawerWidthDp < 393)
    }

    @Test fun tinyPhoneFallsBackToOneCardWithoutClipping() {
        val spec = ResponsiveScreenPolicy.forWidth(300, false)
        val grid = TvGridMetrics.calculate(300, spec.horizontalPaddingDp * 2,
            spec.cardGapDp, spec.minimumCardWidthDp)
        assertEquals(1, grid.columns)
        assertTrue(grid.cardWidth <= 300)
    }

    @Test fun tvRetainsLargeRemoteCardsAndRail() {
        val spec = ResponsiveScreenPolicy.forWidth(960, true)
        assertFalse(spec.handheld)
        assertEquals(44, spec.horizontalPaddingDp)
        assertEquals(205, spec.minimumCardWidthDp)
        assertFalse(spec.compactHero)
    }

    @Test fun landscapePhoneStillUsesTouchMode() {
        val spec = ResponsiveScreenPolicy.forWidth(850, false)
        assertTrue(spec.handheld)
        assertFalse(spec.compactHero)
    }
}
