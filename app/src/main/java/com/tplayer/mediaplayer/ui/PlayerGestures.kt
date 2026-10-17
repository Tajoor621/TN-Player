package com.tplayer.mediaplayer.ui

import android.app.Activity
import android.media.AudioManager
import android.content.Context
import android.view.*
import android.view.accessibility.AccessibilityManager
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.ui.PlayerView
import kotlin.math.*

/** Two-finger swipes avoid taking over normal one-finger controller gestures. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerGestures(private val activity: Activity, private val view: PlayerView,
    private val overlay: TextView, private val player: () -> Player?, private val enabled: () -> Boolean,
    private val seekMs: () -> Long, private val flips: () -> Pair<Boolean, Boolean> = { false to false },
    private val subtitleMode: () -> Boolean = { false }, private val captionJump: (Int) -> Unit = {},
    private val captionMove: (Int) -> Unit = {}, private val captionSize: (Int) -> Unit = {}) {
    private val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val accessibility = activity.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    private var tracking = false; private var mode = ""
    private var startX = 0f; private var startY = 0f; private var distance = 1f
    private var startPosition = 0L; private var target = 0L; private var startVolume = 0
    private var startBrightness = .5f; private var zoom = 1f; private var startZoom = 1f
    private var panX = 0f; private var panY = 0f
    private var captionDelta = 0; private var captionSteps = 0; private var captionDirection = 0
    private val hide = Runnable { overlay.visibility = View.GONE }
    private fun say(message: String) { overlay.removeCallbacks(hide); overlay.text = message; overlay.visibility = View.VISIBLE; overlay.postDelayed(hide, 1000) }
    private val taps = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(event: android.view.MotionEvent): Boolean {
            val media = player() ?: return false
            if (!media.isCurrentMediaItemSeekable) return false
            val delta = if (event.x < view.width / 2) -seekMs() else seekMs()
            media.seekTo((media.currentPosition + delta).coerceAtLeast(0)); say("${if (delta < 0) "−" else "+"}${seekMs() / 1000}s")
            return true
        }
    })
    init {
        @Suppress("ClickableViewAccessibility")
        view.setOnTouchListener { _, event ->
            if (!enabled() || accessibility.isTouchExplorationEnabled) return@setOnTouchListener false
            if (event.pointerCount == 1 && !tracking) return@setOnTouchListener taps.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN && event.pointerCount == 2) {
                tracking = true; mode = ""; captionDelta = 0; captionSteps = 0; captionDirection = 0; startX = (event.getX(0) + event.getX(1)) / 2; startY = (event.getY(0) + event.getY(1)) / 2
                distance = hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0)).coerceAtLeast(1f)
                startPosition = player()?.currentPosition ?: 0; target = startPosition
                startVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                startBrightness = activity.window.attributes.screenBrightness.takeIf { it >= 0 } ?: (android.provider.Settings.System.getInt(activity.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
                startZoom = zoom; panX = view.videoSurfaceView?.translationX ?: 0f; panY = view.videoSurfaceView?.translationY ?: 0f
                view.parent?.requestDisallowInterceptTouchEvent(true); return@setOnTouchListener true
            }
            if (!tracking) return@setOnTouchListener false
            if (event.actionMasked == MotionEvent.ACTION_MOVE && event.pointerCount == 2) {
                val x = (event.getX(0) + event.getX(1)) / 2; val y = (event.getY(0) + event.getY(1)) / 2
                val dx = x - startX; val dy = y - startY
                val ratio = hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0)) / distance
                if (subtitleMode()) {
                    if (mode.isEmpty() && abs(ratio - 1) > .12f) mode = "caption-size"
                    if (mode.isEmpty() && max(abs(dx), abs(dy)) > activity.dp(20)) mode = if (abs(dx) > abs(dy)) "caption-jump" else "caption-position"
                    when (mode) {
                        "caption-size" -> {
                            val steps = (ln(ratio.coerceAtLeast(.01f)) / ln(1.3f)).toInt().coerceIn(-2, 2)
                            if (steps != captionSteps) { captionSize(steps - captionSteps); captionSteps = steps; say("Subtitle text size") }
                        }
                        "caption-position" -> {
                            val delta = (-dy / view.height.coerceAtLeast(1) * 60).roundToInt()
                            if (delta != captionDelta) { captionMove(delta - captionDelta); captionDelta = delta; say("Subtitle position") }
                        }
                        "caption-jump" -> { captionDirection = if (dx < 0) 1 else -1; say(if (captionDirection > 0) "Next caption" else "Previous caption") }
                    }
                    return@setOnTouchListener true
                }
                if (abs(ratio - 1) > .12f) mode = "zoom"
                if (mode.isEmpty() && max(abs(dx), abs(dy)) > activity.dp(20)) mode = if (startZoom > 1.05f) "pan" else if (abs(dx) > abs(dy)) "seek" else if (startX < view.width / 2) "volume" else "brightness"
                when (mode) {
                    "zoom", "pan" -> {
                        zoom = (startZoom * ratio).coerceIn(1f, 3f)
                        view.videoSurfaceView?.apply {
                            scaleX = zoom * if (flips().first) -1f else 1f; scaleY = zoom * if (flips().second) -1f else 1f
                            translationX = (panX + dx).coerceIn(-(zoom - 1) * width / 2, (zoom - 1) * width / 2)
                            translationY = (panY + dy).coerceIn(-(zoom - 1) * height / 2, (zoom - 1) * height / 2)
                        }
                        say("Zoom ${"%.1f".format(zoom)}×")
                    }
                    "seek" -> {
                        val media = player()
                        if (media?.isCurrentMediaItemSeekable == true) {
                            val duration = media.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: Long.MAX_VALUE
                            target = (startPosition + (dx / view.width.coerceAtLeast(1) * 120000).toLong()).coerceIn(0, duration)
                            say("Seek ${target / 60000}:${((target / 1000) % 60).toString().padStart(2, '0')}")
                        } else say("This source cannot seek")
                    }
                    "volume" -> {
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        val value = (startVolume - dy / view.height.coerceAtLeast(1) * max * 2).roundToInt().coerceIn(0, max)
                        try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0); say("Volume ${value * 100 / max}%") } catch (_: SecurityException) { say("Use device volume buttons") }
                    }
                    "brightness" -> {
                        val value = (startBrightness - dy / view.height.coerceAtLeast(1) * 2).coerceIn(.05f, 1f)
                        activity.window.attributes = activity.window.attributes.apply { screenBrightness = value }
                        say("Brightness ${(value * 100).roundToInt()}%")
                    }
                }
            }
            if (event.actionMasked in setOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL)) {
                if (event.actionMasked != MotionEvent.ACTION_CANCEL && mode == "caption-jump" && captionDirection != 0) captionJump(captionDirection)
                if (event.actionMasked != MotionEvent.ACTION_CANCEL && mode == "seek" && player()?.isCurrentMediaItemSeekable == true) player()?.seekTo(target)
                tracking = false; view.parent?.requestDisallowInterceptTouchEvent(false)
            }
            true
        }
    }
    fun refreshTransform() { view.videoSurfaceView?.apply { scaleX = zoom * if (flips().first) -1f else 1f; scaleY = zoom * if (flips().second) -1f else 1f } }
    fun resetZoom() { zoom = 1f; view.videoSurfaceView?.apply { scaleX = if (flips().first) -1f else 1f; scaleY = if (flips().second) -1f else 1f; translationX = 0f; translationY = 0f }; say("Zoom 1×") }
}
