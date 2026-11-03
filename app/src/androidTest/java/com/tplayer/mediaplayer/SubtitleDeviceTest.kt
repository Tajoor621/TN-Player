@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer

import android.net.Uri
import android.view.View
import android.widget.TextView
import android.widget.EditText
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.onData
import org.hamcrest.Matchers.equalTo
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.tplayer.mediaplayer.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SubtitleDeviceTest {
    private fun waitFor(text: String) = DevicePlayback.await("Missing dialog: $text") {
        DevicePlayback.instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.any { it.isVisibleToUser } == true
    }
    private fun tool(text: String) {
        waitFor(text)
        onView(withText(text)).inRoot(isDialog()).perform(object : androidx.test.espresso.ViewAction {
            override fun getConstraints() = isDisplayed()
            override fun getDescription() = "Wait for tools sheet expansion"
            override fun perform(ui: androidx.test.espresso.UiController, view: View) {
                var sheet = view
                while (sheet.id != com.google.android.material.R.id.design_bottom_sheet) sheet = sheet.parent as View
                val behavior = BottomSheetBehavior.from(sheet)
                val deadline = android.os.SystemClock.uptimeMillis() + 5000
                while (behavior.state != BottomSheetBehavior.STATE_EXPANDED && android.os.SystemClock.uptimeMillis() < deadline) ui.loopMainThreadForAtLeast(16)
                assertEquals(BottomSheetBehavior.STATE_EXPANDED, behavior.state)
            }
        }, click())
    }
    @Test fun importedCaptionsRenderSeekShiftAndSurviveRecreationWithoutMovingPlayback() {
        val source = File(DevicePlayback.context.cacheDir, "device-captions.mpl").apply { writeText("[10][40]First caption\n[200][240]Second caption") }
        val scenario = DevicePlayback.launch(DevicePlayback.fixture("video-fixture.mp4"), "video/mp4")
        val controller = DevicePlayback.controller()
        try {
            DevicePlayback.await("Video not ready") { DevicePlayback.main { controller.isPlaying && controller.playbackState == Player.STATE_READY } }
            DevicePlayback.main { controller.pause(); controller.seekTo(2000) }
            scenario.onActivity { it.attachSubtitle(Uri.fromFile(source)) }
            DevicePlayback.await("Subtitle not imported") { var ready = false; scenario.onActivity { ready = ViewModelProvider(it)[PlayerUiState::class.java].captions?.document?.cues?.size == 2 }; ready }
            fun shownCaption(): String {
                var text = ""
                scenario.onActivity { activity -> val view = activity.findViewById<View>(android.R.id.content).findViewWithTag<TextView>("external-subtitles"); if (view.isShown) text = view.text.toString() }
                return text
            }
            DevicePlayback.await("Imported caption did not render") { shownCaption() == "First caption" }
            assertTrue(DevicePlayback.main { !controller.isPlaying && controller.currentPosition in 1800..2200 })
            fun openSubtitle() {
                scenario.onActivity { activity ->
                    DevicePlayback.descendants(activity.findViewById(android.R.id.content)).filterIsInstance<PlayerView>().first().showController()
                    DevicePlayback.descendants(activity.findViewById(android.R.id.content)).filterIsInstance<TextView>().first { it.text.toString() == "More" }.performClick()
                }
                tool("Subtitle")
            }
            openSubtitle(); waitFor("Next caption"); onView(withText("Next caption")).inRoot(isDialog()).perform(click())
            DevicePlayback.await("Next caption did not seek") { DevicePlayback.main { controller.currentPosition in 19800..20200 } }
            openSubtitle(); waitFor("Subtitle timing"); onView(withText("Subtitle timing")).inRoot(isDialog()).perform(click())
            waitFor("Subtitle delay (ms)"); onView(isAssignableFrom(EditText::class.java)).inRoot(isDialog()).perform(replaceText("1000"), closeSoftKeyboard())
            onView(withText("Apply")).inRoot(isDialog()).perform(click())
            DevicePlayback.await("Delay not applied") { var ready = false; scenario.onActivity { ready = ViewModelProvider(it)[PlayerUiState::class.java].captions?.delayMs == 1000L }; ready }
            DevicePlayback.main { controller.seekTo(20500) }
            DevicePlayback.await("Old subtitle remained before shifted cue") { shownCaption().isEmpty() }
            DevicePlayback.main { controller.seekTo(21500) }
            DevicePlayback.await("Shifted subtitle did not render") { shownCaption() == "Second caption" }
            openSubtitle(); onData(equalTo("Subtitle speed correction")).inRoot(isDialog()).perform(click())
            waitFor("Subtitle speed (%)"); onView(isAssignableFrom(EditText::class.java)).inRoot(isDialog()).perform(replaceText("200"), closeSoftKeyboard()); onView(withText("Apply")).inRoot(isDialog()).perform(click())
            DevicePlayback.await("Rate not saved") { var ready = false; scenario.onActivity { ready = ViewModelProvider(it)[PlayerUiState::class.java].captions?.ratePermille == 2000 }; ready }
            DevicePlayback.main { controller.seekTo(11500) }
            DevicePlayback.await("Rate correction did not render") { shownCaption() == "Second caption" }
            scenario.recreate()
            scenario.onActivity { assertEquals(1000L, ViewModelProvider(it)[PlayerUiState::class.java].captions?.delayMs) }
            assertTrue(DevicePlayback.main { !controller.isPlaying && controller.currentPosition in 11300..11700 })
            scenario.close()
            androidx.test.core.app.ActivityScenario.launch<PlayerActivity>(android.content.Intent(DevicePlayback.context, PlayerActivity::class.java)).use { reopened ->
                DevicePlayback.await("Finishing the player lost saved captions") {
                    var text = ""; reopened.onActivity { activity -> val caption = activity.findViewById<View>(android.R.id.content).findViewWithTag<TextView>("external-subtitles"); if (caption.isShown) text = caption.text.toString() }; text == "Second caption"
                }
            }
        } finally { scenario.close(); DevicePlayback.stop(controller); com.tplayer.mediaplayer.subtitle.CaptionRepository.get(DevicePlayback.context).remove(DevicePlayback.fixture("video-fixture.mp4").toString()); source.delete() }
    }
    @Test fun nativeOverlongCaptionDoesNotExtendVideoPlayback() {
        val file = File(DevicePlayback.context.cacheDir, "native-end-bound.ttml").apply {
            writeText("<tt xmlns=\"http://www.w3.org/ns/ttml\"><body><div><p begin=\"00:00:00.000\" end=\"00:03:00.000\">Long caption</p></div></body></tt>")
        }
        val uri = DevicePlayback.fixture("video-fixture.mp4")
        val scenario = DevicePlayback.launch(uri, "video/mp4")
        val controller = DevicePlayback.controller()
        try {
            val caption = androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
                .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_TTML).setLanguage("en")
                .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT).build()
            DevicePlayback.main {
                controller.setMediaItem(androidx.media3.common.MediaItem.Builder().setUri(uri).setMediaId(uri.toString() + "#native-end-test").setSubtitleConfigurations(listOf(caption)).build())
                controller.prepare(); controller.pause()
            }
            DevicePlayback.await("Native subtitle track was not selected") { DevicePlayback.main { controller.currentTracks.isTypeSelected(androidx.media3.common.C.TRACK_TYPE_TEXT) && controller.playbackState == Player.STATE_READY } }
            DevicePlayback.main { controller.seekTo(88000); controller.play() }
            DevicePlayback.await("Overlong native caption kept the video playing", 15) { DevicePlayback.main { controller.playbackState == Player.STATE_ENDED } }
            assertTrue(DevicePlayback.main { controller.currentPosition <= 91000 })
        } finally { scenario.close(); DevicePlayback.stop(controller); file.delete() }
    }

}
