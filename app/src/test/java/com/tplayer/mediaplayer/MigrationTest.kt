package com.tplayer.mediaplayer

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tplayer.mediaplayer.data.MediaDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class MigrationTest {
    @Test fun upgradingV1PreservesCollectionsAndEncryptsTheirNetworkAddresses() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-regression.db"; context.deleteDatabase(name)
        val file = context.getDatabasePath(name); file.parentFile!!.mkdirs()
        val uri = "https://example.com/live?token=old-secret"
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE history (uri TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, position INTEGER NOT NULL, duration INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE favorites (uri TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL)")
            old.execSQL("CREATE TABLE streams (uri TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL)")
            old.execSQL("CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
            old.execSQL("CREATE TABLE playlist_items (playlistId INTEGER NOT NULL, uri TEXT NOT NULL, title TEXT NOT NULL, addedAt INTEGER NOT NULL, PRIMARY KEY(playlistId,uri), FOREIGN KEY(playlistId) REFERENCES playlists(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            old.execSQL("CREATE TABLE bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, uri TEXT NOT NULL, title TEXT NOT NULL, position INTEGER NOT NULL)")
            old.execSQL("INSERT INTO playlists(id,name) VALUES(1,'Old playlist')")
            old.execSQL("INSERT INTO streams(uri,title) VALUES(?,?)", arrayOf(uri, "Old stream $uri"))
            old.execSQL("INSERT INTO favorites(uri,title) VALUES(?,'Old favorite')", arrayOf(uri))
            old.execSQL("INSERT INTO history(uri,title,position,duration,updatedAt) VALUES(?,'Old history',42000,100000,1)", arrayOf(uri))
            old.execSQL("INSERT INTO playlist_items(playlistId,uri,title,addedAt) VALUES(1,?,'Old track',1)", arrayOf(uri))
            old.execSQL("INSERT INTO bookmarks(id,uri,title,position) VALUES(1,?,'Old bookmark',10000)", arrayOf(uri))
            old.version = 1
        }
        val codec = testCodec()
        val db = Room.databaseBuilder(context, MediaDatabase::class.java, name)
            .addMigrations(MediaDatabase.migration(codec)).allowMainThreadQueries().build().useCodec(codec)
        try {
            val dao = db.media()
            assertEquals(uri, dao.streams().single().uri)
            assertEquals("Old stream [network source]", dao.streams().single().title)
            assertEquals(uri, dao.favorites().single().uri)
            assertEquals(42000L, dao.historyFor(uri)!!.position)
            assertEquals(uri, dao.playlistItems(1).single().uri)
            assertEquals(10000L, dao.bookmarks(uri).single().position)
            assertTrue(db.raw().streams().single().uri.startsWith("tpuri:v1:"))
            assertFalse(db.raw().secret(db.raw().streams().single().uri)!!.ciphertext.contains("old-secret"))
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
