package dev.kidstubetv.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.view.WindowManager

/** Device input mode, not screen orientation: tablets and phones can rotate. */
internal object UiDevice {
    fun isTelevision(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        return mode == Configuration.UI_MODE_TYPE_TELEVISION
    }

    fun isHandheld(context: Context): Boolean = !isTelevision(context)

    fun showSystemBarsForHandheld(activity: Activity) {
        if (isHandheld(activity)) {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    fun dimensions(context: Context): ResponsiveScreenSpec {
        val config = context.resources.configuration
        val widthDp = config.screenWidthDp.takeIf { it > 0 }
            ?: (context.resources.displayMetrics.widthPixels /
                context.resources.displayMetrics.density).toInt()
        return ResponsiveScreenPolicy.forWidth(widthDp, isTelevision(context))
    }
}

/** Pure breakpoint policy for remote and touch rendering, including rotation. */
internal data class ResponsiveScreenSpec(
    val handheld: Boolean,
    val horizontalPaddingDp: Int,
    val cardGapDp: Int,
    val minimumCardWidthDp: Int,
    val drawerWidthDp: Int,
    val compactHero: Boolean,
)

internal object ResponsiveScreenPolicy {
    fun forWidth(widthDp: Int, television: Boolean): ResponsiveScreenSpec {
        require(widthDp > 0)
        return if (television) {
            ResponsiveScreenSpec(false, 44, 18, 205, 235, false)
        } else {
            val narrow = widthDp < 600
            ResponsiveScreenSpec(
                handheld = true,
                horizontalPaddingDp = if (narrow) 14 else 24,
                cardGapDp = if (narrow) 10 else 16,
                minimumCardWidthDp = if (widthDp < 350) 144 else 156,
                drawerWidthDp = minOf(300, (widthDp * 0.86f).toInt()),
                compactHero = narrow,
            )
        }
    }
}
