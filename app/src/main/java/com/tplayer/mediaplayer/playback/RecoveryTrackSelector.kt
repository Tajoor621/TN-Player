@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer.playback

import android.content.Context
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

class RecoveryTrackSelector(context: Context) : DefaultTrackSelector(context) {
    fun refreshRendererChoices() { invalidate() }
}
