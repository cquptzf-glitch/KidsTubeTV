package dev.kidstubetv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipboardSubscriptionPolicyTest {
    @Test fun offersNewHttpsLinksWithoutRequiringTyping() {
        val url = "https://example.com/api/config?token=secret-abc"
        assertEquals(url, ClipboardSubscriptionPolicy.candidate(" $url ", ""))
        assertNull(ClipboardSubscriptionPolicy.candidate(url, url))
    }

    @Test fun ignoresUnrelatedClipboardContent() {
        assertNull(ClipboardSubscriptionPolicy.candidate("hello world", ""))
        assertNull(ClipboardSubscriptionPolicy.candidate("http://example.com/config", ""))
    }
}
