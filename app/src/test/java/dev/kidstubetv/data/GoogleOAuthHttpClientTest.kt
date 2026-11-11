package dev.kidstubetv.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleOAuthHttpClientTest {
    private class StubConnection(
        url: URL,
        private val status: Int = 200,
        private val body: String = "{}",
    ) : HttpURLConnection(url) {
        val outgoing = ByteArrayOutputStream()
        var closed = false
        override fun connect() {}
        override fun disconnect() { closed = true }
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
        override fun getErrorStream() = ByteArrayInputStream(body.toByteArray())
        override fun getOutputStream() = outgoing
    }

    @Test fun refusesNonGoogleEndpointsBeforeOpeningConnection() {
        var opened = 0
        val client = GoogleOAuthHttpClient({ true }) { url ->
            opened++
            StubConnection(url)
        }
        listOf(
            "http://www.googleapis.com/youtube/v3/subscriptions",
            "https://www.googleapis.com.evil.example/youtube/v3/subscriptions",
            "https://user:pass@www.googleapis.com/youtube/v3/subscriptions",
            "https://www.googleapis.com/youtube/v3/videos",
            "https://www.googleapis.com:444/youtube/v3/subscriptions",
        ).forEach { url ->
            assertThrows(IllegalArgumentException::class.java) {
                client.getSubscriptions(url, "private-bearer")
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            client.post("https://attacker.example/token", mapOf("secret" to "private"))
        }
        assertEquals(0, opened)
    }

    @Test fun refusesMissingVpnBeforeReadingCredentialsOrOpeningNetwork() {
        var opened = false
        val client = GoogleOAuthHttpClient({ false }) { url ->
            opened = true
            StubConnection(url)
        }
        assertThrows(IllegalStateException::class.java) {
            client.post("https://oauth2.googleapis.com/token", mapOf("refresh_token" to "private"))
        }
        assertFalse(opened)
    }

    @Test fun detectsVpnRevocationBeforeSendingPostBody() {
        var calls = 0
        val connection = StubConnection(URL("https://oauth2.googleapis.com/token"))
        val client = GoogleOAuthHttpClient(
            vpnReady = { ++calls == 1 },
            openConnection = { connection },
        )
        assertThrows(IllegalStateException::class.java) {
            client.post("https://oauth2.googleapis.com/token", mapOf("refresh_token" to "private"))
        }
        assertEquals(0, connection.outgoing.size())
        assertTrue(connection.closed)
    }

    @Test fun subscriptionBearerIsOnlyUsedWithStrictHostAndRedirectsDisabled() {
        val connection = StubConnection(
            URL("https://www.googleapis.com/youtube/v3/subscriptions"),
            body = """{"items":[{"id":"a"}]}""",
        )
        val client = GoogleOAuthHttpClient({ true }) { connection }
        val (status, json) = client.getSubscriptions(
            "https://www.googleapis.com/youtube/v3/subscriptions?mine=true",
            "private-bearer",
        )
        assertEquals(200, status)
        assertEquals(1, json.getJSONArray("items").length())
        assertEquals("Bearer private-bearer", connection.getRequestProperty("Authorization"))
        assertFalse(connection.instanceFollowRedirects)
        assertTrue(connection.closed)
    }

    @Test fun rejectsRedirectResponsesWithoutReadingAttackerBody() {
        val conn = StubConnection(
            URL("https://www.googleapis.com/youtube/v3/subscriptions"),
            status = 302,
            body = "<html>should not follow</html>",
        )
        val client = GoogleOAuthHttpClient({ true }) { conn }
        val error = assertThrows(IllegalStateException::class.java) {
            client.getSubscriptions(
                "https://www.googleapis.com/youtube/v3/subscriptions", "private"
            )
        }
        assertTrue(error.message.orEmpty().contains("HTTP 跳转"))
        assertFalse(conn.instanceFollowRedirects)
        assertTrue(conn.closed)
    }

    @Test fun boundsResponseAndNeverIncludesUntrustedBodyInException() {
        val oversized = StubConnection(
            URL("https://oauth2.googleapis.com/token"),
            body = "secret=" + "x".repeat(GoogleOAuthHttpClient.MAX_RESPONSE_BYTES),
        )
        val client = GoogleOAuthHttpClient({ true }) { oversized }
        val error = assertThrows(IllegalStateException::class.java) {
            client.post("https://oauth2.googleapis.com/token", mapOf("client_secret" to "private"))
        }
        assertTrue(error.message.orEmpty().contains("响应过大"))
        assertFalse(error.message.orEmpty().contains("secret="))
        assertTrue(oversized.closed)
    }

    @Test fun malformedResponseProducesSanitizedError() {
        val conn = StubConnection(
            URL("https://oauth2.googleapis.com/device/code"),
            body = "<html>token=private</html>",
        )
        val client = GoogleOAuthHttpClient({ true }) { conn }
        val error = assertThrows(IllegalStateException::class.java) {
            client.post("https://oauth2.googleapis.com/device/code", mapOf("client_id" to "id"))
        }
        assertTrue(error.message.orEmpty().contains("JSON"))
        assertFalse(error.message.orEmpty().contains("private"))
        assertTrue(conn.closed)
    }

    @Test fun oauthPostEncodesSecretsWithoutExposingThemInUrl() {
        val conn = StubConnection(
            URL("https://oauth2.googleapis.com/token"),
            body = """{"access_token":"issued"}""",
        )
        val client = GoogleOAuthHttpClient({ true }) { conn }
        val response = client.post("https://oauth2.googleapis.com/token",
            mapOf("client_secret" to "a+b&c"))
        assertEquals("issued", response.second.getString("access_token"))
        assertTrue(conn.outgoing.toString("UTF-8").contains("client_secret=a%2Bb%26c"))
        assertEquals(null, conn.url.query)
        assertFalse(conn.instanceFollowRedirects)
        assertTrue(conn.closed)
    }
}
