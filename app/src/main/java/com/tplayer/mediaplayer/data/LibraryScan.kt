package com.tplayer.mediaplayer.data

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque
import kotlin.coroutines.resume

internal fun storageFolder(uri: Uri): IndexedFolder? {
    if (uri.authority != "com.android.externalstorage.documents") return null
    return runCatching {
        val id = if ("document" in uri.pathSegments) DocumentsContract.getDocumentId(uri) else DocumentsContract.getTreeDocumentId(uri)
        val volume = id.substringBefore(':')
        val path = id.substringAfter(':', "").trim('/')
        require(path.split('/').none { it == "." || it == ".." })
        IndexedFolder(if (volume == "primary") (if (Build.VERSION.SDK_INT >= 29) "external_primary" else "external") else volume.lowercase(),
            if (path.isEmpty()) "" else "$path/")
    }.getOrNull()
}

internal object LibraryScan {
    @Suppress("DEPRECATION") // Maps an already chosen local SAF tree; no broad-storage grant is requested.
    suspend fun rescan(context: Context, tree: Uri): Int = withContext(Dispatchers.IO) {
        val folder = storageFolder(tree) ?: return@withContext 0
        val volume = if (folder.volume in setOf("external_primary", "external")) Environment.getExternalStorageDirectory()
            else if (Build.VERSION.SDK_INT >= 30) context.getSystemService(StorageManager::class.java)
                ?.storageVolumes?.firstOrNull { it.uuid.equals(folder.volume, true) }?.directory else null
        val base = volume?.canonicalFile ?: return@withContext 0
        val root = File(base, folder.path).canonicalFile
        if (root != base && !root.path.startsWith(base.path + File.separator)) return@withContext 0
        val pending = ArrayDeque<File>().apply { add(root) }
        val files = mutableListOf<String>()
        val seen = hashSetOf<String>()
        while (pending.isNotEmpty() && seen.size < 10_000 && files.size < 5000) {
            currentCoroutineContext().ensureActive()
            val file = pending.removeFirst().canonicalFile
            if ((file != root && !file.path.startsWith(root.path + File.separator)) || !seen.add(file.path)) continue
            if (file.isFile && file.canRead()) files.add(file.path)
            else if (file.isDirectory) file.listFiles()?.take((10_000 - pending.size).coerceAtLeast(0))?.forEach(pending::addLast)
        }
        if (files.isEmpty()) return@withContext 0
        suspendCancellableCoroutine { continuation ->
            val remaining = java.util.concurrent.atomic.AtomicInteger(files.size)
            MediaScannerConnection.scanFile(context.applicationContext, files.toTypedArray(), null) { _, _ ->
                if (remaining.decrementAndGet() == 0 && continuation.isActive) continuation.resume(files.size)
            }
        }
    }
}
