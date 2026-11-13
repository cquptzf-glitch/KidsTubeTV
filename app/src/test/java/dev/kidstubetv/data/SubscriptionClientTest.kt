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
        assertEquals(listOf("FlClash", "ClashMetaForAndroid"),
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
        assertEquals(SubscriptionRequestPolicy.userAgents.size, attempts)
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


    @Test fun triesMoreClientIdentifiersWhenProviderReturnsInvalidYaml() {
        val connections = mutableListOf<FakeConnection>()
        val client = SubscriptionClient { url ->
            FakeConnection(url,
                200,
                if (connections.size < 2) "{\"msg\":\"unsupported client\"}".toByteArray()
                else yaml.toByteArray()
            ).also(connections::add)
        }
        assertEquals(yaml, client.fetch(link))
        assertEquals(
            listOf("FlClash", "ClashMetaForAndroid", "clash.meta"),
            connections.map { it.getRequestProperty("User-Agent") }
        )
    }

    @Test fun doesNotRetryUnsafeProfileOrUnauthorizedToken() {
        var attempts = 0
        val unsafe = SubscriptionClient { url ->
            attempts++
            FakeConnection(url, 200,
                "allow-lan: true\nproxies:\n  - name: direct\n    type: direct\n".toByteArray())
        }
        val rejected = assertThrows(SubscriptionDownloadException::class.java) {
            unsafe.fetch(link)
        }
        assertEquals(SubscriptionProblem.UNSAFE_PROFILE, rejected.problem)
        assertEquals(1, attempts)

        var denied = 0
        val blocked = SubscriptionClient { url ->
            denied++
            FakeConnection(url, 401)
        }
        val unauthorized = assertThrows(SubscriptionDownloadException::class.java) {
            blocked.fetch(link)
        }
        assertEquals(SubscriptionProblem.HTTP, unauthorized.problem)
        assertEquals(401, unauthorized.httpStatus)
        assertEquals(1, denied)
    }

    @Test fun stopsOnRateLimitsWithoutRepeatedRequests() {
        var attempts = 0
        val downloader = SubscriptionClient { url ->
            attempts++
            FakeConnection(url, 429)
        }
        val rateLimited = assertThrows(SubscriptionDownloadException::class.java) {
            downloader.fetch(link)
        }
        assertEquals(429, rateLimited.httpStatus)
        assertEquals(1, attempts)
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
