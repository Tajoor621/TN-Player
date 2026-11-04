package com.tplayer.mediaplayer

import android.Manifest
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.tplayer.mediaplayer.data.*
import com.tplayer.mediaplayer.security.PrivateFolder
import com.tplayer.mediaplayer.ui.MainActivity
import com.tplayer.mediaplayer.ui.PrivateFolderActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class LibraryDeviceTest {
    private val context get() = DevicePlayback.context
    private val resolver get() = context.contentResolver
    private val audio = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    private fun audioPermission() = DevicePlayback.instrumentation.uiAutomation.grantRuntimePermission(context.packageName,
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
    private fun wav(): ByteArray = ByteBuffer.allocate(8044).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(8036); put("WAVEfmt ".toByteArray()); putInt(16)
        putShort(1); putShort(1); putInt(8000); putInt(8000); putShort(1); putShort(8)
        put("data".toByteArray()); putInt(8000); put(ByteArray(8000) { 0x80.toByte() })
    }.array()
    private fun add(path: String, name: String, bytes: ByteArray, uris: MutableList<Uri>): Uri {
        val uri = checkNotNull(resolver.insert(audio, ContentValues().apply {
            put(MediaStore.Audio.Media.RELATIVE_PATH, path); put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav"); put(MediaStore.Audio.Media.IS_PENDING, 1)
        }))
        uris += uri
        checkNotNull(resolver.openOutputStream(uri)).use { it.write(bytes) }
        check(resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null) == 1)
        return uri
    }
    @Test fun privateFolderRequiresPinAndIsExcludedFromSearchAndRecents() {
        audioPermission()
        val privacy = PrivateFolder.get(context)
        assumeTrue("Use a dedicated test device with no configured private folder", runBlocking { privacy.configuration() == null })
        val token = "TN-Private-${SystemClock.elapsedRealtime()}"
        val path = "Music/$token/"
        val uris = mutableListOf<Uri>()
        val pin = "654321".toCharArray()
        val raw = MediaDatabase.get(context).raw()
        try {
            val protected = add(path, "Secret-$token.wav", wav(), uris)
            val public = add("Music/$token-public/", "Public-$token.wav", wav(), uris)
            runBlocking {
                raw.record(History(protected.toString(), "Private title", 100, 1000))
                raw.record(History(public.toString(), "Public title", 100, 1000))
                @Suppress("DEPRECATION")
                privacy.configure(Uri.fromFile(File(Environment.getExternalStorageDirectory(), path)), pin)
                assertFalse(privacy.authorize(protected.toString()))
                assertFalse(privacy.authenticate("111111".toCharArray()))
                val page = MediaCatalog(context).page(false, LibraryDisplay(mode = MediaViewMode.FILES), null, token, true, emptyList())
                assertEquals(listOf(public.toString()), page.entries.map { it.uri })
                assertFalse(MediaDatabase.get(context).media().history().any { it.uri == protected.toString() })
                assertTrue(privacy.hideThumbnail(protected.toString()))
            }
            ActivityScenario.launch(PrivateFolderActivity::class.java).use { scenario ->
                DevicePlayback.await("PIN gate did not appear") {
                    var ready = false
                    scenario.onActivity { ready = DevicePlayback.descendants(it.findViewById(android.R.id.content)).filterIsInstance<TextView>().any { label -> label.text.toString() == "Unlock with PIN" } }
                    ready
                }
                assertFalse(privacy.isUnlocked())
                onView(withText("Unlock with PIN")).perform(click())
                onView(withContentDescription("Private folder PIN")).perform(replaceText("654321"), closeSoftKeyboard())
                onView(withText("Continue")).perform(click())
                DevicePlayback.await("Correct PIN did not unlock") { privacy.isUnlocked() }
                assertTrue(runBlocking { privacy.authorize(protected.toString()) })
                assertFalse(runBlocking { MediaDatabase.get(context).media().history().any { it.uri == protected.toString() } })
            }
        } finally {
            runBlocking { if (privacy.configuration() != null && privacy.authenticate(pin)) privacy.removeProtection() }
            uris.forEach { uri -> runBlocking { raw.deleteHistory(uri.toString()) }; resolver.delete(uri, null, null) }
            pin.fill('\u0000')
        }
    }

    @Test fun measureFiveThousandFileScanWhileMainThreadKeepsRunning() {
        assumeTrue("Enable only on a dedicated device with library_benchmark=true", androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("library_benchmark") == "true")
        audioPermission()
        val token = "TN-Library-${SystemClock.elapsedRealtime()}"
        val path = "Music/$token/"
        val uris = mutableListOf<Uri>()
        val bytes = wav()
        try {
            repeat(5000) { add(path, "Reference-${it.toString().padStart(4, '0')}.wav", bytes, uris) }
            ActivityScenario.launch(MainActivity::class.java).use {
                val handler = Handler(Looper.getMainLooper())
                val maxGap = AtomicLong(); val beats = AtomicLong(); val last = AtomicLong()
                val pulse = object : Runnable {
                    override fun run() {
                        val now = SystemClock.elapsedRealtime(); val prior = last.getAndSet(now)
                        if (prior != 0L) maxGap.set(maxOf(maxGap.get(), now - prior))
                        beats.incrementAndGet(); handler.postDelayed(this, 16)
                    }
                }
                DevicePlayback.instrumentation.runOnMainSync { pulse.run() }
                val start = SystemClock.elapsedRealtime()
                val page: CatalogPage
                val elapsed: Long
                try {
                    page = runBlocking { MediaCatalog(context).page(false, LibraryDisplay(mode = MediaViewMode.FILES), IndexedFolder("external_primary", path), "", true, emptyList()) }
                    elapsed = SystemClock.elapsedRealtime() - start
                } finally { DevicePlayback.instrumentation.runOnMainSync { handler.removeCallbacks(pulse) } }
                val output = File(checkNotNull(context.getExternalFilesDir(null)), "benchmarks/library.csv")
                check(output.parentFile?.mkdirs() == true || output.parentFile?.isDirectory == true)
                fun csv(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
                output.writeText("revision,device,android,corpus,files,scan_ms,max_ui_gap_ms,ui_ticks\n${BuildConfig.SOURCE_REVISION},${csv(Build.MODEL)},${csv(Build.VERSION.RELEASE)},${csv("1-second mono 8 kHz PCM WAV; 8044 bytes each")},${page.total},$elapsed,${maxGap.get()},${beats.get()}\n")
                assertEquals(5000, page.total)
                assertTrue("Main thread stalled: ${maxGap.get()} ms", maxGap.get() < 1000 && beats.get() > 1)
            }
        } finally { uris.forEach { resolver.delete(it, null, null) } }
    }
}
