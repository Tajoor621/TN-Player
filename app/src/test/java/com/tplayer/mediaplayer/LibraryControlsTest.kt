package com.tplayer.mediaplayer

import com.tplayer.mediaplayer.data.*
import com.tplayer.mediaplayer.security.PrivatePaths
import com.tplayer.mediaplayer.security.PrivateRoot
import org.junit.Assert.*
import org.junit.Test

class LibraryControlsTest {
    private val folder = IndexedFolder("external_primary", "Movies/Test/")
    private val first = CatalogEntry("a", "Alpha", folder, "audio/aac", 10, 5000, 100, 10, 10, 24f, 1, 1,
        dateAdded = 200, watchPosition = 500, watchDuration = 5000)
    private val second = CatalogEntry("b", "Beta", folder.copy(path = "Music/Test/"), "video/mp4", 20, 10000, 200, 20, 20, 30f, 2, 2,
        dateAdded = 300, watchPosition = 10000, watchDuration = 10000)
    private val unknown = CatalogEntry("c", "", folder, state = 1)

    @Test fun allSortKeysReverseKnownValuesAndKeepMissingValuesLast() {
        for (key in MediaSort.values()) {
            val missing = key !in setOf(MediaSort.PATH, MediaSort.STATUS)
            for (reverse in listOf(false, true)) {
                val input = if (missing) listOf(second, unknown, first) else listOf(second, first)
                val order = input.sortedWith(CatalogCollector.comparator(LibraryDisplay(sort = key, descending = reverse)))
                assertEquals(key.name, if (reverse) listOf(second, first) else listOf(first, second), order.take(2))
                if (missing) assertEquals(key.name, unknown, order.last())
            }
        }
    }
    @Test fun searchTrimsAndMatchesCaseInsensitivelyWithinTheRequestedFolder() {
        fun scan(target: IndexedFolder?) = CatalogCollector(LibraryDisplay(mode = MediaViewMode.FILES), target, "  BeTa ", false, 0, 10)
            .apply { add(first); add(second) }.finish().entries
        assertEquals(emptyList<CatalogEntry>(), scan(folder))
        assertEquals(listOf(second), scan(null))
    }
    @Test fun continueWatchingOmitsCompletedAndUnstartedAndOrdersLatestFirst() {
        val older = History("file:/older", "Older", 5000, 12000, updatedAt = 10)
        val latest = History("file:/latest", "Latest", 4000, 0, updatedAt = 20)
        val done = History("file:/done", "Done", 10000, 11000, updatedAt = 30)
        val never = History("file:/never", "Never", 0, 20000, updatedAt = 40)
        assertEquals(listOf(latest, older), ContinueWatching.select(listOf(older, done, never, latest, older)))
        assertTrue(ContinueWatching.items(listOf(latest)).single().detail.contains("0:04"))
    }
    @Test fun sidecarRequiresAnExactStemAndPrefersSrt() {
        assertEquals("Film.SRT", matchingSidecar("Film.mkv", listOf("Film.en.srt", "Film.vtt", "Film.SRT"), linkedSetOf("srt", "vtt")))
        assertNull(matchingSidecar("Film.mkv", listOf("Filmmaker.srt", "Film.en.srt"), setOf("srt")))
    }
    @Test fun privateFolderUsesSegmentBoundariesAndVolumeIdentity() {
        val root = PrivateRoot("", "primary", "Movies/Private")
        assertTrue(PrivatePaths.contains(root, "external_primary", "Movies/Private/Sub/file.mp4"))
        assertFalse(PrivatePaths.contains(root, "external_primary", "Movies/Private2/file.mp4"))
        assertFalse(PrivatePaths.contains(root, "abcd-1234", "Movies/Private/file.mp4"))
    }
}
