package com.tplayer.mediaplayer

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ApplicationProvider
import com.tplayer.mediaplayer.data.Settings
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
            resume = false; autoPip = false; hidden = true
            seekMs = 30000; theme = AppCompatDelegate.MODE_NIGHT_YES
            tree = "content://provider/tree/example"
        }
        val reloaded = Settings(context)
        assertFalse(reloaded.resume)
        assertFalse(reloaded.autoPip)
        assertTrue(reloaded.hidden)
        assertEquals(30000L, reloaded.seekMs)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, reloaded.theme)
        assertEquals("content://provider/tree/example", reloaded.tree)
    }
}
