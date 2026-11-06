package dev.kidstubetv.proxy

import android.content.Context
import dev.kidstubetv.data.SettingsStore
import dev.kidstubetv.data.SubscriptionClient
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Auto-update runs while the app-scoped VPN is already online, so the token-bearing
 * subscription request uses KidsTubeTV's selected VPN network.
 * New config is loaded on next VPN start; never hot-replace an active core.
 */
object SubscriptionUpdater {
    // Shared with the manual TV settings updater.
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
                val yaml = SubscriptionClient().fetch(url)
                // Core already parsed config.yaml. This saves for the next VPN session.
                current.writeProfile(yaml)
                current.subscriptionUpdatedAt = System.currentTimeMillis()
            } catch (_: Exception) {
                // Keep previous good YAML; never expose URL/tokens in logs or TV UI.
            } finally {
                busy.release()
            }
        }, "kids-subscription-auto-refresh").start()
    }
}
