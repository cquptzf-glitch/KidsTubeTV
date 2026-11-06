package dev.kidstubetv.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.text.InputType

/** Original remote-first UI; uses only Android platform APIs for Android 9 compatibility. */
internal object TvUi {
    const val bg = 0xFF101B28.toInt()
    const val surface = 0xFF223448.toInt()
    const val mint = 0xFF62E4C0.toInt()
    const val muted = 0xFFAAB8C6.toInt()
    fun dp(ctx: Context, n: Int) = (ctx.resources.displayMetrics.density * n).toInt()

    fun column(ctx: Context) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(ctx, 28), dp(ctx, 20), dp(ctx, 28), dp(ctx, 20))
        setBackgroundColor(bg)
    }
    fun label(ctx: Context, value: String, sp: Float = 18f, accent: Boolean = false) =
        TextView(ctx).apply {
            text = value
            textSize = sp
            setTextColor(if (accent) mint else Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 2), dp(ctx, 8), dp(ctx, 2), dp(ctx, 8))
        }
    fun button(ctx: Context, value: String, click: () -> Unit): Button =
        Button(ctx).apply {
            text = value
            isAllCaps = false
            textSize = 17f
            setTextColor(Color.WHITE)
            background = shape(ctx, false)
            setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8))
            setOnClickListener { click() }
            setOnFocusChangeListener { _, focused ->
                background = shape(ctx, focused)
                scaleX = if (focused) 1.04f else 1f
                scaleY = if (focused) 1.04f else 1f
            }
        }
    fun input(ctx: Context, hintText: String, value: String, multiline: Boolean = false): EditText =
        EditText(ctx).apply {
            hint = hintText
            setText(value)
            setSingleLine(!multiline)
            if (multiline) minLines = 3
            textSize = 17f
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
            setBackgroundColor(surface)
            inputType = if (multiline) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else InputType.TYPE_CLASS_TEXT
        }
    private fun shape(ctx: Context, focus: Boolean): GradientDrawable = GradientDrawable().apply {
        setColor(if (focus) 0xFF165D58.toInt() else surface)
        cornerRadius = dp(ctx, 12).toFloat()
        setStroke(dp(ctx, if (focus) 3 else 1), if (focus) mint else 0xFF46576A.toInt())
    }
    fun space(layout: LinearLayout, ctx: Context, n: Int = 10) =
        layout.addView(View(ctx), LinearLayout.LayoutParams(1, dp(ctx, n)))
}
