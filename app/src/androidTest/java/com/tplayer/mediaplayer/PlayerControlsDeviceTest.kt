@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer

import android.view.View
import android.widget.TextView
import androidx.media3.ui.PlayerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tplayer.mediaplayer.data.Settings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerControlsDeviceTest {
    @Test fun optionalStatusDisplaysRemainingTimeAndDoubleTapCanPause() {
        val settings = Settings(DevicePlayback.context)
        val original = listOf(settings.showPlayerClock, settings.showPlayerBattery, settings.showRemainingTime, settings.doubleTapPlayPause, settings.gestures)
        settings.showPlayerClock = true; settings.showPlayerBattery = true; settings.showRemainingTime = true; settings.doubleTapPlayPause = true; settings.gestures = true
        val scenario = DevicePlayback.launch(DevicePlayback.fixture("video-fixture.mp4"), "video/mp4")
        val controller = DevicePlayback.controller()
        try {
            DevicePlayback.await("Video did not start") { DevicePlayback.main { controller.isPlaying && controller.duration > 0 } }
            DevicePlayback.await("Optional status was not rendered") {
                var value = ""; scenario.onActivity { activity -> value = activity.findViewById<View>(android.R.id.content).findViewWithTag<TextView>("player-status").text.toString() }
                value.contains("Battery") && Regex("Remaining [0-9]+:[0-9]{2}").containsMatchIn(value) && value.contains(":")
            }
            scenario.onActivity { activity -> DevicePlayback.descendants(activity.findViewById(android.R.id.content)).filterIsInstance<PlayerView>().single().hideController() }
            onView(isAssignableFrom(PlayerView::class.java)).perform(GeneralClickAction(Tap.DOUBLE, { view ->
                val location = IntArray(2); view.getLocationOnScreen(location)
                floatArrayOf(location[0] + view.width * .2f, location[1] + view.height * .3f)
            }, Press.FINGER))
            DevicePlayback.await("Double tap did not pause playback") { DevicePlayback.main { !controller.isPlaying } }
            var pauseIndicator = false
            scenario.onActivity { activity -> pauseIndicator = DevicePlayback.descendants(activity.findViewById(android.R.id.content)).filterIsInstance<TextView>().any { it.text.toString() == "Pause" } }
            assertTrue("Gesture confirmation was not shown", pauseIndicator)
        } finally {
            scenario.close(); DevicePlayback.stop(controller)
            settings.showPlayerClock = original[0]; settings.showPlayerBattery = original[1]; settings.showRemainingTime = original[2]; settings.doubleTapPlayPause = original[3]; settings.gestures = original[4]
        }
    }
}
