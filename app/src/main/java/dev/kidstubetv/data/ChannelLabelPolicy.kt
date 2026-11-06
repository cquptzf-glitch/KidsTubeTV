package dev.kidstubetv.data

/** Parent-provided channel names must never become unbounded UI strings. */
object ChannelLabelPolicy {
    fun clean(text: String): String =
        text.filterNot(Char::isISOControl).trim().take(72)
}
