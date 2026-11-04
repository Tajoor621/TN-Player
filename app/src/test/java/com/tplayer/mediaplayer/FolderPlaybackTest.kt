package com.tplayer.mediaplayer

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tplayer.mediaplayer.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FolderPlaybackTest {
    private val folder = IndexedFolder("external_primary", "Music/Trips/")
    private val sub = folder.copy(path = "Music/Trips/Sub/")
    private val neighbor = folder.copy(path = "Music/Trips-old/")
    private val display = LibraryDisplay(mode = MediaViewMode.FILES)
    private fun row(id: Int, inFolder: IndexedFolder = folder, title: String = "Track $id",
        duration: Long = 40_000, mime: String = "audio/mpeg") =
        CatalogEntry("content://media/external/audio/media/$id", title, inFolder, mime, duration = duration)

    @Test fun fullFolderQueueUsesSelectedSortAndStartsPastTheFirstPage() {
        val order = display.copy(descending = true, sort = MediaSort.LENGTH)
        val collector = CatalogCollector(order, folder, "", true, null, FolderQueue.LIMIT)
        (1..650).forEach { collector.add(row(it, duration = it * 1000L)) }
        collector.add(row(999, neighbor))
        val result = collector.finish()
        val queue = FolderQueue.build(result.entries.map { it.item(order) }, row(400).uri)!!
        assertEquals(650, queue.entries.size)
        assertEquals(250, queue.start)
        assertEquals(row(400).uri, queue.entries[queue.start].uri)
        assertEquals(row(399).uri, queue.entries[queue.start + 1].uri)
        val token = FolderQueueHandoff.put(queue)
        assertNull(FolderQueueHandoff.take("wrong"))
        assertEquals(queue, FolderQueueHandoff.take(token))
        assertNull(FolderQueueHandoff.take(token))
    }

    @Test fun oversizedFolderIsIdentifiableEvenThoughOnlyFiveThousandRowsAreRetained() {
        val collector = CatalogCollector(display, folder, "", true, null, FolderQueue.LIMIT)
        (1..5001).forEach { collector.add(row(it)) }
        val page = collector.finish()
        assertEquals(5001, page.total)
        assertEquals(5000, page.entries.size)
        assertTrue(page.total > FolderQueue.LIMIT)
        val app = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(app.getString(R.string.playback_queue_limit, FolderQueue.LIMIT).contains("5000"))
        assertThrows(IllegalArgumentException::class.java) {
            FolderQueue.build((1..5001).map { row(it).item(display) }, row(1).uri)
        }
    }

    @Test fun diagnosticsCountEachStageForSeparateAudioAndVideoCollections() {
        val audio = listOf(row(1, title = ".song"), row(2, duration = 2), row(3, neighbor),
            row(4, title = "Match"), row(5, title = "Other"))
        val video = listOf(row(6, title = "Match video", mime = "video/mp4"), row(7, title = ".clip", mime = "video/mp4"))
        val settings = display.copy(minimumSeconds = 10)
        fun stats(rows: List<CatalogEntry>, mode: MediaViewMode, query: String) =
            CatalogCollector(settings.copy(mode = mode), if (mode == MediaViewMode.FILES) folder else null,
                query, false, null, 200, setOf(neighbor)).apply { rows.forEach(::add) }.finish().stats
        assertEquals(CatalogStats(5, 4, 3, 2, 2, 2, 1), stats(audio, MediaViewMode.FILES, "Match"))
        assertEquals(CatalogStats(5, 4, 3, 2, 2, 1, 0), stats(audio, MediaViewMode.FOLDERS, "Match"))
        assertEquals(CatalogStats(2, 1, 1, 1, 1, 1, 1), stats(video, MediaViewMode.FILES, "Match"))
    }

    @Test fun exclusionsApplyToDescendantsAndSearchWithoutMatchingSiblingPrefixes() {
        val rows = listOf(row(1), row(2, sub), row(3, neighbor), row(4, folder.copy(volume = "card")))
        for (mode in listOf(MediaViewMode.FILES, MediaViewMode.FOLDERS)) {
            for (show in listOf(false, true)) {
                val result = CatalogCollector(display.copy(mode = mode), null, if (mode == MediaViewMode.FILES) "Track" else "",
                    false, null, 200, setOf(folder), show).apply { rows.forEach(::add) }.finish()
                assertEquals(if (show) 4 else 2, result.total)
                assertEquals(2, result.excludedCounts[folder])
                if (mode == MediaViewMode.FOLDERS) assertEquals(if (show) 2 else 0, result.entries.count { it.excludedFolder })
            }
        }
    }

    @Test fun backupRoundTripsExclusionsAndRejectsMalformedPathsAndOversizedLists() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val settings = Settings(app)
        assertTrue(settings.excludedFolders.isEmpty())
        assertFalse(settings.showExcludedFolders)
        settings.excludedFolders = setOf(folder, neighbor)
        settings.showExcludedFolders = true
        fun backup(values: Map<String, Any>) = LibraryBackup(values, emptyList(), emptyList(), emptyList())
        val values = BackupFormat.decode(BackupFormat.encode(backup(settings.backupValues()))).preferences
        settings.excludedFolders = emptySet(); settings.showExcludedFolders = false
        settings.restoreValues(values)
        assertEquals(setOf(folder, neighbor), settings.excludedFolders)
        assertTrue(settings.showExcludedFolders)
        assertThrows(IllegalArgumentException::class.java) {
            BackupFormat.encode(backup(mapOf(FolderExclusions.KEY to """[["external_primary","Music/../"]]""")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            settings.excludedFolders = (0..FolderExclusions.LIMIT).map { folder.copy(path = "Music/$it/") }.toSet()
        }
    }
}
