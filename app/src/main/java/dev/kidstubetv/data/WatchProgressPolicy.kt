package dev.kidstubetv.data

/** Local per-video resume point in seconds, independent of YouTube account. */
object WatchProgressPolicy {
    private const val MIN_RESUME = 15f
    private const val MAX_SECONDS = 6 * 60 * 60f

    fun safePosition(seconds: Float): Float {
        if (!seconds.isFinite() || seconds < MIN_RESUME || seconds > MAX_SECONDS) return 0f
        return seconds
    }

    fun canSave(id: String, seconds: Float): Boolean =
        id.matches(Regex("[A-Za-z0-9_-]{11}")) && seconds.isFinite() &&
            seconds >= 0 && seconds <= MAX_SECONDS
}
