package com.tplayer.mediaplayer

import android.os.StrictMode
import androidx.appcompat.app.AppCompatDelegate
import androidx.multidex.MultiDexApplication
import androidx.work.*
import com.tplayer.mediaplayer.data.Settings
import com.tplayer.mediaplayer.worker.CleanupWorker
import java.util.concurrent.TimeUnit

class TNPlayerApplication : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(Settings(this).theme)
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectNetwork().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().penaltyLog().build())
        }
        WorkManager.getInstance(this).enqueueUniqueWork("transfer-recovery", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CleanupWorker>().build())
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "cache-cleanup", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.DAYS).build()
        )
    }
}
