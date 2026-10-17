package com.tplayer.mediaplayer

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tplayer.mediaplayer.data.DownloadRepository
import com.tplayer.mediaplayer.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DownloadsDeviceTest {
    private fun waitFor(text: String) {
        DevicePlayback.await("Missing download UI: $text") {
            DevicePlayback.instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.any { it.isVisibleToUser } == true
        }
    }
    @Suppress("DEPRECATION")
    @Test fun completedDownloadIsListedAndRemovalRequiresConfirmation() {
        val context = DevicePlayback.context
        if (android.os.Build.VERSION.SDK_INT <= 28) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(DevicePlayback.instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.WRITE_EXTERNAL_STORAGE")).use { it.readBytes() }
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "TN-download-test-${System.nanoTime()}.wav")
        file.parentFile!!.mkdirs()
        DevicePlayback.instrumentation.context.assets.open("audio-fixture.wav").use { source -> file.outputStream().use { source.copyTo(it) } }
        var id = -1L
        try {
            id = manager.addCompletedDownload(file.name, "TN-Player test fixture", false, "audio/wav", file.absolutePath, file.length(), true)
            val entry = runBlocking { DownloadRepository(context).entries().single { it.id == id } }
            assertTrue(entry.completed); assertEquals(file.length(), entry.total)
            ActivityScenario.launch(MainActivity::class.java).use {
                openActionBarOverflowOrOptionsMenu(context)
                onView(withText("Library")).perform(click())
                waitFor("Your library")
                onView(withText("Downloads")).inRoot(isDialog()).perform(click())
                waitFor(file.name)
                for (command in listOf("mkdir -p /sdcard/Download/tn-player-validation", "screencap -p /sdcard/Download/tn-player-validation/downloads.png")) {
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(DevicePlayback.instrumentation.uiAutomation.executeShellCommand(command)).use { stream -> stream.readBytes() }
                }
                onView(withText(file.name)).perform(click())
                waitFor("Remove download")
                onView(withText("Remove download")).inRoot(isDialog()).perform(click())
                waitFor("Keep")
                onView(withText("Keep")).inRoot(isDialog()).perform(click())
                assertTrue(file.exists())
                assertTrue(runBlocking { DownloadRepository(context).entries().any { e -> e.id == id } })
                onView(withText(file.name)).perform(click())
                waitFor("Remove download")
                onView(withText("Remove download")).inRoot(isDialog()).perform(click())
                waitFor("Keep")
                onView(withText("Remove")).inRoot(isDialog()).perform(click())
                DevicePlayback.await("Confirmed download was not removed") { runBlocking { DownloadRepository(context).entries().none { e -> e.id == id } } }
                assertFalse(file.exists())
            }
        } finally { if (id >= 0) manager.remove(id); file.delete() }
    }
}
