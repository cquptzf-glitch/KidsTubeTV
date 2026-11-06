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
    private var externalUiUntil = 0L

    @Synchronized fun unlock() { expiresAt = nowMs() + idleTimeoutMs }

    @Synchronized fun authorized(): Boolean =
        expiresAt != 0L && nowMs() < expiresAt

    /** Renew only an existing, still-valid parent authorization. */
    @Synchronized fun onInteraction(): Boolean {
        if (!authorized()) return false
        expiresAt = nowMs() + idleTimeoutMs
        return true
    }

    /**
     * Only a parent-initiated system document picker may temporarily background
     * this Activity. The exception expires with the idle ticket itself.
     */
    @Synchronized fun allowExternalUi() {
        if (authorized()) externalUiUntil = expiresAt
    }

    /** Bounded exception for a parent-invoked Android system picker only. */
    @Synchronized fun externalUiAuthorized(): Boolean =
        externalUiUntil != 0L && nowMs() < externalUiUntil && authorized()

    @Synchronized fun onBackground() {
        if (externalUiUntil == 0L || nowMs() >= externalUiUntil) lock()
    }

    @Synchronized fun endExternalUi() {
        externalUiUntil = 0L
    }

    @Synchronized fun lock() {
        expiresAt = 0L
        externalUiUntil = 0L
    }
}
