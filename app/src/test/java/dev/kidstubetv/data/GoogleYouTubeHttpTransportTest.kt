package dev.kidstubetv.data

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleYouTubeHttpTransportTest {
    private class FakeConnection(
        url: URL,
        private val status: Int = 200,
        private val response: String = "{\"items\":[]}",
    ) : HttpURLConnection(url) {
        var disconnected = false
        var responseReads = 0
        override fun connect() {}
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode(): Int { responseReads++; return status }
        override fun getInputStream() = ByteArrayInputStream(response.toByteArray())
        override fun getErrorStream() = ByteArrayInputStream(response.toByteArray())
    }

    @Test fun encodesQueryAndKeepsApiKeyInHeaderOnly() {
        lateinit var conn: FakeConnection
        val client = GoogleYouTubeHttpTransport({ true }) { url ->
            FakeConnection(url).also { conn = it }
        }
        assertEquals(0, client.fetch(
            "videos", mapOf("id" to "a+b&c", "part" to "snippet"),
            YouTubeCredential.ApiKey("private-api-key")
        ).getJSONArray("items").length())
        assertEquals("www.googleapis.com", conn.url.host)
        assertTrue(conn.url.query.contains("id=a%2Bb%26c"))
        assertFalse(conn.url.toExternalForm().contains("private-api-key"))
        assertEquals("private-api-key", conn.getRequestProperty("X-Goog-Api-Key"))
        assertFalse(conn.instanceFollowRedirects)
        assertTrue(conn.disconnected)
    }

    @Test fun bearerIsNotRedirectedOrExposedInErrors() {
        val conn = FakeConnection(
            URL("https://www.googleapis.com/youtube/v3/videos"),
            302, "<html>token=private-bearer</html>"
        )
        val client = GoogleYouTubeHttpTransport({ true }) { conn }
        val error = assertThrows(IllegalStateException::class.java) {
            client.fetch("videos", emptyMap(), YouTubeCredential.OAuthToken("private-bearer"))
        }
        assertTrue(error.message.orEmpty().contains("跳转"))
        assertFalse(error.message.orEmpty().contains("private-bearer"))
        assertEquals("Bearer private-bearer", conn.getRequestProperty("Authorization"))
        assertFalse(conn.instanceFollowRedirects)
        assertTrue(conn.disconnected)
    }

    @Test fun vpnRevokedBeforeConnectionNeverTransmitsCredentials() {
        var checks = 0
        val conn = FakeConnection(URL("https://www.googleapis.com/youtube/v3/videos"))
        val client = GoogleYouTubeHttpTransport({ ++checks == 1 }) { conn }
        assertThrows(IllegalStateException::class.java) {
            client.fetch("videos", emptyMap(), YouTubeCredential.ApiKey("private"))
        }
        assertEquals(0, conn.responseReads)
        assertTrue(conn.disconnected)
    }

    @Test fun oversizedSuccessResponseIsRejectedWithoutLeakingContent() {
        val conn = FakeConnection(
            URL("https://www.googleapis.com/youtube/v3/videos"),
            200, "secret=" + "x".repeat(GoogleYouTubeHttpTransport.MAX_RESPONSE_BYTES)
        )
        val client = GoogleYouTubeHttpTransport({ true }) { conn }
        val error = assertThrows(IllegalStateException::class.java) {
            client.fetch("videos", emptyMap(), YouTubeCredential.ApiKey("private"))
        }
        assertTrue(error.message.orEmpty().contains("安全限制"))
        assertFalse(error.message.orEmpty().contains("secret="))
        assertTrue(conn.disconnected)
    }

    @Test fun oversizedErrorResponseIsAlsoBounded() {
        val conn = FakeConnection(
            URL("https://www.googleapis.com/youtube/v3/videos"),
            403, "private=" + "x".repeat(GoogleYouTubeHttpTransport.MAX_ERROR_BYTES)
        )
        val client = GoogleYouTubeHttpTransport({ true }) { conn }
        assertThrows(IllegalStateException::class.java) {
            client.fetch("videos", emptyMap(), YouTubeCredential.OAuthToken("private"))
        }
        assertTrue(conn.disconnected)
    }

    @Test fun sanitizesInvalidJsonAndMapsQuotaErrors() {
        val malformed = FakeConnection(
            URL("https://www.googleapis.com/youtube/v3/videos"),
            200, "<html>private</html>"
        )
        val client = GoogleYouTubeHttpTransport({ true }) { malformed }
        val error = assertThrows(IllegalStateException::class.java) {
            client.fetch("videos", emptyMap(), YouTubeCredential.ApiKey("private"))
        }
        assertTrue(error.message.orEmpty().contains("JSON"))
        assertFalse(error.message.orEmpty().contains("private"))
        val quota = FakeConnection(
            URL("https://www.googleapis.com/youtube/v3/videos"),
            403, """{"error":{"errors":[{"reason":"quotaExceeded"}]}}"""
        )
        val quotaClient = GoogleYouTubeHttpTransport({ true }) { quota }
        val failure = assertThrows(IllegalStateException::class.java) {
            quotaClient.fetch("videos", emptyMap(), YouTubeCredential.ApiKey("private"))
        }
        assertTrue(failure.message.orEmpty().contains("配额"))
        assertTrue(quota.disconnected)
    }
}
