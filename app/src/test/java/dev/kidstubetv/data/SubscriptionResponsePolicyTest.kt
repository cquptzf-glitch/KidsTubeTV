package dev.kidstubetv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionResponsePolicyTest {
    @Test fun acceptsRealClashYamlAndRejectsHtmlOrBase64() {
        SubscriptionResponsePolicy.validate("proxies:\n  - name: test\n    type: direct\n")
        val html = assertThrows(SubscriptionDownloadException::class.java) {
            SubscriptionResponsePolicy.validate("<html><title>Sign in</title></html>")
        }
        assertEquals(SubscriptionProblem.HTML_RESPONSE, html.problem)
        val encoded = assertThrows(SubscriptionDownloadException::class.java) {
            SubscriptionResponsePolicy.validate("dm1lc3M6Ly9teS1wcml2YXRlLXNlcnZlcgo=")
        }
        assertEquals(SubscriptionProblem.BASE64_RESPONSE, encoded.problem)
    }

    @Test fun refusesUnsafeProfilesWithoutRetryingAsAnotherUserAgent() {
        val failure = assertThrows(SubscriptionDownloadException::class.java) {
            SubscriptionResponsePolicy.validate(
                "allow-lan: true\nproxies:\n  - name: direct\n    type: direct\n"
            )
        }
        assertEquals(SubscriptionProblem.UNSAFE_PROFILE, failure.problem)
        assertTrue(!SubscriptionResponsePolicy.retryWithClashUserAgent(failure))
    }

    @Test fun retriesOnlyLikelyClientFormatRefusals() {
        listOf(403, 406, 415).forEach {
            assertTrue(SubscriptionResponsePolicy.retryWithClashUserAgent(
                SubscriptionDownloadException(SubscriptionProblem.HTTP, it)
            ))
        }
        assertTrue(!SubscriptionResponsePolicy.retryWithClashUserAgent(
            SubscriptionDownloadException(SubscriptionProblem.HTTP, 401)))
        assertTrue(!SubscriptionResponsePolicy.retryWithClashUserAgent(
            SubscriptionDownloadException(SubscriptionProblem.HTTP, 429)))
        assertTrue(SubscriptionResponsePolicy.retryWithClashUserAgent(
            SubscriptionDownloadException(SubscriptionProblem.INVALID_YAML)))
        assertTrue(!SubscriptionResponsePolicy.retryWithClashUserAgent(
            SubscriptionDownloadException(SubscriptionProblem.UNSAFE_PROFILE)))
    }
}
