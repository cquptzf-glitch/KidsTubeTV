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

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (::networkState.isInitialized) refreshSummaries()
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (store.hasPin && !ParentSession.authorized()) { finish(); return }
        scroller = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        content = TvUi.column(this)
        scroller.addView(content)
        setContentView(scroller)
        content.addView(TvUi.label(this, "家长中心", 30f, true))
        content.addView(TvUi.label(this, "孩子只需选视频，所有复杂设置都留在这里。", 16f))
        TvUi.space(content, this, 14)

        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("✦ 儿童内容", "◈ 网络代理", "◉ YouTube 账号").forEachIndexed { index, label ->
            val button = TvUi.button(this, label) { showSection(index) }
            tabs.add(button)
            nav.addView(button, LinearLayout.LayoutParams(0, TvUi.dp(this, 64), 1f).apply {
                rightMargin = TvUi.dp(this@SettingsActivity, 12)
            })
        }
        content.addView(nav)
        TvUi.space(content, this, 9)
        summary = TvUi.label(this, "", 15f).apply { setTextColor(TvUi.muted) }
        content.addView(summary)
        TvUi.space(content, this, 11)

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
        content.addView(TvUi.button(this, "返回儿童首页") { finish() })
        showSection(intent.getIntExtra(EXTRA_SECTION, 0).coerceIn(0, 2))
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
        super.onStop()
    }

    private fun showSection(index: Int) {
        selectedSection = index
        sections.forEachIndexed { i, panel -> panel.visibility = if (i == index) View.VISIBLE else View.GONE }
        tabs.forEachIndexed { i, button -> button.alpha = if (i == index) 1f else 0.67f }
        scroller.post { scroller.smoothScrollTo(0, 0) }
    }

    private fun heading(panel: LinearLayout, title: String, hint: String) {
        panel.addView(TvUi.label(this, title, 24f, true))
        panel.addView(TvUi.label(this, hint, 16f).apply { setTextColor(TvUi.muted) })
        TvUi.space(panel, this, 15)
    }

    private fun buildChildSection(panel: LinearLayout) {
        heading(panel, "儿童内容", "大封面浏览，只显示家长批准且经过儿童标记检查的视频。")
        childSummary = TvUi.label(this, "", 18f)
        panel.addView(childSummary)
        TvUi.space(panel, this, 10)
        panel.addView(TvUi.primaryButton(this, "＋ 通过频道链接添加") {
            addChannelFromLink()
        })
        panel.addView(TvUi.button(this, "查看 / 删除已批准频道") {
            manageApprovedChannels()
        })
        panel.addView(TvUi.button(this, "设置每日观看时长") { pickDailyLimit() })
        val searchControl = Switch(this).apply {
            text = "允许搜索（结果仍受白名单限制）"
            textSize = 18f
            setTextColor(TvUi.text)
            isChecked = store.searchAllowed
            setOnCheckedChangeListener { _, enabled ->
                store.searchAllowed = enabled
                refreshSummaries()
            }
        }
        panel.addView(searchControl)
        panel.addView(Switch(this).apply {
            text = "允许自动播放下一集"
            textSize = 18f
            setTextColor(TvUi.text)
            isChecked = store.autoplayNext
            setOnCheckedChangeListener { _, enabled ->
                store.autoplayNext = enabled
                refreshSummaries()
            }
        })
        TvUi.space(panel, this, 10)
        panel.addView(TvUi.button(this, "设置 YouTube API Key（可选）") {
            editValue("YouTube Data API v3 Key", store.apiKey, secret = true) {
                store.apiKey = it.trim()
            }
        })
        panel.addView(TvUi.button(this, "＋ 通过视频链接添加") {
            addVideoFromLink()
        })
        panel.addView(TvUi.button(this, "查看 / 删除已批准视频") {
            manageApprovedVideos()
        })
        panel.addView(TvUi.button(this, "精确允许的视频 ID") {
            editLines("允许的视频 ID（每行 11 位）", store.allowedVideosText) {
                store.allowedVideosText = it
            }
        })
        panel.addView(TvUi.button(this, "禁止的视频 ID") {
            editLines("禁止的视频 ID（每行 11 位）", store.blockedVideosText) {
                store.blockedVideosText = it
            }
        })
        panel.addView(TvUi.button(this, "修改家长 PIN") { setPinDialog(initial = false) })
        panel.addView(TvUi.button(this, "清除观看历史和收藏") {
            AlertDialog.Builder(this)
                .setMessage("要清空本机的观看记录和收藏吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认清除") { _, _ ->
                    WatchHistory(this).clear()
                    summary.text = "观看历史与收藏已清除"
                }.show()
        })
        TvUi.space(panel, this, 12)
    }

    private fun buildNetworkSection(panel: LinearLayout) {
        heading(panel, "网络代理", "启动 KidsTubeTV 自动连接，离开后自动断开；无需孩子操作。")
        networkState = TvUi.label(this, "", 25f, true)
        networkDetail = TvUi.label(this, "", 16f).apply { setTextColor(TvUi.muted) }
        panel.addView(networkState)
        panel.addView(networkDetail)
        TvUi.space(panel, this, 18)

        // Only two main actions; token entry appears in one dialog, not on every visit.
        panel.addView(TvUi.primaryButton(this, "＋ 添加 / 更换 Clash 订阅") {
            editSubscription()
        })
        TvUi.space(panel, this, 7)
        panel.addView(TvUi.button(this, "◈  选择代理节点") {
            startActivity(Intent(this, ProxyActivity::class.java))
        })
        TvUi.space(panel, this, 18)

        val toggle = TvUi.button(this, "高级设置  ▾") {
            networkAdvanced.visibility =
                if (networkAdvanced.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        panel.addView(toggle)
        networkAdvanced = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        panel.addView(networkAdvanced)
        networkAdvanced.addView(TvUi.button(this, "立即更新当前订阅") {
            if (store.subscriptionUrl.isBlank()) {
                editSubscription()
            } else {
                downloadSubscription(store.subscriptionUrl)
            }
        })
        networkAdvanced.addView(TvUi.button(this, "从 U 盘导入 Clash YAML") { importYaml() })
        networkAdvanced.addView(TvUi.button(this, "订阅自动更新周期") { chooseRefreshInterval() })
        networkAdvanced.addView(TvUi.button(this, "设备与网络诊断") {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        })
        networkAdvanced.addView(TvUi.button(this, "恢复上一次 Clash 配置") {
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
                        networkDetail.text = if (ok) "返回首页重新连接 VPN 以生效"
                            else "文件恢复失败，当前配置未主动删除"
                    }.show()
            }
        })
        networkAdvanced.addView(TvUi.button(this, "关闭当前 VPN") {
            KidsVpnService.stop(this)
            networkState.text = "正在断开连接…"
        })
        TvUi.space(networkAdvanced, this, 12)
    }

    private fun buildAccountSection(panel: LinearLayout) {
        heading(panel, "YouTube 账号", "Google 登录不是必需的；只有家长批准的频道会展示。")
        accountSummary = TvUi.label(this, "", 18f)
        panel.addView(accountSummary)
        TvUi.space(panel, this, 12)
        panel.addView(TvUi.primaryButton(this, "Google 账号授权 / 导入订阅") {
            startActivity(Intent(this, GoogleAccountActivity::class.java))
        })
        panel.addView(TvUi.label(this,
            "Google 验证码可在手机上完成，不需要使用电视遥控器输入账号密码。", 15f)
            .apply { setTextColor(TvUi.muted) })
    }

    private fun refreshSummaries() {
        summary.text = "儿童频道：" + store.allowedChannels.size + "   ·   今日已看 " +
            (WatchTime(this).elapsedSeconds() / 60) + " 分钟"
        childSummary.text = "当前批准 " + store.allowedChannels.size + " 个频道  ·  每日 " +
            store.dailyLimitMinutes + " 分钟"
        val state = when (ProxyStatus.phase) {
            ProxyStatus.Phase.OFF -> "○ 未连接"
            ProxyStatus.Phase.STARTING -> "◌ 正在连接…"
            ProxyStatus.Phase.ERROR -> "⚠ 连接异常"
            ProxyStatus.Phase.READY -> if (ProxyNetworkHealth.state == ProxyNetworkHealth.State.UNREACHABLE)
                "⚠ 已连接，但网络不可达" else "● 已连接"
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
        networkDetail.text = source + time
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
        AlertDialog.Builder(this)
            .setTitle("勾选要保留的视频")
            .setMultiChoiceItems(ids.toTypedArray(), selected) { _, index, checked ->
                selected[index] = checked
            }
            .setNegativeButton("取消", null)
            .setPositiveButton("保存视频白名单") { _, _ ->
                store.allowedVideosText = ids.filterIndexed { index, _ -> selected[index] }
                    .joinToString("\n")
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
        AlertDialog.Builder(this)
            .setTitle("勾选保留的频道")
            .setMultiChoiceItems(channels.toTypedArray(), enabled) { _, index, checked ->
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
            "Clash / Mihomo 订阅地址",
            store.subscriptionUrl,
            hint = "https://example.com/subscription?token=...",
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
    }
}
