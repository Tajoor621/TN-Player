package com.tplayer.mediaplayer.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tplayer.mediaplayer.security.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Entity(tableName = "history")
data class History(@PrimaryKey val uri: String, val title: String, val position: Long,
    val duration: Long, val updatedAt: Long = System.currentTimeMillis(), @ColumnInfo(defaultValue = "''") val mimeType: String = "")
@Entity(tableName = "favorites")
data class Favorite(@PrimaryKey val uri: String, val title: String, @ColumnInfo(defaultValue = "''") val mimeType: String = "")
@Entity(tableName = "streams")
data class SavedStream(@PrimaryKey val uri: String, val title: String, @ColumnInfo(defaultValue = "''") val mimeType: String = "")
@Entity(tableName = "uri_secrets")
data class UriSecret(@PrimaryKey val id: String, val ciphertext: String)
@Entity(tableName = "playlists")
data class Playlist(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)
@Entity(tableName = "playlist_items", primaryKeys = ["playlistId", "uri"],
    foreignKeys = [ForeignKey(entity = Playlist::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE)])
data class PlaylistItem(val playlistId: Long, val uri: String, val title: String, val addedAt: Long = System.currentTimeMillis(), @ColumnInfo(defaultValue = "''") val mimeType: String = "")
@Entity(tableName = "bookmarks")
data class Bookmark(@PrimaryKey(autoGenerate = true) val id: Long = 0, val uri: String, val title: String, val position: Long)

@Dao
interface RawMediaDao {
    @Query("DELETE FROM uri_secrets WHERE id NOT IN (SELECT uri FROM history UNION SELECT uri FROM favorites UNION SELECT uri FROM streams UNION SELECT uri FROM playlist_items UNION SELECT uri FROM bookmarks)") suspend fun collectSecrets()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSecret(secret: UriSecret)
    @Query("SELECT * FROM uri_secrets WHERE id = :id LIMIT 1") suspend fun secret(id: String): UriSecret?
    @Query("SELECT * FROM history ORDER BY updatedAt DESC LIMIT 200") suspend fun history(): List<History>
    @Query("SELECT * FROM history WHERE uri = :uri LIMIT 1") suspend fun historyFor(uri: String): History?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun record(value: History)
    @Query("DELETE FROM history WHERE uri NOT IN (SELECT uri FROM history ORDER BY updatedAt DESC LIMIT 200)") suspend fun trimHistory()
    @Query("DELETE FROM history") suspend fun clearHistory()
    @Query("SELECT * FROM favorites ORDER BY title COLLATE NOCASE") suspend fun favorites(): List<Favorite>
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE uri = :uri)") suspend fun isFavorite(uri: String): Boolean
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addFavorite(value: Favorite)
    @Query("DELETE FROM favorites WHERE uri = :uri") suspend fun removeFavorite(uri: String)
    @Query("SELECT * FROM streams ORDER BY title COLLATE NOCASE") suspend fun streams(): List<SavedStream>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveStream(value: SavedStream)
    @Query("DELETE FROM streams WHERE uri = :uri") suspend fun removeStream(uri: String)
    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE") suspend fun playlists(): List<Playlist>
    @Insert suspend fun createPlaylist(value: Playlist): Long
    @Query("DELETE FROM playlists WHERE id = :id") suspend fun deletePlaylist(id: Long)
    @Query("SELECT * FROM playlist_items WHERE playlistId = :id ORDER BY addedAt") suspend fun playlistItems(id: Long): List<PlaylistItem>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addToPlaylist(value: PlaylistItem)
    @Query("DELETE FROM playlist_items WHERE playlistId = :id AND uri = :uri") suspend fun removeFromPlaylist(id: Long, uri: String)
    @Insert suspend fun addBookmark(value: Bookmark)
    @Query("SELECT * FROM bookmarks WHERE uri = :uri ORDER BY position") suspend fun bookmarks(uri: String): List<Bookmark>
}

@Database(entities = [History::class, Favorite::class, SavedStream::class, Playlist::class, PlaylistItem::class, Bookmark::class, UriSecret::class], version = 2, exportSchema = false)
abstract class MediaDatabase : RoomDatabase() {
    abstract fun raw(): RawMediaDao
    @Volatile private var codec: SecretCodec? = null
    fun media(): MediaDao = MediaDao(this, checkNotNull(codec) { "Secure storage was not initialized." })
    fun useCodec(value: SecretCodec): MediaDatabase { codec = value; return this }
    companion object {
        @Volatile private var instance: MediaDatabase? = null
        fun migration(codec: SecretCodec): Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.query("PRAGMA secure_delete=ON").use { it.moveToFirst() }
                db.execSQL("CREATE TABLE IF NOT EXISTS uri_secrets (id TEXT NOT NULL, ciphertext TEXT NOT NULL, PRIMARY KEY(id))")
                for (table in listOf("streams", "history", "favorites", "playlist_items")) db.execSQL("ALTER TABLE $table ADD COLUMN mimeType TEXT NOT NULL DEFAULT ''")
                for (table in listOf("history", "favorites", "streams", "playlist_items", "bookmarks")) {
                    val titles = mutableListOf<Pair<Long, String>>()
                    db.query("SELECT rowid,title FROM $table").use { c -> while (c.moveToNext()) titles += c.getLong(0) to c.getString(1) }
                    for ((row, title) in titles) {
                        val safe = title.replace(Regex("(?i)(?:https?|rtsp)://\\S+"), "[network source]").take(1024)
                        if (safe != title) db.execSQL("UPDATE $table SET title=? WHERE rowid=?", arrayOf(safe, row))
                    }
                    val urls = mutableListOf<String>()
                    db.query("SELECT DISTINCT uri FROM $table WHERE uri LIKE 'https:%' OR uri LIKE 'http:%' OR uri LIKE 'rtsp:%'").use { c -> while (c.moveToNext()) urls += c.getString(0) }
                    for (url in urls) {
                        val id = "tpuri:v1:" + codec.identifier(url)
                        db.execSQL("INSERT OR REPLACE INTO uri_secrets(id,ciphertext) VALUES(?,?)", arrayOf(id, codec.encrypt(url, id)))
                        db.execSQL("UPDATE $table SET uri=? WHERE uri=?", arrayOf(id, url))
                    }
                }
            }
        }
        fun get(context: Context): MediaDatabase = instance ?: synchronized(this) {
            instance ?: run {
                val codec = AndroidSecretCodec(context.applicationContext)
                Room.databaseBuilder(context.applicationContext, MediaDatabase::class.java, "media.db")
                    .addMigrations(migration(codec)).build().useCodec(codec).also { instance = it }
            }
        }
    }
}
