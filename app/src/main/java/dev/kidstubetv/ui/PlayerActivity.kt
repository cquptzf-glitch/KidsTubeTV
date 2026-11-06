package dev.kidstubetv.ui

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import dev.kidstubetv.data.SettingsStore
import dev.kidstubetv.data.YouTubeRepository
import dev.kidstubetv.proxy.ProxyStatus

/**
 * Full-screen embedded YouTube player controlled by Android TV remote.
 * Admission always re-checks the video at the Data API before playing.
 */
class PlayerActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private var ids = arrayListOf<String>()
    private var index = 0
    private var serial = 0
    private var ready = false
    private var allowedId: String? = null
    private var player: YouTubePlayer? = null
    private var seconds = 0f
    private var playing = false
    private var next: Runnable? = null
    private lateinit var videoView: YouTubePlayerView
    private lateinit var label: TextView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        ids = intent.getStringArrayListExtra(EXTRA_VIDEO_IDS) ?: arrayListOf()
        if (ids.isEmpty()) {
            finish()
            return
        }
        index = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, ids.lastIndex)

        val body = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(body)
        videoView = YouTubePlayerView(this).apply { enableAutomaticInitialization = false }
        body.addView(videoView, FrameLayout.LayoutParams(-1, -1))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(TvUi.dp(this@PlayerActivity, 24), TvUi.dp(this@PlayerActivity, 14),
                TvUi.dp(this@PlayerActivity, 24), TvUi.dp(this@PlayerActivity, 14))
            setBackgroundColor(0xDF101B28.toInt())
        }
        controls.addView(TvUi.button(this, "上一集") { loadAt(index - 1) })
        controls.addView(TvUi.button(this, "暂停/继续") { togglePause() })
        controls.addView(TvUi.button(this, "下一集") { loadAt(index + 1) })
        controls.addView(TvUi.button(this, "返回") { finish() })
        label = TvUi.label(this, "校验视频中…", 16f)
        controls.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        videoView.initialize(object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                player = youTubePlayer
                ready = true
                allowedId?.let { youTubePlayer.loadVideo(it, 0f) }
            }
            override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                playing = state == PlayerConstants.PlayerState.PLAYING
                if (state == PlayerConstants.PlayerState.ENDED) scheduleNext()
            }
            override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                seconds = second
            }
            override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                label.text = "视频播放失败：" + error.toString() + "（检查代理和 TV WebView）"
            }
        }, IFramePlayerOptions.Builder(this).controls(0).rel(0).ivLoadPolicy(3).build())
        loadAt(index)
    }

    private fun loadAt(newIndex: Int) {
        if (newIndex !in ids.indices) {
            label.text = "播放队列已结束"
            return
        }
        next?.let(main::removeCallbacks)
        next = null
        if (ProxyStatus.phase != ProxyStatus.Phase.READY) {
            label.text = "VPN 未就绪：禁止视频直连"
            player?.pause()
            return
        }
        index = newIndex
        val current = ids[index]
        val request = ++serial
        allowedId = null
        player?.pause()
        label.text = "正在校验此视频的儿童标记和频道…"
        Thread({
            val checked = runCatching {
                YouTubeRepository(SettingsStore(this)).approveSingle(current)
            }
            runOnUiThread {
                if (request != serial || isFinishing) return@runOnUiThread
                if (ProxyStatus.phase != ProxyStatus.Phase.READY) {
                    label.text = "VPN 已断开：禁止播放"
                    return@runOnUiThread
                }
                val item = checked.getOrNull()
                if (item == null) {
                    label.text = "视频未通过检查，已禁止播放。可返回选择其他视频。"
                } else {
                    allowedId = item.id
                    label.text = item.title + " · " + item.channelTitle
                    if (ready) player?.loadVideo(item.id, 0f)
                }
            }
        }, "kids-verify-video").start()
    }

    private fun scheduleNext() {
        if (next != null) return
        if (index >= ids.lastIndex) {
            label.text = "本次视频播放已完成"
            return
        }
        label.text = "5 秒后继续下一集（重新执行儿童校验）"
        val action = Runnable { next = null; loadAt(index + 1) }
        next = action
        main.postDelayed(action, 5_000)
    }

    private fun togglePause() {
        player?.let { if (playing) it.pause() else it.play() }
    }

    override fun onResume() {
        super.onResume()
        if (::label.isInitialized && ProxyStatus.phase != ProxyStatus.Phase.READY) {
            player?.pause()
            label.text = "VPN 未连接。请返回首页重新连接。"
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { togglePause(); true }
        KeyEvent.KEYCODE_MEDIA_NEXT -> { loadAt(index + 1); true }
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { loadAt(index - 1); true }
        KeyEvent.KEYCODE_DPAD_LEFT -> { player?.seekTo((seconds - 10f).coerceAtLeast(0f)); true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { player?.seekTo(seconds + 10f); true }
        else -> super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        next?.let(main::removeCallbacks)
        if (::videoView.isInitialized) videoView.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_VIDEO_IDS = "video_ids"
        const val EXTRA_INDEX = "video_index"
    }
}
