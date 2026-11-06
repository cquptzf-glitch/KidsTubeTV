package dev.kidstubetv.ui

/** Pure player gate shared by IFrame initialization and playback callbacks. */
internal object PlayerVisibilityPolicy {
    fun canPlay(
        activityVisible: Boolean,
        videoApproved: Boolean,
        vpnReady: Boolean,
        vpnReachable: Boolean
    ): Boolean = activityVisible && videoApproved && vpnReady && vpnReachable
}
