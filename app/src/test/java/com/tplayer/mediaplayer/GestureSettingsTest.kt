package com.tplayer.mediaplayer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tplayer.mediaplayer.data.*
import com.tplayer.mediaplayer.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class GestureSettingsTest {
    @Test fun allFiveAssignmentsSurviveNewInstanceAndBackupRestore() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("tplayer", Context.MODE_PRIVATE).edit().clear().commit()
        val values = listOf(GestureAction.PLAY_PAUSE, GestureAction.NONE, GestureAction.SPEED, GestureAction.VOLUME, GestureAction.BRIGHTNESS)
        GestureSlot.values().forEachIndexed { i, slot -> Settings(context).setGestureAction(slot, values[i]) }
        GestureSlot.values().forEachIndexed { i, slot -> assertEquals(values[i], Settings(context).gestureAction(slot)) }
        val backup = BackupFormat.decode(BackupFormat.encode(LibraryBackup(Settings(context).backupValues(), emptyList(), emptyList(), emptyList())))
        context.getSharedPreferences("tplayer", Context.MODE_PRIVATE).edit().clear().commit()
        Settings(context).restoreValues(backup.preferences)
        GestureSlot.values().forEachIndexed { i, slot -> assertEquals(values[i], Settings(context).gestureAction(slot)) }
    }
    @Test fun freshDefaultsAndExplicitLegacyChoicesDifferIntentionally() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("tplayer", Context.MODE_PRIVATE).edit().clear().commit()
        assertEquals(GestureAction.BRIGHTNESS, Settings(context).gestureAction(GestureSlot.VERTICAL_LEFT))
        Settings(context).gestureVolumeRight = false
        assertEquals(GestureAction.VOLUME, Settings(context).gestureAction(GestureSlot.VERTICAL_LEFT))
    }
}
