package dev.kidstubetv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelLabelPolicyTest {
    @Test fun keepsLanguageAndRemovesControls() {
        assertEquals("小猪英语 ABC", ChannelLabelPolicy.clean("  小猪\n英语 ABC\t"))
        assertEquals(72, ChannelLabelPolicy.clean("a".repeat(200)).length)
    }
}
