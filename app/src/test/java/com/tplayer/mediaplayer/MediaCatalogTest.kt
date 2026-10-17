package com.tplayer.mediaplayer

import com.tplayer.mediaplayer.data.*
import org.junit.Assert.*
import org.junit.Test

class MediaCatalogTest {
    private fun entry(id: Int, volume: String = "external_primary", path: String = "Movies/Travel/", title: String = "Film $id", bytes: Long = id.toLong(), duration: Long = 12000) =
        CatalogEntry("content://media/$volume/video/media/$id", title, IndexedFolder(volume, path), "video/mp4", bytes, duration)
    private fun collect(rows: List<CatalogEntry>, display: LibraryDisplay = LibraryDisplay(), folder: IndexedFolder? = null,
        query: String = "", offset: Int = 0, pageSize: Int = 200, hidden: Boolean = false): CatalogPage {
        val c = CatalogCollector(display, folder, query, hidden, offset, pageSize); rows.forEach(c::add); return c.finish()
    }
    @Test fun countsAndOpensSameNamedFoldersOnDifferentVolumesWithoutMergingThem() {
        val rows = listOf(entry(1), entry(2), entry(3, "SD-card"), entry(4, path = "Downloads/Travel/"))
        val folders = collect(rows)
        assertEquals(3, folders.total)
        assertEquals(listOf(1, 1, 2), folders.entries.map { it.count }.sorted())
        val target = IndexedFolder("external_primary", "Movies/Travel/")
        assertEquals(listOf("Film 1", "Film 2"), collect(rows, folder = target).entries.map { it.title })
        assertEquals(1, collect(rows, query = "Downloads").total)
    }
    @Test fun hierarchicalNavigationUsesPathBoundariesAndCountsDescendants() {
        val rows = listOf(entry(1, path = "Movies/A/"), entry(2, path = "Movies/A/Child/"), entry(3, path = "Movies/AB/"))
        val display = LibraryDisplay(mode = MediaViewMode.ALL_FOLDERS)
        assertEquals(3, collect(rows, display).entries.single().count)
        assertEquals(2, collect(rows, display, IndexedFolder("external_primary", "Movies/")).total)
        val inside = collect(rows, display, IndexedFolder("external_primary", "Movies/A/"))
        assertEquals(2, inside.total)
        assertTrue(inside.entries.first().directory)
        assertEquals("Film 1", inside.entries.last().title)
        assertEquals(IndexedFolder("external_primary", "Movies/"), IndexedFolder("external_primary", "Movies/A/").parent())
    }
    @Test fun numericSortAndPaginationAreGlobalStableAndDoNotSkipATiedItem() {
        val rows = (1..1000).map { entry(it, bytes = (it % 9).toLong()) }.reversed()
        val display = LibraryDisplay(mode = MediaViewMode.FILES, sort = MediaSort.SIZE, descending = true)
        val expected = rows.sortedWith(CatalogCollector.comparator(display))
        val first = collect(rows, display)
        val second = collect(rows, display, offset = first.nextOffset)
        assertEquals(1000, first.total); assertTrue(first.hasMore)
        assertEquals(expected.take(400), first.entries + second.entries)
        assertEquals(400, second.nextOffset)
    }
    @Test fun filtersAndUnknownMetadataDoNotCreateFakeEntriesOrExposeHiddenItems() {
        val rows = listOf(entry(1, title = ".secret"), entry(2, path = "Movies/.private/"), entry(3, duration = 5000), entry(4, duration = 60000))
        assertEquals(1, collect(rows, LibraryDisplay(minimumSeconds = 10)).entries.single().count)
        assertEquals(4, collect(rows, LibraryDisplay(mode = MediaViewMode.FILES), hidden = true).total)
        assertEquals(0, collect(rows, query = "missing").total)
        assertEquals(0, collect(emptyList()).total)
        val text = entry(4).item(LibraryDisplay(frameRate = true)).detail
        assertTrue(text.contains("Capture fps unknown")); assertFalse(text.contains("0.0 capture fps"))
    }
}
