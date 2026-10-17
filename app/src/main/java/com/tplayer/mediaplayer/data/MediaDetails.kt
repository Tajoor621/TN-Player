package com.tplayer.mediaplayer.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.tplayer.mediaplayer.playback.PlaybackPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

fun mediaTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%d:%02d".format(seconds / 60, seconds % 60)
}
class MediaDetails(private val context: Context) {
    suspend fun describe(item: LibraryItem, history: History?): String = withContext(Dispatchers.IO) {
        val uri = Uri.parse(item.uri)
        if (uri.scheme !in setOf("content", "file")) return@withContext "${item.title}\nNetwork source · ${item.mimeType}\n${com.tplayer.mediaplayer.playback.StreamFormat.displayAddress(item.uri)}"
        var name = item.title; var bytes = -1L; var modified = 0L; var location = uri.toString()
        var mime = item.mimeType
        if (uri.scheme == "file") File(uri.path.orEmpty()).let { name = it.name; bytes = if (it.exists()) it.length() else -1; modified = it.lastModified(); location = it.absolutePath }
        else {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { c -> if (c.moveToFirst()) {
                    fun string(column: String) = c.getColumnIndex(column).takeIf { it >= 0 }?.let { if (c.isNull(it)) null else c.getString(it) }
                    name = string(OpenableColumns.DISPLAY_NAME) ?: name
                    bytes = string(OpenableColumns.SIZE)?.toLongOrNull() ?: -1
                    modified = (string("date_modified")?.toLongOrNull() ?: 0) * 1000
                    string("relative_path")?.let { location = (string("volume_name") ?: "Storage") + " / " + it }
                } }
                mime = context.contentResolver.getType(uri) ?: mime
                if (modified == 0L && uri.authority != "media") modified = DocumentFile.fromSingleUri(context, uri)?.lastModified() ?: 0
            } catch (_: Exception) { }
        }
        val fields = mutableListOf("Name: $name", "Type: ${mime.ifBlank { "Unknown" }}", "Location: $location", "URI: $uri")
        fields += if (bytes >= 0) "Size: $bytes bytes · %.2f MB".format(bytes / 1_000_000.0) else "Size: unavailable"
        if (modified > 0) fields += "Modified: ${DateFormat.getDateTimeInstance().format(Date(modified))}"
        if (mime.startsWith("video/") || mime.startsWith("audio/")) {
            val metadata = MediaMetadataRetriever()
            try {
                metadata.setDataSource(context, uri)
                fun value(key: Int) = metadata.extractMetadata(key)?.takeIf { it.isNotBlank() }
                value(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { fields += "Duration: ${mediaTime(it)}" }
                val width = value(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH); val height = value(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                if (width != null && height != null) fields += "Resolution: $width × $height"
                value(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.let { fields += "Bitrate: $it bits/s" }
                value(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS)?.let { fields += "Tracks: $it" }
                if (android.os.Build.VERSION.SDK_INT >= 23) value(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.let { fields += "Capture frame rate: $it fps" }
            } catch (_: Exception) { fields += "Additional metadata unavailable for this source." }
            finally { try { metadata.release() } catch (_: Exception) { } }
        }
        if (history != null) {
            fields += "Last played: ${DateFormat.getDateTimeInstance().format(Date(history.updatedAt))}"
            fields += "Saved position: ${mediaTime(history.position)}"
            fields += "Resume at: ${mediaTime(PlaybackPolicy.resumePosition(history.position, history.duration))}"
        } else fields += "Last played: not in this app's retained history"
        fields.joinToString("\n\n")
    }
}
