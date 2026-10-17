package com.tplayer.mediaplayer.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.tplayer.mediaplayer.playback.PlaybackPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.UUID

object DownloadPolicy {
    fun error(url: String, mime: String = ""): String? {
        PlaybackPolicy.validateStream(url)?.let { return it }
        if (!url.startsWith("https://", true)) return "Only direct HTTPS files can be downloaded."
        val path = try { java.net.URI(url).path.orEmpty().lowercase() } catch (_: Exception) { return "Invalid download URL." }
        val type = mime.substringBefore(';').trim().lowercase()
        if (type in setOf("application/x-mpegurl", "application/vnd.apple.mpegurl", "application/dash+xml", "application/vnd.ms-sstr+xml") ||
            listOf(".m3u", ".m3u8", ".mpd", ".ism", ".isml").any { path.endsWith(it) } || path.endsWith("/manifest"))
            return "Adaptive/offline stream downloads are not implemented. Choose a direct media file."
        return null
    }
    fun status(status: Int, reason: Int): String = when (status) {
        DownloadManager.STATUS_PENDING -> "Queued"
        DownloadManager.STATUS_RUNNING -> "Downloading"
        DownloadManager.STATUS_SUCCESSFUL -> "Completed"
        DownloadManager.STATUS_PAUSED -> when (reason) {
            DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Waiting for network"
            DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Waiting for Wi-Fi"
            DownloadManager.PAUSED_WAITING_TO_RETRY -> "Waiting to retry"
            else -> "Paused by Android"
        }
        DownloadManager.STATUS_FAILED -> when (reason) {
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Failed: storage full"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Failed: storage unavailable"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "Failed: destination exists"
            DownloadManager.ERROR_CANNOT_RESUME -> "Failed: server cannot resume"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Failed: too many redirects"
            in 400..599 -> "Failed: server HTTP $reason"
            else -> "Failed: Android error $reason"
        }
        else -> "Unknown download state"
    }
    fun progress(bytes: Long, total: Long): String {
        val downloaded = bytes.coerceAtLeast(0)
        return if (total > 0) "%.1f / %.1f MB · %d%%".format(downloaded / 1_000_000.0, total / 1_000_000.0,
            (downloaded.toDouble() / total * 100).coerceIn(0.0, 100.0).toInt())
        else "%.1f MB · size unknown".format(downloaded / 1_000_000.0)
    }
}
data class DownloadEntry(val id: Long, val title: String, val status: Int, val reason: Int, val bytes: Long,
    val total: Long, val mime: String, val playableUri: String?) {
    val completed get() = status == DownloadManager.STATUS_SUCCESSFUL && playableUri != null
    val active get() = status in setOf(DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED)
    fun item() = LibraryItem(playableUri ?: "tn-download:$id", title,
        (if (status == DownloadManager.STATUS_SUCCESSFUL && playableUri == null) "Completed file unavailable" else DownloadPolicy.status(status, reason)) + " · " + DownloadPolicy.progress(bytes, total), mimeType = mime, downloadId = id)
}
class DownloadRepository(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    suspend fun entries(): List<DownloadEntry> = withContext(Dispatchers.IO) {
        val result = mutableListOf<DownloadEntry>()
        manager.query(DownloadManager.Query())?.use { c ->
            fun long(key: String) = c.getLong(c.getColumnIndexOrThrow(key))
            fun string(key: String) = c.getString(c.getColumnIndexOrThrow(key)).orEmpty()
            while (result.size < 500 && c.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val id = long(DownloadManager.COLUMN_ID); val state = long(DownloadManager.COLUMN_STATUS).toInt()
                result += DownloadEntry(id, string(DownloadManager.COLUMN_TITLE).ifBlank { "Download" }.take(1024), state,
                    long(DownloadManager.COLUMN_REASON).toInt(), long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                    long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES), string(DownloadManager.COLUMN_MEDIA_TYPE),
                    if (state == DownloadManager.STATUS_SUCCESSFUL) manager.getUriForDownloadedFile(id)?.toString() else null)
            }
        }
        result
    }
    private fun enqueue(url: String, title: String, mime: String): Long {
        require(DownloadPolicy.error(url, mime) == null) { DownloadPolicy.error(url, mime).orEmpty() }
        val fileName = Uri.parse(url).lastPathSegment?.replace(Regex("[^A-Za-z0-9._-]"), "_")?.take(100)?.ifBlank { "media" } ?: "media"
        val request = DownloadManager.Request(Uri.parse(url)).setTitle(title.take(1024))
            .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, "${UUID.randomUUID()}-$fileName")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        if (mime.isNotBlank()) request.setMimeType(mime)
        return manager.enqueue(request)
    }
    suspend fun start(item: LibraryItem): Long = withContext(Dispatchers.IO) { enqueue(item.uri, item.title, item.mimeType) }
    suspend fun retry(id: Long): Long = withContext(Dispatchers.IO) {
        val request = manager.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            check(c.moveToFirst()) { "This download no longer exists." }
            check(c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_FAILED) { "Only failed downloads can be retried." }
            Triple(c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)), c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)).orEmpty(), c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE)).orEmpty())
        } ?: error("Android could not read this download.")
        // Enqueue first: a failure cannot discard the old failure information.
        val next = enqueue(request.first, request.second, request.third)
        manager.remove(id)
        next
    }
    suspend fun remove(id: Long): Boolean = withContext(Dispatchers.IO) { manager.remove(id) > 0 }
}
