package dev.kidstubetv.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Approved V2 high-density settings: two functional panes on landscape TVs,
 * the same groups stacked on phones. No new activities for simple settings.
 */
internal object DenseSettingsUi {
    private fun dp(context: Context, value: Int): Int = TvUi.dp(context, value)

    private fun shape(context: Context, color: Int, radius: Int = 16): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(context, radius).toFloat()
            setStroke(dp(context, 1), 0xFF35505A.toInt())
        }

    fun intro(context: Context, parent: LinearLayout, headline: String, detail: String) {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10))
            background = shape(context, 0xFF1D3540.toInt(), 13)
        }
        box.addView(TvUi.label(context, headline, 16f, true).apply {
            setTypeface(typeface, Typeface.BOLD)
        })
        box.addView(TvUi.label(context, detail, 13f).apply {
            setTextColor(TvUi.muted)
        })
        parent.addView(box, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(context, 10)
        })
    }

    fun stats(context: Context, parent: LinearLayout, values: List<Pair<String, String>>): List<TextView> {
        val handheld = UiDevice.isHandheld(context)
        val outputs = mutableListOf<TextView>()
        val chunks = values.chunked(DenseSettingsLayoutPolicy.statsPerRow(handheld))
        chunks.forEach { chunk ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                baselineAligned = false
            }
            chunk.forEachIndexed { i, (label, value) ->
                val item = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(context, 12), dp(context, 8), dp(context, 10), dp(context, 8))
                    background = shape(context, 0xFF192F3A.toInt(), 12)
                }
                item.addView(TvUi.label(context, label, 12f).apply {
                    setTextColor(TvUi.muted)
                })
                val number = TvUi.label(context, value,
                    if (handheld) 16f else 18f, true).apply {
                    setTypeface(typeface, Typeface.BOLD)
                }
                outputs.add(number)
                item.addView(number)
                row.addView(item, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (i != chunk.lastIndex) rightMargin = dp(context, 8)
                })
            }
            parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(context, if (handheld) 7 else 11)
            })
        }
        return outputs
    }

    fun twoPanes(
        context: Context,
        parent: LinearLayout,
        leftTitle: String,
        leftHint: String,
        rightTitle: String,
        rightHint: String
    ): Pair<LinearLayout, LinearLayout> {
        val screenWidthDp = context.resources.configuration.screenWidthDp
        val horizontal = DenseSettingsLayoutPolicy.twoColumns(
            UiDevice.isHandheld(context), screenWidthDp
        )
        val container = LinearLayout(context).apply {
            orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            baselineAligned = false
        }
        val left = pane(context, leftTitle, leftHint)
        val right = pane(context, rightTitle, rightHint)
        if (horizontal) {
            container.addView(left, LinearLayout.LayoutParams(0, -2, 1f).apply {
                rightMargin = dp(context, 10)
            })
            container.addView(right, LinearLayout.LayoutParams(0, -2, 1f))
        } else {
            container.addView(left, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(context, 10)
            })
            container.addView(right, LinearLayout.LayoutParams(-1, -2))
        }
        parent.addView(container, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(context, 12)
        })
        return left to right
    }

    private fun pane(context: Context, title: String, hint: String): LinearLayout {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 13), dp(context, 11), dp(context, 13), dp(context, 10))
            background = shape(context, 0xFF172D39.toInt(), 14)
        }
        box.addView(TvUi.label(context, title, 17f, true).apply {
            setTypeface(typeface, Typeface.BOLD)
        })
        box.addView(TvUi.label(context, hint, 12f).apply { setTextColor(TvUi.muted) })
        return box
    }

    fun line(context: Context, parent: LinearLayout, label: String, detail: String) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 3), dp(context, 5), dp(context, 3), dp(context, 5))
        }
        row.addView(TvUi.label(context, label, 14f).apply {
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(-2, -2))
        row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        row.addView(TvUi.label(context, detail, 13f).apply {
            setTextColor(TvUi.muted)
            gravity = Gravity.END
            maxLines = 2
        })
        parent.addView(row)
    }
}

/** Deterministic layout rules; covered by JVM tests without Android views. */
internal object DenseSettingsLayoutPolicy {
    fun statsPerRow(handheld: Boolean): Int = if (handheld) 2 else 4

    fun twoColumns(handheld: Boolean, screenWidthDp: Int): Boolean =
        !handheld && (screenWidthDp <= 0 || screenWidthDp >= 720)
}
