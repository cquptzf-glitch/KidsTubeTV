package dev.kidstubetv.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAuthSessionEpochTest {
    @Test fun logoutWhileDeviceCodePollIsInFlightMustInvalidateResponse() {
        val state = GoogleAuthSessionEpoch()
        val polling = state.snapshot()
        state.invalidate() // parent signs out while old token request is blocked
        assertFalse(state.isCurrent(polling))
        assertTrue(state.isCurrent(state.snapshot()))
    }

    @Test fun switchingClientsAndSigningInRevokesOldRequests() {
        val state = GoogleAuthSessionEpoch()
        val oldClient = state.snapshot()
        state.invalidate() // Google TV client configuration changed
        val newClient = state.snapshot()
        assertFalse(state.isCurrent(oldClient))
        assertTrue(state.isCurrent(newClient))
        state.invalidate() // a later successful login
        assertFalse(state.isCurrent(newClient))
    }
}
