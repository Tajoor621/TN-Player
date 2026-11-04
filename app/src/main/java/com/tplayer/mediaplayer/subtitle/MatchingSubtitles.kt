package com.tplayer.mediaplayer.subtitle

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.DocumentsContract as Docs
import kotlinx.coroutines.*
import java.io.File
import java.util.Locale

internal object SidecarNames {
    private val extensions = listOf("srt", "vtt", "ass", "ssa", "ttml", "dfxp", "mpl", "pjs", "sub", "txt")
    // An optional single language tag, with optional script and region subtags.
    private val language = Regex("[a-zA-Z]{2,3}(?:-[a-zA-Z]{4})?(?:-(?:[a-zA-Z]{2}|[0-9]{3}))?")

    fun rank(video: String, subtitle: String): Int? {
        val videoStem = video.substringBeforeLast('.', "").takeIf { it.isNotEmpty() } ?: return null
        val ext = extensions.indexOf(subtitle.substringAfterLast('.', "").lowercase(Locale.ROOT))
        if (ext < 0) return null
        val stem = subtitle.substringBeforeLast('.')
        if (stem.equals(videoStem, ignoreCase = true)) return ext
        if (!stem.startsWith("$videoStem.", ignoreCase = true)) return null
        return (extensions.size + ext).takeIf { language.matches(stem.substring(videoStem.length + 1)) }
    }
}

internal data class MatchingSubtitle(val uri: Uri, val name: String)

internal class MatchingSubtitles(context: Context) {
    private val resolver = context.contentResolver
    private val provider = "com.android.externalstorage.documents"
    private val limit = 5000

    suspend fun find(mediaId: String, mime: String): List<MatchingSubtitle> = withContext(Dispatchers.IO) {
        val media = Uri.parse(mediaId)
        if (mime.startsWith("audio/", true) || (media.authority == "media" && "audio" in media.pathSegments) || media.scheme !in setOf("content", "file")) return@withContext emptyList()
        val signal = CancellationSignal()
        coroutineScope {
            val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { signal.cancel() }
            }
            try {
                val matches = mutableListOf<Pair<Int, MatchingSubtitle>>()
                val job = currentCoroutineContext()
                fun add(video: String, uri: Uri, name: String) {
                    job.ensureActive()
                    SidecarNames.rank(video, name)?.let { matches += it to MatchingSubtitle(uri, name) }
                }
                fun files(file: File) {
                    val parent = file.canonicalFile.parentFile ?: return
                    parent.listFiles()?.asSequence()?.take(limit)?.forEach { child ->
                        job.ensureActive()
                        if (SidecarNames.rank(file.name, child.name) != null && child.isFile && child.canRead() &&
                            child.canonicalFile.parentFile == parent) add(file.name, Uri.fromFile(child), child.name)
                    }
                }
                if (media.scheme == "file") media.path?.let { files(File(it)) }
                else if (media.authority == "media" && Build.VERSION.SDK_INT < 29) {
                    resolver.query(media, arrayOf("_data"), null, null, null, signal)?.use {
                        if (it.moveToFirst()) it.getString(0)?.let { path -> files(File(path)) }
                    }
                } else {
                    val source = location(media, signal) ?: return@coroutineScope emptyList()
                    val tree = source.tree ?: resolver.persistedUriPermissions.asSequence()
                        .filter { it.isReadPermission && it.uri.authority == provider && it.uri.pathSegments.firstOrNull() == "tree" }
                        .map { it.uri }.firstOrNull { covers(Docs.getTreeDocumentId(it), source.parentId) }
                        ?: return@coroutineScope emptyList()
                    if (!covers(Docs.getTreeDocumentId(tree), source.parentId)) return@coroutineScope emptyList()
                    val children = Docs.buildChildDocumentsUriUsingTree(tree, source.parentId)
                    val fields = arrayOf(Docs.Document.COLUMN_DOCUMENT_ID, Docs.Document.COLUMN_DISPLAY_NAME, Docs.Document.COLUMN_MIME_TYPE)
                    resolver.query(children, fields, null, null, null, signal)?.use { cursor ->
                        var seen = 0
                        while (seen++ < limit && cursor.moveToNext()) {
                            job.ensureActive()
                            if (cursor.getString(2) != Docs.Document.MIME_TYPE_DIR) {
                                val name = cursor.getString(1) ?: continue
                                add(source.name, Docs.buildDocumentUriUsingTree(tree, cursor.getString(0)), name)
                            }
                        }
                    }
                }
                matches.sortedWith(compareBy<Pair<Int, MatchingSubtitle>> { it.first }
                    .thenBy { it.second.name.lowercase(Locale.ROOT) }.thenBy { it.second.uri.toString() })
                    .map { it.second }.distinctBy { it.uri }.take(24)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { currentCoroutineContext().ensureActive(); emptyList() }
            finally { cancellation.cancel() }
        }
    }

    private data class Location(val name: String, val parentId: String, val tree: Uri? = null)

    private fun location(uri: Uri, signal: CancellationSignal): Location? {
        if (uri.authority == provider) {
            val id = Docs.getDocumentId(uri)
            val path = id.substringAfter(':', "")
            if (path.isEmpty()) return null
            val parent = id.substringBefore(':') + ":" + path.substringBeforeLast('/', "")
            return Location(path.substringAfterLast('/'), parent, uri.takeIf { it.pathSegments.firstOrNull() == "tree" })
        }
        if (uri.authority != "media") return null
        return resolver.query(uri, arrayOf("volume_name", "relative_path", "_display_name"), null, null, null, signal)?.use {
            if (!it.moveToFirst()) return@use null
            val volume = it.getString(0) ?: return@use null
            val path = it.getString(1)?.trim('/') ?: return@use null
            val name = it.getString(2) ?: return@use null
            Location(name, "${if (volume == "external_primary") "primary" else volume}:$path")
        }
    }

    private fun covers(root: String, parent: String): Boolean {
        val base = root.trimEnd('/')
        return base == parent || if (base.endsWith(':')) parent.startsWith(base) else parent.startsWith("$base/")
    }
}
