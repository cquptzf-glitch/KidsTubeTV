package dev.kidstubetv.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ApiProbeErrorPolicyTest {
    @Test fun networkFailuresAreActionableWithoutLeakingSecrets() {
        assertTrue(ApiProbeErrorPolicy.describe(UnknownHostException("key=secret")).contains("DNS"))
        assertTrue(ApiProbeErrorPolicy.describe(SocketTimeoutException("token=secret")).contains("超时"))
        assertFalse(ApiProbeErrorPolicy.describe(IllegalStateException(
            "https://example.com?key=private-token")).contains("private-token"))
    }
    @Test fun knownYouTubeApiErrorsRemainReadable() {
        assertTrue(ApiProbeErrorPolicy.describe(IllegalStateException(
            "YouTube API 配额已用尽，请检查 Google Cloud 配额")).contains("配额"))
    }
}
