package com.tplayer.mediaplayer

import android.Manifest
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tplayer.mediaplayer.data.*
import com.tplayer.mediaplayer.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderBrowserDeviceTest {
    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun indexedFoldersOpenTheirMediaAndLayoutPersistsAcrossRecreation() {
        val context = DevicePlayback.context
        val settings = Settings(context)
        val previous = settings.libraryDisplay
        val oldHidden = settings.hidden
        val tag = "TN-Browse-${System.currentTimeMillis()}"
        val uris = mutableListOf<android.net.Uri>()
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
        DevicePlayback.instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
        settings.libraryDisplay = LibraryDisplay()
        try {
            for ((name, folder) in listOf("One.mp4" to tag, "Two.mp4" to tag, "Other.mp4" to "$tag-other")) {
                val uri = checkNotNull(context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name); put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/$folder/"); put(MediaStore.Video.Media.IS_PENDING, 1)
                }))
                uris += uri
                context.contentResolver.openOutputStream(uri)!!.use { output -> DevicePlayback.instrumentation.context.assets.open("video-fixture.mp4").use { it.copyTo(output) } }
                context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            }
            val folders = runBlocking { MediaCatalog(context).page(true, LibraryDisplay(), null, tag, false, emptyList()) }
            assertEquals(2, folders.entries.size)
            assertEquals(2, folders.entries.first { it.title == tag }.count)
            val selected = folders.entries.first { it.title == tag }.folder
            val children = runBlocking { MediaCatalog(context).page(true, LibraryDisplay(), selected, "", false, emptyList()) }
            assertEquals(setOf("One.mp4", "Two.mp4"), children.entries.map { it.title }.toSet())
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                fun labels(activity: MainActivity) = DevicePlayback.descendants(activity.findViewById(android.R.id.content)).filterIsInstance<TextView>()
                DevicePlayback.await("Indexed folder was not displayed") {
                    var found = false; scenario.onActivity { found = labels(it).any { view -> view.text.toString() == tag } }; found
                }
                scenario.onActivity { activity -> (labels(activity).first { it.text.toString() == tag }.parent.parent as View).performClick() }
                DevicePlayback.await("Folder contents did not open") {
                    var found = false; scenario.onActivity { found = labels(it).any { view -> view.text.toString() == "Two.mp4" } }; found
                }
                scenario.onActivity { activity -> labels(activity).first { it.text.toString() == "View & layout" }.performClick() }
                androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Grid")).perform(androidx.test.espresso.action.ViewActions.click())
                androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Done")).perform(androidx.test.espresso.action.ViewActions.click())
                assertTrue(settings.libraryDisplay.grid)
                scenario.recreate()
                scenario.onActivity { activity ->
                    val list = DevicePlayback.descendants(activity.findViewById(android.R.id.content)).filterIsInstance<RecyclerView>().first()
                    assertTrue(list.layoutManager is GridLayoutManager)
                    DevicePlayback.descendants(activity.findViewById(android.R.id.content)).filterIsInstance<BottomNavigationView>().single().selectedItemId = 5
                    assertTrue(labels(activity).any { it.text.toString() == "Me" })
                    assertTrue(labels(activity).any { it.text.toString() == "Media Manager" })
                }
            }
        } finally {
            settings.libraryDisplay = previous; settings.hidden = oldHidden
            uris.forEach { context.contentResolver.delete(it, null, null) }
        }
    }
}
