package dev.kidstubetv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SectionFocusMemoryTest {
    @Test fun remembersDifferentSectionsAndSkipsNonVideoControls() {
        val state = SectionFocusMemory(3)
        state.remember("home", "video:abcdefghijk", 550)
        state.remember("songs", "hero:123456789ab", 180)
        state.remember("home", "settings", 5_000)
        assertEquals(SectionFocusMemory.Location("video:abcdefghijk", 550),
            state.recall("home"))
        assertEquals(SectionFocusMemory.Location("hero:123456789ab", 180),
            state.recall("songs"))
    }
    @Test fun boundedMemoryEvictsOldestAndCanClearOnWhitelistChange() {
        val state = SectionFocusMemory(2)
        state.remember("a", "video:abcdefghijk", -10)
        state.remember("b", "hero:abcdefghijk", 10)
        state.remember("c", "video:123456789ab", 40)
        assertNull(state.recall("a"))
        assertEquals(0, state.recall("b")?.scrollY?.minus(10))
        state.clear()
        assertNull(state.recall("c"))
    }
}
