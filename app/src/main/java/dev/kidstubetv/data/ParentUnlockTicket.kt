package dev.kidstubetv.data

/**
 * Idle-time parent PIN ticket. The clock is monotonic to resist wall-clock
 * changes; the app invalidates this ticket when all activities background.
 */
class ParentUnlockTicket(
    private val nowMs: () -> Long,
    private val idleTimeoutMs: Long = 120_000L
) {
    init { require(idleTimeoutMs > 0) }
    private var expiresAt = 0L

    @Synchronized fun unlock() { expiresAt = nowMs() + idleTimeoutMs }

    @Synchronized fun authorized(): Boolean =
        expiresAt != 0L && nowMs() < expiresAt

    /** Renew only an existing, still-valid parent authorization. */
    @Synchronized fun onInteraction(): Boolean {
        if (!authorized()) return false
        expiresAt = nowMs() + idleTimeoutMs
        return true
    }

    @Synchronized fun lock() { expiresAt = 0L }
}
