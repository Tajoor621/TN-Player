package com.tplayer.mediaplayer.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.URI

/** A merge-only, local-source backup. Network credentials and media bytes never enter the export. */
data class BackupPlaylist(val name: String, val items: List<Favorite>)
data class LibraryBackup(val preferences: Map<String, Any>, val favorites: List<Favorite>,
    val playlists: List<BackupPlaylist>, val bookmarks: List<Bookmark>) {
    fun summary() = "${favorites.size} favorites, ${playlists.size} playlists, ${bookmarks.size} bookmarks. Existing collections are kept; matching entries are merged. Rechoose files/folders if Android access is no longer granted."
}
object BackupFormat {
    const val MAX_BYTES = 2 * 1024 * 1024
    private const val MAX_ENTRIES = 10000
    fun local(uri: String): Boolean = try {
        val parsed = URI(uri)
        uri.length <= 8192 && uri.none { it.code < 32 } && when (parsed.scheme?.lowercase()) {
            "content" -> !parsed.authority.isNullOrBlank()
            "file" -> parsed.path?.startsWith('/') == true
            else -> false
        }
    } catch (_: Exception) { false }
    fun read(input: InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer); if (count < 0) break
            require(output.size() + count <= MAX_BYTES) { "Backup is larger than 2 MB." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
    private fun text(json: JSONObject, key: String, max: Int = 1024): String {
        val value = json.getString(key)
        require(value.length <= max && value.none { it == '\u0000' }) { "Invalid backup text." }
        return value
    }
    private fun entry(json: JSONObject): Favorite {
        val uri = text(json, "uri", 8192); require(local(uri)) { "Only local media references may be restored." }
        return Favorite(uri, text(json, "title"), text(json, "mime", 128))
    }
    private fun json(value: Favorite) = JSONObject().put("uri", value.uri).put("title", value.title).put("mime", value.mimeType)
    fun encode(value: LibraryBackup): ByteArray {
        // Bound serialized entries before building the complete JSON tree/string.
        var estimated = JSONObject(value.preferences).toString().toByteArray(Charsets.UTF_8).size + 256
        var count = 0
        fun budget(entry: JSONObject) {
            estimated += entry.toString().toByteArray(Charsets.UTF_8).size + 2
            require(estimated <= MAX_BYTES && ++count <= MAX_ENTRIES) { "Collections exceed backup limits." }
        }
        for (entry in value.favorites) if (local(entry.uri)) budget(json(entry))
        require(value.playlists.size <= 200)
        for (list in value.playlists) {
            budget(JSONObject().put("name", list.name))
            estimated += 32
            for (entry in list.items) if (local(entry.uri)) budget(json(entry))
        }
        for (entry in value.bookmarks) if (local(entry.uri)) budget(json(Favorite(entry.uri, entry.title)).put("position", entry.position))
        val root = JSONObject().put("app", "com.tplayer.mediaplayer").put("version", 1)
            .put("preferences", JSONObject(value.preferences))
            .put("favorites", JSONArray(value.favorites.filter { local(it.uri) }.map { json(it) }))
            .put("playlists", JSONArray(value.playlists.map { list -> JSONObject().put("name", list.name)
                .put("items", JSONArray(list.items.filter { local(it.uri) }.map { json(it) })) }))
            .put("bookmarks", JSONArray(value.bookmarks.filter { local(it.uri) }.map {
                json(Favorite(it.uri, it.title)).put("position", it.position)
            }))
        val bytes = root.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "Collections exceed the 2 MB backup limit." }
        decode(bytes) // Apply the same bounds to an export as an import.
        return bytes
    }
    fun decode(bytes: ByteArray): LibraryBackup {
        require(bytes.size <= MAX_BYTES) { "Backup is larger than 2 MB." }
        val text = bytes.toString(Charsets.UTF_8)
        var depth = 0; var quoted = false; var escaped = false
        for (character in text) {
            if (quoted) {
                if (escaped) escaped = false else if (character == '\\') escaped = true else if (character == '"') quoted = false
            } else when (character) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 32) { "Backup nesting is too deep." } }
                '}', ']' -> { depth--; require(depth >= 0) { "Invalid backup structure." } }
            }
        }
        require(depth == 0 && !quoted) { "Incomplete backup structure." }
        val root = JSONObject(text)
        require(root.getString("app") == "com.tplayer.mediaplayer" && root.getInt("version") == 1) { "Unsupported backup format." }
        var count = 0
        fun entries(array: JSONArray): List<JSONObject> {
            count += array.length(); require(count <= MAX_ENTRIES) { "Too many backup entries." }
            return (0 until array.length()).map { array.getJSONObject(it) }
        }
        val favorites = entries(root.getJSONArray("favorites")).map { entry(it) }
        val lists = root.getJSONArray("playlists"); require(lists.length() <= 200) { "Too many playlists." }
        val playlists = entries(lists).map { list ->
            val name = text(list, "name"); require(name.isNotBlank()) { "Playlist name is empty." }
            BackupPlaylist(name, entries(list.getJSONArray("items")).map { entry(it) })
        }
        val bookmarks = entries(root.getJSONArray("bookmarks")).map {
            val media = entry(it); val position = it.getLong("position")
            require(position in 0..604800000L) { "Invalid bookmark position." }
            Bookmark(uri = media.uri, title = media.title, position = position)
        }
        val prefs = root.getJSONObject("preferences"); val values = mutableMapOf<String, Any>()
        for (key in listOf("gestureVolumeRight", "doubleTapPlayPause", "showPlayerClock", "showPlayerBattery", "showRemainingTime", "autoPip", "resume", "hidden", "gestures", "amoled", "embeddedSubtitleStyles", "subtitleBackground", "libraryGrid", "libraryDescending", "fieldDuration", "fieldSize", "fieldResolution", "fieldPath", "fieldDate", "fieldPlayed", "fieldFrameRate", "fieldType", "backgroundPlayback", "videoDisplay", "showPlayerShortcuts", "mirror", "verticalFlip", "playerNight"))
            if (prefs.has(key)) { require(prefs.get(key) is Boolean); values[key] = prefs.getBoolean(key) }
        for ((key, range) in mapOf("decoderPreference" to 0..2, "theme" to -1..2, "subtitleSize" to 0..2, "subtitleFont" to 0..2, "subtitlePosition" to 0..35, "aspectRatio" to 0..2, "libraryMode" to 0..2, "librarySort" to 0..9, "minimumSeconds" to 0..3600, "playerOrientation" to 0..2)) {
            if (prefs.has(key)) { val number = prefs.getInt(key); require(number in range && (key != "theme" || number != 0)); values[key] = number }
        }
        if (prefs.has("playerShortcuts")) {
            val keys = prefs.getString("playerShortcuts")
            require(keys.length <= 512 && keys.split(',').all { key -> key.isEmpty() || PlayerShortcut.values().any { it.name == key } })
            values["playerShortcuts"] = keys
        }
        if (prefs.has("subtitleColor")) { val color = prefs.getInt("subtitleColor"); require(color in setOf(-1, -256, -16711681)); values["subtitleColor"] = color }
        if (prefs.has("seekMs")) { val seek = prefs.getLong("seekMs"); require(seek in setOf(5000L, 10000L, 30000L)); values["seekMs"] = seek }
        if (values["amoled"] == true) values["theme"] = 2
        else if (values["theme"] != null && values["theme"] != 2) values["amoled"] = false
        return LibraryBackup(values, favorites, playlists, bookmarks)
    }
}
class BackupRepository(private val db: MediaDatabase) {
    suspend fun snapshot(preferences: Map<String, Any>): LibraryBackup = withContext(Dispatchers.IO) {
        db.withTransaction {
            val raw = db.raw(); val lists = raw.backupPlaylists().sortedBy { it.id }; require(lists.size <= 200)
            val favorites = raw.backupFavorites(); val marks = raw.backupBookmarks()
            require(favorites.size <= 10000 && marks.size <= 10000)
            LibraryBackup(preferences, favorites.filter { BackupFormat.local(it.uri) }, lists.map { list ->
                val items = raw.backupItems(list.id); require(items.size <= 10000)
                BackupPlaylist(list.name, items.filter { BackupFormat.local(it.uri) }.map { Favorite(it.uri, it.title, it.mimeType) })
            }, marks.filter { BackupFormat.local(it.uri) })
        }
    }
    suspend fun merge(backup: LibraryBackup) = withContext(Dispatchers.IO) {
        // Revalidate before making any writes, even for programmatic callers.
        val value = BackupFormat.decode(BackupFormat.encode(backup))
        db.withTransaction {
            val raw = db.raw()
            val lists = raw.playlists().sortedBy { it.id }.groupBy { it.name }
                .mapValues { (_, entries) -> entries.map { it.id }.toMutableList() }.toMutableMap()
            val occurrences = mutableMapOf<String, Int>()
            for (favorite in value.favorites) raw.addFavorite(favorite)
            for (list in value.playlists) {
                val ordinal = occurrences[list.name] ?: 0
                occurrences[list.name] = ordinal + 1
                val matching = lists.getOrPut(list.name) { mutableListOf() }
                val id = matching.getOrNull(ordinal) ?: raw.createPlaylist(Playlist(name = list.name)).also { matching += it }
                for (item in list.items) raw.addToPlaylist(PlaylistItem(id, item.uri, item.title, mimeType = item.mimeType))
            }
            val marks = raw.backupBookmarks(); require(marks.size <= 10000) { "Too many existing bookmarks to merge safely." }
            val existing = marks.map { it.uri to it.position }.toMutableSet()
            for (mark in value.bookmarks) if (existing.add(mark.uri to mark.position)) raw.addBookmark(mark.copy(id = 0))
        }
    }
}
