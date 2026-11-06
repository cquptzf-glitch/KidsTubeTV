package dev.kidstubetv.proxy

import android.content.Context
import android.content.Intent
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Best-effort connectivity check for the current app-only Android VPN.
 *
 * "READY" means that a TUN exists. "ONLINE" adds a successful HTTPS probe,
 * while two consecutive failures mark the VPN as unreachable. The check cannot
 * certify absence of DNS/IPv6 leaks or replace device-level VPN lockdown.
 */
object ProxyNetworkHealth {
    const val ACTION_CHANGED = "dev.kidstubetv.proxy.HEALTH_CHANGED"

    enum class State { UNKNOWN, ONLINE, UNREACHABLE }
    @Volatile var state = State.UNKNOWN
        private set
    @Volatile var diagnostic: String = ""
        private set
    private val checking = AtomicBoolean(false)
    private val generation = java.util.concurrent.atomic.AtomicInteger(0)
    private var failures = 0

    @Synchronized
    private fun update(context: Context, next: State, message: String) {
        if (ProxyStatus.phase != ProxyStatus.Phase.READY && next != State.UNKNOWN) return
        if (state == next && diagnostic == message) return
        state = next
        diagnostic = message
        context.sendBroadcast(Intent(ACTION_CHANGED).setPackage(context.packageName))
    }

    fun reset(context: Context) {
        generation.incrementAndGet()
        synchronized(this) { failures = 0 }
        update(context.applicationContext, State.UNKNOWN, "")
    }

    fun check(context: Context) {
        val app = context.applicationContext
        if (ProxyStatus.phase != ProxyStatus.Phase.READY || !checking.compareAndSet(false, true)) return
        val sessionId = generation.get()
        Thread({
            var reachable = false
            try {
                // Public HTTPS network probe, not a subscription or account request.
                val conn = URL("https://www.gstatic.com/generate_204")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5500
                conn.readTimeout = 5500
                conn.useCaches = false
                try { reachable = conn.responseCode == 204 } finally { conn.disconnect() }
            } catch (_: Exception) {
                reachable = false
            } finally {
                if (generation.get() == sessionId &&
                    ProxyStatus.phase == ProxyStatus.Phase.READY) {
                    if (reachable) {
                        synchronized(this) { failures = 0 }
                        update(app, State.ONLINE, "")
                    } else {
                        val count = synchronized(this) { ++failures }
                        if (count >= 2) {
                            update(app, State.UNREACHABLE, "隧道已建立，但连接 Google 检查失败")
                        }
                    }
                }
                checking.set(false)
            }
        }, "kids-vpn-health-check").start()
    }
}
