@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.*
import androidx.media3.datasource.cache.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

/** Opt-in byte preloading, not a second decoder/player. Never prefetches network/DRM sources. */
class LocalPlaylistPreloader(context: Context) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val upstream = DefaultDataSource.Factory(this.context)
    @Volatile private var cache: SimpleCache? = null
    @Volatile private var writer: CacheWriter? = null
    private var database: StandaloneDatabaseProvider? = null
    @Volatile private var enabled = false
    private var job: Job? = null
    private val cacheLock = Mutex()
    private var target: String? = null
    @Volatile private var ready: String? = null
    private val keyFactory = CacheKeyFactory { spec -> MessageDigest.getInstance("SHA-256").digest(spec.uri.toString().toByteArray()).joinToString("") { "%02x".format(it) } }
    val factory = DataSource.Factory { object : DataSource {
        private var active: DataSource? = null
        private val listeners = mutableListOf<TransferListener>()
        override fun addTransferListener(listener: TransferListener) { listeners.add(listener); active?.addTransferListener(listener) }
        override fun open(spec: DataSpec): Long {
            val ready = cache
            val source = if (enabled && ready != null && local(spec.uri) && this@LocalPlaylistPreloader.ready == spec.uri.toString()) cached(ready).setCacheWriteDataSinkFactory(null).createDataSource() else upstream.createDataSource()
            active = source; listeners.forEach(source::addTransferListener)
            return source.open(spec)
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int) = checkNotNull(active).read(buffer, offset, length)
        override fun getUri(): Uri? = active?.uri
        override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()
        override fun close() { val source = active; active = null; source?.close() }
    } }
    private fun local(uri: Uri) = uri.scheme in setOf("file", "content")
    private fun cached(value: SimpleCache) = CacheDataSource.Factory().setCache(value).setCacheKeyFactory(keyFactory)
        .setUpstreamDataSourceFactory(upstream).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    fun configure(allow: Boolean) { enabled = allow; if (!allow) { ready = null; cancel() } }
    fun warm(next: MediaItem?) {
        val allow = enabled
        val config = next?.localConfiguration
        val uri = config?.uri?.takeIf { local(it) && config.drmConfiguration == null }
        val id = if (allow) uri?.toString() else null
        if (id == target) return
        val previous = job
        cancel(); target = id
        if (id != null && id != ready) ready = null
        if (id == null) return
        job = scope.launch {
            try {
                previous?.cancelAndJoin(); ensureActive()
                cacheLock.withLock {
                    // Construct/initialize the disk cache off the main and playback threads, only
                    // after current playback has started. The current first open bypasses an unready cache.
                    val value = cache ?: SimpleCache(File(context.cacheDir, "next-media"), LeastRecentlyUsedCacheEvictor(8L * 1024 * 1024), (database ?: StandaloneDatabaseProvider(context).also { database = it })).also { cache = it }
                    ensureActive()
                    // Invalidate this next item's old bytes: a URI may have been edited since last play.
                    val spec = DataSpec.Builder().setUri(uri!!).setLength(1024L * 1024).build()
                    value.removeResource(keyFactory.buildCacheKey(spec))
                    val task = CacheWriter(cached(value).createDataSource(), spec, ByteArray(32 * 1024), null)
                    writer = task
                    try { ensureActive(); task.cache(); ensureActive(); ready = id } finally { if (writer === task) writer = null }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Optional warming never changes current playback or shows an error. */ }
        }
    }
    fun cancel() { writer?.cancel(); job?.cancel(); target = null }
    fun close() {
        enabled = false; ready = null; writer?.cancel()
        val previous = job; job = null; target = null
        scope.launch { try { previous?.cancelAndJoin(); cacheLock.withLock { try { cache?.release() } finally { cache = null; database?.close(); database = null } } } finally { scope.cancel() } }
    }
}
