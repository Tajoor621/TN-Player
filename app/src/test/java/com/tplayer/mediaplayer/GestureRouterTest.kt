@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.tplayer.mediaplayer.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class GestureRouterTest {
    @Test fun lockConsumesSurfaceAndChildTouchesAndRequiresAHeldUnlock() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = PlayerGestureFrame(activity); val video = PlayerView(activity)
        val overlay = TextView(activity).apply { visibility = View.GONE }; var clicks = 0; var locked = true
        val unlock = Button(activity).apply { text = "Unlock"; setOnClickListener { clicks++ } }
        root.addView(video, FrameLayout.LayoutParams(-1, -1)); root.addView(overlay)
        root.addView(unlock, FrameLayout.LayoutParams(140, 60).apply { leftMargin = 700; topMargin = 400 })
        activity.setContentView(root); root.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)); root.layout(0, 0, 1000, 600)
        val gestures = PlayerGestures(activity, root, video, overlay, { null }, { true }, { locked }, { unlock }, { locked = false }, { false }, {}, {}, { 8 }, { 100 }, {}, { false to false })
        fun event(action: Int, x: Float, y: Float) {
            val now = SystemClock.uptimeMillis(); val event = MotionEvent.obtain(now, now, action, x, y, 0)
            root.dispatchTouchEvent(event); event.recycle()
        }
        try {
            event(MotionEvent.ACTION_DOWN, 740f, 420f); event(MotionEvent.ACTION_UP, 740f, 420f)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
            assertTrue(locked); assertEquals(0, clicks)
            event(MotionEvent.ACTION_DOWN, 400f, 300f)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
            event(MotionEvent.ACTION_UP, 400f, 300f); assertTrue(locked)
            event(MotionEvent.ACTION_DOWN, 740f, 420f)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
            event(MotionEvent.ACTION_UP, 740f, 420f)
            assertFalse(locked); assertEquals(0, clicks)
        } finally { gestures.release(); activity.finish() }
    }
    @Test fun heldSpeedRestoresOnCancellationBeforeControllerIsReleased() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = PlayerGestureFrame(activity); val video = PlayerView(activity); val overlay = TextView(activity).apply { visibility = View.GONE }
        root.addView(video, FrameLayout.LayoutParams(-1, -1)); root.addView(overlay)
        activity.setContentView(root); root.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)); root.layout(0, 0, 1000, 600); video.hideController()
        var speed = 1.25f
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "isPlaying", "isCommandAvailable" -> true
                "getPlaybackParameters" -> PlaybackParameters(speed)
                "setPlaybackSpeed" -> { speed = args!![0] as Float; null }
                "getCurrentPosition" -> 10000L
                else -> when (method.returnType) { java.lang.Boolean.TYPE -> false; java.lang.Integer.TYPE -> 0; java.lang.Long.TYPE -> 0L; java.lang.Float.TYPE -> 0f; else -> null }
            }
        } as Player
        val gestures = PlayerGestures(activity, root, video, overlay, { player }, { true }, { false }, { null }, {}, { false }, {}, {}, { 8 }, { 100 }, {}, { false to false })
        try {
            val now = SystemClock.uptimeMillis(); val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 700f, 300f, 0)
            assertTrue(root.dispatchTouchEvent(down)); down.recycle()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
            assertEquals(2f, speed, .001f)
            gestures.cancel(); assertEquals(1.25f, speed, .001f)
        } finally { gestures.release(); activity.finish() }
    }
    @Test fun subtitleModeRoutesPinchAndSwipeToCaptionsRatherThanVideoOrBrightness() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = PlayerGestureFrame(activity); val video = PlayerView(activity)
        val overlay = TextView(activity).apply { visibility = View.GONE }
        root.addView(video, FrameLayout.LayoutParams(-1, -1)); root.addView(overlay)
        activity.setContentView(root); root.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)); root.layout(0, 0, 1000, 600); video.hideController()
        var position = 8; var captionScale = 100; var saves = 0
        val originalBrightness = activity.window.attributes.screenBrightness
        val gestures = PlayerGestures(activity, root, video, overlay, { null }, { true }, { false }, { null }, {},
            { true }, { position = it }, { captionScale = it }, { position }, { captionScale }, { saves++ }, { false to false })
        fun single(action: Int, x: Float, y: Float) {
            val now = SystemClock.uptimeMillis(); val event = MotionEvent.obtain(now, now, action, x, y, 0)
            root.dispatchTouchEvent(event); event.recycle()
        }
        fun pair(action: Int, spread: Float) {
            val now = SystemClock.uptimeMillis()
            val properties = Array(2) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
            val coordinates = Array(2) { i -> MotionEvent.PointerCoords().apply { x = 500f + if (i == 0) -spread else spread; y = 300f; pressure = 1f; size = 1f } }
            val event = MotionEvent.obtain(now, now, action, 2, properties, coordinates, 0, 0, 1f, 1f, 0, 0, 0, 0)
            root.dispatchTouchEvent(event); event.recycle()
        }
        try {
            single(MotionEvent.ACTION_DOWN, 400f, 300f)
            pair(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 100f)
            pair(MotionEvent.ACTION_MOVE, 200f)
            pair(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 200f)
            single(MotionEvent.ACTION_UP, 300f, 300f)
            assertEquals(200, captionScale); assertEquals(1f, video.videoSurfaceView!!.scaleX, .001f)
            single(MotionEvent.ACTION_DOWN, 250f, 350f); single(MotionEvent.ACTION_MOVE, 250f, 250f); single(MotionEvent.ACTION_UP, 250f, 250f)
            assertTrue(position > 8); assertEquals(2, saves)
            assertEquals(originalBrightness, activity.window.attributes.screenBrightness, .001f)
        } finally { gestures.release(); activity.finish() }
    }

}
