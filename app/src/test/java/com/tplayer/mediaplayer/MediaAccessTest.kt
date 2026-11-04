package com.tplayer.mediaplayer

import android.Manifest
import com.tplayer.mediaplayer.data.MediaAccess
import com.tplayer.mediaplayer.data.mediaAccess
import com.tplayer.mediaplayer.data.mediaPermissions
import org.junit.Assert.*
import org.junit.Test

class MediaAccessTest {
    @Test fun selectedVideoAccessAndReselectionUseBothPermissionsFrom34Onward() {
        for (sdk in 34..36) {
            val selected = Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            val full = Manifest.permission.READ_MEDIA_VIDEO
            assertArrayEquals(arrayOf(full, selected), mediaPermissions(sdk, true))
            assertArrayEquals(arrayOf(full, Manifest.permission.READ_MEDIA_AUDIO, selected), mediaPermissions(sdk, true, upgrade = true))
            assertEquals(MediaAccess.NONE, mediaAccess(sdk, true) { false })
            assertEquals(MediaAccess.SELECTED, mediaAccess(sdk, true) { it == selected })
            assertEquals(MediaAccess.FULL, mediaAccess(sdk, true) { it == full || it == selected })
            assertEquals(MediaAccess.NONE, mediaAccess(sdk, false) { it == selected })
        }
    }

    @Test fun legacyAndAudioGrantsKeepTheirOwnPermissionPaths() {
        assertArrayEquals(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), mediaPermissions(21, true))
        assertArrayEquals(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), mediaPermissions(32, false))
        assertArrayEquals(arrayOf(Manifest.permission.READ_MEDIA_VIDEO), mediaPermissions(33, true))
        assertArrayEquals(arrayOf(Manifest.permission.READ_MEDIA_AUDIO), mediaPermissions(36, false))
        assertEquals(MediaAccess.FULL, mediaAccess(21, false) { it == Manifest.permission.READ_EXTERNAL_STORAGE })
        assertEquals(MediaAccess.FULL, mediaAccess(36, false) { it == Manifest.permission.READ_MEDIA_AUDIO })
    }
}
