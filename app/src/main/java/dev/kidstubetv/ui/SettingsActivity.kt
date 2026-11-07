package dev.kidstubetv.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ClipboardManager
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
import dev.kidstubetv.data.ClipboardSubscriptionPolicy
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
    private lateinit var scroller: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var summary: TextView
    private lateinit var networkState: TextView
    private lateinit var networkDetail: TextView
    private lateinit var childSummary: TextView
    private lateinit var accountSummary: TextView
    private lateinit var networkAdvanced: LinearLayout
    private val sections = mutableListOf<LinearLayout>()
    private val tabs = mutableListOf<Button>()
    private var selectedSection = 0
    private lateinit var pageTitle: TextView
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

        // Fixed rail + independent content scroller: navigation never disappears
        // when a long configuration section is scrolled with a TV remote.
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(TvUi.bg)
        }
        setContentView(root)
        val rail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(TvUi.dp(this@SettingsActivity, 20), TvUi.dp(this@SettingsActivity, 28),
                TvUi.dp(this@SettingsActivity, 18), TvUi.dp(this@SettingsActivity, 20))
            setBackgroundColor(0xFF142A35.toInt())
        }
        root.addView(rail, LinearLayout.LayoutParams(TvUi.dp(this, 232), -1))

        rail.addView(TvUi.label(this, "✦ KidsTubeTV", 24f, true))
        rail.addView(TvUi.label(this, "家长中心", 16f).apply { setTextColor(TvUi.muted) })
        TvUi.space(rail, this, 34)

        listOf("01  儿童内容", "02  网络与 VPN", "03  YouTube 账号").forEachIndexed { index, title ->
            val button = Button(this).apply {
                id = View.generateViewId()
                text = title
                isAllCaps = false
                textSize = 18f
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setPadding(TvUi.dp(this@SettingsActivity, 18), 0,
                    TvUi.dp(this@SettingsActivity, 12), 0)
                setTextColor(TvUi.text)
                background = SettingsDecor.navShape(this@SettingsActivity, index == 0, false)
                setOnClickListener { showSection(index, moveFocus = true) }
                setOnFocusChangeListener { _, hasFocus ->
                    background = SettingsDecor.navShape(this@SettingsActivity,
                        selectedSection == index, hasFocus)
                }
            }
            tabs.add(button)
            rail.addView(button, LinearLayout.LayoutParams(-1, TvUi.dp(this, 72)).apply {
                bottomMargin = TvUi.dp(this@SettingsActivity, 11)
            })
        }
        rail.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        rail.addView(SettingsDecor.action(this, "← 返回儿童首页", "关闭家长设置") { finish() },
            LinearLayout.LayoutParams(-1, TvUi.dp(this, 78)))

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(TvUi.dp(this@SettingsActivity, 28), TvUi.dp(this@SettingsActivity, 17),
                TvUi.dp(this@SettingsActivity, 32), 0)
        }
        root.addView(right, LinearLayout.LayoutParams(0, -1, 1f))
        pageTitle = TvUi.label(this, "网络与 VPN", 28f, true)
        right.addView(pageTitle)
        summary = TvUi.label(this, "", 15f).apply { setTextColor(TvUi.muted) }
        right.addView(summary)
        TvUi.space(right, this, 8)
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

        repeat(3) {
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
            }
            sections.add(panel)
            content.addView(panel)
        }
        buildChildSection(sections[0])
        buildNetworkSection(sections[1])
        buildAccountSection(sections[2])
        showSection(intent.getIntExtra(EXTRA_SECTION, 1).coerceIn(0, 2), moveFocus = false)
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
        selectedSection = index
        pageTitle.text = listOf("儿童内容", "网络与 VPN", "YouTube 账号")[index]
        sections.forEachIndexed { i, panel ->
            panel.visibility = if (i == index) View.VISIBLE else View.GONE
        }
        tabs.forEachIndexed { i, button ->
            button.background = SettingsDecor.navShape(this, i == index, button.hasFocus())
        }
        scroller.scrollTo(0, 0)
        if (moveFocus) {
            val targets = ArrayList<View>()
            sections[index].addFocusables(targets, View.FOCUS_DOWN)
            targets.firstOrNull()?.let { target ->
                target.nextFocusLeftId = tabs[index].id
                target.requestFocus()
            }
        }
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
        }
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
        searchTile = action(panel, "允许搜索", "仅显示白名单与儿童审核通过的内容") {
            store.searchAllowed = !store.searchAllowed
            refreshSummaries()
        }
        autoplayTile = action(panel, "自动播放下一集", "播放结束后可自动切换到下一条已批准视频") {
            store.autoplayNext = !store.autoplayNext
            refreshSummaries()
        }
        TvUi.space(panel, this, 9)
        val advanced = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        expandButton(panel, "更多儿童管理", advanced)
        panel.addView(advanced)
        action(advanced, "YouTube Data API Key", "没有 Google 授权时可使用 API Key") {
            editValue("YouTube Data API v3 Key", store.apiKey, secret = true) { store.apiKey = it.trim() }
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
        action(advanced, "修改家长 PIN", "设置 4–12 位数字") { setPinDialog(initial = false) }
        action(advanced, "清除历史与收藏", "不可撤销，请确认") {
            AlertDialog.Builder(this)
                .setMessage("要清空本机的观看记录和收藏吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认清除") { _, _ ->
                    WatchHistory(this).clear()
                    summary.text = "观看历史与收藏已清除"
                }.show()
        }
    }

    private fun buildNetworkSection(panel: LinearLayout) {
        heading(panel, "VPN 与网络", "先连接网络，再浏览儿童视频。常用操作保留在当前屏幕。")
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
        TvUi.space(panel, this, 14)

        vpnTile = action(panel, "连接 VPN", "首次连接会出现 Android 系统 VPN 授权", true) {
            connectOrTestVpn()
        }
        action(panel, "＋ 添加或更换 Clash 订阅", "粘贴手机传来的 HTTPS YAML 链接，无需直接编辑配置") {
            editSubscription()
        }
        action(panel, "选择代理节点", "一键选择节点；通常可使用订阅默认策略") {
            startActivity(Intent(this, ProxyActivity::class.java))
        }
        action(panel, "手动重启 VPN", "仅在确实断线或换配置时重启；检测失败不会自动重连") {
            restartVpnFromParent()
        }
        action(panel, "网络故障诊断", "查看 DNS、VPN、系统信息和排查提示") {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
        TvUi.space(panel, this, 8)
        networkAdvanced = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        expandButton(panel, "更多网络设置", networkAdvanced)
        panel.addView(networkAdvanced)
        action(networkAdvanced, "立即更新订阅", "保留现有配置，更新成功后下次连接生效") {
            if (store.subscriptionUrl.isBlank()) editSubscription()
            else downloadSubscription(store.subscriptionUrl)
        }
        action(networkAdvanced, "从 U 盘导入 YAML", "适合电视无法打开订阅链接的情况") { importYaml() }
        action(networkAdvanced, "订阅自动更新周期", "关闭自动更新或设置更新间隔") { chooseRefreshInterval() }
        action(networkAdvanced, "恢复上一次代理配置", "当前连接断开后可恢复本机备份") {
            if (ProxyStatus.phase == ProxyStatus.Phase.READY ||
                ProxyStatus.phase == ProxyStatus.Phase.STARTING) {
                networkDetail.text = "请先关闭 VPN，再恢复之前的配置"
            } else if (!store.hasBackupProfile) {
                networkDetail.text = "没有可以恢复的上一版配置"
            } else {
                AlertDialog.Builder(this)
                    .setTitle("恢复上一版 Clash 配置")
                    .setMessage("将当前配置替换成之前备份的 YAML，之后需要重新连接 VPN。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("恢复") { _, _ ->
                        val ok = store.restoreBackup()
                        networkState.text = if (ok) "✓ 已恢复上一版代理配置" else "⚠ 恢复失败"
                        networkDetail.text = if (ok) "重新连接 VPN 以生效" else "文件恢复失败"
                    }.show()
            }
        }
        action(networkAdvanced, "断开 VPN", "手动暂停，不会在后台自动重新连接") {
            VpnManualPause.pause()
            uiHandler.removeCallbacks(startVpnAfterStop)
            KidsVpnService.stop(this)
            networkState.text = "家长已暂停 VPN"
        }
    }

    private fun connectOrTestVpn() {
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
            networkDetail.text = "请先添加 Clash 订阅或从 U 盘导入配置"
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
        action(panel, "手机扫码登录 Google", "使用设备验证码，并由家长挑选允许的订阅频道", true) {
            startActivity(Intent(this, GoogleAccountActivity::class.java))
        }
        panel.addView(TvUi.label(this,
            "不想登录也可以：在儿童内容的“更多管理”里填写 YouTube API Key。", 15f)
            .apply { setTextColor(TvUi.muted) })
    }

    private fun refreshSummaries() {
        summary.text = "儿童频道：" + store.allowedChannels.size + "   ·   今日已看 " +
            (WatchTime(this).elapsedSeconds() / 60) + " 分钟"
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
            store.hasProfile -> "已导入本地 Clash 配置"
            else -> "未配置代理；先添加 Clash 订阅或从 U 盘导入"
        }
        val time = if (store.subscriptionUpdatedAt > 0) {
            " · 更新于 " + DateFormat.getDateTimeInstance().format(Date(store.subscriptionUpdatedAt))
        } else ""
        networkDetail.text = source + time +
            when {
                ProxyStatus.phase == ProxyStatus.Phase.ERROR ->
                    "\n" + ProxyStatus.detail
                ProxyStatus.phase == ProxyStatus.Phase.READY &&
                    dev.kidstubetv.proxy.ProxyDnsStatus.underlyingCount == 0 ->
                    "\n未检测到物理网络 DNS，请打开网络诊断"
                else -> ""
            }
        vpnTile.setTitle(when (ProxyStatus.phase) {
            ProxyStatus.Phase.READY -> "检测 VPN 网络"
            ProxyStatus.Phase.STARTING -> "VPN 正在连接"
            else -> "连接 VPN"
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

    private fun editSubscription() {
        // Read clipboard ONLY from a parent-initiated click. Never render a
        // subscription token on a TV screen shared with children or guests.
        val clipboardText = runCatching {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        }.getOrDefault("")
        val candidate = ClipboardSubscriptionPolicy.candidate(clipboardText, store.subscriptionUrl)
        if (candidate == null) {
            manuallyEditSubscription()
            return
        }
        val provider = runCatching { URI(candidate).host }.getOrNull() ?: "订阅服务"
        AlertDialog.Builder(this)
            .setTitle("从剪贴板添加 Clash 订阅？")
            .setMessage("已发现来自 $provider 的 HTTPS 链接。为保护订阅密钥，不会显示完整地址。")
            .setPositiveButton("使用此订阅") { _, _ -> downloadSubscription(candidate, saveNewUrl = true) }
            .setNeutralButton("手动输入") { _, _ -> manuallyEditSubscription() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun manuallyEditSubscription() {
        editValue(
            "输入新的 Clash 订阅地址",
            "",
            secret = true,
            hint = "粘贴 HTTPS 订阅链接（不会显示密钥）",
        ) { input ->
            val url = SubscriptionFormat.normalizeUrl(input)
            downloadSubscription(url, saveNewUrl = true)
        }
    }

    private fun downloadSubscription(url: String, saveNewUrl: Boolean = false) {
        // Do not stop the active VPN: some providers can only be reached through it.
        if (!dev.kidstubetv.proxy.SubscriptionUpdateGate.tryAcquire()) {
            networkState.text = "当前正在更新订阅"
            networkDetail.text = "请稍后重试，避免同时写入代理文件"
            return
        }
        networkState.text = "◌ 正在更新订阅…"
        Thread({
            val result = runCatching {
                val yaml = SubscriptionClient().fetch(url)
                store.writeProfile(yaml)
                if (saveNewUrl) {
                    try {
                        // A new provider's group names may not match old selections.
                        if (store.subscriptionUrl != url) store.clearProxySelections()
                        store.subscriptionUrl = url
                    } catch (error: Exception) {
                        store.restoreBackup()
                        throw error
                    }
                }
                store.subscriptionUpdatedAt = System.currentTimeMillis()
            }
            dev.kidstubetv.proxy.SubscriptionUpdateGate.release()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                refreshSummaries()
                result.onSuccess {
                    networkState.text = "✓ 订阅已更新"
                    networkDetail.text = "下次 VPN 连接时生效；当前播放不受影响"
                }.onFailure {
                    networkState.text = "⚠ 订阅更新失败"
                    // No provider URL or subscription token in on-screen errors.
                    networkDetail.text = "旧配置保持不变，请检查订阅链接、网络及 YAML 格式"
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
    }
}
