@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tplayer.mediaplayer.data.*
import com.tplayer.mediaplayer.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SelectionPlayerDeviceTest {
    private fun waitForDialog(text: String) {
        DevicePlayback.await("Dialog did not show: $text") {
            DevicePlayback.instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.any { it.isVisibleToUser } == true
        }
    }
    private fun capture(name: String) {
        DevicePlayback.instrumentation.waitForIdleSync()
        for (command in listOf("mkdir -p /sdcard/Download/tn-player-validation", "screencap -p /sdcard/Download/tn-player-validation/$name.png")) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(DevicePlayback.instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
    }
    @Test fun selectionCancelAndDeleteAffectOnlyTheConfirmedFilesAndDetailsKeepHistory() {
        val context = DevicePlayback.context
        val base = File(context.cacheDir, "selection-${System.nanoTime()}").apply { mkdirs() }
        val first = File(base, "One.wav"); val second = File(base, "Two.wav")
        for (file in listOf(first, second)) DevicePlayback.instrumentation.context.assets.open("audio-fixture.wav").use { input -> file.outputStream().use { input.copyTo(it) } }
        val secondUri = android.net.Uri.fromFile(second).toString()
        runBlocking { MediaDatabase.get(context).media().record(History(secondUri, "Two.wav", 10000, 90000, mimeType = "audio/wav")) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                fun labels(activity: MainActivity) = DevicePlayback.descendants(activity.findViewById(android.R.id.content)).filterIsInstance<TextView>()
                scenario.onActivity { activity ->
                    ViewModelProvider(activity)[BrowserState::class.java].folder = androidx.documentfile.provider.DocumentFile.fromFile(base)
                    DevicePlayback.descendants(activity.findViewById(android.R.id.content)).filterIsInstance<BottomNavigationView>().single().selectedItemId = 3
                }
                DevicePlayback.await("Chosen-folder files were not listed") { var ok = false; scenario.onActivity { ok = labels(it).any { v -> v.text.toString() == "One.wav" } }; ok }
                scenario.onActivity { (labels(it).first { v -> v.text.toString() == "One.wav" }.parent.parent as View).performLongClick() }
                scenario.onActivity { activity -> assertTrue(labels(activity).any { it.text.toString() == "1 / 2 selected" }) }
                scenario.onActivity { labels(it).first { v -> v.text.toString() == "Select all" }.performClick() }
                scenario.onActivity { activity -> assertTrue(labels(activity).any { it.text.toString() == "2 / 2 selected" }) }
                capture("file-selection")
                scenario.onActivity { activity -> DevicePlayback.descendants(activity.findViewById(android.R.id.content)).first { it.contentDescription == "Delete selected" }.performClick() }
                waitForDialog("Delete 2 files?")
                onView(withText("Cancel")).inRoot(isDialog()).perform(click())
                assertTrue(first.exists()); assertTrue(second.exists())
                scenario.onActivity { (labels(it).first { v -> v.text.toString() == "Two.wav" }.parent.parent as View).performClick() }
                scenario.onActivity { activity -> DevicePlayback.descendants(activity.findViewById(android.R.id.content)).first { it.contentDescription == "Delete selected" }.performClick() }
                waitForDialog("Delete 1 file?")
                onView(withText("Delete")).inRoot(isDialog()).perform(click())
                DevicePlayback.await("Confirmed file was not removed") { !first.exists() }
                assertTrue("Unselected sibling was removed", second.exists())
                DevicePlayback.await("Deletion result was not acknowledged") { var ok = false; scenario.onActivity { ok = ViewModelProvider(it)[FileDeleteModel::class.java].state.value.total == 0 }; ok }
                onView(withText("OK")).perform(click())
            }
            val description = runBlocking { MediaDetails(context).describe(LibraryItem(secondUri, "Two.wav", mimeType = "audio/wav"), MediaDatabase.get(context).raw().historyFor(secondUri)) }
            assertTrue(description.contains("Name: Two.wav")); assertTrue(description.contains("Saved position: 0:10")); assertTrue(description.contains("Resume at: 0:10"))
        } finally { base.deleteRecursively() }
    }
    @androidx.test.filters.SdkSuppress(minSdkVersion = 30)
    @Test fun indexedDeletionWaitsForApprovalAndCancellationLeavesTheFileIntact() {
        val context = DevicePlayback.context
        val uri = checkNotNull(context.contentResolver.insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, android.content.ContentValues().apply {
            put("_display_name", "Consent-${System.nanoTime()}.mp4"); put("mime_type", "video/mp4"); put("relative_path", "Movies/TN-consent-test/"); put("is_pending", 1)
        }))
        try {
            context.contentResolver.openOutputStream(uri)!!.use { output -> DevicePlayback.instrumentation.context.assets.open("video-fixture.mp4").use { it.copyTo(output) } }
            context.contentResolver.update(uri, android.content.ContentValues().apply { put("is_pending", 0) }, null, null)
            val model = DevicePlayback.main { FileDeleteModel(context.applicationContext as android.app.Application).apply { start(listOf(LibraryItem(uri.toString(), "Approval fixture", mimeType = "video/mp4"))) } }
            DevicePlayback.await("System approval request was not created") { model.state.value.sender != null }
            assertTrue(context.contentResolver.query(uri, arrayOf("_id"), null, null, null)!!.use { it.moveToFirst() })
            DevicePlayback.main { model.result(false) }
            assertFalse(model.state.value.running)
            assertTrue(context.contentResolver.query(uri, arrayOf("_id"), null, null, null)!!.use { it.moveToFirst() })
        } finally { context.contentResolver.delete(uri, null, null) }
    }
    @Test fun portraitLandscapeToolsAndReopeningAFilePreserveItsResumePosition() {
        val context = DevicePlayback.context; val settings = Settings(context)
        val oldDisplay = settings.videoDisplay; val oldBackground = settings.backgroundPlayback; val oldResume = settings.resume
        val oldRotation = settings.playerOrientation; val oldMirror = settings.mirror; val oldFlip = settings.verticalFlip; val oldNight = settings.playerNight
        settings.videoDisplay = true; settings.backgroundPlayback = true; settings.resume = true; settings.playerOrientation = 1; settings.mirror = false; settings.verticalFlip = false; settings.playerNight = false
        val source = File(context.cacheDir, "exports/resume-${System.nanoTime()}.wav").apply { parentFile!!.mkdirs() }
        DevicePlayback.instrumentation.context.assets.open("audio-fixture.wav").use { input -> source.outputStream().use { input.copyTo(it) } }
        val audio = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", source)
        val video = DevicePlayback.fixture("video-fixture.mp4")
        runBlocking { MediaDatabase.get(context).media().record(History(audio.toString(), "Resume fixture", 12000, 90000, mimeType = "audio/wav")) }
        val controller = DevicePlayback.controller()
        var scenario: ActivityScenario<PlayerActivity>? = null
        try {
            DevicePlayback.main { controller.stop(); controller.clearMediaItems() }
            scenario = DevicePlayback.launch(audio, "audio/wav")
            DevicePlayback.await("Stored resume point was not restored") { DevicePlayback.main { controller.currentMediaItem?.mediaId == audio.toString() && controller.currentPosition in 11000..25000 } }
            DevicePlayback.main { controller.seekTo(18000); controller.pause() }
            DevicePlayback.await("Pause did not save per-file history") { runBlocking { (MediaDatabase.get(context).raw().historyFor(audio.toString())?.position ?: 0) >= 17500 } }
            scenario.close(); scenario = DevicePlayback.launch(video, "video/mp4")
            DevicePlayback.await("Video did not start") { DevicePlayback.main { controller.videoSize.width > 0 && controller.isPlaying } }
            DevicePlayback.main { controller.pause(); controller.seekTo(8000) }
            val active = checkNotNull(scenario)
            fun showControls() { active.onActivity { activity -> DevicePlayback.descendants(activity.findViewById(android.R.id.content)).filterIsInstance<PlayerView>().first().showController() } }
            showControls(); capture("player-portrait")
            active.onActivity { activity -> DevicePlayback.descendants(activity.findViewById(android.R.id.content)).filterIsInstance<TextView>().first { it.text.toString() == "Rotate" }.performClick() }
            waitForDialog("Landscape")
            onView(withText("Landscape")).inRoot(isDialog()).perform(click())
            DevicePlayback.await("Landscape was not applied") { var ok = false; active.onActivity { ok = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }; ok }
            assertEquals(video.toString(), DevicePlayback.main { controller.currentMediaItem?.mediaId })
            assertTrue(DevicePlayback.main { !controller.isPlaying && controller.currentPosition in 7800..8200 })
            showControls()
            DevicePlayback.await("Landscape must retain time and skip controls") {
                var ok = false
                active.onActivity { activity ->
                    val duration = activity.findViewById<View>(androidx.media3.ui.R.id.exo_duration)
                    val previous = activity.findViewById<View>(androidx.media3.ui.R.id.exo_prev)
                    ok = duration.isShown && previous.isShown
                }
                ok
            }
            capture("player-landscape")
            active.onActivity { activity -> DevicePlayback.descendants(activity.findViewById(android.R.id.content)).filterIsInstance<TextView>().first { it.text.toString() == "More" }.performClick() }
            waitForDialog("Display Settings")
            capture("player-tools")
            onView(withText("Display Settings")).perform(click())
            waitForDialog("Mirror mode")
            onView(withText("Mirror mode")).inRoot(isDialog()).perform(click())
            active.onActivity { activity -> assertTrue(DevicePlayback.descendants(activity.findViewById(android.R.id.content)).filterIsInstance<PlayerView>().first().videoSurfaceView!!.scaleX < 0) }
            active.close(); scenario = DevicePlayback.launch(audio, "audio/wav")
            DevicePlayback.await("Returning to a different file lost its saved position") { DevicePlayback.main { controller.currentMediaItem?.mediaId == audio.toString() && controller.currentPosition in 17500..27000 } }
        } finally {
            scenario?.close(); DevicePlayback.stop(controller)
            settings.videoDisplay = oldDisplay; settings.backgroundPlayback = oldBackground; settings.resume = oldResume; settings.playerOrientation = oldRotation
            settings.mirror = oldMirror; settings.verticalFlip = oldFlip; settings.playerNight = oldNight
            source.delete()
        }
    }
}
