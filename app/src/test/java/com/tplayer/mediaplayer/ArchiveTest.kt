package com.tplayer.mediaplayer

import com.tplayer.mediaplayer.data.*
import java.io.*
import java.util.zip.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class ArchiveTest {
    private fun archive(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip -> for ((name, data) in entries) { zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() } }
        return output.toByteArray()
    }
    private class Sink : ZipSink {
        val files = mutableMapOf<String, ByteArrayOutputStream>()
        override fun directory(path: List<String>) {}
        override fun file(path: List<String>) = ByteArrayOutputStream().also { files[path.joinToString("/")] = it }
    }
    @Test fun nestedAndEmptyArchivesExtractWithoutLosingTheirBytes() = runBlocking {
        val sink = Sink(); val bytes = ByteArray(16384) { (it % 251).toByte() }
        BoundedZip.extract(ByteArrayInputStream(archive("folder/song.bin" to bytes)), sink)
        assertArrayEquals(bytes, sink.files.getValue("folder/song.bin").toByteArray())
        BoundedZip.extract(ByteArrayInputStream(archive()), Sink())
    }
    @Test fun traversalAbsoluteAndAmbiguousPathsAreRejected() {
        for (name in listOf("../outside", "/absolute", "dir/../../outside", "C:/file", "dir\\file", "dir//file", "bad\u0000name")) {
            try { BoundedZip.path(name); fail("Unsafe path accepted: $name") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun expansionAndCollisionLimitsFailBeforePublishing() = runBlocking {
        val sink = Sink()
        try { BoundedZip.extract(ByteArrayInputStream(archive("large" to ByteArray(2048))), sink, limit = 64); fail("Expansion cap was ignored") } catch (_: IllegalArgumentException) { }
        assertTrue(sink.files.values.sumOf { it.size() } <= 64)
        try { BoundedZip.extract(ByteArrayInputStream(archive("A.txt" to byteArrayOf(1), "a.txt" to byteArrayOf(2))), Sink()); fail("Collision accepted") } catch (_: IllegalArgumentException) { }
        try { BoundedZip.extract(ByteArrayInputStream(archive("folder/" to ByteArray(2048))), Sink()); fail("Directory payload bypassed bounds") } catch (_: IllegalArgumentException) { }
        try { BoundedZip.extract(ByteArrayInputStream("not a zip".toByteArray()), Sink()); fail("Invalid signature accepted") } catch (_: IllegalArgumentException) { }
    }
}
