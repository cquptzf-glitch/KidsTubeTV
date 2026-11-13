package dev.kidstubetv.data

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the real download/validation implementation with an in-memory fake
 * transport. No real tokens, servers, or sensitive response bodies are used.
 */
class SubscriptionProbeTest {
    private val sampleUrl =
        "https://panel.example.org/api/v1/client/subscribe?token=local-test-placeholder"
    private val sampleYaml = "proxies:\n  - name: direct\n    type: direct\n"

    private class FakeConnection(
        url: URL,
        private val code: Int,
        private val data: ByteArray = byteArrayOf()
    ) : HttpURLConnection(url) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getInputStream() = ByteArrayInputStream(data)
    }

    @Test fun v2BoardStyleEndpointAcceptedWithFlClashHeadersAndNoFileWrites() {
        val visited = mutableListOf<URL>()
        val connections = mutableListOf<FakeConnection>()
        val client = SubscriptionClient { url ->
            visited.add(url)
            FakeConnection(url, 200, sampleYaml.toByteArray()).also(connections::add)
        }
        val result = SubscriptionProbe(client).check(sampleUrl, vpnReady = false)

        assertTrue(result.message, result.successful)
        assertEquals(1, visited.size)
        assertEquals(sampleUrl, visited.single().toExternalForm())
        assertEquals("FlClash", connections.single().getRequestProperty("User-Agent"))
        assertTrue(connections.single().getRequestProperty("Accept").contains("yaml"))
        assertFalse(result.message.contains("local-test-placeholder"))
        assertFalse(result.message.contains(sampleUrl))
        assertFalse(result.message.contains("name: direct"))
    }

    @Test fun reportsHttp403WithoutDisclosingTokenOrHtmlResponse() {
        val failureBody = "<html>token=local-test-placeholder</html>"
        val client = SubscriptionClient { url ->
            FakeConnection(url, 403, failureBody.toByteArray())
        }
        val result = SubscriptionProbe(client).check(sampleUrl, vpnReady = false)
        assertFalse(result.successful)
        assertTrue(result.message.contains("403"))
        assertFalse(result.message.contains("local-test-placeholder"))
        assertFalse(result.message.contains(failureBody))
    }

    @Test fun dnsFailureNeverIncludesUrlOrToken() {
        val client = SubscriptionClient { _ ->
            throw UnknownHostException("private URL token=local-test-placeholder")
        }
        val result = SubscriptionProbe(client).check(sampleUrl, vpnReady = false)
        assertFalse(result.successful)
        assertTrue(result.message.contains("DNS"))
        assertFalse(result.message.contains("local-test-placeholder"))
        assertFalse(result.message.contains(sampleUrl))
    }

    @Test fun fakeServerReturningOnlyBase64CannotBeMistakenForSuccess() {
        val client = SubscriptionClient { url ->
            FakeConnection(url, 200,
                "dm1lc3M6Ly9teS1wcml2YXRlLXNlcnZlcgo=".toByteArray())
        }
        val result = SubscriptionProbe(client).check(sampleUrl, vpnReady = true)
        assertFalse(result.successful)
        assertTrue(result.message.contains("Base64"))
    }
}
