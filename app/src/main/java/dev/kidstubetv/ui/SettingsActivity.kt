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
 * Parent-only three-section TV hub.
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
    private lateinit var networkAdvanced: LinearLayout
    private lateinit var subscriptionTile: SettingsActionTile
    private lateinit var subscriptionRefreshTile: SettingsActionTile
    private lateinit var yamlImportTile: SettingsActionTile
    private val sections = mutableListOf<LinearLayout>()
    private val tabs = mutableListOf<Button>()
    private var selectedSection = 0
    private lateinit var pageTitle: TextView
    private lateinit var mobileBackButton: Button
    private lateinit var searchTile: SettingsActionTile
    private lateinit var autoplayTile: SettingsActionTile
    private lateinit var vpnTile: SettingsActionTile
    private val uiHandler = Handler(Looper.getMainLooper())
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

        // TV: persistent D-pad rail and focusable content pane.
        // Phone: a compact header and single-column overview/detail navigation.
        val root = LinearLayout(this).apply {
            orientation = if (handheld) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            setBackgroundColor(TvUi.bg)
        }
        setContentView(root)
        if (!handheld) {
            val rail = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(TvUi.dp(this@SettingsActivity, 18),
                    TvUi.dp(this@SettingsActivity, 24),
                    TvUi.dp(this@SettingsActivity, 14),
                    TvUi.dp(this@SettingsActivity, 20))
                setBackgroundColor(0xFF142A35.toInt())
            }
            root.addView(rail, LinearLayout.LayoutParams(TvUi.dp(this, 244), -1))
            rail.addView(TvUi.label(this, "✦ KidsTubeTV", 23f, true))
            rail.addView(TvUi.label(this, "家长中心", 15f).apply { setTextColor(TvUi.muted) })
            TvUi.space(rail, this, 23)
            SettingsDestination.entries.forEachIndexed { index, route ->
                val button = Button(this).apply {
                    id = View.generateViewId()
                    text = route.navigationTitle
                    isAllCaps = false
                    textSize = 18f
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    setPadding(TvUi.dp(this@SettingsActivity, 17), 0,
                        TvUi.dp(this@SettingsActivity, 10), 0)
                    setTextColor(TvUi.text)
                    background = SettingsDecor.navShape(this@SettingsActivity, index == 0, false)
                    setOnClickListener { showSection(index, moveFocus = true) }
                    setOnFocusChangeListener { _, focused ->
                        background = SettingsDecor.navShape(this@SettingsActivity,
                            selectedSection == index, focused)
                    }
                }
                tabs.add(button)
                rail.addView(button, LinearLayout.LayoutParams(-1, TvUi.dp(this, 65)).apply {
                    bottomMargin = TvUi.dp(this@SettingsActivity, 6)
                })
            }
            rail.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
            rail.addView(SettingsDecor.action(this, "← 返回儿童首页", "退出家长设置") { finish() },
                LinearLayout.LayoutParams(-1, TvUi.dp(this, 74)))
        }

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val horizontal = if (handheld) 15 else 28
            setPadding(TvUi.dp(this@SettingsActivity, horizontal),
                TvUi.dp(this@SettingsActivity, if (handheld) 8 else 16),
                TvUi.dp(this@SettingsActivity, horizontal), 0)
        }
        root.addView(right, if (handheld)
            LinearLayout.LayoutParams(-1, -1)
        else LinearLayout.LayoutParams(0, -1, 1f))
        if (handheld) {
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            mobileBackButton = TvUi.button(this, "‹ 返回") { onBackPressed() }.apply {
                textSize = 15f
            }
            header.addView(mobileBackButton, LinearLayout.LayoutParams(
                TvUi.dp(this, 84), TvUi.dp(this, 46)).apply {
                rightMargin = TvUi.dp(this@SettingsActivity, 8)
            })
            pageTitle = TvUi.label(this, "设置概览", 23f, true)
            header.addView(pageTitle, LinearLayout.LayoutParams(0, -2, 1f))
            right.addView(header)
        } else {
            pageTitle = TvUi.label(this, "设置概览", 28f, true)
            right.addView(pageTitle)
        }

        summary = TvUi.label(this, "", if (handheld) 13f else 15f).apply {
            setTextColor(TvUi.muted)
        }
        right.addView(summary)
        setupTile = SettingsDecor.action(this, "继续配置", "完成下一步初始化", true) {
            advanceSetup()
        }
        right.addView(setupTile, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = TvUi.dp(this@SettingsActivity, if (handheld) 6 else 12)
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
                0, TvUi.dp(this@SettingsActivity, 32))
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

    override fun onStart() {
        super.onStart()
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
        unregisterReceiver(statusReceiver)
        uiHandler.removeCallbacks(startVpnAfterStop)
        super.onStop()
    }

    private fun showSection(index: Int, moveFocus: Boolean = false) {
        val route = SettingsDestination.fromIndex(index)
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
            bottomMargin = TvUi.dp(this@SettingsActivity, 10)
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
        heading(panel, "一眼看懂当前状态", "只显示最重要的信息，点击卡片进入对应设置。")
        overviewNetwork = action(panel, "应用网络", "检查网络配置和连接状态", true) {
            showSection(SettingsDestination.NETWORK.ordinal, moveFocus = !handheld)
        }
        overviewYoutube = action(panel, "YouTube 账号", "Google 授权或使用 API Key") {
            showSection(SettingsDestination.YOUTUBE.ordinal, moveFocus = !handheld)
        }
        overviewContent = action(panel, "儿童内容", "频道白名单、观看限制与自动播放") {
            showSection(SettingsDestination.CONTENT.ordinal, moveFocus = !handheld)
        }
        overviewParent = action(panel, "家长与安全", "修改 PIN、清理观看记录与诊断") {
            showSection(SettingsDestination.PARENT.ordinal, moveFocus = !handheld)
        }
    }

    private fun buildParentSection(panel: LinearLayout) {
        heading(panel, "家长与安全", "敏感操作仅在家长 PIN 解锁后允许。")
        parentSummary = TvUi.label(this, "", if (handheld) 15f else 18f)
        panel.addView(parentSummary)
        TvUi.space(panel, this, 12)
        action(panel, "修改家长 PIN", "使用 4～12 位数字，验证后才能访问设置", true) {
            setPinDialog(initial = false)
        }
        action(panel, "清除历史与收藏", "删除本机观看历史和收藏，需要再次确认") {
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
        action(panel, "设备与网络诊断", "查看脱敏设备信息，不包含订阅令牌") {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
        panel.addView(TvUi.label(this,
            "为了安全，订阅令牌和 Google 凭据不会出现在普通诊断日志中。",
            if (handheld) 13f else 15f).apply { setTextColor(TvUi.muted) })
    }

    private fun buildChildSection(panel: LinearLayout) {
        heading(panel, "内容白名单", "默认只看家长批准的频道；分类与搜索仍经过儿童内容审核。")
        childSummary = TvUi.label(this, "", 18f)
        panel.addView(childSummary)
        TvUi.space(panel, this, 12)
        action(panel, "＋ 添加 YouTube 频道", "粘贴分享链接或 @handle，确认后加入白名单", true) {
            addChannelFromLink()
        }
        action(panel, "管理已批准频道", "查看频道名称，移除不想继续推荐的频道") {
            manageApprovedChannels()
        }
        action(panel, "每日观看时长", "15–120 分钟，达到上限后停止播放") { pickDailyLimit() }
        TvUi.space(panel, this, 9)
        val advanced = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        expandButton(panel, "更多儿童管理", advanced)
        panel.addView(advanced)
        searchTile = action(advanced, "允许搜索", "仅显示白名单与儿童审核通过的内容") {
            store.searchAllowed = !store.searchAllowed
            refreshSummaries()
        }
        autoplayTile = action(advanced, "自动播放下一集", "播放结束后可自动切换到下一条已批准视频") {
            store.autoplayNext = !store.autoplayNext
            refreshSummaries()
        }
        action(advanced, "＋ 添加单个视频", "粘贴视频链接，审核后单独允许") { addVideoFromLink() }
        action(advanced, "管理已批准视频", "查看名称与移除视频") { manageApprovedVideos() }
        action(advanced, "精确允许的视频 ID", "手动编辑，适合高级家长配置") {
            editLines("允许的视频 ID（每行 11 位）", store.allowedVideosText) {
                store.allowedVideosText = it
            }
        }
        action(advanced, "禁止的视频 ID", "手动禁止特定视频") {
            editLines("禁止的视频 ID（每行 11 位）", store.blockedVideosText) {
                store.blockedVideosText = it
            }
        }
    }

    private fun buildNetworkSection(panel: LinearLayout) {
        heading(panel, "仅本应用使用网络代理", "第一次添加订阅并连接，之后打开 KidsTubeTV 会自动连接。")
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(TvUi.dp(this@SettingsActivity, 18), TvUi.dp(this@SettingsActivity, 12),
                TvUi.dp(this@SettingsActivity, 18), TvUi.dp(this@SettingsActivity, 12))
            background = SettingsDecor.statusShape(this@SettingsActivity)
        }
        networkState = TvUi.label(this, "", 24f, true)
        networkDetail = TvUi.label(this, "", 15f).apply { setTextColor(TvUi.muted) }
        statusCard.addView(networkState)
        statusCard.addView(networkDetail)
        panel.addView(statusCard)
        TvUi.space(panel, this, 12)

        panel.addView(TvUi.label(this,
            "✓ 只代理 KidsTubeTV，其他应用继续使用原来的网络。" +
                "\nAndroid 同时通常只能启用一个 VPN，启动本应用可能断开其他 VPN。", 14f)
            .apply { setTextColor(TvUi.muted) })
        TvUi.space(panel, this, 12)

        vpnTile = action(panel, "连接应用网络", "首次连接需要同意系统 VPN 授权", true) {
            connectOrTestVpn()
        }
        subscriptionTile = action(panel, "添加或更换订阅", "粘贴订阅链接，自动下载配置") {
            editSubscription()
        }

        networkAdvanced = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        expandButton(panel, "更多设置与故障排查", networkAdvanced)
        panel.addView(networkAdvanced)

        subscriptionRefreshTile = action(networkAdvanced, "重新下载订阅",
            "订阅失效或节点列表未更新时使用") {
            val url = store.subscriptionUrl
            if (url.isBlank()) editSubscription() else downloadSubscription(url)
        }
        yamlImportTile = action(networkAdvanced, "从文件导入配置（备用）",
            "无法下载订阅时，可从手机下载目录或 U 盘导入 YAML") {
            importYaml()
        }
        action(networkAdvanced, "选择连接节点", "默认自动选择；只有连接异常时才需要更换") {
            startActivity(Intent(this, ProxyActivity::class.java))
        }
        action(networkAdvanced, "检测连接问题", "检查 Google 网络、YouTube 账号及诊断信息") {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
        action(networkAdvanced, "重启应用网络", "新配置会在重连后生效") {
            restartVpnFromParent()
        }
        action(networkAdvanced, "订阅更新周期", "默认自动更新，需要时再调整") { chooseRefreshInterval() }
        action(networkAdvanced, "恢复上一次配置", "恢复前先断开本应用 VPN") {
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
                        networkDetail.text = if (ok) "请重新连接应用网络" else "无法恢复之前的配置"
                    }.show()
            }
        }
        action(networkAdvanced, "暂停应用网络", "手动暂停后不自动重连；不会更改其他应用的网络") {
            VpnManualPause.pause()
            uiHandler.removeCallbacks(startVpnAfterStop)
            KidsVpnService.stop(this)
            networkState.text = "家长已暂停应用网络"
        }
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
        heading(panel, "YouTube 账号", "无需孩子在电视上输入 Google 密码。授权与白名单只允许家长操作。")
        accountSummary = TvUi.label(this, "", 18f)
        panel.addView(accountSummary)
        TvUi.space(panel, this, 16)
        action(panel, "Google 授权 / 导入订阅频道", "扫码或在手机浏览器授权，再挑选允许的频道", true) {
            startActivity(Intent(this, GoogleAccountActivity::class.java))
        }
        action(panel, "只用 YouTube API Key", "无需登录 Google；适合手机上快速验证视频列表") {
            editValue("YouTube Data API v3 Key", "", secret = true,
                hint = "粘贴自己的 API Key；留空不更改") {
                if (it.isNotBlank()) store.apiKey = it
            }
        }
        panel.addView(TvUi.label(this,
            "Google 授权和 API Key 二选一；添加频道后才会出现儿童视频。", 15f)
            .apply { setTextColor(TvUi.muted) })
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

    /** Dedicated, privacy-protected editor with touch and D-pad layouts. */
    private fun editSubscription() {
        if (!ParentSession.authorized()) { finish(); return }
        startActivityForResult(
            Intent(this, SubscriptionEditorActivity::class.java), REQUEST_SUBSCRIPTION
        )
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
                }.onFailure {
                    networkState.text = "⚠ 导入失败"
                    networkDetail.text = "文件无法读取，或不是有效的 Clash YAML 配置"
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
