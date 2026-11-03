package com.tplayer.mediaplayer.ui

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.accessibility.AccessibilityManager
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.tplayer.mediaplayer.data.Settings
import kotlin.math.*
import com.tplayer.mediaplayer.ui.GestureThresholds as T

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerGestures(private val activity: Activity, private val root: PlayerGestureFrame, private val view: PlayerView,
    private val overlay: TextView, private val player: () -> Player?, private val enabled: () -> Boolean,
    private val locked: () -> Boolean, private val unlockView: () -> View?, private val unlock: () -> Unit,
    private val subtitleMode: () -> Boolean, private val captionMove: (Int) -> Unit, private val captionSize: (Int) -> Unit,
    private val captionPosition: () -> Int, private val captionTextSize: () -> Int,
    private val persistCaptions: () -> Unit, private val flips: () -> Pair<Boolean, Boolean>) {
    private val settings = Settings(activity)
    private val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val accessibility = activity.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    private val handler = Handler(Looper.getMainLooper())
    private val density = activity.resources.displayMetrics.density
    private val held = HeldSpeedState()
    private var heldPlayer: Player? = null
    private var captured = false; private var mode = ""; private var moved = false; private var ignored = false
    private var x0 = 0f; private var y0 = 0f; private var downAt = 0L
    private var span0 = 1f; private var zoom = 1f; private var zoom0 = 1f; private var panX0 = 0f; private var panY0 = 0f
    private var volume0 = 0; private var brightness0 = .5f; private var position0 = 0L; private var target = 0L; private var speed0 = 1f
    private var captionPosition0 = 8; private var captionSize0 = 100; private var action = GestureAction.NONE
    private var double = false; private var lastTapAt = -1L; private var lastTapX = 0f; private var lastTapY = 0f
    private var multiAt = -1L; private var multiX = 0f; private var multiY = 0f
    private val hide = Runnable { overlay.visibility = View.GONE }
    private val single = Runnable { perform(settings.gestureAction(GestureSlot.SINGLE_TAP), 1) }
    private val hold = Runnable {
        if (!captured || moved || ignored) return@Runnable
        if (locked()) { if (mode == "unlock") { unlock(); captured = true }; ignored = true; return@Runnable }
        val p = player() ?: return@Runnable
        if (p.isPlaying && p.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)) {
            heldPlayer = p; p.setPlaybackSpeed(held.begin(p, p.playbackParameters.speed)); mode = "hold"
            say("2× · release to restore speed", false)
        }
    }
    init { root.gestures = ::touch }
    fun say(text: String, temporary: Boolean = true) {
        handler.removeCallbacks(hide); overlay.text = text; overlay.visibility = View.VISIBLE
        if (temporary) handler.postDelayed(hide, T.OVERLAY_MS)
    }
    private fun hit(view: View, x: Float, y: Float): Boolean {
        val a = IntArray(2); val b = IntArray(2); view.getLocationOnScreen(a); root.getLocationOnScreen(b)
        return Rect(a[0] - b[0], a[1] - b[1], a[0] - b[0] + view.width, a[1] - b[1] + view.height).contains(x.toInt(), y.toInt())
    }
    private fun interactive(view: View, x: Float, y: Float): Boolean {
        if (view.visibility != View.VISIBLE || !hit(view, x, y)) return false
        if (view is ViewGroup) for (i in view.childCount - 1 downTo 0) if (interactive(view.getChildAt(i), x, y)) return true
        return view !== root && view !== this.view && (view.isClickable || view.isLongClickable || view is androidx.media3.ui.TimeBar)
    }
    private fun safe(x: Float, y: Float): Boolean {
        val insets = ViewCompat.getRootWindowInsets(root)
        val navigation = insets?.getInsets(WindowInsetsCompat.Type.systemGestures() or WindowInsetsCompat.Type.systemBars())
        return GesturePolicy.safeStart(x, y, root.width, root.height,
            max(T.EDGE_GUARD_DP * density, (navigation?.left ?: 0).toFloat()),
            max(T.EDGE_GUARD_DP * density, (navigation?.top ?: 0).toFloat()),
            max(T.EDGE_GUARD_DP * density, (navigation?.right ?: 0).toFloat()),
            max(T.EDGE_GUARD_DP * density, (navigation?.bottom ?: 0).toFloat()))
    }
    private fun start(x: Float, y: Float) {
        x0 = x; y0 = y; mode = ""; moved = false
        val p = player(); position0 = p?.currentPosition ?: 0; target = position0; speed0 = p?.playbackParameters?.speed ?: 1f
        volume0 = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        brightness0 = activity.window.attributes.screenBrightness.takeIf { it >= 0 }
            ?: (android.provider.Settings.System.getInt(activity.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
        zoom0 = zoom; panX0 = view.videoSurfaceView?.translationX ?: 0f; panY0 = view.videoSurfaceView?.translationY ?: 0f
        captionPosition0 = captionPosition(); captionSize0 = captionTextSize()
    }
    private fun restoreHeld() {
        heldPlayer?.let { p -> held.release(p)?.let { old -> if (p.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)) p.setPlaybackSpeed(old) } }
        heldPlayer = null
    }
    fun cancel() {
        handler.removeCallbacks(hold); handler.removeCallbacks(single); handler.removeCallbacks(hide)
        restoreHeld(); captured = false; ignored = false; lastTapAt = -1; multiAt = -1
        root.parent?.requestDisallowInterceptTouchEvent(false); overlay.visibility = View.GONE
    }
    private fun touch(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            handler.removeCallbacks(hold)
            if (locked()) {
                cancel(); captured = true; downAt = event.eventTime; x0 = event.x; y0 = event.y
                mode = if (unlockView()?.let { hit(it, x0, y0) } == true) "unlock" else "locked"
                if (mode == "unlock") handler.postDelayed(hold, T.HOLD_MS)
                return true
            }
            if (!enabled() || accessibility.isTouchExplorationEnabled || !safe(event.x, event.y) || interactive(root, event.x, event.y)) return false
            captured = true; ignored = false; start(event.x, event.y); downAt = event.eventTime
            double = lastTapAt >= 0 && GesturePolicy.doubleTap(lastTapX, lastTapY, event.x, event.y, event.eventTime - lastTapAt, density, root.width)
            if (double) { handler.removeCallbacks(single); lastTapAt = -1 }
            handler.postDelayed(hold, T.HOLD_MS)
            root.parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        if (!captured) return false
        if (locked()) {
            if (event.actionMasked == MotionEvent.ACTION_MOVE && hypot(event.x - x0, event.y - y0) > T.TAP_SLOP_DP * density) { moved = true; handler.removeCallbacks(hold) }
            if (event.actionMasked in intArrayOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN)) { handler.removeCallbacks(hold); captured = event.actionMasked == MotionEvent.ACTION_POINTER_DOWN; ignored = true }
            return true
        }
        if (!enabled() || accessibility.isTouchExplorationEnabled) { cancel(); return true }
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                handler.removeCallbacks(hold); handler.removeCallbacks(single); lastTapAt = -1; double = false; restoreHeld()
                if (event.pointerCount == 2) {
                    start((event.getX(0) + event.getX(1)) / 2, (event.getY(0) + event.getY(1)) / 2)
                    span0 = hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0)).coerceAtLeast(T.MIN_SPAN_PX)
                    mode = "two"; ignored = false
                } else ignored = true
            }
            MotionEvent.ACTION_MOVE -> if (!ignored) {
                val two = event.pointerCount == 2
                val x = if (two) (event.getX(0) + event.getX(1)) / 2 else event.x
                val y = if (two) (event.getY(0) + event.getY(1)) / 2 else event.y
                val dx = x - x0; val dy = y - y0
                val ratio = if (two) hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0)) / span0 else 1f
                if (mode == "hold") return true
                if (two && abs(ratio - 1) > T.PINCH_SLOP) {
                    mode = if (subtitleMode()) "caption-size" else "zoom"; moved = true; handler.removeCallbacks(hold)
                }
                if (max(abs(dx), abs(dy)) > T.DRAG_SLOP_DP * density) {
                    moved = true; handler.removeCallbacks(hold); handler.removeCallbacks(single); lastTapAt = -1
                    if (mode == "" || mode == "two") {
                        mode = when {
                            subtitleMode() && abs(dy) >= abs(dx) -> "caption-position"
                            zoom0 > T.PAN_ZOOM_THRESHOLD && !subtitleMode() -> "pan"
                            abs(dx) > abs(dy) -> "seek"
                            else -> "assigned"
                        }
                        action = settings.gestureAction(if (x0 < root.width / 2) GestureSlot.VERTICAL_LEFT else GestureSlot.VERTICAL_RIGHT)
                        if (mode == "assigned" && action in setOf(GestureAction.PLAY_PAUSE, GestureAction.CONTROLS)) perform(action, if (dy < 0) 1 else -1)
                    }
                }
                when (mode) {
                    "zoom", "pan" -> {
                        zoom = GesturePolicy.zoom(zoom0 * ratio)
                        view.videoSurfaceView?.apply {
                            scaleX = zoom * if (flips().first) -1 else 1; scaleY = zoom * if (flips().second) -1 else 1
                            translationX = GesturePolicy.pan(panX0 + dx, zoom, width); translationY = GesturePolicy.pan(panY0 + dy, zoom, height)
                        }; say("Zoom ${"%.1f".format(zoom)}×")
                    }
                    "caption-position" -> { captionMove((captionPosition0 - dy / root.height.coerceAtLeast(1) * T.SUBTITLE_MAX_POSITION).roundToInt().coerceIn(0, T.SUBTITLE_MAX_POSITION)); say("Subtitle position ${captionPosition()}%") }
                    "caption-size" -> { captionSize((captionSize0 * ratio).roundToInt().coerceIn(T.SUBTITLE_MIN_SCALE, T.SUBTITLE_MAX_SCALE)); say("Subtitle size ${captionTextSize()}%") }
                    "seek" -> preview((dx / root.width.coerceAtLeast(1) * T.SEEK_SPAN_MS).toLong())
                    "assigned" -> when (action) {
                        GestureAction.SEEK, GestureAction.SEEK_BACK, GestureAction.SEEK_FORWARD -> preview((-dy / root.height.coerceAtLeast(1) * T.SEEK_SPAN_MS).toLong().let { if (action == GestureAction.SEEK_BACK) -abs(it) else if (action == GestureAction.SEEK_FORWARD) abs(it) else it })
                        GestureAction.VOLUME -> volume(volume0.toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) - dy / root.height.coerceAtLeast(1) * T.LEVEL_SWIPE_RANGE)
                        GestureAction.BRIGHTNESS -> brightness(brightness0 - dy / root.height.coerceAtLeast(1) * T.LEVEL_SWIPE_RANGE)
                        GestureAction.SPEED -> { val speed = GesturePolicy.speed(speed0 + (-dy / (T.SPEED_STEP_DP * density)).toInt() * T.SPEED_STEP); player()?.setPlaybackSpeed(speed); say("Speed ${speed}×") }
                        else -> Unit
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (!ignored && event.pointerCount == 2 && !moved && event.eventTime - downAt <= T.TAP_MAX_MS) {
                    if (multiAt >= 0 && event.eventTime - multiAt <= T.DOUBLE_TAP_MS && hypot(x0 - multiX, y0 - multiY) <= T.DOUBLE_TAP_DISTANCE_DP * density) { resetZoom(); multiAt = -1 }
                    else { multiAt = event.eventTime; multiX = x0; multiY = y0 }
                }
                finishDrag(false); ignored = true; handler.removeCallbacks(hold)
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(hold)
                if (held.active) { restoreHeld(); say("Speed restored") }
                else if (!ignored && !moved && mode != "hold" && event.eventTime - downAt <= T.TAP_MAX_MS) {
                    if (double) perform(settings.gestureAction(if (event.x < root.width / 2) GestureSlot.DOUBLE_LEFT else GestureSlot.DOUBLE_RIGHT), if (event.x < root.width / 2) -1 else 1)
                    else { lastTapAt = event.eventTime; lastTapX = event.x; lastTapY = event.y; handler.postDelayed(single, T.DOUBLE_TAP_MS) }
                }
                finishDrag(false); captured = false; ignored = false; root.parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> { finishDrag(true); cancel() }
        }
        return true
    }
    private fun preview(delta: Long) {
        val p = player() ?: return
        if (!p.isCurrentMediaItemSeekable) { say("This source cannot seek"); return }
        target = GesturePolicy.seek(position0, delta, p.duration)
        say("Seek ${target / 60000}:${(target / 1000 % 60).toString().padStart(2, '0')} · release to seek", false)
    }
    private fun finishDrag(cancelled: Boolean) {
        if (cancelled) return
        if ((mode == "seek" || mode == "assigned" && action in setOf(GestureAction.SEEK, GestureAction.SEEK_BACK, GestureAction.SEEK_FORWARD)) && player()?.isCurrentMediaItemSeekable == true && moved) player()?.seekTo(target)
        if (mode in setOf("caption-position", "caption-size")) persistCaptions()
        handler.removeCallbacks(hide); handler.postDelayed(hide, T.OVERLAY_MS)
        mode = ""
    }
    private fun volume(fraction: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val level = (fraction.coerceIn(0f, 1f) * max).roundToInt()
        try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0); say("Volume ${level * 100 / max}%") }
        catch (_: SecurityException) { say("Use device volume buttons") }
    }
    private fun brightness(fraction: Float) {
        val level = fraction.coerceIn(T.BRIGHTNESS_MIN, 1f)
        activity.window.attributes = activity.window.attributes.apply { screenBrightness = level }; say("Brightness ${(level * 100).roundToInt()}%")
    }
    private fun perform(action: GestureAction, direction: Int) {
        if (locked() || !enabled()) return
        val p = player() ?: return
        when (action) {
            GestureAction.CONTROLS -> if (view.isControllerFullyVisible) view.hideController() else view.showController()
            GestureAction.PLAY_PAUSE -> { if (p.playWhenReady) p.pause() else { if (p.playbackState == Player.STATE_ENDED) p.seekToDefaultPosition(); if (p.playbackState == Player.STATE_IDLE) p.prepare(); p.play() }; say(if (p.playWhenReady) "Play" else "Pause") }
            GestureAction.SEEK, GestureAction.SEEK_BACK, GestureAction.SEEK_FORWARD -> if (p.isCurrentMediaItemSeekable) {
                val sign = if (action == GestureAction.SEEK_BACK) -1 else if (action == GestureAction.SEEK_FORWARD) 1 else direction
                p.seekTo(GesturePolicy.seek(p.currentPosition, sign * T.TAP_SEEK_MS, p.duration)); say(if (sign < 0) "−10 s" else "+10 s")
            } else say("This source cannot seek")
            GestureAction.VOLUME -> volume(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) + direction * T.LEVEL_TAP_STEP)
            GestureAction.BRIGHTNESS -> brightness((activity.window.attributes.screenBrightness.takeIf { it >= 0 } ?: brightness0) + direction * T.LEVEL_TAP_STEP)
            GestureAction.SPEED -> { val speed = GesturePolicy.speed(p.playbackParameters.speed + direction * T.SPEED_STEP); p.setPlaybackSpeed(speed); say("Speed ${speed}×") }
            GestureAction.NONE -> Unit
        }
    }
    fun refreshTransform() { view.videoSurfaceView?.apply { scaleX = zoom * if (flips().first) -1 else 1; scaleY = zoom * if (flips().second) -1 else 1 } }
    fun resetZoom() { zoom = T.ZOOM_MIN; refreshTransform(); view.videoSurfaceView?.apply { translationX = 0f; translationY = 0f }; say("Zoom reset · 1×") }
    fun release() { cancel(); root.gestures = null }
}
