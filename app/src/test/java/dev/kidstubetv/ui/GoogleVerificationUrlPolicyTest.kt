package dev.kidstubetv.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleVerificationUrlPolicyTest {
    @Test fun allowsOnlyOfficialDeviceAuthorizationHosts() {
        assertTrue(GoogleVerificationUrlPolicy.isOfficial("https://www.google.com/device"))
        assertTrue(GoogleVerificationUrlPolicy.isOfficial("https://youtube.com/activate"))
        assertTrue(GoogleVerificationUrlPolicy.isOfficial("https://yt.be/activate"))
        assertFalse(GoogleVerificationUrlPolicy.isOfficial("http://www.google.com/device"))
        assertFalse(GoogleVerificationUrlPolicy.isOfficial("https://www.google.com.evil.org/device"))
        assertFalse(GoogleVerificationUrlPolicy.isOfficial("https://user@www.google.com/device"))
        assertFalse(GoogleVerificationUrlPolicy.isOfficial("https://www.google.com:444/device"))
        assertFalse(GoogleVerificationUrlPolicy.isOfficial("https://example.com/device"))
        assertFalse(GoogleVerificationUrlPolicy.isOfficial("wrong uri"))
    }
}
