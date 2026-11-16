package dev.kidstubetv.data

import org.json.JSONObject

/**
 * Single auditable admission point for videos.list responses used by home,
 * category search, approved channel uploads, favorites, history and player
 * re-check. Every input remains untrusted until official YouTube metadata
 * confirms both child labeling and iframe embeddability.
 */
internal object YouTubeVideoScreeningPolicy {
    private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")

    fun accept(
        item: JSONObject,
        approvedChannels: Set<String>,
        approvedVideos: Set<String>,
        blockedVideos: Set<String>
    ): KidVideo? {
        val id = item.optString("id")
        if (!VIDEO_ID.matches(id)) return null
        val snippet = item.optJSONObject("snippet") ?: return null
        val status = item.optJSONObject("status") ?: return null
        val thumbnails = snippet.optJSONObject("thumbnails")
        val thumb = (thumbnails?.optJSONObject("medium")
            ?: thumbnails?.optJSONObject("default"))?.optString("url").orEmpty()
        val video = KidVideo(
            id = id,
            title = snippet.optString("title"),
            channelId = snippet.optString("channelId"),
            channelTitle = snippet.optString("channelTitle"),
            thumbnailUrl = thumb
        )
        if (!AdmissionPolicy.allows(
                video, status.optBoolean("madeForKids", false),
                status.optBoolean("embeddable", false),
                approvedChannels, approvedVideos, blockedVideos
            )) return null
        return video
    }
}
