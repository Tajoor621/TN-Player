package com.tplayer.mediaplayer.ui

import android.Manifest
import android.app.Application
import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tplayer.mediaplayer.data.FileSelection
import com.tplayer.mediaplayer.data.LibraryItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

 data class DeleteUi(val running: Boolean = false, val done: Int = 0, val total: Int = 0,
    val sender: IntentSender? = null, val permission: Boolean = false, val dispatched: Boolean = false,
    val removed: List<String> = emptyList(), val notice: String? = null)

/** Keeps the approval/operation state across rotation. A killed process never replays a deletion. */
class FileDeleteModel(app: Application) : AndroidViewModel(app) {
    private val mutable = MutableStateFlow(DeleteUi())
    val state = mutable.asStateFlow()
    private var items = emptyList<LibraryItem>()
    private var index = 0
    private var failed = 0
    private val removed = mutableListOf<String>()
    private var bulk = false
    private var retried = false
    private var task: Job? = null
    private val context get() = getApplication<Application>()
    private val resolver get() = context.contentResolver
    fun start(values: List<LibraryItem>) {
        check(!mutable.value.running) { "Another deletion is already running." }
        require(values.isNotEmpty() && values.size <= FileSelection.LIMIT)
        require(values.all { it.indexedFolder == null && it.detail != "Folder" && Uri.parse(it.uri).scheme in setOf("content", "file") })
        items = values.distinctBy { it.uri }; index = 0; failed = 0; removed.clear(); retried = false
        mutable.value = DeleteUi(running = true, total = items.size)
        bulk = Build.VERSION.SDK_INT >= 30 && items.all { Uri.parse(it.uri).authority == "media" }
        if (bulk && Build.VERSION.SDK_INT >= 30) {
            task = viewModelScope.launch {
                try {
                    val sender = withContext(Dispatchers.IO) { MediaStore.createDeleteRequest(resolver, items.map { Uri.parse(it.uri) }).intentSender }
                    mutable.value = mutable.value.copy(sender = sender)
                } catch (_: Exception) { finish("Android could not request deletion. Try Files → Choose folder for provider access.") }
            }
        } else if (Build.VERSION.SDK_INT <= 28 && items.any { Uri.parse(it.uri).authority == "media" } &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            mutable.value = mutable.value.copy(permission = true)
        } else runLegacy()
    }
    fun dispatched() { mutable.value = mutable.value.copy(dispatched = true) }
    fun result(approved: Boolean) {
        if (!mutable.value.running) return
        if (bulk) {
            if (!approved) { finish("Deletion cancelled. No approval was given."); return }
            task = viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    for (item in items) {
                        ensureActive()
                        try {
                            val exists = resolver.query(Uri.parse(item.uri), arrayOf("_id"), null, null, null)?.use { it.moveToFirst() }
                            if (exists == false) removed += item.uri else failed++
                        } catch (_: java.io.FileNotFoundException) { removed += item.uri }
                        catch (_: Exception) { failed++ }
                    }
                }
                finish()
            }
        } else if (mutable.value.permission) {
            if (approved) { mutable.value = mutable.value.copy(permission = false, dispatched = false); runLegacy() }
            else finish("Storage permission was not granted. No files were deleted.")
        } else {
            mutable.value = mutable.value.copy(sender = null, dispatched = false)
            if (approved) retried = true else { failed++; index++; retried = false }
            runLegacy()
        }
    }
    private fun runLegacy() {
        task = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    while (index < items.size) {
                        ensureActive()
                        val uri = Uri.parse(items[index].uri)
                        try {
                            val ok = when {
                                uri.scheme == "file" -> File(checkNotNull(uri.path)).let { it.isFile && it.delete() }
                                uri.authority == "media" -> resolver.delete(uri, null, null) > 0
                                else -> DocumentFile.fromSingleUri(context, uri)?.let { !it.isDirectory && it.delete() } == true
                            }
                            if (ok) removed += items[index].uri else failed++
                        } catch (error: SecurityException) {
                            if (Build.VERSION.SDK_INT == 29 && error is RecoverableSecurityException && !retried) {
                                mutable.value = mutable.value.copy(sender = error.userAction.actionIntent.intentSender, dispatched = false)
                                return@withContext
                            }
                            failed++
                        } catch (error: CancellationException) { throw error }
                        catch (_: Exception) { failed++ }
                        index++; retried = false
                        mutable.value = mutable.value.copy(done = index)
                    }
                }
                if (index == items.size) finish()
            } catch (_: CancellationException) { finish("Cancelled. ${removed.size} files were already deleted; they cannot be restored here.") }
        }
    }
    fun cancel() { if (mutable.value.sender == null && !mutable.value.permission) task?.cancel() }
    fun launchFailed() { finish("Android's approval screen could not open. No further files were deleted.") }
    private fun finish(message: String? = null) {
        mutable.value = DeleteUi(total = items.size, done = index, removed = removed.toList(),
            notice = message ?: "${removed.size} deleted · $failed failed or could not be verified.")
        items = emptyList()
    }
    fun acknowledge() { mutable.value = DeleteUi() }
}
