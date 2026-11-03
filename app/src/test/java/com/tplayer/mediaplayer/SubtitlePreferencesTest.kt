package com.tplayer.mediaplayer

import com.tplayer.mediaplayer.subtitle.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class SubtitlePreferencesTest {
    @Test fun fileSpecificDelaySizeAndPositionSurviveNewStore() {
        val root = Files.createTempDirectory("subtitle-preferences").toFile(); val codec = testCodec()
        try {
            val state = SubtitlePreferences(100, 22, 135, true)
            SubtitlePreferenceFiles(root, codec).save("content://file/one", state)
            assertEquals(state, SubtitlePreferenceFiles(root, codec).read("content://file/one"))
            assertNull(SubtitlePreferenceFiles(root, codec).read("content://file/two"))
            assertFalse(root.listFiles()!!.single().readText().contains("content://"))
        } finally { root.deleteRecursively() }
    }
    @Test fun latestPendingSaveAndOrderedReadCannotRestoreStaleGestureValues() = runBlocking {
        val root = Files.createTempDirectory("subtitle-pending").toFile()
        try {
            val codec = testCodec(); val repository = SubtitlePreferenceRepository(SubtitlePreferenceFiles(root, codec))
            val saves = (0..50).map { repository.save("content://media", SubtitlePreferences(it * 100L, 20, 120)) }
            saves.forEach { assertTrue(it.await().isSuccess) }
            assertEquals(5000L, repository.read("content://media")!!.delayMs)
            assertEquals(5000L, SubtitlePreferenceFiles(root, codec).read("content://media")!!.delayMs)
        } finally { root.deleteRecursively() }
    }
    @Test fun aReadInProgressSeesANewerPendingEdit() = runBlocking {
        val root = Files.createTempDirectory("subtitle-read-race").toFile(); val codec = testCodec()
        val entered = java.util.concurrent.CountDownLatch(1); val release = java.util.concurrent.CountDownLatch(1)
        try {
            SubtitlePreferenceFiles(root, codec).save("content://media", SubtitlePreferences())
            val slow = object : com.tplayer.mediaplayer.security.SecretCodec by codec {
                override fun decrypt(value: String, purpose: String): String {
                    entered.countDown(); check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    return codec.decrypt(value, purpose)
                }
            }
            val repository = SubtitlePreferenceRepository(SubtitlePreferenceFiles(root, slow))
            val reading = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).async { repository.read("content://media") }
            check(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val saving = repository.save("content://media", SubtitlePreferences(100))
            release.countDown()
            assertEquals(100L, reading.await()!!.delayMs)
            assertTrue(saving.await().isSuccess)
        } finally { release.countDown(); root.deleteRecursively() }
    }
    @Test fun invalidControlStateCannotOverwriteCommittedPreferences() {
        val root = Files.createTempDirectory("subtitle-invalid").toFile(); val codec = testCodec()
        try {
            val files = SubtitlePreferenceFiles(root, codec); files.save("content://media", SubtitlePreferences())
            try { files.save("content://media", SubtitlePreferences(600001)); fail("Invalid delay accepted") } catch (_: IllegalArgumentException) { }
            try { files.save("content://media", SubtitlePreferences(scale = 0)); fail("Invalid size accepted") } catch (_: IllegalArgumentException) { }
            assertEquals(SubtitlePreferences(), files.read("content://media"))
        } finally { root.deleteRecursively() }
    }
}
