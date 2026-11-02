@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer.playback

import android.content.Context
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import java.util.concurrent.ConcurrentHashMap
import androidx.media3.exoplayer.*
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.audio.*
import androidx.media3.exoplayer.video.*
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.tplayer.mediaplayer.data.Settings

open class TnRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    var platformRenderers: Map<Int, Int> = emptyMap()
        protected set
    private val tunneled = java.util.Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())
    val tunnelingActive: Boolean get() = tunneled.isNotEmpty()
    @Volatile var tunnelingFailed = false; private set
    fun clearTunnelingFailure() { tunnelingFailed = false }
    private data class TrackKey(val mime: String?, val id: String?, val codecs: String?)
    private val softwareFormats = java.util.Collections.newSetFromMap(ConcurrentHashMap<TrackKey, Boolean>())
    private fun key(format: Format) = TrackKey(format.sampleMimeType, format.id, format.codecs)
    fun forceSoftware(formats: List<Format>) { formats.forEach { softwareFormats.add(key(it)) } }
    fun clearSoftwareOverrides() { softwareFormats.clear() }
    fun usesSoftwareFor(format: Format): Boolean = softwareFormats.contains(key(format))
    protected fun guardPlatform(renderer: Renderer): Renderer {
        val original = renderer.capabilities
        val capabilities = object : RendererCapabilities by original {
            override fun supportsFormat(format: Format): Int = if (usesSoftwareFor(format))
                RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE) else original.supportsFormat(format)
        }
        return object : Renderer by renderer {
            override fun getCapabilities(): RendererCapabilities = capabilities
            override fun enable(configuration: RendererConfiguration, formats: Array<Format>, stream: SampleStream,
                positionUs: Long, joining: Boolean, mayRenderStartOfStream: Boolean, startPositionUs: Long,
                offsetUs: Long, mediaPeriodId: MediaPeriodId) {
                if (configuration.tunneling) tunneled.add(renderer.trackType) else tunneled.remove(renderer.trackType)
                try { renderer.enable(configuration, formats, stream, positionUs, joining, mayRenderStartOfStream, startPositionUs, offsetUs, mediaPeriodId) }
                catch (e: Exception) { if (configuration.tunneling) tunnelingFailed = true; tunneled.remove(renderer.trackType); throw e }
            }
            override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
                try { renderer.render(positionUs, elapsedRealtimeUs) }
                catch (e: Exception) { if (tunneled.contains(renderer.trackType)) tunnelingFailed = true; throw e }
            }
            override fun disable() { try { renderer.disable() } finally { tunneled.remove(renderer.trackType) } }
        }
    }
    init {
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)
        setEnableDecoderFallback(true)
        setMediaCodecSelector { mime, secure, tunneling ->
            val preference = Settings(context).decoderPreference
            DecoderPreference.order(MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling),
                if (preference == 0) 1 else preference, { it.hardwareAccelerated }, { it.softwareOnly })
        }
    }
    override fun createRenderers(handler: Handler, video: VideoRendererEventListener, audio: AudioRendererEventListener,
        text: TextOutput, metadata: MetadataOutput): Array<Renderer> =
        super.createRenderers(handler, video, audio, text, metadata).let { renderers ->
            platformRenderers = renderers.mapIndexedNotNull { index, renderer -> when (renderer) {
                is MediaCodecAudioRenderer -> C.TRACK_TYPE_AUDIO to index
                is MediaCodecVideoRenderer -> C.TRACK_TYPE_VIDEO to index
                else -> null
            } }.toMap()
            renderers.mapIndexed { index, renderer -> if (index in platformRenderers.values) guardPlatform(renderer) else renderer }.toTypedArray()
        }
}
