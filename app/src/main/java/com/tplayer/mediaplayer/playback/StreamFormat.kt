package com.tplayer.mediaplayer.playback

import android.net.Uri
import androidx.media3.common.MimeTypes

enum class StreamFormat(val label: String, val mimeType: String) {
    AUTO("Auto / direct file", ""), HLS("HLS", MimeTypes.APPLICATION_M3U8),
    DASH("DASH", MimeTypes.APPLICATION_MPD), SMOOTH("SmoothStreaming", MimeTypes.APPLICATION_SS);
    companion object {
        fun fromMime(value: String?) = values().firstOrNull { it.mimeType == value } ?: AUTO
        fun displayAddress(value: String): String = try {
            val uri = Uri.parse(value)
            if (uri.scheme?.lowercase() !in setOf("https", "http", "rtsp")) value
            else uri.buildUpon().clearQuery().fragment(null).encodedAuthority(uri.host + (if (uri.port > 0) ":${uri.port}" else "")).build().toString()
        } catch (_: Exception) { "Saved source" }
    }
}
