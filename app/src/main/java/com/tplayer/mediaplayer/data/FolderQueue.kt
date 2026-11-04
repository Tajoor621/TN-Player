package com.tplayer.mediaplayer.data

import com.tplayer.mediaplayer.playback.PlaybackPolicy
import com.tplayer.mediaplayer.playback.QueueEntry
import java.util.UUID

internal data class FolderQueue(val entries: List<QueueEntry>, val start: Int) {
    companion object {
        const val LIMIT = 5000
        fun build(rows: List<LibraryItem>, tapped: String): FolderQueue? {
            require(rows.size <= LIMIT)
            val entries = rows.distinctBy { it.uri }.map { QueueEntry(it.uri, it.title.take(1024), it.mimeType.take(128)) }
            val start = entries.indexOfFirst { it.uri == tapped }
            if (start < 0) return null
            require(PlaybackPolicy.queueError(entries.map { it.uri }, entries.map { it.title }, entries.map { it.mimeType },
                maxItems = LIMIT, maxCharacters = 1_000_000) == null)
            require(entries.all {
                val scheme = it.uri.substringBefore(':').lowercase(java.util.Locale.ROOT)
                scheme in setOf("content", "file") || (scheme in setOf("https", "rtsp") && PlaybackPolicy.validateStream(it.uri) == null)
            })
            return FolderQueue(entries, start)
        }
    }
}

/** Avoids serializing thousands of media URIs into a single Activity Intent. */
internal object FolderQueueHandoff {
    private var pending: Pair<String, FolderQueue>? = null
    @Synchronized fun put(queue: FolderQueue): String = UUID.randomUUID().toString().also { pending = it to queue }
    @Synchronized fun take(token: String): FolderQueue? {
        val request = pending?.takeIf { it.first == token } ?: return null
        pending = null
        return request.second
    }
}
