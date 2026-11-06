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
import io.github.oviron.libmihomo.Clash
import io.github.oviron.libmihomo.TunInterface
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android user-consented VPN. Only this package is routed into TUN; the rest of the
 * TV keeps its normal connection. No root, private API, or global VPN changes.
 */
class KidsVpnService : VpnService(), TunInterface {
    private val active = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())
    private var tunStarted = false
    private var failed = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopNow()
            ACTION_START -> if (active.compareAndSet(false, true)) {
                failed = false
                ensureForeground()
                ProxyStatus.update(this, ProxyStatus.Phase.STARTING, "Initializing Mihomo")
                startCoreAsync()
            }
        }
        return Service.START_NOT_STICKY
    }

    private fun startCoreAsync() {
        Thread({
            try {
                val profile = SettingsStore(this).profileFile
                require(profile.isFile) { "Missing Mihomo profile. Import in Parent Settings." }
                Clash.load(applicationInfo.nativeLibraryDir)
                val init = JSONObject().put("homeDir", filesDir.absolutePath).toString()
                val setup = JSONObject().put("profile", profile.absolutePath).toString()
                Clash.quickSetup(init, setup) { error ->
                    main.post {
                        if (!active.get()) return@post
                        if (!error.isNullOrEmpty()) {
                            fail("Mihomo configuration: $error")
                        } else {
                            establishTun()
                        }
                    }
                }
            } catch (error: Throwable) {
                main.post { fail(error.message ?: error.javaClass.simpleName) }
            }
        }, "kidstubetv-mihomo-init").start()
    }

    private fun establishTun() {
        if (!active.get()) return
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
            Clash.startTUN(fd, this, "kidstubetv", "system",
                "172.19.0.2/30", "1.1.1.1", 1400)
            tunStarted = true
            ProxyStatus.update(this, ProxyStatus.Phase.READY, "VPN tunnel started")
        } catch (error: Throwable) {
            fail(error.message ?: "Unable to start TUN")
        }
    }

    /** Avoid loops: outbound Mihomo connections must bypass the same VPN. */
    override fun protect(fd: Int) {
        super.protect(fd)
    }

    override fun resolverProcess(protocol: Int, source: String, target: String, uid: Int): String = ""

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

    private fun fail(message: String) {
        if (!active.get()) return
        failed = true
        ProxyStatus.update(this, ProxyStatus.Phase.ERROR, message)
        stopNow()
    }

    private fun stopNow() {
        if (!active.getAndSet(false)) {
            stopSelf()
            return
        }
        runCatching {
            if (Clash.isLoaded() && tunStarted) Clash.stopTun()
            tunStarted = false
            if (Clash.isLoaded()) Clash.setEventListener(null as io.github.oviron.libmihomo.InvokeInterface?)
        }
        if (!failed) ProxyStatus.update(this, ProxyStatus.Phase.OFF, "Disconnected")
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
            context.startService(Intent(context, KidsVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
