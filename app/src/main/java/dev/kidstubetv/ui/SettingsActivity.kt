package dev.kidstubetv.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.net.VpnService
import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.view.WindowManager
import android.content.ClipboardManager
import android.content.ClipData
import android.content.ClipDescription
import android.os.PersistableBundle
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import dev.kidstubetv.data.ChannelReference
import dev.kidstubetv.data.YouTubeVideoReference
import dev.kidstubetv.data.KidVideo
import dev.kidstubetv.data.YouTubeRepository
import dev.kidstubetv.data.YouTubeChannelInfo
import dev.kidstubetv.data.GoogleTvAuth
import dev.kidstubetv.data.ParentSession
import dev.kidstubetv.data.SettingsStore
import dev.kidstubetv.data.SubscriptionClient
import dev.kidstubetv.data.SubscriptionProbe
import dev.kidstubetv.data.SubscriptionFormat
import dev.kidstubetv.data.SubscriptionFailurePolicy
import dev.kidstubetv.data.WatchHistory
import dev.kidstubetv.data.WatchTime
import dev.kidstubetv.proxy.KidsVpnService
import dev.kidstubetv.proxy.VpnManualPause
import dev.kidstubetv.proxy.ProxyNetworkHealth
import dev.kidstubetv.proxy.ProxyStatus
import java.io.ByteArrayOutputStream
import java.net.URI
import java.text.DateFormat
import java.util.Date

/**
 * Parent-only V2 high-density settings hub, with TV rail and phone tabs.
 *
 * Most settings are one-tap actions; no full-screen editable form. Sensitive
 * subscription URLs are only shown while explicitly editing behind PIN.
 */
class SettingsActivity : Activity() {
    /** Consume an expired parent's remote event before it reaches a sensitive button. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && store.hasPin && !ParentSession.onInteraction()) {
            finish()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Every remote action is gated by the recent parent PIN session. */
    override fun onUserInteraction() {
        super.onUserInteraction()
        if (store.hasPin && !ParentSession.onInteraction()) finish()
    }

    private val store by lazy { SettingsStore(this) }
    private val handheld by lazy { UiDevice.isHandheld(this) }
    private lateinit var scroller: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var summary: TextView
    private lateinit var setupTile: SettingsActionTile
    private lateinit var networkState: TextView
    private lateinit var networkDetail: TextView
    private lateinit var childSummary: TextView
    private lateinit var accountSummary: TextView
    private lateinit var parentSummary: TextView
    private lateinit var overviewNetwork: SettingsActionTile
    private lateinit var overviewYoutube: SettingsActionTile
    private lateinit var overviewContent: SettingsActionTile
    private lateinit var overviewParent: SettingsActionTile
    private var overviewStats: List<TextView> = emptyList()
    private var networkStats: List<TextView> = emptyList()
    private var contentStats: List<TextView> = emptyList()
    private var accountStats: List<TextView> = emptyList()
    private lateinit var networkAdvanced: LinearLayout
    private lateinit var subscriptionTile: SettingsActionTile
    private lateinit var subscriptionRefreshTile: SettingsActionTile
    private lateinit var yamlImportTile: SettingsActionTile
    private lateinit var inlineSubscriptionPanel: LinearLayout
    private lateinit var inlineSubscriptionInput: EditText
    private var inlineSubscriptionOpen = false
    private val sections = mutableListOf<LinearLayout>()
    private val tabs = mutableListOf<Button>()
    private var selectedSection = 0
    private lateinit var pageTitle: TextView
    private lateinit var mobileBackButton: Button
    private lateinit var searchTile: SettingsActionTile
    private lateinit var autoplayTile: SettingsActionTile
    private lateinit var vpnTile: SettingsActionTile
    private val uiHandler = Handler(Looper.getMainLooper())
    private var screenStarted = false
    private val subscriptionProbeRequests = LatestResultGate()
    private var subscriptionProbeRunning = false
    private val startVpnAfterStop = Runnable {
        if (!isFinishing && !isDestroyed && ParentSession.authorized()) requestVpnFromParent()
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (::networkState.isInitialized) refreshSummaries()
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (store.hasPin && !ParentSession.authorized()) { finish(); return }
        UiDevice.showSystemBarsForHandheld(this)

        // V2: persistent category rail on TV; horizontal category strip on phone.
        // Pages hold two densely populated functional groups, not nested menus.
        val root = LinearLayout(this).apply {
            orientation = if (handheld) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            setBackgroundColor(TvUi.bg)
        }
        setContentView(root)
        if (!handheld) {
            val rail = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(TvUi.dp(this@SettingsActivity, 13),
                    TvUi.dp(this@SettingsActivity, 18),
                    TvUi.dp(this@SettingsActivity, 13),
                    TvUi.dp(this@SettingsActivity, 12))
                setBackgroundColor(0xFF142630.toInt())
            }
            root.addView(rail, LinearLayout.LayoutParams(TvUi.dp(this, 205), -1))
            rail.addView(TvUi.label(this, "◈ KidsTubeTV", 20f, true))
            rail.addView(TvUi.label(this, "家长设置", 13f).apply { setTextColor(TvUi.muted) })
            TvUi.space(rail, this, 13)
            SettingsDestination.entries.forEachIndexed { index, route ->
                addNavigationButton(rail, index, route.navigationTitle, false)
            }
            rail.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
            rail.addView(TvUi.label(this, "专属儿童空间", 14f, true))
            rail.addView(TvUi.label(this,
                "仅 KidsTubeTV 使用应用专属 VPN，所有设置受 PIN 保护。", 12f)
                .apply { setTextColor(TvUi.muted) })
            rail.addView(TvUi.button(this, "← 返回儿童首页") { finish() },
                LinearLayout.LayoutParams(-1, TvUi.dp(this, 49)))
        }

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = if (handheld) 12 else 20
            setPadding(TvUi.dp(this@SettingsActivity, pad),
                TvUi.dp(this@SettingsActivity, if (handheld) 5 else 12),
                TvUi.dp(this@SettingsActivity, pad), 0)
        }
        root.addView(right, if (handheld) LinearLayout.LayoutParams(-1, -1)
            else LinearLayout.LayoutParams(0, -1, 1f))
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        pageTitle = TvUi.label(this, "设置概览", if (handheld) 21f else 25f, true)
        titleRow.addView(pageTitle, LinearLayout.LayoutParams(0, -2, 1f))
        if (handheld) {
            mobileBackButton = TvUi.button(this, "返回") { onBackPressed() }.apply {
                textSize = 13f
            }
            titleRow.addView(mobileBackButton, LinearLayout.LayoutParams(
                TvUi.dp(this, 68), TvUi.dp(this, 39)))
        }
        right.addView(titleRow)

        if (handheld) {
            val navScroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                isFillViewport = false
            }
            val navRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            SettingsDestination.entries.forEachIndexed { index, route ->
                addNavigationButton(navRow, index, route.navigationTitle, true)
            }
            navScroll.addView(navRow)
            right.addView(navScroll, LinearLayout.LayoutParams(-1, TvUi.dp(this, 47)).apply {
                bottomMargin = TvUi.dp(this@SettingsActivity, 3)
            })
        }

        summary = TvUi.label(this, "", if (handheld) 12f else 14f).apply {
            setTextColor(TvUi.muted)
        }
        right.addView(summary)
        setupTile = SettingsDecor.action(this, "继续配置", "完成下一步初始化", true) {
            advanceSetup()
        }
        right.addView(setupTile, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = TvUi.dp(this@SettingsActivity, 7)
        })
        scroller = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            clipToPadding = false
        }
        right.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, TvUi.dp(this@SettingsActivity, 4),
                0, TvUi.dp(this@SettingsActivity, 20))
        }
        scroller.addView(content)
        repeat(SettingsDestination.entries.size) {
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
            }
            sections.add(panel)
            content.addView(panel)
        }
        buildOverviewSection(sections[SettingsDestination.OVERVIEW.ordinal])
        buildNetworkSection(sections[SettingsDestination.NETWORK.ordinal])
        buildAccountSection(sections[SettingsDestination.YOUTUBE.ordinal])
        buildChildSection(sections[SettingsDestination.CONTENT.ordinal])
        buildParentSection(sections[SettingsDestination.PARENT.ordinal])
        val initial = state?.getInt(SAVED_SECTION) ?: intent.getIntExtra(EXTRA_SECTION, 0)
        showSection(SettingsDestination.fromIndex(initial).ordinal, moveFocus = !handheld)
        refreshSummaries()
        if (!store.hasPin) setPinDialog(initial = true)
    }

    private fun addNavigationButton(parent: LinearLayout, index: Int, label: String, phone: Boolean) {
        val button = Button(this).apply {
            id = View.generateViewId()
            text = label
            isAllCaps = false
            textSize = if (phone) 13f else 16f
            gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
            val spacing = TvUi.dp(this@SettingsActivity, if (phone) 11 else 12)
            setPadding(spacing, 0, spacing, 0)
            setTextColor(TvUi.text)
            background = SettingsDecor.navShape(this@SettingsActivity, index == 0, false)
            setOnClickListener { showSection(index, moveFocus = !handheld) }
            setOnFocusChangeListener { _, focused ->
                background = SettingsDecor.navShape(this@SettingsActivity,
                    selectedSection == index, focused)
            }
        }
        tabs.add(button)
        parent.addView(button, if (phone)
            LinearLayout.LayoutParams(-2, TvUi.dp(this, 40)).apply {
                rightMargin = TvUi.dp(this@SettingsActivity, 5)
            }
        else LinearLayout.LayoutParams(-1, TvUi.dp(this, 52)).apply {
            bottomMargin = TvUi.dp(this@SettingsActivity, 5)
        })
    }

    override fun onStart() {
        super.onStart()
        screenStarted = true
        val filter = IntentFilter(ProxyStatus.ACTION_CHANGED).apply {
            addAction(ProxyNetworkHealth.ACTION_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(statusReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        if (store.hasPin && !ParentSession.authorized()) {
            finish()
            return
        }
        if (::summary.isInitialized) refreshSummaries()
    }

    override fun onStop() {
        screenStarted = false
        subscriptionProbeRequests.invalidate()
        subscriptionProbeRunning = false
        unregisterReceiver(statusReceiver)
        uiHandler.removeCallbacks(startVpnAfterStop)
        super.onStop()
    }

    private fun showSection(index: Int, moveFocus: Boolean = false) {
        val route = SettingsDestination.fromIndex(index)
        if (inlineSubscriptionOpen && route != SettingsDestination.NETWORK) {
            hideSubscriptionEditor()
        }
        selectedSection = route.ordinal
        pageTitle.text = route.title
        sections.forEachIndexed { i, panel ->
            panel.visibility = if (i == route.ordinal) View.VISIBLE else View.GONE
        }
        tabs.forEachIndexed { i, button ->
            button.background = SettingsDecor.navShape(this,
                i == route.ordinal, button.hasFocus())
        }
        val overview = route == SettingsDestination.OVERVIEW
        summary.visibility = if (overview) View.VISIBLE else View.GONE
        setupTile.visibility = if (overview) View.VISIBLE else View.GONE
        if (handheld) {
            mobileBackButton.text = if (overview) "‹ 首页" else "‹ 概览"
        }
        scroller.scrollTo(0, 0)
        wireTvFocus(moveFocus)
    }

    /**
     * Explicit left/right focus edges avoid TV remote focus escaping into the
     * previous screen. New items revealed by an advanced accordion are wired too.
     */
    private fun wireTvFocus(moveFocus: Boolean = false) {
        if (handheld) return
        val targets = ArrayList<View>()
        sections[selectedSection].addFocusables(targets, View.FOCUS_FORWARD)
        val railId = tabs[selectedSection].id
        targets.forEach { target ->
            if (target.id == View.NO_ID) target.id = View.generateViewId()
            target.nextFocusLeftId = railId
        }
        targets.firstOrNull()?.let { first ->
            tabs[selectedSection].nextFocusRightId = first.id
            if (moveFocus) first.requestFocus()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(SAVED_SECTION, selectedSection)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("TV remote and Android back both return to the settings overview")
    override fun onBackPressed() {
        if (inlineSubscriptionOpen) {
            hideSubscriptionEditor()
            return
        }
        val back = SettingsNavigationPolicy.back(SettingsDestination.fromIndex(selectedSection))
        if (back == null) finish() else showSection(back.ordinal, moveFocus = !handheld)
    }

    private fun heading(panel: LinearLayout, title: String, hint: String) {
        panel.addView(TvUi.label(this, title, 21f, true))
        panel.addView(TvUi.label(this, hint, 15f).apply { setTextColor(TvUi.muted) })
        TvUi.space(panel, this, 12)
    }

    private fun action(
        panel: LinearLayout,
        title: String,
        hint: String,
        primary: Boolean = false,
        callback: () -> Unit
    ): SettingsActionTile {
        val tile = SettingsDecor.action(this, title, hint, primary, callback)
        panel.addView(tile, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = TvUi.dp(this@SettingsActivity, 6)
        })
        return tile
    }

    private fun expandButton(panel: LinearLayout, title: String, advanced: LinearLayout) {
        val button = action(panel, title + "  ▾", "低频功能，需要时再展开") {
            advanced.visibility = if (advanced.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        button.setOnClickListener {
            val open = advanced.visibility != View.VISIBLE
            advanced.visibility = if (open) View.VISIBLE else View.GONE
            button.setTitle(title + if (open) "  ▴" else "  ▾")
            wireTvFocus()
        }
    }

    private fun buildOverviewSection(panel: LinearLayout) {
        DenseSettingsUi.intro(this, panel, "家长设置总览",
            "网络状态、儿童观看规则和常用操作集中在一页；不必逐个进入子页面。")
        overviewStats = DenseSettingsUi.stats(this, panel, listOf(
            "网络" to "未连接", "允许频道" to "0 个",
            "今日观看" to "0 分钟", "每日限额" to "60 分钟"
        ))
        val (quick, rules) = DenseSettingsUi.twoPanes(this, panel,
            "快速操作", "最常用的四个入口", "日常观看设置", "主要规则可以直接操作")
        overviewNetwork = action(quick, "应用网络", "订阅、导入 YAML 与连接状态", true) {
            showSection(SettingsDestination.NETWORK.ordinal, moveFocus = !handheld)
        }
        overviewYoutube = action(quick, "YouTube 账号", "Google 授权或 API Key") {
            showSection(SettingsDestination.YOUTUBE.ordinal, moveFocus = !handheld)
        }
        overviewContent = action(quick, "儿童内容", "频道、视频白名单与观看限制") {
            showSection(SettingsDestination.CONTENT.ordinal, moveFocus = !handheld)
        }
        overviewParent = action(quick, "家长与安全", "PIN、隐私和设备诊断") {
            showSection(SettingsDestination.PARENT.ordinal, moveFocus = !handheld)
        }
        DenseSettingsUi.line(this, rules, "观看内容", "只显示家长批准的频道或视频")
        action(rules, "每日观看时长", "调整孩子每天可看的分钟数") { pickDailyLimit() }
        action(rules, "允许搜索 / 自动播放", "可在儿童内容页直接切换") {
            showSection(SettingsDestination.CONTENT.ordinal, moveFocus = !handheld)
        }
        action(rules, "添加 YouTube 频道", "粘贴频道链接或 @handle") { addChannelFromLink() }
        DenseSettingsUi.line(this, rules, "家长保护", "修改 PIN 需要已解锁的会话")
    }

    private fun buildParentSection(panel: LinearLayout) {
        DenseSettingsUi.intro(this, panel, "家长控制与隐私",
            "PIN、会话超时、观看记录和设备诊断集中管理。")
        val (security, privacy) = DenseSettingsUi.twoPanes(this, panel,
            "家长安全", "敏感操作需 PIN 解锁", "数据与隐私", "仅管理本机数据")
        parentSummary = TvUi.label(this, "", 15f).apply { setTextColor(TvUi.muted) }
        security.addView(parentSummary)
        action(security, "修改家长 PIN", "4～12 位数字，防止儿童修改设置", true) {
            setPinDialog(initial = false)
        }
        DenseSettingsUi.line(this, security, "切换到后台", "3 分钟内返回无需重新解锁")
        DenseSettingsUi.line(this, security, "前台闲置", "2 分钟未操作需重新验证")
        action(security, "设备与网络诊断", "检查 VPN、账号及本机状态") {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
        action(privacy, "清除历史与收藏", "本机观看数据；清除前再次确认", true) {
            AlertDialog.Builder(this)
                .setTitle("清除观看数据？")
                .setMessage("将清除本机的观看历史和收藏，操作无法撤销。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认清除") { _, _ ->
                    WatchHistory(this).clear()
                    refreshSummaries()
                    parentSummary.text = "✓ 本机观看历史与收藏已清空"
                }.show()
        }
        DenseSettingsUi.line(this, privacy, "订阅令牌", "仅家长编辑时显示")
        DenseSettingsUi.line(this, privacy, "Google 凭据", "不写入普通诊断日志")
        DenseSettingsUi.line(this, privacy, "儿童观看记录", "仅存储在本机")
        action(privacy, "查看脱敏诊断报告", "不包含订阅链接和 Google 密钥") {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
    }

    private fun buildChildSection(panel: LinearLayout) {
        DenseSettingsUi.intro(this, panel, "儿童内容白名单",
            "频道、单条视频、观看时长、搜索和自动播放都在本页操作。")
        contentStats = DenseSettingsUi.stats(this, panel, listOf(
            "已批准频道" to "0 个", "已批准视频" to "0 个",
            "每日上限" to "60 分钟", "自动播放" to "关闭"
        ))
        val (channels, watching) = DenseSettingsUi.twoPanes(this, panel,
            "频道与视频白名单", "家长审核后才可浏览", "观看规则", "开关及时间限制即时保存")
        childSummary = TvUi.label(this, "", 14f).apply { setTextColor(TvUi.muted) }
        channels.addView(childSummary)
        action(channels, "＋ 添加 YouTube 频道", "输入链接或 @handle，审核后加入", true) {
            addChannelFromLink()
        }
        action(channels, "管理已批准频道", "查看名称或删除频道") {
            manageApprovedChannels()
        }
        action(channels, "＋ 添加单个视频", "粘贴视频链接，单独批准") {
            addVideoFromLink()
        }
        action(channels, "管理已批准视频", "查看、删除已批准的视频") {
            manageApprovedVideos()
        }
        action(watching, "每日观看时长", "15～120 分钟，达到限制后停止播放", true) {
            pickDailyLimit()
        }
        searchTile = action(watching, "允许搜索", "仅在家长批准的范围内搜索") {
            store.searchAllowed = !store.searchAllowed
            refreshSummaries()
        }
        autoplayTile = action(watching, "自动播放下一集", "只播放下一条已批准视频") {
            store.autoplayNext = !store.autoplayNext
            refreshSummaries()
        }
        DenseSettingsUi.line(this, watching, "儿童审核", "仅展示允许嵌入的合规儿童视频")

        val advanced = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        expandButton(panel, "精确视频规则（高级）", advanced)
        panel.addView(advanced)
        action(advanced, "精确允许的视频 ID", "每行一条 11 位视频 ID") {
            editLines("允许的视频 ID（每行 11 位）", store.allowedVideosText) {
                store.allowedVideosText = it
            }
        }
        action(advanced, "禁止的视频 ID", "精准屏蔽特定视频") {
            editLines("禁止的视频 ID（每行 11 位）", store.blockedVideosText) {
                store.blockedVideosText = it
            }
        }
    }

    private fun buildNetworkSection(panel: LinearLayout) {
        DenseSettingsUi.intro(this, panel, "应用专用网络",
            "订阅、YAML、连接、节点与诊断全部在一页；不需要理解 Clash 规则。")
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(TvUi.dp(this@SettingsActivity, 13), TvUi.dp(this@SettingsActivity, 8),
                TvUi.dp(this@SettingsActivity, 13), TvUi.dp(this@SettingsActivity, 8))
            background = SettingsDecor.statusShape(this@SettingsActivity)
        }
        networkState = TvUi.label(this, "", if (handheld) 19f else 21f, true)
        networkDetail = TvUi.label(this, "", if (handheld) 13f else 14f).apply {
            setTextColor(TvUi.muted)
        }
        statusCard.addView(networkState)
        statusCard.addView(networkDetail)
        panel.addView(statusCard, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = TvUi.dp(this@SettingsActivity, 10)
        })

        val (subscription, connection) = DenseSettingsUi.twoPanes(this, panel,
            "订阅与配置", "三个常用功能：添加、导入、检测",
            "连接与节点", "默认自动选择，不需要手动规则")
        subscriptionTile = action(subscription, "添加在线订阅",
            "在本页粘贴、下载与校验 HTTPS 链接", true) {
            editSubscription()
        }
        inlineSubscriptionPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(TvUi.dp(this@SettingsActivity, 10), TvUi.dp(this@SettingsActivity, 8),
                TvUi.dp(this@SettingsActivity, 10), TvUi.dp(this@SettingsActivity, 10))
            background = SettingsDecor.statusShape(this@SettingsActivity)
        }
        val editHint = TvUi.label(this,
            "订阅可能包含访问令牌；编辑时启用系统防截图保护。", 13f)
            .apply { setTextColor(TvUi.muted) }
        inlineSubscriptionPanel.addView(editHint)
        inlineSubscriptionInput = TvUi.input(this,
            "https://服务商域名/api/v1/client/subscribe?token=…", "").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            minHeight = TvUi.dp(this@SettingsActivity, if (handheld) 49 else 53)
            contentDescription = "订阅地址（敏感，仅限家长）"
        }
        inlineSubscriptionPanel.addView(inlineSubscriptionInput,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = TvUi.dp(this@SettingsActivity, 6)
            })
        val editActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val paste = TvUi.button(this, "粘贴") { pasteSubscriptionFromClipboard() }.apply {
            textSize = if (handheld) 13f else 14f
        }
        editActions.addView(paste, LinearLayout.LayoutParams(0,
            TvUi.dp(this, if (handheld) 42 else 46), 0.92f))
        val copy = TvUi.button(this, "复制") { copySubscriptionToClipboard() }.apply {
            textSize = if (handheld) 13f else 14f
        }
        editActions.addView(copy, LinearLayout.LayoutParams(0,
            TvUi.dp(this, if (handheld) 42 else 46), 0.92f).apply {
            leftMargin = TvUi.dp(this@SettingsActivity, 4)
        })
        val clear = TvUi.button(this, "清空") {
            inlineSubscriptionInput.setText("")
            inlineSubscriptionInput.requestFocus()
        }.apply {
            textSize = if (handheld) 13f else 14f
        }
        editActions.addView(clear, LinearLayout.LayoutParams(0,
            TvUi.dp(this, if (handheld) 42 else 46), 0.92f).apply {
            leftMargin = TvUi.dp(this@SettingsActivity, 4)
        })
        val cancel = TvUi.button(this, "取消") { hideSubscriptionEditor() }.apply {
            textSize = if (handheld) 13f else 14f
        }
        editActions.addView(cancel, LinearLayout.LayoutParams(0,
            TvUi.dp(this, if (handheld) 42 else 46), 0.92f).apply {
            leftMargin = TvUi.dp(this@SettingsActivity, 4)
        })
        val save = TvUi.primaryButton(this, "保存") { saveInlineSubscription() }.apply {
            textSize = if (handheld) 13f else 14f
        }
        editActions.addView(save, LinearLayout.LayoutParams(0,
            TvUi.dp(this, if (handheld) 42 else 46), 1.2f).apply {
            leftMargin = TvUi.dp(this@SettingsActivity, 4)
        })
        inlineSubscriptionPanel.addView(editActions)
        subscription.addView(inlineSubscriptionPanel, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = TvUi.dp(this@SettingsActivity, 7)
        })
        yamlImportTile = action(subscription, "导入本地 YAML",
            "从 FlClash 导出 .yaml / .yml 后直接选择文件") {
            importYaml()
        }
        subscriptionRefreshTile = action(subscription, "重新下载订阅",
            "订阅失效或节点未更新时使用") {
            val url = store.subscriptionUrl
            if (url.isBlank()) editSubscription() else downloadSubscription(url)
        }
        action(subscription, "检测订阅是否可用（不保存）",
            "仅验证下载和配置格式，不修改现有连接") {
            testSubscriptionWithoutSaving()
        }
        action(subscription, "订阅自动更新周期",
            "默认每天一次，其他时间按需要调整") {
            chooseRefreshInterval()
        }

        vpnTile = action(connection, "连接应用网络",
            "首次连接需要 Android 系统 VPN 授权", true) {
            connectOrTestVpn()
        }
        DenseSettingsUi.line(this, connection, "节点策略", "自动选择（推荐）")
        action(connection, "节点测速与选择",
            "连接后可查看节点，异常时再手动切换") {
            startActivity(Intent(this, ProxyActivity::class.java))
        }
        action(connection, "一键网络诊断",
            "检查应用 VPN、Google HTTPS 与 API") {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
        DenseSettingsUi.line(this, connection, "应用网络保护", "仅代理 KidsTubeTV")
        DenseSettingsUi.line(this, connection, "其他应用", "继续使用设备原来的网络")

        networkAdvanced = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        expandButton(panel, "高级网络工具", networkAdvanced)
        panel.addView(networkAdvanced)
        action(networkAdvanced, "重启应用网络",
            "更换配置后重新建立本应用连接") { restartVpnFromParent() }
        action(networkAdvanced, "恢复上一次配置",
            "断开本应用 VPN 后可以恢复备份") {
            if (ProxyStatus.phase == ProxyStatus.Phase.READY ||
                ProxyStatus.phase == ProxyStatus.Phase.STARTING) {
                networkDetail.text = "请先断开应用网络，再恢复旧配置"
            } else if (!store.hasBackupProfile) {
                networkDetail.text = "没有可以恢复的上一版配置"
            } else {
                AlertDialog.Builder(this)
                    .setTitle("恢复上一次配置")
                    .setMessage("恢复此前保存的配置，下次连接时生效。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("恢复") { _, _ ->
                        val ok = store.restoreBackup()
                        networkState.text = if (ok) "✓ 配置已恢复" else "⚠ 恢复失败"
                        networkDetail.text = if (ok)
                            "请重新连接应用网络" else "无法恢复之前的配置"
                    }.show()
            }
        }
        action(networkAdvanced, "暂停应用网络",
            "手动暂停后不自动重连；其他应用不受影响") {
            VpnManualPause.pause()
            uiHandler.removeCallbacks(startVpnAfterStop)
            KidsVpnService.stop(this)
            networkState.text = "家长已暂停应用网络"
        }
        panel.addView(TvUi.label(this,
            "Android 同时通常只能启用一个 VPN。本应用不会路由其他 App 的流量。",
            12f).apply { setTextColor(TvUi.muted) })
    }

    private fun connectOrTestVpn() {
        if (!store.hasProfile) {
            editSubscription()
            return
        }
        when (ProxyStatus.phase) {
            ProxyStatus.Phase.READY -> {
                networkState.text = "正在检测 Google HTTPS…"
                ProxyNetworkHealth.check(this)
            }
            ProxyStatus.Phase.STARTING -> networkState.text = "VPN 正在连接，请稍后"
            else -> restartVpnFromParent()
        }
    }

    private fun restartVpnFromParent() {
        if (!store.hasProfile) {
            networkDetail.text = "请先添加订阅链接；下载失败可在更多设置中导入文件"
            return
        }
        VpnManualPause.resume()
        uiHandler.removeCallbacks(startVpnAfterStop)
        if (ProxyStatus.phase == ProxyStatus.Phase.READY ||
            ProxyStatus.phase == ProxyStatus.Phase.STARTING) {
            KidsVpnService.stop(this)
            uiHandler.postDelayed(startVpnAfterStop, 1_000)
        } else requestVpnFromParent()
    }

    private fun requestVpnFromParent() {
        if (!ParentSession.authorized() || !store.hasProfile) return
        val request = VpnService.prepare(this)
        if (request == null) KidsVpnService.start(this)
        else {
            ParentSession.allowExternalUi()
            try {
                startActivityForResult(request, REQUEST_VPN_PERMISSION)
            } catch (error: Exception) {
                ParentSession.endExternalUi()
                networkDetail.text = "无法打开 Android VPN 授权对话框"
            }
        }
    }

    private fun buildAccountSection(panel: LinearLayout) {
        DenseSettingsUi.intro(this, panel, "YouTube 账号与接口",
            "Google 电视授权与 API Key 二选一，无需孩子在电视上输入密码。")
        accountStats = DenseSettingsUi.stats(this, panel, listOf(
            "Google" to "未授权", "API Key" to "未配置",
            "允许频道" to "0 个", "网络" to "未连接"
        ))
        val (google, api) = DenseSettingsUi.twoPanes(this, panel,
            "Google 授权", "二维码 / 设备码登录",
            "使用 API Key", "快速浏览与 API 调试")
        accountSummary = TvUi.label(this, "", 15f).apply { setTextColor(TvUi.muted) }
        google.addView(accountSummary)
        action(google, "Google 授权 / 导入订阅频道",
            "手机浏览器授权后选择可看的频道", true) {
            startActivity(Intent(this, GoogleAccountActivity::class.java))
        }
        action(google, "管理儿童频道白名单", "导入后仍由家长批准") {
            showSection(SettingsDestination.CONTENT.ordinal, moveFocus = !handheld)
        }
        DenseSettingsUi.line(this, google, "账号权限", "只读 YouTube 资料")
        action(api, "设置或更换 YouTube API Key",
            "无需登录 Google；用于查询视频信息", true) {
            editValue("YouTube Data API v3 Key", "", secret = true,
                hint = "粘贴自己的 API Key；留空不更改") {
                if (it.isNotBlank()) store.apiKey = it
            }
        }
        action(api, "添加 YouTube 频道", "粘贴频道链接或 @handle") {
            addChannelFromLink()
        }
        DenseSettingsUi.line(this, api, "内容审核", "无论使用哪种授权都需白名单")
    }

    private fun advanceSetup() {
        when (SetupFlowPolicy.next(
            store.hasPin, store.hasProfile,
            ProxyStatus.phase == ProxyStatus.Phase.READY,
            store.hasYouTubeAuth,
            store.allowedChannels.isNotEmpty() || store.approvedVideoIds.isNotEmpty()
        )) {
            SetupFlowPolicy.Step.PIN -> setPinDialog(initial = true)
            SetupFlowPolicy.Step.PROFILE -> { showSection(1); editSubscription() }
            SetupFlowPolicy.Step.VPN -> { showSection(1); connectOrTestVpn() }
            SetupFlowPolicy.Step.AUTH -> showSection(2)
            SetupFlowPolicy.Step.APPROVAL -> { showSection(SettingsDestination.CONTENT.ordinal); addChannelFromLink() }
            SetupFlowPolicy.Step.COMPLETE -> finish()
        }
    }

    private fun refreshSummaries() {
        val step = SetupFlowPolicy.next(
            store.hasPin, store.hasProfile,
            ProxyStatus.phase == ProxyStatus.Phase.READY,
            store.hasYouTubeAuth,
            store.allowedChannels.isNotEmpty() || store.approvedVideoIds.isNotEmpty()
        )
        setupTile.setTitle(when (step) {
            SetupFlowPolicy.Step.PIN -> "1 / 5  设置家长 PIN"
            SetupFlowPolicy.Step.PROFILE -> "2 / 5  添加网络订阅"
            SetupFlowPolicy.Step.VPN -> "3 / 5  连接应用网络"
            SetupFlowPolicy.Step.AUTH -> "4 / 5  配置 YouTube"
            SetupFlowPolicy.Step.APPROVAL -> "5 / 5  添加允许的频道"
            SetupFlowPolicy.Step.COMPLETE -> "✓ 配置完成 · 返回儿童首页"
        })
        setupTile.setSubtitle(when (step) {
            SetupFlowPolicy.Step.PIN -> "创建家长密码，保护敏感设置"
            SetupFlowPolicy.Step.PROFILE -> "复制 HTTPS 订阅链接后轻触这里"
            SetupFlowPolicy.Step.VPN -> "仅 KidsTubeTV 使用 VPN，其他应用仍使用原网络"
            SetupFlowPolicy.Step.AUTH -> "Google 手机授权或直接输入 API Key"
            SetupFlowPolicy.Step.APPROVAL -> "粘贴频道链接，只允许儿童视频播放"
            SetupFlowPolicy.Step.COMPLETE -> "网络、内容审核和家长设置已齐备"
        })
        summary.text = "已批准 " + store.allowedChannels.size + " 个频道  ·  今日已看 " +
            (WatchTime(this).elapsedSeconds() / 60) + " 分钟"
        overviewNetwork.setTitle("应用网络  ·  " + when (ProxyStatus.phase) {
            ProxyStatus.Phase.READY -> "已连接"
            ProxyStatus.Phase.STARTING -> "连接中"
            ProxyStatus.Phase.ERROR -> "连接异常"
            ProxyStatus.Phase.OFF -> "未连接"
        })
        overviewNetwork.setSubtitle(if (store.hasProfile) "仅本应用代理 · 已保存配置"
            else "还没有网络订阅，点击添加")
        overviewYoutube.setSubtitle(if (GoogleTvAuth(this).isLinked) "Google 已授权"
            else if (store.apiKey.isNotBlank()) "已配置 YouTube API Key"
            else "未配置；可扫码授权或使用 API Key")
        overviewContent.setSubtitle(store.allowedChannels.size.toString() + " 个频道 · " +
            store.approvedVideoIds.size + " 个视频 · 每天 " + store.dailyLimitMinutes + " 分钟")
        overviewParent.setSubtitle(if (store.hasPin) "家长 PIN 已设置 · 可管理设备数据"
            else "需要创建家长 PIN")
        parentSummary.text = if (store.hasPin) "● PIN 已启用，家长会话有时间限制"
            else "○ 尚未设置家长 PIN"
        childSummary.text = "已批准 " + store.allowedChannels.size + " 个频道 · " +
            store.approvedVideoIds.size + " 个视频 · 每日 " + store.dailyLimitMinutes + " 分钟"
        searchTile.setTitle("允许搜索  " + if (store.searchAllowed) "● 已开启" else "○ 已关闭")
        autoplayTile.setTitle("自动播放下一集  " + if (store.autoplayNext) "● 已开启" else "○ 已关闭")
        val state = when (ProxyStatus.phase) {
            ProxyStatus.Phase.OFF -> "○ 未连接"
            ProxyStatus.Phase.STARTING -> "◌ 正在连接…"
            ProxyStatus.Phase.ERROR -> "⚠ 连接异常"
            ProxyStatus.Phase.READY -> when (ProxyNetworkHealth.state) {
                ProxyNetworkHealth.State.ONLINE -> "● Google 网络可用"
                ProxyNetworkHealth.State.UNREACHABLE -> "⚠ Google 探测失败（不等于所有 API 不可达）"
                ProxyNetworkHealth.State.UNKNOWN -> "◌ VPN 已启动，网络检测中"
            }
        }
        networkState.text = state
        networkState.setTextColor(if (ProxyStatus.phase == ProxyStatus.Phase.READY &&
            ProxyNetworkHealth.state != ProxyNetworkHealth.State.UNREACHABLE) TvUi.mint else TvUi.warm)
        val host = runCatching { URI(store.subscriptionUrl).host }.getOrNull()
        val source = when {
            store.hasProfile && host != null -> "订阅来源：" + host
            store.hasProfile -> "已导入本地网络配置"
            else -> "尚未添加订阅；完成后可自动连接应用网络"
        }
        val time = if (store.subscriptionUpdatedAt > 0) {
            " · 更新于 " + DateFormat.getDateTimeInstance().format(Date(store.subscriptionUpdatedAt))
        } else ""
        val subscriptionIssue = store.subscriptionUpdateIssue
        networkDetail.text = source + time +
            (if (subscriptionIssue.isNotBlank()) "\n⚠ 订阅上次失败：" + subscriptionIssue else "") +
            when {
                ProxyStatus.phase == ProxyStatus.Phase.ERROR ->
                    "\n" + ProxyStatus.detail
                else -> ""
            }
        subscriptionRefreshTile.visibility =
            if (store.subscriptionUrl.isBlank()) View.GONE else View.VISIBLE
        subscriptionTile.setTitle(if (store.subscriptionUrl.isBlank())
            "添加网络订阅" else "更换网络订阅")
        subscriptionTile.setSubtitle(if (store.subscriptionUrl.isBlank())
            "粘贴 HTTPS 订阅链接，保存后自动下载" else "当前订阅已保存，可查看、复制或更换")
        vpnTile.setTitle(when (ProxyStatus.phase) {
            ProxyStatus.Phase.READY -> "检测应用网络"
            ProxyStatus.Phase.STARTING -> "正在连接应用网络…"
            else -> if (store.hasProfile) "连接应用网络" else "先添加订阅"
        })
        vpnTile.setSubtitle(when (ProxyStatus.phase) {
            ProxyStatus.Phase.READY -> "仅 KidsTubeTV 走 VPN，点击检测连通性"
            ProxyStatus.Phase.STARTING -> "正在准备应用专属网络，不影响其他应用路由"
            else -> if (store.hasProfile) "仅 KidsTubeTV 使用 VPN，点击连接" else "点击后直接填写订阅链接"
        })
        accountSummary.text = if (GoogleTvAuth(this).isLinked) "Google 已授权（只读 YouTube 资料）"
            else "未登录 Google；也可使用 YouTube API Key 浏览"
        // The overview and category metrics stay in sync after each action.
        overviewStats.getOrNull(0)?.text = when (ProxyStatus.phase) {
            ProxyStatus.Phase.READY -> "已连接"
            ProxyStatus.Phase.STARTING -> "连接中"
            ProxyStatus.Phase.ERROR -> "连接异常"
            else -> "未连接"
        }
        overviewStats.getOrNull(1)?.text = "${store.allowedChannels.size} 个"
        overviewStats.getOrNull(2)?.text = "${WatchTime(this).elapsedSeconds() / 60} 分钟"
        overviewStats.getOrNull(3)?.text = "${store.dailyLimitMinutes} 分钟"
        contentStats.getOrNull(0)?.text = "${store.allowedChannels.size} 个"
        contentStats.getOrNull(1)?.text = "${store.approvedVideoIds.size} 个"
        contentStats.getOrNull(2)?.text = "${store.dailyLimitMinutes} 分钟"
        contentStats.getOrNull(3)?.text = if (store.autoplayNext) "开启" else "关闭"
        accountStats.getOrNull(0)?.text = if (GoogleTvAuth(this).isLinked) "已授权" else "未授权"
        accountStats.getOrNull(1)?.text = if (store.apiKey.isNotBlank()) "已配置" else "未配置"
        accountStats.getOrNull(2)?.text = "${store.allowedChannels.size} 个"
        accountStats.getOrNull(3)?.text = if (ProxyStatus.phase == ProxyStatus.Phase.READY)
            "已连接" else "未连接"
    }

    /** One input + one confirmation, instead of typing a 24-character channel ID. */
    private fun addChannelFromLink() {
        val editor = EditText(this).apply {
            hint = "https://youtube.com/@频道名 或 @handle"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("添加家长允许的频道")
            .setMessage("粘贴频道链接或 @handle，查询后由家长确认加入。")
            .setView(editor)
            .setNegativeButton("取消", null)
            .setPositiveButton("查询频道", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val reference = runCatching {
                    ChannelReference.parse(editor.text.toString())
                }.getOrElse { editor.error = it.message; return@setOnClickListener }
                if (ProxyStatus.phase != ProxyStatus.Phase.READY) {
                    editor.error = "请先从儿童首页建立 VPN 连接"
                    return@setOnClickListener
                }
                dialog.dismiss()
                childSummary.text = "正在确认频道信息…"
                Thread({
                    val result = runCatching { YouTubeRepository(store).resolveChannel(reference) }
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        result.onSuccess { confirmChannel(it) }
                            .onFailure {
                                childSummary.text = "查询频道失败，请检查地址和网络 / YouTube API 配额"
                            }
                    }
                }, "kids-parent-channel-resolve").start()
            }
        }
        dialog.show()
    }

    private fun confirmChannel(channel: YouTubeChannelInfo) {
        AlertDialog.Builder(this).setTitle("允许此频道？")
            .setMessage("频道：${channel.title}\n\n只有由 YouTube 标记为儿童内容、允许嵌入且未被家长屏蔽的视频可以播放。")
            .setNegativeButton("取消", null)
            .setPositiveButton("加入白名单") { _, _ ->
                store.channelsText = (store.allowedChannels + channel.id).joinToString("\n")
                store.rememberChannelName(channel.id, channel.title)
                refreshSummaries()
                childSummary.text = "已加入频道：${channel.title}"
            }.show()
    }

    private fun addVideoFromLink() {
        val editor = EditText(this).apply {
            hint = "https://youtu.be/… 或 YouTube 视频 URL"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("添加允许的视频")
            .setMessage("粘贴分享链接；视频必须可嵌入且标记为儿童内容。")
            .setView(editor)
            .setNegativeButton("取消", null)
            .setPositiveButton("检查视频", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val id = runCatching {
                    YouTubeVideoReference.parse(editor.text.toString())
                }.getOrElse { editor.error = it.message; return@setOnClickListener }
                if (ProxyStatus.phase != ProxyStatus.Phase.READY) {
                    editor.error = "请先连接 VPN 再检查视频信息"
                    return@setOnClickListener
                }
                dialog.dismiss()
                childSummary.text = "正在检查视频的儿童标记与播放限制…"
                Thread({
                    val result = runCatching { YouTubeRepository(store).inspectVideoForParent(id) }
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        result.onSuccess { confirmVideo(it) }
                            .onFailure { childSummary.text =
                                "未能确认视频适合儿童，不能添加（检查网络、API Key、视频权限）" }
                    }
                }, "kids-parent-video-inspect").start()
            }
        }
        dialog.show()
    }

    private fun confirmVideo(video: KidVideo) {
        AlertDialog.Builder(this).setTitle("批准这条视频？")
            .setMessage("${video.title}\n频道：${video.channelTitle}\n\n添加后孩子可以从家长精选直接播放。")
            .setNegativeButton("取消", null)
            .setPositiveButton("允许播放") { _, _ ->
                val updated = store.approvedVideoIds + video.id
                store.allowedVideosText = updated.joinToString("\n")
                store.rememberVideoName(video.id, video.title)
                refreshSummaries()
                childSummary.text = "已批准视频：${video.title}"
            }.show()
    }

    private fun manageApprovedVideos() {
        val ids = store.approvedVideoIds.sorted()
        if (ids.isEmpty()) {
            childSummary.text = "暂无单独批准的视频，可使用分享链接添加"
            return
        }
        val selected = BooleanArray(ids.size) { true }
        val titles = store.videoNames
        val labels = ids.mapIndexed { index, id ->
            titles[id]?.let { it + "  ·  " + id } ?: "视频 ${index + 1}  ·  " + id
        }
        AlertDialog.Builder(this)
            .setTitle("勾选要保留的视频")
            .setMultiChoiceItems(labels.toTypedArray(), selected) { _, index, checked ->
                selected[index] = checked
            }
            .setNegativeButton("取消", null)
            .setPositiveButton("保存视频白名单") { _, _ ->
                val kept = ids.filterIndexed { index, _ -> selected[index] }
                store.allowedVideosText = kept.joinToString("\n")
                store.forgetVideoNames(ids.filterNot { it in kept })
                refreshSummaries()
            }.show()
    }

    private fun manageApprovedChannels() {
        val channels = store.allowedChannels.sorted()
        if (channels.isEmpty()) {
            childSummary.text = "暂无批准频道。可通过 @handle 或频道链接添加。"
            return
        }
        val enabled = BooleanArray(channels.size) { true }
        val names = store.channelNames
        val labels = channels.mapIndexed { index, id ->
            names[id]?.let { it + "  ·  " + id } ?: "频道 ${index + 1}  ·  " + id
        }
        AlertDialog.Builder(this)
            .setTitle("勾选保留的频道")
            .setMultiChoiceItems(labels.toTypedArray(), enabled) { _, index, checked ->
                enabled[index] = checked
            }
            .setNegativeButton("取消", null)
            .setPositiveButton("保存白名单") { _, _ ->
                store.channelsText = channels.filterIndexed { i, _ -> enabled[i] }.joinToString("\n")
                refreshSummaries()
            }.show()
    }

    /** V2: edit directly on the network page. Screenshot protection remains on
     * while the sensitive subscription token is visible. */
    private fun editSubscription() {
        if (!ParentSession.authorized()) { finish(); return }
        if (selectedSection != SettingsDestination.NETWORK.ordinal) {
            showSection(SettingsDestination.NETWORK.ordinal, moveFocus = !handheld)
        }
        inlineSubscriptionOpen = true
        inlineSubscriptionInput.setText(store.subscriptionUrl)
        inlineSubscriptionInput.setSelection(inlineSubscriptionInput.text.length)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        inlineSubscriptionPanel.visibility = View.VISIBLE
        if (handheld) inlineSubscriptionInput.requestFocus()
        wireTvFocus(moveFocus = !handheld)
        scroller.post { scroller.smoothScrollTo(0, inlineSubscriptionPanel.top) }
    }

    private fun hideSubscriptionEditor() {
        inlineSubscriptionOpen = false
        inlineSubscriptionInput.setText("")
        inlineSubscriptionInput.error = null
        inlineSubscriptionPanel.visibility = View.GONE
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        wireTvFocus()
    }

    private fun pasteSubscriptionFromClipboard() {
        if (!ParentSession.authorized()) return
        val raw = runCatching {
            val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            manager.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        }.getOrDefault("")
        val candidate = dev.kidstubetv.data.ClipboardSubscriptionPolicy.candidate(
            raw, inlineSubscriptionInput.text.toString()
        )
        if (candidate == null) {
            inlineSubscriptionInput.error = "剪贴板中没有有效 HTTPS 订阅链接"
        } else {
            inlineSubscriptionInput.setText(candidate)
            inlineSubscriptionInput.setSelection(inlineSubscriptionInput.text.length)
        }
    }

    private fun copySubscriptionToClipboard() {
        if (!ParentSession.authorized()) return
        val value = inlineSubscriptionInput.text.toString().trim()
        if (value.isBlank()) {
            inlineSubscriptionInput.error = "当前没有可复制的链接"
            return
        }
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("KidsTubeTV subscription", value)
        if (Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        manager.setPrimaryClip(clip)
    }

    private fun saveInlineSubscription() {
        if (!ParentSession.authorized()) { hideSubscriptionEditor(); finish(); return }
        val url = runCatching {
            SubscriptionFormat.normalizeUrl(inlineSubscriptionInput.text.toString())
        }.getOrElse {
            inlineSubscriptionInput.error = "请输入完整 HTTPS 订阅链接"
            inlineSubscriptionInput.requestFocus()
            return
        }
        val changed = store.subscriptionUrl != url
        runCatching {
            store.subscriptionUrl = url
            if (changed) store.subscriptionUpdatedAt = 0L
        }.onFailure {
            inlineSubscriptionInput.error = "订阅安全存储失败"
            return
        }
        hideSubscriptionEditor()
        refreshSummaries()
        downloadSubscription(url, providerChanged = changed)
    }

    /** Same downloader as normal updates; no URL, token or YAML in UI output. */
    private fun testSubscriptionWithoutSaving() {
        if (!ParentSession.authorized()) return
        val url = store.subscriptionUrl
        if (url.isBlank()) {
            networkDetail.text = "请先添加并保存 HTTPS 订阅地址"
            return
        }
        if (subscriptionProbeRunning) {
            networkDetail.text = "订阅检测进行中，请勿重复点击"
            return
        }
        subscriptionProbeRunning = true
        val request = subscriptionProbeRequests.begin()
        val vpnReady = ProxyStatus.phase == ProxyStatus.Phase.READY
        networkState.text = "◌ 正在检测订阅…"
        networkDetail.text = "仅检测 HTTPS 下载与 Clash YAML 格式，不会改动已有配置"
        Thread({
            val result = SubscriptionProbe().check(url, vpnReady)
            runOnUiThread {
                if (isFinishing || isDestroyed || !screenStarted ||
                    !ParentSession.authorized() || store.subscriptionUrl != url ||
                    !subscriptionProbeRequests.isCurrent(request)) return@runOnUiThread
                subscriptionProbeRunning = false
                networkState.text = if (result.successful)
                    "✓ 订阅接口与 YAML 校验通过" else "⚠ 订阅检测失败"
                networkDetail.text = result.message
            }
        }, "kids-subscription-probe").start()
    }

    private fun downloadSubscription(url: String, providerChanged: Boolean = false) {
        // Do not stop an active VPN during download; some provider domains are
        // reachable only through the selected proxy. Apply config next session.
        if (!dev.kidstubetv.proxy.SubscriptionUpdateGate.tryAcquire()) {
            networkState.text = "◌ 订阅已经在更新"
            networkDetail.text = "请在当前任务完成后再重试"
            return
        }
        networkState.text = "◌ 正在下载并校验 Clash 订阅…"
        networkDetail.text = "下载失败不会覆盖原有配置"
        val vpnReadyAtStart = ProxyStatus.phase == ProxyStatus.Phase.READY
        val subscriptionRevision = store.subscriptionRevision()
        Thread({
            val result = try {
                runCatching {
                    val yaml = SubscriptionClient().fetch(url)
                    check(store.installFetchedSubscription(
                        url, subscriptionRevision, yaml, clearSelections = providerChanged
                    )) { "订阅已被新的配置替换，本次下载结果已作废" }
                }.onFailure { failure ->
                    // Never stamp an old request's error over a newer profile.
                    store.recordSubscriptionFailureIfCurrent(
                        url, subscriptionRevision, failure, vpnReadyAtStart
                    )
                }
            } finally {
                dev.kidstubetv.proxy.SubscriptionUpdateGate.release()
            }
            runOnUiThread {
                if (isFinishing || isDestroyed ||
                    store.subscriptionUrl != url) return@runOnUiThread
                refreshSummaries()
                // The background download may finish after the parent left
                // Settings. Never launch a VPN consent dialog over another app.
                if (!screenStarted || !ParentSession.authorized()) return@runOnUiThread
                result.onSuccess {
                    networkState.text = "✓ 订阅下载并校验成功"
                    networkDetail.text = "配置已保存，可在下次连接 VPN 时生效"
                    if (ParentSession.authorized() &&
                        ProxyStatus.phase == ProxyStatus.Phase.READY) {
                        AlertDialog.Builder(this)
                            .setTitle("使用更新后的配置？")
                            .setMessage("新订阅已下载成功，重新连接后生效。")
                            .setNegativeButton("稍后", null)
                            .setPositiveButton("立即连接") { _, _ -> restartVpnFromParent() }
                            .show()
                    } else if (ParentSession.authorized() &&
                        !VpnManualPause.isPaused &&
                        ProxyStatus.phase != ProxyStatus.Phase.STARTING) {
                        // First-time configuration: skip a second remote click.
                        requestVpnFromParent()
                    }
                }.onFailure {
                    networkState.text = "⚠ 订阅更新失败"
                    networkDetail.text = store.subscriptionUpdateIssue +
                        if (store.hasProfile) "\n已保留之前可用的配置"
                        else "\n尚无有效 YAML，可在更多网络设置中导入本地配置"
                }
            }
        }, "kids-subscription-manual").start()
    }

    private fun editLines(title: String, current: String, save: (String) -> Unit) {
        editValue(title, current, multiline = true, save = save)
    }

    private fun editValue(
        title: String,
        current: String,
        secret: Boolean = false,
        multiline: Boolean = false,
        hint: String = "请输入内容",
        save: (String) -> Unit,
    ) {
        val editor = EditText(this).apply {
            setText(current)
            this.hint = hint
            setTextColor(TvUi.text)
            setHintTextColor(TvUi.muted)
            setPadding(TvUi.dp(this@SettingsActivity, 20), TvUi.dp(this@SettingsActivity, 15),
                TvUi.dp(this@SettingsActivity, 20), TvUi.dp(this@SettingsActivity, 15))
            inputType = when {
                multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                secret -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            }
            if (multiline) {
                minLines = 4
                maxLines = 8
            } else setSingleLine(true)
        }
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(editor)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                runCatching { save(editor.text.toString().trim()) }
                    .onSuccess {
                        dialog.dismiss()
                        refreshSummaries()
                    }.onFailure {
                        editor.error = it.message ?: "保存失败"
                    }
            }
        }
        dialog.show()
    }

    private fun pickDailyLimit() {
        val minutes = intArrayOf(15, 20, 30, 45, 60, 90, 120)
        AlertDialog.Builder(this).setTitle("每天可以看多久？")
            .setSingleChoiceItems(minutes.map { "$it 分钟" }.toTypedArray(),
                minutes.indexOf(store.dailyLimitMinutes).coerceAtLeast(0)) { dialog, position ->
                store.dailyLimitMinutes = minutes[position]
                refreshSummaries()
                dialog.dismiss()
            }.setNegativeButton("取消", null).show()
    }

    private fun chooseRefreshInterval() {
        val hours = intArrayOf(0, 6, 24, 72, 168)
        val labels = arrayOf("不自动更新", "每 6 小时", "每天一次（推荐）", "每 3 天", "每 7 天")
        AlertDialog.Builder(this).setTitle("Clash 订阅更新")
            .setSingleChoiceItems(labels, hours.indexOf(store.subscriptionIntervalHours).coerceAtLeast(0)) {
                    dialog, index ->
                store.subscriptionIntervalHours = hours[index]
                dialog.dismiss()
                networkDetail.text = "自动更新周期已保存，下次连接时检查订阅。"
            }.setNegativeButton("取消", null).show()
    }

    private fun importYaml() {
        // Android's document picker runs in another process. Preserve the
        // parent's recent PIN permission only for this bounded transition.
        ParentSession.allowExternalUi()
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }, REQUEST_YAML)
        } catch (error: Exception) {
            ParentSession.endExternalUi()
            networkDetail.text = "无法打开系统文件选择器"
        }
    }

    @Deprecated("Legacy storage document picker callback for Android TV 9")
    override fun onActivityResult(request: Int, code: Int, data: Intent?) {
        super.onActivityResult(request, code, data)
        if (request == REQUEST_SUBSCRIPTION) {
            if (code == RESULT_OK && ParentSession.authorized()) {
                val url = store.subscriptionUrl
                if (url.isNotBlank()) {
                    showSection(SettingsDestination.NETWORK.ordinal)
                    downloadSubscription(url, providerChanged = data?.getBooleanExtra(
                        SubscriptionEditorActivity.EXTRA_PROVIDER_CHANGED, false
                    ) == true)
                }
            }
            return
        }
        if (request == REQUEST_VPN_PERMISSION) {
            val permitted = ParentSession.externalUiAuthorized()
            ParentSession.endExternalUi()
            if (code == RESULT_OK && permitted) KidsVpnService.start(this)
            else networkDetail.text = "VPN 授权取消或家长会话已到期，请重新解锁后连接"
            return
        }
        if (request != REQUEST_YAML) return
        val permitted = ParentSession.authorized()
        ParentSession.endExternalUi()
        if (!permitted || code != RESULT_OK) return
        val uri = data?.data ?: return
        if (!dev.kidstubetv.proxy.SubscriptionUpdateGate.tryAcquire()) {
            networkState.text = "已有订阅更新正在运行，请稍后导入"
            return
        }
        networkState.text = "◌ 正在导入…"
        Thread({
            val result = try {
                runCatching {
                    contentResolver.openInputStream(uri).use { stream ->
                    requireNotNull(stream) { "无法读取配置文件" }
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = stream.read(buffer)
                        if (n == -1) break
                        require(output.size() + n <= SubscriptionFormat.MAX_PROFILE_BYTES) {
                            "配置文件超过 1 MB"
                        }
                        output.write(buffer, 0, n)
                    }
                        store.writeProfile(output.toString(Charsets.UTF_8.name()))
                    }
                    store.subscriptionUpdatedAt = System.currentTimeMillis()
                    // Imported local YAML supersedes any previously scheduled URL refresh.
                    store.subscriptionUrl = ""
                    store.clearProxySelections()
                }
            } finally {
                dev.kidstubetv.proxy.SubscriptionUpdateGate.release()
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                refreshSummaries()
                result.onSuccess {
                    networkState.text = "✓ 本地配置已导入"
                    networkDetail.text = "下次启动 VPN 时生效"
                }.onFailure { error ->
                    networkState.text = "⚠ YAML 导入失败"
                    networkDetail.text = SubscriptionFailurePolicy.describe(
                        error, ProxyStatus.phase == ProxyStatus.Phase.READY
                    )
                }
            }
        }, "kids-yaml-import").start()
    }

    private fun setPinDialog(initial: Boolean) {
        val editor = EditText(this).apply {
            hint = "请输入 4～12 位数字"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (initial) "设置家长 PIN" else "修改家长 PIN")
            .setView(editor)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消") { _, _ -> if (initial) finish() }
            .setCancelable(!initial)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                runCatching { store.setPin(editor.text.toString()) }
                    .onSuccess {
                        ParentSession.unlock()
                        dialog.dismiss()
                        refreshSummaries()
                    }.onFailure { editor.error = it.message }
            }
        }
        dialog.show()
    }

    companion object {
        const val EXTRA_SECTION = "open_section"
        private const val REQUEST_YAML = 918
        private const val REQUEST_VPN_PERMISSION = 919
        private const val REQUEST_SUBSCRIPTION = 920
        private const val SAVED_SECTION = "settings_section"
    }
}
