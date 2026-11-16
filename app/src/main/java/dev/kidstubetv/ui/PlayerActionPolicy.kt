package dev.kidstubetv.ui

/**
 * Common fail-closed playback control policy: even explicit remote/phone input
 * must not revive a previous or still-unverified YouTube IFrame.
 */
internal object PlayerActionPolicy {
    fun canControl(
        visibleToUser: Boolean,
        videoApproved: Boolean,
        reportedVideoMatches: Boolean,
        vpnReady: Boolean,
        hasTime: Boolean
    ): Boolean = PlayerVisibilityPolicy.canPlay(
        visibleToUser, videoApproved && reportedVideoMatches,
        vpnReady, true, hasTime
    )
}
