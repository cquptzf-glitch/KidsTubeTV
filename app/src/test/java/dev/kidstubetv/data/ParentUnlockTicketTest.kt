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

    @Test fun pickerKeepsTicketOnlyUntilExpiryAndNeverUnlocksByItself() {
        var clock = 100L
        val ticket = ParentUnlockTicket({ clock }, idleTimeoutMs = 200L)
        ticket.unlock()
        ticket.allowExternalUi()
        ticket.onBackground()
        assertTrue(ticket.authorized())
        assertTrue(ticket.externalUiAuthorized())
        clock = 301L
        assertFalse(ticket.authorized())
        assertFalse(ticket.externalUiAuthorized())
        ticket.endExternalUi()
        ticket.onBackground()
        assertFalse(ticket.authorized())
    }

    @Test fun normalBackgroundAlwaysLocks() {
        val ticket = ParentUnlockTicket({ 50L })
        ticket.unlock()
        ticket.onBackground()
        assertFalse(ticket.authorized())
        assertFalse(ticket.externalUiAuthorized())
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
