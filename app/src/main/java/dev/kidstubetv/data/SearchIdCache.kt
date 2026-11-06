package dev.kidstubetv.data

/** Bounded in-memory search-ID cache; admission metadata is NEVER cached. */
class SearchIdCache(
    private val ttlMillis: Long = 10 * 60 * 1000L,
    private val maxEntries: Int = 48,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    init {
        require(ttlMillis > 0 && maxEntries > 0)
    }
    private data class Entry(val ids: List<String>, val storedAt: Long)
    private val map = object : LinkedHashMap<String, Entry>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > maxEntries
    }

    @Synchronized fun get(query: String): List<String>? {
        val key = query.trim().lowercase()
        val item = map[key] ?: return null
        if (nowMillis() - item.storedAt >= ttlMillis || nowMillis() < item.storedAt) {
            map.remove(key)
            return null
        }
        return item.ids.toList()
    }

    @Synchronized fun put(query: String, ids: List<String>) {
        map[query.trim().lowercase()] = Entry(ids.toList(), nowMillis())
    }

    @Synchronized fun clear() { map.clear() }
}
