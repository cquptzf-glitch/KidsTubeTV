package dev.kidstubetv.data

/**
 * Invalidates asynchronous Google device-code/refresh responses after logout,
 * account switch, or successful sign-in. Owns no tokens or OAuth secrets.
 */
internal class GoogleAuthSessionEpoch {
    private var epoch = 0L

    @Synchronized fun snapshot(): Long = epoch
    @Synchronized fun isCurrent(value: Long): Boolean = epoch == value
    @Synchronized fun invalidate(): Long {
        epoch++
        return epoch
    }
}
