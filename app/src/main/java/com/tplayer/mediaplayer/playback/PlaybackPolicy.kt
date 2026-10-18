package com.tplayer.mediaplayer.playback

import java.net.URI

object PlaybackPolicy {
    val streamSchemes = setOf("https", "http", "rtsp")
    fun validateStream(value: String): String? = try {
        require(value.length in 1..32768 && value.none { it.code < 32 })
        val uri = URI(value.trim())
        when {
            uri.scheme?.lowercase() !in streamSchemes -> "Supported streams: HTTPS and RTSP. HTTP is blocked by the secure default."
            uri.scheme.equals("http", true) -> "Unencrypted HTTP is disabled. Use an HTTPS source."
            uri.host.isNullOrBlank() -> "Enter a complete stream URL with a host."
            uri.userInfo != null -> "Do not store passwords inside stream URLs."
            else -> null
        }
    } catch (_: Exception) { "Enter a valid URL." }
    fun queueError(uris: List<String>, titles: List<String>, mimes: List<String>): String? {
        if (uris.isEmpty() || uris.size > 1000) return "Choose between 1 and 1000 files. Split larger playlists."
        if (titles.size != uris.size || mimes.size != uris.size) return "Playlist metadata does not match its files."
        var chars = 0L
        for (index in uris.indices) {
            if (uris[index].length !in 1..32768 || titles[index].length > 1024 || mimes[index].length > 128) return "A playlist entry is too large."
            chars += uris[index].length + titles[index].length + mimes[index].length
            if (chars > 100000) return "Playlist metadata is too large for Android. Play a smaller selection."
        }
        return null
    }
    fun resumePosition(position: Long, duration: Long): Long = when {
        position < 0 -> 0
        duration > 0 && position >= duration - 3000 -> 0
        else -> position
    }
    fun validLoop(a: Long, b: Long): Boolean = a >= 0 && b > a && b - a >= 500
}
