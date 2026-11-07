package dev.kidstubetv.data

import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
    private val transport: YouTubeHttpTransport = GoogleYouTubeHttpTransport(),
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

/** Single official YouTube Data API v3 HTTP adapter for all catalog requests. */
internal class GoogleYouTubeHttpTransport : YouTubeHttpTransport {
    override fun fetch(
        endpoint: String, params: Map<String, String>, credential: YouTubeCredential
    ): JSONObject {
        require(endpoint in setOf("videos", "channels", "playlistItems", "search")) {
            "不允许的 YouTube API 路径"
        }
        val uri = Uri.parse("https://www.googleapis.com/youtube/v3/$endpoint")
            .buildUpon().apply {
                params.forEach { (key, value) -> appendQueryParameter(key, value) }
            }.build()

        val conn = URL(uri.toString()).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.instanceFollowRedirects = false
        conn.useCaches = false
        when (credential) {
            is YouTubeCredential.ApiKey ->
                conn.setRequestProperty("X-Goog-Api-Key", credential.value)
            is YouTubeCredential.OAuthToken ->
                conn.setRequestProperty("Authorization", "Bearer ${credential.value}")
        }
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val body = conn.errorStream?.bufferedReader()
                    ?.use { it.readText().take(16_384) }.orEmpty()
                val details = runCatching {
                    JSONObject(body).optJSONObject("error")
                }.getOrNull()
                error(YouTubeApiErrorPolicy.explain(code, details))
            }
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally { conn.disconnect() }
    }
}
