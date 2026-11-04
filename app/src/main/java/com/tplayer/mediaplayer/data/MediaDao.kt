package com.tplayer.mediaplayer.data

import androidx.room.withTransaction
import com.tplayer.mediaplayer.security.SecretCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaDao(private val db: MediaDatabase, private val codec: SecretCodec) {
    private val raw = db.raw()
    private fun id(uri: String) = if (network(uri)) "tpuri:v1:" + codec.identifier(uri) else uri
    private fun network(uri: String) = uri.startsWith("https:", true) || uri.startsWith("http:", true) || uri.startsWith("rtsp:", true) || uri.startsWith("tnnet:", true)
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
    suspend fun history() = read { raw.history().map { it.copy(uri = reveal(it.uri)) }.filter { !db.isPrivate(it.uri)() } }
    suspend fun historyFor(uri: String) = read { raw.historyFor(id(uri))?.takeIf { !db.isPrivate(uri)() }?.let { it.copy(uri = reveal(it.uri)) } }
    suspend fun record(value: History) { if (db.isPrivate(value.uri)()) return; write {
        val key = protect(value.uri)
        val previous = raw.historyFor(key)
        raw.record(value.copy(uri = key, title = title(value.title),
            duration = value.duration.takeIf { it > 0 } ?: previous?.duration ?: 0,
            mimeType = value.mimeType.ifBlank { previous?.mimeType.orEmpty() }))
        raw.trimHistory()
    } }
    suspend fun clearHistory() = write { raw.clearHistory() }
    suspend fun favorites() = read { raw.favorites().map { it.copy(uri = reveal(it.uri)) }.filter { !db.isPrivate(it.uri)() } }
    suspend fun isFavorite(uri: String) = read { raw.isFavorite(id(uri)) }
    suspend fun addFavorite(value: Favorite) = write { check(!db.isPrivate(value.uri)()) { "Private media cannot be added to public favorites." }; raw.addFavorite(value.copy(uri = protect(value.uri), title = title(value.title))) }
    suspend fun removeFavorite(uri: String) = write { raw.removeFavorite(id(uri)) }
    suspend fun streams() = read { raw.streams().map { it.copy(uri = reveal(it.uri)) } }
    suspend fun saveStream(value: SavedStream) = write { raw.saveStream(value.copy(uri = protect(value.uri), title = title(value.title))) }
    suspend fun removeStream(uri: String) = write { raw.removeStream(id(uri)) }
    suspend fun playlists() = read { raw.playlists() }
    suspend fun createPlaylist(value: Playlist) = write { require(value.name.trim().length in 1..100); raw.createPlaylist(value.copy(name = value.name.trim(), position = raw.lastPlaylistPosition() + 1)) }
    suspend fun renamePlaylist(id: Long, name: String) = write { require(name.trim().length in 1..100); raw.renamePlaylist(id, name.trim()) }
    suspend fun movePlaylist(id: Long, delta: Int) = write {
        val values = raw.playlists().toMutableList(); val from = values.indexOfFirst { it.id == id }
        if (from >= 0) { val next = (from + delta).coerceIn(0, values.lastIndex); values.add(next, values.removeAt(from)); values.forEachIndexed { i, item -> raw.playlistPosition(item.id, i) } }
    }
    suspend fun moveItem(id: Long, uri: String, delta: Int) = write {
        val values = raw.playlistItems(id).toMutableList(); val from = values.indexOfFirst { it.uri == this.id(uri) }
        if (from >= 0) { val next = (from + delta).coerceIn(0, values.lastIndex); values.add(next, values.removeAt(from)); values.forEachIndexed { i, item -> raw.itemPosition(id, item.uri, i) } }
    }
    suspend fun deletePlaylist(id: Long) = write { raw.deletePlaylist(id) }
    suspend fun playlistItems(id: Long) = read { raw.playlistItems(id).map { it.copy(uri = reveal(it.uri)) }.filter { !db.isPrivate(it.uri)() } }
    suspend fun addToPlaylist(value: PlaylistItem) = write {
        check(!db.isPrivate(value.uri)()) { "Private media cannot enter public playlists." }
        val key = protect(value.uri); val existing = raw.playlistItems(value.playlistId).firstOrNull { it.uri == key }
        raw.addToPlaylist(value.copy(uri = key, title = title(value.title), position = existing?.position ?: (raw.lastItemPosition(value.playlistId) + 1)))
    }
    suspend fun removeFromPlaylist(playlistId: Long, uri: String) = write { raw.removeFromPlaylist(playlistId, id(uri)) }
    suspend fun addBookmark(value: Bookmark) = write { check(!db.isPrivate(value.uri)()) { "Private media cannot be bookmarked publicly." }; raw.addBookmark(value.copy(uri = protect(value.uri), title = title(value.title))) }
    suspend fun bookmarks(uri: String) = read { raw.bookmarks(id(uri)).map { it.copy(uri = reveal(it.uri)) }.filter { !db.isPrivate(it.uri)() } }
}
