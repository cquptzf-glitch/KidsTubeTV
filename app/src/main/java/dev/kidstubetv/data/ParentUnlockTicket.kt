package dev.kidstubetv.data

/**
 * Process-local parent PIN ticket with an idle timeout and a bounded app-switch
 * grace period. A short switch to another app does not require a new PIN, but
 * process death, an explicit lock and an extended background stay still do.
 */
class ParentUnlockTicket(
    private val nowMs: () -> Long,
    private val idleTimeoutMs: Long = 120_000L,
    private val backgroundGraceMs: Long = 180_000L
) {
    init { require(idleTimeoutMs > 0 && backgroundGraceMs > 0) }
    private var expiresAt = 0L
    private var externalUiUntil = 0L
    private var backgroundUntil = 0L

    @Synchronized fun unlock() {
        expiresAt = nowMs() + idleTimeoutMs
        backgroundUntil = 0L
        externalUiUntil = 0L
    }

    @Synchronized fun authorized(): Boolean =
        expiresAt != 0L && nowMs() < expiresAt

    /** A backgrounded app must not refresh its authorization with stray events. */
    @Synchronized fun onInteraction(): Boolean {
        if (!authorized() || backgroundUntil != 0L) return false
        expiresAt = nowMs() + idleTimeoutMs
        return true
    }

    /**
     * A parent-initiated Android document picker / VPN consent has an explicit
     * three-minute handoff window, separate from the shorter idle timer.
     * Long picker interactions should not silently discard the selected YAML.
     */
    @Synchronized fun allowExternalUi() {
        if (authorized() && backgroundUntil == 0L) {
            externalUiUntil = nowMs() + backgroundGraceMs
            expiresAt = maxOf(expiresAt, externalUiUntil)
        }
    }

    @Synchronized fun externalUiAuthorized(): Boolean =
        externalUiUntil != 0L && nowMs() < externalUiUntil && authorized()

    @Synchronized fun onBackground() {
        if (!authorized()) {
            lock()
            return
        }
        // System pickers and normal app switches are both bounded. Only a
        // parent-started system handoff can use its reserved deadline.
        val limit = if (externalUiUntil != 0L) externalUiUntil
            else nowMs() + backgroundGraceMs
        backgroundUntil = limit
        expiresAt = limit
    }

    /** Called when the first app activity becomes visible again. */
    @Synchronized fun onForeground() {
        if (backgroundUntil == 0L) return
        if (nowMs() >= backgroundUntil || !authorized()) {
            lock()
            return
        }
        // User returned promptly; normal foreground idle timeout resumes.
        backgroundUntil = 0L
        expiresAt = nowMs() + idleTimeoutMs
    }

    @Synchronized fun endExternalUi() {
        externalUiUntil = 0L
    }

    @Synchronized fun lock() {
        expiresAt = 0L
        externalUiUntil = 0L
        backgroundUntil = 0L
    }
}
