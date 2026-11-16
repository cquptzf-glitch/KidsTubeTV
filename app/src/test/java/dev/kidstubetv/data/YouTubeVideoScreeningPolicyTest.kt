package dev.kidstubetv.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeVideoScreeningPolicyTest {
    private val id = "abcdefghijk"
    private val channel = "UC" + "a".repeat(22)

    private fun response(
        madeForKids: Boolean? = true,
        embeddable: Boolean? = true,
        responseId: String = id,
        channelId: String = channel
    ) = JSONObject().put("id", responseId)
        .put("snippet", JSONObject()
            .put("title", "A child-friendly video")
            .put("channelId", channelId)
            .put("channelTitle", "Teacher")
            .put("thumbnails", JSONObject()
                .put("medium", JSONObject().put("url", "https://example.org/a.jpg"))))
        .put("status", JSONObject().apply {
            if (madeForKids != null) put("madeForKids", madeForKids)
            if (embeddable != null) put("embeddable", embeddable)
        })

    private fun approved(item: JSONObject, channels: Set<String> = setOf(channel),
                         videos: Set<String> = emptySet(), blocked: Set<String> = emptySet()) =
        YouTubeVideoScreeningPolicy.accept(item, channels, videos, blocked)

    @Test fun allowsOnlyExactApprovedVideosOrAllowedChannels() {
        assertEquals(id, approved(response())?.id)
        assertNull(approved(response(), channels = emptySet()))
        assertEquals(id, approved(response(), channels = emptySet(), videos = setOf(id))?.id)
    }

    @Test fun missingOrFalseKidsAndEmbeddingFlagsFailClosed() {
        assertNull(approved(response(madeForKids = false)))
        assertNull(approved(response(madeForKids = null)))
        assertNull(approved(response(embeddable = false)))
        assertNull(approved(response(embeddable = null)))
    }

    @Test fun blockOverridesBothChannelAndExplicitVideoPermission() {
        assertNull(approved(response(), channels = setOf(channel),
            videos = setOf(id), blocked = setOf(id)))
    }

    @Test fun malformedIdsAndMetadataNeverEnterCatalog() {
        assertNull(approved(response(responseId = "not-a-valid-id")))
        assertNull(approved(response(channelId = "UC" + "b".repeat(22))))
        assertNull(approved(JSONObject().put("id", id).put("status", JSONObject())))
    }
}
