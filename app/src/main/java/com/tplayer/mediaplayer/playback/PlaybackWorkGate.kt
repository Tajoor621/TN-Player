package com.tplayer.mediaplayer.playback

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** No disk I/O or retained Activities. Startup deferral expires even when media cannot open. */
object PlaybackWorkGate {
    @Volatile private var openingUntil = 0L
    fun opening() { openingUntil = SystemClock.elapsedRealtime() + 8000 }
    fun started() { openingUntil = 0 }
    suspend fun awaitPlaybackStart() {
        while (SystemClock.elapsedRealtime() < openingUntil) delay(50)
    }
}
object AppVisibility : Application.ActivityLifecycleCallbacks {
    private val state = MutableStateFlow(false)
    val visible = state.asStateFlow()
    private var count = 0
    override fun onActivityStarted(activity: Activity) { state.value = ++count > 0 }
    override fun onActivityStopped(activity: Activity) { count = (count - 1).coerceAtLeast(0); state.value = count > 0 }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
