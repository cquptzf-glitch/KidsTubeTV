package dev.kidstubetv.data

import android.os.SystemClock

/** Process-local parental authorization; never persisted to disk. */
object ParentSession {
    private val ticket = ParentUnlockTicket(SystemClock::elapsedRealtime)
    fun unlock() = ticket.unlock()
    fun authorized(): Boolean = ticket.authorized()
    fun onInteraction(): Boolean = ticket.onInteraction()
    fun allowExternalUi() = ticket.allowExternalUi()
    fun endExternalUi() = ticket.endExternalUi()
    fun onBackground() = ticket.onBackground()
    fun lock() = ticket.lock()
}
