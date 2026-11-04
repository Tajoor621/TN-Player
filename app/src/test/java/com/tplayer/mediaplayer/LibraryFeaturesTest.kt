package com.tplayer.mediaplayer

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tplayer.mediaplayer.data.*
import com.tplayer.mediaplayer.subtitle.SidecarNames
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LibraryFeaturesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences("tplayer", Context.MODE_PRIVATE)
    private val folder = IndexedFolder("external_primary", "Movies/Trips/")
    private val other = IndexedFolder("abcd-1234", folder.path)

    @Before fun clear() { prefs.edit().clear().commit() }

    @Test fun legacyGlobalSortRemainsFallbackAndFolderOverrideSurvivesNewSettingsInstance() {
        prefs.edit().putInt("librarySort", MediaSort.DATE.ordinal).putBoolean("libraryDescending", true).commit()
        val settings = Settings(context)
        assertEquals(MediaSort.DATE, settings.libraryDisplayFor(folder).sort)
        assertTrue(settings.libraryDisplayFor(folder).descending)
        settings.saveFolderSort(folder, LibraryDisplay(sort = MediaSort.SIZE, descending = false))
        val reopened = Settings(context)
        assertEquals(MediaSort.SIZE, reopened.libraryDisplayFor(folder.copy(path = "Movies/Trips")).sort)
        assertFalse(reopened.libraryDisplayFor(folder).descending)
        assertEquals(MediaSort.DATE, reopened.libraryDisplayFor(other).sort)
        assertTrue(reopened.libraryDisplayFor(other).descending)
        assertEquals(MediaSort.DATE.ordinal, prefs.getInt("librarySort", -1))
        assertTrue(prefs.getBoolean("libraryDescending", false))
    }

    @Test fun bothSearchScopesUseCatalogPagingAndKeepBlankQueryInCurrentFolder() {
        val video = CatalogEntry("a", "Trip One", folder)
        val remoteFolder = CatalogEntry("b", "Trip Two", other)
        val excluded = CatalogEntry("c", "Party", folder)
        fun search(query: String, all: Boolean) = catalogCollector(LibraryDisplay(), folder, query, false, null, all)
            .apply { listOf(video, remoteFolder, excluded).forEach(::add) }.finish()
        assertEquals(listOf(video), search("trip", false).entries)
        assertEquals(listOf(video, remoteFolder), search("trip", true).entries)
        assertEquals(2, search("trip", true).total)
        assertEquals(setOf(video, excluded), search("", true).entries.toSet())
    }

    @Test fun continueWatchingFiltersCompletedNonVideoNetworkAndUnknownDurationsAndLimitsToTen() {
        val videos = (1L..12).map { History("file:///video-$it.mp4", "Video $it", 3001, 7000, it, "video/mp4") }
        val base = videos.last().copy(updatedAt = 999)
        val input = videos + listOf(base.copy(uri = "file:///finished.mp4", position = 4000),
            base.copy(uri = "file:///unknown.mp4", duration = 0), base.copy(uri = "file:///unstarted.mp4", position = 0),
            base.copy(uri = "file:///negative.mp4", position = -1), base.copy(uri = "https://example.com/movie.mp4"),
            base.copy(uri = "file:///music.mp3", mimeType = "audio/mpeg"))
        assertEquals(videos.reversed().take(10), ContinueWatching.select(input))
    }

    @Test fun sidecarMatchesExactBaseAndLanguageTagsButNotOtherMoviesOrExtraSuffixes() {
        val video = "movie.cut.mp4"
        val names = listOf("movie.cut.srt", "movie.cut.en.srt", "movie.cut.fr.vtt", "movie.cut.zh-Hant-TW.ass",
            "movie.cut.es-419.ttml", "movie.cut.de.sub", "movie.cut.txt", "movie.cut.pjs", "movie.cut.mpl", "movie.cut.ssa")
        assertTrue(names.all { SidecarNames.rank(video, it) != null })
        assertNotNull(SidecarNames.rank(video, "MOVIE.CUT.EN.SRT"))
        assertTrue(SidecarNames.rank(video, names[0])!! < SidecarNames.rank(video, names[1])!!)
        assertTrue(listOf("movie.srt", "movie.cuts.srt", "movie.cut.trailer.srt", "movie.cut.en.forced.srt", "movie.cut.srt.zip", "movie.cut.sup")
            .all { SidecarNames.rank(video, it) == null })
    }

    @Test fun backupRoundTripsEveryNewKeyAndRejectsBadFolderSortAndAutoValues() {
        val settings = Settings(context)
        assertFalse(settings.autoMatchingSubtitles)
        settings.autoMatchingSubtitles = true
        settings.saveFolderSort(folder, LibraryDisplay(sort = MediaSort.TYPE, descending = true))
        settings.saveFolderSort(other, LibraryDisplay(sort = MediaSort.LENGTH, descending = false))
        val keys = setOf(FolderSort.key(folder), FolderSort.key(other), "autoMatchingSubtitles")
        val values = settings.backupValues().filterKeys { it in keys }
        assertEquals(keys, values.keys)
        fun backup(input: Map<String, Any>) = LibraryBackup(input, emptyList(), emptyList(), emptyList())
        val restored = BackupFormat.decode(BackupFormat.encode(backup(values))).preferences
        assertEquals(values, restored)
        clear()
        Settings(context).restoreValues(restored)
        assertEquals(values, Settings(context).backupValues().filterKeys { it in keys })
        for (bad in listOf(mapOf(FolderSort.key(folder) to "10:1"), mapOf(FolderSort.key(folder) to "1:2"),
            mapOf(FolderSort.key(folder) to 1), mapOf("libraryFolderSort_bad" to "1:0"), mapOf("autoMatchingSubtitles" to "true"))) {
            assertThrows(IllegalArgumentException::class.java) { BackupFormat.encode(backup(bad)) }
        }
    }
}
