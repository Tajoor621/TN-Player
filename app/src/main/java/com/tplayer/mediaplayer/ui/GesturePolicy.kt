package com.tplayer.mediaplayer.ui

import kotlin.math.*

/** All app gesture thresholds, ranges and step sizes live here (distances are dp unless named otherwise). */
object GestureThresholds {
    const val DRAG_SLOP_DP = 16f
    const val TAP_SLOP_DP = 24f
    const val EDGE_GUARD_DP = 24f
    const val DOUBLE_TAP_DISTANCE_DP = 64f
    const val DOUBLE_TAP_MS = 300L
    const val TAP_MAX_MS = 300L
    const val HOLD_MS = 550L
    const val OVERLAY_MS = 1200L
    const val PINCH_SLOP = .08f
    const val MIN_SPAN_PX = 1f
    const val ZOOM_MIN = 1f
    const val ZOOM_MAX = 3f
    const val PAN_ZOOM_THRESHOLD = 1.02f
    const val SEEK_SPAN_MS = 120000L
    const val TAP_SEEK_MS = 10000L
    const val HOLD_SPEED = 2f
    const val SPEED_MIN = .25f
    const val SPEED_MAX = 4f
    const val SPEED_STEP = .25f
    const val SPEED_STEP_DP = 48f
    const val LEVEL_SWIPE_RANGE = 2f
    const val LEVEL_TAP_STEP = .05f
    const val BRIGHTNESS_MIN = .05f
    const val SUBTITLE_MIN_SCALE = 50
    const val SUBTITLE_MAX_SCALE = 200
    const val SUBTITLE_DEFAULT_SCALE = 100
    const val SUBTITLE_DEFAULT_POSITION = 8
    const val SUBTITLE_MAX_POSITION = 40
    const val SUBTITLE_SYNC_STEP_MS = 100L
    const val SUBTITLE_MAX_DELAY_MS = 600000L
}

enum class GestureSlot(val label: String) {
    SINGLE_TAP("Single tap"), DOUBLE_LEFT("Double-tap left"), DOUBLE_RIGHT("Double-tap right"),
    VERTICAL_LEFT("Vertical swipe left"), VERTICAL_RIGHT("Vertical swipe right")
}
enum class GestureAction(val label: String) {
    CONTROLS("Show / hide controls"), PLAY_PAUSE("Play / pause"), SEEK("Seek · direction follows gesture"),
    SEEK_BACK("Seek backward 10 s"), SEEK_FORWARD("Seek forward 10 s"), BRIGHTNESS("Brightness"),
    VOLUME("Volume"), SPEED("Playback speed"), NONE("None")
}
object GesturePolicy {
    fun default(slot: GestureSlot) = when (slot) {
        GestureSlot.SINGLE_TAP -> GestureAction.CONTROLS
        GestureSlot.DOUBLE_LEFT -> GestureAction.SEEK_BACK
        GestureSlot.DOUBLE_RIGHT -> GestureAction.SEEK_FORWARD
        GestureSlot.VERTICAL_LEFT -> GestureAction.BRIGHTNESS
        GestureSlot.VERTICAL_RIGHT -> GestureAction.VOLUME
    }
    fun resolve(slot: GestureSlot, stored: String?, oldVolumeRight: Boolean?, oldTapPlayPause: Boolean): GestureAction {
        GestureAction.values().firstOrNull { it.name == stored }?.let { return it }
        if (oldTapPlayPause && slot in setOf(GestureSlot.DOUBLE_LEFT, GestureSlot.DOUBLE_RIGHT)) return GestureAction.PLAY_PAUSE
        if (oldVolumeRight != null && slot in setOf(GestureSlot.VERTICAL_LEFT, GestureSlot.VERTICAL_RIGHT)) {
            return if ((slot == GestureSlot.VERTICAL_RIGHT) == oldVolumeRight) GestureAction.VOLUME else GestureAction.BRIGHTNESS
        }
        return default(slot)
    }
    fun seek(position: Long, delta: Long, duration: Long): Long {
        val end = duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val base = position.coerceIn(0, end)
        return base + delta.coerceIn(-base, end - base)
    }
    fun safeStart(x: Float, y: Float, width: Int, height: Int, left: Float, top: Float, right: Float, bottom: Float) =
        x > left && x < width - right && y > top && y < height - bottom
    fun doubleTap(firstX: Float, firstY: Float, x: Float, y: Float, elapsed: Long, density: Float, width: Int): Boolean =
        elapsed in 0..GestureThresholds.DOUBLE_TAP_MS && hypot(x - firstX, y - firstY) <= GestureThresholds.DOUBLE_TAP_DISTANCE_DP * density &&
            (firstX < width / 2f) == (x < width / 2f)
    fun speed(value: Float) = value.coerceIn(GestureThresholds.SPEED_MIN, GestureThresholds.SPEED_MAX)
    fun zoom(value: Float) = value.coerceIn(GestureThresholds.ZOOM_MIN, GestureThresholds.ZOOM_MAX)
    fun pan(value: Float, zoom: Float, size: Int) = value.coerceIn(-(zoom - 1f) * size / 2f, (zoom - 1f) * size / 2f)
}

/** Owner is the controller instance; cancellation can never restore a different session's speed. */
class HeldSpeedState {
    private var owner: Any? = null
    private var previous = 1f
    val active get() = owner != null
    fun begin(token: Any, speed: Float): Float { if (owner == null) { owner = token; previous = speed }; return GestureThresholds.HOLD_SPEED }
    fun release(token: Any): Float? { if (owner !== token) return null; owner = null; return previous }
}
