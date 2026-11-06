package dev.kidstubetv.data

import android.os.SystemClock

/** Process-local parental authorization; never persisted to disk. */
object ParentSession {
    private val ticket = ParentUnlockTicket(SystemClock::elapsedRealtime)
    fun unlock() = ticket.unlock()
    fun authorized(): Boolean = ticket.authorized()
    fun onInteraction(): Boolean = ticket.onInteraction()
    fun lock() = ticket.lock()
}
