package dev.kidstubetv.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.TextView
import dev.kidstubetv.data.ParentSession
import dev.kidstubetv.data.SettingsStore
import java.io.ByteArrayOutputStream

/** PIN-gated settings: approved channels, YouTube API key, and user-provided Mihomo YAML. */
class SettingsActivity : Activity() {
    private val settings by lazy { SettingsStore(this) }
    private lateinit var api: EditText
    private lateinit var channels: EditText
    private lateinit var allowed: EditText
    private lateinit var blocked: EditText
    private lateinit var message: TextView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (settings.hasPin && !ParentSession.authorized()) {
            finish()
            return
        }
        val scroll = ScrollView(this)
        val body = TvUi.column(this)
        scroll.addView(body)
        setContentView(scroll)
        body.addView(TvUi.label(this, "家长设置", 29f, true))
        body.addView(TvUi.label(this, "内容只允许来自指定频道或视频，且通过儿童及可嵌入检查。", 16f))
        body.addView(TvUi.label(this, "YouTube Data API v3 Key", 17f))
        api = TvUi.input(this, "Google Cloud API Key", settings.apiKey)
        body.addView(api)
        TvUi.space(body, this)
        body.addView(TvUi.label(this, "批准频道 ID：每行一个 UC...，不支持频道名称", 17f))
        channels = TvUi.input(this, "UCxxxxxxxxxxxxxxxxxxxxxx", settings.channelsText, true)
        body.addView(channels)
        TvUi.space(body, this)
        body.addView(TvUi.label(this, "精确批准的视频 ID（可选）", 17f))
        allowed = TvUi.input(this, "每行一个 11 字符视频 ID", settings.allowedVideosText, true)
        body.addView(allowed)
        TvUi.space(body, this)
        body.addView(TvUi.label(this, "禁止视频 ID（禁止优先）", 17f))
        blocked = TvUi.input(this, "每行一个视频 ID", settings.blockedVideosText, true)
        body.addView(blocked)
        TvUi.space(body, this)

        message = TvUi.label(this, profileMessage(), 16f)
        body.addView(message)
        body.addView(TvUi.button(this, "从 U 盘导入 Mihomo YAML") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }, REQUEST_PROFILE)
        })
        TvUi.space(body, this)
        body.addView(TvUi.button(this, "保存设置") {
            settings.apiKey = api.text.toString()
            settings.channelsText = channels.text.toString()
            settings.allowedVideosText = allowed.text.toString()
            settings.blockedVideosText = blocked.text.toString()
            message.text = if (settings.ready) "已保存：返回首页可连接代理" else
                "已保存；还需配置 PIN、有效白名单、API Key 或完整的代理 YAML"
        })
        TvUi.space(body, this)
        body.addView(TvUi.button(this, "设置 / 修改家长 PIN") { pinDialog(false) })
        TvUi.space(body, this)
        body.addView(TvUi.button(this, "返回首页") { finish() })
        if (!settings.hasPin) pinDialog(true)
    }

    private fun profileMessage() = if (settings.hasProfile) "已导入 Mihomo YAML（保存在应用私有目录）"
        else "尚未导入 Mihomo 配置。未配置完成时不发送任何 YouTube 请求。"

    private fun pinDialog(firstRun: Boolean) {
        val field = EditText(this).apply {
            hint = "至少四位数字"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (firstRun) "初次设置家长 PIN" else "修改家长 PIN")
            .setView(field)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消") { _, _ -> if (firstRun) finish() }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                runCatching { settings.setPin(field.text.toString()) }
                    .onSuccess {
                        ParentSession.unlock()
                        message.text = "家长 PIN 已保存"
                        dialog.dismiss()
                    }.onFailure { field.error = it.message }
            }
        }
        dialog.show()
    }

    @Deprecated("Storage Access Framework callback for Android TV")
    override fun onActivityResult(code: Int, result: Int, data: Intent?) {
        super.onActivityResult(code, result, data)
        if (code != REQUEST_PROFILE || result != RESULT_OK) return
        val uri = data?.data ?: return
        val saved = runCatching {
            val stream = contentResolver.openInputStream(uri) ?: error("无法读取文件")
            val output = ByteArrayOutputStream()
            stream.use { source ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = source.read(buffer)
                    if (count == -1) break
                    require(output.size() + count <= 1024 * 1024) { "YAML 文件超过 1 MB" }
                    output.write(buffer, 0, count)
                }
            }
            settings.writeProfile(output.toString(Charsets.UTF_8.name()))
        }
        message.text = saved.fold({ profileMessage() }, { "导入失败：" + (it.message ?: "") })
    }

    override fun onStop() {
        super.onStop()
        // The in-memory unlock ticket expires independently after two minutes.
    }

    companion object { private const val REQUEST_PROFILE = 918 }
}
