@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import com.tplayer.mediaplayer.playback.*
import com.tplayer.mediaplayer.security.AndroidSecretCodec
import com.tplayer.mediaplayer.ui.PlayerActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

internal object DevicePlayback {
    val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    val context get() = ApplicationProvider.getApplicationContext<Context>()
    fun <T> main(action: () -> T): T {
        var value: Any? = null; var failure: Throwable? = null
        instrumentation.runOnMainSync { try { value = action() } catch (e: Throwable) { failure = e } }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST") return value as T
    }
    fun await(message: String, seconds: Long = 45, condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
        while (System.nanoTime() < end) { if (condition()) return; Thread.sleep(100) }
        fail(message)
    }
    fun fixture(name: String): android.net.Uri {
        val file = File(context.cacheDir, "exports/$name"); file.parentFile!!.mkdirs()
        instrumentation.context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
        return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }
    fun controller(): MediaController {
        val future = main { MediaController.Builder(context, SessionToken(context, ComponentName(context, MediaPlaybackService::class.java))).buildAsync() }
        return future.get(45, TimeUnit.SECONDS)
    }
    fun stop(controller: MediaController) = main { controller.stop(); controller.clearMediaItems(); controller.release() }
    fun launch(uri: android.net.Uri, mime: String, title: String = "Playback fixture"): ActivityScenario<PlayerActivity> = ActivityScenario.launch(
        Intent(context, PlayerActivity::class.java).setData(uri).putExtra("title", title).putExtra("mime", mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    fun descendants(view: View): Sequence<View> = sequence { yield(view); if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i))) }
}

@RunWith(AndroidJUnit4::class)
class PlaybackDeviceTest {
    @Test fun audioContinuesAfterActivityClosesAndPauseClearsScreenAwake() {
        val scenario = DevicePlayback.launch(DevicePlayback.fixture("audio-fixture.wav"), "audio/wav")
        val controller = DevicePlayback.controller()
        try {
            DevicePlayback.await("Audio never started") { DevicePlayback.main { controller.isPlaying && controller.currentPosition > 250 } }
            var awake = false
            scenario.onActivity { awake = it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0 }
            assertTrue("Playing audio did not enable screen awake", awake)
            DevicePlayback.main { controller.pause() }
            DevicePlayback.await("Pause did not clear screen awake") {
                scenario.onActivity { awake = it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0 }
                !awake
            }
            DevicePlayback.main { controller.play() }
            scenario.close()
            val before = DevicePlayback.main { controller.currentPosition }
            DevicePlayback.await("Audio stopped when the Activity closed") { DevicePlayback.main { controller.isPlaying && controller.currentPosition > before + 300 } }
        } finally { DevicePlayback.stop(controller); scenario.close() }
    }
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun videoEntersPipAndPlaybackSurvivesTheTransition() {
        assumeTrue(DevicePlayback.context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))
        val scenario = DevicePlayback.launch(DevicePlayback.fixture("video-fixture.mp4"), "video/mp4")
        val controller = DevicePlayback.controller()
        try {
            DevicePlayback.main { controller.repeatMode = Player.REPEAT_MODE_ONE; if (controller.playbackState == Player.STATE_ENDED) { controller.seekToDefaultPosition(); controller.play() } }
            DevicePlayback.await("Video never started", 90) { DevicePlayback.main { controller.isPlaying && controller.videoSize.width > 0 } }
            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(android.R.id.content)
                DevicePlayback.descendants(root).filterIsInstance<TextView>().first { it.text.toString() == "PiP" }.performClick()
            }
            var pip = false
            DevicePlayback.await("PiP transition failed") { scenario.onActivity { pip = it.isInPictureInPictureMode }; pip }
            assertTrue(DevicePlayback.main { controller.isPlaying })
        } finally { DevicePlayback.stop(controller); scenario.close() }
    }
    @Test fun realAndroidKeystoreRoundTripsAndRejectsWrongPurpose() {
        val codec = AndroidSecretCodec(DevicePlayback.context)
        val encrypted = codec.encrypt("https://example.com/live?token=device-secret", "device-test")
        assertFalse(encrypted.contains("device-secret"))
        assertEquals("https://example.com/live?token=device-secret", AndroidSecretCodec(DevicePlayback.context).decrypt(encrypted, "device-test"))
        try { codec.decrypt(encrypted, "wrong-purpose"); fail("Wrong-purpose decryption succeeded") } catch (_: javax.crypto.AEADBadTagException) { }
    }
    @Test fun localFileMoveVerifiesItsBytesBeforeRemovingSource() = runBlocking {
        val root = File(DevicePlayback.context.cacheDir, "transfer-fixture").apply { mkdirs() }
        val source = File(root, "source.bin").apply { writeBytes(ByteArray(180000) { (it % 251).toByte() }) }
        val expected = source.readBytes(); val target = File(root, "target").apply { mkdir() }
        try {
            val result = com.tplayer.mediaplayer.data.DocumentOperations.copy(DevicePlayback.context,
                androidx.documentfile.provider.DocumentFile.fromFile(source), androidx.documentfile.provider.DocumentFile.fromFile(target), true)
            assertTrue(result.sourceRemoved); assertFalse(source.exists()); assertArrayEquals(expected, File(target, "source.bin").readBytes())
        } finally { root.deleteRecursively() }
    }
}

@RunWith(AndroidJUnit4::class)
class PrepareRecoveryTest {
    @Test fun persistAQueueForTheExternalProcessDeathCheck() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("prepare") == "true")
        val uri = DevicePlayback.fixture("audio-fixture.wav")
        val scenario = DevicePlayback.launch(uri, "audio/wav", "Recovery audio")
        val controller = DevicePlayback.controller()
        try {
            DevicePlayback.await("Recovery fixture did not start") { DevicePlayback.main { controller.isPlaying && controller.isCurrentMediaItemSeekable } }
            DevicePlayback.main { controller.seekTo(12000) }
            DevicePlayback.await("Recovery position was not checkpointed") { runBlocking { PlaybackPersistence.get(DevicePlayback.context).read()?.position ?: 0 } >= 12000 }
            runBlocking { PlaybackPersistence.get(DevicePlayback.context).flush() }
        } finally { scenario.close(); DevicePlayback.main { controller.release() } }
    }
}
@RunWith(AndroidJUnit4::class)
class ColdRestoreTest {
    @Test fun aNewProcessRestoresTheEncryptedQueueAndPosition() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("coldRestore") == "true")
        val saved = runBlocking { PlaybackPersistence.get(DevicePlayback.context).read() }
        assertNotNull("No checkpoint survived process death", saved)
        assertTrue("Checkpoint position was lost", saved!!.position >= 12000)
        val scenario = ActivityScenario.launch<PlayerActivity>(Intent(DevicePlayback.context, PlayerActivity::class.java).putExtra("resume", true))
        val controller = DevicePlayback.controller()
        try {
            DevicePlayback.await("Cold playback did not resume") { DevicePlayback.main { controller.isPlaying && controller.mediaMetadata.title?.toString() == "Recovery audio" && controller.currentPosition >= 12000 } }
        } finally { DevicePlayback.stop(controller); scenario.close() }
    }
}
