package dev.kidstubetv.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.text.InputType
import android.widget.EditText
import android.widget.ScrollView
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
    private val subscriptionRequests = LatestResultGate()

    override fun onResume() {
        super.onResume()
        if (!ParentSession.authorized()) finish()
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (!settings.hasPin || !ParentSession.authorized()) {
            finish()
            return
        }
        val scrolling = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val layout = TvUi.column(this)
        scrolling.addView(layout)
        setContentView(scrolling)
        layout.addView(TvUi.label(this, "Google / YouTube 授权", 29f, true))
        layout.addView(TvUi.label(this,
            "家长用手机完成授权。只有家长之后勾选的频道才会加入儿童白名单。", 17f))
        status = TvUi.label(this,
            when {
                auth.isLinked -> "✓ Google 已授权，可以导入 YouTube 订阅"
                !auth.isConfigured -> "需要先配置自己的 Google TV OAuth 客户端"
                else -> "客户端已配置，选择“手机扫码授权”即可"
            }, 18f)
        layout.addView(status)
        TvUi.space(layout, this, 12)

        layout.addView(TvUi.primaryButton(this, "手机扫码授权 Google") {
            if (!auth.isConfigured || credentialsPane.visibility == View.VISIBLE) {
                if (saveCredentials()) {
                    credentialsPane.visibility = View.GONE
                    startAuth()
                }
            } else startAuth()
        })
        TvUi.space(layout, this, 9)
        layout.addView(TvUi.button(this, "导入 Google 已订阅的频道") { chooseSubscriptions() })
        layout.addView(TvUi.button(this, "配置 / 更换 Google TV OAuth 客户端") {
            credentialsPane.visibility =
                if (credentialsPane.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        })
        TvUi.space(layout, this, 9)

        credentialsPane = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (auth.isConfigured) View.GONE else View.VISIBLE
            setPadding(TvUi.dp(this@GoogleAccountActivity, 16), TvUi.dp(this@GoogleAccountActivity, 10),
                TvUi.dp(this@GoogleAccountActivity, 16), TvUi.dp(this@GoogleAccountActivity, 10))
            setBackgroundColor(TvUi.surface)
        }
        layout.addView(credentialsPane)
        credentialsPane.addView(TvUi.label(this,
            "首次使用：在 Google Cloud 开启 YouTube Data API v3，并创建“TVs and Limited Input devices”类型的 OAuth 客户端。", 16f))
        credentialsPane.addView(TvUi.label(this,
            "KidsTubeTV 不内置通用 Google 登录凭据；请在手机上准备好 Client ID 和 Client Secret。", 15f))
        credentialsPane.addView(TvUi.label(this, "OAuth Client ID", 18f))
        clientId = TvUi.input(this, "xxx.apps.googleusercontent.com", auth.clientId)
        credentialsPane.addView(clientId)
        credentialsPane.addView(TvUi.label(this, "OAuth Client Secret", 18f))
        clientSecret = TvUi.input(this, if (auth.clientSecret.isNotEmpty())
            "已保存，留空表示不修改" else "输入 Google TV Client Secret", "").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        credentialsPane.addView(clientSecret)
        credentialsPane.addView(TvUi.button(this, "保存客户端配置") {
            if (saveCredentials()) credentialsPane.visibility = View.GONE
        })

        TvUi.space(layout, this, 14)
        layout.addView(TvUi.button(this, "清除本机 Google 授权") {
            AlertDialog.Builder(this)
                .setMessage("只删除本机 Google 登录令牌，Google Cloud 客户端配置会保留。")
                .setNegativeButton("取消", null)
                .setPositiveButton("退出") { _, _ ->
                    cancel?.set(true)
                    challengeDialog?.dismiss()
                    challengeDialog = null
                    subscriptionRequests.invalidate()
                    auth.logoutLocal()
                    status.text = "已退出本机 Google 账号"
                }.show()
        })
        layout.addView(TvUi.button(this, "返回家长设置 / 网络与 VPN") { finish() })
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
        status.text = "保存失败：" + (it.message ?: "请检查 Client ID / Secret")
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
            }
            runOnUiThread {
                if (isFinishing || isDestroyed || cancelled.get() || cancel !== cancelled)
                    return@runOnUiThread
                result.onFailure { e ->
                    status.text = "Google 登录失败：" + (e.message ?: "请检查网络与客户端")
                }.onSuccess {
                    status.text = "✓ Google 授权成功，现在可以导入订阅频道"
                    challengeDialog?.setOnDismissListener(null)
                    challengeDialog?.dismiss()
                    challengeDialog = null
                }
            }
        }, "google-tv-oauth").start()
    }

    private fun showChallenge(challenge: TvDeviceCode, cancelled: AtomicBoolean) {
        val description = TvUi.label(this,
            "请用手机或电脑打开以下网址：\n\n" +
            challenge.verificationUrl + "\n\n输入验证码（请区分大小写）：\n\n" +
            challenge.userCode + "\n\n同意只读访问 YouTube 订阅资料。", 21f)
        val panel = TvUi.column(this)
        panel.addView(description)
        // QR contains only Google's verification URL, not OAuth tokens,
        // the device code or the user's YouTube account identifiers.
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
        challengeDialog = AlertDialog.Builder(this)
            .setTitle("Google TV 授权 · 手机扫码")
            .setView(panel)
            .setNegativeButton("取消登录") { _, _ -> cancelled.set(true) }
            .create().also { dialog ->
                dialog.setOnDismissListener { cancelled.set(true) }
                dialog.show()
            }
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
