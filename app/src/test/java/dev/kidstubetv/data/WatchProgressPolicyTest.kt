package dev.kidstubetv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchProgressPolicyTest {
    @Test fun shortOrMalformedProgressDoesNotResume() {
        assertEquals(0f, WatchProgressPolicy.safePosition(14.9f))
        assertEquals(0f, WatchProgressPolicy.safePosition(-1f))
        assertEquals(0f, WatchProgressPolicy.safePosition(Float.NaN))
        assertEquals(0f, WatchProgressPolicy.safePosition(Float.POSITIVE_INFINITY))
        assertEquals(123f, WatchProgressPolicy.safePosition(123f))
    }

    @Test fun onlyValidVideoIdsAndPositionsCanBeStored() {
        assertTrue(WatchProgressPolicy.canSave("dQw4w9WgXcQ", 123f))
        assertFalse(WatchProgressPolicy.canSave("INVALID", 123f))
        assertFalse(WatchProgressPolicy.canSave("dQw4w9WgXcQ", Float.NaN))
        assertFalse(WatchProgressPolicy.canSave("dQw4w9WgXcQ", 999999f))
    }
}
