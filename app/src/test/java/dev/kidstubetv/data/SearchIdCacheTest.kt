package dev.kidstubetv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchIdCacheTest {
    @Test fun cachesSearchIdsButExpiresThem() {
        var clock = 100L
        val cache = SearchIdCache(ttlMillis = 200L, nowMillis = { clock })
        val original = mutableListOf("a", "b")
        cache.put("  ANIMALS ", original)
        original.clear()
        assertEquals(listOf("a", "b"), cache.get("animals"))
        clock = 300L
        assertNull(cache.get("animals"))
    }

    @Test fun evictsOldEntries() {
        val cache = SearchIdCache(maxEntries = 2)
        cache.put("one", listOf("1"))
        cache.put("two", listOf("2"))
        cache.get("one")
        cache.put("three", listOf("3"))
        assertNull(cache.get("two"))
        assertEquals(listOf("1"), cache.get("one"))
    }
}
