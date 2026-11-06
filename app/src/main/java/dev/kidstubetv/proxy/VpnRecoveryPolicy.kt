package dev.kidstubetv.proxy

/**
 * Remote-control friendly VPN recovery budget: bounded attempts, increasing
 * delays, no infinite loops while a subscription/node remains broken.
 *
 * The UI owns scheduling and only retries while its Activity is foregrounded.
 */
class VpnRecoveryPolicy(
    private val delaysMs: List<Long> = listOf(1_500, 5_000, 15_000)
) {
    init { require(delaysMs.isNotEmpty() && delaysMs.all { it >= 0 }) }
    private var attempted = 0
    val retriesUsed: Int get() = attempted
    val exhausted: Boolean get() = attempted >= delaysMs.size

    fun nextDelay(): Long? =
        if (exhausted) null else delaysMs[attempted++]

    fun onHealthy() { attempted = 0 }
    fun onManualRetry() { attempted = 0 }
}
