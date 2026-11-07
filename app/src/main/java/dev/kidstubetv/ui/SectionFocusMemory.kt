package dev.kidstubetv.ui

/**
 * Remembers one remote focus position per section without retaining Android
 * View instances (which would leak Activities on TV screen rotation).
 */
internal class SectionFocusMemory(private val capacity: Int = 16) {
    init { require(capacity > 0) }
    data class Location(val focusId: String, val scrollY: Int)

    private val entries = object : LinkedHashMap<String, Location>(capacity, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, Location>
        ): Boolean = size > capacity
    }

    @Synchronized
    fun remember(section: String, focusId: String?, scrollY: Int) {
        if (section.isBlank() || focusId.isNullOrBlank() ||
            !(focusId.startsWith("hero:") || focusId.startsWith("video:"))) return
        entries[section] = Location(focusId, scrollY.coerceAtLeast(0))
    }

    @Synchronized fun recall(section: String): Location? = entries[section]
    @Synchronized fun clear() = entries.clear()
}
