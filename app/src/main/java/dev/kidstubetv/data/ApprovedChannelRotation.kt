package dev.kidstubetv.data

/** Bounded, deterministic channel carousel; never widens the parent's whitelist. */
internal object ApprovedChannelRotation {
    fun select(ids: List<String>, cursor: Int, maxChannels: Int = 5): List<String> {
        require(maxChannels > 0)
        val unique = ids.distinct()
        if (unique.isEmpty()) return emptyList()
        val offset = Math.floorMod(cursor, unique.size)
        return (0 until minOf(maxChannels, unique.size)).map { index ->
            unique[(offset + index) % unique.size]
        }
    }

    fun nextCursor(size: Int, cursor: Int, maxChannels: Int = 5): Int {
        require(maxChannels > 0)
        if (size <= 0) return 0
        return (Math.floorMod(cursor, size) + minOf(size, maxChannels)) % size
    }
}
