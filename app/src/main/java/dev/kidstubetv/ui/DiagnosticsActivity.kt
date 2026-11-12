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
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.webkit.WebView
import dev.kidstubetv.data.ParentSession
import dev.kidstubetv.data.SettingsStore
import dev.kidstubetv.data.YouTubeRepository
import dev.kidstubetv.data.ApiProbeErrorPolicy
import dev.kidstubetv.proxy.ProxyNetworkHealth
import dev.kidstubetv.proxy.ProxyStatus
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
    private lateinit var advanced: LinearLayout
    private val credentialProbeGate = LatestResultGate()
    private val diagnosticRequests = LatestResultGate()
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
        UiDevice.showSystemBarsForHandheld(this)
        if (!store.hasPin || !ParentSession.authorized()) { finish(); return }
        val page = SettingsDetailPage(
            this, "网络与账号诊断",
            "检测分为 VPN、Google HTTPS 和 YouTube 凭据三步。不会上传订阅密钥、家长 PIN 或观看记录。"
        )
        val phone = UiDevice.isHandheld(this)
        val quick = page.action("开始一键检测",
            "检查应用专用 VPN、Google HTTPS 和 YouTube API 登录状态", primary = true) {
            runQuickDiagnostic()
        }
        message = page.status("尚未检测。网络异常不等于账号错误，可逐项排查。")

        advanced = page.collapsible("详细诊断与单项测试", "展开设备信息、网络探测和 API 凭据检查")
        report = TvUi.label(this, "", if (phone) 13f else 15f).apply {
            setTextColor(TvUi.text)
            setTextIsSelectable(true)
        }
        advanced.addView(report, LinearLayout.LayoutParams(-1, -2))
        TvUi.space(advanced, this, 12)
        fun advancedAction(title: String, hint: String, callback: () -> Unit) {
            advanced.addView(SettingsDecor.action(this, title, hint, false, callback),
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = TvUi.dp(this@DiagnosticsActivity, if (phone) 8 else 12)
                })
        }
        advancedAction("仅检查 Google HTTPS",
            "不读取账号或订阅链接；判断网络可达性") {
            if (ProxyStatus.phase == ProxyStatus.Phase.READY) {
                message.text = "正在检测 Google HTTPS…"
                ProxyNetworkHealth.check(this)
            } else {
                message.text = "请先连接应用网络；不会回退直连。"
            }
        }
        advancedAction("检查 OAuth / YouTube 服务地址",
            "分别检测 Google OAuth 和 YouTube Data API 的 HTTPS 入口") {
            val ticket = diagnosticRequests.begin()
            message.text = "正在检测 Google OAuth 和 YouTube 的 HTTPS 连通性…"
            GoogleEndpointProbe.check { answer ->
                if (!isFinishing && !isDestroyed && ParentSession.authorized() &&
                    diagnosticRequests.isCurrent(ticket)) message.text = answer
            }
        }
        advancedAction("验证 YouTube API 凭据",
            "使用真实 YouTube API；只有 VPN 已连接时才会请求") {
            checkApiCredential()
        }
        advancedAction("刷新设备诊断", "重新获取当前系统与 VPN 状态") { refresh() }

        page.divider()
        page.action("复制脱敏诊断报告",
            "仅复制系统版本、网络状态和安全的错误类别") {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("KidsTubeTV diagnostics", safeReport()))
            message.text = "脱敏诊断已复制。分享前仍请检查内容是否包含设备隐私。"
        }
        page.focusPrimary(quick)
        refresh()
    }

    private fun runQuickDiagnostic() {
        if (ProxyStatus.phase != ProxyStatus.Phase.READY) {
            message.text = "✖ VPN 未连接：请返回家长设置，先添加 Clash 订阅并连接 VPN"
            refresh()
            return
        }
        message.text = "① VPN 已启动，检测 Google OAuth 与 YouTube HTTPS…"
        ProxyNetworkHealth.check(this)
        val ticket = diagnosticRequests.begin()
        GoogleEndpointProbe.check { https ->
            if (isFinishing || isDestroyed || !ParentSession.authorized() ||
                !diagnosticRequests.isCurrent(ticket)) return@check
            if (ProxyStatus.phase != ProxyStatus.Phase.READY) {
                message.text = "检测期间 VPN 断开；请重新连接后再试"
                return@check
            }
            if (!store.hasYouTubeAuth) {
                message.text = "① HTTPS 检查：\n" + https +
                    "\n② 未配置 YouTube API Key / Google OAuth，先完成家长设置"
                refresh()
                return@check
            }
            message.text = "① HTTPS 检查：\n" + https + "\n② 正在验证 YouTube API 凭据…"
            checkApiCredential(prefix = "① HTTPS 检查：\n" + https + "\n② ")
        }
    }

    private fun checkApiCredential(prefix: String = "") {
        if (credentialProbeInFlight) {
            message.text = "已有一次 API 检查在进行中"
            return
        }
        if (ProxyStatus.phase != ProxyStatus.Phase.READY) {
            message.text = "VPN 尚未启动，不会直连 Google"
            return
        }
        if (!store.hasYouTubeAuth) {
            message.text = "尚未配置 YouTube API Key 或 Google TV 登录"
            return
        }
        val request = credentialProbeGate.begin()
        credentialProbeInFlight = true
        message.text = prefix + "正在检查 YouTube Data API v3 的真实认证接口…"
        Thread({
            val outcome = runCatching {
                YouTubeRepository(store).checkApiCredentials()
            }
            runOnUiThread {
                if (isFinishing || isDestroyed || !ParentSession.authorized() ||
                    !credentialProbeGate.isCurrent(request)) return@runOnUiThread
                credentialProbeInFlight = false
                credentialProbeResult = outcome.fold(
                    { "成功：API 凭据与该接口可用（不代表 IFrame 播放成功）" },
                    { ApiProbeErrorPolicy.describe(it) }
                )
                message.text = prefix + credentialProbeResult
                refresh()
            }
        }, "kids-youtube-credential-probe").start()
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
        diagnosticRequests.invalidate()
        // A stopped screen must not stay permanently busy when reopened.
        credentialProbeInFlight = false
        unregisterReceiver(receiver)
        super.onStop()
    }

    private fun webViewVersion(): String = runCatching {
        if (Build.VERSION.SDK_INT >= 26) {
            WebView.getCurrentWebViewPackage()?.versionName ?: "未检测到 WebView 提供方"
        } else "不支持当前查询方式"
    }.getOrDefault("WebView 版本读取失败")

    private fun safeReport(): String = buildString {
        appendLine("KidsTubeTV · 手机/电视诊断")
        appendLine("App 版本: " + runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrDefault("未知"))
        appendLine("已安装包名: " + packageName)
        appendLine("安装渠道: " + (if (packageName.contains(".preview."))
            "独立测试版（无法覆盖其他 beta；允许与旧版并存）"
        else if (packageName.endsWith(".preview"))
            "固定密钥测试版（可使用相同签名升级）"
        else "标准包名（升级必须使用同一签名证书）"))
        appendLine("Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
        appendLine("设备: " + Build.MANUFACTURER + " / " + Build.MODEL)
        appendLine("ABI: " + Build.SUPPORTED_ABIS.take(5).joinToString())
        appendLine("WebView: " + webViewVersion())
        appendLine("Mihomo 配置已导入: " + if (store.hasProfile) "是" else "否")
        appendLine("VPN: " + ProxyStatus.phase.name)
        appendLine("VPN 阶段 / 最后状态: " + ProxyStatus.detail)
        appendLine("VPN HTTPS 检测: " + ProxyNetworkHealth.state.name)
        appendLine("探测地址状态: " + ProxyNetworkHealth.diagnostic)
        appendLine("Google / YouTube API 已配置: " + if (store.hasYouTubeAuth) "是" else "否")
        appendLine("YouTube API 凭据实测: " + credentialProbeResult)
        appendLine("家长白名单频道数: " + store.allowedChannels.size)
        appendLine("注意: HTTPS 成功不等于 DNS/IPv6 不泄漏；需进一步真机测试。")
    }

    private fun refresh() {
        if (::report.isInitialized) report.text = safeReport()
    }
}
