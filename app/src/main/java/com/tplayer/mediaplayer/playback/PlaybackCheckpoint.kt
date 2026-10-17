@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer.playback

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.tplayer.mediaplayer.security.*
import com.tplayer.mediaplayer.data.History
import com.tplayer.mediaplayer.data.MediaDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong

data class QueueEntry(val uri: String, val title: String, val mimeType: String = "") {
    fun mediaItem(): MediaItem = MediaItem.Builder().setMediaId(uri).setUri(Uri.parse(uri))
        .setMimeType(mimeType.ifBlank { null }).setMediaMetadata(MediaMetadata.Builder().setTitle(title).setExtras(android.os.Bundle().apply { putString("mime", mimeType) }).build()).build()
}
data class PlaybackCheckpoint(val items: List<QueueEntry>, val index: Int, val position: Long, val repeatMode: Int = 0, val shuffle: Boolean = false) {
    fun json(): String = JSONObject().apply {
        put("schema", 1); put("index", index); put("position", position); put("repeat", repeatMode); put("shuffle", shuffle)
        put("items", JSONArray().also { array -> items.forEach { array.put(JSONObject().put("uri", it.uri).put("title", it.title).put("mime", it.mimeType)) } })
    }.toString()
    companion object {
        fun parse(value: String): PlaybackCheckpoint {
            val obj = JSONObject(value); require(obj.getInt("schema") == 1)
            val entries = obj.getJSONArray("items"); require(entries.length() in 1..1000)
            val items = (0 until entries.length()).map { i -> entries.getJSONObject(i).let { e ->
                val uri = e.getString("uri"); require(uri.length <= 32768 && Uri.parse(uri).scheme?.lowercase() in setOf("content", "file", "https", "rtsp"))
                QueueEntry(uri, e.optString("title", "Media").take(1024), e.optString("mime"))
            } }
            val index = obj.getInt("index"); require(index in items.indices)
            return PlaybackCheckpoint(items, index, obj.getLong("position").coerceAtLeast(0), obj.optInt("repeat").coerceIn(0, 2), obj.optBoolean("shuffle"))
        }
    }
}
class CheckpointFile(private val file: AtomicFile, private val codec: SecretCodec) {
    @Synchronized fun read(): PlaybackCheckpoint? = if (file.baseFile.exists()) { check(file.baseFile.length() <= 4 * 1024 * 1024) { "Checkpoint is too large." }; PlaybackCheckpoint.parse(codec.decrypt(String(file.readFully(), Charsets.UTF_8), "playback-checkpoint-v1")) } else null
    @Synchronized fun write(value: PlaybackCheckpoint?) {
        if (value == null || value.items.isEmpty()) { file.delete(); return }
        val json = value.json(); require(json.toByteArray(Charsets.UTF_8).size <= 2 * 1024 * 1024) { "Checkpoint is too large." }
        val bytes = codec.encrypt(json, "playback-checkpoint-v1").toByteArray(Charsets.UTF_8)
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) } catch (e: Exception) { file.failWrite(output); throw e }
    }
}
/** One ordered writer outlives Activity/service recreation, but never starts playback by itself. */
class PlaybackPersistence private constructor(context: Context) {
    private val store = CheckpointFile(AtomicFile(File(context.noBackupFilesDir, "playback.v1")), AndroidSecretCodec(context))
    private val app = context.applicationContext
    @Volatile private var hasMemory = false
    @Volatile private var latest: PlaybackCheckpoint? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val revision = AtomicLong()
    @Volatile private var persistedRevision = 0L
    private data class Update(val checkpoint: PlaybackCheckpoint?, val revision: Long)
    private val writes = Channel<Update>(Channel.CONFLATED)
    private val history = OrderedHistory(scope,
        { MediaDatabase.get(app).media().record(it) }, { MediaDatabase.get(app).media().historyFor(it) },
        { MediaDatabase.get(app).media().clearHistory() }, { android.util.Log.w("TNPlayer", "Playback history could not be saved") })
    init {
        scope.launch {
            for (update in writes) try { store.write(update.checkpoint); persistedRevision = update.revision }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { android.util.Log.w("TNPlayer", "Playback checkpoint could not be saved") }
        }
    }
    fun recordHistory(value: History) { history.record(value) }
    suspend fun historyFor(uri: String): History? = history.historyFor(uri)
    suspend fun flushHistory() = history.flush()
    suspend fun clearHistory() = history.clear()
    fun save(value: PlaybackCheckpoint?) { latest = value; hasMemory = true; writes.trySend(Update(value, revision.incrementAndGet())) }
    suspend fun flush() {
        val target = revision.get()
        withTimeout(10000) {
            history.flush()
            while (persistedRevision < target) delay(20)
        }
    }
    suspend fun read(): PlaybackCheckpoint? = withContext(Dispatchers.IO) { if (hasMemory) latest else store.read() }
    companion object {
        @Volatile private var instance: PlaybackPersistence? = null
        fun get(context: Context): PlaybackPersistence = instance ?: synchronized(this) {
            instance ?: PlaybackPersistence(context.applicationContext).also { instance = it }
        }
    }
}
