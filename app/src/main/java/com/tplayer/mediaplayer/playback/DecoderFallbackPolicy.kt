package com.tplayer.mediaplayer.playback

/** One extension retry per selected track; unrelated source/DRM/sink failures never loop. */
class DecoderFallbackPolicy {
    private var mediaId: String? = null
    private val attempted = linkedSetOf<String>()
    fun reset(id: String?) { mediaId = id; attempted.clear() }
    fun claim(id: String, trackKey: String, decoderFailure: Boolean, platformRenderer: Boolean,
        softwareAvailable: Boolean, encrypted: Boolean): Boolean {
        if (id != mediaId) reset(id)
        if (!decoderFailure || !platformRenderer || !softwareAvailable || encrypted || attempted.size >= 32) return false
        return attempted.add(trackKey)
    }
}
object CodecLabels {
    fun name(mime: String?, codecs: String? = null): String = when (mime) {
        "audio/ac3" -> "AC3"; "audio/eac3" -> "EAC3"; "audio/eac3-joc" -> "EAC3 (Atmos)"
        "audio/vnd.dts" -> "DTS"; "audio/vnd.dts.hd" -> "DTS-HD"; "audio/true-hd" -> "TrueHD"
        "video/av01" -> "AV1"; "video/x-vnd.on2.vp9" -> "VP9"; "video/avc" -> "H.264"
        "video/hevc" -> "HEVC"; "audio/mp4a-latm" -> "AAC"; "audio/mpeg" -> "MP3"
        "audio/vorbis" -> "Vorbis"; "audio/opus" -> "Opus"; "audio/flac" -> "FLAC"
        else -> clean(codecs?.ifBlank { null } ?: mime ?: "Unknown codec")
    }
    fun clean(value: String): String = value.filter { it.code >= 32 && it.code != 127 }.take(160)
    fun unsupported(mime: String?, codecs: String? = null): String =
        "${name(mime, codecs)} ${if (mime?.startsWith("video/") == true) "video" else "audio"} is not supported on this device."
}
