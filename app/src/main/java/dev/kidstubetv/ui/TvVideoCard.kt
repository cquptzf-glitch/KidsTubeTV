package dev.kidstubetv.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import dev.kidstubetv.data.KidVideo

/** Focusable 16:9 card with thumbnail, title and owner, sized for 1080p Android TV. */
object TvVideoCard {
    fun make(activity: Activity, video: KidVideo, onClick: () -> Unit): View {
        val width = TvUi.dp(activity, 236)
        val item = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isClickable = true
            setPadding(TvUi.dp(activity, 9), TvUi.dp(activity, 9),
                TvUi.dp(activity, 9), TvUi.dp(activity, 9))
            setOnClickListener { onClick() }
            setOnFocusChangeListener { _, focused ->
                background = shape(activity, focused)
                scaleX = if (focused) 1.06f else 1f
                scaleY = if (focused) 1.06f else 1f
                elevation = if (focused) TvUi.dp(activity, 12).toFloat() else 0f
            }
            background = shape(activity, false)
        }
        val image = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = video.title
            setBackgroundColor(TvUi.surface)
        }
        item.addView(image, LinearLayout.LayoutParams(-1, TvUi.dp(activity, 130)))
        val title = TvUi.label(activity, video.title, 16f).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        item.addView(title)
        val channel = TvUi.label(activity, video.channelTitle, 13f).apply {
            setTextColor(TvUi.muted)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        item.addView(channel)
        if (video.thumbnailUrl.startsWith("https://")) {
            Glide.with(activity).load(video.thumbnailUrl)
                .placeholder(android.R.drawable.ic_media_play).centerCrop().into(image)
        }
        item.layoutParams = LinearLayout.LayoutParams(width, TvUi.dp(activity, 232))
        return item
    }

    private fun shape(activity: Activity, focus: Boolean) = GradientDrawable().apply {
        setColor(if (focus) 0xFF1C655D.toInt() else TvUi.surface)
        cornerRadius = TvUi.dp(activity, 14).toFloat()
        setStroke(TvUi.dp(activity, if (focus) 3 else 1),
            if (focus) TvUi.mint else 0xFF394C62.toInt())
    }
}
