package dev.kidstubetv.data

import org.junit.Assert.assertThrows
import org.junit.Test

class GoogleRequestPolicyTest {
    @Test fun actualApiCanBeAttemptedWhenVpnIsReadyDespiteProbeFailure() {
        GoogleRequestPolicy.requireAvailable(vpnReady = true)
    }
    @Test fun neverFallsBackToDirectConnectionWhenVpnIsDown() {
        assertThrows(IllegalStateException::class.java) {
            GoogleRequestPolicy.requireAvailable(vpnReady = false)
        }
    }
}
