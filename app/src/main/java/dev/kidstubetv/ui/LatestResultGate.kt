package dev.kidstubetv.ui

/**
 * Monotonic request IDs keep stale asynchronous responses from replacing a
 * newer TV view or committing an old account's subscription picker.
 */
internal class LatestResultGate {
    private var generation = 0L

    @Synchronized fun begin(): Long {
        generation++
        return generation
    }

    @Synchronized fun invalidate() {
        generation++
    }

    @Synchronized fun isCurrent(value: Long): Boolean = value == generation
}
