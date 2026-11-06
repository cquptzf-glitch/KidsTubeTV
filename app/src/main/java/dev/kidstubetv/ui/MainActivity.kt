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
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.kidstubetv.data.KidCategories
import dev.kidstubetv.data.SettingsStore
import dev.kidstubetv.data.YouTubeRepository
import dev.kidstubetv.proxy.KidsVpnService
import dev.kidstubetv.proxy.ProxyStatus

/** Original Android TV launcher, with no requests before an app-scoped tunnel exists. */
class MainActivity : Activity() {
    private val store by lazy { SettingsStore(this) }
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var results: LinearLayout
    private var authorizationPending = false
    private var permissionDeclined = false
    private var serial = 0

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateStatus()
        }
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
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
    }

    override fun onStop() {
        unregisterReceiver(receiver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        showHome()
        if (store.ready && !permissionDeclined && !authorizationPending &&
            ProxyStatus.phase == ProxyStatus.Phase.OFF) requestVpn()
    }

    private fun showHome() {
        root.removeAllViews()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TvUi.label(this, "KidsTubeTV", 30f, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TvUi.button(this, "家长设置") { parentGate() })
        row.addView(TvUi.button(this, "连接") {
            permissionDeclined = false
            requestVpn()
        })
        row.addView(TvUi.button(this, "退出") {
            KidsVpnService.stop(this)
            finishAffinity()
        })
        root.addView(row)
        status = TvUi.label(this, "", 16f)
        root.addView(status)
        TvUi.space(root, this)
        val categoryBar = HorizontalScrollView(this)
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        KidCategories.defaults.forEach { category ->
            tabs.addView(TvUi.button(this, category.title) {
                loadVideos(category.title, category.query)
            })
            tabs.addView(View(this), LinearLayout.LayoutParams(TvUi.dp(this, 12), 1))
        }
        categoryBar.addView(tabs)
        root.addView(categoryBar)
        TvUi.space(root, this, 16)

        val scrolling = ScrollView(this)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrolling.addView(results)
        root.addView(scrolling, LinearLayout.LayoutParams(-1, 0, 1f))

        results.addView(TvUi.label(this,
            if (store.ready) "选择上方分类浏览已审核视频。" else
                "请先进入家长设置：配置 API Key、批准频道或视频，并导入 Mihomo YAML。", 20f))
        updateStatus()
    }

    private fun updateStatus() {
        if (!::status.isInitialized) return
        status.text = when (ProxyStatus.phase) {
            ProxyStatus.Phase.OFF -> if (store.ready) "代理未连接" else "等待家长完成配置"
            ProxyStatus.Phase.STARTING -> "代理连接中：" + ProxyStatus.detail
            ProxyStatus.Phase.READY -> "代理就绪，仅 KidsTubeTV 使用此 VPN"
            ProxyStatus.Phase.ERROR -> "连接错误：" + ProxyStatus.detail
        }
        status.setTextColor(if (ProxyStatus.phase == ProxyStatus.Phase.READY) TvUi.mint else TvUi.muted)
    }

    private fun requestVpn() {
        if (!store.ready) {
            showHome()
            return
        }
        if (authorizationPending || ProxyStatus.phase == ProxyStatus.Phase.STARTING ||
            ProxyStatus.phase == ProxyStatus.Phase.READY) return
        val intent = VpnService.prepare(this)
        if (intent == null) {
            KidsVpnService.start(this)
        } else {
            authorizationPending = true
            startActivityForResult(intent, REQUEST_VPN)
        }
    }

    @Deprecated("Legacy Android TV uses onActivityResult for VPN consent")
    override fun onActivityResult(code: Int, result: Int, data: Intent?) {
        super.onActivityResult(code, result, data)
        if (code == REQUEST_VPN) {
            authorizationPending = false
            permissionDeclined = result != RESULT_OK
            if (!permissionDeclined) KidsVpnService.start(this)
            else status.text = "未授予 VPN 权限，请在准备好后点击「连接」。"
        }
    }

    private fun loadVideos(title: String, query: String) {
        if (ProxyStatus.phase != ProxyStatus.Phase.READY) {
            results.removeAllViews()
            results.addView(TvUi.label(this, "代理未就绪：已阻止直接访问 YouTube。", 20f))
            return
        }
        val request = ++serial
        results.removeAllViews()
        results.addView(TvUi.label(this, "正在加载 " + title + " …", 20f))
        Thread({
            val result = runCatching { YouTubeRepository(store).search(query) }
            runOnUiThread {
                if (request != serial || isFinishing || !::results.isInitialized) return@runOnUiThread
                results.removeAllViews()
                result.onSuccess { videos ->
                    if (videos.isEmpty()) {
                        results.addView(TvUi.label(this,
                            "没有符合白名单、儿童标记及可嵌入要求的视频。", 20f))
                    } else {
                        results.addView(TvUi.label(this,
                            title + " · " + videos.size + " 条已批准内容", 22f, true))
                        val ids = ArrayList(videos.map { it.id })
                        videos.forEachIndexed { index, video ->
                            results.addView(TvUi.button(this,
                                (index + 1).toString() + ". " + video.title + "    /    " + video.channelTitle) {
                                startActivity(Intent(this, PlayerActivity::class.java).apply {
                                    putStringArrayListExtra(PlayerActivity.EXTRA_VIDEO_IDS, ids)
                                    putExtra(PlayerActivity.EXTRA_INDEX, index)
                                })
                            })
                            TvUi.space(results, this)
                        }
                    }
                }.onFailure { err ->
                    results.addView(TvUi.label(this, "加载失败：" +
                        (err.message ?: "未知错误") + "。请检查代理、Google API Key 和配额。", 18f))
                }
            }
        }, "kids-search").start()
    }

    private fun parentGate() {
        if (!store.hasPin) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        val pin = EditText(this).apply {
            hint = "家长 PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("家长验证")
            .setView(pin)
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                if (store.verifyPin(pin.text.toString())) {
                    startActivity(Intent(this, SettingsActivity::class.java))
                } else {
                    AlertDialog.Builder(this).setMessage("PIN 错误").setPositiveButton("确定", null).show()
                }
            }.show()
    }

    @Deprecated("The explicit Back action exits this client")
    override fun onBackPressed() {
        KidsVpnService.stop(this)
        super.onBackPressed()
    }

    companion object { private const val REQUEST_VPN = 891 }
}
