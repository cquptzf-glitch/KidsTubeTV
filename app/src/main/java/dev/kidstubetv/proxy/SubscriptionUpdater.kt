package dev.kidstubetv.proxy

import android.content.Context
import dev.kidstubetv.data.SettingsStore
import dev.kidstubetv.data.SubscriptionClient

/**
 * Auto-refresh uses the existing app-scoped VPN. File commits are conditional
 * on the original provider URL and profile revision, so late downloads cannot
 * replace a newly imported or edited subscription.
 */
object SubscriptionUpdater {
    private val busy = SubscriptionUpdateGate

    fun updateIfDue(context: Context) {
        val app = context.applicationContext
        val settings = SettingsStore(app)
        if (!settings.autoRefreshDue || ProxyStatus.phase != ProxyStatus.Phase.READY) return
        if (!busy.tryAcquire()) return

        Thread({
            try {
                val current = SettingsStore(app)
                if (!current.autoRefreshDue || ProxyStatus.phase != ProxyStatus.Phase.READY) return@Thread
                val url = current.subscriptionUrl
                val revision = current.subscriptionRevision()
                try {
                    val yaml = SubscriptionClient().fetch(url)
                    if (ProxyStatus.phase == ProxyStatus.Phase.READY) {
                        // No hot-reload: core reads the new YAML on the next start.
                        current.installFetchedSubscription(url, revision, yaml)
                    }
                } catch (failure: Exception) {
                    // Stale responses must never overwrite the next provider's
                    // status with an error from the previous provider.
                    current.recordSubscriptionFailureIfCurrent(
                        url, revision, failure,
                        ProxyStatus.phase == ProxyStatus.Phase.READY
                    )
                }
            } finally {
                busy.release()
            }
        }, "kids-subscription-auto-refresh").start()
    }
}
