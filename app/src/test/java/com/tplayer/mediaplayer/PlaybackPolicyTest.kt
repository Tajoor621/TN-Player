package com.tplayer.mediaplayer
import com.tplayer.mediaplayer.playback.PlaybackPolicy
import com.tplayer.mediaplayer.data.DocumentOperations
import org.junit.Assert.*
import org.junit.Test

class PlaybackPolicyTest {
    @Test fun rejectsMalformedAndUnsupportedStreams() {
        listOf("garbage", "https://", "https://user:password@example.com/file.mp4", "ftp://example.com/a", "javascript:alert(1)", "http://example.com/video.mp4").forEach {
            assertNotNull(it, PlaybackPolicy.validateStream(it))
        }
        assertNull(PlaybackPolicy.validateStream("https://example.com/master.m3u8?token=abc"))
        assertNull(PlaybackPolicy.validateStream("rtsp://192.168.1.20:554/live"))
    }
    @Test fun oversizedRequestsFailBeforeBuildingAnAndroidIntent() {
        assertNotNull(PlaybackPolicy.validateStream("https://example.com/" + "x".repeat(32768)))
        assertNotNull(PlaybackPolicy.validateStream("https://example.com/video\n.mp4"))
        assertNull(PlaybackPolicy.queueError(listOf("content://media/1"), listOf("File"), listOf("video/mp4")))
        assertNotNull(PlaybackPolicy.queueError(List(1001) { "content://media/$it" }, List(1001) { "File" }, List(1001) { "" }))
        assertNotNull(PlaybackPolicy.queueError(List(200) { "content://media/$it" }, List(200) { "x".repeat(1000) }, List(200) { "" }))
        assertNotNull(PlaybackPolicy.queueError(listOf("content://media/1"), emptyList(), listOf("")))
        assertNotNull(PlaybackPolicy.queueError(listOf("content://media/1"), listOf("x".repeat(1025)), listOf("")))
    }
    @Test fun completedMediaRestartsButUnfinishedMediaResumes() {
        assertEquals(0L, PlaybackPolicy.resumePosition(99900, 100000))
        assertEquals(42000L, PlaybackPolicy.resumePosition(42000, 100000))
        assertEquals(42000L, PlaybackPolicy.resumePosition(42000, 0))
        assertEquals(0L, PlaybackPolicy.resumePosition(-100, 100000))
    }
    @Test fun loopHasMinimumDurationAndCannotRunBackwards() {
        assertFalse(PlaybackPolicy.validLoop(1000, 900))
        assertFalse(PlaybackPolicy.validLoop(-1, 5000))
        assertFalse(PlaybackPolicy.validLoop(10, Long.MIN_VALUE))
        assertFalse(PlaybackPolicy.validLoop(1000, 1200))
        assertTrue(PlaybackPolicy.validLoop(1000, 2000))
    }
    @Test fun filenamesCannotEscapeChosenDirectory() {
        listOf("", " ", ".", "..", "../movie.mp4", "folder/movie.mp4", "bad\\name", "bad\u0000name").forEach { assertFalse(it, DocumentOperations.validName(it)) }
        assertTrue(DocumentOperations.validName("My video (2026).mp4"))
        assertTrue(DocumentOperations.validName(".hidden.mp3"))
    }
}
