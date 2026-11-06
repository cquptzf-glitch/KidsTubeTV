package dev.kidstubetv.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.bumptech.glide.Glide
import java.util.concurrent.Executors
import java.util.concurrent.Future
import dev.kidstubetv.data.*
import dev.kidstubetv.proxy.*

/**
 * Lean-back TV layout: content fills the screen; LEFT/MENU reveals a temporary
 * navigation drawer. Each video has one large D-pad focus target. VPN errors
 * are non-modal and anchored at the bottom, never hidden in a small icon.
 */
class MainActivity : Activity() {
    private val settings by lazy { SettingsStore(this) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var frame: FrameLayout
    private lateinit var list: LinearLayout
    private lateinit var scrolling: ScrollView
    private lateinit var status: TextView
    private lateinit var errorBar: TextView
    private lateinit var dimmer: View
    private lateinit var drawer: LinearLayout
    private var drawerFocus: View? = null
    private var showCollectionKey = "home"
    private var nextRequest = 0
    private val catalogExecutor = Executors.newSingleThreadExecutor { job ->
        Thread(job, "kids-catalog-io").apply { isDaemon = true }
    }
    private var catalogTask: Future<*>? = null
    private var loadedAfterConnection = false
    private var consentPending = false
    private var consentDeclined = false
    private val hideDrawerLater = Runnable { if (::drawer.isInitialized) hideDrawer() }
    private val networkPulse = object : Runnable {
        override fun run() {
            if (ProxyStatus.phase == ProxyStatus.Phase.READY) {
                ProxyNetworkHealth.check(this@MainActivity)
            }
            mainHandler.postDelayed(this, 25_000)
        }
    }

    private val proxyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateVpnChrome()
            if (ProxyStatus.phase == ProxyStatus.Phase.READY) loadDefaultOnce()
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        buildLayout()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(ProxyStatus.ACTION_CHANGED).apply {
            addAction(ProxyNetworkHealth.ACTION_CHANGED)
        }
        mainHandler.removeCallbacks(networkPulse)
        mainHandler.post(networkPulse)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(proxyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(proxyReceiver, filter)
        }
    }

    private var lastContentSignature = ""

    private fun contentSignature() = listOf(
        settings.channelsText,
        settings.allowedVideosText,
        settings.blockedVideosText,
        settings.apiKey.isNotBlank().toString(),
        settings.searchAllowed.toString(),
        settings.dailyLimitMinutes.toString()
    ).joinToString("|")

    override fun onResume() {
        super.onResume()
        val signature = contentSignature()
        if (signature != lastContentSignature) {
            lastContentSignature = signature
            loadedAfterConnection = false
            buildDrawer()
            showPlaceholder()
        }
        updateVpnChrome()
        if (settings.proxyReady && !consentPending && !consentDeclined &&
            ProxyStatus.phase == ProxyStatus.Phase.OFF) requestVpn()
        if (ProxyStatus.phase == ProxyStatus.Phase.READY) loadDefaultOnce()
    }

    override fun onStop() {
        unregisterReceiver(proxyReceiver)
        mainHandler.removeCallbacks(networkPulse)
        nextRequest++
        catalogTask?.cancel(true)
        catalogTask = null
        super.onStop()
    }

    private fun buildLayout() {
        frame = FrameLayout(this).apply { setBackgroundColor(TvUi.bg) }
        setContentView(frame)

        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        frame.addView(body, FrameLayout.LayoutParams(-1, -1))

        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(TvUi.dp(this@MainActivity, 44), TvUi.dp(this@MainActivity, 10),
                TvUi.dp(this@MainActivity, 42), TvUi.dp(this@MainActivity, 2))
        }
        top.addView(TvUi.label(this, "KidsTubeTV", 22f, true).apply {
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        status = TvUi.label(this, "● 网络检测中", 13f).apply {
            setTextColor(TvUi.muted)
        }
        top.addView(status)
        body.addView(top, LinearLayout.LayoutParams(-1, TvUi.dp(this, 49)))

        scrolling = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(TvUi.dp(this@MainActivity, 44), TvUi.dp(this@MainActivity, 4),
                TvUi.dp(this@MainActivity, 44), TvUi.dp(this@MainActivity, 88))
        }
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            clipChildren = false
        }
        scrolling.addView(list)
        body.addView(scrolling, LinearLayout.LayoutParams(-1, 0, 1f))

        dimmer = View(this).apply {
            setBackgroundColor(0xA609121B.toInt())
            visibility = View.GONE
            setOnClickListener { hideDrawer() }
            isFocusable = false
        }
        frame.addView(dimmer, FrameLayout.LayoutParams(-1, -1))
        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(TvUi.dp(this@MainActivity, 15), TvUi.dp(this@MainActivity, 24),
                TvUi.dp(this@MainActivity, 15), TvUi.dp(this@MainActivity, 26))
            setBackgroundColor(0xFF152932.toInt())
            visibility = View.GONE
            elevation = TvUi.dp(this@MainActivity, 22).toFloat()
        }
        frame.addView(drawer, FrameLayout.LayoutParams(TvUi.dp(this, 235), -1, Gravity.START))
        buildDrawer()

        errorBar = TvUi.label(this, "", 16f).apply {
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            setPadding(TvUi.dp(this@MainActivity, 23), TvUi.dp(this@MainActivity, 12),
                TvUi.dp(this@MainActivity, 23), TvUi.dp(this@MainActivity, 12))
            setTextColor(0xFFFFE7B5.toInt())
            background = warningShape()
            setOnClickListener {
                consentDeclined = false
                if (ProxyStatus.phase == ProxyStatus.Phase.READY) ProxyNetworkHealth.check(this@MainActivity)
                else requestVpn()
            }
            visibility = View.GONE
        }
        val barLp = FrameLayout.LayoutParams(-1, TvUi.dp(this, 56), Gravity.BOTTOM).apply {
            leftMargin = TvUi.dp(this@MainActivity, 36)
            rightMargin = TvUi.dp(this@MainActivity, 36)
            bottomMargin = TvUi.dp(this@MainActivity, 15)
        }
        frame.addView(errorBar, barLp)
        showPlaceholder()
        updateVpnChrome()
    }

    private fun warningShape() = GradientDrawable().apply {
        setColor(0xEF49352D.toInt())
        cornerRadius = TvUi.dp(this@MainActivity, 14).toFloat()
        setStroke(TvUi.dp(this@MainActivity, 1), 0xFFC58F6F.toInt())
    }

    private fun buildDrawer() {
        drawer.removeAllViews()
        drawer.addView(TvUi.label(this, "✦  KidsTubeTV", 24f, true).apply {
            setTypeface(typeface, Typeface.BOLD)
        })
        TvUi.space(drawer, this, 15)
        addDrawerItem("⌂  首页") { loadDefault(force = true) }
        addDrawerItem("♫  英语儿歌") {
            loadCollection("songs", "英语儿歌") {
                YouTubeRepository(settings).search("English nursery rhymes preschool")
            }
        }
        addDrawerItem("✦  动物世界") {
            loadCollection("animals", "动物世界") {
                YouTubeRepository(settings).search("animals English learning children")
            }
        }
        addDrawerItem("☾  睡前故事") {
            loadCollection("stories", "睡前故事") {
                YouTubeRepository(settings).search("bedtime English stories children")
            }
        }
        addDrawerItem("★  我的收藏") {
            loadCollection("favorites", "我的收藏") {
                YouTubeRepository(settings).lookup(WatchHistory(this).favorites().toList())
            }
        }
        addDrawerItem("◷  最近观看") {
            loadCollection("history", "最近观看") {
                YouTubeRepository(settings).lookup(WatchHistory(this).recentIds())
            }
        }
        if (settings.searchAllowed) addDrawerItem("⌕  搜索") { searchDialog() }
        drawer.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        val line = View(this).apply { setBackgroundColor(0xFF37505A.toInt()) }
        drawer.addView(line, LinearLayout.LayoutParams(-1, TvUi.dp(this, 1)))
        TvUi.space(drawer, this, 10)
        addDrawerItem("⚙  家长设置") { parentGate() }
    }

    private fun addDrawerItem(label: String, click: () -> Unit) {
        val button = TvUi.button(this, label) {
            hideDrawer(restoreFocus = false)
            click()
        }.apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            textSize = 19f
            setPadding(TvUi.dp(this@MainActivity, 16), 0, 0, 0)
        }
        drawer.addView(button, LinearLayout.LayoutParams(-1, TvUi.dp(this, 53)).apply {
            bottomMargin = TvUi.dp(this@MainActivity, 5)
        })
    }

    private fun openDrawer() {
        if (drawer.visibility == View.VISIBLE) return
        drawerFocus = currentFocus
        buildDrawer()
        dimmer.visibility = View.VISIBLE
        drawer.visibility = View.VISIBLE
        (drawer.getChildAt(2) ?: drawer).requestFocus()
        scheduleDrawerHide()
    }

    private fun scheduleDrawerHide() {
        mainHandler.removeCallbacks(hideDrawerLater)
        mainHandler.postDelayed(hideDrawerLater, 10_000)
    }

    private fun hideDrawer(restoreFocus: Boolean = true) {
        mainHandler.removeCallbacks(hideDrawerLater)
        drawer.visibility = View.GONE
        dimmer.visibility = View.GONE
        if (restoreFocus) {
            val target = drawerFocus
            if (target != null && target.isAttachedToWindow) target.requestFocus()
        }
    }

    private fun showPlaceholder() {
        list.removeAllViews()
        val intro = TvUi.label(this, "今天想看点什么？", 34f, true)
        list.addView(intro)
        list.addView(TvUi.label(this, "遥控器按 ← 可以打开菜单。", 16f))
        TvUi.space(list, this, 26)
        if (!settings.proxyReady || !settings.ready) {
            list.addView(TvUi.label(this,
                "需要家长先配置代理和儿童频道；设置完成后自动显示视频。", 19f))
            TvUi.space(list, this, 10)
            list.addView(TvUi.primaryButton(this, "进入家长设置") { parentGate() }.apply {
                tag = "menu-edge"
            })
        } else if (ProxyStatus.phase == ProxyStatus.Phase.STARTING) {
            list.addView(TvUi.label(this, "正在准备网络连接…", 20f))
        } else if (ProxyNetworkHealth.state == ProxyNetworkHealth.State.UNREACHABLE) {
            list.addView(TvUi.label(this, "网络暂时不可用，请使用底部提示检测连接。", 20f))
        } else {
            list.addView(TvUi.primaryButton(this, "开始观看") { loadDefault(force = true) }.apply {
                tag = "menu-edge"
            })
        }
    }

    private fun loadDefaultOnce() {
        if (!loadedAfterConnection && settings.ready) {
            loadedAfterConnection = true
            loadDefault(force = true)
        }
    }

    private fun loadDefault(force: Boolean = false) {
        if (!force && showCollectionKey == "home" && loadedAfterConnection) return
        loadCollection("home", "为你挑选") {
            YouTubeRepository(settings).homeFeed()
        }
    }

    private fun loadCollection(key: String, name: String, loader: () -> List<KidVideo>) {
        showCollectionKey = key
        if (!settings.ready) {
            showPlaceholder()
            return
        }
        if (ProxyStatus.phase != ProxyStatus.Phase.READY ||
            ProxyNetworkHealth.state == ProxyNetworkHealth.State.UNREACHABLE) {
            showPlaceholder()
            updateVpnChrome()
            return
        }
        if (WatchTime(this).remaining(settings.dailyLimitMinutes) <= 0) {
            list.removeAllViews()
            list.addView(TvUi.label(this, "今天的观看时间已经用完啦。明天再见！", 25f))
            return
        }
        val request = ++nextRequest
        catalogTask?.cancel(true)
        list.removeAllViews()
        list.addView(TvUi.label(this, "正在为你准备好看的视频…", 22f))
        catalogTask = catalogExecutor.submit {
            if (Thread.currentThread().isInterrupted) return@submit
            val result = runCatching { loader() }
            if (Thread.currentThread().isInterrupted) return@submit
            runOnUiThread {
                if (isFinishing || request != nextRequest || !::list.isInitialized) return@runOnUiThread
                if (ProxyStatus.phase != ProxyStatus.Phase.READY ||
                    ProxyNetworkHealth.state == ProxyNetworkHealth.State.UNREACHABLE) {
                    showPlaceholder()
                    updateVpnChrome()
                    return@runOnUiThread
                }
                result.onSuccess { renderCollection(name, it) }
                    .onFailure { error ->
                        list.removeAllViews()
                        list.addView(TvUi.label(this,
                            "暂时没有加载成功，请稍后再试。", 23f))
                        list.addView(TvUi.label(this, error.message.orEmpty().take(120), 14f))
                    }
            }
        }
    }

    private fun renderCollection(name: String, videos: List<KidVideo>) {
        list.removeAllViews()
        if (videos.isEmpty()) {
            list.addView(TvUi.label(this, name, 28f, true))
            list.addView(TvUi.label(this, "暂无符合家长白名单的视频。", 21f))
            return
        }
        val ids = ArrayList(videos.map { it.id })
        val hero = TvHeroCard.make(this, videos.first(), name) { openVideo(ids, 0) }
        val heroDp = (resources.displayMetrics.heightPixels /
            resources.displayMetrics.density * 0.43f).toInt().coerceIn(255, 380)
        list.addView(hero, LinearLayout.LayoutParams(-1, TvUi.dp(this, heroDp)))
        TvUi.space(list, this, 19)
        list.addView(TvUi.label(this, "发现更多精彩", 24f).apply {
            setTypeface(typeface, Typeface.BOLD)
        })
        TvUi.space(list, this, 8)

        val gap = TvUi.dp(this, 18)
        val metrics = TvGridMetrics.calculate(
            resources.displayMetrics.widthPixels, TvUi.dp(this, 88),
            gap, TvUi.dp(this, 205)
        )
        val columns = metrics.columns
        val cardWidth = metrics.cardWidth
        val grid = GridLayout(this).apply {
            columnCount = columns
            clipChildren = false
            clipToPadding = false
            setPadding(0, 0, 0, TvUi.dp(this@MainActivity, 22))
        }
        videos.forEachIndexed { index, video ->
            val card = TvVideoCard.make(this, video, cardWidth) { openVideo(ids, index) }
            if (index % columns == 0) card.tag = "menu-edge"
            grid.addView(card, GridLayout.LayoutParams().apply {
                width = cardWidth
                height = -2
                if (index % columns != columns - 1) rightMargin = gap
                bottomMargin = TvUi.dp(this@MainActivity, 20)
            })
        }
        list.addView(grid)
        scrolling.post { scrolling.scrollTo(0, 0); hero.requestFocus() }
    }

    private fun heroShape(focused: Boolean) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(0xFF21463F.toInt(), 0xFF2C5360.toInt(), 0xFF24364A.toInt())
    ).apply {
        cornerRadius = TvUi.dp(this@MainActivity, 22).toFloat()
        setStroke(TvUi.dp(this@MainActivity, if (focused) 4 else 1),
            if (focused) TvUi.warm else 0xFF567A75.toInt())
    }

    private fun openVideo(ids: ArrayList<String>, index: Int) {
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putStringArrayListExtra(PlayerActivity.EXTRA_VIDEO_IDS, ids)
            putExtra(PlayerActivity.EXTRA_INDEX, index)
        })
    }

    private fun updateVpnChrome() {
        if (!::status.isInitialized) return
        status.text = when (ProxyStatus.phase) {
            ProxyStatus.Phase.READY -> if (ProxyNetworkHealth.state == ProxyNetworkHealth.State.UNREACHABLE)
                "● 网络异常" else "● 网络已连接"
            ProxyStatus.Phase.STARTING -> "◌ 正在连接"
            else -> "○ 网络未就绪"
        }
        status.setTextColor(if (ProxyStatus.phase == ProxyStatus.Phase.READY)
            TvUi.mint else TvUi.muted)
        val error = when (ProxyStatus.phase) {
            ProxyStatus.Phase.ERROR -> "⚠ VPN 连接异常 · 按确定重试"
            ProxyStatus.Phase.OFF -> if (settings.proxyReady) "⚠ 网络已断开 · 按确定重新连接" else null
            ProxyStatus.Phase.READY -> if (ProxyNetworkHealth.state == ProxyNetworkHealth.State.UNREACHABLE)
                "⚠ VPN 已建立，但无法访问 Google · 按确定检测网络" else null
            else -> null
        }
        errorBar.text = error.orEmpty()
        errorBar.visibility = if (error == null) View.GONE else View.VISIBLE
        if (ProxyStatus.phase == ProxyStatus.Phase.OFF) loadedAfterConnection = false
    }

    private fun searchDialog() {
        if (!settings.searchAllowed) return
        val field = EditText(this).apply {
            hint = "英语儿歌 / 动物 / 故事"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        AlertDialog.Builder(this).setTitle("搜索已批准内容")
            .setView(field).setNegativeButton("取消", null)
            .setPositiveButton("搜索") { _, _ ->
                val q = field.text.toString().trim()
                if (q.isNotBlank()) loadCollection("search", "搜索结果") {
                    YouTubeRepository(settings).search(q)
                }
            }.show()
    }

    /** First-time setup opens the network tab directly, saving TV remote steps. */
    private fun openParentSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).apply {
            putExtra(SettingsActivity.EXTRA_SECTION, if (settings.hasProfile) 0 else 1)
        })
    }

    private fun parentGate() {
        if (!settings.hasPin) {
            openParentSettings()
            return
        }
        val field = EditText(this).apply {
            hint = "家长 PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this).setTitle("家长验证").setView(field)
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                if (settings.verifyPin(field.text.toString())) {
                    ParentSession.unlock()
                    openParentSettings()
                } else AlertDialog.Builder(this).setMessage("PIN 不正确或暂时锁定")
                    .setPositiveButton("确定", null).show()
            }.show()
    }

    private fun requestVpn() {
        if (!settings.proxyReady) { showPlaceholder(); return }
        if (consentPending || ProxyStatus.phase == ProxyStatus.Phase.READY ||
            ProxyStatus.phase == ProxyStatus.Phase.STARTING) return
        val intent = VpnService.prepare(this)
        if (intent == null) KidsVpnService.start(this)
        else {
            consentPending = true
            startActivityForResult(intent, REQUEST_VPN)
        }
    }

    @Deprecated("System VPN consent callback for older Android TV")
    override fun onActivityResult(code: Int, result: Int, data: Intent?) {
        super.onActivityResult(code, result, data)
        if (code == REQUEST_VPN) {
            consentPending = false
            consentDeclined = result != RESULT_OK
            if (!consentDeclined) KidsVpnService.start(this)
            else updateVpnChrome()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (::drawer.isInitialized && drawer.visibility == View.VISIBLE) {
            scheduleDrawerHide()
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
                hideDrawer()
                return true
            }
        } else {
            if (keyCode == KeyEvent.KEYCODE_MENU ||
                (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && currentFocus?.tag == "menu-edge")) {
                openDrawer()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Back first closes the drawer, then exits on the TV home screen")
    override fun onBackPressed() {
        if (::drawer.isInitialized && drawer.visibility == View.VISIBLE) {
            hideDrawer()
            return
        }
        if (showCollectionKey != "home") {
            loadDefault(force = true)
            return
        }
        KidsVpnService.stop(this)
        super.onBackPressed()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(hideDrawerLater)
        mainHandler.removeCallbacks(networkPulse)
        catalogTask?.cancel(true)
        catalogExecutor.shutdownNow()
        super.onDestroy()
    }

    companion object { private const val REQUEST_VPN = 891 }
}
