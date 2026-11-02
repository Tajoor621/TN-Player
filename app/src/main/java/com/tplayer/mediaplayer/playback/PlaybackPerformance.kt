@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer.playback

import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import androidx.media3.common.Format
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener

/** In-memory engine measurements. No automatic file export, identifiers, URLs or network reporting. */
class PlaybackPerformance : AnalyticsListener {
    private var expectedId = ""
    private var startMs = 0L
    private var cpuStart = 0L
    private var firstFrameMs = -1L
    private var seekStartMs = -1L
    private var seekMs = -1L
    private var seekSerial = 0
    private var video: Format? = null
    private var counters: DecoderCounters? = null
    private var audioInits = 0; private var audioReleases = 0
    private var videoInits = 0; private var videoReleases = 0
    var onStarted: (() -> Unit)? = null
    var hasStarted = false; private set
    private fun started() { PlaybackWorkGate.started(); if (!hasStarted) { hasStarted = true; onStarted?.invoke() } }
    override fun onMediaItemTransition(time: AnalyticsListener.EventTime, item: androidx.media3.common.MediaItem?, reason: Int) { hasStarted = false }
    fun begin(id: String) { expectedId = id; startMs = SystemClock.elapsedRealtime(); cpuStart = Process.getElapsedCpuTime(); firstFrameMs = -1; seekStartMs = -1; seekMs = -1; seekSerial = 0; hasStarted = false; video = null; counters = null }
    fun seek(): Int { seekStartMs = SystemClock.elapsedRealtime(); seekMs = -1; return ++seekSerial }
    private fun matches(time: AnalyticsListener.EventTime): Boolean {
        val timeline = time.timeline
        return !timeline.isEmpty && time.windowIndex in 0 until timeline.windowCount &&
            timeline.getWindow(time.windowIndex, androidx.media3.common.Timeline.Window()).mediaItem.mediaId == expectedId
    }
    override fun onVideoEnabled(time: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) { counters = decoderCounters }
    override fun onVideoInputFormatChanged(time: AnalyticsListener.EventTime, format: Format, evaluation: DecoderReuseEvaluation?) { if (matches(time)) video = format }
    override fun onRenderedFirstFrame(time: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
        started()
        if (!matches(time) || startMs == 0L) return
        if (firstFrameMs < 0) firstFrameMs = (renderTimeMs - startMs).coerceAtLeast(0)
        if (seekStartMs >= 0 && renderTimeMs >= seekStartMs) { seekMs = renderTimeMs - seekStartMs; seekStartMs = -1 }
    }
    override fun onAudioPositionAdvancing(time: AnalyticsListener.EventTime, playoutStartSystemTimeMs: Long) { started() }
    override fun onAudioDecoderInitialized(time: AnalyticsListener.EventTime, name: String, timestampMs: Long, durationMs: Long) { audioInits++ }
    override fun onAudioDecoderReleased(time: AnalyticsListener.EventTime, name: String) { audioReleases++ }
    override fun onVideoDecoderInitialized(time: AnalyticsListener.EventTime, name: String, timestampMs: Long, durationMs: Long) { videoInits++ }
    override fun onVideoDecoderReleased(time: AnalyticsListener.EventTime, name: String) { videoReleases++ }
    fun snapshot(): Bundle {
        counters?.ensureUpdated()
        val wall = if (startMs > 0) (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1) else 0
        val cpu = if (startMs > 0) (Process.getElapsedCpuTime() - cpuStart).coerceAtLeast(0) else 0
        return Bundle().apply {
            putLong("ttff_ms", firstFrameMs); putLong("seek_ms", seekMs); putInt("seek_serial", seekSerial)
            putLong("wall_ms", wall); putLong("cpu_ms", cpu); putDouble("cpu_percent_one_core", if (wall > 0) cpu * 100.0 / wall else 0.0)
            putInt("dropped_frames", counters?.droppedBufferCount ?: 0)
            putInt("audio_inits", audioInits); putInt("audio_releases", audioReleases)
            putInt("video_inits", videoInits); putInt("video_releases", videoReleases)
            putInt("active_audio", audioInits - audioReleases); putInt("active_video", videoInits - videoReleases)
            putString("video_mime", video?.sampleMimeType.orEmpty()); putString("video_codecs", video?.codecs.orEmpty())
            putInt("width", video?.width ?: -1); putInt("height", video?.height ?: -1)
            putInt("color_transfer", video?.colorInfo?.colorTransfer ?: -1)
        }
    }
}
