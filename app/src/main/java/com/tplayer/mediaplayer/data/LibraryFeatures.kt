package com.tplayer.mediaplayer.data

import com.tplayer.mediaplayer.playback.PlaybackPolicy
import java.security.MessageDigest

internal object FolderSort {
    const val PREFIX = "libraryFolderSort_"
    const val LIMIT = 512
    private val keyPattern = Regex("libraryFolderSort_[0-9a-f]{64}")
    private val valuePattern = Regex("(0|[1-9][0-9]*):[01]")

    fun key(folder: IndexedFolder): String {
        val identity = "${folder.volume}\u0000${folder.path.trim('/')}"
        val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        return buildString(PREFIX.length + hash.size * 2) {
            append(PREFIX)
            for (byte in hash) {
                val value = byte.toInt() and 255
                append("0123456789abcdef"[value ushr 4]); append("0123456789abcdef"[value and 15])
            }
        }
    }

    fun validKey(key: String) = keyPattern.matches(key)

    fun decode(value: Any?): Pair<MediaSort, Boolean>? {
        if (value !is String || value.length > 12 || !valuePattern.matches(value)) return null
        val ordinal = value.substringBefore(':').toIntOrNull() ?: return null
        return (MediaSort.values().getOrNull(ordinal) ?: return null) to value.endsWith(":1")
    }

    fun encode(display: LibraryDisplay) = "${display.sort.ordinal}:${if (display.descending) 1 else 0}"
}

internal object ContinueWatching {
    fun select(history: List<History>): List<History> =
        history.asSequence().filter { BackupFormat.local(it.uri) && it.duration > 0 &&
            PlaybackPolicy.resumePosition(it.position, it.duration) > 0 }
            .sortedWith(compareByDescending<History> { it.updatedAt }.thenBy { it.uri })
            .distinctBy { it.uri }.filter(::indexedOrTypedVideo).take(10).toList()

    private val videoExtensions = setOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "3gp", "3g2", "ts", "mts", "m2ts", "mpg", "mpeg", "flv", "wmv", "ogv")
    private fun indexedOrTypedVideo(item: History): Boolean = item.mimeType.startsWith("video/", true) ||
        (item.mimeType in setOf("", "application/octet-stream") && (
            (item.uri.startsWith("content://media/") && "/video/media/" in item.uri) ||
                item.title.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT) in videoExtensions))
}
