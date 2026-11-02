package com.tplayer.mediaplayer

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Uses Android IPC only: never relies on unobfuscated application or Media3 class names. */
class ReleaseCodecSmokeTest {
    @Test fun optimizedReleaseLoadsRealFfmpegThroughThePlaybackService() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("releaseCodec") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
        }
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.MEDIA_CONTENT_CONTROL")
        try {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            for (name in listOf("eac3", "dts", "ac3", "truehd")) {
                val file = File(context.cacheDir, "exports/release-$name.mka").apply { parentFile!!.mkdirs() }
                instrumentation.context.assets.open("codecs/$name.mka").use { input -> file.outputStream().use { input.copyTo(it) } }
                shell("logcat -c")
                val intent = Intent().setComponent(ComponentName(context.packageName, context.packageName + ".ui.PlayerActivity"))
                    .setData(Uri.parse("content://${context.packageName}.fileprovider/exports/${file.name}"))
                    .putExtra("mime", "audio/x-matroska").putExtra("title", "Release $name fixture")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                ActivityScenario.launch<Activity>(intent).use {
                    val deadline = SystemClock.elapsedRealtime() + 30000
                    var passed = false
                    while (SystemClock.elapsedRealtime() < deadline) {
                        val state = manager.getActiveSessions(null).firstOrNull { it.packageName == context.packageName }?.playbackState
                        val decoder = shell("logcat -d -s TNPlayerFormats:D '*:S'")
                        if (state?.state == PlaybackState.STATE_PLAYING && decoder.contains("Audio decoder: ffmpeg", ignoreCase = true)) { passed = true; break }
                        Thread.sleep(100)
                    }
                    assertTrue("Optimized release did not load FFmpeg and play $name through its media session", passed)
                    manager.getActiveSessions(null).firstOrNull { it.packageName == context.packageName }?.transportControls?.stop()
                }
                file.delete()
            }
        } finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
    }
}
