package com.tplayer.mediaplayer.playback

/** Unknown/denied vendor output is inconclusive, never a successful zero-lock measurement. */
object BenchmarkWakeLocks {
    fun count(dump: String, uid: Int): Int? {
        if (dump.contains("Permission Denial", true) || dump.contains("permission denied", true)) return null
        val lines = dump.lines()
        val start = lines.indexOfFirst { Regex("^Wake Locks: size=\\d+\\s*$").matches(it.trim()) }
        if (start < 0) return null
        val size = lines[start].substringAfter("size=").trim().toIntOrNull() ?: return null
        val entries = lines.drop(start + 1).takeWhile { it.isBlank() || it.first().isWhitespace() }.filter { it.contains("uid=") }
        if (entries.size != size) return null
        return entries.count { Regex("\\buid=$uid\\b").containsMatchIn(it) && it.contains("ExoPlayer:WakeLock") }
    }
}
