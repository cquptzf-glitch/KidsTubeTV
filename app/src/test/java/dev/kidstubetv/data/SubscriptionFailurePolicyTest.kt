package dev.kidstubetv.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SubscriptionFailurePolicyTest {
    @Test fun networkErrorsAreActionableButDoNotLeakUrls() {
        val token = "private-token-do-not-show"
        val errors = listOf(
            UnknownHostException("https://provider.example/?token=$token"),
            SocketTimeoutException("https://provider.example/?token=$token"),
            IllegalArgumentException("Malformed URL with $token"),
            IllegalStateException("Unexpected body $token")
        )
        errors.forEach {
            val status = SubscriptionFailurePolicy.describe(it, vpnReady = false)
            assertFalse(status, status.contains(token))
            assertFalse(status, status.contains("https://provider.example"))
        }
    }

    @Test fun mapsHttpAndFormatFailuresToUsefulMessages() {
        assertTrue(SubscriptionFailurePolicy.describe(
            SubscriptionDownloadException(SubscriptionProblem.HTTP, 403), true).contains("403"))
        assertTrue(SubscriptionFailurePolicy.describe(
            SubscriptionDownloadException(SubscriptionProblem.BASE64_RESPONSE), true).contains("Base64"))
        assertTrue(SubscriptionFailurePolicy.describe(
            SubscriptionDownloadException(SubscriptionProblem.HTML_RESPONSE), true).contains("网页"))
        assertTrue(SubscriptionFailurePolicy.describe(
            SubscriptionDownloadException(SubscriptionProblem.UNSAFE_PROFILE), true).contains("安全"))
    }
}
