package dev.kidstubetv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ApprovedPickerPolicyTest {
    @Test fun uncheckingPreviouslyApprovedGoogleChannelReallyRemovesIt() {
        val old = setOf("manual", "google-1", "google-2")
        val result = ApprovedPickerPolicy.apply(old,
            listOf("google-1", "google-2", "google-3"),
            listOf("google-2", "google-3"))
        assertEquals(setOf("manual", "google-2", "google-3"), result)
    }

    @Test fun onlyDisplayedEntriesCanBeSelected() {
        assertEquals(setOf("unchanged", "a"),
            ApprovedPickerPolicy.apply(setOf("unchanged"), listOf("a"), listOf("a","untrusted")))
    }
}
