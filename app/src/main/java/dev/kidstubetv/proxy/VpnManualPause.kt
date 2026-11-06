package dev.kidstubetv.proxy

/**
 * Process-only parent pause. A manual stop is not an error: it must not be
 * overridden by reconnect logic. App process restart starts unpaused.
 */
object VpnManualPause {
    @Volatile private var paused = false
    val isPaused: Boolean get() = paused
    fun pause() { paused = true }
    fun resume() { paused = false }
}
