@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer

import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.media3.session.SessionCommand
import androidx.media3.ui.PlayerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tplayer.mediaplayer.data.Settings
import com.tplayer.mediaplayer.ui.*
import com.tplayer.mediaplayer.subtitle.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class GestureDeviceTest {
    @Test fun doubleTapSeeksTenSecondsOnTheProductionPlayer() = exercise(false)
    @Test fun captureGestureGalleryOnAPreinstalledBuild() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("capture_gestures") == "true")
        exercise(true)
    }
    private fun exercise(capture: Boolean) {
        val settings = Settings(DevicePlayback.context)
        val previous = settings.backupValues()
        settings.gestures = true; settings.autoPip = false
        GestureSlot.values().forEach { settings.setGestureAction(it, GesturePolicy.default(it)) }
        val uri = DevicePlayback.fixture("video-fixture.mp4")
        if (capture) runBlocking {
            SubtitlePreferenceRepository.get(DevicePlayback.context).save(uri.toString(), SubtitlePreferences()).await().getOrThrow()
            CaptionRepository.get(DevicePlayback.context).save(ExternalCaptions(uri.toString(), "Gesture test captions", SubtitleDocument(listOf(SubtitleCue(0, 90000, "TN-Player gesture evidence")))))
        }
        val scenario = DevicePlayback.launch(uri, "video/mp4", "Gesture fixture")
        val controller = DevicePlayback.controller()
        var root: PlayerGestureFrame? = null
        fun surface() = scenario.onActivity { activity ->
            root = DevicePlayback.descendants(activity.findViewById(android.R.id.content)).filterIsInstance<PlayerGestureFrame>().first()
            DevicePlayback.descendants(root!!).filterIsInstance<PlayerView>().first().hideController()
        }
        fun point(x: Float, y: Float): Pair<Float, Float> = x * root!!.width to y * root!!.height
        var downAt = 0L
        fun event(action: Int, x: Float, y: Float) = DevicePlayback.main {
            val p = point(x, y); val now = SystemClock.uptimeMillis()
            if (action == MotionEvent.ACTION_DOWN) downAt = now
            val event = MotionEvent.obtain(downAt, now, action, p.first, p.second, 0)
            try { root!!.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        fun tap(x: Float, y: Float) { event(MotionEvent.ACTION_DOWN, x, y); Thread.sleep(35); event(MotionEvent.ACTION_UP, x, y) }
        fun doubleTap(x: Float, y: Float) { tap(x, y); Thread.sleep(80); tap(x, y) }
        fun save(name: String) {
            if (!capture) return
            Thread.sleep(120)
            val bitmap = DevicePlayback.instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
            val file = File(DevicePlayback.context.getExternalFilesDir(null), "gesture-evidence/$name.png"); file.parentFile!!.mkdirs()
            try { file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } } finally { bitmap.recycle() }
        }
        fun drag(x: Float, y: Float, endX: Float, endY: Float, name: String) {
            event(MotionEvent.ACTION_DOWN, x, y); Thread.sleep(50); event(MotionEvent.ACTION_MOVE, endX, endY); save(name); event(MotionEvent.ACTION_UP, endX, endY)
        }
        fun pair(action: Int, spread: Float, centerY: Float = .5f) = DevicePlayback.main {
            val coords = arrayOf(MotionEvent.PointerCoords(), MotionEvent.PointerCoords())
            val props = arrayOf(MotionEvent.PointerProperties(), MotionEvent.PointerProperties())
            for (i in 0..1) { props[i].id = i; props[i].toolType = MotionEvent.TOOL_TYPE_FINGER; coords[i].x = root!!.width * (.5f + if (i == 0) -spread else spread); coords[i].y = root!!.height * centerY; coords[i].pressure = 1f; coords[i].size = 1f }
            val now = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(downAt, now, action, 2, props, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { root!!.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        fun pinch(name: String) {
            event(MotionEvent.ACTION_DOWN, .4f, .5f); pair(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), .1f)
            pair(MotionEvent.ACTION_MOVE, .2f); save(name)
            pair(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), .2f); event(MotionEvent.ACTION_UP, .3f, .5f)
        }
        fun twoTap() {
            event(MotionEvent.ACTION_DOWN, .4f, .5f); pair(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), .1f)
            pair(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), .1f); event(MotionEvent.ACTION_UP, .4f, .5f)
        }
        try {
            val expected = InstrumentationRegistry.getArguments().getString("expected_revision")
            if (expected != null) {
                val result = DevicePlayback.main { controller.sendCustomCommand(SessionCommand("PLAYBACK_DIAGNOSTICS", Bundle.EMPTY), Bundle.EMPTY) }.get(10, TimeUnit.SECONDS)
                assertEquals("Installed APK must match source", expected, result.extras.getString("source_revision"))
            }
            DevicePlayback.await("Fixture did not start") { DevicePlayback.main { controller.isPlaying && controller.isCurrentMediaItemSeekable } }
            DevicePlayback.main { controller.pause(); controller.seekTo(20000) }
            DevicePlayback.await("Fixture seek failed") { DevicePlayback.main { controller.currentPosition in 19500..20500 } }
            surface(); doubleTap(.75f, .45f)
            DevicePlayback.await("Right double-tap did not seek 10 s") { DevicePlayback.main { controller.currentPosition in 29500..30500 } }; save("01-double-tap-right")
            doubleTap(.25f, .45f)
            DevicePlayback.await("Left double-tap did not seek 10 s") { DevicePlayback.main { controller.currentPosition in 19500..20500 } }; save("02-double-tap-left")
            if (capture) {
                tap(.5f, .45f); Thread.sleep(350); save("03-single-tap-controls"); surface()
                drag(.25f, .6f, .25f, .4f, "04-brightness")
                drag(.75f, .6f, .75f, .4f, "05-volume")
                drag(.4f, .45f, .6f, .45f, "06-seek-preview")
                DevicePlayback.main { controller.setPlaybackSpeed(1.25f); controller.play() }
                DevicePlayback.await("Playback did not resume") { DevicePlayback.main { controller.isPlaying } }
                event(MotionEvent.ACTION_DOWN, .5f, .45f); Thread.sleep(700)
                DevicePlayback.await("Hold did not enable 2×") { DevicePlayback.main { controller.playbackParameters.speed == 2f } }; save("07-held-speed")
                event(MotionEvent.ACTION_UP, .5f, .45f)
                DevicePlayback.await("Hold speed was not restored") { DevicePlayback.main { controller.playbackParameters.speed == 1.25f } }
                DevicePlayback.main { controller.pause() }
                pinch("08-pinch-zoom"); drag(.5f, .5f, .55f, .4f, "09-pan")
                twoTap(); Thread.sleep(60); twoTap(); save("10-zoom-reset")
                scenario.onActivity { activity -> DevicePlayback.descendants(activity.findViewById(android.R.id.content)).filterIsInstance<PlayerView>().first().showController() }
                onView(withText("More")).perform(click()); onView(withText("Subtitle")).perform(click())
                onView(withText("Subtitle gestures: off")).perform(click()); onView(withText("OK")).perform(click()); surface()
                drag(.5f, .6f, .5f, .4f, "11-subtitle-position"); pinch("12-subtitle-size")
                scenario.onActivity { activity -> DevicePlayback.descendants(activity.findViewById(android.R.id.content)).filterIsInstance<PlayerView>().first().showController() }
                onView(withText("More")).perform(click()); onView(withText("Subtitle")).perform(click()); onView(withText("Subtitle timing")).perform(click())
                onView(withText("+0.1 s · Later")).perform(click()); save("13-subtitle-sync"); onView(withText("Done")).perform(click())
                scenario.onActivity { activity -> DevicePlayback.descendants(activity.findViewById(android.R.id.content)).filterIsInstance<PlayerView>().first().showController() }
                onView(withText("Lock")).perform(click()); save("14-screen-lock")
                val before = DevicePlayback.main { controller.currentPosition }
                doubleTap(.75f, .45f); assertEquals(before, DevicePlayback.main { controller.currentPosition })
                var unlock: View? = null
                scenario.onActivity { activity -> unlock = activity.findViewById<View>(android.R.id.content).findViewWithTag("hold-unlock") }
                val location = DevicePlayback.main { val a = IntArray(2); val b = IntArray(2); unlock!!.getLocationOnScreen(a); root!!.getLocationOnScreen(b); (a[0] - b[0] + unlock!!.width / 2f) / root!!.width to (a[1] - b[1] + unlock!!.height / 2f) / root!!.height }
                event(MotionEvent.ACTION_DOWN, location.first, location.second); Thread.sleep(700); event(MotionEvent.ACTION_UP, location.first, location.second); save("15-unlocked")
            }
        } finally {
            DevicePlayback.stop(controller); scenario.close(); settings.restoreValues(previous)
            if (capture) runBlocking { CaptionRepository.get(DevicePlayback.context).remove(uri.toString()) }
        }
    }
}
