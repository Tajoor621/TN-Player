package com.tplayer.mediaplayer
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tplayer.mediaplayer.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class DatabaseTest {
    private lateinit var db: MediaDatabase
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MediaDatabase::class.java).allowMainThreadQueries().build().useCodec(testCodec()) }
    @After fun close() { db.close() }
    @Test fun playlistDeletionCascadesWithoutRemovingFavorites() = runBlocking {
        val dao = db.media()
        val id = dao.createPlaylist(Playlist(name = "Test"))
        dao.addToPlaylist(PlaylistItem(id, "content://media/video/1", "Video"))
        dao.addFavorite(Favorite("content://media/video/1", "Video"))
        assertEquals(1, dao.playlistItems(id).size)
        dao.deletePlaylist(id)
        assertTrue(dao.playlistItems(id).isEmpty())
        assertTrue(dao.isFavorite("content://media/video/1"))
    }
    @Test fun historyUpdatesRatherThanDuplicating() = runBlocking {
        val dao = db.media()
        dao.record(History("content://media/audio/1", "Song", 100, 1000))
        dao.record(History("content://media/audio/1", "Song", 500, 1000))
        assertEquals(1, dao.history().size)
        assertEquals(500L, dao.historyFor("content://media/audio/1")!!.position)
    }
    @Test fun signedUrlsAreEncryptedAcrossEveryCollectionAndStillResolve() = runBlocking {
        val dao = db.media(); val uri = "https://example.com/live?access_token=very-private-token"
        val playlist = dao.createPlaylist(Playlist(name = "Streams"))
        dao.saveStream(SavedStream(uri, "Live", "application/x-mpegURL"))
        dao.addFavorite(Favorite(uri, "Live")); dao.record(History(uri, "Live", 30000, 90000))
        dao.addToPlaylist(PlaylistItem(playlist, uri, "Live")); dao.addBookmark(Bookmark(uri = uri, title = "Live", position = 2000))
        val stored = db.raw().streams().single().uri
        assertTrue(stored.startsWith("tpuri:v1:")); assertFalse(stored.contains("token"))
        assertFalse(db.raw().secret(stored)!!.ciphertext.contains("very-private-token"))
        assertEquals(stored, db.raw().favorites().single().uri)
        assertEquals(stored, db.raw().playlistItems(playlist).single().uri)
        assertEquals(uri, dao.streams().single().uri)
        assertEquals("application/x-mpegURL", dao.streams().single().mimeType)
        assertEquals(uri, dao.favorites().single().uri)
        assertEquals(uri, dao.bookmarks(uri).single().uri)
        assertTrue(dao.isFavorite(uri)); dao.removeFavorite(uri); assertFalse(dao.isFavorite(uri))
    }
    @Test fun historyRetentionIsActuallyBounded() = runBlocking {
        val dao = db.media()
        repeat(205) { dao.record(History("content://media/audio/$it", "Song", 1, 100, updatedAt = it.toLong())) }
        val cursor = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM history")
        cursor.use { it.moveToFirst(); assertEquals(200, it.getInt(0)) }
        assertNull(dao.historyFor("content://media/audio/0"))
    }

}
