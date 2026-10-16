package com.tplayer.mediaplayer

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tplayer.mediaplayer.ui.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class NavigationSmokeTest {
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
    @org.junit.Before fun unavailablePlaybackService() {
        org.robolectric.Shadows.shadowOf(org.robolectric.RuntimeEnvironment.getApplication()).declareComponentUnbindable(android.content.ComponentName(org.robolectric.RuntimeEnvironment.getApplication(), com.tplayer.mediaplayer.playback.MediaPlaybackService::class.java))
    }
    @Test fun launchesAndReachesSettingsWithoutOpeningMedia() {
        val lifecycle = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = lifecycle.get()
            val root = activity.findViewById<View>(android.R.id.content)
            val nav = descendants(root).filterIsInstance<BottomNavigationView>().single()
            nav.selectedItemId = 5
            assertTrue(descendants(root).filterIsInstance<TextView>().any { it.text.toString() == "Make it yours" })
            nav.selectedItemId = 3
            assertTrue(descendants(root).filterIsInstance<TextView>().any { it.text.toString() == "Choose folder" })
        } finally { lifecycle.pause().stop().destroy() }
    }
    @Test fun cancellingPlaylistChooserStillLetsFilesOpenASubfolder() {
        val lifecycle = Robolectric.buildActivity(MainActivity::class.java).setup()
        val base = java.nio.file.Files.createTempDirectory("tplayer-browser").toFile()
        val child = java.io.File(base, "Child").apply { mkdir() }
        try {
            val activity = lifecycle.get()
            val browser = androidx.lifecycle.ViewModelProvider(activity)[com.tplayer.mediaplayer.ui.BrowserState::class.java]
            browser.folder = androidx.documentfile.provider.DocumentFile.fromFile(base)
            val root = activity.findViewById<View>(android.R.id.content)
            descendants(root).filterIsInstance<BottomNavigationView>().single().selectedItemId = 3
            repeat(30) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); if (descendants(root).filterIsInstance<TextView>().none { it.text.toString() == "Child" }) Thread.sleep(20) }
            val toolbar = descendants(root).filterIsInstance<com.google.android.material.appbar.MaterialToolbar>().single()
            toolbar.menu.performIdentifierAction(toolbar.menu.getItem(0).itemId, 0)
            val chooser = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
            chooser.listView!!.performItemClick(null, 2, 2)
            repeat(10) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(20) }
            org.robolectric.shadows.ShadowDialog.getLatestDialog()?.cancel()
            val label = descendants(root).filterIsInstance<TextView>().first { it.text.toString() == "Child" }
            (label.parent.parent as View).performClick()
            assertTrue(browser.folder?.uri == android.net.Uri.fromFile(child))
        } finally { lifecycle.pause().stop().destroy(); base.deleteRecursively() }
    }

}
