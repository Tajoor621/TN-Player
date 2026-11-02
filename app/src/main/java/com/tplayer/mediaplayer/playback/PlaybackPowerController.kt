@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer.playback

import android.os.Handler
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*

/** Keeps the queue/session; stop() frees renderers and wake mode NONE releases playback locks. */
class PlaybackPowerController(private val player: ExoPlayer, private val settings: PlaybackTuning,
    private val performance: PlaybackPerformance, private val preloader: LocalPlaylistPreloader) : Player.Listener {
    private val handler = Handler(player.applicationLooper)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var visible = AppVisibility.visible.value
    private var stoppedForIdle = false
    var idleStops = 0; private set
    private val release = Runnable {
        if (eligible()) {
            stoppedForIdle = true; idleStops++
            preloader.cancel(); player.stop(); player.setWakeMode(C.WAKE_MODE_NONE)
        }
    }
    init { performance.onStarted = { update() }; scope.launch { AppVisibility.visible.collect { visible = it; update() } } }
    private fun eligible() = PlaybackPowerPolicy.shouldRelease(settings.releasePaused, visible,
        player.playWhenReady, player.playbackState != Player.STATE_IDLE)
    fun update() {
        handler.removeCallbacks(release)
        preloader.configure(settings.preload)
        if (stoppedForIdle && (visible || player.playWhenReady)) {
            stoppedForIdle = false
            player.setWakeMode(C.WAKE_MODE_LOCAL)
            if (player.mediaItemCount > 0 && player.playbackState == Player.STATE_IDLE) player.prepare()
        }
        if (player.isPlaying && performance.hasStarted && settings.preload) {
            val index = player.nextMediaItemIndex
            val next = if (index in 0 until player.mediaItemCount) player.getMediaItemAt(index) else null
            preloader.warm(next?.takeUnless { it.localConfiguration?.uri == player.currentMediaItem?.localConfiguration?.uri })
        } else preloader.cancel()
        if (eligible()) handler.postDelayed(release, PlaybackPowerPolicy.IDLE_RELEASE_MS)
    }
    override fun onIsPlayingChanged(value: Boolean) { update() }
    override fun onPlayWhenReadyChanged(value: Boolean, reason: Int) { update() }
    override fun onPlaybackStateChanged(state: Int) { update() }
    override fun onMediaItemTransition(item: MediaItem?, reason: Int) { update() }
    fun release() { handler.removeCallbacksAndMessages(null); performance.onStarted = null; scope.cancel(); player.removeListener(this); preloader.close() }
}
