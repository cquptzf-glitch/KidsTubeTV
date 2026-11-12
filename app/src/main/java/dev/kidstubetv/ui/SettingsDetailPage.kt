package dev.kidstubetv.ui

import android.app.Activity
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Shared responsive detail-page shell for parent settings.
 *
 * TV: centered 880dp content, clear focus outlines, predictable Back target.
 * Phone: full-width vertical scrolling with an always-visible return header.
 * No credentials or URLs are held by this component.
 */
internal class SettingsDetailPage(
    private val activity: Activity,
    title: String,
    subtitle: String
) {
    private val handheld = UiDevice.isHandheld(activity)
    val body: LinearLayout
    val back: Button
    private val scroll: ScrollView

    init {
        val root = FrameLayout(activity).apply { setBackgroundColor(TvUi.bg) }
        activity.setContentView(root)
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(TvUi.bg)
        }
        val widthDp = activity.resources.configuration.screenWidthDp.takeIf { it > 0 }
            ?: (activity.resources.displayMetrics.widthPixels /
                activity.resources.displayMetrics.density).toInt()
        val maxContentDp = SettingsDetailLayoutPolicy.contentWidthDp(widthDp, handheld)
        val width = if (maxContentDp == null) -1 else TvUi.dp(activity, maxContentDp)
        root.addView(column, FrameLayout.LayoutParams(
            width, -1, Gravity.CENTER_HORIZONTAL
        ))

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(TvUi.dp(activity, if (handheld) 14 else 24),
                TvUi.dp(activity, if (handheld) 8 else 16),
                TvUi.dp(activity, if (handheld) 14 else 24),
                TvUi.dp(activity, 5))
        }
        column.addView(header, LinearLayout.LayoutParams(-1, -2))
        back = TvUi.button(activity, "‹ 返回设置") { activity.finish() }.apply {
            textSize = if (handheld) 15f else 18f
            contentDescription = "返回家长设置"
        }
        header.addView(back, LinearLayout.LayoutParams(
            TvUi.dp(activity, if (handheld) 110 else 156),
            TvUi.dp(activity, if (handheld) 48 else 64)
        ).apply { rightMargin = TvUi.dp(activity, if (handheld) 9 else 16) })
        val headerTitle = TvUi.label(activity, title,
            if (handheld) 22f else 30f, true).apply {
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 2
        }
        header.addView(headerTitle, LinearLayout.LayoutParams(0, -2, 1f))

        scroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            clipToPadding = false
        }
        column.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        body = TvUi.column(activity)
        scroll.addView(body)
        body.addView(TvUi.label(activity, subtitle, if (handheld) 14f else 17f).apply {
            setTextColor(TvUi.muted)
        })
        TvUi.space(body, activity, if (handheld) 14 else 24)
    }

    fun action(
        title: String,
        subtitle: String,
        primary: Boolean = false,
        onClick: () -> Unit
    ): SettingsActionTile {
        val tile = SettingsDecor.action(activity, title, subtitle, primary, onClick)
        body.addView(tile, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = TvUi.dp(activity, if (handheld) 8 else 12)
        })
        return tile
    }

    fun status(message: String): TextView {
        val value = TvUi.label(activity, message, if (handheld) 15f else 18f).apply {
            background = SettingsDecor.statusShape(activity)
            setPadding(TvUi.dp(activity, 16), TvUi.dp(activity, 13),
                TvUi.dp(activity, 16), TvUi.dp(activity, 13))
        }
        body.addView(value, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = TvUi.dp(activity, if (handheld) 12 else 18)
        })
        return value
    }

    fun heading(title: String) {
        body.addView(TvUi.label(activity, title, if (handheld) 17f else 21f, true))
        TvUi.space(body, activity, 7)
    }

    fun divider() = TvUi.space(body, activity, if (handheld) 14 else 22)

    fun collapsible(
        title: String,
        hint: String = "仅在需要时展开"
    ): LinearLayout {
        val details = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val trigger = action(title + "  ▾", hint) {
            val open = details.visibility != View.VISIBLE
            details.visibility = if (open) View.VISIBLE else View.GONE
        }
        trigger.setOnClickListener {
            val open = details.visibility != View.VISIBLE
            details.visibility = if (open) View.VISIBLE else View.GONE
            trigger.setTitle(title + if (open) "  ▴" else "  ▾")
            if (open) scroll.post { scroll.smoothScrollTo(0, trigger.bottom) }
        }
        body.addView(details, LinearLayout.LayoutParams(-1, -2))
        return details
    }

    fun focusPrimary(action: View) {
        if (!handheld) action.requestFocus()
    }
}

/** Pure width policy used by both subpages and the URL editor. */
internal object SettingsDetailLayoutPolicy {
    fun contentWidthDp(screenWidthDp: Int, handheld: Boolean, maxWidthDp: Int = 880): Int? {
        require(screenWidthDp > 0 && maxWidthDp > 0)
        if (handheld) return null
        // Preserve a margin for TV overscan and low-resolution 720p panels.
        return minOf(maxWidthDp, (screenWidthDp - 40).coerceAtLeast(240))
    }
}
