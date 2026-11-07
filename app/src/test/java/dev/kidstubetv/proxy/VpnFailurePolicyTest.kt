package dev.kidstubetv.proxy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnFailurePolicyTest {
    @Test fun masksRawCoreErrorsAndKeepsUsefulCategory() {
        val message = VpnFailurePolicy.fromSetup(
            "parse proxy foo password=mysecret https://example.com/token=private")
        assertTrue(message.contains("CONFIG_REJECTED"))
        assertFalse(message.contains("mysecret"))
        assertFalse(message.contains("private"))
    }
    @Test fun identifiesMissingProfileWithoutLeakingFileLocation() {
        val result = VpnFailurePolicy.fromException(
            IllegalArgumentException("Missing Mihomo profile /private/path"), "SETUP")
        assertTrue(result.contains("PROFILE_MISSING"))
        assertFalse(result.contains("/private"))
    }
}
