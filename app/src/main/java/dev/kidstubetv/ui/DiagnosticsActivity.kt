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
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.webkit.WebView
import dev.kidstubetv.data.ParentSession
import dev.kidstubetv.data.SettingsStore
import dev.kidstubetv.proxy.ProxyNetworkHealth
import dev.kidstubetv.proxy.ProxyStatus

/**
 * Troubleshooting for real Android 9 televisions. No credentials, subscription
 * URLs, profile contents, access tokens or video history appear in the report.
 */
class DiagnosticsActivity : Activity() {
    /** Every remote action is gated by the recent parent PIN session. */
    override fun onUserInteraction() {
        super.onUserInteraction()
        if (!ParentSession.onInteraction()) finish()
    }

    private val store by lazy { SettingsStore(this) }
    private lateinit var report: TextView
    private lateinit var message: TextView

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
                message.text = "需先从儿童主页连接 VPN，再检查连通性"
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
        appendLine("VPN HTTPS 检测: " + ProxyNetworkHealth.state.name)
        appendLine("Google / YouTube API 已配置: " + if (store.hasYouTubeAuth) "是" else "否")
        appendLine("家长白名单频道数: " + store.allowedChannels.size)
        appendLine("注意: HTTPS 成功不等于 DNS/IPv6 不泄漏；需进一步真机测试。")
    }

    private fun refresh() {
        if (::report.isInitialized) report.text = safeReport()
    }
}
