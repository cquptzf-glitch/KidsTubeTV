package dev.kidstubetv.data

import android.os.SystemClock

/** Process-local parental unlock ticket; never persisted across app restarts. */
object ParentSession {
    @Volatile private var until = 0L
    fun unlock() { until = SystemClock.elapsedRealtime() + 120_000L }
    fun authorized(): Boolean = SystemClock.elapsedRealtime() < until
    fun lock() { until = 0L }
}
