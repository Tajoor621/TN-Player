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
class BackupTest {
    private lateinit var source: MediaDatabase
    private lateinit var target: MediaDatabase
    @Before fun setup() {
        fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MediaDatabase::class.java).allowMainThreadQueries().build().useCodec(testCodec())
        source = database(); target = database()
    }
    @After fun close() { source.close(); target.close() }
    @Test fun localCollectionsRoundTripWithoutExportingNetworkTokensAndMergeIsIdempotent() = runBlocking {
        val local = "content://media/external/audio/media/42"
        val remote = "https://example.com/live?token=never-export-this"
        val dao = source.media(); val list = dao.createPlaylist(Playlist(name = "Travel"))
        dao.addFavorite(Favorite(local, "Song", "audio/mpeg")); dao.addFavorite(Favorite(remote, "Stream"))
        dao.saveStream(SavedStream(remote, "Private stream"))
        dao.addToPlaylist(PlaylistItem(list, local, "Song", mimeType = "audio/mpeg"))
        dao.addToPlaylist(PlaylistItem(list, remote, "Stream"))
        dao.addBookmark(Bookmark(uri = local, title = "Song", position = 10000))
        val duplicateName = dao.createPlaylist(Playlist(name = "Travel"))
        dao.addToPlaylist(PlaylistItem(duplicateName, "content://media/external/audio/media/43", "Different song"))
        val snapshot = BackupRepository(source).snapshot(mapOf("seekMs" to 30000L, "gestures" to false))
        val bytes = BackupFormat.encode(snapshot)
        assertFalse(bytes.toString(Charsets.UTF_8).contains("never-export-this"))
        assertFalse(bytes.toString(Charsets.UTF_8).contains("tpuri:v1:"))
        val decoded = BackupFormat.decode(bytes)
        target.media().addFavorite(Favorite("content://media/external/audio/media/99", "Keep me"))
        repeat(2) { BackupRepository(target).merge(decoded) }
        assertEquals(2, target.media().favorites().size)
        val restored = target.media().playlists().sortedBy { it.id }
        assertEquals(2, restored.size)
        assertEquals(local, target.media().playlistItems(restored[0].id).single().uri)
        assertEquals("content://media/external/audio/media/43", target.media().playlistItems(restored[1].id).single().uri)
        assertEquals(1, target.media().bookmarks(local).size)
        assertEquals("audio/mpeg", target.media().favorites().first { it.uri == local }.mimeType)
        assertEquals(30000L, decoded.preferences["seekMs"])
    }
    @Test fun malformedOrForeignSourcesAreRejectedBeforeAnyRestore() = runBlocking {
        val valid = BackupFormat.encode(LibraryBackup(emptyMap(), listOf(Favorite("content://media/1", "Safe")), emptyList(), emptyList())).toString(Charsets.UTF_8)
        fun withUri(uri: String): String = org.json.JSONObject(valid).apply {
            getJSONArray("favorites").getJSONObject(0).put("uri", uri)
        }.toString()
        val foreignVersion = org.json.JSONObject(valid).put("version", 2).toString()
        for (bad in listOf(withUri("https://example.com/private?token=secret"), foreignVersion, "{}", "[".repeat(100) + "]".repeat(100), withUri("javascript:alert(1)"))) {
            try { BackupFormat.decode(bad.toByteArray()); fail("Invalid backup accepted") } catch (_: Exception) { }
        }
        assertTrue(target.media().favorites().isEmpty())
        try { BackupFormat.read(java.io.ByteArrayInputStream(ByteArray(BackupFormat.MAX_BYTES + 1))); fail("Oversized import accepted") } catch (_: IllegalArgumentException) { }
    }
    @Test fun restoredSettingsAreBoundedAndPersistAcrossInstances() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settings = Settings(context)
        settings.subtitleSize = 2; settings.subtitleColor = -256; settings.subtitleFont = 2
        settings.subtitlePosition = 28; settings.gestures = false; settings.aspectRatio = 2
        val copy = BackupFormat.decode(BackupFormat.encode(LibraryBackup(settings.backupValues(), emptyList(), emptyList(), emptyList())))
        settings.subtitleSize = 0; settings.restoreValues(copy.preferences)
        assertEquals(2, Settings(context).subtitleSize)
        assertEquals(-256, Settings(context).subtitleColor)
        assertEquals(2, Settings(context).subtitleFont)
        assertEquals(28, Settings(context).subtitlePosition)
        assertFalse(Settings(context).gestures)
        assertEquals(2, Settings(context).aspectRatio)
        val dark = copy.copy(preferences = mapOf("amoled" to true, "theme" to 1))
        val normalized = BackupFormat.decode(BackupFormat.encode(dark))
        assertEquals(2, normalized.preferences["theme"])
    }
}
