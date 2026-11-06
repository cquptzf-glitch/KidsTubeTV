package dev.kidstubetv.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdmissionPolicyTest {
    private val video = KidVideo(
        id = "abc12345678", title = "Animals", channelId = "UCabcdefghijklmnopqrstuv",
        channelTitle = "Teacher", thumbnailUrl = "",
    )

    @Test fun unknownChannelsAreDenied() {
        assertFalse(AdmissionPolicy.allows(video, true, true, emptySet(), emptySet(), emptySet()))
    }

    @Test fun approvedChannelStillNeedsKidsAndEmbeddable() {
        val allowed = setOf(video.channelId)
        assertTrue(AdmissionPolicy.allows(video, true, true, allowed, emptySet(), emptySet()))
        assertFalse(AdmissionPolicy.allows(video, false, true, allowed, emptySet(), emptySet()))
        assertFalse(AdmissionPolicy.allows(video, true, false, allowed, emptySet(), emptySet()))
    }

    @Test fun exactBlockOverridesExactAllow() {
        assertFalse(AdmissionPolicy.allows(video, true, true, emptySet(),
            setOf(video.id), setOf(video.id)))
    }

    @Test fun explicitVideoAllowStillChecksSafetyFlags() {
        assertTrue(AdmissionPolicy.allows(video, true, true, emptySet(), setOf(video.id), emptySet()))
        assertFalse(AdmissionPolicy.allows(video, false, true, emptySet(), setOf(video.id), emptySet()))
    }
}
