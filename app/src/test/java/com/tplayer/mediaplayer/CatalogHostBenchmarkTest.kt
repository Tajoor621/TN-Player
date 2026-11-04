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
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CatalogHostBenchmarkTest {
    // Call the JVM clock via reflection to avoid Robolectric's simulated System clock.
    private val nanoTime = System::class.java.getMethod("nanoTime")
    private fun now() = nanoTime.invoke(null) as Long

    private fun folders(count: Int) = List(count) { i ->
        IndexedFolder(if (i % 4 == 0) "abcd-1234" else "external_primary",
            when (i % 3) { 0 -> "Movies/Series $i/"; 1 -> "Downloads/Channel $i/Season 2/"; else -> "Camera/Year/Trip $i/Day 3/" })
    }

    private fun fixture(count: Int, folders: List<IndexedFolder>) = List(count) { i ->
        val slot = (i * 31) % folders.size
        CatalogEntry("content://media/video/$i", if (i % 71 == 0) "Needle ${i % 7}" else "Clip ${i % 97}", folders[slot],
            mime = if (i % 11 == 0) "" else "video/mp4", bytes = if (i % 13 == 0) 0 else i * 100L,
            duration = if (i % 7 == 0) 0 else i * 190L, date = if (i % 19 == 0) 0 else i * 1000L,
            width = if (i % 5 == 0) 0 else 1920, height = 1080, frameRate = if (i % 3 == 0) 0f else 29.97f,
            lastPlayed = if (i % 4 == 0) i * 100L else 0, state = i % 3)
    }

    private fun measured(count: Int, name: String, task: () -> Unit) {
        task() // Warm each workload before recording samples.
        val nanos = LongArray(3) {
            val start = now()
            task()
            now() - start
        }.sorted()
        val ms = nanos[1] / 1_000_000.0
        println("HOST_CATALOG|$count|$name|" + String.format(Locale.ROOT, "%.3f", ms))
        assertTrue("$name median took $ms ms for $count entries", ms > 0 && ms < 1500)
    }

    @Test fun syntheticCatalogTimings() {
        val start = now()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("tplayer", Context.MODE_PRIVATE)
        val settings = Settings(context)
        for (size in listOf(5000, 50000)) {
            val directories = folders(if (size == 5000) 271 else 503)
            val entries = fixture(size, directories)
            val display = LibraryDisplay(mode = MediaViewMode.FILES)
            fun page(mode: MediaViewMode, after: CatalogEntry? = null, sort: MediaSort = MediaSort.TITLE): CatalogPage {
                val c = CatalogCollector(display.copy(mode = mode, sort = sort), null, "", false, after, 200)
                entries.forEach(c::add)
                return c.finish()
            }
            for (key in MediaSort.values()) measured(size, "sort.${key.name}") {
                val result = page(MediaViewMode.FILES, sort = key)
                assertEquals(size, result.total)
                assertEquals(200, result.entries.size)
            }
            for (mode in listOf(MediaViewMode.FILES, MediaViewMode.FOLDERS)) measured(size, "twoPages.${mode.name}") {
                val first = page(mode)
                val next = page(mode, checkNotNull(first.nextCursor))
                assertEquals(if (mode == MediaViewMode.FOLDERS) directories.size else size, first.total)
                assertEquals(200, next.before)
                assertTrue(first.entries.map { it.uri }.intersect(next.entries.map { it.uri }.toSet()).isEmpty())
                assertEquals(minOf(200, first.total - 200), next.entries.size)
            }
            measured(size, "search.allFolders") {
                val c = catalogCollector(LibraryDisplay(), directories.first(), "NEEDLE", false, null, true)
                entries.forEach(c::add)
                val result = c.finish()
                assertEquals((size + 70) / 71, result.total)
                assertTrue(result.entries.all { it.title.startsWith("Needle") && !it.directory })
            }
            prefs.edit().clear().apply()
            val editor = prefs.edit()
            directories.forEach { editor.putString(FolderSort.key(it), "5:1") }
            editor.apply()
            measured(size, "folderSort.1000Lookups") {
                repeat(1000) { i ->
                    val order = settings.libraryDisplayFor(directories[i % directories.size])
                    assertEquals(MediaSort.SIZE, order.sort)
                    assertTrue(order.descending)
                }
            }
        }
        assertTrue("Host workloads must finish within 55 seconds", now() - start < 55_000_000_000L)
    }
}
