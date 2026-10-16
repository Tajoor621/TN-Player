package com.tplayer.mediaplayer

import android.util.AtomicFile
import com.tplayer.mediaplayer.playback.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.AEADBadTagException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class SecureCheckpointTest {
    @Test fun encryptionIsRandomizedAuthenticatedAndBoundToItsPurpose() {
        val codec = testCodec(); val value = "https://example.com/live?token=secret"
        val first = codec.encrypt(value, "queue"); val second = codec.encrypt(value, "queue")
        assertNotEquals(first, second); assertFalse(first.contains("secret")); assertEquals(value, codec.decrypt(first, "queue"))
        try { codec.decrypt(first, "other-purpose"); fail("Wrong purpose accepted") } catch (_: AEADBadTagException) { }
        val parts = first.split('.').toMutableList()
        val bytes = android.util.Base64.decode(parts[2], android.util.Base64.NO_WRAP); bytes[0] = (bytes[0].toInt() xor 1).toByte()
        parts[2] = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        try { codec.decrypt(parts.joinToString("."), "queue"); fail("Tampered ciphertext accepted") } catch (_: AEADBadTagException) { }
        assertEquals(codec.identifier(value), codec.identifier(value))
        assertNotEquals(codec.identifier(value), testCodec().identifier(value))
    }
    @Test fun aFreshStoreRestoresQueueIndexPositionAndExplicitStreamTypeWithoutPlaintext() {
        val file = File.createTempFile("playback-checkpoint", ".bin").apply { delete() }
        try {
            val codec = testCodec(); val uri = "https://example.com/live?token=secret"
            val value = PlaybackCheckpoint(listOf(QueueEntry("content://media/audio/1", "Song"), QueueEntry(uri, "Live", "application/x-mpegURL")), 1, 42123, 2, true)
            CheckpointFile(AtomicFile(file), codec).write(value)
            assertFalse(file.readText().contains("secret")); assertFalse(file.readText().contains("https://"))
            val restored = CheckpointFile(AtomicFile(file), codec).read()!!
            assertEquals(value, restored)
            assertEquals(uri, restored.items[1].mediaItem().mediaId)
            assertEquals("application/x-mpegURL", restored.items[1].mediaItem().localConfiguration!!.mimeType)
            CheckpointFile(AtomicFile(file), codec).write(null); assertNull(CheckpointFile(AtomicFile(file), codec).read())
        } finally { file.delete() }
    }
    @Test fun malformedCheckpointCannotRestoreAnArbitrarySchemeOrInvalidIndex() {
        val badScheme = """{"schema":1,"index":0,"position":1,"items":[{"uri":"javascript:evil()","title":"Bad"}]}"""
        try { PlaybackCheckpoint.parse(badScheme); fail("Invalid scheme accepted") } catch (_: IllegalArgumentException) { }
        val badIndex = """{"schema":1,"index":20,"position":1,"items":[{"uri":"content://media/1","title":"Song"}]}"""
        try { PlaybackCheckpoint.parse(badIndex); fail("Invalid index accepted") } catch (_: IllegalArgumentException) { }
    }
}
