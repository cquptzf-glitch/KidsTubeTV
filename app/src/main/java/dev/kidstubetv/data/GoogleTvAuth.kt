package dev.kidstubetv.data

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

data class TvDeviceCode(
    val code: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresAt: Long,
    val intervalSeconds: Int,
)
data class YoutubeSubscription(val channelId: String, val title: String)

/**
 * Google's official TV / limited-input OAuth device-code flow.
 * No account password or Google cookie is read or stored by this application.
 * Requires developer-owned OAuth client of type "TVs and Limited Input devices".
 */
class GoogleTvAuth(context: Context) {
    private val secret = SecurePrefs(context.applicationContext)
    private val prefs = context.getSharedPreferences("kids_google_tv_oauth", Context.MODE_PRIVATE)

    var clientId: String
        get() = secret.get("oauth_client_id")
        set(v) { secret.put("oauth_client_id", v.trim()) }

    var clientSecret: String
        get() = secret.get("oauth_client_secret")
        set(v) { secret.put("oauth_client_secret", v.trim()) }

    val isLinked: Boolean get() = secret.get("oauth_refresh_token").isNotBlank()
    /** Get an OAuth bearer for public YouTube API calls when no API key was configured. */
    fun authorizedToken(): String = accessToken()
    val isConfigured: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank()

    fun begin(): TvDeviceCode {
        require(isConfigured) { "请先设置 Google TV OAuth Client ID 和 Client Secret" }
        val (status, json) = post("https://oauth2.googleapis.com/device/code", mapOf(
            "client_id" to clientId,
            "scope" to "https://www.googleapis.com/auth/youtube.readonly",
        ))
        require(status in 200..299) { "申请 Google 验证码失败 HTTP $status" }
        val expires = json.getLong("expires_in").coerceIn(1, 3600)
        return TvDeviceCode(
            code = json.getString("device_code"),
            userCode = json.getString("user_code"),
            verificationUrl = json.getString("verification_url"),
            expiresAt = System.currentTimeMillis() + expires * 1000L,
            intervalSeconds = json.optInt("interval", 5).coerceIn(5, 60),
        )
    }

    /**
     * Invoke only from background thread. Cancellation is cooperative and the
     * server-provided interval/expiry are honoured to avoid excessive polling.
     */
    fun awaitAuthorization(challenge: TvDeviceCode, cancelled: AtomicBoolean, onProgress: (String) -> Unit) {
        var delaySec = challenge.intervalSeconds
        while (!cancelled.get() && System.currentTimeMillis() < challenge.expiresAt) {
            Thread.sleep(delaySec * 1000L)
            if (cancelled.get()) return
            val (status, response) = post("https://oauth2.googleapis.com/token", mapOf(
                "client_id" to clientId,
                "client_secret" to clientSecret,
                "device_code" to challenge.code,
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
            ))
            if (status in 200..299 && response.has("access_token")) {
                val refresh = response.optString("refresh_token")
                if (refresh.isNotBlank()) secret.put("oauth_refresh_token", refresh)
                saveAccessToken(response)
                check(isLinked) { "Google 未返回 refresh token" }
                onProgress("Google 授权成功，可以读取订阅频道")
                return
            }
            when (response.optString("error")) {
                "authorization_pending" -> onProgress("正在等待你在手机上完成 Google 授权…")
                "slow_down" -> delaySec = (delaySec + 5).coerceAtMost(60)
                "access_denied" -> error("Google 授权被取消")
                "expired_token" -> error("验证码过期，请重新发起授权")
                else -> error("Google 授权失败：HTTP $status / " + response.optString("error", "未知错误"))
            }
        }
        if (!cancelled.get()) error("Google 验证码已过期")
    }

    fun subscriptions(maxChannels: Int = 250): List<YoutubeSubscription> {
        check(isLinked) { "Google 账户尚未授权" }
        val result = linkedMapOf<String, YoutubeSubscription>()
        var token = ""
        repeat(5) {
            val query = "https://www.googleapis.com/youtube/v3/subscriptions?part=snippet&mine=true&maxResults=50" +
                if (token.isBlank()) "" else "&pageToken=" + enc(token)
            val (code, response) = get(query, accessToken())
            require(code in 200..299) { "YouTube 订阅列表请求失败 HTTP $code" }
            val list = response.optJSONArray("items")
            if (list != null) for (i in 0 until list.length()) {
                val detail = list.optJSONObject(i)?.optJSONObject("snippet") ?: continue
                val id = detail.optJSONObject("resourceId")?.optString("channelId").orEmpty()
                if (id.matches(Regex("UC[A-Za-z0-9_-]{22}"))) {
                    result[id] = YoutubeSubscription(id, detail.optString("title"))
                }
                if (result.size >= maxChannels) break
            }
            token = response.optString("nextPageToken")
            if (token.isBlank() || result.size >= maxChannels) return result.values.toList()
        }
        return result.values.toList()
    }

    fun logoutLocal() {
        secret.put("oauth_refresh_token", "")
        secret.put("oauth_access_token", "")
        prefs.edit().remove("expires_at").apply()
    }

    private fun accessToken(): String {
        val existing = secret.get("oauth_access_token")
        if (existing.isNotBlank() && System.currentTimeMillis() < prefs.getLong("expires_at", 0)) {
            return existing
        }
        val refresh = secret.get("oauth_refresh_token")
        check(refresh.isNotBlank()) { "Google 登录已失效，请重新授权" }
        val (code, response) = post("https://oauth2.googleapis.com/token", mapOf(
            "client_id" to clientId,
            "client_secret" to clientSecret,
            "refresh_token" to refresh,
            "grant_type" to "refresh_token",
        ))
        require(code in 200..299 && response.has("access_token")) {
            "刷新 Google 凭据失败，请重新授权"
        }
        saveAccessToken(response)
        return secret.get("oauth_access_token")
    }

    private fun saveAccessToken(data: JSONObject) {
        secret.put("oauth_access_token", data.getString("access_token"))
        val expires = data.optLong("expires_in", 3600).coerceIn(30, 86400)
        prefs.edit().putLong("expires_at", System.currentTimeMillis() + expires * 1000 - 30_000).apply()
    }

    private fun get(uri: String, token: String): Pair<Int, JSONObject> {
        val conn = URL(uri).openConnection() as HttpURLConnection
        conn.connectTimeout = 12_000
        conn.readTimeout = 18_000
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val content = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to JSONObject(if (content.isNotBlank()) content else "{}")
        } finally { conn.disconnect() }
    }

    private fun post(endpoint: String, values: Map<String,String>): Pair<Int, JSONObject> {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 12_000
        conn.readTimeout = 18_000
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.setRequestProperty("Accept", "application/json")
        val body = values.entries.joinToString("&") { (k, v) -> enc(k) + "=" + enc(v) }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return status to JSONObject(if (response.isNotBlank()) response else "{}")
        } finally { conn.disconnect() }
    }

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
}
