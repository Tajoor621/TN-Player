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
    TITLE("Title"), DATE("Date"), PLAYED("Played time"), STATUS("Status"), LENGTH("Length"), SIZE("Size"),
    RESOLUTION("Resolution"), PATH("Path"), FRAME_RATE("Capture frame rate"), TYPE("Type")
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
    val count: Int = 0, val resumeAt: Long = 0, val excludedFolder: Boolean = false
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
        return LibraryItem(uri, title, fields.joinToString(" · "), mime, artwork, if (directory) folder else null, sourceFolder = folder, excludedFolder = excludedFolder)
    }
}
data class CatalogStats(val raw: Int = 0, val named: Int = 0, val visible: Int = 0,
    val duration: Int = 0, val scoped: Int = 0, val grouped: Int = 0, val searched: Int = 0)

data class CatalogPage(val entries: List<CatalogEntry>, val total: Int, val nextCursor: CatalogEntry?, val before: Int, val accessibleFiles: Int = 0, val stats: CatalogStats = CatalogStats(), val excludedCounts: Map<IndexedFolder, Int> = emptyMap()) {
    val hasMore get() = nextCursor != null
}

/** One ordered page is buffered, regardless of depth. Directory aggregates are separate scan metadata. */
class CatalogCollector(private val display: LibraryDisplay, private val current: IndexedFolder?,
    private val query: String, private val hidden: Boolean, private val after: CatalogEntry?, private val pageSize: Int, private val excluded: Set<IndexedFolder> = emptySet(), private val showExcluded: Boolean = false) {
    init { require(pageSize in 1..5000) }
    private val order = compareByDescending<CatalogEntry> { it.directory }.then(comparator(display))
    private val page = PriorityQueue<CatalogEntry>(pageSize, Comparator { a, b -> order.compare(b, a) })
    private val folders = mutableMapOf<IndexedFolder, CatalogEntry>()
    private var total = 0
    private var before = 0
    private var raw = 0
    private var named = 0
    private var visible = 0
    private var duration = 0
    private var scoped = 0
    private var groupedFiles = 0
    private val excludedCounts = mutableMapOf<IndexedFolder, Int>()
    internal val bufferedRows get() = page.size
    private fun keep(entry: CatalogEntry) {
        total++
        if (after != null && order.compare(entry, after) <= 0) { before++; return }
        if (page.size < pageSize) page.add(entry)
        else if (order.compare(entry, page.peek()) < 0) { page.poll(); page.add(entry) }
    }
    fun add(entry: CatalogEntry) {
        raw++
        val matching = if (excluded.isEmpty()) emptyList() else FolderExclusions.matches(entry.folder, excluded).toList()
        matching.forEach { excludedCounts[it] = (excludedCounts[it] ?: 0) + 1 }
        if (!hidden && (entry.title.startsWith('.') || entry.folder.path.startsWith('.') || "/." in entry.folder.path)) return
        named++
        if (matching.isNotEmpty() && !showExcluded) return
        visible++
        if (entry.duration < display.minimumSeconds * 1000L) return
        duration++
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
        scoped++
        if (key != null) {
            val old = folders[key]
            folders[key] = if (old == null) entry.copy(uri = "tnfolder:${key.volume}:${key.path}", title = key.name, folder = key, count = 1, mime = "", artwork = null, excludedFolder = excluded.isNotEmpty() && FolderExclusions.matches(key, excluded).any())
                else old.copy(count = old.count + 1, bytes = old.bytes + entry.bytes, duration = old.duration + entry.duration,
                    date = maxOf(old.date, entry.date), width = if (entry.width.toLong() * entry.height > old.width.toLong() * old.height) entry.width else old.width,
                    height = if (entry.width.toLong() * entry.height > old.width.toLong() * old.height) entry.height else old.height,
                    lastPlayed = maxOf(old.lastPlayed, entry.lastPlayed), state = maxOf(old.state, entry.state), frameRate = maxOf(old.frameRate, entry.frameRate))
        } else { groupedFiles++; if (entry.title.contains(query, true)) keep(entry) }
    }
    fun finish(): CatalogPage {
        for (folder in folders.values) {
            if (folder.title.contains(query, true) || folder.folder.displayPath.contains(query, true)) keep(folder)
        }
        val grouped = groupedFiles + folders.size
        folders.clear()
        val rows = page.sortedWith(order)
        return CatalogPage(rows, total, rows.lastOrNull()?.takeIf { total - before > rows.size }, before, raw,
            CatalogStats(raw, named, visible, duration, scoped, grouped, total), excludedCounts.toMap())
    }
    companion object {
        fun comparator(display: LibraryDisplay): Comparator<CatalogEntry> {
            val primary = Comparator<CatalogEntry> { a, b -> when (display.sort) {
                MediaSort.TITLE -> a.title.lowercase(Locale.ROOT).compareTo(b.title.lowercase(Locale.ROOT))
                MediaSort.DATE -> a.date.compareTo(b.date)
                MediaSort.PLAYED -> a.lastPlayed.compareTo(b.lastPlayed)
                MediaSort.STATUS -> a.state.compareTo(b.state)
                MediaSort.LENGTH -> a.duration.compareTo(b.duration)
                MediaSort.SIZE -> a.bytes.compareTo(b.bytes)
                MediaSort.RESOLUTION -> (a.width.toLong() * a.height).compareTo(b.width.toLong() * b.height)
                MediaSort.PATH -> a.folder.displayPath.lowercase(Locale.ROOT).compareTo(b.folder.displayPath.lowercase(Locale.ROOT))
                MediaSort.FRAME_RATE -> a.frameRate.compareTo(b.frameRate)
                MediaSort.TYPE -> a.mime.compareTo(b.mime)
            } }
            return (if (display.descending) Comparator<CatalogEntry> { a, b -> primary.compare(b, a) } else primary)
                .thenBy { it.title.lowercase(Locale.ROOT) }.thenBy { it.uri }
        }
    }
}

internal fun catalogCollector(display: LibraryDisplay, folder: IndexedFolder?, query: String,
    hidden: Boolean, after: CatalogEntry?, allFolders: Boolean, excluded: Set<IndexedFolder> = emptySet(), showExcluded: Boolean = false): CatalogCollector {
    val global = allFolders && query.isNotBlank()
    return CatalogCollector(if (global) display.copy(mode = MediaViewMode.FILES) else display,
        if (global) null else folder, query, hidden, after, 200, excluded, showExcluded)
}

class MediaCatalog(private val context: Context) {
    suspend fun page(video: Boolean, display: LibraryDisplay, folder: IndexedFolder?, query: String,
        hidden: Boolean, history: List<History>, after: CatalogEntry? = null, allFolders: Boolean = false,
        excluded: Set<IndexedFolder> = emptySet(), showExcluded: Boolean = false): CatalogPage = withContext(Dispatchers.IO) {
        val collector = catalogCollector(display, folder, query, hidden, after, allFolders, excluded, showExcluded)
        scan(video, history, collector::add)
        collector.finish()
    }

    internal suspend fun folderItems(folder: IndexedFolder, display: LibraryDisplay, history: List<History>,
        excluded: Set<IndexedFolder>, showExcluded: Boolean, showDotNames: Boolean): CatalogPage = withContext(Dispatchers.IO) {
        val collector = CatalogCollector(display.copy(mode = MediaViewMode.FILES, minimumSeconds = 0), folder,
            "", showDotNames, null, FolderQueue.LIMIT, excluded, showExcluded)
        for (video in listOf(true, false)) try { scan(video, history, collector::add, folder) } catch (_: SecurityException) { }
        collector.finish()
    }

    internal suspend fun indexedFolder(uri: String): IndexedFolder? = withContext(Dispatchers.IO) {
        val source = android.net.Uri.parse(uri)
        if (source.authority != "media") return@withContext null
        val columns = if (Build.VERSION.SDK_INT >= 29) arrayOf("volume_name", "relative_path") else arrayOf("_data")
        try {
            context.contentResolver.query(source, columns, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val volume = if (columns.size == 2) c.getString(0).orEmpty().ifBlank { "external_primary" } else "external"
                val path = if (columns.size == 2) c.getString(1).orEmpty() else c.getString(0).orEmpty().substringBeforeLast('/', "")
                IndexedFolder(volume, path.trim('/').let { if (it.isEmpty()) "" else "$it/" })
            }
        } catch (_: SecurityException) { null } catch (_: IllegalArgumentException) { null }
    }

    private suspend fun scan(video: Boolean, history: List<History>, add: (CatalogEntry) -> Unit, folder: IndexedFolder? = null) {
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = mutableListOf("_id", "_display_name", "duration", "_size", "mime_type", "date_modified")
        if (Build.VERSION.SDK_INT >= 29) projection += listOf("relative_path", "volume_name") else projection += "_data"
        if (video) { projection += listOf("width", "height"); if (Build.VERSION.SDK_INT >= 30) projection += "capture_framerate" }
        else projection += "album_id"
        val recent = history.associateBy { it.uri }
        val selection = if (Build.VERSION.SDK_INT < 29 || folder == null) null
            else if (folder.path.isEmpty()) "volume_name = ? AND (relative_path IS NULL OR relative_path = '')"
            else "volume_name = ? AND relative_path = ?"
        val args = if (selection == null || folder == null) null
            else if (folder.path.isEmpty()) arrayOf(folder.volume) else arrayOf(folder.volume, folder.path)
        checkNotNull(context.contentResolver.query(collection, projection.toTypedArray(), selection, args, "_id DESC")) { "Android's media index is unavailable." }.use { c ->
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
                val prior = recent[uri]
                val duration = number("duration").coerceAtLeast(0)
                val state = if (prior == null) 0 else if (prior.duration > 0 && prior.position > 0 && prior.position >= prior.duration - minOf(5000, prior.duration / 20)) 2 else 1
                val rate = string("capture_framerate").toFloatOrNull()?.takeIf { it.isFinite() && it > 0 } ?: 0f
                add(CatalogEntry(uri, string("_display_name").ifBlank { "Untitled" }, key, string("mime_type"),
                    number("_size").coerceAtLeast(0), duration, number("date_modified") * 1000L,
                    number("width").toInt(), number("height").toInt(), rate, prior?.updatedAt ?: 0, state,
                    if (!video && number("album_id") > 0) "content://media/$volume/audio/albumart/${number("album_id")}" else null,
                    resumeAt = prior?.let { com.tplayer.mediaplayer.playback.PlaybackPolicy.resumePosition(it.position, it.duration) } ?: 0))
            }
        }
    }
}
