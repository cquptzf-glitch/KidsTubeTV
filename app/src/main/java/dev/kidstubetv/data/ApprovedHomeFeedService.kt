package dev.kidstubetv.data

import org.json.JSONObject

/** Backend-agnostic YouTube Data API reader. Fake implementations make paging testable. */
internal fun interface YouTubeCatalogSource {
    fun request(endpoint: String, params: Map<String, String>): JSONObject
}

/**
 * Assembles the kids home with bounded and batched calls.
 *
 * channels.list is issued ONCE for up to five approved channels, instead of
 * once per channel. playlistItems.list retrieves bounded candidate IDs. Every
 * resulting video still passes videos.list with madeForKids + embeddability +
 * parent whitelist checks via [verify]; candidate IDs are never approval.
 */
internal class ApprovedHomeFeedService(
    private val source: YouTubeCatalogSource,
    private val verify: (List<String>) -> List<KidVideo>,
) {
    companion object {
        private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
        private val CHANNEL_ID = Regex("UC[A-Za-z0-9_-]{22}")
    }

    fun load(exactVideoIds: List<String>, approvedChannelIds: List<String>): List<KidVideo> {
        val attempts = mutableListOf<Result<List<String>>>()
        val exact = exactVideoIds.distinct().filter { VIDEO_ID.matches(it) }.take(40)
        if (exact.isNotEmpty()) attempts.add(Result.success(exact))

        val channels = approvedChannelIds.distinct()
            .filter { CHANNEL_ID.matches(it) }.take(5)
        if (channels.isNotEmpty()) {
            val uploadsResult = runCatching {
                val response = source.request(
                    "channels", mapOf("part" to "contentDetails", "id" to channels.joinToString(","))
                )
                val items = response.optJSONArray("items")
                buildMap<String, String> {
                    if (items != null) for (index in 0 until items.length()) {
                        val item = items.optJSONObject(index) ?: continue
                        val channel = item.optString("id")
                        if (channel !in channels) continue
                        val playlist = item.optJSONObject("contentDetails")
                            ?.optJSONObject("relatedPlaylists")?.optString("uploads").orEmpty()
                        if (playlist.isNotBlank()) put(channel, playlist)
                    }
                }
            }

            channels.forEach { channel ->
                val playlist = uploadsResult.getOrNull()?.get(channel)
                when {
                    uploadsResult.isFailure -> attempts.add(
                        Result.failure(uploadsResult.exceptionOrNull()
                            ?: IllegalStateException("频道元数据请求失败"))
                    )
                    playlist.isNullOrBlank() -> attempts.add(Result.success(emptyList()))
                    else -> attempts.add(runCatching {
                        val response = source.request("playlistItems", mapOf(
                            "part" to "contentDetails",
                            "playlistId" to playlist,
                            "maxResults" to "40"
                        ))
                        val items = response.optJSONArray("items")
                        if (items == null) emptyList()
                        else (0 until items.length()).mapNotNull { index ->
                            items.optJSONObject(index)?.optJSONObject("contentDetails")
                                ?.optString("videoId")?.takeIf(VIDEO_ID::matches)
                        }.distinct()
                    })
                }
            }
        }

        // A real outage must not turn into "you have no approved videos".
        val candidateGroups = HomeFeedLoadPolicy.successfulGroups(attempts)
        val candidates = candidateGroups.flatten().distinct().take(240)
        if (candidates.isEmpty()) return emptyList()

        // Verify in official videos.list batches; a failed page may not suppress
        // other verified batches, but all failed pages must surface as an error.
        val verifiedAttempts = candidates.chunked(50).map { ids ->
            runCatching { verify(ids) }
        }
        val verifiedById = HomeFeedLoadPolicy.successfulGroups(verifiedAttempts)
            .flatten().associateBy { it.id }
        val approvedGroups = candidateGroups.map { group ->
            group.mapNotNull(verifiedById::get)
        }
        return HomeFeedMixer.interleave(approvedGroups, { it.id }, 48)
    }
}
