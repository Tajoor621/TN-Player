package com.tplayer.mediaplayer

import com.tplayer.mediaplayer.subtitle.*
import org.junit.Assert.*
import org.junit.Test

class SubtitleImportTest {
    @Test fun delayClipsExpiredCuesAndNavigationUsesShiftedSortedTimes() {
        val document = SubtitleImport.parse("2\n00:00:04,000 --> 00:00:06,000\nSecond\n\n1\n00:00:00,500 --> 00:00:01,500\nFirst\n", "captions.srt")
        assertEquals(listOf(500L, 4000L), document.cues.map { it.start })
        assertEquals(4500L, document.adjacent(1000, 1, 500))
        assertEquals(1000L, document.adjacent(4500, -1, 500))
        assertNull(document.adjacent(4500, 1, 500))
        val shifted = SubtitleImport.parse(document.srt(-1000), "shifted.srt")
        assertEquals(0L, shifted.cues.first().start)
        assertEquals(500L, shifted.cues.first().end)
        assertEquals(1, SubtitleImport.parse(document.srt(-2000), "shifted.srt").cues.size)
    }
    @Test fun legacyTextFormatsNormalizeToTheSameTimeline() {
        val fixtures = mapOf(
            "a.srt" to "1\n00:00:01,000 --> 00:00:03,000\nHello\nworld\n",
            "a.vtt" to "WEBVTT\n\ncaption-id\n00:01.000 --> 00:03.000 align:center\nHello\nworld\n",
            "a.mpl" to "[10][30]Hello|world",
            "a.pjs" to "10,30,\"Hello|world\"",
            "a.sub" to "{1}{1}25\n{25}{75}Hello|world",
            "subviewer.sub" to "[SUBTITLE]\n00:00:01.00,00:00:03.00\nHello[br]world\n"
        )
        for ((name, value) in fixtures) {
            val cue = SubtitleImport.parse(value, name).cues.single()
            assertEquals(name, SubtitleCue(1000, 3000, "Hello\nworld"), cue)
            assertEquals(cue, SubtitleImport.parse(SubtitleDocument(listOf(cue)).srt(0), "roundtrip.srt").cues.single())
        }
        val tm = SubtitleImport.parse("0:00:01:Hello|world\n0:00:03:Next", "a.txt")
        assertEquals(SubtitleCue(1000, 3000, "Hello\nworld"), tm.cues.first())
        assertEquals(8000L, tm.cues.last().end)
    }
    @Test fun decodesBomMarkedUtf16AndRejectsMalformedOrOversizedBytes() {
        val text = "1\n00:00:01,000 --> 00:00:03,000\n字幕\n"
        val bytes = byteArrayOf(0xff.toByte(), 0xfe.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        assertEquals(text, SubtitleImport.read(bytes.inputStream()))
        rejects { SubtitleImport.read(byteArrayOf(0xc3.toByte(), 0x28).inputStream()) }
        rejects { SubtitleImport.read(ByteArray(SubtitleImport.MAX_BYTES + 1).inputStream()) }
    }
    @Test fun rejectsAmbiguousFrameRateAndInvalidTimesBeforePublishing() {
        rejects { SubtitleImport.parse("{25}{75}Guess my frame rate", "bad.sub") }
        rejects { SubtitleImport.parse("{1}{1}NaN\n{25}{75}Bad", "bad.sub") }
        rejects { SubtitleImport.parse("[20][10]Backwards", "bad.mpl") }
        rejects { SubtitleImport.parse("[184467440737095517][184467440737095518]Overflow", "bad.mpl") }
        rejects { SubtitleImport.parse("1\n00:99:01,000 --> 00:99:03,000\nBad", "bad.srt") }
        rejects { SubtitleImport.parse("0:00:02:a\n0:00:01:b", "bad.txt") }
        rejects { SubtitleImport.parse("not a subtitle", "bad.txt") }
        rejects { SubtitleImport.parse("\u0000binary", "bad.sub") }
        rejects { SubtitleDocument(listOf(SubtitleCue(0, 1000, "text"))).srt(600001) }
        rejects { SubtitleImport.parse((1..20001).joinToString("\n") { "[10][30]cue" }, "large.mpl") }
    }
    private fun rejects(action: () -> Unit) {
        try { action(); fail("Invalid subtitle was accepted") } catch (_: Exception) { }
    }
}
