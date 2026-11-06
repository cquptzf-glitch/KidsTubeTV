package dev.kidstubetv.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.kidstubetv.data.*
import dev.kidstubetv.proxy.*

/** Android 9 TV home: focus cards, approved channels, collections and child-safe search. */
class MainActivity : Activity() {
    private val settings by lazy { SettingsStore(this) }
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var results: LinearLayout
    private var authorizationPending = false
    private var permissionDeclined = false
    private var searchSerial = 0

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { updateStatus() }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        root = TvUi.column(this)
        setContentView(root)
        showHome()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(ProxyStatus.ACTION_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
    }

    override fun onStop() {
        unregisterReceiver(receiver)
        searchSerial++
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        showHome()
        if (settings.proxyReady && !permissionDeclined && !authorizationPending &&
            ProxyStatus.phase == ProxyStatus.Phase.OFF) requestVpn()
    }

    private fun showHome() {
        root.removeAllViews()
        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleBar.addView(TvUi.label(this, "KidsTubeTV", 29f, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        titleBar.addView(TvUi.button(this, "家长设置") { parentGate() })
        titleBar.addView(TvUi.button(this, "连接 VPN") {
            permissionDeclined = false
            requestVpn()
        })
        titleBar.addView(TvUi.button(this, "退出") {
            KidsVpnService.stop(this)
            finishAffinity()
        })
        root.addView(titleBar)
        status = TvUi.label(this, "", 16f)
        root.addView(status)
        root.addView(TvUi.label(this, "今日剩余观看：" +
            (WatchTime(this).remaining(settings.dailyLimitMinutes) / 60) +
            " 分钟（每日上限 " + settings.dailyLimitMinutes + " 分钟）", 15f))
        TvUi.space(root, this)

        val bar = HorizontalScrollView(this)
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (category in KidCategories.defaults) {
            tabs.addView(TvUi.button(this, category.title) {
                loadVideos(category.title) { YouTubeRepository(settings).search(category.query) }
            })
            tabs.addView(View(this), LinearLayout.LayoutParams(TvUi.dp(this, 12), 1))
        }
        tabs.addView(TvUi.button(this, "收藏") {
            loadVideos("我的收藏") { YouTubeRepository(settings).lookup(WatchHistory(this).favorites().toList()) }
        })
        tabs.addView(TvUi.button(this, "最近观看") {
            loadVideos("最近观看") { YouTubeRepository(settings).lookup(WatchHistory(this).recentIds()) }
        })
        if (settings.searchAllowed) {
            tabs.addView(TvUi.button(this, "搜索") { showSearch() })
        }
        settings.allowedChannels.take(8).forEachIndexed { index, channel ->
            tabs.addView(TvUi.button(this, "批准频道 " + (index + 1)) {
                loadVideos("频道 " + channel.take(10)) { YouTubeRepository(settings).uploads(channel) }
            })
        }
        bar.addView(tabs)
        root.addView(bar)
        TvUi.space(root, this, 14)

        val scroll = ScrollView(this)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(results)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        results.addView(TvUi.label(this, if (settings.ready) {
            "选择分类或「批准频道」开始观看。推荐先从已批准频道播放。"
        } else {
            "请进入家长设置：配置 PIN、YouTube API 密钥、批准频道或视频、Clash/Mihomo YAML 或 HTTPS 订阅。"
        }, 19f))
        updateStatus()
    }

    private fun updateStatus() {
        if (!::status.isInitialized) return
        status.text = when (ProxyStatus.phase) {
            ProxyStatus.Phase.OFF -> if (settings.ready) "代理已断开" else "等待家长完成配置"
            ProxyStatus.Phase.STARTING -> "代理连接中：" + ProxyStatus.detail
            ProxyStatus.Phase.READY -> "VPN 已建立（仅本应用走代理）"
            ProxyStatus.Phase.ERROR -> "代理连接失败：" + ProxyStatus.detail
        }
        status.setTextColor(if (ProxyStatus.phase == ProxyStatus.Phase.READY)
            TvUi.mint else TvUi.muted)
    }

    private fun requestVpn() {
        if (!settings.proxyReady) { showHome(); return }
        if (authorizationPending || ProxyStatus.phase == ProxyStatus.Phase.STARTING ||
            ProxyStatus.phase == ProxyStatus.Phase.READY) return
        val request = VpnService.prepare(this)
        if (request == null) KidsVpnService.start(this)
        else {
            authorizationPending = true
            startActivityForResult(request, REQUEST_VPN)
        }
    }

    @Deprecated("System VPN permission activity result")
    override fun onActivityResult(code: Int, result: Int, data: Intent?) {
        super.onActivityResult(code, result, data)
        if (code == REQUEST_VPN) {
            authorizationPending = false
            permissionDeclined = result != RESULT_OK
            if (!permissionDeclined) KidsVpnService.start(this)
            else status.text = "VPN 授权已取消，请在准备好后点击「连接 VPN」。"
        }
    }

    private fun showSearch() {
        if (!settings.searchAllowed) return
        val field = EditText(this).apply {
            hint = "搜索英语儿歌、动物、数字等"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        AlertDialog.Builder(this).setTitle("搜索家长批准的视频")
            .setView(field).setNegativeButton("取消", null)
            .setPositiveButton("搜索") { _, _ ->
                val keyword = field.text.toString().trim()
                if (keyword.isNotEmpty()) {
                    loadVideos("搜索：" + keyword) { YouTubeRepository(settings).search(keyword) }
                }
            }.show()
    }

    private fun loadVideos(title: String, load: () -> List<KidVideo>) {
        if (!settings.ready) {
            results.removeAllViews()
            results.addView(TvUi.label(this,
                "请先在家长设置中配置 API Key 或 Google 登录，并批准至少一个频道/视频。", 18f))
            return
        }
        if (ProxyStatus.phase != ProxyStatus.Phase.READY) {
            results.removeAllViews()
            results.addView(TvUi.label(this, "代理未就绪，已阻止直连访问 YouTube。", 18f))
            return
        }
        if (WatchTime(this).remaining(settings.dailyLimitMinutes) <= 0) {
            results.removeAllViews()
            results.addView(TvUi.label(this, "今天的观看时间已用完，明天再见！", 21f))
            return
        }
        val request = ++searchSerial
        results.removeAllViews()
        results.addView(TvUi.label(this, "正在加载 " + title + " …", 19f))
        Thread({
            val response = runCatching { load() }
            runOnUiThread {
                if (request != searchSerial || isFinishing || !::results.isInitialized) return@runOnUiThread
                if (ProxyStatus.phase != ProxyStatus.Phase.READY) return@runOnUiThread
                results.removeAllViews()
                response.onSuccess { videos ->
                    if (videos.isEmpty()) {
                        results.addView(TvUi.label(this,
                            "没有通过家长白名单、儿童标记及可嵌入检查的内容。", 18f))
                        return@onSuccess
                    }
                    results.addView(TvUi.label(this, title + " · " + videos.size + " 条", 22f, true))
                    val grid = GridLayout(this).apply { columnCount = 3 }
                    val ids = ArrayList(videos.map { it.id })
                    videos.forEachIndexed { index, video ->
                        val card = TvVideoCard.make(this, video) {
                            startActivity(Intent(this, PlayerActivity::class.java).apply {
                                putStringArrayListExtra(PlayerActivity.EXTRA_VIDEO_IDS, ids)
                                putExtra(PlayerActivity.EXTRA_INDEX, index)
                            })
                        }
                        val layout = GridLayout.LayoutParams().apply {
                            width = TvUi.dp(this@MainActivity, 236)
                            height = TvUi.dp(this@MainActivity, 232)
                            setMargins(TvUi.dp(this@MainActivity, 6), TvUi.dp(this@MainActivity, 6),
                                TvUi.dp(this@MainActivity, 6), TvUi.dp(this@MainActivity, 6))
                        }
                        grid.addView(card, layout)
                    }
                    results.addView(grid)
                }.onFailure {
                    results.addView(TvUi.label(this,
                        "加载失败：" + (it.message ?: "未知错误") + "。检查代理 / 密钥 / Google API 配额。", 17f))
                }
            }
        }, "kids-catalogue").start()
    }

    private fun parentGate() {
        if (!settings.hasPin) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        val pin = EditText(this).apply {
            hint = "家长 PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this).setTitle("家长验证").setView(pin)
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                if (settings.verifyPin(pin.text.toString())) {
                    ParentSession.unlock()
                    startActivity(Intent(this, SettingsActivity::class.java))
                } else {
                    val seconds = ((settings.pinLockedUntil - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                    AlertDialog.Builder(this)
                        .setMessage(if (seconds > 0) "尝试次数过多，请 " + seconds + " 秒后再试"
                            else "PIN 错误")
                        .setPositiveButton("确定", null).show()
                }
            }.show()
    }

    @Deprecated("TV Back from root closes child app")
    override fun onBackPressed() {
        KidsVpnService.stop(this)
        super.onBackPressed()
    }

    companion object { private const val REQUEST_VPN = 891 }
}
