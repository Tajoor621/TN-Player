package com.tplayer.mediaplayer

import com.tplayer.mediaplayer.data.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.*

class VerifiedTransferTest {
    private class Backend(val source: ByteArray, val corrupt: Boolean = false, val failWrite: Boolean = false, val removable: Boolean = true) : TransferBackend {
        val output = ByteArrayOutputStream(); var committed = false; var deleted = false; var aborted = false
        override val expectedLength = source.size.toLong()
        override fun openSource() = ByteArrayInputStream(source)
        override fun createTarget(): OutputStream = if (failWrite) object : OutputStream() { override fun write(value: Int) { throw IOException("disk full") } } else output
        override fun openTarget(): InputStream = ByteArrayInputStream(output.toByteArray().also { if (corrupt && it.isNotEmpty()) it[0] = (it[0].toInt() xor 1).toByte() })
        override fun commit() { committed = true }
        override fun deleteSource(): Boolean { check(committed); deleted = removable; return removable }
        override fun abort() { aborted = true }
    }
    @Test fun moveOnlyDeletesTheOriginalAfterFullReadbackVerification() = runBlocking {
        val backend = Backend(ByteArray(200000) { (it % 251).toByte() })
        val phases = mutableSetOf<String>()
        val result = VerifiedTransfer.run(backend, true) { phases += it.phase }
        assertTrue(backend.committed); assertTrue(backend.deleted); assertFalse(backend.aborted)
        assertEquals(200000L, result.bytes); assertTrue(result.sourceRemoved)
        assertEquals(setOf("Copying", "Verifying"), phases)
    }
    @Test fun corruptionRetainsOriginalAndAbortsTheIncompleteTarget() = runBlocking {
        val backend = Backend(byteArrayOf(1,2,3), corrupt = true)
        try { VerifiedTransfer.run(backend, true); fail("Corruption accepted") } catch (_: IllegalStateException) { }
        assertFalse(backend.deleted); assertFalse(backend.committed); assertTrue(backend.aborted)
    }
    @Test fun diskFullNeverDeletesTheOriginal() = runBlocking {
        val backend = Backend(byteArrayOf(1,2,3), failWrite = true)
        try { VerifiedTransfer.run(backend, true); fail("Failed write accepted") } catch (_: IOException) { }
        assertFalse(backend.deleted); assertFalse(backend.committed); assertTrue(backend.aborted)
    }
    @Test fun failedSourceDeletionLeavesBothCopiesAndReportsIt() = runBlocking {
        val backend = Backend(byteArrayOf(1,2,3), removable = false)
        val result = VerifiedTransfer.run(backend, true)
        assertTrue(backend.committed); assertFalse(result.sourceRemoved); assertFalse(backend.aborted)
    }
    @Test fun cancellationDuringCopyKeepsTheOriginal() = runBlocking {
        val backend = Backend(ByteArray(300000) { 12 })
        val job = launch {
            val context = currentCoroutineContext()
            try { VerifiedTransfer.run(backend, true) { context.cancel() } }
            catch (_: CancellationException) { }
        }
        job.join(); assertFalse(backend.deleted); assertFalse(backend.committed); assertTrue(backend.aborted)
    }
}
