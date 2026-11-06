package dev.kidstubetv.proxy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import dev.kidstubetv.data.SettingsStore
import dev.kidstubetv.ui.MainActivity

/**
 * Android user-consented VPN. Only this package is routed into TUN; the rest of the
 * TV keeps its normal connection. No root, private API, or global VPN changes.
 */
class KidsVpnService : VpnService() {
    private val sessions = VpnSessionGate()
    private fun tunCallbacks(sessionId: Long) = object : NativeTunCallbacks {
        override fun protectSocket(fd: Int): Boolean {
            val success = this@KidsVpnService.protect(fd)
            if (!success) main.post { fail("VPN socket protection failed", sessionId) }
            return success
        }
        override fun resolverProcess(protocol: Int, source: String, target: String, uid: Int) = ""
    }
    private val core: NativeProxyCore = CurrentMihomoAarCore()
    private val main = Handler(Looper.getMainLooper())
    private var tunStarted = false
    private var failed = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopNow()
            ACTION_START -> {
                val sessionId = sessions.start()
                if (sessionId != null) {
                    failed = false
                    ProxyNetworkHealth.reset(this)
                    try {
                        ensureForeground()
                        ProxyStatus.update(this, ProxyStatus.Phase.STARTING, "Initializing Mihomo")
                        startCoreAsync(sessionId)
                    } catch (_: Throwable) {
                        fail("Unable to start foreground VPN", sessionId)
                    }
                }
            }
        }
        return Service.START_NOT_STICKY
    }

    private fun startCoreAsync(sessionId: Long) {
        Thread({
            try {
                val store = SettingsStore(applicationContext)
                val profile = store.profileFile
                require(profile.isFile) { "Missing Mihomo profile. Import in Parent Settings." }
                // A stopped session may still finish its asynchronous SDK setup.
                // Its callback must never establish a TUN for a later session.
                if (!isCurrent(sessionId)) return@Thread
                core.setup(applicationInfo.nativeLibraryDir,
                    profile.parentFile!!.absolutePath, Build.VERSION.SDK_INT,
                    store.selectedProxyNodes) { error ->
                    main.post {
                        if (!isCurrent(sessionId)) return@post
                        if (!error.isNullOrEmpty()) {
                            fail("Mihomo configuration failed", sessionId)
                        } else {
                            establishTun(sessionId)
                        }
                    }
                }
            } catch (error: Throwable) {
                main.post { fail(error.message ?: error.javaClass.simpleName, sessionId) }
            }
        }, "kidstubetv-mihomo-init").start()
    }

    private fun isCurrent(sessionId: Long): Boolean =
        sessions.current(sessionId)

    private fun establishTun(sessionId: Long) {
        if (!isCurrent(sessionId)) return
        try {
            val builder = Builder()
                .setSession("KidsTubeTV")
                .setMtu(1400)
                .addAddress("172.19.0.2", 30)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addAllowedApplication(packageName)

            val pfd = builder.establish() ?: error("System refused to establish VPN")
            // Ownership of this FD transfers to Mihomo, which closes it in stopTun().
            val fd = pfd.detachFd()
            core.startTun(fd, tunCallbacks(sessionId))
            tunStarted = true
            ProxyStatus.update(this, ProxyStatus.Phase.READY, "VPN tunnel started")
            ProxyNetworkHealth.check(applicationContext)
            // Update only after the app-only VPN exists. New YAML takes effect next start.
            SubscriptionUpdater.updateIfDue(applicationContext)
        } catch (error: Throwable) {
            fail(error.message ?: "Unable to start TUN", sessionId)
        }
    }

    private fun ensureForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "KidsTubeTV proxy", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("KidsTubeTV proxy")
            .setContentText("VPN is active only for KidsTubeTV")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun fail(message: String, sessionId: Long) {
        if (!isCurrent(sessionId)) return
        failed = true
        ProxyStatus.update(this, ProxyStatus.Phase.ERROR, message)
        stopNow()
    }

    private fun stopNow() {
        if (!sessions.stop()) {
            stopSelf()
            return
        }
        runCatching {
            if (tunStarted) core.stopTun()
            tunStarted = false
        }
        if (!failed) ProxyStatus.update(this, ProxyStatus.Phase.OFF, "Disconnected")
        ProxyNetworkHealth.reset(this)
        stopForeground(true)
        stopSelf()
    }

    override fun onRevoke() {
        stopNow()
        super.onRevoke()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopNow()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopNow()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "dev.kidstubetv.action.START_PROXY"
        const val ACTION_STOP = "dev.kidstubetv.action.STOP_PROXY"
        private const val CHANNEL_ID = "kidstubetv_proxy"
        private const val NOTIFICATION_ID = 17

        fun start(context: android.content.Context) {
            val intent = Intent(context, KidsVpnService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: android.content.Context) {
            // stopService is permitted while our Activity is backgrounded and
            // does not create another foreground Service to deliver STOP.
            context.stopService(Intent(context, KidsVpnService::class.java))
        }
    }
}
