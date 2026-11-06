package dev.kidstubetv.data

import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Uses the documented YouTube Data API, not reverse engineered YouTube endpoints.
 * All calls must be scheduled after the app-specific VPN has been established.
 */
class YouTubeRepository(private val settings: SettingsStore) {
    fun search(query: String): List<KidVideo> {
        require(settings.apiKey.isNotBlank()) { "YouTube Data API key missing" }
        val search = get("search", mapOf(
            "part" to "snippet",
            "type" to "video",
            "q" to query,
            "maxResults" to "20",
            "safeSearch" to "strict",
            "videoEmbeddable" to "true",
        ))
        val ids = search.optJSONArray("items")?.let { list ->
            (0 until list.length()).mapNotNull {
                list.optJSONObject(it)?.optJSONObject("id")?.optString("videoId")?.takeIf(String::isNotBlank)
            }
        } ?: emptyList()
        if (ids.isEmpty()) return emptyList()

        val detail = get("videos", mapOf("part" to "snippet,status", "id" to ids.joinToString(",")))
        val allowedChannels = settings.allowedChannels
        val allowedVideos = settings.approvedVideoIds
        val blocked = settings.blockedVideoIds
        val valid = mutableMapOf<String, KidVideo>()
        val items = detail.optJSONArray("items") ?: return emptyList()
        for (i in 0 until items.length()) {
            val row = items.optJSONObject(i) ?: continue
            val snippet = row.optJSONObject("snippet") ?: continue
            val status = row.optJSONObject("status") ?: continue
            val thumbnails = snippet.optJSONObject("thumbnails")
            val image = (thumbnails?.optJSONObject("medium") ?: thumbnails?.optJSONObject("default"))
                ?.optString("url").orEmpty()
            val video = KidVideo(
                id = row.optString("id"),
                title = snippet.optString("title"),
                channelId = snippet.optString("channelId"),
                channelTitle = snippet.optString("channelTitle"),
                thumbnailUrl = image,
            )
            if (AdmissionPolicy.allows(
                video, status.optBoolean("madeForKids", false), status.optBoolean("embeddable", false),
                allowedChannels, allowedVideos, blocked
            )) valid[video.id] = video
        }
        return ids.mapNotNull(valid::get)
    }

    /** Re-check each selected video at admission time; never trust stale UI/Intent data. */
    fun approveSingle(videoId: String): KidVideo? {
        if (!videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) return null
        val result = get("videos", mapOf("part" to "snippet,status", "id" to videoId))
        val row = result.optJSONArray("items")?.optJSONObject(0) ?: return null
        val snippet = row.optJSONObject("snippet") ?: return null
        val status = row.optJSONObject("status") ?: return null
        val video = KidVideo(row.optString("id"), snippet.optString("title"),
            snippet.optString("channelId"), snippet.optString("channelTitle"), "")
        return video.takeIf {
            AdmissionPolicy.allows(it, status.optBoolean("madeForKids", false),
                status.optBoolean("embeddable", false),
                settings.allowedChannels, settings.approvedVideoIds, settings.blockedVideoIds)
        }
    }

    private fun get(route: String, params: Map<String, String>): JSONObject {
        val uri = Uri.parse("https://www.googleapis.com/youtube/v3/$route")
            .buildUpon().apply { params.forEach { (name, value) -> appendQueryParameter(name, value) } }.build()
        val connection = URL(uri.toString()).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("X-Goog-Api-Key", settings.apiKey)
        connection.setRequestProperty("Accept", "application/json")
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("YouTube API HTTP $code (check API key, quota and network)")
            }
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
}
