package dev.kidstubetv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChannelReferenceTest {
    private val id = "UC1234567890123456789012"

    @Test fun supportsChannelIdsAndLinks() {
        assertEquals(ChannelReference.Id(id), ChannelReference.parse(id))
        assertEquals(ChannelReference.Id(id),
            ChannelReference.parse("https://www.youtube.com/channel/$id?sub_confirmation=1"))
        assertEquals(ChannelReference.Handle("@ExampleKids"),
            ChannelReference.parse("https://m.youtube.com/@ExampleKids"))
        assertEquals(ChannelReference.Handle("@ExampleKids"),
            ChannelReference.parse("@ExampleKids"))
        assertEquals(ChannelReference.Username("LegacyName"),
            ChannelReference.parse("https://youtube.com/user/LegacyName"))
    }

    @Test fun rejectsNonYouTubeHostsAndVideoLinks() {
        listOf(
            "https://youtube.com.evil.test/@ExampleKids",
            "http://youtube.com/@ExampleKids",
            "https://user:pass@youtube.com/@ExampleKids",
            "https://youtube.com/watch?v=12345678901",
            "https://youtu.be/12345678901",
            "https://youtube.com/c/UnresolvableAlias"
        ).forEach {
            assertThrows(IllegalArgumentException::class.java) { ChannelReference.parse(it) }
        }
    }
}
