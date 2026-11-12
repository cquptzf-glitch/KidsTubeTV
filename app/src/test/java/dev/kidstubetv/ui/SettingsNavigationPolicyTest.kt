package dev.kidstubetv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsNavigationPolicyTest {
    @Test fun routeIdsAreStableForExistingNetworkEntry() {
        assertEquals(SettingsDestination.OVERVIEW, SettingsDestination.fromIndex(0))
        assertEquals(SettingsDestination.NETWORK, SettingsDestination.fromIndex(1))
        assertEquals(SettingsDestination.YOUTUBE, SettingsDestination.fromIndex(2))
        assertEquals(SettingsDestination.CONTENT, SettingsDestination.fromIndex(3))
        assertEquals(SettingsDestination.PARENT, SettingsDestination.fromIndex(4))
    }

    @Test fun invalidRoutesGoToOverviewInsteadOfOpeningSensitiveSettings() {
        assertEquals(SettingsDestination.OVERVIEW, SettingsDestination.fromIndex(-1))
        assertEquals(SettingsDestination.OVERVIEW, SettingsDestination.fromIndex(999))
    }

    @Test fun backFromDetailReturnsToOverviewBeforeLeavingSettings() {
        for (page in SettingsDestination.entries.filter { it != SettingsDestination.OVERVIEW }) {
            assertEquals(SettingsDestination.OVERVIEW, SettingsNavigationPolicy.back(page))
        }
        assertNull(SettingsNavigationPolicy.back(SettingsDestination.OVERVIEW))
    }
}
