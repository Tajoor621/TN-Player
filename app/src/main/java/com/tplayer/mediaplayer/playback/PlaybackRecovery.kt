@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer.playback

import android.os.Handler
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ExoPlaybackException

/** Changes renderer capabilities and selection on the owning player's thread; never creates a second player. */
class PlaybackRecovery(private val player: ExoPlayer, private val selector: RecoveryTrackSelector,
    private val renderers: TnRenderersFactory, private val diagnostics: PlaybackDiagnostics,
    private val softwareSupports: (Format) -> Boolean = SoftwareDecoders::supports,
    private val notify: (String) -> Unit) : Player.Listener {
    private val policy = DecoderFallbackPolicy()
    private val handler = Handler(player.applicationLooper)
    private var activeId: String? = null
    private var recovering = false
    private var generation = 0
    private val notices = linkedSetOf<String>()
    private fun notice(text: String) { diagnostics.message(text); if (notices.size < 16 && notices.add(text)) notify(text) }
    fun reset() {
        generation++; recovering = false; policy.reset(activeId); notices.clear()
        renderers.clearSoftwareOverrides(); selector.refreshRendererChoices()
    }
    override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
        if (item?.mediaId != activeId) { activeId = item?.mediaId; reset(); diagnostics.reset() }
    }
    override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) recovering = false }
    override fun onTracksChanged(tracks: Tracks) {
        diagnostics.tracks(tracks)
        if (recovering) return
        for (type in listOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO)) {
            if (selector.parameters.disabledTrackTypes.contains(type)) continue
            val groups = tracks.groups.filter { it.type == type }
            if (groups.isNotEmpty() && groups.none { it.isSelected || it.isSupported }) {
                val format = groups.first().getTrackFormat(0)
                notice(CodecLabels.unsupported(format.sampleMimeType, format.codecs))
            }
        }
    }
    override fun onPlayerError(error: PlaybackException) {
        recovering = false
        val failure = error as? ExoPlaybackException
        val format = failure?.rendererFormat
        val type = when {
            format?.sampleMimeType?.startsWith("audio/") == true -> C.TRACK_TYPE_AUDIO
            format?.sampleMimeType?.startsWith("video/") == true -> C.TRACK_TYPE_VIDEO
            else -> C.TRACK_TYPE_UNKNOWN
        }
        val decoderFailure = error.errorCode in setOf(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED, PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES)
        val id = player.currentMediaItem?.mediaId
        val index = renderers.platformRenderers[type]
        val isPlatform = failure?.type == ExoPlaybackException.TYPE_RENDERER && index != null && failure.rendererIndex == index && format != null && !renderers.usesSoftwareFor(format)
        val key = "$type:${format?.id}:${format?.sampleMimeType}:${format?.codecs}:${format?.sampleRate}:${format?.channelCount}:${format?.width}:${format?.height}"
        if (id != null && format != null && policy.claim(id, key, decoderFailure, isPlatform, softwareSupports(format), format.drmInitData != null)) {
            val formats = player.currentTracks.groups.filter { it.type == type && it.isSelected }
                .flatMap { group -> (0 until group.length).map(group::getTrackFormat) }.ifEmpty { listOf(format) }
            val position = player.currentPosition.coerceAtLeast(0); val itemIndex = player.currentMediaItemIndex
            val playing = player.playWhenReady; val seekable = player.isCurrentMediaItemSeekable; val version = generation
            recovering = true
            notice("Retrying ${CodecLabels.name(format.sampleMimeType, format.codecs)} with software decoding.")
            handler.post {
                if (version != generation || id != player.currentMediaItem?.mediaId) return@post
                // Disabling a renderer alone does not remap a TrackGroup in Media3.
                // Gate its failed group's capabilities, then invalidate selection so the
                // matching software renderer actually receives that group.
                renderers.forceSoftware(formats); selector.refreshRendererChoices()
                if (seekable) player.seekTo(itemIndex, position)
                player.prepare(); player.playWhenReady = playing
            }
        } else if (format != null && decoderFailure) {
            notice("${CodecLabels.name(format.sampleMimeType, format.codecs)} ${if (type == C.TRACK_TYPE_VIDEO) "video" else "audio"} could not be decoded on this device.")
        } else {
            notice("Unable to play this media (${error.errorCodeName}). Check file access, network availability or DRM requirements.")
        }
    }
    fun release() { generation++; handler.removeCallbacksAndMessages(null); player.removeListener(this) }
}
