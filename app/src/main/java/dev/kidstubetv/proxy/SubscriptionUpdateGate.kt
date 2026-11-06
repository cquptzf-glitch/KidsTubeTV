package dev.kidstubetv.proxy

import java.util.concurrent.atomic.AtomicBoolean

/**
 * One subscription updater at a time across TV manual import and scheduled
 * refresh. No network requests or provider tokens are tracked here.
 */
object SubscriptionUpdateGate {
    private val updating = AtomicBoolean(false)
    fun tryAcquire(): Boolean = updating.compareAndSet(false, true)
    fun release() { updating.set(false) }
}
