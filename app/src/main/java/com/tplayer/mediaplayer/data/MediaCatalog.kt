package com.tplayer.mediaplayer.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.PriorityQueue

/** Storage identity is part of the key: two SD cards may have the same folder names. */
data class IndexedFolder(val volume: String, val path: String) {
    val name: String get() = path.trimEnd('/').substringAfterLast('/').ifBlank { storageName }
    val storageName: String get() = if (volume in setOf("external", "external_primary")) "Internal storage" else "Storage $volume"
    val displayPath: String get() = "$storageName / $path"
    fun parent(): IndexedFolder? {
        val trimmed = path.trimEnd('/')
        return if (trimmed.isEmpty()) null else copy(path = trimmed.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" })
    }
}
enum class MediaViewMode(val label: String) { ALL_FOLDERS("All folders"), FILES("Files"), FOLDERS("Folders") }
enum class MediaSort(val label: String) {
    TITLE("Name"), DATE("Date modified"), PLAYED("Played time"), STATUS("Status"), LENGTH("Duration"), SIZE("Size"),
    RESOLUTION("Resolution"), PATH("Path"), FRAME_RATE("Capture frame rate"), TYPE("Type"), DATE_ADDED("Date added"), WATCH_PROGRESS("Watch progress")
}
data class LibraryDisplay(
    val mode: MediaViewMode = MediaViewMode.FOLDERS, val grid: Boolean = false,
    val sort: MediaSort = MediaSort.TITLE, val descending: Boolean = false,
    val duration: Boolean = true, val size: Boolean = true, val resolution: Boolean = false,
    val path: Boolean = false, val date: Boolean = false, val played: Boolean = false,
    val frameRate: Boolean = false, val type: Boolean = false, val minimumSeconds: Int = 0
)
data class CatalogEntry(
    val uri: String, val title: String, val folder: IndexedFolder, val mime: String = "", val bytes: Long = 0,
    val duration: Long = 0, val date: Long = 0, val width: Int = 0, val height: Int = 0,
    val frameRate: Float = 0f, val lastPlayed: Long = 0, val state: Int = 0, val artwork: String? = null,
    val count: Int = 0, val resumeAt: Long = 0, val dateAdded: Long = 0, val watchPosition: Long = 0, val watchDuration: Long = 0
) {
    val directory get() = count > 0
    fun item(display: LibraryDisplay): LibraryItem {
        val fields = mutableListOf<String>()
        if (directory) fields += "$count media ${if (count == 1) "file" else "files"}"
        else {
            if (display.duration) fields += "%d:%02d".format(duration / 60000, duration / 1000 % 60)
            if (display.resolution && width > 0 && height > 0) fields += "${width}×$height"
            if (display.frameRate) fields += if (frameRate > 0) "%.1f capture fps".format(frameRate) else "Capture fps unknown"
            if (display.type) fields += mime.ifBlank { "Unknown type" }
            if (lastPlayed > 0) fields += if (resumeAt > 0) "Resume ${mediaTime(resumeAt)}" else "Played · start from beginning"
            else if (display.played) fields += "Unplayed"
        }
        if (display.size) fields += "%.1f MB".format(bytes / 1_000_000.0)
        if (display.date && date > 0) fields += java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(java.util.Date(date))
        // Disambiguate duplicate folder names even when the optional path field is off.
        if (directory || display.path) fields += folder.displayPath
        return LibraryItem(uri, title, fields.joinToString(" · "), mime, artwork, if (directory) folder else null)
    }
}
data class CatalogPage(val entries: List<CatalogEntry>, val total: Int, val nextOffset: Int) {
    val hasMore get() = nextOffset < total
}

/** Selection and sorting are independent of the provider's SQL dialect. Only a page prefix is retained. */
class CatalogCollector(private val display: LibraryDisplay, private val current: IndexedFolder?,
    private val query: String, private val hidden: Boolean, private val offset: Int, private val pageSize: Int) {
    init { require(offset >= 0 && pageSize in 1..500); require(offset <= Int.MAX_VALUE - pageSize) }
    private val order = comparator(display)
    private val files = PriorityQueue<CatalogEntry>(pageSize, Comparator { a, b -> order.compare(b, a) })
    private val folders = mutableMapOf<IndexedFolder, CatalogEntry>()
    private var totalFiles = 0
    private fun keep(entry: CatalogEntry) {
        totalFiles++
        if (files.size < offset + pageSize) files.add(entry)
        else if (order.compare(entry, files.peek()) < 0) { files.poll(); files.add(entry) }
    }
    fun add(entry: CatalogEntry) {
        if (!hidden && (entry.title.startsWith('.') || entry.folder.path.split('/').any { it.startsWith('.') })) return
        if (entry.duration < display.minimumSeconds * 1000L) return
        if (display.mode == MediaViewMode.FILES && current != null && entry.folder != current) return
        val key: IndexedFolder? = when {
            display.mode == MediaViewMode.FILES -> null
            display.mode == MediaViewMode.FOLDERS -> if (current == null) entry.folder else null
            current == null -> entry.folder.copy(path = "")
            entry.folder.volume != current.volume || !entry.folder.path.startsWith(current.path) -> return
            else -> entry.folder.path.removePrefix(current.path).substringBefore('/').takeIf { it.isNotEmpty() }
                ?.let { current.copy(path = current.path + it + "/") }
        }
        if (display.mode == MediaViewMode.FOLDERS && current != null && entry.folder != current) return
        if (key != null) {
            val old = folders[key]
            folders[key] = if (old == null) entry.copy(uri = "tnfolder:${key.volume}:${key.path}", title = key.name, folder = key, count = 1, mime = "", artwork = null)
                else old.copy(count = old.count + 1, bytes = old.bytes + entry.bytes, duration = old.duration + entry.duration,
                    date = maxOf(old.date, entry.date), dateAdded = maxOf(old.dateAdded, entry.dateAdded), watchPosition = old.watchPosition + entry.watchPosition, watchDuration = old.watchDuration + entry.watchDuration, width = if (entry.width.toLong() * entry.height > old.width.toLong() * old.height) entry.width else old.width,
                    height = if (entry.width.toLong() * entry.height > old.width.toLong() * old.height) entry.height else old.height,
                    lastPlayed = maxOf(old.lastPlayed, entry.lastPlayed), state = maxOf(old.state, entry.state), frameRate = maxOf(old.frameRate, entry.frameRate))
        } else if (LibrarySearch.matches(entry.title, query)) keep(entry)
    }
    fun finish(): CatalogPage {
        val directories = folders.values.filter { LibrarySearch.matches(it.title, query) || LibrarySearch.matches(it.folder.displayPath, query) }
        // Folders stay ahead of files. A page crossing the boundary uses the correct file offset.
        val all = (directories.sortedWith(order) + files.sortedWith(order)).drop(offset).take(pageSize)
        val total = directories.size + totalFiles
        return CatalogPage(all, total, minOf(offset + all.size, total))
    }
    companion object {
        fun comparator(display: LibraryDisplay): Comparator<CatalogEntry> {
            fun value(entry: CatalogEntry): Comparable<*>? = when (display.sort) {
                MediaSort.TITLE -> entry.title.takeIf { it.isNotBlank() }?.lowercase(Locale.ROOT)
                MediaSort.DATE -> entry.date.takeIf { it > 0 }
                MediaSort.DATE_ADDED -> entry.dateAdded.takeIf { it > 0 }
                MediaSort.PLAYED -> entry.lastPlayed.takeIf { it > 0 }
                MediaSort.STATUS -> entry.state
                MediaSort.LENGTH -> entry.duration.takeIf { it > 0 }
                MediaSort.SIZE -> entry.bytes.takeIf { it > 0 }
                MediaSort.RESOLUTION -> (entry.width.toLong() * entry.height).takeIf { entry.width > 0 && entry.height > 0 }
                MediaSort.PATH -> entry.folder.displayPath.lowercase(Locale.ROOT)
                MediaSort.FRAME_RATE -> entry.frameRate.takeIf { it.isFinite() && it > 0 }
                MediaSort.TYPE -> entry.mime.takeIf { it.isNotBlank() }?.lowercase(Locale.ROOT)
                MediaSort.WATCH_PROGRESS -> when {
                    entry.state == 0 -> 0.0
                    entry.state == 2 -> 1.0
                    entry.watchDuration > 0 -> (entry.watchPosition.toDouble() / entry.watchDuration).coerceIn(0.0, 1.0)
                    else -> null
                }
            }
            return Comparator<CatalogEntry> { a, b ->
                val first = value(a); val second = value(b)
                // Missing values are last in BOTH directions; ties retain stable deterministic order.
                @Suppress("UNCHECKED_CAST")
                val order = when { first == null && second == null -> 0; first == null -> 1; second == null -> -1
                    else -> (first as Comparable<Any>).compareTo(second) * if (display.descending) -1 else 1 }
                if (order != 0) order else a.title.lowercase(Locale.ROOT).compareTo(b.title.lowercase(Locale.ROOT)).takeIf { it != 0 } ?: a.uri.compareTo(b.uri)
            }
        }
    }
}

class MediaCatalog(private val context: Context) {
    suspend fun page(video: Boolean, display: LibraryDisplay, folder: IndexedFolder?, query: String,
        hidden: Boolean, history: List<History>, offset: Int = 0, progress: (Int) -> Unit = {}): CatalogPage = withContext(Dispatchers.IO) {
        val collector = CatalogCollector(display, folder, query, hidden, offset, 200)
        val privacy = com.tplayer.mediaplayer.security.PrivateFolder.get(context)
        val privateRoot = privacy.configuration()
        var scanned = 0
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = mutableListOf("_id", "_display_name", "duration", "_size", "mime_type", "date_modified", "date_added")
        if (Build.VERSION.SDK_INT >= 29) projection += listOf("relative_path", "volume_name") else projection += "_data"
        if (video) { projection += listOf("width", "height"); if (Build.VERSION.SDK_INT >= 30) projection += "capture_framerate" }
        else projection += "album_id"
        val recent = history.associateBy { it.uri }
        checkNotNull(context.contentResolver.query(collection, projection.toTypedArray(), null, null, "_id DESC")) { "Android's media index is unavailable." }.use { c ->
            fun string(name: String) = c.getColumnIndex(name).takeIf { it >= 0 }?.let { if (c.isNull(it)) "" else c.getString(it) }.orEmpty()
            fun number(name: String) = c.getColumnIndex(name).takeIf { it >= 0 }?.let { if (c.isNull(it)) 0L else c.getLong(it) } ?: 0L
            while (c.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val volume = if (Build.VERSION.SDK_INT >= 29) string("volume_name").ifBlank { "external_primary" } else "external"
                val path = if (Build.VERSION.SDK_INT >= 29) string("relative_path") else string("_data").substringBeforeLast('/', "").trimStart('/')
                val key = IndexedFolder(volume, path.trim('/').let { if (it.isEmpty()) "" else "$it/" })
                // Keep the original aggregate content URI stable for existing favorites,
                // playlists and history. Volume identity is carried separately in the folder key.
                val uri = ContentUris.withAppendedId(collection, number("_id")).toString()
                if (++scanned % 100 == 0) progress(scanned)
                if (privacy.matchesLocation(privateRoot, volume, key.path)) { privacy.publicClassification[uri] = false; continue }
                privacy.publicClassification[uri] = true
                val prior = recent[uri]
                val duration = number("duration").coerceAtLeast(0)
                val state = if (prior == null) 0 else if (prior.duration > 0 && prior.position > 0 && prior.position >= prior.duration - minOf(5000, prior.duration / 20)) 2 else 1
                val rate = string("capture_framerate").toFloatOrNull()?.takeIf { it.isFinite() && it > 0 } ?: 0f
                collector.add(CatalogEntry(uri, string("_display_name").ifBlank { "Untitled" }, key, string("mime_type"),
                    number("_size").coerceAtLeast(0), duration, number("date_modified") * 1000L,
                    number("width").toInt(), number("height").toInt(), rate, prior?.updatedAt ?: 0, state,
                    if (!video && number("album_id") > 0) "content://media/$volume/audio/albumart/${number("album_id")}" else null,
                    resumeAt = prior?.let { com.tplayer.mediaplayer.playback.PlaybackPolicy.resumePosition(it.position, it.duration) } ?: 0, dateAdded = number("date_added") * 1000L, watchPosition = prior?.position ?: 0, watchDuration = prior?.duration?.takeIf { it > 0 } ?: duration))
            }
        }
        progress(scanned); collector.finish()
    }
}
