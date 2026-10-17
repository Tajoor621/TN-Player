package com.tplayer.mediaplayer

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ApplicationProvider
import com.tplayer.mediaplayer.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class SettingsPersistenceTest {
    @Test fun preferencesSurviveNewRepositoryInstance() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("tplayer", Context.MODE_PRIVATE).edit().clear().commit()
        Settings(context).apply {
            decoderPreference = 2; resume = false; autoPip = false; hidden = true
            seekMs = 30000; theme = AppCompatDelegate.MODE_NIGHT_YES
            tree = "content://provider/tree/example"
            backgroundPlayback = false; mirror = true; verticalFlip = true; playerOrientation = 2
            playerShortcuts = setOf(PlayerShortcut.MUTE, PlayerShortcut.SCREENSHOT)
            libraryDisplay = LibraryDisplay(mode = MediaViewMode.FILES, grid = true, sort = MediaSort.SIZE, descending = true, minimumSeconds = 30, path = true)
        }
        val reloaded = Settings(context)
        assertEquals(LibraryDisplay(mode = MediaViewMode.FILES, grid = true, sort = MediaSort.SIZE, descending = true, minimumSeconds = 30, path = true), reloaded.libraryDisplay)
        val backup = LibraryBackup(reloaded.backupValues(), emptyList(), emptyList(), emptyList())
        val restored = BackupFormat.decode(BackupFormat.encode(backup))
        context.getSharedPreferences("tplayer", Context.MODE_PRIVATE).edit().clear().commit()
        reloaded.restoreValues(restored.preferences)
        assertTrue(reloaded.mirror); assertTrue(reloaded.verticalFlip); assertFalse(reloaded.backgroundPlayback)
        assertEquals(2, reloaded.playerOrientation)
        assertEquals(setOf(PlayerShortcut.MUTE, PlayerShortcut.SCREENSHOT), reloaded.playerShortcuts)
        assertTrue(reloaded.libraryDisplay.grid)
        assertEquals(MediaSort.SIZE, reloaded.libraryDisplay.sort)
        assertEquals(2, reloaded.decoderPreference)
        assertFalse(reloaded.resume)
        assertFalse(reloaded.autoPip)
        assertTrue(reloaded.hidden)
        assertEquals(30000L, reloaded.seekMs)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, reloaded.theme)
        assertNull(reloaded.tree) // Backup never transfers URI grants.
    }
}
