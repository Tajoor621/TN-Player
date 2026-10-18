package com.tplayer.mediaplayer

import android.util.AtomicFile
import com.tplayer.mediaplayer.security.SecretCodec
import com.tplayer.mediaplayer.subtitle.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class CaptionStoreTest {
    private fun captions(id: String = "https://example.com/movie?token=private") = ExternalCaptions(id, "Private captions", SubtitleDocument(listOf(SubtitleCue(1000, 4000, "Secret caption"))), -250, 1250)
    @Test fun freshStoreRestoresTextTimingRateAndKeepsDiskContentsAndFilenamesOpaque() {
        val dir = Files.createTempDirectory("captions").toFile(); val codec = testCodec()
        try {
            val value = captions(); CaptionFileStore(dir, codec).save(value)
            assertEquals(value, CaptionFileStore(dir, codec).read(value.mediaId))
            val file = dir.listFiles()!!.single(); assertTrue(file.name.matches(Regex("[a-f0-9]{64}\\.v1")))
            assertFalse(file.readText().contains("Secret")); assertFalse(file.readText().contains("https")); assertFalse(file.readText().contains("private"))
            assertNull(CaptionFileStore(dir, codec).read("content://other-file"))
            CaptionFileStore(dir, codec).remove(value.mediaId); assertNull(CaptionFileStore(dir, codec).read(value.mediaId))
        } finally { dir.deleteRecursively() }
    }
    @Test fun failedEncryptionPreservesThePreviouslyCommittedSession() {
        val dir = Files.createTempDirectory("captions-failure").toFile(); val codec = testCodec(); val value = captions()
        try {
            CaptionFileStore(dir, codec).save(value)
            val failing = object : SecretCodec by codec { override fun encrypt(value: String, purpose: String): String = error("Keystore unavailable") }
            rejects { CaptionFileStore(dir, failing).save(value.copy(delayMs = 1000)) }
            assertEquals(value, CaptionFileStore(dir, codec).read(value.mediaId))
        } finally { dir.deleteRecursively() }
    }
    @Test fun wrongKeysAndTamperedRecordsNeverFallBackToPlaintext() {
        val dir = Files.createTempDirectory("captions-tampered").toFile(); val codec = testCodec(); val value = captions()
        try {
            CaptionFileStore(dir, codec).save(value)
            val file = dir.listFiles()!!.single()
            file.writeText(file.readText().dropLast(4) + "AAAA")
            rejects { CaptionFileStore(dir, codec).read(value.mediaId) }
            CaptionFileStore(dir, codec).save(value)
            val bad = object : SecretCodec by codec { override fun decrypt(value: String, purpose: String) = testCodec().decrypt(value, purpose) }
            rejects { CaptionFileStore(dir, bad).read(value.mediaId) }
            assertTrue(file.exists())
        } finally { dir.deleteRecursively() }
    }
    @Test fun retentionIsBoundedAndUnrelatedAppFilesAreNeverDeleted() {
        val dir = Files.createTempDirectory("captions-retention").toFile(); val codec = testCodec(); val store = CaptionFileStore(dir, codec)
        try {
            val unrelated = File(dir, "keep.txt").apply { writeText("Unrelated") }
            repeat(12) { store.save(captions("content://media/$it")); File(dir, codec.identifier("content://media/$it") + ".v1").setLastModified(1000L + it) }
            assertEquals(CaptionFileStore.MAX_RECORDS, dir.listFiles()!!.count { it.extension == "v1" })
            assertNull(store.read("content://media/0")); assertNotNull(store.read("content://media/11")); assertTrue(unrelated.exists())
        } finally { dir.deleteRecursively() }
    }
    @Test fun recoveryUsesAtomicBackupAndRejectsInvalidDocumentTiming() {
        val dir = Files.createTempDirectory("captions-atomic").toFile(); val codec = testCodec(); val value = captions(); val store = CaptionFileStore(dir, codec)
        try {
            store.save(value); val file = dir.listFiles()!!.single()
            check(file.renameTo(File(file.path + ".bak")))
            assertEquals(value, CaptionFileStore(dir, codec).read(value.mediaId))
            rejects { store.save(value.copy(document = SubtitleDocument(listOf(SubtitleCue(4000, 1000, "Bad"))))) }
            rejects { store.save(value.copy(ratePermille = 499)) }
            assertEquals(value, store.read(value.mediaId))
        } finally { dir.deleteRecursively() }
    }
    private fun rejects(action: () -> Any?) { try { action(); fail("Invalid caption state accepted") } catch (_: Exception) {} }
}
