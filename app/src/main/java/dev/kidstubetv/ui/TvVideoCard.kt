package dev.kidstubetv.ui

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import dev.kidstubetv.data.KidVideo

/** Large 16:9 artwork, one remote focus target per video, no tiny action buttons. */
object TvVideoCard {
    fun make(activity: Activity, video: KidVideo, onClick: () -> Unit): View =
        make(activity, video, TvUi.dp(activity, 292), onClick)

    fun make(activity: Activity, video: KidVideo, widthPixels: Int, onClick: () -> Unit): View {
        val height = (widthPixels * 9f / 16f).toInt()
        val item = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isClickable = true
            clipChildren = false
            clipToPadding = false
            setPadding(TvUi.dp(activity, 3), TvUi.dp(activity, 3),
                TvUi.dp(activity, 3), TvUi.dp(activity, 5))
            setOnClickListener { onClick() }
        }
        val image = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = video.title
            background = TvUi.rounded(activity, 0xFF2D4C54.toInt())
            clipToOutline = true
        }
        val imageHolder = android.widget.FrameLayout(activity).apply {
            background = cardShape(activity, false)
            clipToOutline = true
            setPadding(TvUi.dp(activity, 3), TvUi.dp(activity, 3),
                TvUi.dp(activity, 3), TvUi.dp(activity, 3))
            addView(image, android.widget.FrameLayout.LayoutParams(-1, -1))
        }
        item.addView(imageHolder, LinearLayout.LayoutParams(-1, height))
        val title = TvUi.label(activity, video.title, 19f).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        item.addView(title)
        item.addView(TvUi.label(activity, video.channelTitle, 13f).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(TvUi.muted)
            setPadding(TvUi.dp(activity, 2), 0, 0, 0)
        })
        item.setOnFocusChangeListener { _, focused ->
            imageHolder.background = cardShape(activity, focused)
            title.setTextColor(if (focused) TvUi.warm else TvUi.text)
            item.scaleX = if (focused) 1.035f else 1f
            item.scaleY = if (focused) 1.035f else 1f
            item.elevation = if (focused) TvUi.dp(activity, 10).toFloat() else 0f
        }
        if (video.thumbnailUrl.startsWith("https://")) {
            Glide.with(activity).load(video.thumbnailUrl)
                .placeholder(android.R.drawable.ic_media_play)
                .centerCrop().into(image)
        }
        item.layoutParams = LinearLayout.LayoutParams(widthPixels, -2)
        return item
    }

    private fun cardShape(activity: Activity, focused: Boolean): GradientDrawable =
        GradientDrawable().apply {
            setColor(TvUi.surface)
            cornerRadius = TvUi.dp(activity, 18).toFloat()
            setStroke(TvUi.dp(activity, if (focused) 4 else 1),
                if (focused) TvUi.warm else 0xFF3A5660.toInt())
        }
}
