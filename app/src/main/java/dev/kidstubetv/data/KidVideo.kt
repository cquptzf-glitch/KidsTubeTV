package dev.kidstubetv.data

/** Domain model deliberately independent from an individual video UI or network backend. */
data class KidVideo(
    val id: String,
    val title: String,
    val channelId: String,
    val channelTitle: String,
    val thumbnailUrl: String,
)

data class VideoCategory(val title: String, val query: String)

object KidCategories {
    val defaults = listOf(
        VideoCategory("ABC & Numbers", "alphabet numbers educational songs for preschool kids"),
        VideoCategory("Animals & Nature", "animals nature educational videos for preschool kids"),
        VideoCategory("Nursery Rhymes", "nursery rhymes for preschool children"),
        VideoCategory("English Stories", "short English stories for preschool kids"),
    )
}

/** Never admit unverified or unapproved videos, including direct player requests. */
object AdmissionPolicy {
    fun allows(
        video: KidVideo,
        madeForKids: Boolean,
        embeddable: Boolean,
        approvedChannels: Set<String>,
        approvedVideos: Set<String>,
        blockedVideos: Set<String>,
    ): Boolean {
        if (!madeForKids || !embeddable || video.id.isBlank()) return false
        if (video.id in blockedVideos) return false
        return video.id in approvedVideos ||
            (video.channelId.isNotBlank() && video.channelId in approvedChannels)
    }
}
