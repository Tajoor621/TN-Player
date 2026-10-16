@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.tplayer.mediaplayer.playback.MediaPlaybackService

class NowPlayingBar(private val activity: MainActivity) : LinearLayout(activity), DefaultLifecycleObserver {
    private val title = TextView(activity).apply { textSize = 15f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val time = TextView(activity).apply { textSize = 12f; alpha = .75f }
    private val toggle = activity.button("Play") { player?.let { p ->
        if (p.isPlaying) p.pause() else { if (p.playbackState == Player.STATE_ENDED) p.seekToDefaultPosition(); if (p.playbackState == Player.STATE_IDLE) p.prepare(); p.play() }
    } }
    private var connection: ListenableFuture<MediaController>? = null
    private var player: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() { updateTime(); if (player?.isPlaying == true) handler.postDelayed(this, 1000) }
    }
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { refresh() }
    }
    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; minimumHeight = activity.dp(72)
        setPadding(activity.dp(16), activity.dp(8), activity.dp(12), activity.dp(8)); visibility = View.GONE
        val labels = LinearLayout(activity).apply { orientation = VERTICAL; isClickable = true; isFocusable = true
            addView(title); addView(time)
            contentDescription = "Open now playing"
            setOnClickListener { activity.startActivity(Intent(activity, PlayerActivity::class.java)) }
        }
        addView(labels, LayoutParams(0, -2, 1f)); addView(toggle)
        activity.lifecycle.addObserver(this)
    }
    override fun onStart(owner: LifecycleOwner) {
        try {
            val future = MediaController.Builder(activity, SessionToken(activity, ComponentName(activity, MediaPlaybackService::class.java))).buildAsync()
            connection = future
            future.addListener({
                if (connection !== future) return@addListener
                try { player = future.get(); player?.addListener(listener); refresh() }
                catch (_: Exception) { visibility = View.GONE }
            }, ContextCompat.getMainExecutor(activity))
        } catch (_: Exception) { visibility = View.GONE }
    }
    private fun refresh() {
        val p = player ?: return
        visibility = if (p.currentMediaItem != null) View.VISIBLE else View.GONE
        title.text = p.mediaMetadata.title?.toString() ?: "Now playing"
        toggle.text = if (p.isPlaying) "Pause" else "Play"
        toggle.contentDescription = "${toggle.text} ${title.text}"
        handler.removeCallbacks(ticker); updateTime()
        if (p.isPlaying) handler.postDelayed(ticker, 1000)
    }
    private fun updateTime() {
        val p = player ?: return
        fun format(ms: Long): String { val seconds = ms.coerceAtLeast(0) / 1000; return "%d:%02d".format(seconds / 60, seconds % 60) }
        time.text = if (p.duration == C.TIME_UNSET || p.duration <= 0) format(p.currentPosition) else "${format(p.currentPosition)} / ${format(p.duration)}"
    }
    override fun onStop(owner: LifecycleOwner) {
        handler.removeCallbacksAndMessages(null); player?.removeListener(listener); player = null
        connection?.let { MediaController.releaseFuture(it) }; connection = null
    }
}
