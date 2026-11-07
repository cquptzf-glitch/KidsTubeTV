package dev.kidstubetv.ui

import java.net.URI

/** Only launch the explicit verification URL returned by Google's device flow. */
internal object GoogleVerificationUrlPolicy {
    private val hosts = setOf(
        "google.com", "www.google.com", "accounts.google.com",
        "youtube.com", "www.youtube.com", "yt.be"
    )

    fun isOfficial(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme?.lowercase() == "https" &&
            uri.host?.lowercase() in hosts &&
            uri.rawUserInfo == null &&
            uri.port == -1 &&
            !uri.path.isNullOrBlank()
    }.getOrDefault(false)
}
