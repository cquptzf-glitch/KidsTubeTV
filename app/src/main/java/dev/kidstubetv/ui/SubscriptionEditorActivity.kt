package dev.kidstubetv.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.kidstubetv.data.ClipboardSubscriptionPolicy
import dev.kidstubetv.data.ParentSession
import dev.kidstubetv.data.SettingsStore
import dev.kidstubetv.data.SubscriptionFormat

/**
 * One focused subscription editor for TV D-pad and phone touch.
 * Only the parent session can enter; the URL is never placed in Intent extras.
 * SettingsActivity owns the asynchronous download after RESULT_OK.
 */
class SubscriptionEditorActivity : Activity() {
    private val store by lazy { SettingsStore(this) }
    private val handheld by lazy { UiDevice.isHandheld(this) }
    private lateinit var editor: EditText
    private lateinit var feedback: TextView

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

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (!store.hasPin || !ParentSession.authorized()) { finish(); return }
        UiDevice.showSystemBarsForHandheld(this)
        // Subscription URLs can contain bearer credentials; keep them out of
        // system screenshot previews and screen-recording tools.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        if (!ParentSession.authorized()) finish()
    }

    private fun buildScreen() {
        val frame = FrameLayout(this).apply { setBackgroundColor(TvUi.bg) }
        setContentView(frame)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        frame.addView(column, FrameLayout.LayoutParams(
            if (handheld) -1 else TvUi.dp(this, 780), -1, Gravity.CENTER_HORIZONTAL
        ))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        column.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val body = TvUi.column(this)
        scroll.addView(body)

        val back = TvUi.button(this, "‹ 返回网络设置") { finish() }
        body.addView(back, LinearLayout.LayoutParams(-2, TvUi.dp(this, if (handheld) 48 else 62)))
        TvUi.space(body, this, if (handheld) 16 else 24)

        body.addView(TvUi.label(this, "设置网络订阅", if (handheld) 26f else 32f, true).apply {
            setTypeface(typeface, Typeface.BOLD)
        })
        body.addView(TvUi.label(this,
            "只需一个 HTTPS Clash / Mihomo YAML 链接。成功下载后，KidsTubeTV 可以自动连接应用专用 VPN。",
            if (handheld) 15f else 18f).apply { setTextColor(TvUi.muted) })
        TvUi.space(body, this, 22)

        body.addView(TvUi.label(this, "订阅地址", if (handheld) 17f else 21f, true))
        editor = TvUi.input(this, "https://example.com/clash?token=...", store.subscriptionUrl).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setSelectAllOnFocus(false)
            isLongClickable = true
            contentDescription = "订阅完整地址，可编辑、复制和粘贴"
            minHeight = TvUi.dp(this@SubscriptionEditorActivity, if (handheld) 56 else 70)
        }
        body.addView(editor, LinearLayout.LayoutParams(-1, -2))
        feedback = TvUi.label(this,
            "链接可能包含访问令牌。只在家长界面显示，不会写入日志或传给其它页面。",
            if (handheld) 13f else 15f).apply { setTextColor(TvUi.muted) }
        body.addView(feedback)
        TvUi.space(body, this, 16)

        val helpers = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        body.addView(helpers)
        helpers.addView(TvUi.button(this, "粘贴链接") { pasteFromClipboard() },
            LinearLayout.LayoutParams(0, TvUi.dp(this, if (handheld) 54 else 68), 1f))
        helpers.addView(TvUi.button(this, "复制链接") { copyToClipboard() },
            LinearLayout.LayoutParams(0, TvUi.dp(this, if (handheld) 54 else 68), 1f).apply {
                leftMargin = TvUi.dp(this@SubscriptionEditorActivity, 10)
            })
        TvUi.space(body, this, 12)
        body.addView(TvUi.button(this, "清空输入") {
            editor.setText("")
            editor.requestFocus()
            feedback.text = "已清空输入；原订阅仍保留，直到保存新的地址。"
        }, LinearLayout.LayoutParams(-2, TvUi.dp(this, if (handheld) 48 else 62)))

        TvUi.space(body, this, 22)
        body.addView(TvUi.label(this,
            "下载失败时会保留之前可用的 YAML。首次下载需要电视或手机能够访问订阅服务；不可访问时可在更多设置中从文件导入。",
            if (handheld) 14f else 16f).apply { setTextColor(TvUi.muted) })

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(TvUi.bg)
            val pad = if (handheld) 14 else 26
            setPadding(TvUi.dp(this@SubscriptionEditorActivity, pad),
                TvUi.dp(this@SubscriptionEditorActivity, 8),
                TvUi.dp(this@SubscriptionEditorActivity, pad),
                TvUi.dp(this@SubscriptionEditorActivity, if (handheld) 14 else 20))
        }
        column.addView(footer, LinearLayout.LayoutParams(-1, -2))
        val save = TvUi.primaryButton(this, "保存并下载订阅") { saveAndClose() }
        footer.addView(save, LinearLayout.LayoutParams(-1, TvUi.dp(this, if (handheld) 56 else 72)))

        // TV enters on a predictable target instead of opening the keyboard.
        if (!handheld) back.requestFocus()
        else back.isFocusableInTouchMode = false
    }

    private fun pasteFromClipboard() {
        val text = runCatching {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        }.getOrDefault("")
        val candidate = ClipboardSubscriptionPolicy.candidate(text, editor.text.toString())
        if (candidate == null) {
            editor.error = "剪贴板中没有可用的 HTTPS 订阅链接"
        } else {
            editor.setText(candidate)
            editor.setSelection(editor.text.length)
            feedback.text = "已粘贴链接。点击下方按钮保存并下载。"
        }
    }

    private fun copyToClipboard() {
        val value = editor.text.toString().trim()
        if (value.isEmpty()) { editor.error = "没有可以复制的链接"; return }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("KidsTubeTV subscription", value)
        if (Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        feedback.text = "已复制。订阅令牌可能留在系统剪贴板历史中，请妥善保管。"
    }

    private fun saveAndClose() {
        if (!ParentSession.authorized()) { finish(); return }
        val url = runCatching { SubscriptionFormat.normalizeUrl(editor.text.toString()) }
            .getOrElse {
                editor.error = "请输入公开域名的完整 HTTPS Clash/Mihomo 订阅链接"
                editor.requestFocus()
                return
            }
        try {
            val changed = store.subscriptionUrl != url
            store.subscriptionUrl = url
            if (changed) store.subscriptionUpdatedAt = 0L
            // Do not pass the URL through Intent extras; SettingsActivity
            // re-reads the encrypted value from SettingsStore on return.
            setResult(RESULT_OK, Intent().putExtra(EXTRA_PROVIDER_CHANGED, changed))
            finish()
        } catch (_: Exception) {
            editor.error = "无法保存订阅，请检查 Android 安全存储"
        }
    }

    companion object {
        const val EXTRA_PROVIDER_CHANGED = "provider_changed"
    }
}
