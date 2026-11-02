@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer

import android.content.Context
import android.os.Handler
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.media3.common.*
import androidx.media3.exoplayer.*
import androidx.media3.exoplayer.audio.*
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tplayer.mediaplayer.playback.*
import com.tplayer.mediaplayer.ui.PlayerActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
class SoftwareCodecDeviceTest {
    private class PcmProbe : TeeAudioProcessor.AudioBufferSink {
        val nonSilentSamples = AtomicLong()
        @Volatile var encoding = C.ENCODING_INVALID
        override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) { this.encoding = encoding }
        override fun handleBuffer(buffer: ByteBuffer) {
            val data = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            var count = 0L
            if (encoding == C.ENCODING_PCM_16BIT) while (data.remaining() >= 2) { if (kotlin.math.abs(data.short.toInt()) > 32) count++ }
            nonSilentSamples.addAndGet(count)
        }
    }
    private class FailingPlatformAudio(private val mime: String, private val errorCode: Int) : BaseRenderer(C.TRACK_TYPE_AUDIO) {
        override fun getName() = "TNTestFailingHardware"
        override fun supportsFormat(format: Format) = RendererCapabilities.create(if (format.sampleMimeType == mime) C.FORMAT_HANDLED else C.FORMAT_UNSUPPORTED_TYPE)
        override fun isReady() = true
        override fun isEnded() = false
        override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
            if (errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED && positionUs < 500000) return
            throw createRendererException(IllegalStateException("Injected hardware decode failure"),
                streamFormats.first(), errorCode)
        }
    }
    private class ProbeFactory(context: Context, private val excluded: Set<String>, val probe: PcmProbe,
        private val failingMime: String? = null, private val failureCode: Int = PlaybackException.ERROR_CODE_DECODING_FAILED) : TnRenderersFactory(context) {
        init { setMediaCodecSelector { mime, secure, tunneling -> if (mime in excluded) emptyList() else MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling) } }
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
            DefaultAudioSink.Builder(context).setEnableFloatOutput(false).setAudioProcessors(arrayOf(TeeAudioProcessor(probe))).build()
        override fun createRenderers(handler: Handler, video: VideoRendererEventListener, audio: AudioRendererEventListener, text: TextOutput, metadata: MetadataOutput): Array<Renderer> {
            val base = super.createRenderers(handler, video, audio, text, metadata)
            if (failingMime == null) return base
            platformRenderers = platformRenderers.mapValues { it.value + 1 } + (C.TRACK_TYPE_AUDIO to 0)
            return arrayOf(guardPlatform(FailingPlatformAudio(failingMime, failureCode))) + base
        }
    }
    private fun exerciseAudio(file: String, mime: String, failHardware: Boolean = false, failureCode: Int = PlaybackException.ERROR_CODE_DECODING_FAILED) {
        val probe = PcmProbe(); val messages = CopyOnWriteArrayList<String>(); val diagnostics = PlaybackDiagnostics()
        val factory = DevicePlayback.main { ProbeFactory(DevicePlayback.context, setOf(mime), probe, if (failHardware) mime else null, failureCode) }
        val selector = DevicePlayback.main { RecoveryTrackSelector(DevicePlayback.context) }
        val player = DevicePlayback.main { ExoPlayer.Builder(DevicePlayback.context, factory).setTrackSelector(selector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DevicePlayback.context, DiagnosticExtractorsFactory(diagnostics))).build() }
        val recovery = DevicePlayback.main { PlaybackRecovery(player, selector, factory, diagnostics, notify = messages::add).also { player.addListener(it); player.addAnalyticsListener(diagnostics) } }
        try {
            DevicePlayback.main { player.setMediaItem(MediaItem.fromUri(DevicePlayback.fixture("codecs/$file"))); player.prepare(); player.play() }
            DevicePlayback.await("$file did not produce non-silent PCM and advance through AudioTrack: ${messages.joinToString()}") {
                probe.nonSilentSamples.get() > 1000 && DevicePlayback.main { player.currentPosition > 500 }
            }
            assertNull(DevicePlayback.main { player.playerError })
            assertEquals(1, DevicePlayback.main { player.mediaItemCount })
            val report = DevicePlayback.main { diagnostics.report() }
            assertTrue(report, report.contains("Audio decoder: ffmpeg", ignoreCase = true))
            assertTrue(report, report.contains("MatroskaExtractor"))
            if (mime == MimeTypes.AUDIO_TRUEHD) {
                val before = probe.nonSilentSamples.get()
                DevicePlayback.main { player.seekTo(1800); player.play() }
                DevicePlayback.await("TrueHD seek lost PCM output") { probe.nonSilentSamples.get() > before + 1000 && DevicePlayback.main { player.currentPosition > 1900 } }
            }
            if (failHardware) {
                assertEquals(1, messages.count { it.startsWith("Retrying") })
                assertTrue(DevicePlayback.main { factory.usesSoftwareFor(requireNotNull(diagnostics.audioFormat)) })
            } else assertTrue(messages.toString(), messages.none { it.contains("not supported") || it.contains("could not") })
        } finally { DevicePlayback.main { recovery.release(); player.release() } }
    }
    @Test fun eac3DtsAc3AndTrueHdDecodeToNonSilentPcmWithoutPlatformCodecs() {
        listOf("eac3.mka" to MimeTypes.AUDIO_E_AC3, "dts.mka" to MimeTypes.AUDIO_DTS,
            "ac3.mka" to MimeTypes.AUDIO_AC3, "truehd.mka" to MimeTypes.AUDIO_TRUEHD, "truehd-6ch.mka" to MimeTypes.AUDIO_TRUEHD).forEach { (file, mime) -> exerciseAudio(file, mime) }
    }
    @Test fun initializationFailureRetriesThroughRealFfmpegAudioTrack() { exerciseAudio("eac3.mka", MimeTypes.AUDIO_E_AC3, true, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) }
    @Test fun runtimeHardwareFailureRetriesThroughRealFfmpegAudioTrack() { exerciseAudio("eac3.mka", MimeTypes.AUDIO_E_AC3, true) }
    @Test fun unsupportedTrackReportsItsCodecWithoutCrashing() {
        val messages = CopyOnWriteArrayList<String>(); val diagnostics = PlaybackDiagnostics()
        val factory = DevicePlayback.main { ProbeFactory(DevicePlayback.context, setOf(MimeTypes.AUDIO_VORBIS), PcmProbe()) }
        val selector = DevicePlayback.main { RecoveryTrackSelector(DevicePlayback.context) }
        val player = DevicePlayback.main { ExoPlayer.Builder(DevicePlayback.context, factory).setTrackSelector(selector).build() }
        val recovery = DevicePlayback.main { PlaybackRecovery(player, selector, factory, diagnostics, notify = messages::add).also { player.addListener(it) } }
        try {
            DevicePlayback.main { player.setMediaItem(MediaItem.fromUri(DevicePlayback.fixture("codecs/unsupported.mka"))); player.prepare(); player.play() }
            DevicePlayback.await("Unsupported audio was silently omitted") { messages.contains("Vorbis audio is not supported on this device.") }
            assertTrue(messages.toString(), messages.none { it.startsWith("Retrying") })
            assertTrue(DevicePlayback.main { player.applicationLooper.thread.isAlive })
        } finally { DevicePlayback.main { recovery.release(); player.release() } }
    }
    @Test fun repeatedlyCorruptEac3ReportsItsCodecInsteadOfPlayingSilently() {
        val messages = CopyOnWriteArrayList<String>(); val diagnostics = PlaybackDiagnostics(); val probe = PcmProbe()
        val factory = DevicePlayback.main { ProbeFactory(DevicePlayback.context, setOf(MimeTypes.AUDIO_E_AC3), probe) }
        val selector = DevicePlayback.main { RecoveryTrackSelector(DevicePlayback.context) }
        val player = DevicePlayback.main { ExoPlayer.Builder(DevicePlayback.context, factory).setTrackSelector(selector).build() }
        val recovery = DevicePlayback.main { PlaybackRecovery(player, selector, factory, diagnostics, notify = messages::add).also { player.addListener(it); player.addAnalyticsListener(diagnostics) } }
        try {
            DevicePlayback.main { player.setMediaItem(MediaItem.fromUri(DevicePlayback.fixture("codecs/corrupt-eac3.mka"))); player.prepare(); player.play() }
            DevicePlayback.await("Corrupt EAC3 produced no user-visible codec notice") { messages.any { it.contains("EAC3") && (it.contains("could not") || it.contains("no playable")) } }
            assertEquals(0L, probe.nonSilentSamples.get())
        } finally { DevicePlayback.main { recovery.release(); player.release() } }
    }
    @Test fun av1AndVp9SoftwareRenderersDrawFramesWithoutPlatformDecoders() {
        for ((file, mime) in listOf("av1.mkv" to MimeTypes.VIDEO_AV1, "vp9.webm" to MimeTypes.VIDEO_VP9)) {
            val scenario = ActivityScenario.launch(PlayerActivity::class.java)
            val diagnostics = PlaybackDiagnostics(); val factory = DevicePlayback.main { ProbeFactory(DevicePlayback.context, setOf(mime), PcmProbe()) }
            val player = DevicePlayback.main { ExoPlayer.Builder(DevicePlayback.context, factory).build().also { it.addAnalyticsListener(diagnostics) } }
            val rendered = java.util.concurrent.atomic.AtomicBoolean(false)
            try {
                scenario.onActivity { activity ->
                    val surface = SurfaceView(activity); activity.setContentView(surface)
                    player.setVideoSurfaceView(surface)
                    player.addListener(object : Player.Listener { override fun onRenderedFirstFrame() { rendered.set(true) } })
                    player.setMediaItem(MediaItem.fromUri(DevicePlayback.fixture("codecs/$file"))); player.prepare(); player.play()
                }
                DevicePlayback.await("$file software renderer did not display a frame") { rendered.get() && DevicePlayback.main { player.currentPosition > 250 } }
                assertNull(DevicePlayback.main { player.playerError })
                val report = DevicePlayback.main { diagnostics.report() }
                assertTrue(report, report.contains(if (mime == MimeTypes.VIDEO_AV1) "gav1" else "vpx", ignoreCase = true))
            } finally { DevicePlayback.main { player.release() }; scenario.close() }
        }
    }
}
