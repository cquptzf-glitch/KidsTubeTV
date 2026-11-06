package dev.kidstubetv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class YouTubeVideoReferenceTest {
    private val id = "dQw4w9WgXcQ"

    @Test fun supportsOfficialWatchShortAndShareLinks() {
        listOf(
            id,
            "https://youtu.be/$id?t=10",
            "https://www.youtube.com/watch?v=$id&t=10",
            "https://m.youtube.com/shorts/$id",
            "https://youtube.com/embed/$id",
            "https://youtube.com/live/$id"
        ).forEach { assertEquals(id, YouTubeVideoReference.parse(it)) }
    }

    @Test fun refusesLookalikeHostsAndWrongLengths() {
        listOf(
            "http://youtube.com/watch?v=$id",
            "https://youtube.com.evil.example/watch?v=$id",
            "https://youtube.com/watch?v=BAD",
            "https://youtu.be/$id/extra",
            "https://youtube.com/@ChannelHandle"
        ).forEach {
            assertThrows(IllegalArgumentException::class.java) {
                YouTubeVideoReference.parse(it)
            }
        }
    }
}
