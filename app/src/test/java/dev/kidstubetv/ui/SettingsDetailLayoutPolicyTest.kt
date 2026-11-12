package dev.kidstubetv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Existing TV/phone mode selection is based on device category, not orientation. */
class SettingsDetailLayoutPolicyTest {
    @Test fun tvDetailsFit720pAndWideScreensWithoutHorizontalClipping() {
        assertEquals(720, SettingsDetailLayoutPolicy.contentWidthDp(760, false))
        assertEquals(880, SettingsDetailLayoutPolicy.contentWidthDp(1280, false))
        assertEquals(780, SettingsDetailLayoutPolicy.contentWidthDp(1280, false, 780))
        assertEquals(720, SettingsDetailLayoutPolicy.contentWidthDp(760, false, 780))
        assertNull(SettingsDetailLayoutPolicy.contentWidthDp(360, true))
        assertNull(SettingsDetailLayoutPolicy.contentWidthDp(780, true))
    }

    @Test fun preservesTvVsTouchRulesAcrossRotationAndSmallScreens() {
        val tv = ResponsiveScreenPolicy.forWidth(960, television = true)
        val phonePortrait = ResponsiveScreenPolicy.forWidth(360, television = false)
        val phoneLandscape = ResponsiveScreenPolicy.forWidth(780, television = false)
        assertEquals(false, tv.handheld)
        assertEquals(true, phonePortrait.handheld)
        assertEquals(true, phoneLandscape.handheld)
        assertEquals(true, phonePortrait.horizontalPaddingDp < phoneLandscape.horizontalPaddingDp)
    }
}
