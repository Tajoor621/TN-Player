package com.tplayer.mediaplayer

import com.tplayer.mediaplayer.playback.BenchmarkWakeLocks
import com.tplayer.mediaplayer.playback.PlaybackPowerPolicy
import org.junit.Assert.*
import org.junit.Test

class PlaybackPerformancePolicyTest {
    @Test fun idleReleaseRequiresEveryCondition() {
        for (enabled in listOf(false, true)) for (visible in listOf(false, true))
            for (playing in listOf(false, true)) for (prepared in listOf(false, true)) {
                assertEquals(enabled && !visible && !playing && prepared,
                    PlaybackPowerPolicy.shouldRelease(enabled, visible, playing, prepared))
            }
    }
    @Test fun softwareTracksCannotTunnel() {
        assertTrue(PlaybackPowerPolicy.allowTunneling(true, false))
        assertFalse(PlaybackPowerPolicy.allowTunneling(true, true))
        assertFalse(PlaybackPowerPolicy.allowTunneling(false, false))
    }
    @Test fun wakeLocksRequireRecognizedCompleteOutput() {
        assertNull(BenchmarkWakeLocks.count("Permission Denial", 10123))
        assertNull(BenchmarkWakeLocks.count("unsupported vendor output", 10123))
        assertNull(BenchmarkWakeLocks.count("Wake Locks: size=1\n", 10123))
        assertEquals(0, BenchmarkWakeLocks.count("Wake Locks: size=0\n\nSuspend Blockers:", 10123))
        val dump = "Wake Locks: size=2\n  PARTIAL_WAKE_LOCK 'ExoPlayer:WakeLock' ACQ uid=10123 pid=22\n  PARTIAL_WAKE_LOCK 'Other' ACQ uid=10124 pid=23\n\nSuspend Blockers:"
        assertEquals(1, BenchmarkWakeLocks.count(dump, 10123))
        assertEquals(0, BenchmarkWakeLocks.count(dump, 10124))
    }
}
