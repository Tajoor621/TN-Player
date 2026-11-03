@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer

import android.os.Looper
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.text.TextOutput
import com.tplayer.mediaplayer.playback.CueHistory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class SubtitleTimingTest {
    private fun cue(text: String, timeUs: Long) = CueGroup(listOf(Cue.Builder().setText(text).build()), timeUs)
    @Test fun delayedCaptionsCanReplayEarlierDecodedCuesWithoutChangingAv() {
        val emitted = mutableListOf<CueGroup>()
        val history = CueHistory(object : TextOutput { override fun onCues(cueGroup: CueGroup) { emitted += cueGroup } }, Looper.getMainLooper())
        history.onCues(cue("one", 1000000)); history.onCues(cue("two", 2000000))
        history.emit(1500000, 1000000); shadowOf(Looper.getMainLooper()).idle()
        assertEquals("one", emitted.last().cues.single().text.toString())
        assertEquals(2000000L, emitted.last().presentationTimeUs)
        history.emit(2500000, 0); shadowOf(Looper.getMainLooper()).idle()
        assertEquals("two", emitted.last().cues.single().text.toString())
    }
    @Test fun negativeOffsetCanEmitADecodedFutureCueAtEarlierPresentationTime() {
        val emitted = mutableListOf<CueGroup>()
        val history = CueHistory(object : TextOutput { override fun onCues(cueGroup: CueGroup) { emitted += cueGroup } }, Looper.getMainLooper())
        history.onCues(cue("ahead", 2000000)); history.emit(2000000, -100000)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1900000L, emitted.last().presentationTimeUs)
    }
    @Test fun resetDiscardsQueuedCuesFromPreviousFileOrSeek() {
        val emitted = mutableListOf<CueGroup>()
        val history = CueHistory(object : TextOutput { override fun onCues(cueGroup: CueGroup) { emitted += cueGroup } }, Looper.getMainLooper())
        history.onCues(cue("old file", 0)); history.emit(1000, 0); history.reset()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(emitted.all { it.cues.isEmpty() })
    }
}
