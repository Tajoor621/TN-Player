package com.tplayer.mediaplayer

import android.app.DownloadManager
import com.tplayer.mediaplayer.data.DownloadPolicy
import org.junit.Assert.*
import org.junit.Test

class DownloadPolicyTest {
    @Test fun rejectsAdaptiveManifestsEvenWhenTheUrlHasNoManifestExtension() {
        assertNotNull(DownloadPolicy.error("https://example.com/play?token=a", "application/vnd.apple.mpegurl"))
        assertNotNull(DownloadPolicy.error("https://example.com/MASTER.M3U8?token=a"))
        assertNotNull(DownloadPolicy.error("https://example.com/video.ism/Manifest"))
        assertNotNull(DownloadPolicy.error("http://example.com/a.mp4"))
        assertNotNull(DownloadPolicy.error("rtsp://example.com/live"))
        assertNotNull(DownloadPolicy.error("https://user:password@example.com/a.mp4"))
        assertNull(DownloadPolicy.error("https://example.com/a.mp4?token=a", "video/mp4"))
        assertNull(DownloadPolicy.error("https://example.com/download?id=7", "audio/flac"))
    }
    @Test fun unknownLengthsAndLargeCountersDoNotOverflowProgress() {
        assertTrue(DownloadPolicy.progress(-1, -1).contains("size unknown"))
        assertTrue(DownloadPolicy.progress(Long.MAX_VALUE, Long.MAX_VALUE).contains("100%"))
        assertTrue(DownloadPolicy.progress(200, 100).contains("100%"))
    }
    @Test fun completedImportsDisplayTheirKnownTotalEvenWhenAndroidReportsZeroCurrentBytes() {
        val entry = com.tplayer.mediaplayer.data.DownloadEntry(1, "Imported", DownloadManager.STATUS_SUCCESSFUL, 0, 0, 200, "audio/wav", "content://downloads/1")
        assertTrue(entry.completed)
        assertTrue(entry.item().detail.contains("100%"))
    }
    @Test fun explainsActionableFailureAndWaitReasons() {
        assertEquals("Waiting for Wi-Fi", DownloadPolicy.status(DownloadManager.STATUS_PAUSED, DownloadManager.PAUSED_QUEUED_FOR_WIFI))
        assertEquals("Failed: storage full", DownloadPolicy.status(DownloadManager.STATUS_FAILED, DownloadManager.ERROR_INSUFFICIENT_SPACE))
        assertEquals("Failed: server HTTP 404", DownloadPolicy.status(DownloadManager.STATUS_FAILED, 404))
    }
}
