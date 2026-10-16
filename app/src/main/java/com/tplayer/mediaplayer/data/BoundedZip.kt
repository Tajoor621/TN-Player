package com.tplayer.mediaplayer.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.*
import java.util.Locale
import java.util.zip.ZipInputStream

interface ZipSink {
    fun directory(path: List<String>)
    fun file(path: List<String>): OutputStream
}
object BoundedZip {
    const val MAX_BYTES = 512L * 1024 * 1024
    const val MAX_ENTRIES = 5000
    fun path(name: String): List<String> {
        require(name.length in 1..4096 && !name.startsWith('/') && !name.contains('\\') && !name.contains(':')) { "Unsafe archive path." }
        val parts = name.removeSuffix("/").split('/')
        require(parts.size <= 24 && parts.all { it.length <= 255 && DocumentOperations.validName(it) }) { "Unsafe archive path." }
        return parts
    }
    suspend fun extract(input: InputStream, sink: ZipSink, limit: Long = MAX_BYTES, entriesLimit: Int = MAX_ENTRIES,
        progress: (Long) -> Unit = {}) {
        require(limit in 1..MAX_BYTES && entriesLimit in 1..MAX_ENTRIES)
        val source = PushbackInputStream(BufferedInputStream(input), 4)
        val signature = ByteArray(4); var read = 0
        while (read < 4) { val n = source.read(signature, read, 4 - read); require(n > 0) { "Invalid ZIP file." }; read += n }
        require(signature.contentEquals(byteArrayOf(80, 75, 3, 4)) || signature.contentEquals(byteArrayOf(80, 75, 5, 6))) { "Invalid ZIP file." }
        if (signature.contentEquals(byteArrayOf(80, 75, 5, 6))) {
            val end = ByteArray(18); var count = 0
            while (count < end.size) { val n = source.read(end, count, end.size - count); require(n > 0) { "Truncated empty ZIP." }; count += n }
            require(end.take(16).all { it == 0.toByte() }) { "Invalid empty ZIP directory." }
            val comment = (end[16].toInt() and 255) or ((end[17].toInt() and 255) shl 8)
            repeat(comment) { require(source.read() >= 0) { "Truncated ZIP comment." } }
            require(source.read() == -1) { "Unexpected data after empty ZIP." }
            return
        }
        source.unread(signature)
        var bytes = 0L; var count = 0; val names = mutableSetOf<String>(); val buffer = ByteArray(65536)
        ZipInputStream(source).use { zip ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = zip.nextEntry ?: break
                require(++count <= entriesLimit) { "Archive has too many entries." }
                val parts = path(entry.name)
                require(names.add(parts.joinToString("/").lowercase(Locale.ROOT))) { "Archive contains colliding paths." }
                if (entry.isDirectory) {
                    sink.directory(parts)
                    require(zip.read() == -1) { "Directory entry unexpectedly contains data." }
                } else sink.file(parts).use { output ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val size = zip.read(buffer); if (size < 0) break
                        require(bytes + size <= limit) { "Archive expansion exceeds the size limit." }
                        output.write(buffer, 0, size); bytes += size; progress(bytes)
                    }
                }
                zip.closeEntry() // Validates the entry CRC before proceeding.
            }
        }
    }
}
