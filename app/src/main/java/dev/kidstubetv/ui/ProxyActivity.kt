package dev.kidstubetv.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import dev.kidstubetv.data.ParentSession
import dev.kidstubetv.data.SettingsStore
import dev.kidstubetv.proxy.MihomoControl
import dev.kidstubetv.proxy.ProxyGroup
import dev.kidstubetv.proxy.ProxyStatus

/**
 * Optional selector override. The subscription's default routing strategy is
 * used unless a parent explicitly changes a selector. No Clash rules editor.
 */
class ProxyActivity : Activity() {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && !ParentSession.onInteraction()) {
            finish()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (!ParentSession.onInteraction()) finish()
    }

    private lateinit var state: TextView
    private lateinit var groups: LinearLayout
    private lateinit var advanced: LinearLayout
    private var knownGroups = emptyList<ProxyGroup>()
    private val groupRequests = LatestResultGate()
    private val selectionRequests = LatestResultGate()
    private var reloadOnResume = false

    override fun onResume() {
        super.onResume()
        if (!ParentSession.authorized()) {
            finish()
            return
        }
        if (reloadOnResume && ::groups.isInitialized) {
            reloadOnResume = false
            load()
        }
    }

    override fun onStop() {
        // On return, re-query after invalidating background callbacks.
        reloadOnResume = true
        // Results from a previous page lifetime must never redraw a new list.
        groupRequests.invalidate()
        selectionRequests.invalidate()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiDevice.showSystemBarsForHandheld(this)
        if (!SettingsStore(this).hasPin || !ParentSession.authorized()) {
            finish()
            return
        }
        val page = SettingsDetailPage(
            this, "选择连接节点",
            "一般使用订阅默认策略即可。只有连接慢或无法访问时，才需要手动选择节点。"
        )
        state = page.status("准备读取可选节点…")
        val refresh = page.action("刷新可用节点", "重新读取当前订阅的代理策略组", true) {
            load()
        }
        page.heading("当前策略组")
        groups = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.body.addView(groups, LinearLayout.LayoutParams(-1, -2))
        page.divider()

        advanced = page.collapsible("测速与流量信息", "排查连接慢或订阅节点不可用")
        fun advancedAction(title: String, hint: String, run: () -> Unit) {
            advanced.addView(SettingsDecor.action(this, title, hint, false, run),
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = TvUi.dp(this@ProxyActivity, 10)
                })
        }
        advancedAction("查看当前流量", "仅显示 Mihomo 内核统计，不改变网络设置") {
            if (ProxyStatus.phase != ProxyStatus.Phase.READY) {
                state.text = "请先连接应用网络"
            } else {
                val traffic = MihomoControl.traffic().take(240)
                state.text = if (traffic.isBlank()) "尚无流量数据" else "流量：$traffic"
            }
        }
        advancedAction("测速当前节点", "检测第一个可用策略组的当前节点延迟") {
            val node = knownGroups.firstOrNull()?.selected
            if (node.isNullOrBlank() || ProxyStatus.phase != ProxyStatus.Phase.READY) {
                state.text = "暂无可测速节点，请先连接应用网络"
            } else {
                state.text = "正在测速当前节点…"
                val ticket = selectionRequests.begin()
                MihomoControl.testNode(node) { result ->
                    if (isFinishing || isDestroyed || !ParentSession.authorized() ||
                        !selectionRequests.isCurrent(ticket)) return@testNode
                    state.text = result.fold(
                        { "当前节点延迟：$it ms" },
                        { "测速失败，请检查当前节点或网络" }
                    )
                }
            }
        }
        page.focusPrimary(refresh)
        load()
    }

    private fun load() {
        val request = groupRequests.begin()
        groups.removeAllViews()
        knownGroups = emptyList()
        if (ProxyStatus.phase != ProxyStatus.Phase.READY) {
            state.text = "应用网络尚未连接。返回网络设置连接后再选择节点。"
            return
        }
        state.text = "正在读取代理策略组…"
        MihomoControl.queryGroups { response ->
            if (isFinishing || isDestroyed || !ParentSession.authorized() ||
                !groupRequests.isCurrent(request)) return@queryGroups
            response.onSuccess { items ->
                knownGroups = items
                state.text = if (items.isEmpty()) {
                    "当前订阅没有需要手动选择的策略组，继续使用默认配置即可。"
                } else {
                    "可选择以下策略组。只会切换本应用的代理，不影响其他应用。"
                }
                renderGroups(items)
            }.onFailure {
                state.text = "无法获取节点。请检查应用网络是否连接并尝试刷新。"
            }
        }
    }

    private fun renderGroups(items: List<ProxyGroup>) {
        groups.removeAllViews()
        items.forEach { group ->
            val card = SettingsDecor.action(
                this,
                "◈  " + group.name,
                "当前：" + group.selected + "  ·  点击选择节点",
                false
            ) {
                if (group.nodes.isEmpty()) return@action
                AlertDialog.Builder(this)
                    .setTitle("为 " + group.name + " 选择节点")
                    .setSingleChoiceItems(
                        group.nodes.toTypedArray(), group.nodes.indexOf(group.selected)
                    ) { dialog, index ->
                        val selected = group.nodes[index]
                        dialog.dismiss()
                        state.text = "正在切换节点…"
                        val ticket = selectionRequests.begin()
                        MihomoControl.select(group.name, selected) { result ->
                            if (isFinishing || isDestroyed || !ParentSession.authorized() ||
                                !selectionRequests.isCurrent(ticket)) return@select
                            result.onSuccess {
                                runCatching {
                                    SettingsStore(this@ProxyActivity)
                                        .rememberProxySelection(group.name, selected)
                                }.onFailure {
                                    state.text = "节点已切换，但无法保存下次启动的选择"
                                }
                                load()
                            }.onFailure {
                                state.text = "节点切换失败，请检查策略组和 VPN 状态"
                            }
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            groups.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = TvUi.dp(this@ProxyActivity, 9)
            })
        }
    }
}
