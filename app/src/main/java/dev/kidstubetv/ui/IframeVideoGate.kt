package dev.kidstubetv.ui

/**
 * IFrame state/seconds callbacks carry no media ID. Use its separate onVideoId
 * callback to bind such events to the media that passed the latest admission.
 * Never attribute an old iframe event to a newly approved video.
 */
internal class IframeVideoGate {
    private var approvedId: String? = null
    private var reportedId: String? = null

    fun approve(id: String) {
        require(id.matches(Regex("[A-Za-z0-9_-]{11}")))
        approvedId = id
        reportedId = null
    }

    fun onVideoId(id: String): Boolean {
        reportedId = id
        return isCurrent()
    }

    fun isCurrent(): Boolean = approvedId != null && approvedId == reportedId

    fun hasUnapprovedReportedVideo(): Boolean =
        reportedId != null && !isCurrent()

    fun revoke() {
        approvedId = null
        reportedId = null
    }
}
