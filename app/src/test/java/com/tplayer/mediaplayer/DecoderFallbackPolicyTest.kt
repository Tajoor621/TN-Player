package com.tplayer.mediaplayer

import com.tplayer.mediaplayer.playback.CodecLabels
import com.tplayer.mediaplayer.playback.DecoderFallbackPolicy
import org.junit.Assert.*
import org.junit.Test

class DecoderFallbackPolicyTest {
    @Test fun hardwareFailureCanRetryOnlyOnceForTheSameTrack() {
        val policy = DecoderFallbackPolicy()
        assertTrue(policy.claim("file", "EAC3:audio-1", true, true, true, false))
        assertFalse(policy.claim("file", "EAC3:audio-1", true, true, true, false))
        assertTrue(policy.claim("file", "DTS:audio-2", true, true, true, false))
    }
    @Test fun sourceFailuresEncryptedTracksAndUnavailableExtensionsAreNotRetried() {
        val policy = DecoderFallbackPolicy()
        assertFalse(policy.claim("file", "EAC3", false, true, true, false))
        assertFalse(policy.claim("file", "EAC3", true, true, true, true))
        assertFalse(policy.claim("file", "EAC3", true, true, false, false))
        assertTrue(policy.claim("file", "EAC3", true, true, true, false))
    }
    @Test fun extensionFailuresCannotRetryThemselvesAndNewFilesStartFresh() {
        val policy = DecoderFallbackPolicy()
        assertFalse(policy.claim("file", "VP9", true, false, true, false))
        assertTrue(policy.claim("file", "VP9", true, true, true, false))
        assertTrue(policy.claim("other", "VP9", true, true, true, false))
        policy.reset("file")
        assertTrue(policy.claim("file", "VP9", true, true, true, false))
    }
    @Test fun adaptiveTrackChurnHasABoundedRetryBudget() {
        val policy = DecoderFallbackPolicy()
        repeat(32) { assertTrue(policy.claim("file", "track-$it", true, true, true, false)) }
        assertFalse(policy.claim("file", "track-33", true, true, true, false))
    }
    @Test fun unsupportedMessagesNameTheCodecAndSanitizeUnknownMetadata() {
        assertEquals("EAC3 audio is not supported on this device.", CodecLabels.unsupported("audio/eac3"))
        assertEquals("DTS audio is not supported on this device.", CodecLabels.unsupported("audio/vnd.dts"))
        assertEquals("AV1 video is not supported on this device.", CodecLabels.unsupported("video/av01"))
        assertEquals("foo", CodecLabels.name(null, "foo\n\u0000"))
        assertEquals(160, CodecLabels.name(null, "x".repeat(500)).length)
    }
}
