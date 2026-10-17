package com.tplayer.mediaplayer
import com.tplayer.mediaplayer.data.*
import org.junit.Assert.*
import org.junit.Test

class FileSelectionTest {
    private fun item(i: Int) = LibraryItem("content://media/external/video/media/$i", "Movie $i", mimeType = "video/mp4")
    @Test fun individualAllAndScopeChangesNeverRetainHiddenDeletionTargets() {
        val selection = FileSelection()
        selection.scope("Videos/A"); selection.toggle(item(1)); assertEquals(1, selection.size)
        selection.toggle(item(1)); assertEquals(0, selection.size); assertTrue(selection.active)
        selection.all(listOf(item(1), item(2), item(1))); assertEquals(2, selection.size)
        val version = selection.version
        selection.scope("Videos/B"); assertTrue(selection.items.isEmpty()); assertFalse(selection.active); assertTrue(selection.version > version)
        selection.all(listOf(item(3))); selection.remove(listOf(item(3).uri)); assertEquals(0, selection.size)
    }
    @Test fun oversizedAllSelectionIsAtomicAndDirectoriesAreExcluded() {
        val selection = FileSelection(); selection.all(listOf(item(9)))
        try { selection.all((1..1001).map(::item)); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(listOf(item(9)), selection.items)
        selection.all(listOf(item(1), LibraryItem("content://provider/folder", "Folder", "Folder")))
        assertEquals(listOf(item(1)), selection.items)
    }
}
