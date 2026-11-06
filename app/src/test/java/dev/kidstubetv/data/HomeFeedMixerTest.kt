package dev.kidstubetv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedMixerTest {
    @Test fun alternatesChannelsAndSkipsDuplicates() {
        val groups = listOf(listOf("a1", "a2", "shared"), listOf("b1", "shared", "b3"))
        assertEquals(listOf("a1", "b1", "a2", "shared", "b3"),
            HomeFeedMixer.interleave(groups, { it }))
    }

    @Test fun handlesEmptyGroupsAndLimits() {
        assertEquals(listOf(10, 20, 11), HomeFeedMixer.interleave(
            listOf(listOf(10, 11, 12), emptyList(), listOf(20, 21)),
            { it }, 3
        ))
        assertTrue(HomeFeedMixer.interleave(emptyList<List<Int>>(), { it }).isEmpty())
        assertTrue(HomeFeedMixer.interleave(listOf(listOf(1)), { it }, 0).isEmpty())
    }
}
