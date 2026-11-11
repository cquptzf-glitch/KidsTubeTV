package dev.kidstubetv.data

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Do not use data classes: secret-bearing credentials must not get toString(). */
internal sealed interface YouTubeCredential {
    class ApiKey(val value: String) : YouTubeCredential
    class OAuthToken(val value: String) : YouTubeCredential
}

/** Transport injection allows deterministic tests without a Google account or real VPN. */
internal fun interface YouTubeHttpTransport {
    fun fetch(
        endpoint: String, params: Map<String, String>, credential: YouTubeCredential
    ): JSONObject
}

internal class YouTubeDataApiClient(
    private val credential: () -> YouTubeCredential,
    private val vpnReady: () -> Boolean,
    private val transport: YouTubeHttpTransport = GoogleYouTubeHttpTransport(vpnReady),
) {
    fun get(endpoint: String, params: Map<String, String>): JSONObject {
        // Fail closed before OAuth refresh OR any Google network operation.
        GoogleRequestPolicy.requireAvailable(vpnReady())
        val auth = credential()
        val secret = when (auth) {
            is YouTubeCredential.ApiKey -> auth.value
            is YouTubeCredential.OAuthToken -> auth.value
        }
        require(secret.isNotBlank()) {
            "请配置 YouTube Data API Key 或完成 Google TV 只读授权"
        }
        // Refreshing an OAuth token may itself take time. Recheck VPN before
        // the actual API request, never falling back to unproxied networking.
        GoogleRequestPolicy.requireAvailable(vpnReady())
        return transport.fetch(endpoint, params, auth)
    }
}

/**
 * Strict HTTPS adapter for YouTube Data API v3. No redirects, no credential in
 * URL parameters or logs, and bounded response bodies even when a server sends
 * an unreasonably large success/error document.
 */
internal class GoogleYouTubeHttpTransport(
    private val vpnReady: () -> Boolean,
    private val openConnection: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
) : YouTubeHttpTransport {
    companion object {
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        const val MAX_ERROR_BYTES = 32 * 1024
        private val ENDPOINTS = setOf("videos", "channels", "playlistItems", "search")
    }

    override fun fetch(
        endpoint: String, params: Map<String, String>, credential: YouTubeCredential
    ): JSONObject {
        require(endpoint in ENDPOINTS) { "不允许的 YouTube API 路径" }
        GoogleRequestPolicy.requireAvailable(vpnReady())
        val query = params.entries.joinToString("&") { (key, value) ->
            encode(key) + "=" + encode(value)
        }
        val url = URL("https://www.googleapis.com/youtube/v3/$endpoint" +
            if (query.isEmpty()) "" else "?$query")
        val conn = openConnection(url)
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Accept-Encoding", "identity")
            when (credential) {
                is YouTubeCredential.ApiKey -> {
                    require(credential.value.isNotBlank()) { "YouTube API Key 不能为空" }
                    conn.setRequestProperty("X-Goog-Api-Key", credential.value)
                }
                is YouTubeCredential.OAuthToken -> {
                    require(credential.value.isNotBlank()) { "Google 授权已失效" }
                    conn.setRequestProperty("Authorization", "Bearer " + credential.value)
                }
            }
            // Headers have not left the client yet. Refuse a request if VPN
            // stopped while credentials or query parameters were prepared.
            GoogleRequestPolicy.requireAvailable(vpnReady())
            val code = conn.responseCode
            check(code !in 300..399) { "YouTube API HTTP 跳转已拒绝，避免转发凭据" }
            if (code !in 200..299) {
                val body = conn.errorStream?.use { readBounded(it, MAX_ERROR_BYTES) }.orEmpty()
                val error = runCatching { JSONObject(body).optJSONObject("error") }.getOrNull()
                throw IllegalStateException(YouTubeApiErrorPolicy.explain(code, error))
            }
            val input = conn.inputStream.use { readBounded(it, MAX_RESPONSE_BYTES) }
            return runCatching { JSONObject(input) }.getOrElse {
                throw IllegalStateException("YouTube API 返回了无效的 JSON 数据")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun readBounded(source: InputStream, maxBytes: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = source.read(buffer)
            if (count < 0) break
            check(output.size() + count <= maxBytes) {
                "YouTube API 响应超过安全限制"
            }
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}
