package com.tplayer.mediaplayer.subtitle

import android.content.Context
import android.util.AtomicFile
import com.tplayer.mediaplayer.security.AndroidSecretCodec
import com.tplayer.mediaplayer.security.SecretCodec
import com.tplayer.mediaplayer.ui.GestureThresholds as T
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

/** Small per-file control state, independently retained from the ten imported-caption documents. */
data class SubtitlePreferences(val delayMs: Long = 0, val position: Int = T.SUBTITLE_DEFAULT_POSITION,
    val scale: Int = T.SUBTITLE_DEFAULT_SCALE, val overrideNativeFont: Boolean = false) {
    fun valid() = delayMs in -T.SUBTITLE_MAX_DELAY_MS..T.SUBTITLE_MAX_DELAY_MS &&
        position in 0..T.SUBTITLE_MAX_POSITION && scale in T.SUBTITLE_MIN_SCALE..T.SUBTITLE_MAX_SCALE
}
class SubtitlePreferenceFiles(private val directory: File, private val codec: SecretCodec) {
    companion object { const val MAX_BYTES = 256 * 1024; const val MAX_RECORDS = 200 }
    private fun file(id: String): AtomicFile {
        require(id.length in 1..32768)
        val key = codec.identifier(id); require(key.matches(Regex("[a-f0-9]{64}")))
        return AtomicFile(File(directory, "$key.json"))
    }
    @Synchronized fun read(id: String): SubtitlePreferences? {
        val file = file(id)
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        val bytes = file.openRead().use { it.readBytesBounded(MAX_BYTES) }
        val json = JSONObject(codec.decrypt(bytes.toString(Charsets.UTF_8), "subtitle-preferences:${file.baseFile.name}"))
        require(json.getInt("schema") == 1 && json.getString("id") == id)
        return SubtitlePreferences(json.getLong("delay"), json.getInt("position"), json.getInt("scale"), json.optBoolean("overrideFont", false)).also { require(it.valid()) }
    }
    @Synchronized fun save(id: String, state: SubtitlePreferences) {
        require(state.valid()); check(directory.mkdirs() || directory.isDirectory)
        val file = file(id)
        val text = JSONObject().put("schema", 1).put("id", id).put("delay", state.delayMs).put("position", state.position).put("scale", state.scale).put("overrideFont", state.overrideNativeFont).toString()
        val bytes = codec.encrypt(text, "subtitle-preferences:${file.baseFile.name}").toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES)
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) } catch (e: Exception) { file.failWrite(output); throw e }
        file.baseFile.setLastModified(System.currentTimeMillis())
        directory.listFiles()?.filter { it.isFile && it.name.matches(Regex("[a-f0-9]{64}\\.json")) && it != file.baseFile }
            ?.sortedByDescending { it.lastModified() }?.drop(MAX_RECORDS - 1)?.forEach { AtomicFile(it).delete() }
    }
    private fun java.io.InputStream.readBytesBounded(maximum: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
        while (true) { val size = read(buffer); if (size < 0) break; require(output.size() + size <= maximum); output.write(buffer, 0, size) }
        return output.toByteArray()
    }
}
class SubtitlePreferenceRepository(private val files: SubtitlePreferenceFiles) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val pending = linkedMapOf<String, Pair<Long, SubtitlePreferences>>()
    private var revision = 0L
    fun save(id: String, state: SubtitlePreferences): Deferred<Result<Unit>> {
        require(state.valid())
        val version = synchronized(pending) {
            if (id !in pending && pending.size >= SubtitlePreferenceFiles.MAX_RECORDS)
                return CompletableDeferred(Result.failure(IllegalStateException("Too many pending subtitle preferences")))
            val next = ++revision; pending[id] = next to state; next
        }
        return scope.async {
            runCatching { mutex.withLock {
                if (synchronized(pending) { pending[id]?.first } != version) return@withLock
                files.save(id, state)
                synchronized(pending) { if (pending[id]?.first == version) pending.remove(id) }
                Unit
            } }
        }
    }
    suspend fun read(id: String): SubtitlePreferences? = withContext(Dispatchers.IO) { mutex.withLock {
        val cached = synchronized(pending) { pending[id]?.second }
        if (cached != null) cached else {
            val stored = files.read(id)
            synchronized(pending) { pending[id]?.second } ?: stored
        }
    } }
    companion object {
        @Volatile private var instance: SubtitlePreferenceRepository? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: SubtitlePreferenceRepository(SubtitlePreferenceFiles(File(context.applicationContext.noBackupFilesDir, "subtitle-preferences"), AndroidSecretCodec(context.applicationContext))).also { instance = it }
        }
    }
}
