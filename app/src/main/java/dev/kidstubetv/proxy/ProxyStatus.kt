package dev.kidstubetv.proxy

import android.content.Context
import android.content.Intent

/** Process-local snapshot plus explicit package-scoped broadcasts to active UI. */
object ProxyStatus {
    const val ACTION_CHANGED = "dev.kidstubetv.proxy.STATUS_CHANGED"
    enum class Phase { OFF, STARTING, READY, ERROR }
    @Volatile var phase: Phase = Phase.OFF
        private set
    @Volatile var detail: String = ""
        private set

    fun update(context: Context, newPhase: Phase, message: String = "") {
        phase = newPhase
        detail = message
        context.sendBroadcast(Intent(ACTION_CHANGED).setPackage(context.packageName))
    }
}
