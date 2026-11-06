package dev.kidstubetv.data

/** Parent-initiated clipboard link discovery; never emits URLs in diagnostics. */
object ClipboardSubscriptionPolicy {
    fun candidate(clipboard: String, current: String): String? =
        runCatching { SubscriptionFormat.normalizeUrl(clipboard) }
            .getOrNull()?.takeUnless { it == current }
}
