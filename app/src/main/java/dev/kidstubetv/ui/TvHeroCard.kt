package dev.kidstubetv.ui

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import dev.kidstubetv.data.KidVideo

/**
 * One focusable, full-bleed featured video.
 * The background is artwork, the left scrim protects readable typography,
 * and there is only ONE remote action (OK to play).
 */
object TvHeroCard {
    fun make(activity: Activity, video: KidVideo, section: String, play: () -> Unit): View {
        val radius = TvUi.dp(activity, 22).toFloat()
        val root = FrameLayout(activity).apply {
            isFocusable = true
            isClickable = true
            tag = "menu-edge"
            transitionName = "hero:${video.id}"
            clipChildren = false
            clipToPadding = false
            background = TvUi.rounded(activity, 0xFF1C3640.toInt(), 22)
            foreground = focusRing(activity, focused = false)
            setOnClickListener { play() }
        }
        val picture = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = video.title
            background = TvUi.rounded(activity, 0xFF2E504E.toInt(), 22)
            clipToOutline = true
        }
        root.addView(picture, FrameLayout.LayoutParams(-1, -1).apply {
            setMargins(TvUi.dp(activity, 4), TvUi.dp(activity, 4),
                TvUi.dp(activity, 4), TvUi.dp(activity, 4))
        })

        // Gradients avoid extra buttons and keep white titles legible on bright images.
        val scrim = View(activity).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    0xF20E2028.toInt(),
                    0xDA112B33.toInt(),
                    0x7A17313B.toInt(),
                    0x18131E2C.toInt(),
                )
            ).apply { cornerRadius = radius }
            isFocusable = false
        }
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))

        val copy = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(TvUi.dp(activity, 42), TvUi.dp(activity, 26),
                TvUi.dp(activity, 10), TvUi.dp(activity, 26))
            isFocusable = false
        }
        val eyebrow = TvUi.label(activity, "✦  " + section, 18f, true)
        copy.addView(eyebrow)
        val title = TvUi.label(activity, video.title, 35f).apply {
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            setTypeface(typeface, Typeface.BOLD)
        }
        copy.addView(title)
        val channel = TvUi.label(activity, video.channelTitle, 17f).apply {
            setTextColor(0xFFD2DED9.toInt())
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        copy.addView(channel)
        TvUi.space(copy, activity, 19)
        val action = TvUi.label(activity, "▶  按确定播放", 20f).apply {
            setTextColor(TvUi.warm)
            setTypeface(typeface, Typeface.BOLD)
        }
        copy.addView(action)
        root.addView(copy, FrameLayout.LayoutParams(
            TvUi.dp(activity, 535), -1, Gravity.START or Gravity.CENTER_VERTICAL
        ))

        root.setOnFocusChangeListener { _, focused ->
            root.foreground = focusRing(activity, focused)
            root.scaleX = if (focused) 1.008f else 1f
            root.scaleY = if (focused) 1.008f else 1f
            action.setTextColor(if (focused) 0xFFFFF4D9.toInt() else TvUi.warm)
        }
        val candidate = if (video.id.matches(Regex("[A-Za-z0-9_-]{11}"))) {
            "https://i.ytimg.com/vi/${video.id}/hqdefault.jpg"
        } else video.thumbnailUrl
        if (candidate.startsWith("https://")) {
            Glide.with(activity)
                .load(candidate)
                .override(activity.resources.displayMetrics.widthPixels,
                    (activity.resources.displayMetrics.heightPixels * 0.55f).toInt())
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .error(android.R.drawable.ic_menu_report_image)
                .centerCrop()
                .into(picture)
        }
        return root
    }

    private fun focusRing(activity: Activity, focused: Boolean) =
        GradientDrawable().apply {
            setColor(android.graphics.Color.TRANSPARENT)
            cornerRadius = TvUi.dp(activity, 22).toFloat()
            setStroke(
                TvUi.dp(activity, if (focused) 4 else 1),
                if (focused) TvUi.warm else 0xFF597C77.toInt()
            )
        }
}
