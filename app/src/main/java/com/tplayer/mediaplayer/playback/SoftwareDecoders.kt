@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer.playback

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.decoder.av1.Gav1Library
import androidx.media3.decoder.vp9.VpxLibrary

object SoftwareDecoders {
    fun supports(format: Format): Boolean {
        if (format.drmInitData != null) return false
        return when (format.sampleMimeType) {
            MimeTypes.VIDEO_AV1 -> Gav1Library.isAvailable()
            MimeTypes.VIDEO_VP9 -> VpxLibrary.isAvailable()
            null -> false
            else -> format.sampleMimeType!!.startsWith("audio/") && FfmpegLibrary.supportsFormat(format.sampleMimeType!!)
        }
    }
    fun versions(): String = "FFmpeg ${FfmpegLibrary.getVersion() ?: "unavailable"}\nVP9/libvpx ${VpxLibrary.getVersion() ?: "unavailable"}\nAV1/libgav1 ${if (Gav1Library.isAvailable()) "0.20.0 (pinned build)" else "unavailable"}"
}
