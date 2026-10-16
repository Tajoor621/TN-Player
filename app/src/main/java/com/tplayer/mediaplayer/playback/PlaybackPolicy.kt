package com.tplayer.mediaplayer.playback

import java.net.URI

object PlaybackPolicy {
    val streamSchemes = setOf("https", "http", "rtsp")
    fun validateStream(value: String): String? = try {
        val uri = URI(value.trim())
        when {
            uri.scheme?.lowercase() !in streamSchemes -> "Supported streams: HTTPS and RTSP. HTTP is blocked by the secure default."
            uri.scheme.equals("http", true) -> "Unencrypted HTTP is disabled. Use an HTTPS source."
            uri.host.isNullOrBlank() -> "Enter a complete stream URL with a host."
            uri.userInfo != null -> "Do not store passwords inside stream URLs."
            else -> null
        }
    } catch (_: Exception) { "Enter a valid URL." }
    fun resumePosition(position: Long, duration: Long): Long = when {
        position < 0 -> 0
        duration > 0 && position >= duration - 3000 -> 0
        else -> position
    }
    fun validLoop(a: Long, b: Long): Boolean = a >= 0 && b > a && b - a >= 500
}
