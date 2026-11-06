package dev.kidstubetv.data

import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** YouTube Data API v3, never retrieves video from undocumented endpoints. */
class YouTubeRepository(private val settings: SettingsStore) {
    companion object {
        private val searchIds = SearchIdCache()
    }

    fun search(query: String): List<KidVideo> {
        val cleaned = query.trim()
        require(cleaned.length in 1..128) { "搜索词长度不正确" }
        val ids = searchIds.get(cleaned) ?: run {
            val result = get("search", mapOf(
                "part" to "snippet",
                "type" to "video",
                "q" to cleaned,
                "maxResults" to "30",
                "safeSearch" to "strict",
                "videoEmbeddable" to "true",
            ))
            val loaded = result.optJSONArray("items")?.let { a ->
                (0 until a.length()).mapNotNull {
                    a.optJSONObject(it)?.optJSONObject("id")?.optString("videoId")
                        ?.takeIf { id -> id.matches(Regex("[A-Za-z0-9_-]{11}")) }
                }
            }.orEmpty()
            searchIds.put(cleaned, loaded)
            loaded
        }
        // A cached search never bypasses per-video, per-channel admission.
        return lookup(ids)
    }

    /**
     * Fetch uploads from an approved channel through channels.list + playlistItems.list;
     * much cheaper than search.list and unlikely to return unrelated channels.
     */
    fun uploads(channelId: String): List<KidVideo> {
        require(channelId in settings.allowedChannels) { "频道不在家长白名单" }
        val channels = get("channels", mapOf("part" to "contentDetails", "id" to channelId))
        val uploadsId = channels.optJSONArray("items")?.optJSONObject(0)
            ?.optJSONObject("contentDetails")?.optJSONObject("relatedPlaylists")
            ?.optString("uploads").orEmpty()
        if (uploadsId.isBlank()) return emptyList()
        val response = get("playlistItems", mapOf("part" to "contentDetails",
            "playlistId" to uploadsId, "maxResults" to "40"))
        val items = response.optJSONArray("items") ?: return emptyList()
        val ids = (0 until items.length()).mapNotNull { idx ->
            items.optJSONObject(idx)?.optJSONObject("contentDetails")?.optString("videoId")
                ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
        }
        return lookup(ids)
    }

    /**
     * Parent-only metadata inspection for a single pasted video, independent of
     * current whitelist. The confirmation UI must explicitly approve the ID.
     */
    fun inspectVideoForParent(id: String): KidVideo {
        require(id.matches(Regex("[A-Za-z0-9_-]{11}"))) { "无效的视频 ID" }
        val data = get("videos", mapOf("part" to "snippet,status", "id" to id))
        val item = data.optJSONArray("items")?.optJSONObject(0)
            ?: error("找不到该 YouTube 视频")
        val snippet = item.optJSONObject("snippet") ?: error("视频缺少频道信息")
        val status = item.optJSONObject("status") ?: error("无法验证视频儿童状态")
        require(status.optBoolean("madeForKids", false) &&
            status.optBoolean("embeddable", false)) {
            "该视频未标记为儿童内容，或禁止嵌入播放"
        }
        return KidVideo(id,
            snippet.optString("title"),
            snippet.optString("channelId"),
            snippet.optString("channelTitle"),
            snippet.optJSONObject("thumbnails")?.optJSONObject("medium")?.optString("url").orEmpty()
        )
    }

    /** Resolve a parent-supplied official channel URL/handle before approval. */
    fun resolveChannel(reference: ChannelReference): YouTubeChannelInfo {
        val filter = when (reference) {
            is ChannelReference.Id -> "id" to reference.value
            is ChannelReference.Handle -> "forHandle" to reference.value
            is ChannelReference.Username -> "forUsername" to reference.value
        }
        val response = get("channels", mapOf("part" to "snippet", filter))
        val entry = response.optJSONArray("items")?.optJSONObject(0)
            ?: error("没有找到此 YouTube 频道")
        val id = entry.optString("id")
        require(id.matches(Regex("UC[A-Za-z0-9_-]{22}"))) { "返回了无效的频道 ID" }
        val title = entry.optJSONObject("snippet")?.optString("title").orEmpty()
        return YouTubeChannelInfo(id, title.ifBlank { id })
    }

    /**
     * Home combines a bounded set of approved channels and exact-approved
     * videos. Every candidate still passes videos.list admission checks.
     * Round-robin order keeps one prolific channel from dominating the UI.
     */
    fun homeFeed(): List<KidVideo> {
        val sources = mutableListOf<List<KidVideo>>()
        val exact = settings.approvedVideoIds.toList()
        if (exact.isNotEmpty()) sources.add(lookup(exact.take(40)))
        settings.allowedChannels.take(5).forEach { channel ->
            // A deleted/unavailable channel should not hide other valid channels.
            runCatching { uploads(channel) }.getOrNull()?.let(sources::add)
        }
        return HomeFeedMixer.interleave(sources, { it.id }, 48)
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
        val token = if (key.isBlank()) settings.oauthAccessToken() else ""
        require(key.isNotBlank() || token.isNotBlank()) {
            "请配置 YouTube Data API Key 或完成 Google 电视账号授权"
        }
        val uri = Uri.parse("https://www.googleapis.com/youtube/v3/$endpoint").buildUpon()
            .apply { params.forEach { (k, v) -> appendQueryParameter(k, v) } }.build()
        val conn = URL(uri.toString()).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        if (key.isNotBlank()) conn.setRequestProperty("X-Goog-Api-Key", key)
        else conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            require(code in 200..299) { "YouTube HTTP $code（检查 VPN / 配额 / API Key）" }
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally { conn.disconnect() }
    }
}
