package com.tplayer.mediaplayer.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract as Docs
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.tplayer.mediaplayer.security.PrivateFolder
import kotlinx.coroutines.*
import java.io.File
import java.util.Locale

/** Reads a sibling through an existing grant. Never requests broad storage access or copies subtitle bytes. */
class SidecarSubtitles(private val context: Context) {
    private val types = linkedMapOf("srt" to MimeTypes.APPLICATION_SUBRIP, "vtt" to MimeTypes.TEXT_VTT,
        "ass" to MimeTypes.TEXT_SSA, "ssa" to MimeTypes.TEXT_SSA, "ttml" to MimeTypes.APPLICATION_TTML)
    private val resolver = context.contentResolver
    private val folders = mutableMapOf<Uri, List<Pair<Uri, String>>>()

    suspend fun find(video: Uri, mime: String?): MediaItem.SubtitleConfiguration? = withContext(Dispatchers.IO) {
        if (mime?.startsWith("audio/") == true || video.scheme !in setOf("file", "content")) return@withContext null
        try {
            val (name, siblings) = when {
                video.scheme == "file" -> {
                    val file = File(video.path ?: return@withContext null)
                    file.name to (file.parentFile?.listFiles()?.asSequence()?.take(5000)?.filter { it.isFile }?.map { Uri.fromFile(it) to it.name }?.toList().orEmpty())
                }
                Build.VERSION.SDK_INT >= 21 && Docs.isDocumentUri(context, video) && video.pathSegments.firstOrNull() == "tree" &&
                    video.authority == "com.android.externalstorage.documents" -> {
                    val id = Docs.getDocumentId(video); val parent = id.substringAfter(':').substringBeforeLast('/', "")
                    id.substringAfterLast('/').substringAfter(':') to children(video, id.substringBefore(':') + ":" + parent)
                }
                video.authority == "media" && Build.VERSION.SDK_INT >= 29 -> {
                    val location = resolver.query(video, arrayOf("volume_name", "relative_path", "_display_name"), null, null, null)?.use { c ->
                        if (c.moveToFirst()) Triple(c.getString(0).orEmpty(), c.getString(1).orEmpty().trim('/'), c.getString(2).orEmpty()) else null
                    } ?: return@withContext null
                    val volume = if (location.first == "external_primary") "primary" else location.first
                    val grant = resolver.persistedUriPermissions.firstOrNull { permission ->
                        val uri = permission.uri
                        if (!permission.isReadPermission || uri.authority != "com.android.externalstorage.documents" || !Docs.isTreeUri(uri)) false
                        else Docs.getTreeDocumentId(uri).let { id ->
                            val root = id.substringAfter(':').trim('/')
                            id.substringBefore(':').equals(volume, true) && (root.isEmpty() || location.second.equals(root, true) || location.second.startsWith("$root/", true))
                        }
                    } ?: return@withContext null
                    location.third to children(grant.uri, "$volume:${location.second}")
                }
                else -> return@withContext null
            }
            val match = matchingSidecar(name, siblings.map { it.second }, types.keys) ?: return@withContext null
            val subtitle = siblings.first { it.second == match }.first
            val privacy = PrivateFolder.get(context)
            val privateVideo = privacy.isPrivate(video.toString())
            if (!privacy.authorize(subtitle.toString()) || (privacy.isPrivate(subtitle.toString()) && !privateVideo)) return@withContext null
            MediaItem.SubtitleConfiguration.Builder(subtitle).setMimeType(types.getValue(match.substringAfterLast('.').lowercase(Locale.ROOT)))
                .setLabel(if (privateVideo) "External subtitle" else match).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
    }

    private suspend fun children(tree: Uri, parent: String): List<Pair<Uri, String>> {
        val query = Docs.buildChildDocumentsUriUsingTree(tree, parent)
        return folders[query] ?: buildList {
            resolver.query(query, arrayOf(Docs.Document.COLUMN_DOCUMENT_ID, Docs.Document.COLUMN_DISPLAY_NAME, Docs.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
                var scanned = 0
                while (scanned++ < 5000 && c.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    if (c.getString(2) != Docs.Document.MIME_TYPE_DIR) add(Docs.buildDocumentUriUsingTree(tree, c.getString(0)) to c.getString(1).orEmpty())
                }
            }
        }.also { if (folders.size < 8) folders[query] = it }
    }
}

internal fun matchingSidecar(video: String, siblings: List<String>, extensions: Set<String>): String? {
    val stem = video.substringBeforeLast('.', "").takeIf { it.isNotEmpty() } ?: return null
    return extensions.firstNotNullOfOrNull { extension ->
        siblings.firstOrNull { it == "$stem.$extension" } ?: siblings.filter { it.equals("$stem.$extension", true) }.minOrNull()
    }
}
