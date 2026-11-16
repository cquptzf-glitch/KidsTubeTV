package dev.kidstubetv.proxy

/** Bound the interval between auto-refresh checks while the app VPN is active. */
internal object SubscriptionRefreshSchedule {
    const val POLL_INTERVAL_MS: Long = 15 * 60 * 1000L
    fun shouldPoll(connected: Boolean, running: Boolean): Boolean = connected && running
}
