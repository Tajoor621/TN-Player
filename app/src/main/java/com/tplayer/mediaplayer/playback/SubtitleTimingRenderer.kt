@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Format
import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererConfiguration
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Text-only scheduling: positive delay replays bounded cue history; negative delay decodes ahead.
 * Audio/video clocks, timestamps, renderers and the source playlist are unchanged. */
class SubtitleTimingRenderer private constructor(private val engine: TextRenderer, private val output: CueHistory,
    private val delayMs: AtomicLong, private val timeline: AtomicReference<Timeline>) : Renderer by engine {
    private var periodId: MediaPeriodId? = null
    private var streamOffsetUs = 0L
    constructor(output: TextOutput, looper: Looper, delayMs: AtomicLong, timeline: AtomicReference<Timeline>) : this(CueHistory(output, looper), delayMs, timeline)
    private constructor(output: CueHistory, delayMs: AtomicLong, timeline: AtomicReference<Timeline>) : this(TextRenderer(output, null), output, delayMs, timeline)
    override fun enable(configuration: RendererConfiguration, formats: Array<Format>, stream: SampleStream,
        positionUs: Long, joining: Boolean, mayRenderStartOfStream: Boolean, startPositionUs: Long,
        offsetUs: Long, mediaPeriodId: MediaPeriodId) {
        periodId = mediaPeriodId; streamOffsetUs = offsetUs; output.reset()
        engine.enable(configuration, formats, stream, positionUs, joining, mayRenderStartOfStream, startPositionUs, offsetUs, mediaPeriodId)
    }
    override fun replaceStream(formats: Array<Format>, stream: SampleStream, startPositionUs: Long, offsetUs: Long, mediaPeriodId: MediaPeriodId) {
        periodId = mediaPeriodId; streamOffsetUs = offsetUs; output.reset(); engine.replaceStream(formats, stream, startPositionUs, offsetUs, mediaPeriodId)
    }
    override fun setCurrentStreamFinal() {
        engine.setCurrentStreamFinal()
        // Media3 normally forwards this bound only to instanceof TextRenderer.
        // Preserve it through delegation so an overlong subtitle cannot extend A/V playback.
        val id = periodId ?: return
        if (id.isAd) return
        val current = timeline.get(); val index = current.getIndexOfPeriod(id.periodUid)
        if (index < 0) return
        val duration = current.getPeriod(index, Timeline.Period()).durationUs
        if (duration != C.TIME_UNSET && duration >= 0 && streamOffsetUs <= Long.MAX_VALUE - duration)
            engine.setFinalStreamEndPositionUs(streamOffsetUs + duration)
    }
    override fun resetPosition(positionUs: Long) { output.reset(); engine.resetPosition(positionUs) }
    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        val offset = delayMs.get() * 1000
        engine.render(positionUs + (-offset).coerceAtLeast(0), elapsedRealtimeUs)
        output.emit(positionUs - streamOffsetUs - offset, offset)
    }
    override fun disable() { engine.disable(); output.reset() }
}

internal class CueHistory(private val downstream: TextOutput, looper: Looper) : TextOutput {
    private val handler = Handler(looper)
    private val history = java.util.TreeMap<Long, CueGroup>()
    private var bytes = 0L
    @Volatile private var epoch = 0
    private var lastDelay = Long.MIN_VALUE
    private var last: CueGroup? = null
    private fun cost(cues: CueGroup): Long = cues.cues.sumOf { (it.text?.length ?: 0) * 2L + (it.bitmap?.allocationByteCount ?: 0) + 128 }
    override fun onCues(cueGroup: CueGroup) {
        history.put(cueGroup.presentationTimeUs, cueGroup)?.let { bytes -= cost(it) }
        bytes += cost(cueGroup)
        while ((bytes > 8L * 1024 * 1024 || history.size > 2048) && history.size > 1) bytes -= cost(history.pollFirstEntry().value)
    }
    fun emit(positionUs: Long, delayUs: Long) {
        val cues = history.floorEntry(positionUs)?.value ?: CueGroup.EMPTY_TIME_ZERO
        if (last === cues && lastDelay == delayUs) return
        last = cues; lastDelay = delayUs; val version = epoch
        val shifted = CueGroup(cues.cues, (cues.presentationTimeUs + delayUs).coerceAtLeast(0))
        handler.post { if (version == epoch) downstream.onCues(shifted) }
    }
    fun reset() {
        epoch++; history.clear(); bytes = 0; last = null
        val version = epoch
        handler.post { if (version == epoch) downstream.onCues(CueGroup.EMPTY_TIME_ZERO) }
    }
}
