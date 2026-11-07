package dev.kidstubetv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ApprovedChannelRotationTest {
    @Test fun cyclesAllApprovedChannelsAcrossRefreshes() {
        val ids = (1..9).map { "channel-$it" }
        var cursor = 0
        val windows = mutableListOf<List<String>>()
        repeat(9) {
            windows.add(ApprovedChannelRotation.select(ids, cursor, 5))
            cursor = ApprovedChannelRotation.nextCursor(ids.size, cursor, 5)
        }
        assertEquals(9, windows.flatten().toSet().size)
        assertEquals(5, windows.first().size)
        assertEquals(listOf("channel-6","channel-7","channel-8","channel-9","channel-1"),
            windows[1])
    }

    @Test fun doesNotDuplicateOrWidenSmallWhitelist() {
        assertEquals(listOf("a", "b"),
            ApprovedChannelRotation.select(listOf("a", "b", "a"), 0, 5))
        assertEquals(listOf("b", "a"),
            ApprovedChannelRotation.select(listOf("a", "b"), 1, 5))
        assertEquals(emptyList<String>(), ApprovedChannelRotation.select(emptyList(), 1))
    }

    @Test fun handlesStoredCursorFromOlderLargerWhitelist() {
        assertEquals(listOf("c","a"), ApprovedChannelRotation.select(
            listOf("a","b","c"), 99, 2))
        assertEquals(1, ApprovedChannelRotation.nextCursor(3, 99, 1))
    }
}
