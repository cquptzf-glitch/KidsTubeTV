package dev.kidstubetv.data

import org.junit.Assert.assertThrows
import org.junit.Test

class GoogleRequestPolicyTest {
    @Test fun allowsVpnWhileHealthUnknownOrOnline() {
        GoogleRequestPolicy.requireAvailable(vpnReady = true, knownUnreachable = false)
    }

    @Test fun blocksBeforeVpnAndAfterUnreachableHealth() {
        assertThrows(IllegalStateException::class.java) {
            GoogleRequestPolicy.requireAvailable(vpnReady = false, knownUnreachable = false)
        }
        assertThrows(IllegalStateException::class.java) {
            GoogleRequestPolicy.requireAvailable(vpnReady = true, knownUnreachable = true)
        }
    }
}
