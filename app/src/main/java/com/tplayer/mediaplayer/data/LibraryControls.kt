package com.tplayer.mediaplayer.data

import android.content.Context
import com.tplayer.mediaplayer.playback.PlaybackPolicy
import java.security.MessageDigest

class FolderOrders(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("folder-orders", Context.MODE_PRIVATE)
    private fun key(folder: String) = MessageDigest.getInstance("SHA-256").digest(folder.toByteArray()).joinToString("") { "%02x".format(it) }
    fun read(folder: String, default: LibraryDisplay): LibraryDisplay {
        val value = prefs.getString(key(folder), null)?.split(':') ?: return default
        val order = value.firstOrNull()?.let { name -> MediaSort.values().firstOrNull { it.name == name } } ?: return default
        return default.copy(sort = order, descending = value.getOrNull(1) == "true")
    }
    fun save(folder: String, value: LibraryDisplay) { prefs.edit().putString(key(folder), "${value.sort.name}:${value.descending}").apply() }
}
object LibrarySearch {
    fun matches(title: String, query: String) = title.contains(query.trim(), ignoreCase = true)
}
object ContinueWatching {
    fun select(history: List<History>): List<History> = history.filter { it.duration <= 0 || PlaybackPolicy.resumePosition(it.position, it.duration) > 0 }
        .filter { it.position > 0 }.sortedWith(compareByDescending<History> { it.updatedAt }.thenBy { it.uri }).distinctBy { it.uri }
    fun items(history: List<History>) = select(history).map {
        LibraryItem(it.uri, it.title, "Resume at ${mediaTime(PlaybackPolicy.resumePosition(it.position, it.duration))}" +
            if (it.duration > 0) " / ${mediaTime(it.duration)}" else " · duration unknown", it.mimeType)
    }
}
