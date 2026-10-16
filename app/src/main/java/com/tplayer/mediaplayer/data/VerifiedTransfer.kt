package com.tplayer.mediaplayer.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

data class TransferProgress(val phase: String, val bytes: Long, val total: Long)
data class TransferResult(val bytes: Long, val sourceRemoved: Boolean)
interface TransferBackend {
    val expectedLength: Long
    fun openSource(): InputStream
    fun createTarget(): OutputStream
    fun openTarget(): InputStream
    fun commit()
    fun deleteSource(): Boolean
    fun abort()
}
object VerifiedTransfer {
    private suspend fun digest(input: InputStream, output: OutputStream?, phase: String, total: Long, progress: (TransferProgress) -> Unit): Pair<Long, ByteArray> {
        val hash = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(64 * 1024)
        var count = 0L; var last = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val size = input.read(buffer); if (size < 0) break
            if (size == 0) continue
            output?.write(buffer, 0, size); hash.update(buffer, 0, size); count += size
            val now = System.nanoTime()
            if (now - last > 100_000_000) { progress(TransferProgress(phase, count, total)); last = now }
        }
        progress(TransferProgress(phase, count, total))
        return count to hash.digest()
    }
    suspend fun run(backend: TransferBackend, move: Boolean, progress: (TransferProgress) -> Unit = {}): TransferResult {
        var committed = false
        var failure: Throwable? = null
        try {
            val copied = backend.openSource().use { input -> backend.createTarget().use { output -> digest(input, output, "Copying", backend.expectedLength, progress) } }
            check(backend.expectedLength < 0 || copied.first == backend.expectedLength) { "The source changed or could not be read completely. Original retained." }
            val verified = backend.openTarget().use { digest(it, null, "Verifying", copied.first, progress) }
            check(copied.first == verified.first && MessageDigest.isEqual(copied.second, verified.second)) { "Destination verification failed. Original retained." }
            currentCoroutineContext().ensureActive()
            backend.commit(); committed = true
            currentCoroutineContext().ensureActive()
            return TransferResult(copied.first, move && backend.deleteSource())
        } catch (e: Throwable) {
            failure = e; throw e
        } finally {
            if (!committed) try { backend.abort() } catch (cleanup: Exception) {
                if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
            }
        }
    }
}
