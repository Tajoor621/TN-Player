@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer

import android.content.Intent
import android.os.*
import androidx.lifecycle.Lifecycle
import androidx.media3.session.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tplayer.mediaplayer.playback.BenchmarkWakeLocks
import com.tplayer.mediaplayer.ui.PlayerActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Manual offline workload, skipped by ordinary CI. Uses the service and real PlayerView surface. */
@RunWith(AndroidJUnit4::class)
class PlaybackBenchmarkTest {
    private val args get() = InstrumentationRegistry.getArguments()
    private val root get() = File(DevicePlayback.context.getExternalFilesDir(null), "benchmarks")
    private fun command(player: MediaController, action: String, values: Bundle = Bundle()): Bundle {
        val reply = DevicePlayback.main { player.sendCustomCommand(SessionCommand("BENCHMARK_$action", Bundle.EMPTY), values) }.get(30, TimeUnit.SECONDS)
        check(reply.resultCode == SessionResult.RESULT_SUCCESS) { "$action rejected: ${reply.extras.getString("error")}" }
        return reply.extras
    }
    @Test fun prepareExternalAssetDirectory() {
        assumeTrue(args.getString("benchmark_prepare") == "true")
        assertTrue(File(root, "input").let { it.isDirectory || it.mkdirs() })
    }
    @Test fun measureReferencesAndBackgroundRelease() {
        assumeTrue(args.getString("benchmark") == "true")
        check(com.tplayer.mediaplayer.data.Settings(DevicePlayback.context).backgroundPlayback) { "Enable Background playback before running this workload" }
        val seconds = args.getString("long_session_seconds", "1800").toLong()
        require(seconds in 10..7200)
        val manifest = JSONObject(File(root, "input/manifest.json").readText()).getJSONArray("references")
        require(manifest.length() == 3)
        val refs = (0 until manifest.length()).map(manifest::getJSONObject)
        require(refs.any { it.getString("codec") == "hevc" && it.getInt("width") == 3840 && it.getInt("height") == 2160 })
        require(refs.any { it.getInt("bit_depth") == 10 && it.getString("transfer") == "smpte2084" })
        refs.forEach { ref ->
            val name = ref.getString("name"); require(name.matches(Regex("[A-Za-z0-9_.-]+")))
            val file = File(root, "input/$name")
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream -> val buffer = ByteArray(65536); while (true) { val n = stream.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
            assertEquals(ref.getString("sha256"), digest.digest().joinToString("") { "%02x".format(it) })
        }
        val output = File(root, "results.csv")
        output.parentFile!!.mkdirs()
        val columns = listOf("kind", "status", "error", "device", "android", "sdk", "source_revision", "file", "sha256", "size_bytes", "duration_s", "codec", "width", "height", "bit_depth", "transfer", "ttff_ms", "seek_ms", "dropped_frames", "cpu_ms", "wall_ms", "cpu_percent_one_core", "active_audio", "active_video", "audio_inits", "audio_releases", "video_inits", "video_releases", "tunnel_requested", "tunnel_active", "preload", "pss_kb", "fd_count", "wake_locks", "idle_stops", "elapsed_s", "video_mime", "video_codecs", "color_transfer")
        fun quote(value: Any?) = "\"${value?.toString().orEmpty().replace("\"", "\"\"")}\""
        output.writeText(columns.joinToString(",", transform = ::quote) + "\n")
        fun row(kind: String, status: String, metrics: Bundle, ref: JSONObject? = null, error: String = "", elapsed: Long = 0, locks: Int? = null) {
            val memory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
            val values = mutableMapOf<String, Any?>("kind" to kind, "status" to status, "error" to error,
                "device" to "${Build.MANUFACTURER} ${Build.MODEL}", "android" to Build.VERSION.RELEASE, "sdk" to Build.VERSION.SDK_INT,
                "pss_kb" to memory.totalPss, "fd_count" to (File("/proc/self/fd").list()?.size ?: -1),
                "elapsed_s" to elapsed, "wake_locks" to (locks?.toString() ?: "unknown"))
            metrics.keySet().forEach { key -> @Suppress("DEPRECATION") values[key] = metrics.get(key) }
            ref?.keys()?.forEach { key -> values[if (key == "name") "file" else key] = ref.get(key) }
            output.appendText(columns.joinToString(",") { quote(values[it]) } + "\n")
        }
        fun open(player: MediaController, names: List<String>) = command(player, "OPEN", Bundle().apply { putStringArrayList("files", ArrayList(names)) })
        fun snapshot(player: MediaController) = command(player, "SNAPSHOT")
        fun waitFor(player: MediaController, label: String, condition: (Bundle) -> Boolean): Bundle {
            val end = SystemClock.elapsedRealtime() + 45000
            while (SystemClock.elapsedRealtime() < end) {
                val value = snapshot(player)
                check(value.getString("error").isNullOrEmpty()) { value.getString("error").orEmpty() }
                if (condition(value)) return value
                Thread.sleep(100)
            }
            error("Timed out waiting for $label")
        }
        val scenario = ActivityScenario.launch<PlayerActivity>(Intent(DevicePlayback.context, PlayerActivity::class.java))
        val player = DevicePlayback.controller()
        var began = false
        val failures = mutableListOf<String>()
        try {
            command(player, "BEGIN", Bundle().apply {
                putBoolean("tunnel", args.getString("tunnel") == "true")
                putBoolean("preload", args.getString("preload") == "true")
            }); began = true
            val revision = snapshot(player).getString("source_revision")
            assertEquals("Installed debug APK must match the requested source", args.getString("expected_revision"), revision)
            for (ref in refs) {
                try {
                    open(player, listOf(ref.getString("name")))
                    val first = waitFor(player, "first frame") { it.getLong("ttff_ms", -1) >= 0 }
                    assertEquals(ref.getInt("width"), first.getInt("width"))
                    assertEquals(ref.getInt("height"), first.getInt("height"))
                    if (ref.getString("transfer") == "smpte2084") assertEquals(androidx.media3.common.C.COLOR_TRANSFER_ST2084, first.getInt("color_transfer"))
                    command(player, "SEEK", Bundle().apply { putLong("position_ms", 7000) })
                    waitFor(player, "seek frame") { it.getLong("seek_ms", -1) >= 0 }
                    Thread.sleep(3000)
                    val data = snapshot(player)
                    assertTrue(data.getBoolean("playing"))
                    row("reference", "pass", data, ref)
                } catch (e: Exception) {
                    failures.add("${ref.getString("name")}: ${e.message}")
                    row("reference", "fail", snapshot(player), ref, e.message.orEmpty())
                } catch (e: AssertionError) {
                    failures.add("${ref.getString("name")}: ${e.message}")
                    row("reference", "fail", snapshot(player), ref, e.message.orEmpty())
                }
            }
            open(player, refs.map { it.getString("name") })
            waitFor(player, "background first frame") { it.getLong("ttff_ms", -1) >= 0 }
            scenario.moveToState(Lifecycle.State.CREATED)
            val start = SystemClock.elapsedRealtime()
            var previousPosition = -1L
            while (SystemClock.elapsedRealtime() - start < seconds * 1000) {
                Thread.sleep(10000)
                val data = snapshot(player)
                val elapsed = (SystemClock.elapsedRealtime() - start) / 1000
                val position = data.getLong("position_ms")
                val healthy = data.getBoolean("playing") && data.getString("error").isNullOrEmpty() && position != previousPosition
                row("background_sample", if (healthy) "pass" else "fail", data, elapsed = elapsed,
                    error = if (healthy) "" else "Playback stalled or failed")
                check(healthy) { "Background playback stalled at ${elapsed}s" }
                previousPosition = position
            }
            val before = snapshot(player).getInt("idle_stops")
            DevicePlayback.main { player.pause() }
            val stopped = waitFor(player, "paused-background decoder release") {
                it.getInt("idle_stops") > before && it.getInt("active_audio") == 0 && it.getInt("active_video") == 0
            }
            val locks = currentWakeLocks()
            val status = when { locks == null -> "inconclusive"; locks != 0 -> "fail"; seconds < 1800 -> "short"; else -> "pass" }
            row("background_release", status, stopped, elapsed = (SystemClock.elapsedRealtime() - start) / 1000,
                locks = locks, error = if (locks == null) "Cannot parse current OS wake locks" else "")
            assertEquals("Current ExoPlayer wake-lock count is unknown or nonzero", 0, locks)
            assertTrue("Reference failures: $failures", failures.isEmpty())
        } catch (e: Throwable) {
            row("run", "fail", if (began) snapshot(player) else Bundle(), error = e.message.orEmpty())
            throw e
        } finally {
            try { if (began) command(player, "END") }
            finally { DevicePlayback.main { player.release() }; scenario.close() }
        }
    }
    private fun currentWakeLocks(): Int? = try {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("dumpsys power")
        val text = ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
        BenchmarkWakeLocks.count(text, Process.myUid())
    } catch (_: Exception) { null }
}
