package dev.kidstubetv.data

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

/**
 * Strict, bounded transport for Google's public device-code OAuth endpoints
 * and the read-only YouTube subscription listing.
 *
 * Google credentials must never be forwarded to a redirect target. The app's
 * VPN remains the network boundary; these checks do not replace an Android
 * device-level no-leak test.
 */
internal class GoogleOAuthHttpClient(
    private val vpnReady: () -> Boolean,
    private val openConnection: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
) {
    companion object {
        const val MAX_RESPONSE_BYTES = 512 * 1024
        private val POST_ENDPOINTS = setOf(
            "https://oauth2.googleapis.com/device/code",
            "https://oauth2.googleapis.com/token",
        )
    }

    fun getSubscriptions(uri: String, bearer: String): Pair<Int, JSONObject> {
        require(bearer.isNotBlank()) { "Google 登录已失效，请重新授权" }
        require(isSubscriptionsEndpoint(uri)) { "不允许的 Google API 请求地址" }
        requireVpn()
        val conn = connection(uri)
        try {
            conn.setRequestProperty("Authorization", "Bearer $bearer")
            requireVpn()
            return readResponse(conn)
        } finally {
            conn.disconnect()
        }
    }

    fun post(endpoint: String, values: Map<String, String>): Pair<Int, JSONObject> {
        require(endpoint in POST_ENDPOINTS) { "不允许的 Google OAuth 请求地址" }
        requireVpn()
        val conn = connection(endpoint)
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val body = values.entries.joinToString("&") { (key, value) ->
                encode(key) + "=" + encode(value)
            }.toByteArray(Charsets.UTF_8)
            requireVpn()
            conn.outputStream.use { it.write(body) }
            requireVpn()
            return readResponse(conn)
        } finally {
            conn.disconnect()
        }
    }

    private fun connection(endpoint: String): HttpURLConnection =
        openConnection(URL(endpoint)).apply {
            // HttpURLConnection follows redirects by default, and an OAuth
            // bearer or client secret must never be sent to another host.
            instanceFollowRedirects = false
            useCaches = false
            connectTimeout = 12_000
            readTimeout = 18_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Encoding", "identity")
        }

    private fun readResponse(conn: HttpURLConnection): Pair<Int, JSONObject> {
        val status = conn.responseCode
        check(status !in 300..399) {
            "Google 请求出现 HTTP 跳转，已拒绝转发授权信息"
        }
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.use(::readBounded).orEmpty()
        val json = if (body.isBlank()) JSONObject() else runCatching {
            JSONObject(body)
        }.getOrElse {
            // A server-controlled HTML/JSON error may contain secrets.
            throw IllegalStateException("Google API 返回了无效的 JSON 响应")
        }
        return status to json
    }

    private fun readBounded(source: InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = source.read(buffer)
            if (count < 0) break
            check(out.size() + count <= MAX_RESPONSE_BYTES) {
                "Google API 响应过大，已停止读取"
            }
            out.write(buffer, 0, count)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun requireVpn() = GoogleRequestPolicy.requireAvailable(vpnReady())

    private fun isSubscriptionsEndpoint(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("www.googleapis.com", ignoreCase = true) &&
            uri.port == -1 && uri.rawUserInfo == null &&
            uri.rawFragment == null && uri.path == "/youtube/v3/subscriptions"
    }.getOrDefault(false)

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
