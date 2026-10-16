package com.tplayer.mediaplayer.data

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.documentfile.provider.DocumentFile
import com.tplayer.mediaplayer.security.AndroidSecretCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

object DocumentOperations {
    private val gate = Mutex()
    private class Journal(context: Context) {
        private val file = AtomicFile(File(context.noBackupFilesDir, "transfer.v1"))
        private val codec = AndroidSecretCodec(context)
        fun save(uri: String, name: String) {
            val encrypted = codec.encrypt(JSONObject().put("uri", uri).put("stagingName", name).toString(), "transfer-journal-v1")
            val output = file.startWrite()
            try { output.write(encrypted.toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
            catch (e: Exception) { file.failWrite(output); throw e }
        }
        fun read(): JSONObject? = if (file.baseFile.exists()) JSONObject(codec.decrypt(String(file.readFully(), Charsets.UTF_8), "transfer-journal-v1")) else null
        fun clear() { file.delete() }
    }
    /** Only removes a positively identified app-owned unfinished file. Never deletes a committed filename. */
    suspend fun recoverInterrupted(context: Context) = withContext(Dispatchers.IO) {
        gate.withLock {
            val journal = Journal(context.applicationContext)
            val record = journal.read() ?: return@withLock
            val name = record.getString("stagingName")
            if (!name.startsWith(".tplayer-part-")) return@withLock
            val doc = DocumentFile.fromSingleUri(context, Uri.parse(record.getString("uri")))
            if (doc != null && doc.name == name) { if (doc.delete()) journal.clear() }
            else journal.clear() // Renamed or no longer present: do not touch a completed file.
        }
    }
    suspend fun copy(context: Context, source: DocumentFile, target: DocumentFile, move: Boolean,
        progress: (TransferProgress) -> Unit = {}): TransferResult = withContext(Dispatchers.IO) {
        gate.withLock {
            require(!source.isDirectory && source.canRead()) { "Select a readable individual file." }
            require(target.isDirectory && target.canWrite()) { "Choose a writable destination folder." }
            val name = source.name ?: "media"
            require(validName(name)) { "This filename cannot be copied safely." }
            require(target.findFile(name) == null) { "A file with this name already exists. No file was overwritten." }
            val stagingName = ".tplayer-part-${UUID.randomUUID()}-$name"
            val resolver = context.contentResolver
            val journal = Journal(context.applicationContext)
            var destination: DocumentFile? = null
            val backend = object : TransferBackend {
                override val expectedLength = source.length().takeIf { it > 0 } ?: -1L
                override fun openSource(): InputStream = resolver.openInputStream(source.uri) ?: error("Cannot read source.")
                override fun createTarget(): OutputStream {
                    val doc = target.createFile(source.type ?: "application/octet-stream", stagingName) ?: error("Cannot create destination.")
                    destination = doc
                    journal.save(doc.uri.toString(), doc.name ?: stagingName)
                    return resolver.openOutputStream(doc.uri) ?: error("Cannot write destination.")
                }
                override fun openTarget(): InputStream = resolver.openInputStream(checkNotNull(destination).uri) ?: error("Cannot verify destination. Original retained.")
                override fun commit() {
                    check(target.findFile(name) == null) { "Destination changed while copying. Original retained." }
                    val doc = checkNotNull(destination)
                    check(doc.renameTo(name) && doc.name == name) { "Destination provider cannot commit this filename. Original retained." }
                    journal.clear()
                }
                override fun deleteSource() = source.delete()
                override fun abort() {
                    val doc = destination
                    if (doc == null || doc.delete()) journal.clear()
                }
            }
            VerifiedTransfer.run(backend, move, progress)
        }
    }
    fun validName(value: String): Boolean = value.isNotBlank() && value != "." && value != ".." &&
        value.none { it == '/' || it == '\\' || it.code < 32 }
}
