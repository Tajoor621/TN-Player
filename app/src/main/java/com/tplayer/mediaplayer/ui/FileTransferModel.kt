package com.tplayer.mediaplayer.ui

import android.app.Application
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tplayer.mediaplayer.data.DocumentOperations
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TransferUi(val running: Boolean = false, val phase: String = "", val bytes: Long = 0, val total: Long = -1,
    val notice: String? = null, val succeeded: Boolean = false)
class FileTransferModel(app: Application) : AndroidViewModel(app) {
    private val mutable = MutableStateFlow(TransferUi())
    val state = mutable.asStateFlow()
    private var work: Job? = null
    fun start(source: DocumentFile, target: DocumentFile, move: Boolean) {
        if (work?.isActive == true) return
        mutable.value = TransferUi(running = true, phase = "Preparing")
        work = viewModelScope.launch {
            try {
                val result = DocumentOperations.copy(getApplication(), source, target, move) { p ->
                    mutable.value = TransferUi(true, p.phase, p.bytes, p.total)
                }
                mutable.value = TransferUi(notice = if (move && !result.sourceRemoved) "Copy verified, but the original could not be deleted. Both copies were retained." else if (move) "Move completed and verified." else "Copy completed and verified.", succeeded = true)
            } catch (_: CancellationException) {
                mutable.value = TransferUi(notice = "Transfer cancelled. Check the destination if finalization had started. Originals are removed only after destination verification.")
            } catch (e: Exception) {
                mutable.value = TransferUi(notice = e.message ?: "Transfer failed. Original retained.")
            }
        }
    }
    fun cancel() { work?.cancel() }
    fun acknowledge() { mutable.value = TransferUi() }
}
