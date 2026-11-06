package dev.kidstubetv.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.*
import dev.kidstubetv.data.*
import dev.kidstubetv.proxy.*
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.util.Date

/** Parent-only settings; subscription tokens and API key are encrypted by Android Keystore. */
class SettingsActivity : Activity() {
    private val store by lazy { SettingsStore(this) }
    private lateinit var api: EditText
    private lateinit var channels: EditText
    private lateinit var allowed: EditText
    private lateinit var blocked: EditText
    private lateinit var subscription: EditText
    private lateinit var searchSwitch: Switch
    private lateinit var limitSpinner: Spinner
    private lateinit var refreshSpinner: Spinner
    private lateinit var message: TextView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (store.hasPin && !ParentSession.authorized()) { finish(); return }
        val screen = ScrollView(this)
        val body = TvUi.column(this)
        screen.addView(body)
        setContentView(screen)
        body.addView(TvUi.label(this, "家长控制 / 代理设置", 28f, true))
        body.addView(TvUi.label(this, "只有批准频道或视频且满足儿童及嵌入条件的内容可以播放。", 17f))
        TvUi.space(body, this)

        // Remote-friendly parent hub: three large categories; hide all input-heavy
        // settings unless the parent explicitly navigates into that section.
        val childPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val networkPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val accountPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val categories = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val panels = listOf(childPanel, networkPanel, accountPanel)
        val categoryButtons = mutableListOf<android.widget.Button>()
        fun selectPanel(index: Int) {
            panels.forEachIndexed { i, panel -> panel.visibility = if (i == index) android.view.View.VISIBLE else android.view.View.GONE }
            categoryButtons.forEachIndexed { i, button ->
                button.alpha = if (i == index) 1f else 0.68f
            }
            screen.post { screen.smoothScrollTo(0, 0) }
        }
        listOf("✦  儿童内容", "◈  网络代理", "◉  YouTube 账号")
            .forEachIndexed { index, title ->
                val button = TvUi.button(this, title) { selectPanel(index) }
                categoryButtons.add(button)
                categories.addView(button, LinearLayout.LayoutParams(0, TvUi.dp(this, 58), 1f).apply {
                    rightMargin = TvUi.dp(this@SettingsActivity, 10)
                })
            }
        body.addView(categories)
        TvUi.space(body, this, 10)
        message = TvUi.label(this, stateText(), 15f).apply { setTextColor(TvUi.muted) }
        body.addView(message)
        TvUi.space(body, this, 10)
        body.addView(childPanel)
        body.addView(networkPanel)
        body.addView(accountPanel)
        selectPanel(0)
        networkPanel.addView(TvUi.label(this, "Clash / Mihomo 订阅地址（HTTPS YAML）", 18f, true))
        subscription = TvUi.input(this, "https://.../subscription?token=...", store.subscriptionUrl)
        networkPanel.addView(subscription)
        networkPanel.addView(TvUi.button(this, "保存订阅地址并立即更新") { saveSubscriptionAndFetch() })
        TvUi.space(body, this)
        networkPanel.addView(TvUi.label(this, "自动更新周期", 17f))
        refreshSpinner = spinner(body, listOf("关闭", "每 6 小时", "每 24 小时", "每 72 小时", "每 7 天"),
            listOf(0,6,24,72,168).indexOf(store.subscriptionIntervalHours).coerceAtLeast(0))
        networkPanel.addView(TvUi.button(this, "从 U 盘导入完整 Mihomo YAML") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }, REQUEST_PROFILE)
        })
        networkPanel.addView(TvUi.button(this, "节点选择 / 延迟测试 / 流量") {
            startActivity(Intent(this, ProxyActivity::class.java))
        })
        TvUi.space(body, this)
        accountPanel.addView(TvUi.button(this, "Google / YouTube 账号（可选登录）") {
            startActivity(Intent(this, GoogleAccountActivity::class.java))
        })
        accountPanel.addView(TvUi.label(this,
            "登录需 Google Cloud 的 TV OAuth 客户端；仅用于导入家长订阅频道，不会自动批准。", 15f))
        TvUi.space(body, this)
        childPanel.addView(TvUi.label(this, "YouTube Data API v3 Key", 18f, true))
        api = TvUi.input(this, "Google Cloud API Key", store.apiKey)
        api.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        childPanel.addView(api)
        childPanel.addView(TvUi.label(this, "批准的频道 ID，每行一个 UC...", 18f))
        channels = TvUi.input(this, "UCxxxxxxxxxxxxxxxxxxxxxx", store.channelsText, true)
        childPanel.addView(channels)
        childPanel.addView(TvUi.label(this, "额外批准的视频 ID（每行一个）", 18f))
        allowed = TvUi.input(this, "11 位视频 ID", store.allowedVideosText, true)
        childPanel.addView(allowed)
        childPanel.addView(TvUi.label(this, "禁止的视频 ID（禁止优先）", 18f))
        blocked = TvUi.input(this, "11 位视频 ID", store.blockedVideosText, true)
        childPanel.addView(blocked)
        searchSwitch = Switch(this).apply {
            text = "允许儿童搜索（仍只显示白名单）"
            setTextColor(-1)
            textSize = 17f
            isChecked = store.searchAllowed
        }
        childPanel.addView(searchSwitch)
        childPanel.addView(TvUi.label(this, "每日观看时间", 17f))
        val limits = listOf(15,20,30,45,60,90,120)
        limitSpinner = spinner(body, limits.map { "$it 分钟" },
            limits.indexOf(store.dailyLimitMinutes).coerceAtLeast(0))
        TvUi.space(body, this)
        childPanel.addView(TvUi.button(this, "保存所有设置") { saveFields() })
        childPanel.addView(TvUi.button(this, "设置 / 修改家长 PIN") { pinDialog(false) })
        childPanel.addView(TvUi.button(this, "清空本地历史与收藏") {
            AlertDialog.Builder(this).setMessage("确定删除所有本地历史和收藏？")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空") { _, _ ->
                    WatchHistory(this).clear()
                    message.text = "已清空"
                }.show()
        })
        childPanel.addView(TvUi.button(this, "返回首页") { finish() })
        if (!store.hasPin) pinDialog(true)
    }

    private fun spinner(body: LinearLayout, values: List<String>, selected: Int): Spinner =
        Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity,
                android.R.layout.simple_spinner_dropdown_item, values)
            setSelection(selected.coerceIn(0, values.lastIndex))
            body.addView(this, LinearLayout.LayoutParams(-1, TvUi.dp(this@SettingsActivity, 50)))
        }

    private fun saveFields(): Boolean {
        return runCatching {
            store.apiKey = api.text.toString()
            store.channelsText = channels.text.toString()
            store.allowedVideosText = allowed.text.toString()
            store.blockedVideosText = blocked.text.toString()
            store.searchAllowed = searchSwitch.isChecked
            store.dailyLimitMinutes = listOf(15,20,30,45,60,90,120)[limitSpinner.selectedItemPosition]
            store.subscriptionIntervalHours = listOf(0,6,24,72,168)[refreshSpinner.selectedItemPosition]
            store.subscriptionUrl = subscription.text.toString()
        }.fold({
            message.text = if (store.ready) "设置已保存，可返回首页连接" else
                "已保存，仍需完整配置 API Key / PIN / 白名单 / 代理文件"
            true
        }, {
            message.text = "保存失败：" + (it.message ?: "")
            false
        })
    }

    private fun stateText(): String {
        val timestamp = store.subscriptionUpdatedAt
        val date = if (timestamp == 0L) "从未" else DateFormat.getDateTimeInstance().format(Date(timestamp))
        return (if (store.hasProfile) "代理配置已导入" else "没有代理配置") + " · 最近更新：" + date
    }

    private fun saveSubscriptionAndFetch() {
        val url = runCatching { SubscriptionFormat.normalizeUrl(subscription.text.toString()) }
            .getOrElse { message.text = "URL 无效：" + it.message; return }
        if (!saveFields()) return
        if (ProxyStatus.phase != ProxyStatus.Phase.OFF) {
            message.text = "正在关闭 VPN 以更新订阅…"
            KidsVpnService.stop(this)
            Handler(Looper.getMainLooper()).postDelayed({ fetch(url) }, 1000)
        } else fetch(url)
    }

    private fun fetch(url: String) {
        if (ProxyStatus.phase != ProxyStatus.Phase.OFF) {
            message.text = "VPN 尚未关闭，请再试一次"
            return
        }
        message.text = "正在下载订阅…"
        Thread({
            val result = runCatching {
                val content = SubscriptionClient().fetch(url)
                store.writeProfile(content)
                store.subscriptionUpdatedAt = System.currentTimeMillis()
            }
            runOnUiThread {
                if (!isFinishing) message.text = result.fold(
                    { "订阅更新成功；返回首页后使用新配置" },
                    { "订阅失败，保留旧配置：" + (it.message ?: "连接失败") }
                )
            }
        }, "subscription-manual").start()
    }

    @Deprecated("Storage Access Framework callback for Android 9")
    override fun onActivityResult(request: Int, code: Int, data: Intent?) {
        super.onActivityResult(request, code, data)
        if (request != REQUEST_PROFILE || code != RESULT_OK) return
        if (ProxyStatus.phase != ProxyStatus.Phase.OFF) {
            message.text = "请先在节点管理中断开 VPN 后导入"
            return
        }
        val uri = data?.data ?: return
        Thread({
            val result = runCatching {
                contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "无法读取文件" }
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        require(output.size() + n <= SubscriptionFormat.MAX_PROFILE_BYTES) { "文件太大" }
                        output.write(buffer, 0, n)
                    }
                    store.writeProfile(output.toString(Charsets.UTF_8.name()))
                }
                store.subscriptionUpdatedAt = System.currentTimeMillis()
            }
            runOnUiThread { message.text = result.fold(
                { "导入成功；返回首页可连接" },
                { "导入失败：" + (it.message ?: "未知错误") }
            ) }
        }, "yaml-import").start()
    }

    private fun pinDialog(firstRun: Boolean) {
        val input = EditText(this).apply {
            hint = "4～12 位数字"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (firstRun) "首次设置家长 PIN" else "更换家长 PIN")
            .setView(input).setPositiveButton("保存", null)
            .setNegativeButton("取消") { _, _ -> if (firstRun) finish() }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                runCatching { store.setPin(input.text.toString()) }
                    .onSuccess { ParentSession.unlock(); message.text = "PIN 已保存"; dialog.dismiss() }
                    .onFailure { input.error = it.message }
            }
        }
        dialog.show()
    }
    companion object { private const val REQUEST_PROFILE = 918 }
}
