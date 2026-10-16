package com.tplayer.mediaplayer.data

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ArchiveOperations {
    private val gate = Mutex()
    suspend fun run(context: Context, source: DocumentFile, parent: DocumentFile, extract: Boolean,
        progress: (Long) -> Unit) = withContext(Dispatchers.IO) { gate.withLock {
        require(source.canRead() && parent.isDirectory && parent.canWrite()) { "Choose a readable source and writable folder." }
        val sourceName = source.name ?: error("Unknown source name.")
        val finalName = if (extract) sourceName.substringBeforeLast('.', sourceName) + "-extracted" else "$sourceName.zip"
        require(DocumentOperations.validName(finalName))
        require(parent.findFile(finalName) == null) { "Destination exists. Nothing was overwritten." }
        val stageName = ".tn-archive-${UUID.randomUUID()}"
        val stage = if (extract) parent.createDirectory(stageName) else parent.createFile("application/zip", "$stageName.zip")
        checkNotNull(stage) { "Cannot create archive staging output." }
        var committed = false
        try {
            if (extract) {
                require(!source.isDirectory)
                fun ensureDirectory(parts: List<String>): DocumentFile {
                    var current = stage
                    for (part in parts) {
                        val next = current.findFile(part) ?: current.createDirectory(part) ?: error("Cannot create extracted folder.")
                        check(next.isDirectory && next.name == part) { "The provider changed an extracted path." }
                        current = next
                    }
                    return current
                }
                val sink = object : ZipSink {
                    override fun directory(path: List<String>) { ensureDirectory(path) }
                    override fun file(path: List<String>): OutputStream {
                        val folder = ensureDirectory(path.dropLast(1)); val name = path.last()
                        check(folder.findFile(name) == null) { "Archive paths collide." }
                        val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
                        val file = folder.createFile(mime, name) ?: error("Cannot create extracted file.")
                        if (file.name != name) check(folder.findFile(name) == null && file.renameTo(name) && file.name == name) { "The provider changed an extracted filename." }
                        return context.contentResolver.openOutputStream(file.uri) ?: error("Cannot write extracted file.")
                    }
                }
                checkNotNull(context.contentResolver.openInputStream(source.uri)).use { BoundedZip.extract(it, sink, progress = progress) }
            } else {
                val files = mutableListOf<Pair<DocumentFile, String>>(); val seen = mutableSetOf<String>()
                fun collect(doc: DocumentFile, name: String, depth: Int) {
                    require(depth <= 24 && files.size < BoundedZip.MAX_ENTRIES && seen.add(doc.uri.toString())) { "Folder is too deep, large or cyclic." }
                    BoundedZip.path(name)
                    if (doc.isDirectory) {
                        files += doc to "$name/"
                        for (child in doc.listFiles()) collect(child, "$name/${child.name ?: error("Unknown filename.")}", depth + 1)
                    } else { require(doc.canRead()); files += doc to name }
                }
                collect(source, sourceName, 0)
                var bytes = 0L; val buffer = ByteArray(65536)
                checkNotNull(context.contentResolver.openOutputStream(stage.uri)).use { raw -> ZipOutputStream(raw.buffered()).use { zip ->
                    for ((file, name) in files) {
                        currentCoroutineContext().ensureActive(); zip.putNextEntry(ZipEntry(name))
                        if (!file.isDirectory) checkNotNull(context.contentResolver.openInputStream(file.uri)).use { input ->
                            while (true) {
                                currentCoroutineContext().ensureActive(); val count = input.read(buffer); if (count < 0) break
                                require(bytes + count <= BoundedZip.MAX_BYTES) { "Archive input exceeds the size limit." }
                                zip.write(buffer, 0, count); bytes += count; progress(bytes)
                            }
                        }
                        zip.closeEntry()
                    }
                } }
                // Read every written entry back and validate its CRC before naming the result.
                checkNotNull(context.contentResolver.openInputStream(stage.uri)).use { input -> BoundedZip.extract(input, object : ZipSink {
                    override fun directory(path: List<String>) {}
                    override fun file(path: List<String>) = object : OutputStream() { override fun write(value: Int) {}; override fun write(bytes: ByteArray, offset: Int, length: Int) {} }
                }) }
            }
            currentCoroutineContext().ensureActive()
            check(parent.findFile(finalName) == null && stage.renameTo(finalName) && stage.name == finalName) { "Could not finalize the archive output. Original retained." }
            committed = true
        } finally {
            if (!committed && !stage.delete()) android.util.Log.w("TNPlayer", "Archive staging output could not be removed; check hidden files.")
        }
    } }
}
