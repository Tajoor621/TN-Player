package com.tplayer.mediaplayer.data

import androidx.room.withTransaction
import com.tplayer.mediaplayer.security.SecretCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaDao(private val db: MediaDatabase, private val codec: SecretCodec) {
    private val raw = db.raw()
    private fun id(uri: String) = if (network(uri)) "tpuri:v1:" + codec.identifier(uri) else uri
    private fun network(uri: String) = uri.startsWith("https:", true) || uri.startsWith("http:", true) || uri.startsWith("rtsp:", true)
    private fun title(value: String) = value.replace(Regex("(?i)(?:https?|rtsp)://\\S+"), "[network source]").take(1024)
    private suspend fun protect(uri: String): String {
        val key = id(uri)
        if (key != uri) raw.putSecret(UriSecret(key, codec.encrypt(uri, key)))
        return key
    }
    private suspend fun reveal(key: String): String = if (key.startsWith("tpuri:v1:")) {
        val secret = checkNotNull(raw.secret(key)) { "Saved source is unavailable. Please add it again." }
        codec.decrypt(secret.ciphertext, key)
    } else key
    private suspend fun <T> read(action: suspend () -> T): T = withContext(Dispatchers.IO) { action() }
    private suspend fun <T> write(action: suspend () -> T): T = withContext(Dispatchers.IO) { db.withTransaction { val result = action(); raw.collectSecrets(); result } }
    suspend fun history() = read { raw.history().map { it.copy(uri = reveal(it.uri)) } }
    suspend fun historyFor(uri: String) = read { raw.historyFor(id(uri))?.let { it.copy(uri = reveal(it.uri)) } }
    suspend fun record(value: History) = write {
        val key = protect(value.uri)
        val previous = raw.historyFor(key)
        raw.record(value.copy(uri = key, title = title(value.title),
            duration = value.duration.takeIf { it > 0 } ?: previous?.duration ?: 0,
            mimeType = value.mimeType.ifBlank { previous?.mimeType.orEmpty() }))
        raw.trimHistory()
    }
    suspend fun clearHistory() = write { raw.clearHistory() }
    suspend fun favorites() = read { raw.favorites().map { it.copy(uri = reveal(it.uri)) } }
    suspend fun isFavorite(uri: String) = read { raw.isFavorite(id(uri)) }
    suspend fun addFavorite(value: Favorite) = write { raw.addFavorite(value.copy(uri = protect(value.uri), title = title(value.title))) }
    suspend fun removeFavorite(uri: String) = write { raw.removeFavorite(id(uri)) }
    suspend fun streams() = read { raw.streams().map { it.copy(uri = reveal(it.uri)) } }
    suspend fun saveStream(value: SavedStream) = write { raw.saveStream(value.copy(uri = protect(value.uri), title = title(value.title))) }
    suspend fun removeStream(uri: String) = write { raw.removeStream(id(uri)) }
    suspend fun playlists() = read { raw.playlists() }
    suspend fun createPlaylist(value: Playlist) = write { raw.createPlaylist(value) }
    suspend fun deletePlaylist(id: Long) = write { raw.deletePlaylist(id) }
    suspend fun playlistItems(id: Long) = read { raw.playlistItems(id).map { it.copy(uri = reveal(it.uri)) } }
    suspend fun addToPlaylist(value: PlaylistItem) = write { raw.addToPlaylist(value.copy(uri = protect(value.uri), title = title(value.title))) }
    suspend fun removeFromPlaylist(playlistId: Long, uri: String) = write { raw.removeFromPlaylist(playlistId, id(uri)) }
    suspend fun addBookmark(value: Bookmark) = write { raw.addBookmark(value.copy(uri = protect(value.uri), title = title(value.title))) }
    suspend fun bookmarks(uri: String) = read { raw.bookmarks(id(uri)).map { it.copy(uri = reveal(it.uri)) } }
}
