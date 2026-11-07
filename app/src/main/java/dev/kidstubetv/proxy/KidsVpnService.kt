package dev.kidstubetv.proxy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.net.ConnectivityManager
import android.content.Context
import android.os.Process
import java.net.InetSocketAddress
import java.net.URI
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
            if (!success) main.post { fail("VPN_PROTECT_FAILED：内核套接字保护失败", sessionId) }
            return success
        }
        override fun resolverProcess(protocol: Int, source: String, target: String, uid: Int) = ""

        // FlClash uses /proc/net on Android 9 and the OS API on 10+.
        override fun resolveUid(protocol: Int, source: String, target: String): Int {
            if (Build.VERSION.SDK_INT < 29) return -1
            return runCatching {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.getConnectionOwnerUid(
                    protocol, socketAddress(source), socketAddress(target)
                )
            }.getOrDefault(-1)
        }
        override fun resolvePackage(uid: Int): String = if (uid == Process.myUid()) {
            packageName
        } else ""
    }

    private fun socketAddress(raw: String): InetSocketAddress {
        val uri = URI("tcp://$raw")
        require(uri.port in 0..65535 && !uri.host.isNullOrBlank()) {
            "Invalid address passed by native VPN core"
        }
        return InetSocketAddress(uri.host, uri.port)
    }
    private val core: NativeProxyCore = NativeCoreFactory.create()
    private val main = Handler(Looper.getMainLooper())
    private var tunStarted = false
    private var tunAttempted = false
    private var failed = false
    private var dnsMonitor: UnderlyingDnsMonitor? = null


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
                        ProxyStatus.update(this, ProxyStatus.Phase.STARTING, "VPN_CORE_STARTING：正在加载代理配置")
                        startCoreAsync(sessionId)
                    } catch (_: Throwable) {
                        fail("VPN_FOREGROUND_FAILED：无法启动 VPN 前台服务", sessionId)
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
                            fail(VpnFailurePolicy.fromSetup(error), sessionId)
                        } else {
                            // Official core's "system" DNS has no Android fallback.
                            // Give it physical network DNS, not our own TUN DNS.
                            try {
                                startDnsMonitor(sessionId)
                                establishTun(sessionId)
                            } catch (cause: Throwable) {
                                fail(VpnFailurePolicy.fromException(cause, "DNS"), sessionId)
                            }
                        }
                    }
                }
            } catch (error: Throwable) {
                main.post { fail(VpnFailurePolicy.fromException(error, "SETUP"), sessionId) }
            }
        }, "kidstubetv-mihomo-init").start()
    }

    private fun isCurrent(sessionId: Long): Boolean =
        sessions.current(sessionId)

    private fun startDnsMonitor(sessionId: Long) {
        dnsMonitor?.stop()
        dnsMonitor = UnderlyingDnsMonitor(this, core.tunDns) { servers ->
            if (isCurrent(sessionId)) {
                ProxyDnsStatus.underlyingCount = servers.size
                core.updateSystemDns(servers)
            }
        }.also { it.start() }
    }

    private fun establishTun(sessionId: Long) {
        if (!isCurrent(sessionId)) return
        try {
            val builder = Builder()
                .setSession("KidsTubeTV")
                .setMtu(core.tunMtu)
                .addAddress(core.tunAddress, 30)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(core.tunDns)
                .addAllowedApplication(packageName)

            val pfd = builder.establish() ?: error("System refused to establish VPN")
            // Both Go backends own this detached FD once startTun is called.
            // A failed or throwing native start may have partially installed
            // socket hooks; stopTun must run even when no READY state was set.
            val fd = pfd.detachFd()
            tunAttempted = true
            check(core.startTun(fd, tunCallbacks(sessionId))) {
                "Native proxy core refused the TUN descriptor"
            }
            tunStarted = true
            ProxyStatus.update(this, ProxyStatus.Phase.READY, "VPN_TUN_ESTABLISHED：隧道启动成功，等待接口检测")
            ProxyNetworkHealth.check(applicationContext)
            // Update only after the app-only VPN exists. New YAML takes effect next start.
            SubscriptionUpdater.updateIfDue(applicationContext)
        } catch (error: Throwable) {
            fail(VpnFailurePolicy.fromException(error, "TUN"), sessionId)
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
        dnsMonitor?.stop()
        dnsMonitor = null
        ProxyDnsStatus.underlyingCount = 0
        val cleanup = runCatching {
            if (tunAttempted) core.stopTun()
        }
        tunStarted = false
        tunAttempted = false
        if (cleanup.isFailure) {
            failed = true
            ProxyStatus.update(this, ProxyStatus.Phase.ERROR,
                "VPN_CLEANUP_FAILED：原生网络资源清理失败")
        } else if (!failed) {
            ProxyStatus.update(this, ProxyStatus.Phase.OFF, "Disconnected")
        }
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
