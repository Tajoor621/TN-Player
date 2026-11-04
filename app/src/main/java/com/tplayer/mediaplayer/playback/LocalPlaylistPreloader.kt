@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.*
import com.tplayer.mediaplayer.security.PrivateFolder
import kotlinx.coroutines.*

/** Warm only next-item metadata. Playback buffering remains in Media3 memory; media bytes are never cached to disk. */
class LocalPlaylistPreloader(context: Context) {
    private val app = context.applicationContext
    private val upstream = DefaultDataSource.Factory(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    init { scope.launch { java.io.File(app.cacheDir, "next-media").deleteRecursively() } }
    private var enabled = false; private var job: Job? = null; private var target: String? = null
    val factory = DataSource.Factory { GuardedDataSource(upstream.createDataSource(), PrivateFolder.get(app)) }
    fun configure(allow: Boolean) { enabled = allow; if (!allow) cancel() }
    fun warm(next: MediaItem?) {
        val uri = next?.localConfiguration?.takeIf { it.drmConfiguration == null }?.uri
        if (!enabled || uri?.scheme !in setOf("content", "file")) { cancel(); return }
        if (target == uri.toString()) return
        cancel(); target = uri.toString()
        job = scope.launch {
            runCatching {
                if (!PrivateFolder.get(app).authorize(uri.toString())) return@runCatching
                if (uri!!.scheme == "content") app.contentResolver.query(uri, arrayOf("_display_name", "_size"), null, null, null)?.use { it.moveToFirst() }
                else java.io.File(checkNotNull(uri.path)).let { it.length(); it.lastModified() }
            }
        }
    }
    fun cancel() { job?.cancel(); job = null; target = null }
    fun close() { cancel(); scope.cancel() }
}
private class GuardedDataSource(private val delegate: DataSource, private val privacy: PrivateFolder) : DataSource by delegate {
    override fun open(spec: DataSpec): Long {
        // Media3 opens on a loader thread, never the UI thread.
        if (!runBlocking { privacy.authorize(spec.uri.toString()) }) throw java.io.IOException("Private file is locked")
        return delegate.open(spec)
    }
}
