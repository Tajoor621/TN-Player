@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer.playback

import android.content.Context
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.exoplayer.*
import androidx.media3.exoplayer.audio.*
import androidx.media3.exoplayer.video.*
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.tplayer.mediaplayer.data.Settings

open class TnRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    var platformRenderers: Map<Int, Int> = emptyMap()
        protected set
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
        super.createRenderers(handler, video, audio, text, metadata).also { renderers ->
            platformRenderers = renderers.mapIndexedNotNull { index, renderer -> when (renderer) {
                is MediaCodecAudioRenderer -> C.TRACK_TYPE_AUDIO to index
                is MediaCodecVideoRenderer -> C.TRACK_TYPE_VIDEO to index
                else -> null
            } }.toMap()
        }
}
