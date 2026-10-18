package com.tplayer.mediaplayer.subtitle

import android.content.Context
import android.util.AtomicFile
import com.tplayer.mediaplayer.security.AndroidSecretCodec
import com.tplayer.mediaplayer.security.SecretCodec
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Local caption state is encrypted; filenames disclose neither source URIs nor caption text. */
data class ExternalCaptions(val mediaId: String, val name: String, val document: SubtitleDocument,
    val delayMs: Long = 0, val ratePermille: Int = 1000)
class CaptionFileStore(private val directory: File, private val codec: SecretCodec) {
    companion object { const val MAX_RECORDS = 10; const val MAX_TOTAL = 32L * 1024 * 1024; const val MAX_PLAIN = 4 * 1024 * 1024; const val MAX_ENCODED = 6 * 1024 * 1024 }
    private fun file(id: String): AtomicFile {
        require(id.length in 1..32768)
        val key = codec.identifier(id)
        check(key.matches(Regex("[a-f0-9]{64}")))
        return AtomicFile(File(directory, "$key.v1"))
    }
    private fun valid(value: ExternalCaptions) {
        require(value.mediaId.length in 1..32768 && value.name.length <= 1024)
        require(value.delayMs in -600000L..600000L && value.ratePermille in 500..2000)
        require(value.document.cues.size in 1..SubtitleImport.MAX_CUES)
        var previous = -1L; var textBytes = 0L
        for (cue in value.document.cues) {
            require(cue.start in 0..604800000L && cue.end in 1..604800000L && cue.start < cue.end && cue.start >= previous)
            require(!cue.text.contains('\u0000'))
            textBytes += cue.text.toByteArray(Charsets.UTF_8).size
            require(textBytes <= SubtitleImport.MAX_BYTES); previous = cue.start
        }
    }
    @Synchronized fun read(mediaId: String): ExternalCaptions? {
        val atomic = file(mediaId)
        if (!atomic.baseFile.exists() && !File(atomic.baseFile.path + ".bak").exists()) return null
        require(atomic.baseFile.length() <= MAX_ENCODED)
        val encrypted = atomic.openRead().use { input ->
            val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) { val size = input.read(buffer); if (size < 0) break; require(output.size() + size <= MAX_ENCODED); output.write(buffer, 0, size) }
            output.toString("UTF-8")
        }
        val json = codec.decrypt(encrypted, "captions-v1:${atomic.baseFile.name}")
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_PLAIN)
        val root = JSONObject(json); require(root.getInt("schema") == 1 && root.getString("mediaId") == mediaId)
        val entries = root.getJSONArray("cues"); require(entries.length() in 1..SubtitleImport.MAX_CUES)
        val cues = (0 until entries.length()).map { index -> entries.getJSONArray(index).let { row -> require(row.length() == 3); SubtitleCue(row.getLong(0), row.getLong(1), row.getString(2)) } }
        val result = ExternalCaptions(mediaId, root.getString("name"), SubtitleDocument(cues), root.getLong("delay"), root.optInt("rate", 1000))
        valid(result); atomic.baseFile.setLastModified(System.currentTimeMillis()); return result
    }
    @Synchronized fun save(value: ExternalCaptions) {
        valid(value); check(directory.mkdirs() || directory.isDirectory)
        val json = JSONObject().put("schema", 1).put("mediaId", value.mediaId).put("name", value.name)
            .put("delay", value.delayMs).put("rate", value.ratePermille)
            .put("cues", JSONArray().also { rows -> value.document.cues.forEach { rows.put(JSONArray().put(it.start).put(it.end).put(it.text)) } }).toString()
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_PLAIN) { "Caption session is too large to save." }
        val atomic = file(value.mediaId)
        val encrypted = codec.encrypt(json, "captions-v1:${atomic.baseFile.name}").toByteArray(Charsets.UTF_8)
        require(encrypted.size <= MAX_ENCODED)
        val output = atomic.startWrite()
        try { output.write(encrypted); atomic.finishWrite(output) } catch (e: Exception) { atomic.failWrite(output); throw e }
        val retained = directory.listFiles()?.filter { it.isFile && it.name.matches(Regex("[a-f0-9]{64}\\.v1")) }
            ?.filter { it != atomic.baseFile }?.sortedByDescending { it.lastModified() }.orEmpty()
        var bytes = atomic.baseFile.length(); var records = 1
        for (item in retained) {
            if (records >= MAX_RECORDS || bytes + item.length() > MAX_TOTAL) AtomicFile(item).delete()
            else { records++; bytes += item.length() }
        }
    }
    @Synchronized fun remove(mediaId: String) { val atomic = file(mediaId); atomic.delete(); check(!atomic.baseFile.exists() && !File(atomic.baseFile.path + ".bak").exists()) { "Saved subtitles could not be removed." } }
}
object CaptionRepository {
    @Volatile private var instance: CaptionFileStore? = null
    fun get(context: Context): CaptionFileStore = instance ?: synchronized(this) {
        instance ?: CaptionFileStore(File(context.applicationContext.noBackupFilesDir, "caption-sessions"), AndroidSecretCodec(context.applicationContext)).also { instance = it }
    }
}
