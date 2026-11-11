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
        val scroller = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val panel = TvUi.column(this)
        scroller.addView(panel)
        setContentView(scroller)
        panel.addView(TvUi.label(this, "一键诊断",
            if (UiDevice.isHandheld(this)) 24f else 29f, true))
        panel.addView(TvUi.label(this, "先检测 VPN 和 Google 接口，再检查账号凭据。", 16f))
        TvUi.space(panel, this, 10)
        panel.addView(TvUi.primaryButton(this, "▶ 开始一键检测") {
            runQuickDiagnostic()
        })
        message = TvUi.label(this, "点击开始检测；不会上传密钥或观看记录。", 16f)
        panel.addView(message)
        TvUi.space(panel, this, 16)
        panel.addView(TvUi.button(this, "展开详细诊断 ▾") {
            advanced.visibility = if (advanced.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        })
        advanced = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        panel.addView(advanced)
        report = TvUi.label(this, "", 15f).apply {
            setTextColor(TvUi.text)
            setTextIsSelectable(true)
        }
        advanced.addView(report)
        TvUi.space(advanced, this, 12)
        advanced.addView(TvUi.button(this, "仅检测 Google HTTPS 连通性") {
            if (ProxyStatus.phase == ProxyStatus.Phase.READY) {
                message.text = "检测已开始，通常几秒内显示结果…"
                ProxyNetworkHealth.check(this)
            } else {
                message.text = "请先在家长中心连接 VPN，再检测连通性"
            }
        })
        advanced.addView(TvUi.button(this, "检查 Google OAuth / YouTube HTTPS 地址") {
            message.text = "正在分别检测 Google OAuth 与 YouTube API 的 HTTPS 可达性…"
            GoogleEndpointProbe.check { text ->
                if (!isFinishing && !isDestroyed) {
                    message.text = text
                }
            }
        })
        advanced.addView(TvUi.button(this, "仅检查 YouTube API 凭据") {
            checkApiCredential()
        })
        advanced.addView(TvUi.button(this, "刷新诊断信息") { refresh() })
        panel.addView(TvUi.button(this, "复制不含密钥的诊断报告") {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("KidsTubeTV diagnostics", safeReport()))
            message.text = "已复制，可粘贴给开发者排查。"
        })
        panel.addView(TvUi.button(this, "返回网络设置") { finish() })
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
        GoogleEndpointProbe.check { https ->
            if (isFinishing || isDestroyed || !ParentSession.authorized()) return@check
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
                credentialProbeInFlight = false
                if (isFinishing || isDestroyed || !ParentSession.authorized() ||
                    !credentialProbeGate.isCurrent(request)) return@runOnUiThread
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
