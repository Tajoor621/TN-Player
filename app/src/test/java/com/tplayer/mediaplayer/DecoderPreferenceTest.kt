package com.tplayer.mediaplayer

import com.tplayer.mediaplayer.playback.DecoderPreference
import org.junit.Assert.*
import org.junit.Test

class DecoderPreferenceTest {
    private data class Codec(val name: String, val hardware: Boolean = false, val software: Boolean = false)
    @Test fun preferenceKeepsEveryCompatibleFallbackInStableOrder() {
        val codecs = listOf(Codec("sw1", software = true), Codec("unknown"), Codec("hw1", hardware = true), Codec("sw2", software = true), Codec("hw2", hardware = true))
        fun order(mode: Int) = DecoderPreference.order(codecs, mode, { it.hardware }, { it.software }).map { it.name }
        assertEquals(listOf("sw1", "unknown", "hw1", "sw2", "hw2"), order(0))
        assertEquals(listOf("hw1", "hw2", "sw1", "unknown", "sw2"), order(1))
        assertEquals(listOf("sw1", "sw2", "unknown", "hw1", "hw2"), order(2))
        assertEquals(order(0), order(-1))
        assertTrue(DecoderPreference.order(emptyList<Codec>(), 1, { it.hardware }, { it.software }).isEmpty())
    }
}
