package dev.kidstubetv.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import dev.kidstubetv.data.SettingsStore
import dev.kidstubetv.data.WatchHistory
import dev.kidstubetv.data.WatchTime
import dev.kidstubetv.data.YouTubeRepository
import dev.kidstubetv.proxy.ProxyStatus
import dev.kidstubetv.proxy.ProxyNetworkHealth

/**
 * Remote-first fullscreen YouTube IFrame player.
 *
 * Normal playback: no permanent buttons. UP reveals controls; LEFT/RIGHT seek
 * while controls are hidden. Player entry/next always re-check child admission.
 */
class PlayerActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val history by lazy { WatchHistory(this) }
    private val settings by lazy { SettingsStore(this) }
    private val clock by lazy { WatchTime(this) }

    private var ids = arrayListOf<String>()
    private var position = 0
    private var requestSerial = 0
    private var iframeReady = false
    private var approvedVideoId: String? = null
    private var player: YouTubePlayer? = null
    private var positionSeconds = 0f
    private var playing = false
    private var lastRecordedVideo: String? = null
    private var autoNext: Runnable? = null
    private var controlsVisible = false
    private var healthPulseSeconds = 0

    private lateinit var videoView: YouTubePlayerView
    private lateinit var controls: LinearLayout
    private lateinit var videoTitle: TextView
    private lateinit var proxyWarning: TextView
    private lateinit var favoriteButton: Button
    private lateinit var pauseButton: Button

    private val hideControls = Runnable { setControlsVisible(false) }
    private val playbackTick = object : Runnable {
        override fun run() {
            healthPulseSeconds++
            if (healthPulseSeconds % 25 == 1 && ProxyStatus.phase == ProxyStatus.Phase.READY)
                ProxyNetworkHealth.check(this@PlayerActivity)
            if (playing) {
                if (ProxyStatus.phase != ProxyStatus.Phase.READY ||
                    ProxyNetworkHealth.state == ProxyNetworkHealth.State.UNREACHABLE) {
                    pauseOnVpnLost()
                } else if (clock.addSecond() >= settings.dailyLimitMinutes * 60) {
                    playing = false
                    player?.pause()
                    setControlsVisible(true)
                    videoTitle.text = "今天的观看时间到啦，明天见！"
                    handler.postDelayed({ if (!isFinishing) finish() }, 2000)
                    return
                }
            }
            handler.postDelayed(this, 1000)
        }
    }
    private val vpnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = renderVpnStatus()
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        ids = intent.getStringArrayListExtra(EXTRA_VIDEO_IDS) ?: arrayListOf()
        if (ids.isEmpty()) { finish(); return }
        position = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, ids.lastIndex)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)
        videoView = YouTubePlayerView(this).apply {
            enableAutomaticInitialization = false
            isFocusable = false
        }
        root.addView(videoView, FrameLayout.LayoutParams(-1, -1))

        controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(TvUi.dp(this@PlayerActivity, 22), TvUi.dp(this@PlayerActivity, 14),
                TvUi.dp(this@PlayerActivity, 22), TvUi.dp(this@PlayerActivity, 14))
            setBackgroundColor(0xEA142832.toInt())
        }
        controls.addView(TvUi.button(this, "上一集") { loadAt(position - 1); revealControls() })
        pauseButton = TvUi.primaryButton(this, "暂停 / 继续") { togglePlayback(); revealControls() }
        controls.addView(pauseButton)
        controls.addView(TvUi.button(this, "下一集") { loadAt(position + 1); revealControls() })
        favoriteButton = TvUi.button(this, "☆ 收藏") { toggleFavorite(); revealControls() }
        controls.addView(favoriteButton)
        controls.addView(TvUi.button(this, "返回") { finish() })
        root.addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        videoTitle = TvUi.label(this, "正在检查视频是否适合儿童…", 19f).apply {
            setPadding(TvUi.dp(this@PlayerActivity, 24), TvUi.dp(this@PlayerActivity, 15),
                TvUi.dp(this@PlayerActivity, 24), TvUi.dp(this@PlayerActivity, 15))
            setBackgroundColor(0xD5142832.toInt())
        }
        root.addView(videoTitle, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        proxyWarning = TvUi.label(this, "", 17f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(TvUi.warm)
            setPadding(TvUi.dp(this@PlayerActivity, 25), TvUi.dp(this@PlayerActivity, 13),
                TvUi.dp(this@PlayerActivity, 25), TvUi.dp(this@PlayerActivity, 13))
            setBackgroundColor(0xF04C342C.toInt())
            visibility = View.GONE
            isFocusable = true
            setOnClickListener { finish() } // VPN permission must be requested by the launcher.
        }
        root.addView(proxyWarning, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        controls.bringToFront() // Transport controls stay above warning only when visible.
        proxyWarning.bringToFront()

        videoView.initialize(object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                player = youTubePlayer
                iframeReady = true
                if (ProxyStatus.phase == ProxyStatus.Phase.READY)
                    approvedVideoId?.let { youTubePlayer.loadVideo(it, 0f) }
            }

            override fun onStateChange(
                youTubePlayer: YouTubePlayer,
                state: PlayerConstants.PlayerState
            ) {
                playing = state == PlayerConstants.PlayerState.PLAYING
                if (playing) {
                    val id = approvedVideoId
                    if (id != null && lastRecordedVideo != id) {
                        history.record(id)
                        lastRecordedVideo = id
                    }
                    scheduleAutoHide()
                }
                if (state == PlayerConstants.PlayerState.ENDED) scheduleNext()
            }

            override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                positionSeconds = second
            }

            override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                revealControls()
                videoTitle.text = "暂时无法播放，请返回选择其他视频（$error）"
            }
        }, IFramePlayerOptions.Builder(this).controls(0).rel(0).ivLoadPolicy(3).build())

        revealControls()
        loadAt(position)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(ProxyStatus.ACTION_CHANGED).apply {
            addAction(ProxyNetworkHealth.ACTION_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(vpnReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(vpnReceiver, filter)
        }
        handler.removeCallbacks(playbackTick)
        handler.postDelayed(playbackTick, 1000)
        renderVpnStatus()
    }

    override fun onStop() {
        requestSerial++ // Stale async admission must not autoplay while backgrounded.
        handler.removeCallbacks(playbackTick)
        handler.removeCallbacks(hideControls)
        cancelNext()
        playing = false
        player?.pause()
        unregisterReceiver(vpnReceiver)
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacks(playbackTick)
        handler.removeCallbacks(hideControls)
        cancelNext()
        if (::videoView.isInitialized) videoView.release()
        super.onDestroy()
    }

    private fun loadAt(newPosition: Int) {
        if (newPosition !in ids.indices) {
            revealControls()
            videoTitle.text = "本次播放结束啦！"
            return
        }
        cancelNext()
        if (clock.remaining(settings.dailyLimitMinutes) <= 0) {
            player?.pause()
            revealControls()
            videoTitle.text = "今天的观看时间已经用完啦"
            return
        }
        if (ProxyStatus.phase != ProxyStatus.Phase.READY ||
            ProxyNetworkHealth.state == ProxyNetworkHealth.State.UNREACHABLE) {
            pauseOnVpnLost()
            return
        }
        position = newPosition
        approvedVideoId = null
        player?.pause()
        val request = ++requestSerial
        videoTitle.text = "正在检查视频是否适合儿童…"
        Thread({
            val result = runCatching {
                YouTubeRepository(settings).approveSingle(ids[newPosition])
            }
            runOnUiThread {
                if (isFinishing || isDestroyed || request != requestSerial) return@runOnUiThread
                if (ProxyStatus.phase != ProxyStatus.Phase.READY) {
                    pauseOnVpnLost()
                    return@runOnUiThread
                }
                val item = result.getOrNull()
                if (item == null) {
                    revealControls()
                    videoTitle.text = "这条视频尚未通过家长审核，请换一条看看"
                    return@runOnUiThread
                }
                approvedVideoId = item.id
                videoTitle.text = item.title
                favoriteButton.text = if (history.isFavorite(item.id)) "★ 已收藏" else "☆ 收藏"
                if (iframeReady) player?.loadVideo(item.id, 0f)
            }
        }, "kids-video-admission").start()
    }

    private fun cancelNext() {
        autoNext?.let(handler::removeCallbacks)
        autoNext = null
    }

    private fun scheduleNext() {
        if (autoNext != null) return
        if (position >= ids.lastIndex) {
            revealControls()
            videoTitle.text = "已经看完啦！"
            return
        }
        revealControls()
        videoTitle.text = "5 秒后播放下一集 · 按下一集可立即跳过"
        val callback = Runnable {
            autoNext = null
            if (!isFinishing) loadAt(position + 1)
        }
        autoNext = callback
        handler.postDelayed(callback, 5000)
    }

    private fun toggleFavorite() {
        approvedVideoId?.let {
            favoriteButton.text = if (history.toggleFavorite(it)) "★ 已收藏" else "☆ 收藏"
        }
    }

    private fun togglePlayback() {
        if (ProxyStatus.phase != ProxyStatus.Phase.READY ||
            ProxyNetworkHealth.state == ProxyNetworkHealth.State.UNREACHABLE) {
            pauseOnVpnLost(); return
        }
        if (clock.remaining(settings.dailyLimitMinutes) <= 0) { player?.pause(); return }
        player?.let { if (playing) it.pause() else it.play() }
    }

    private fun pauseOnVpnLost() {
        playing = false
        player?.pause()
        cancelNext()
        revealControls()
        renderVpnStatus()
        videoTitle.text = "网络断开，视频已暂停"
    }

    private fun renderVpnStatus() {
        if (!::proxyWarning.isInitialized) return
        val healthFailed = ProxyNetworkHealth.state == ProxyNetworkHealth.State.UNREACHABLE
        if (ProxyStatus.phase == ProxyStatus.Phase.READY && !healthFailed) {
            proxyWarning.visibility = View.GONE
        } else {
            proxyWarning.text = if (healthFailed)
                "⚠ VPN 隧道已启动，但 Google 网络无法访问 · 按确定返回检查"
                else "⚠ VPN 未连接，播放已停止 · 按确定返回首页重新连接"
            proxyWarning.visibility = View.VISIBLE
            playing = false
            player?.pause()
            cancelNext()
        }
    }

    private fun setControlsVisible(visible: Boolean) {
        controlsVisible = visible
        handler.removeCallbacks(hideControls)
        if (!::controls.isInitialized) return
        controls.visibility = if (visible) View.VISIBLE else View.GONE
        videoTitle.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) videoView.requestFocus()
    }

    private fun revealControls() {
        setControlsVisible(true)
        pauseButton.requestFocus()
        scheduleAutoHide()
    }

    private fun scheduleAutoHide() {
        handler.removeCallbacks(hideControls)
        if (playing && controlsVisible) handler.postDelayed(hideControls, 5500)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        val code = event.keyCode
        // The warning is a non-modal banner; OK returns to the launcher even
        // when transport controls previously held D-pad focus.
        if (ProxyStatus.phase != ProxyStatus.Phase.READY ||
            ProxyNetworkHealth.state == ProxyNetworkHealth.State.UNREACHABLE) {
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                finish()
                return true
            }
        }
        if (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { togglePlayback(); return true }
        if (code == KeyEvent.KEYCODE_MEDIA_NEXT) { loadAt(position + 1); return true }
        if (code == KeyEvent.KEYCODE_MEDIA_PREVIOUS) { loadAt(position - 1); return true }
        if (!controlsVisible) {
            when (code) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    togglePlayback()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (ProxyStatus.phase == ProxyStatus.Phase.READY) {
                        player?.seekTo((positionSeconds - 10f).coerceAtLeast(0f))
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (ProxyStatus.phase == ProxyStatus.Phase.READY) player?.seekTo(positionSeconds + 10f)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    revealControls()
                    return true
                }
            }
        } else if (code == KeyEvent.KEYCODE_BACK) {
            setControlsVisible(false)
            return true
        } else if (code == KeyEvent.KEYCODE_DPAD_LEFT ||
            code == KeyEvent.KEYCODE_DPAD_RIGHT ||
            code == KeyEvent.KEYCODE_DPAD_UP ||
            code == KeyEvent.KEYCODE_DPAD_DOWN) {
            scheduleAutoHide()
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Back hides controls before leaving the player")
    override fun onBackPressed() {
        if (controlsVisible) setControlsVisible(false)
        else super.onBackPressed()
    }

    companion object {
        const val EXTRA_VIDEO_IDS = "video_ids"
        const val EXTRA_INDEX = "video_index"
    }
}
