package dev.kidstubetv.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.TextView
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
    private var cancel: AtomicBoolean? = null
    private var challengeDialog: AlertDialog? = null

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
        val scrolling = ScrollView(this)
        val layout = TvUi.column(this)
        scrolling.addView(layout)
        setContentView(scrolling)
        layout.addView(TvUi.label(this, "连接 Google / YouTube", 29f, true))
        layout.addView(TvUi.label(this,
            "本功能可选，仅用于读取你的 YouTube 订阅频道，不会自动允许任何频道给孩子观看。", 17f))
        layout.addView(TvUi.label(this,
            "请在 Google Cloud 创建类型为「TVs and Limited Input devices」的 OAuth 客户端。", 16f))
        TvUi.space(layout, this)
        layout.addView(TvUi.label(this, "OAuth Client ID", 18f))
        clientId = TvUi.input(this, "xxxx.apps.googleusercontent.com", auth.clientId)
        layout.addView(clientId)
        layout.addView(TvUi.label(this, "OAuth Client Secret", 18f))
        clientSecret = TvUi.input(this, "Google TV OAuth Client Secret", auth.clientSecret).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(clientSecret)
        status = TvUi.label(this,
            if (auth.isLinked) "Google 已授权，可选择导入订阅频道" else "Google 尚未登录", 18f)
        layout.addView(status)
        TvUi.space(layout, this)
        layout.addView(TvUi.button(this, "保存 OAuth 客户端配置") { saveCredentials() })
        layout.addView(TvUi.button(this, "开始 Google 电视授权（手机操作）") {
            if (saveCredentials()) startAuth()
        })
        layout.addView(TvUi.button(this, "读取订阅频道并选择加入儿童白名单") { chooseSubscriptions() })
        layout.addView(TvUi.button(this, "从本机退出 Google 授权") {
            AlertDialog.Builder(this)
                .setMessage("仅清除本机的 Google 凭据；如需彻底撤销授权，请到 Google 账号安全设置操作。")
                .setNegativeButton("取消", null)
                .setPositiveButton("退出") { _, _ ->
                    auth.logoutLocal()
                    status.text = "本机 Google 凭据已清除"
                }.show()
        })
        layout.addView(TvUi.button(this, "返回家长设置") { finish() })
    }

    private fun saveCredentials(): Boolean = runCatching {
        auth.clientId = clientId.text.toString()
        auth.clientSecret = clientSecret.text.toString()
        require(auth.isConfigured) { "OAuth Client ID 和 Secret 均不能为空" }
    }.fold({
        status.text = "Google OAuth 客户端配置已保存（Keystore 加密）"
        true
    }, {
        status.text = "保存失败：" + (it.message ?: "未知错误")
        false
    })

    private fun startAuth() {
        if (ProxyStatus.phase != ProxyStatus.Phase.READY) {
            status.text = "请先返回首页启动 VPN，确保能够访问 Google 授权服务"
            return
        }
        cancel?.set(true)
        val cancelled = AtomicBoolean(false)
        cancel = cancelled
        status.text = "向 Google 请求电视验证码…"
        Thread({
            val result = runCatching {
                val challenge = auth.begin()
                runOnUiThread {
                    if (isFinishing || cancelled.get()) return@runOnUiThread
                    showChallenge(challenge, cancelled)
                }
                auth.awaitAuthorization(challenge, cancelled) { progress ->
                    runOnUiThread { if (!isFinishing) status.text = progress }
                }
            }
            runOnUiThread {
                if (!isFinishing) {
                    result.onFailure { e ->
                        if (!cancelled.get()) status.text = "Google 登录失败：" + (e.message ?: "")
                    }.onSuccess {
                        if (!cancelled.get()) status.text = "Google 授权成功，现在可以导入订阅频道"
                        challengeDialog?.setOnDismissListener(null)
                        challengeDialog?.dismiss()
                    }
                }
            }
        }, "google-tv-oauth").start()
    }

    private fun showChallenge(challenge: TvDeviceCode, cancelled: AtomicBoolean) {
        val description = TvUi.label(this,
            "请用手机或电脑打开以下网址：\n\n" +
            challenge.verificationUrl + "\n\n输入验证码（请区分大小写）：\n\n" +
            challenge.userCode + "\n\n同意只读访问 YouTube 订阅资料。", 21f)
        challengeDialog = AlertDialog.Builder(this)
            .setTitle("Google TV 授权 · 请在手机操作")
            .setView(description)
            .setNegativeButton("取消登录") { _, _ -> cancelled.set(true) }
            .create().also { dialog ->
                dialog.setOnDismissListener { cancelled.set(true) }
                dialog.show()
            }
    }

    private fun chooseSubscriptions() {
        if (ProxyStatus.phase != ProxyStatus.Phase.READY) {
            status.text = "请先启动 VPN"
            return
        }
        if (!auth.isLinked) {
            status.text = "请先完成 Google 授权"
            return
        }
        status.text = "正在读取 Google 账户已订阅频道…"
        Thread({
            val result = runCatching { auth.subscriptions() }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                result.onSuccess { channels ->
                    if (channels.isEmpty()) {
                        status.text = "没有发现订阅频道"
                    } else {
                        showSubscriptionPicker(channels)
                    }
                }.onFailure { status.text = "读取失败：" + (it.message ?: "") }
            }
        }, "google-subscriptions").start()
    }

    private fun showSubscriptionPicker(channels: List<YoutubeSubscription>) {
        val selected = BooleanArray(channels.size) { channels[it].channelId in settings.allowedChannels }
        AlertDialog.Builder(this)
            .setTitle("选择要批准的频道（未选择的不会加入）")
            .setMultiChoiceItems(channels.map { it.title }.toTypedArray(), selected) { _, i, checked ->
                selected[i] = checked
            }
            .setNegativeButton("取消", null)
            .setPositiveButton("保存频道白名单") { _, _ ->
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
        challengeDialog?.dismiss()
        super.onDestroy()
    }
}
