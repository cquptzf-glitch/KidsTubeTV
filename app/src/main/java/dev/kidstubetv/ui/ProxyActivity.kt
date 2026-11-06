package dev.kidstubetv.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.kidstubetv.data.ParentSession
import dev.kidstubetv.data.SettingsStore
import dev.kidstubetv.proxy.KidsVpnService
import dev.kidstubetv.proxy.MihomoControl
import dev.kidstubetv.proxy.ProxyGroup
import dev.kidstubetv.proxy.ProxyStatus

/** Remote-friendly proxy group selection, latency probe, and traffic inspector. */
class ProxyActivity : Activity() {
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var groupsArea: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SettingsStore(this).hasPin || !ParentSession.authorized()) { finish(); return }
        val scroller = ScrollView(this)
        content = TvUi.column(this)
        scroller.addView(content)
        setContentView(scroller)
        content.addView(TvUi.label(this, "Mihomo 节点管理", 29f, true))
        status = TvUi.label(this, "读取运行状态…", 17f)
        content.addView(status)
        content.addView(TvUi.button(this, "刷新策略组") { loadGroups() })
        content.addView(TvUi.button(this, "查看实时流量") {
            status.text = "流量：" + MihomoControl.traffic().take(500)
        })
        content.addView(TvUi.button(this, "断开 VPN") {
            KidsVpnService.stop(this)
            status.text = "正在断开 VPN"
        })
        content.addView(TvUi.button(this, "返回家长设置") { finish() })
        TvUi.space(content, this, 16)
        groupsArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(groupsArea)
        loadGroups()
    }

    private fun loadGroups() {
        groupsArea.removeAllViews()
        if (ProxyStatus.phase != ProxyStatus.Phase.READY) {
            status.text = "当前 VPN 未运行。返回首页连接后再选择节点。"
            return
        }
        status.text = "正在读取策略组…"
        MihomoControl.queryGroups { result ->
            if (isFinishing) return@queryGroups
            result.onSuccess { groups ->
                status.text = if (groups.isEmpty()) "没有可以手动切换的 Selector 策略组"
                    else "已加载 " + groups.size + " 个策略组"
                showGroups(groups)
            }.onFailure { status.text = "节点读取失败：" + (it.message ?: "未知错误") }
        }
    }

    private fun showGroups(groups: List<ProxyGroup>) {
        groupsArea.removeAllViews()
        groups.forEach { group ->
            groupsArea.addView(TvUi.label(this, group.name + " · 当前：" + group.selected, 19f, true))
            groupsArea.addView(TvUi.button(this, "选择节点") {
                val names = group.nodes.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle(group.name + " / 选择节点")
                    .setItems(names) { _, i ->
                        MihomoControl.select(group.name, names[i]) { result ->
                            if (!isFinishing) {
                                status.text = result.fold(
                                    { "已切换为：" + names[i] },
                                    { "切换失败：" + (it.message ?: "未知错误") }
                                )
                                if (result.isSuccess) loadGroups()
                            }
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            })
            groupsArea.addView(TvUi.button(this, "测速当前节点：" + group.selected) {
                status.text = "正在测试：" + group.selected
                MihomoControl.testNode(group.selected) { result ->
                    if (!isFinishing) status.text = result.fold(
                        { "节点延迟：" + it + " ms（-1 表示不可用）" },
                        { "测速失败：" + (it.message ?: "未知错误") }
                    )
                }
            })
            TvUi.space(groupsArea, this, 12)
        }
    }
}
