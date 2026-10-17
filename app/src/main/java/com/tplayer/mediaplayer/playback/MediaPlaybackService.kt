package com.tplayer.mediaplayer.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.session.*
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.tplayer.mediaplayer.data.History
import com.tplayer.mediaplayer.data.MediaDatabase
import com.tplayer.mediaplayer.data.Settings
import com.tplayer.mediaplayer.ui.PlayerActivity
import com.tplayer.mediaplayer.widget.PlaybackWidget
import kotlinx.coroutines.*

@UnstableApi
class MediaPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val effects by lazy { AudioEffects(Settings(this)) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val persistence by lazy { PlaybackPersistence.get(this) }
    private var restoring = true
    private var applyingCheckpoint = false
    private var preparingHistory = false
    private var historyRestore: Job? = null
    private var transition = 0L
    private var previousId: String? = null
    private var loopA = -1L
    private var loopB = -1L
    private val sleep = Runnable { session?.player?.pause() }
    private val loopTicker = object : Runnable {
        override fun run() {
            val player = session?.player ?: return
            if (!player.isPlaying || !PlaybackPolicy.validLoop(loopA, loopB)) return
            if (player.currentPosition >= loopB) player.seekTo(loopA)
            handler.postDelayed(this, 100)
        }
    }
    private val ticker = object : Runnable {
        override fun run() {
            if (session?.player?.isPlaying != true) return
            save(); PlaybackWidget.render(this@MediaPlaybackService, session?.player); handler.postDelayed(this, 5000)
        }
    }
    private fun updateTimers() {
        handler.removeCallbacks(ticker); handler.removeCallbacks(loopTicker)
        if (session?.player?.isPlaying == true) {
            handler.postDelayed(ticker, 5000)
            if (PlaybackPolicy.validLoop(loopA, loopB)) handler.post(loopTicker)
        }
    }
    private val listener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) { effects.attach(audioSessionId) }
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) { PlaybackWidget.render(this@MediaPlaybackService, session?.player) }

        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            val player = session?.player ?: return
            val version = ++transition
            historyRestore?.cancel(); preparingHistory = false
            loopA = -1; loopB = -1; handler.removeCallbacks(loopTicker)
            val oldId = previousId; previousId = item?.mediaId
            if (item == null) { if (oldId != null) persistence.save(null); return }
            if (applyingCheckpoint) return
            // Repeats, auto-next and subtitle updates must never jump back to an old history position.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && item.mediaId != oldId &&
                player.currentPosition == 0L && Settings(this@MediaPlaybackService).resume) {
                preparingHistory = true
                historyRestore = scope.launch {
                    try {
                        val saved = persistence.historyFor(item.mediaId)
                        if (transition == version && player.currentMediaItem?.mediaId == item.mediaId && player.currentPosition < 1000 && saved != null) {
                            player.seekTo(PlaybackPolicy.resumePosition(saved.position, saved.duration))
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { android.util.Log.w("TNPlayer", "Resume history is unavailable") }
                    finally { if (transition == version) { preparingHistory = false; save() } }
                }
            } else save()
        }
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (!restoring && oldPosition.mediaItem != null && oldPosition.mediaItem?.mediaId != newPosition.mediaItem?.mediaId) {
                val item = oldPosition.mediaItem!!
                persistence.recordHistory(History(item.mediaId, item.mediaMetadata.title?.toString() ?: "Media", oldPosition.positionMs, 0, mimeType = item.mediaMetadata.extras?.getString("mime").orEmpty()))
            }
            save()
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) { updateTimers(); save(); PlaybackWidget.render(this@MediaPlaybackService, session?.player) }
        override fun onPlaybackStateChanged(playbackState: Int) { save() }
        override fun onRepeatModeChanged(repeatMode: Int) { save() }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { save() }
        override fun onTimelineChanged(timeline: Timeline, reason: Int) { save() }
    }
    override fun onCreate() {
        super.onCreate()
        val renderers = DefaultRenderersFactory(this).setEnableDecoderFallback(true).setMediaCodecSelector { mime, secure, tunneling ->
            DecoderPreference.order(MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling), Settings(this).decoderPreference,
                { it.hardwareAccelerated }, { it.softwareOnly })
        }
        val player = ExoPlayer.Builder(this, renderers)
            .setSeekBackIncrementMs(Settings(this).seekMs).setSeekForwardIncrementMs(Settings(this).seekMs)
            .build().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
                setHandleAudioBecomingNoisy(true); setWakeMode(C.WAKE_MODE_LOCAL)
            }
        val activity = PendingIntent.getActivity(this, 0, Intent(this, PlayerActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this, player).setSessionActivity(activity)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    if (controller.packageName != packageName && !controller.isTrusted) return MediaSession.ConnectionResult.reject()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                            .add(SessionCommand("SET_LOOP", Bundle.EMPTY)).add(SessionCommand("SLEEP_TIMER", Bundle.EMPTY))
                            .add(SessionCommand("AUDIO_EFFECTS_STATE", Bundle.EMPTY)).add(SessionCommand("AUDIO_EFFECTS_SET", Bundle.EMPTY))
                            .add(SessionCommand("TOGGLE_PLAYBACK", Bundle.EMPTY)).add(SessionCommand("DECODER_PREFERENCE", Bundle.EMPTY)).build()).build()
                }
                override fun onPlaybackResumption(session: MediaSession, controller: MediaSession.ControllerInfo): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
                    scope.launch {
                        try {
                            val saved = persistence.read() ?: error("No saved playback queue.")
                            result.set(MediaSession.MediaItemsWithStartPosition(saved.items.map { it.mediaItem() }, saved.index, saved.position))
                        } catch (e: Exception) { result.setException(e) }
                    }
                    return result
                }
                override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        "TOGGLE_PLAYBACK" -> {
                            val result = SettableFuture.create<SessionResult>()
                            scope.launch {
                                while (restoring) delay(20)
                                if (player.mediaItemCount == 0) result.set(SessionResult(SessionError.ERROR_INVALID_STATE))
                                else {
                                    if (player.isPlaying) player.pause() else {
                                        if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
                                        if (player.playbackState == Player.STATE_IDLE) player.prepare()
                                        player.play()
                                    }
                                    result.set(SessionResult(SessionResult.RESULT_SUCCESS))
                                }
                            }
                            return result
                        }
                        "DECODER_PREFERENCE" -> {
                            val mode = args.getInt("mode", -1)
                            if (mode !in 0..2) return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                            Settings(this@MediaPlaybackService).decoderPreference = mode
                            if (player.mediaItemCount > 0 && player.playbackState != Player.STATE_IDLE) {
                                val playing = player.playWhenReady
                                player.stop(); player.prepare(); player.playWhenReady = playing
                            }
                        }
                        "AUDIO_EFFECTS_STATE" -> {
                            return try {
                                effects.attach(player.audioSessionId)
                                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, effects.state()))
                            } catch (_: RuntimeException) { Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED)) }
                        }
                        "AUDIO_EFFECTS_SET" -> {
                            try { effects.apply(args) }
                            catch (_: RuntimeException) { effects.release(); return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE)) }
                        }
                        "SET_LOOP" -> {
                            val a = args.getLong("a", -1); val b = args.getLong("b", -1)
                            if (a == -1L && b == -1L) { loopA = -1; loopB = -1 }
                            else if (player.isCurrentMediaItemSeekable && PlaybackPolicy.validLoop(a, b) && (player.duration == C.TIME_UNSET || b <= player.duration)) {
                                loopA = a; loopB = b
                            } else return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                            updateTimers()
                        }
                        "SLEEP_TIMER" -> {
                            val minutes = args.getLong("minutes", 0)
                            if (minutes !in 0L..240L) return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                            handler.removeCallbacks(sleep)
                            if (minutes > 0) handler.postDelayed(sleep, minutes * 60000)
                        }
                        else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }).build()
        player.addListener(listener)
        effects.attach(player.audioSessionId)
        scope.launch {
            try {
                val saved = persistence.read()
                // An explicit new media intent always takes precedence over a late disk restore.
                if (saved != null && transition == 0L && player.mediaItemCount == 0) {
                    applyingCheckpoint = true
                    try {
                        player.setMediaItems(saved.items.map { it.mediaItem() }, saved.index, saved.position)
                        player.repeatMode = saved.repeatMode; player.shuffleModeEnabled = saved.shuffle
                        player.playWhenReady = false
                    } finally { applyingCheckpoint = false }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { android.util.Log.w("TNPlayer", "Saved playback queue is unavailable") }
            finally { restoring = false; if (player.mediaItemCount > 0) save() }
        }
    }
    private fun save() {
        if (restoring || preparingHistory) return
        val player = session?.player ?: return
        val item = player.currentMediaItem
        if (item == null) { persistence.save(null); return }
        val position = player.currentPosition.coerceAtLeast(0)
        persistence.recordHistory(History(item.mediaId, item.mediaMetadata.title?.toString() ?: "Media", position,
            player.duration.takeIf { it != C.TIME_UNSET } ?: 0, mimeType = item.localConfiguration?.mimeType ?: item.mediaMetadata.extras?.getString("mime").orEmpty()))
        val items = (0 until player.mediaItemCount.coerceAtMost(1000)).map { index ->
            val value = player.getMediaItemAt(index)
            QueueEntry(value.localConfiguration?.uri?.toString() ?: value.mediaId,
                value.mediaMetadata.title?.toString() ?: "Media", value.localConfiguration?.mimeType ?: value.mediaMetadata.extras?.getString("mime").orEmpty())
        }
        if (player.currentMediaItemIndex in items.indices) persistence.save(PlaybackCheckpoint(items, player.currentMediaItemIndex, if (player.playbackState == Player.STATE_ENDED) 0 else position, player.repeatMode, player.shuffleModeEnabled))
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onTaskRemoved(rootIntent: Intent?) { if (session?.player?.playWhenReady != true) stopSelf() }
    override fun onDestroy() {
        save(); handler.removeCallbacksAndMessages(null); historyRestore?.cancel()
        session?.run { player.removeListener(listener); player.release(); release() }; session = null
        effects.release(); PlaybackWidget.render(this, null); scope.cancel(); super.onDestroy()
    }
}
