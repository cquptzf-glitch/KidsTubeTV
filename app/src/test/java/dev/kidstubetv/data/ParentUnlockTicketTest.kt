package dev.kidstubetv.data

import org.junit.Assert.*
import org.junit.Test

class ParentUnlockTicketTest {
    @Test fun expiresAfterInactivityAndDoesNotRefreshExpiredTicket() {
        var clock = 1000L
        val ticket = ParentUnlockTicket({ clock }, idleTimeoutMs = 200L)
        assertFalse(ticket.authorized())
        ticket.unlock()
        clock = 1199L
        assertTrue(ticket.onInteraction())
        clock = 1300L
        assertTrue(ticket.authorized())
        clock = 1400L
        assertFalse(ticket.onInteraction())
        assertFalse(ticket.authorized())
    }

    @Test fun backgroundLockInvalidatesEvenRecentlyUsedTicket() {
        val ticket = ParentUnlockTicket({ 50L })
        ticket.unlock()
        assertTrue(ticket.authorized())
        ticket.lock()
        assertFalse(ticket.authorized())
        assertFalse(ticket.onInteraction())
    }
}
