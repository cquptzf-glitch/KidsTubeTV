package dev.kidstubetv.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Compact, high-contrast, focus-visible cards for D-pad TV settings. */
internal object SettingsDecor {
    fun navShape(context: Context, selected: Boolean, focused: Boolean): GradientDrawable =
        GradientDrawable().apply {
            setColor(if (selected) 0xFF284A4B.toInt() else 0xFF1B3540.toInt())
            cornerRadius = TvUi.dp(context, 15).toFloat()
            setStroke(TvUi.dp(context, if (focused) 3 else 1),
                if (focused) TvUi.warm else if (selected) TvUi.mint else 0xFF35505A.toInt())
        }

    fun statusShape(context: Context) = GradientDrawable().apply {
        setColor(0xFF213A45.toInt())
        cornerRadius = TvUi.dp(context, 18).toFloat()
        setStroke(TvUi.dp(context, 1), 0xFF40636B.toInt())
    }

    fun action(
        context: Context, title: String, subtitle: String,
        emphasized: Boolean = false, click: () -> Unit
    ): SettingsActionTile = SettingsActionTile(context, title, subtitle, emphasized, click)
}

internal class SettingsActionTile(
    context: Context, title: String, subtitle: String,
    private val emphasized: Boolean, click: () -> Unit
) : LinearLayout(context) {
    private val titleText = TextView(context).apply {
        text = title
        textSize = if (UiDevice.isHandheld(context)) 16f else 19f
        setTextColor(if (emphasized) 0xFF182C31.toInt() else TvUi.text)
        gravity = Gravity.CENTER_VERTICAL
    }
    private val subtitleText = TextView(context).apply {
        text = subtitle
        textSize = if (UiDevice.isHandheld(context)) 13f else 14f
        setTextColor(if (emphasized) 0xFF365756.toInt() else TvUi.muted)
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 2
    }

    init {
        orientation = VERTICAL
        isFocusable = true
        isClickable = true
        minimumHeight = TvUi.dp(context, if (UiDevice.isHandheld(context)) 64 else 78)
        val pad = if (UiDevice.isHandheld(context)) 13 else 20
        setPadding(TvUi.dp(context, pad), TvUi.dp(context, 9),
            TvUi.dp(context, pad), TvUi.dp(context, 9))
        addView(titleText)
        addView(subtitleText)
        background = shape(false)
        setOnFocusChangeListener { _, focused ->
            background = shape(focused)
            elevation = if (focused) TvUi.dp(context, 6).toFloat() else 0f
        }
        setOnClickListener { click() }
    }

    fun setTitle(value: String) { titleText.text = value }
    fun setSubtitle(value: String) { subtitleText.text = value }

    private fun shape(focused: Boolean) = GradientDrawable().apply {
        setColor(if (emphasized) {
            if (focused) 0xFFFFEDD1.toInt() else TvUi.warm
        } else {
            if (focused) 0xFF31524F.toInt() else TvUi.surface
        })
        cornerRadius = TvUi.dp(context, 15).toFloat()
        setStroke(TvUi.dp(context, if (focused) 3 else 1),
            if (focused) 0xFFF9FAF4.toInt() else 0xFF344E57.toInt())
    }
}
