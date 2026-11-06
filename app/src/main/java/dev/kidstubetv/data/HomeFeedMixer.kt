package dev.kidstubetv.data

/** Alternate approved channels instead of filling the TV home from one channel. */
object HomeFeedMixer {
    fun <T, K> interleave(groups: List<List<T>>, key: (T) -> K, limit: Int = 48): List<T> {
        require(limit >= 0)
        if (limit == 0 || groups.isEmpty()) return emptyList()
        val out = ArrayList<T>(limit)
        val seen = HashSet<K>()
        var step = 0
        while (out.size < limit) {
            var advanced = false
            for (group in groups) {
                if (step >= group.size) continue
                advanced = true
                val item = group[step]
                if (seen.add(key(item))) {
                    out.add(item)
                    if (out.size == limit) break
                }
            }
            if (!advanced) break
            step++
        }
        return out
    }
}
