package dev.kidstubetv.proxy

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemDnsPolicyTest {
    @Test fun forwardsUnderlyingResolversWithoutTunnelLoopsOrDuplicates() {
        assertEquals(
            listOf("192.168.1.1", "2001:4860:4860::8888"),
            SystemDnsPolicy.normalize(
                listOf(" 192.168.1.1 ", "198.18.0.2", "192.168.1.1",
                    "2001:4860:4860::8888", "", "bad,malformed", "fe80::1%wlan0"),
                "198.18.0.2"
            )
        )
    }
    @Test fun handlesMissingConnectivitySafely() {
        assertEquals(emptyList<String>(), SystemDnsPolicy.normalize(emptyList(), "198.18.0.2"))
    }
}
