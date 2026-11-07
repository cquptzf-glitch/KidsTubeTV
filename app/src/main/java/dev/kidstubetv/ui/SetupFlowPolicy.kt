package dev.kidstubetv.ui

/** Pure onboarding priority, shared between TV remote and phone touch UI. */
internal object SetupFlowPolicy {
    enum class Step { PIN, PROFILE, VPN, AUTH, APPROVAL, COMPLETE }

    fun next(
        pin: Boolean,
        profile: Boolean,
        vpnReady: Boolean,
        youtubeCredential: Boolean,
        hasApprovedContent: Boolean,
    ): Step = when {
        !pin -> Step.PIN
        !profile -> Step.PROFILE
        !vpnReady -> Step.VPN
        !youtubeCredential -> Step.AUTH
        !hasApprovedContent -> Step.APPROVAL
        else -> Step.COMPLETE
    }
}
