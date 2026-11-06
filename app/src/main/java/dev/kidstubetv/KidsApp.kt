package dev.kidstubetv

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import dev.kidstubetv.proxy.KidsVpnService
import dev.kidstubetv.data.ParentSession

/** Stop the app-only VPN after all app activities have been backgrounded. */
class KidsApp : Application(), Application.ActivityLifecycleCallbacks {
    private val handler = Handler(Looper.getMainLooper())
    private var activeScreens = 0
    private val disconnect = Runnable {
        if (activeScreens == 0) KidsVpnService.stop(this)
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }
    override fun onActivityStarted(activity: Activity) {
        activeScreens++
        handler.removeCallbacks(disconnect)
    }
    override fun onActivityStopped(activity: Activity) {
        activeScreens = (activeScreens - 1).coerceAtLeast(0)
        if (activeScreens == 0) {
            ParentSession.onBackground()
            handler.postDelayed(disconnect, 6000)
        }
    }
    override fun onActivityCreated(activity: Activity, state: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
