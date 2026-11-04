package com.tplayer.mediaplayer.data

import android.Manifest

internal enum class MediaAccess { FULL, SELECTED, NONE }

internal fun mediaPermissions(sdk: Int, video: Boolean): Array<String> = when {
    sdk >= 34 && video -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    sdk >= 33 -> arrayOf(if (video) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_MEDIA_AUDIO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

internal fun mediaAccess(sdk: Int, video: Boolean, granted: (String) -> Boolean): MediaAccess {
    val requested = mediaPermissions(sdk, video)
    return when {
        granted(requested[0]) -> MediaAccess.FULL
        requested.size > 1 && granted(requested[1]) -> MediaAccess.SELECTED
        else -> MediaAccess.NONE
    }
}
