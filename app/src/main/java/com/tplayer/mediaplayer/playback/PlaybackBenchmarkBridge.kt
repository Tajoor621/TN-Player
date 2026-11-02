@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionError
import com.tplayer.mediaplayer.BuildConfig
import kotlinx.coroutines.*
import java.io.File

/** Same-package debug-only control of the production engine. No exported benchmark Activity. */
class PlaybackBenchmarkBridge(private val context: Context, private val player: ExoPlayer,
    private val selector: RecoveryTrackSelector, private val renderers: TnRenderersFactory,
    private val metrics: PlaybackPerformance, private val power: PlaybackPowerController,
    private val recovery: PlaybackRecovery, private val tuning: PlaybackTuning) {
    var active = false; private set
    private data class Saved(val items: List<MediaItem>, val index: Int, val position: Long,
        val repeat: Int, val shuffle: Boolean, val volume: Float, val speed: PlaybackParameters,
        val parameters: DefaultTrackSelector.Parameters)
    private var saved: Saved? = null
    suspend fun command(action: String, args: Bundle): SessionResult {
        if (!BuildConfig.DEBUG) return SessionResult(SessionError.ERROR_PERMISSION_DENIED)
        if (action == "BENCHMARK_BEGIN") {
            check(!active && !player.playWhenReady) { "Pause current playback before benchmarking" }
            saved = Saved((0 until player.mediaItemCount).map(player::getMediaItemAt), player.currentMediaItemIndex,
                player.currentPosition, player.repeatMode, player.shuffleModeEnabled, player.volume,
                player.playbackParameters, selector.parameters)
            active = true
            tuning.benchmark = PlaybackTuning.Options(args.getBoolean("tunnel"), args.getBoolean("preload"), true)
            player.stop(); player.volume = 0f; player.repeatMode = Player.REPEAT_MODE_OFF
            player.shuffleModeEnabled = false; player.playbackParameters = PlaybackParameters.DEFAULT
            recovery.reset(); power.update()
        } else {
            check(active) { "Begin a benchmark first" }
            when (action) {
                "BENCHMARK_OPEN" -> {
                    val names = args.getStringArrayList("files") ?: error("No reference files")
                    require(names.size in 1..3)
                    val files = withContext(Dispatchers.IO) {
                        val root = File(context.getExternalFilesDir(null), "benchmarks/input").canonicalFile
                        names.map { name ->
                            require(name.matches(Regex("[A-Za-z0-9_.-]+")))
                            File(root, name).canonicalFile.also { require(it.parentFile == root && it.isFile) }
                        }
                    }
                    val token = java.util.UUID.randomUUID().toString()
                    val items = files.mapIndexed { index, file -> MediaItem.Builder().setUri(Uri.fromFile(file))
                        .setMediaId("benchmark-$token-$index")
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(file.name).build()).build() }
                    metrics.begin(items[0].mediaId)
                    player.setMediaItems(items); player.repeatMode = if (files.size > 1) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                    player.prepare(); player.play()
                }
                "BENCHMARK_SEEK" -> { metrics.seek(); player.seekTo(args.getLong("position_ms")) }
                "BENCHMARK_SNAPSHOT" -> return SessionResult(SessionResult.RESULT_SUCCESS, metrics.snapshot().apply {
                    putString("source_revision", BuildConfig.SOURCE_REVISION)
                    putBoolean("tunnel_requested", tuning.tunnel); putBoolean("tunnel_active", renderers.tunnelingActive)
                    putBoolean("preload", tuning.preload); putInt("idle_stops", power.idleStops)
                    putInt("state", player.playbackState); putBoolean("playing", player.isPlaying)
                    putLong("position_ms", player.currentPosition)
                    putString("error", player.playerError?.errorCodeName.orEmpty())
                })
                "BENCHMARK_END" -> restore()
                else -> error("Unknown benchmark command")
            }
        }
        return SessionResult(SessionResult.RESULT_SUCCESS)
    }
    suspend fun restore() {
        val old = saved ?: return
        // Keep history/checkpoint listeners suppressed through restoration callbacks.
        player.stop(); player.clearMediaItems()
        tuning.benchmark = null
        recovery.reset(); selector.setParameters(old.parameters)
        player.volume = old.volume; player.playbackParameters = old.speed
        player.repeatMode = old.repeat; player.shuffleModeEnabled = old.shuffle
        if (old.items.isNotEmpty()) player.setMediaItems(old.items, old.index.coerceIn(old.items.indices), old.position)
        player.playWhenReady = false; power.update()
        delay(200)
        saved = null; active = false
    }
    /** Discard in-memory test tuning on service shutdown. */
    fun restoreOptionsOnDestroy() {
        tuning.benchmark = null
    }
}
