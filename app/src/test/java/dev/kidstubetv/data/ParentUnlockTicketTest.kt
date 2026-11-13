package dev.kidstubetv.data

import org.junit.Assert.*
import org.junit.Test

class ParentUnlockTicketTest {
    @Test fun expiresAfterForegroundInactivityAndNeverRefreshesExpiredTicket() {
        var clock = 1000L
        val ticket = ParentUnlockTicket({ clock }, idleTimeoutMs = 200L, backgroundGraceMs = 300L)
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

    @Test fun quickAppSwitchPreservesParentPinButRestartsIdleClock() {
        var clock = 10_000L
        val ticket = ParentUnlockTicket({ clock }, idleTimeoutMs = 200L, backgroundGraceMs = 300L)
        ticket.unlock()
        clock = 10_070L
        ticket.onBackground()
        clock = 10_250L
        assertTrue(ticket.authorized())
        assertFalse(ticket.onInteraction()) // Cannot silently renew from background
        ticket.onForeground()
        assertTrue(ticket.authorized())
        clock = 10_449L
        assertTrue(ticket.authorized())
        clock = 10_450L
        assertFalse(ticket.authorized())
    }

    @Test fun longBackgroundOrExpiredTicketRequiresParentPinAgain() {
        var clock = 1_000L
        val ticket = ParentUnlockTicket({ clock }, idleTimeoutMs = 200L, backgroundGraceMs = 300L)
        ticket.unlock()
        ticket.onBackground()
        clock = 1_301L
        ticket.onForeground()
        assertFalse(ticket.authorized())
        assertFalse(ticket.onInteraction())
        ticket.unlock()
        clock = 1_502L
        ticket.onBackground()
        assertFalse(ticket.authorized())
    }

    @Test fun systemPickerKeepsOriginalShorterAuthorizationBoundary() {
        var clock = 100L
        val ticket = ParentUnlockTicket({ clock }, idleTimeoutMs = 200L, backgroundGraceMs = 500L)
        ticket.unlock()
        ticket.allowExternalUi()
        ticket.onBackground()
        clock = 299L
        assertTrue(ticket.externalUiAuthorized())
        ticket.onForeground()
        ticket.endExternalUi()
        assertTrue(ticket.authorized())
        clock = 301L
        ticket.allowExternalUi()
        ticket.onBackground()
        clock = 502L
        assertFalse(ticket.externalUiAuthorized())
        ticket.onForeground()
        assertFalse(ticket.authorized())
    }

    @Test fun explicitLockAndNewProcessNeverReuseParentSession() {
        val ticket = ParentUnlockTicket({ 50L })
        ticket.unlock()
        ticket.onBackground()
        ticket.lock()
        ticket.onForeground()
        assertFalse(ticket.authorized())
        assertFalse(ticket.onInteraction())
        assertFalse(ParentUnlockTicket({ 50L }).authorized())
    }
}
