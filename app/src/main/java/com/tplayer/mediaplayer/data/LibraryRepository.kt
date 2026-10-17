package com.tplayer.mediaplayer.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class LibraryItem(val uri: String, val title: String, val detail: String = "", val mimeType: String = "", val artworkUri: String? = null, val indexedFolder: IndexedFolder? = null, val downloadId: Long? = null)
data class LibraryPage(val items: List<LibraryItem>, val nextKey: Long, val hasMore: Boolean)
class LibraryRepository(private val context: Context) {
    suspend fun page(video: Boolean, before: Long = Long.MAX_VALUE, query: String = "", pageSize: Int = 200): LibraryPage = withContext(Dispatchers.IO) {
        require(pageSize in 1..500)
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val rows = mutableListOf<Pair<Long, LibraryItem>>()
        val needle = query.replace("!", "!!").replace("%", "!%").replace("_", "!_")
        val selection = "_id < ?" + if (query.isNotBlank()) " AND _display_name LIKE ? ESCAPE '!'" else ""
        val args = if (query.isNotBlank()) arrayOf(before.toString(), "%$needle%") else arrayOf(before.toString())
        context.contentResolver.query(collection, (listOf("_id", "_display_name", "duration", "_size", "mime_type") + if (video) emptyList() else listOf("album_id")).toTypedArray(), selection, args, "_id DESC")?.use { cursor ->
            while (rows.size <= pageSize && cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val duration = cursor.getLong(2).coerceAtLeast(0) / 1000
                val id = cursor.getLong(0)
                rows += id to LibraryItem(ContentUris.withAppendedId(collection, id).toString(), cursor.getString(1) ?: "Untitled",
                    "%d:%02d · %.1f MB".format(duration / 60, duration % 60, cursor.getLong(3) / 1_000_000.0), cursor.getString(4).orEmpty(), if (!video && cursor.getLong(5) > 0) "content://media/external/audio/albumart/${cursor.getLong(5)}" else null)
            }
        }
        val page = rows.take(pageSize)
        LibraryPage(page.map { it.second }, page.lastOrNull()?.first ?: before, rows.size > pageSize)
    }
}
