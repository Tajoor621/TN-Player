@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer.playback

import android.net.Uri
import android.util.Log
import androidx.media3.common.*
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.extractor.*
import androidx.media3.extractor.text.SubtitleParser

/** No paths, server URLs, tokens or media titles are collected. In-memory current-file data only. */
class PlaybackDiagnostics : AnalyticsListener {
    @Volatile private var container = "Not detected yet (adaptive streams may use several segment containers)"
    private var audioDecoder = "not selected"
    private var videoDecoder = "not selected"
    private var trackReport = "No tracks detected yet"
    var audioFormat: Format? = null; private set
    var videoFormat: Format? = null; private set
    var lastMessage: String = ""; private set
    @Synchronized fun reset() { container = "Not detected yet"; audioDecoder = "not selected"; videoDecoder = "not selected"; audioFormat = null; videoFormat = null; trackReport = "No tracks detected yet"; lastMessage = "" }
    @Synchronized fun containerDetected(name: String) { container = CodecLabels.clean(name); Log.d("TNPlayerFormats", "Detected extractor/container: $container") }
    @Synchronized fun message(value: String) { lastMessage = value; Log.w("TNPlayerFormats", value) }
    @Synchronized fun tracks(tracks: Tracks) {
        trackReport = tracks.groups.take(32).flatMap { group -> (0 until group.length.coerceAtMost(32)).map { index ->
            val f = group.getTrackFormat(index)
            "${if (group.isTrackSelected(index)) "Selected" else "Available"} ${CodecLabels.name(f.sampleMimeType, f.codecs)} | MIME=${CodecLabels.clean(f.sampleMimeType.orEmpty())} | codecs=${CodecLabels.clean(f.codecs.orEmpty())} | ${f.width}x${f.height} | ${f.sampleRate} Hz/${f.channelCount} ch | support=${group.getTrackSupport(index)} | DRM=${f.drmInitData != null}"
        } }.joinToString("\n").take(18000)
        Log.d("TNPlayerFormats", trackReport)
    }
    @Synchronized override fun onAudioDecoderInitialized(time: AnalyticsListener.EventTime, name: String, timestampMs: Long, durationMs: Long) { audioDecoder = CodecLabels.clean(name); Log.d("TNPlayerFormats", "Audio decoder: $audioDecoder") }
    @Synchronized override fun onVideoDecoderInitialized(time: AnalyticsListener.EventTime, name: String, timestampMs: Long, durationMs: Long) { videoDecoder = CodecLabels.clean(name); Log.d("TNPlayerFormats", "Video decoder: $videoDecoder") }
    @Synchronized override fun onAudioInputFormatChanged(time: AnalyticsListener.EventTime, format: Format, evaluation: DecoderReuseEvaluation?) { audioFormat = format }
    @Synchronized override fun onVideoInputFormatChanged(time: AnalyticsListener.EventTime, format: Format, evaluation: DecoderReuseEvaluation?) { videoFormat = format }
    @Synchronized fun report(): String = "Container: $container\nAudio decoder: $audioDecoder\nVideo decoder: $videoDecoder\n\n$trackReport\n\n${SoftwareDecoders.versions()}\n\n$lastMessage".take(24000)
}

/** Report the extractor that actually accepted the bytes, rather than guessing by filename. */
class DiagnosticExtractorsFactory(private val diagnostics: PlaybackDiagnostics) : ExtractorsFactory {
    private val delegate = DefaultExtractorsFactory()
    override fun createExtractors(): Array<Extractor> = wrap(delegate.createExtractors())
    override fun createExtractors(uri: Uri, headers: Map<String, List<String>>): Array<Extractor> = wrap(delegate.createExtractors(uri, headers))
    override fun experimentalSetTextTrackTranscodingEnabled(enabled: Boolean): ExtractorsFactory { delegate.experimentalSetTextTrackTranscodingEnabled(enabled); return this }
    override fun setSubtitleParserFactory(factory: SubtitleParser.Factory): ExtractorsFactory { delegate.setSubtitleParserFactory(factory); return this }
    private fun wrap(extractors: Array<Extractor>): Array<Extractor> = extractors.map { extractor -> object : Extractor by extractor {
        override fun sniff(input: ExtractorInput): Boolean = extractor.sniff(input).also { if (it) diagnostics.containerDetected(extractor.javaClass.simpleName) }
        override fun getUnderlyingImplementation(): Extractor = extractor.underlyingImplementation
    } }.toTypedArray()
}
