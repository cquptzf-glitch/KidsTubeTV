package dev.kidstubetv.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.text.InputType
import android.widget.EditText
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.view.View
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dev.kidstubetv.data.ApprovedPickerPolicy
import dev.kidstubetv.data.GoogleTvAuth
import dev.kidstubetv.data.ParentSession
import dev.kidstubetv.data.SettingsStore
import dev.kidstubetv.data.TvDeviceCode
import dev.kidstubetv.data.YoutubeSubscription
import dev.kidstubetv.proxy.ProxyStatus
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Optional Google account connection for reading the parent's subscribed channels.
 * Google login happens on another device via Google's official TV OAuth flow.
 * Importing follows requires explicit parental selection, never blanket approval.
 */
class GoogleAccountActivity : Activity() {
    /** Consume an expired parent's remote event before it reaches a sensitive button. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && !ParentSession.onInteraction()) {
            finish()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Every remote action is gated by the recent parent PIN session. */
    override fun onUserInteraction() {
        super.onUserInteraction()
        if (!ParentSession.onInteraction()) finish()
    }

    private val auth by lazy { GoogleTvAuth(this) }
    private val settings by lazy { SettingsStore(this) }
    private lateinit var clientId: EditText
    private lateinit var clientSecret: EditText
    private lateinit var status: TextView
    private lateinit var credentialsPane: LinearLayout
    private var cancel: AtomicBoolean? = null
    private var challengeDialog: AlertDialog? = null
    private var browserHandoff = false
    private val subscriptionRequests = LatestResultGate()

    override fun onResume() {
        super.onResume()
        if (browserHandoff) {
            browserHandoff = false
            ParentSession.endExternalUi()
        }
        if (!ParentSession.authorized()) finish()
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        UiDevice.showSystemBarsForHandheld(this)
        if (!settings.hasPin || !ParentSession.authorized()) {
            finish()
            return
        }
        // This page contains OAuth client credentials and a device code.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val phone = UiDevice.isHandheld(this)
        val page = SettingsDetailPage(
            this,
            "Google / YouTube",
            "仅家长使用。用手机或电脑在 Google 官方网站完成设备码授权，随后选择可观看的频道。"
        )
        val layout = page.body
        status = page.status(when {
            auth.isLinked -> "✓ Google 已授权，可以选择订阅频道"
            !auth.isConfigured -> "需要先配置自己的 Google TV OAuth 客户端"
            else -> "客户端已配置，可以获取新的 Google 验证码"
        })

        val primary = page.action(
            if (phone) "获取 Google 验证码" else "手机扫码登录 Google",
            "仅申请 YouTube 只读权限；不会同步个人播放记录",
            primary = true
        ) {
            if (!auth.isConfigured || credentialsPane.visibility == View.VISIBLE) {
                if (saveCredentials()) {
                    credentialsPane.visibility = View.GONE
                    startAuth()
                }
            } else startAuth()
        }
        page.action(
            "导入已订阅的 YouTube 频道",
            "授权完成后勾选需要给孩子看的频道，不会自动全部添加"
        ) { chooseSubscriptions() }

        val credentialsToggle = page.action(
            "OAuth 客户端配置  ▾",
            "首次使用需要 Google Cloud TV 客户端，日常无需更改"
        ) {
            val open = credentialsPane.visibility != View.VISIBLE
            credentialsPane.visibility = if (open) View.VISIBLE else View.GONE
        }
        credentialsPane = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (auth.isConfigured) View.GONE else View.VISIBLE
            val pad = TvUi.dp(this@GoogleAccountActivity, if (phone) 12 else 20)
            setPadding(pad, pad, pad, pad)
            background = SettingsDecor.statusShape(this@GoogleAccountActivity)
        }
        credentialsToggle.setOnClickListener {
            val open = credentialsPane.visibility != View.VISIBLE
            credentialsPane.visibility = if (open) View.VISIBLE else View.GONE
            credentialsToggle.setTitle("OAuth 客户端配置  " + if (open) "▴" else "▾")
        }
        layout.addView(credentialsPane)
        credentialsPane.addView(TvUi.label(this,
            "在 Google Cloud 启用 YouTube Data API v3，创建“TVs and Limited Input devices”类型的客户端。",
            if (phone) 14f else 17f))
        credentialsPane.addView(TvUi.label(this,
            "不会显示已保存的 Client Secret；如需更换，请重新输入。",
            if (phone) 13f else 15f).apply { setTextColor(TvUi.muted) })
        credentialsPane.addView(TvUi.label(this, "OAuth Client ID", if (phone) 15f else 18f))
        clientId = TvUi.input(this, "xxx.apps.googleusercontent.com", auth.clientId)
        credentialsPane.addView(clientId, LinearLayout.LayoutParams(-1, -2))
        credentialsPane.addView(TvUi.label(this, "OAuth Client Secret", if (phone) 15f else 18f))
        clientSecret = TvUi.input(this, if (auth.clientSecret.isNotEmpty())
            "已保存；留空表示不修改" else "输入 Google TV Client Secret", "").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        credentialsPane.addView(clientSecret, LinearLayout.LayoutParams(-1, -2))
        TvUi.space(credentialsPane, this, 9)
        credentialsPane.addView(TvUi.primaryButton(this, "保存客户端配置") {
            if (saveCredentials()) {
                credentialsPane.visibility = View.GONE
                credentialsToggle.setTitle("OAuth 客户端配置  ▾")
            }
        }, LinearLayout.LayoutParams(-1, TvUi.dp(this, if (phone) 52 else 66)))

        page.divider()
        page.action(
            "退出本机 Google 账号",
            "仅删除本机令牌，已配置的 Google Cloud 客户端仍保留"
        ) {
            AlertDialog.Builder(this)
                .setTitle("退出 Google 授权？")
                .setMessage("删除本机授权令牌，但不删除已经批准给孩子的频道。")
                .setNegativeButton("取消", null)
                .setPositiveButton("退出授权") { _, _ ->
                    cancel?.set(true)
                    challengeDialog?.dismiss()
                    challengeDialog = null
                    subscriptionRequests.invalidate()
                    auth.logoutLocal()
                    status.text = "已退出本机 Google 账号"
                }.show()
        }
        page.focusPrimary(primary)
    }

    private fun saveCredentials(): Boolean = runCatching {
        val changed = auth.configureClient(
            clientId.text.toString(), clientSecret.text.toString()
        )
        if (changed) {
            // No in-flight pairing may finish under a different OAuth client.
            cancel?.set(true)
            challengeDialog?.dismiss()
            subscriptionRequests.invalidate()
            status.text = "✓ Google 客户端已更新，旧授权已清除，请重新扫码"
        } else {
            status.text = "✓ Google 客户端设置已保存，可继续使用原授权"
        }
    }.fold({
        true
    }, {
        status.text = "保存失败：请检查 Client ID / Secret 和系统安全存储"
        false
    })

    private fun startAuth() {
        if (ProxyStatus.phase != ProxyStatus.Phase.READY) {
            status.text = "VPN 尚未就绪。请返回家长中心 → 网络与 VPN，先连接，再扫码授权"
            return
        }
        cancel?.set(true)
        challengeDialog?.dismiss()
        challengeDialog = null
        val cancelled = AtomicBoolean(false)
        cancel = cancelled
        status.text = "向 Google 请求电视验证码…"
        Thread({
            val result = runCatching {
                val challenge = auth.begin()
                runOnUiThread {
                    if (isFinishing || cancelled.get() || cancel !== cancelled)
                        return@runOnUiThread
                    showChallenge(challenge, cancelled)
                }
                auth.awaitAuthorization(challenge, cancelled) { progress ->
                    runOnUiThread {
                        if (!isFinishing && cancel === cancelled && !cancelled.get())
                            status.text = progress
                    }
                }
                // The device-code poller can return early without authorization.
                // Checking the session revision also distinguishes a previous
                // linked account from the current new authorization.
                auth.isLinked && auth.authRevision != challenge.issuedSession
            }
            runOnUiThread {
                if (isFinishing || isDestroyed || cancelled.get() || cancel !== cancelled)
                    return@runOnUiThread
                result.onFailure { e ->
                    status.text = "Google 登录失败：" +
                        (e.message?.takeIf { it.length <= 160 && !it.contains("http") }
                            ?: "请检查网络和客户端配置")
                }.onSuccess { freshAuthorization ->
                    status.text = if (freshAuthorization) {
                        "✓ Google 授权成功，可以选择订阅频道"
                    } else {
                        "授权未完成或已失效，请重新获取验证码"
                    }
                    challengeDialog?.setOnDismissListener(null)
                    challengeDialog?.dismiss()
                    challengeDialog = null
                }
            }
        }, "google-tv-oauth").start()
    }

    private fun showChallenge(challenge: TvDeviceCode, cancelled: AtomicBoolean) {
        val phone = UiDevice.isHandheld(this)
        val description = TvUi.label(this, if (phone) {
            "① 复制验证码：${challenge.userCode}\n" +
            "② 用浏览器打开 Google 授权页并输入验证码。\n" +
            "③ 返回 KidsTubeTV，等待授权结果。"
        } else {
            "请用手机或电脑打开以下网址：\n\n" +
            challenge.verificationUrl + "\n\n输入验证码：\n\n" +
            challenge.userCode + "\n\n同意只读访问 YouTube 订阅资料。"
        }, if (phone) 17f else 21f)
        val panel = TvUi.column(this)
        panel.addView(description)
        if (!phone) {
            // A TV QR contains only an official verification URL, not tokens.
            runCatching {
                val matrix = QRCodeWriter().encode(
                    challenge.verificationUrl, BarcodeFormat.QR_CODE, 360, 360,
                    mapOf(EncodeHintType.MARGIN to 2)
                )
                val pixels = IntArray(360 * 360) { index ->
                    if (matrix[index % 360, index / 360]) Color.BLACK else Color.WHITE
                }
                val bitmap = Bitmap.createBitmap(360, 360, Bitmap.Config.ARGB_8888)
                bitmap.setPixels(pixels, 0, 360, 0, 0, 360, 360)
                val qr = ImageView(this).apply {
                    setImageBitmap(bitmap)
                    contentDescription = "扫码打开 Google 官方设备授权页面"
                }
                panel.addView(qr, LinearLayout.LayoutParams(
                    TvUi.dp(this, 250), TvUi.dp(this, 250)
                ))
            }
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(if (phone) "Google 验证码 · 手机授权" else "Google TV 授权 · 手机扫码")
            .setView(panel)
            .setNegativeButton("取消登录") { _, _ -> cancelled.set(true) }
        if (phone) {
            builder.setNeutralButton("复制验证码", null)
                .setPositiveButton("打开授权网页", null)
        }
        challengeDialog = builder.create().also { dialog ->
            dialog.setOnDismissListener { cancelled.set(true) }
            dialog.show()
            if (phone) {
                // Replacing default click handlers prevents Android AlertDialog
                // from closing and cancelling the still-polling device code.
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                    copyDeviceCode(challenge.userCode)
                    status.text = "验证码已复制，打开 Google 授权页后粘贴"
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    if (cancelled.get() || !ParentSession.authorized()) return@setOnClickListener
                    copyDeviceCode(challenge.userCode)
                    val url = challenge.verificationUrl
                    if (!GoogleVerificationUrlPolicy.isOfficial(url)) {
                        status.text = "Google 授权网址未通过安全检查，已取消跳转"
                        return@setOnClickListener
                    }
                    // The Android browser is outside this app-only VPN.
                    // Allow an explicit, short-lived external UI transition only
                    // for the parent; do not allow arbitrary background access.
                    ParentSession.allowExternalUi()
                    browserHandoff = true
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }.onFailure {
                        browserHandoff = false
                        ParentSession.endExternalUi()
                        status.text = "无法打开浏览器，可手动访问：" + url
                    }
                }
            }
        }
    }

    private fun copyDeviceCode(userCode: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Google TV 验证码", userCode))
    }

    private fun chooseSubscriptions() {
        if (ProxyStatus.phase != ProxyStatus.Phase.READY) {
            status.text = "请先到家长中心连接 VPN，再读取订阅频道"
            return
        }
        if (!auth.isLinked) {
            status.text = "请先完成 Google 授权"
            return
        }
        val request = subscriptionRequests.begin()
        val authRevision = auth.authRevision
        status.text = "正在读取 Google 账户已订阅频道…"
        Thread({
            val result = runCatching { auth.subscriptions() }
            runOnUiThread {
                // Account may have changed or parent PIN session may have expired.
                if (isFinishing || isDestroyed || !ParentSession.authorized() ||
                    !subscriptionRequests.isCurrent(request) ||
                    !auth.isLinked || auth.authRevision != authRevision) return@runOnUiThread
                result.onSuccess { channels ->
                    if (channels.isEmpty()) {
                        status.text = "没有发现订阅频道"
                    } else {
                        showSubscriptionPicker(channels, request, authRevision)
                    }
                }.onFailure {
                    status.text = "读取失败：" + (it.message ?: "检查网络或账号")
                }
            }
        }, "google-subscriptions").start()
    }

    private fun showSubscriptionPicker(
        channels: List<YoutubeSubscription>,
        request: Long,
        authRevision: Long
    ) {
        val selected = BooleanArray(channels.size) { channels[it].channelId in settings.allowedChannels }
        AlertDialog.Builder(this)
            .setTitle("选择要批准的频道（未选择的不会加入）")
            .setMultiChoiceItems(channels.map { it.title }.toTypedArray(), selected) { _, i, checked ->
                selected[i] = checked
            }
            .setNegativeButton("取消", null)
            .setPositiveButton("保存频道白名单") { _, _ ->
                if (!ParentSession.authorized() || !subscriptionRequests.isCurrent(request) ||
                    !auth.isLinked || auth.authRevision != authRevision) {
                    status.text = "Google 账号已改变或家长会话已过期，请重新读取订阅频道"
                    return@setPositiveButton
                }
                val displayed = channels.map { it.channelId }
                val chosen = channels.filterIndexed { index, _ -> selected[index] }
                    .map { it.channelId }
                val updated = ApprovedPickerPolicy.apply(
                    settings.allowedChannels, displayed, chosen)
                settings.channelsText = updated.joinToString("\n")
                channels.filterIndexed { index, _ -> selected[index] }.forEach {
                    settings.rememberChannelName(it.channelId, it.title)
                }
                status.text = "已批准 " + updated.size +
                    " 个频道。请返回儿童首页查看批准频道。"
            }
            .show()
    }

    override fun onDestroy() {
        cancel?.set(true)
        subscriptionRequests.invalidate()
        challengeDialog?.dismiss()
        super.onDestroy()
    }
}
