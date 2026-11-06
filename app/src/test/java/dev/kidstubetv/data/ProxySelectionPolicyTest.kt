package dev.kidstubetv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProxySelectionPolicyTest {
    @Test fun keepsUnicodeAndAutomaticSelectorNames() {
        val valid = mapOf("🚀 节点选择" to "香港 01", "PROXY" to "自动选择")
        assertEquals(valid, ProxySelectionPolicy.sanitize(valid))
    }

    @Test fun dropsNewlinesAndOverlongValuesAndBoundsGroups() {
        val candidates = linkedMapOf("bad\nname" to "safe", "" to "empty", "good" to "node")
        assertEquals(mapOf("good" to "node"), ProxySelectionPolicy.sanitize(candidates))
        val many = (0..100).associate { "group$it" to "node$it" }
        assertEquals(64, ProxySelectionPolicy.sanitize(many).size)
        assertFalse(ProxySelectionPolicy.sanitize(mapOf("a" to "x".repeat(161))).containsKey("a"))
    }
}
