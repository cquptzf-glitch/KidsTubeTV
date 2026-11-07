package dev.kidstubetv.data

import org.json.JSONObject

/** Recognize only known API error codes; never expose provider response bodies. */
object YouTubeApiErrorPolicy {
    fun explain(status: Int, error: JSONObject?): String {
        val reason = error?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty()
        return when (reason) {
            "quotaExceeded", "dailyLimitExceeded" -> "YouTube API 配额已用尽，请检查 Google Cloud 配额"
            "keyInvalid", "accessNotConfigured" -> "YouTube API Key 无效或 Data API v3 尚未启用"
            "insufficientPermissions", "subscriptionForbidden" ->
                "YouTube 权限不足，请检查 Google 授权范围和频道账号"
            else -> when (status) {
                401 -> "YouTube 授权失败，请重新登录 Google 或检查 API Key"
                403 -> "YouTube HTTP 403，可能是 API Key、权限或配额问题"
                429 -> "YouTube 请求过于频繁，请稍后再试"
                else -> "YouTube API HTTP $status，请检查网络或 API 配置"
            }
        }
    }
}
