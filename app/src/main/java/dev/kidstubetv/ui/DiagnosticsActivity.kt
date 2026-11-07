package dev.kidstubetv.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.webkit.WebView
import dev.kidstubetv.data.ParentSession
import dev.kidstubetv.data.SettingsStore
import dev.kidstubetv.data.YouTubeRepository
import dev.kidstubetv.data.ApiProbeErrorPolicy
import dev.kidstubetv.proxy.ProxyNetworkHealth
import dev.kidstubetv.proxy.ProxyStatus
import dev.kidstubetv.proxy.ProxyDnsStatus
import dev.kidstubetv.proxy.GoogleEndpointProbe

/**
 * Troubleshooting for real Android 9 televisions. No credentials, subscription
 * URLs, profile contents, access tokens or video history appear in the report.
 */
class DiagnosticsActivity : Activity() {
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

    private val store by lazy { SettingsStore(this) }
    private lateinit var report: TextView
    private lateinit var message: TextView
    private val credentialProbeGate = LatestResultGate()
    private var credentialProbeInFlight = false
    private var credentialProbeResult = "未检测"

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh()
    }

    override fun onResume() {
        super.onResume()
        if (!ParentSession.authorized()) finish()
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (!store.hasPin || !ParentSession.authorized()) { finish(); return }
        val scroller = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val panel = TvUi.column(this)
        scroller.addView(panel)
        setContentView(scroller)
        panel.addView(TvUi.label(this, "设备与网络诊断", 29f, true))
        panel.addView(TvUi.label(this, "仅供家长调试。不会复制订阅链接、账号令牌或观看历史。", 16f))
        TvUi.space(panel, this, 10)
        report = TvUi.label(this, "", 17f).apply {
            setTextColor(TvUi.text)
            setTextIsSelectable(true)
        }
        panel.addView(report)
        TvUi.space(panel, this, 12)
        panel.addView(TvUi.primaryButton(this, "检测 Google HTTPS 连通性") {
            if (ProxyStatus.phase == ProxyStatus.Phase.READY) {
                message.text = "检测已开始，通常几秒内显示结果…"
                ProxyNetworkHealth.check(this)
            } else {
                message.text = "请先在家长中心连接 VPN，再检测连通性"
            }
        })
        panel.addView(TvUi.button(this, "检查 Google OAuth / YouTube 真实 HTTPS 地址") {
            message.text = "正在分别检测 Google OAuth 与 YouTube API 的 HTTPS 可达性…"
            GoogleEndpointProbe.check { text ->
                if (!isFinishing && !isDestroyed) {
                    message.text = text
                }
            }
        })
        panel.addView(TvUi.button(this, "检查 YouTube API Key / OAuth 凭据（真实接口）") {
            if (credentialProbeInFlight) {
                message.text = "已有一次 API 检查在进行中"
            } else if (ProxyStatus.phase != ProxyStatus.Phase.READY) {
                message.text = "VPN 尚未启动，不会直连 Google"
            } else if (!store.hasYouTubeAuth) {
                message.text = "尚未配置 YouTube API Key 或 Google TV 登录"
            } else {
                val request = credentialProbeGate.begin()
                credentialProbeInFlight = true
                message.text = "正在检查 YouTube Data API v3 的真实认证接口…"
                Thread({
                    val outcome = runCatching {
                        YouTubeRepository(store).checkApiCredentials()
                    }
                    runOnUiThread {
                        credentialProbeInFlight = false
                        if (isFinishing || isDestroyed || !ParentSession.authorized() ||
                            !credentialProbeGate.isCurrent(request)) return@runOnUiThread
                        credentialProbeResult = outcome.fold(
                            { "成功：API 凭据与该接口可用（不代表 IFrame 播放成功）" },
                            { ApiProbeErrorPolicy.describe(it) }
                        )
                        message.text = credentialProbeResult
                        refresh()
                    }
                }, "kids-youtube-credential-probe").start()
            }
        })
        panel.addView(TvUi.button(this, "刷新诊断信息") { refresh() })
        panel.addView(TvUi.button(this, "复制不含密钥的诊断报告") {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("KidsTubeTV diagnostics", safeReport()))
            message.text = "已复制，可粘贴给开发者排查。"
        })
        panel.addView(TvUi.button(this, "返回网络设置") { finish() })
        message = TvUi.label(this, "", 16f).apply { setTextColor(TvUi.muted) }
        panel.addView(message)
        refresh()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(ProxyStatus.ACTION_CHANGED).apply {
            addAction(ProxyNetworkHealth.ACTION_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
    }

    override fun onStop() {
        credentialProbeGate.invalidate()
        unregisterReceiver(receiver)
        super.onStop()
    }

    private fun webViewVersion(): String = runCatching {
        if (Build.VERSION.SDK_INT >= 26) {
            WebView.getCurrentWebViewPackage()?.versionName ?: "未检测到 WebView 提供方"
        } else "不支持当前查询方式"
    }.getOrDefault("WebView 版本读取失败")

    private fun safeReport(): String = buildString {
        appendLine("KidsTubeTV · TV 诊断")
        appendLine("App 版本: " + runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrDefault("未知"))
        appendLine("Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
        appendLine("设备: " + Build.MANUFACTURER + " / " + Build.MODEL)
        appendLine("ABI: " + Build.SUPPORTED_ABIS.take(5).joinToString())
        appendLine("WebView: " + webViewVersion())
        appendLine("Mihomo 配置已导入: " + if (store.hasProfile) "是" else "否")
        appendLine("VPN: " + ProxyStatus.phase.name)
        appendLine("VPN 阶段 / 最后状态: " + ProxyStatus.detail)
        appendLine("VPN HTTPS 检测: " + ProxyNetworkHealth.state.name)
        appendLine("探测地址状态: " + ProxyNetworkHealth.diagnostic)
        appendLine("底层网络 DNS 数量: " + ProxyDnsStatus.underlyingCount +
            if (ProxyDnsStatus.underlyingCount == 0) " (VPN 可能无法解析域名)" else "")
        appendLine("Google / YouTube API 已配置: " + if (store.hasYouTubeAuth) "是" else "否")
        appendLine("YouTube API 凭据实测: " + credentialProbeResult)
        appendLine("家长白名单频道数: " + store.allowedChannels.size)
        appendLine("注意: HTTPS 成功不等于 DNS/IPv6 不泄漏；需进一步真机测试。")
    }

    private fun refresh() {
        if (::report.isInitialized) report.text = safeReport()
    }
}
