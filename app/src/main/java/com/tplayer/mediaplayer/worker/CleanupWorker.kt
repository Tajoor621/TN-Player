package com.tplayer.mediaplayer.worker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import kotlinx.coroutines.runBlocking
import com.tplayer.mediaplayer.data.DocumentOperations

class CleanupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        // Only app-owned disposable exports. Never traverse media or user folders.
        try { runBlocking { DocumentOperations.recoverInterrupted(applicationContext) } }
        catch (_: Exception) { /* Keep the encrypted journal for a later retry after access is restored. */ }
        val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        for (folder in listOf("exports", "subtitles")) File(applicationContext.cacheDir, folder).listFiles()?.filter { it.isFile && it.lastModified() < cutoff }?.forEach { it.delete() }
        return Result.success()
    }
}
