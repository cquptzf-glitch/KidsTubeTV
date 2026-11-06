package dev.kidstubetv.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.kidstubetv.data.ParentSession
import dev.kidstubetv.data.SettingsStore
import dev.kidstubetv.proxy.KidsVpnService
import dev.kidstubetv.proxy.MihomoControl
import dev.kidstubetv.proxy.ProxyGroup
import dev.kidstubetv.proxy.ProxyStatus

/**
 * One remote action per Mihomo selector. Diagnostics are behind "advanced",
 * not part of the child's everyday viewing navigation.
 */
class ProxyActivity : Activity() {
    private lateinit var content: LinearLayout
    private lateinit var state: TextView
    private lateinit var groups: LinearLayout
    private lateinit var advanced: LinearLayout
    private var knownGroups = emptyList<ProxyGroup>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SettingsStore(this).hasPin || !ParentSession.authorized()) { finish(); return }
        val page = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        content = TvUi.column(this)
        page.addView(content)
        setContentView(page)

        content.addView(TvUi.label(this, "选择代理节点", 30f, true))
        content.addView(TvUi.label(this, "通常无需选择节点，可直接使用订阅默认的自动策略。", 17f)
            .apply { setTextColor(TvUi.muted) })
        state = TvUi.label(this, "", 19f)
        content.addView(state)
        TvUi.space(content, this, 14)
        groups = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(groups)
        TvUi.space(content, this, 13)
        content.addView(TvUi.button(this, "刷新节点") { load() })
        content.addView(TvUi.button(this, "高级诊断  ▾") {
            advanced.visibility = if (advanced.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        })
        advanced = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        content.addView(advanced)
        advanced.addView(TvUi.button(this, "查看流量统计") {
            state.text = "流量：" + MihomoControl.traffic().take(280)
        })
        advanced.addView(TvUi.button(this, "测速当前节点") {
            val group = knownGroups.firstOrNull() ?: return@button
            state.text = "检测节点：" + group.selected
            MihomoControl.testNode(group.selected) { result ->
                if (isFinishing || isDestroyed) return@testNode
                state.text = result.fold(
                    { "测速结果：" + it + " ms" },
                    { "测速失败：" + (it.message ?: "未知错误") }
                )
            }
        })
        advanced.addView(TvUi.button(this, "断开 VPN") {
            KidsVpnService.stop(this)
            state.text = "正在关闭代理"
        })
        content.addView(TvUi.button(this, "返回网络设置") { finish() })
        load()
    }

    private fun load() {
        groups.removeAllViews()
        if (ProxyStatus.phase != ProxyStatus.Phase.READY) {
            state.text = "VPN 尚未连接，请返回儿童主页等待自动连接。"
            return
        }
        state.text = "正在获取可选节点…"
        MihomoControl.queryGroups { response ->
            if (isFinishing || isDestroyed) return@queryGroups
            response.onSuccess { items ->
                knownGroups = items
                state.text = if (items.isEmpty()) "当前订阅没有手动可选的策略组，仍可使用自动模式。"
                    else "选择一个节点即可切换，不需要修改 YAML。"
                renderGroups(items)
            }.onFailure { error ->
                state.text = "无法读取节点：" + (error.message ?: "未知错误")
            }
        }
    }

    private fun renderGroups(items: List<ProxyGroup>) {
        groups.removeAllViews()
        items.forEach { group ->
            groups.addView(TvUi.primaryButton(this,
                "◈  " + group.name + "       " + group.selected) {
                if (group.nodes.isEmpty()) return@primaryButton
                AlertDialog.Builder(this)
                    .setTitle("选择 " + group.name + " 的节点")
                    .setSingleChoiceItems(group.nodes.toTypedArray(),
                        group.nodes.indexOf(group.selected)) { dialog, index ->
                        val selected = group.nodes[index]
                        dialog.dismiss()
                        state.text = "正在切换：" + selected
                        MihomoControl.select(group.name, selected) { result ->
                            if (isFinishing || isDestroyed) return@select
                            result.onSuccess { load() }
                                .onFailure { state.text = "切换失败：" + (it.message ?: "未知错误") }
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            })
            TvUi.space(groups, this, 11)
        }
    }
}
