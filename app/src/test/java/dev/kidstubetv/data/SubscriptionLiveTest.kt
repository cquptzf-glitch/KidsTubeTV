package dev.kidstubetv.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Optional live test: run with an environment variable on your own machine:
 *
 * KIDSTUBE_TEST_SUBSCRIPTION_URL=<private HTTPS URL>
 * gradle --no-daemon :app:testDebugUnitTest --tests '*SubscriptionLiveTest'
 *
 * Do not put real URLs in test files, gradle flags, git, or CI logs.
 * Without the variable this test is SKIPPED rather than falsely passing.
 */
class SubscriptionLiveTest {
    @Test fun downloadsAndValidatesProfileFromEnvironmentOnly() {
        val url = System.getenv("KIDSTUBE_TEST_SUBSCRIPTION_URL").orEmpty()
        assumeTrue("Set KIDSTUBE_TEST_SUBSCRIPTION_URL only in your local environment", url.isNotBlank())

        val result = SubscriptionProbe().check(url, vpnReady = false)
        assertTrue("Real download/format check failed: " + result.message, result.successful)
        assertFalse("Probe output leaked the URL", result.message.contains(url))
    }
}
