package com.tplayer.mediaplayer.ui

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout

/** Routing before child dispatch prevents PlayerView controls from receiving the second tap of a gesture. */
class PlayerGestureFrame(context: Context) : FrameLayout(context) {
    var gestures: ((MotionEvent) -> Boolean)? = null
    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        if (gestures?.invoke(event) == true) true else super.dispatchTouchEvent(event)
}
