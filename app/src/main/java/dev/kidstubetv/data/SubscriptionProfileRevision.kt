package dev.kidstubetv.data

/**
 * In-process generation gate for installing a fetched subscription.
 * The commit callback runs under the gate monitor so a provider/local-profile
 * change cannot pass its revision check until the in-progress install finishes.
 */
internal class SubscriptionProfileRevision {
    private var revision = 0L

    @Synchronized fun snapshot(): Long = revision

    @Synchronized fun isCurrent(expected: Long): Boolean = revision == expected

    @Synchronized fun invalidate() { revision++ }

    @Synchronized fun installIfCurrent(expected: Long, commit: () -> Unit): Boolean {
        if (revision != expected) return false
        // A failed file install leaves the previous profile in place. Keep this
        // revision so the caller may record its failure on the same provider.
        // A successful file install invalidates sibling downloads.
        commit()
        revision++
        return true
    }
}
