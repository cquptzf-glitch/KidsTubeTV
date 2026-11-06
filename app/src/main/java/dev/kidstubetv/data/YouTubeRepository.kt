package dev.kidstubetv.data

import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** YouTube Data API v3, never retrieves video from undocumented endpoints. */
class YouTubeRepository(private val settings: SettingsStore) {
    fun search(query: String): List<KidVideo> {
        require(query.length in 1..128) { "搜索词长度不正确" }
        val result = get("search", mapOf(
            "part" to "snippet",
            "type" to "video",
            "q" to query,
            "maxResults" to "30",
            "safeSearch" to "strict",
            "videoEmbeddable" to "true",
        ))
        val ids = result.optJSONArray("items")?.let { a ->
            (0 until a.length()).mapNotNull {
                a.optJSONObject(it)?.optJSONObject("id")?.optString("videoId")
                    ?.takeIf { id -> id.matches(Regex("[A-Za-z0-9_-]{11}")) }
            }
        }.orEmpty()
        return lookup(ids)
    }

    /** 50 IDs per videos.list; avoids the 100-unit search quota for favorites/history. */
    fun lookup(videoIds: List<String>): List<KidVideo> {
        val validIds = videoIds.distinct().filter { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
        if (validIds.isEmpty()) return emptyList()
        val verified = mutableMapOf<String, KidVideo>()
        validIds.chunked(50).forEach { chunk ->
            val response = get("videos", mapOf("part" to "snippet,status", "id" to chunk.joinToString(",")))
            val items = response.optJSONArray("items") ?: return@forEach
            for (i in 0 until items.length()) {
                val obj = items.optJSONObject(i) ?: continue
                val snippet = obj.optJSONObject("snippet") ?: continue
                val status = obj.optJSONObject("status") ?: continue
                val thumbnails = snippet.optJSONObject("thumbnails")
                val thumb = (thumbnails?.optJSONObject("medium") ?: thumbnails?.optJSONObject("default"))
                    ?.optString("url").orEmpty()
                val video = KidVideo(obj.optString("id"), snippet.optString("title"),
                    snippet.optString("channelId"), snippet.optString("channelTitle"), thumb)
                if (AdmissionPolicy.allows(video, status.optBoolean("madeForKids", false),
                        status.optBoolean("embeddable", false),
                        settings.allowedChannels, settings.approvedVideoIds, settings.blockedVideoIds)) {
                    verified[video.id] = video
                }
            }
        }
        return validIds.mapNotNull(verified::get)
    }

    /** Recheck at every play/next, independent of the navigation queue. */
    fun approveSingle(id: String): KidVideo? = lookup(listOf(id)).firstOrNull()

    private fun get(endpoint: String, params: Map<String, String>): JSONObject {
        val key = settings.apiKey
        require(key.isNotBlank()) { "请先配置 YouTube Data API 密钥" }
        val uri = Uri.parse("https://www.googleapis.com/youtube/v3/$endpoint").buildUpon()
            .apply { params.forEach { (k, v) -> appendQueryParameter(k, v) } }.build()
        val conn = URL(uri.toString()).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("X-Goog-Api-Key", key)
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            require(code in 200..299) { "YouTube HTTP $code（检查 VPN / 配额 / API Key）" }
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally { conn.disconnect() }
    }
}
