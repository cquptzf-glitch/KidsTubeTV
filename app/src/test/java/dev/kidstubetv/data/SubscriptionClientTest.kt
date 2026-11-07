package dev.kidstubetv.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionClientTest {
    private val link = "https://provider.example/clash?token=private"
    private val yaml = "proxies:\n  - name: direct\n    type: direct\n"

    private class FakeConnection(
        url: URL,
        private val status: Int,
        private val body: ByteArray = byteArrayOf(),
        private val redirect: String? = null,
        private val encoding: String? = null,
    ) : HttpURLConnection(url) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getInputStream() = ByteArrayInputStream(body)
        override fun getHeaderField(name: String?): String? =
            if (name.equals("Location", ignoreCase = true)) redirect else null
        override fun getContentEncoding(): String? = encoding
    }

    @Test fun switchesToClashUserAgentWhenProviderRejectsFirstAttempt() {
        val connections = mutableListOf<FakeConnection>()
        val downloader = SubscriptionClient { url ->
            FakeConnection(url,
                if (connections.isEmpty()) 403 else 200,
                yaml.toByteArray()).also(connections::add)
        }
        assertEquals(yaml, downloader.fetch(link))
        assertEquals(listOf("ClashMetaForAndroid", "clash"),
            connections.map { it.getRequestProperty("User-Agent") })
    }

    @Test fun retriesAnHtmlLoginPageOnlyOnceAndKeepsTokensOutOfErrors() {
        var attempts = 0
        val client = SubscriptionClient { url ->
            attempts++
            FakeConnection(url, 200,
                "<html><body>Sign in token=private</body></html>".toByteArray())
        }
        val failure = assertThrows(SubscriptionDownloadException::class.java) {
            client.fetch(link)
        }
        assertEquals(2, attempts)
        assertEquals(SubscriptionProblem.HTML_RESPONSE, failure.problem)
        assertTrue(!failure.message.orEmpty().contains("private"))
    }

    @Test fun resolvesSafeRelativeRedirectAndSupportsGzipYaml() {
        val visited = mutableListOf<String>()
        val gzipped = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(yaml.toByteArray()) }
        }.toByteArray()
        val client = SubscriptionClient { url ->
            visited.add(url.toExternalForm())
            if (visited.size == 1) FakeConnection(url, 302, redirect = "/download?token=private")
            else FakeConnection(url, 200, gzipped, encoding = "gzip")
        }
        assertEquals(yaml, client.fetch(link))
        assertEquals("https://provider.example/download?token=private", visited.last())
    }

    @Test fun rejectsNonHttpsRedirectAndRetainsSafeErrorCategory() {
        val client = SubscriptionClient { url ->
            FakeConnection(url, 302, redirect = "http://insecure.example/file")
        }
        val error = assertThrows(SubscriptionDownloadException::class.java) {
            client.fetch(link)
        }
        assertEquals(SubscriptionProblem.REDIRECT, error.problem)
    }

    @Test fun rejectsOversizedDecompressedYaml() {
        val large = "proxies:\n" + " ".repeat(SubscriptionFormat.MAX_PROFILE_BYTES + 100)
        val client = SubscriptionClient { url ->
            FakeConnection(url, 200, large.toByteArray())
        }
        val error = assertThrows(SubscriptionDownloadException::class.java) {
            client.fetch(link)
        }
        assertEquals(SubscriptionProblem.TOO_LARGE, error.problem)
    }
}
