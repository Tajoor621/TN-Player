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
    @Test fun activeCuesHandleOverlapBoundariesAndPausedSeekPositions() {
        val first = SubtitleCue(1000, 4000, "first"); val second = SubtitleCue(2000, 3000, "second")
        val doc = SubtitleDocument(listOf(first, second, SubtitleCue(20000, 24000, "third")))
        assertTrue(doc.activeAt(999, 0).isEmpty())
        assertEquals(listOf(first), doc.activeAt(1000, 0))
        assertEquals(listOf(first, second), doc.activeAt(2500, 0))
        assertEquals(listOf(first), doc.activeAt(3000, 0))
        assertTrue(doc.activeAt(4000, 0).isEmpty())
        assertTrue(doc.activeAt(20500, 1000).isEmpty())
        assertEquals("third", doc.activeAt(21500, 1000).single().text)
        assertEquals(listOf(first, second), doc.activeAt(2500, 0)) // backward seek after a later cue
    }
    @Test fun rateCorrectionKeepsDisplayNavigationAndExportOnTheSameTimeline() {
        val doc = SubtitleDocument(listOf(SubtitleCue(10000, 12000, "first"), SubtitleCue(20000, 24000, "second")))
        assertEquals("second", doc.activeAt(11500, 1000, 2000).single().text)
        assertEquals(11000L, doc.adjacent(7000, 1, 1000, 2000))
        val exported = SubtitleImport.parse(doc.srt(1000, 2000), "adjusted.srt")
        assertEquals(listOf(6000L, 11000L), exported.cues.map { it.start })
        assertEquals(listOf(7000L, 13000L), exported.cues.map { it.end })
        assertEquals("second", exported.activeAt(11500, 0).single().text)
        rejects { doc.srt(0, 0) }
        rejects { SubtitleDocument(listOf(SubtitleCue(600000000, 604800000, "Late"))).srt(600000) }
    }
    @Test fun webVttMetadataNeverBecomesVisibleCaptionText() {
        val value = "WEBVTT\n\nNOTE hidden diagnostic\n00:01.000 --> 00:02.000\nNot a caption\n\nSTYLE\n::cue { color: cyan; }\n\n00:03.000 --> 00:04.000\nVisible caption\n"
        assertEquals(listOf(SubtitleCue(3000, 4000, "Visible caption")), SubtitleImport.parse(value, "captions.vtt").cues)
        rejects { SubtitleImport.parse("bad --> timing\nText", "bad.srt") }
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
