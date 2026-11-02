package com.tplayer.mediaplayer.playback

object PlaybackPowerPolicy {
    const val IDLE_RELEASE_MS = 10000L
    fun shouldRelease(enabled: Boolean, visible: Boolean, playWhenReady: Boolean, prepared: Boolean) =
        enabled && !visible && !playWhenReady && prepared
    fun allowTunneling(requested: Boolean, softwareForced: Boolean) = requested && !softwareForced
}

/** Test overrides live in memory so interrupted benchmarks cannot change user preferences. */
class PlaybackTuning(private val settings: com.tplayer.mediaplayer.data.Settings) {
    data class Options(val tunnel: Boolean, val preload: Boolean, val releasePaused: Boolean)
    var benchmark: Options? = null
    val tunnel get() = benchmark?.tunnel ?: settings.tunneledPlayback
    val preload get() = benchmark?.preload ?: settings.preloadNext
    val releasePaused get() = benchmark?.releasePaused ?: settings.releasePausedInBackground
}
