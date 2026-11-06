package dev.kidstubetv.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.text.InputType

/**
 * KidsTubeTV design tokens: ink navy + sea-glass + warm focus ring.
 * Large TV targets and high contrast without a fluorescent UI.
 */
internal object TvUi {
    const val bg = 0xFF0E1923.toInt()
    const val surface = 0xFF192E39.toInt()
    const val surfaceRaised = 0xFF25443F.toInt()
    const val mint = 0xFFB0E2CD.toInt()
    const val warm = 0xFFFFE2AC.toInt()
    const val text = 0xFFF8F6EC.toInt()
    const val muted = 0xFFA5B9BE.toInt()

    fun dp(ctx: Context, n: Int) = (ctx.resources.displayMetrics.density * n + 0.5f).toInt()

    fun column(ctx: Context) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(ctx, 28), dp(ctx, 20), dp(ctx, 28), dp(ctx, 20))
        setBackgroundColor(bg)
    }

    fun label(ctx: Context, value: String, sp: Float = 18f, accent: Boolean = false) =
        TextView(ctx).apply {
            text = value
            textSize = sp
            setTextColor(if (accent) TvUi.mint else TvUi.text)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 2), dp(ctx, 6), dp(ctx, 2), dp(ctx, 6))
        }

    fun button(ctx: Context, value: String, click: () -> Unit): Button =
        Button(ctx).apply {
            text = value
            isAllCaps = false
            textSize = 17f
            setTextColor(TvUi.text)
            background = buttonShape(ctx, false)
            setPadding(dp(ctx, 18), dp(ctx, 11), dp(ctx, 18), dp(ctx, 11))
            setOnClickListener { click() }
            setOnFocusChangeListener { _, focused ->
                background = buttonShape(ctx, focused)
                scaleX = if (focused) 1.035f else 1f
                scaleY = if (focused) 1.035f else 1f
            }
        }

    fun primaryButton(ctx: Context, value: String, click: () -> Unit): Button =
        button(ctx, value, click).apply {
            setTextColor(0xFF233C38.toInt())
            setTypeface(typeface, Typeface.BOLD)
            background = primaryShape(ctx, false)
            setOnFocusChangeListener { _, focused ->
                background = primaryShape(ctx, focused)
                scaleX = if (focused) 1.035f else 1f
                scaleY = if (focused) 1.035f else 1f
            }
        }

    fun input(ctx: Context, hintText: String, value: String, multiline: Boolean = false): EditText =
        EditText(ctx).apply {
            hint = hintText
            setText(value)
            setSingleLine(!multiline)
            if (multiline) minLines = 3
            textSize = 17f
            setTextColor(TvUi.text)
            setHintTextColor(muted)
            setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
            setBackgroundColor(surface)
            inputType = if (multiline) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else InputType.TYPE_CLASS_TEXT
        }

    fun rounded(ctx: Context, color: Int, radius: Int = 18): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(ctx, radius).toFloat()
        }

    private fun buttonShape(ctx: Context, focused: Boolean) = GradientDrawable().apply {
        setColor(if (focused) surfaceRaised else surface)
        cornerRadius = dp(ctx, 13).toFloat()
        setStroke(dp(ctx, if (focused) 3 else 1), if (focused) warm else 0xFF344E57.toInt())
    }

    private fun primaryShape(ctx: Context, focused: Boolean) = GradientDrawable().apply {
        setColor(if (focused) 0xFFFFF0D2.toInt() else warm)
        cornerRadius = dp(ctx, 13).toFloat()
        if (focused) setStroke(dp(ctx, 3), Color.WHITE)
    }

    fun space(layout: LinearLayout, ctx: Context, n: Int = 10) =
        layout.addView(View(ctx), LinearLayout.LayoutParams(1, dp(ctx, n)))
}
