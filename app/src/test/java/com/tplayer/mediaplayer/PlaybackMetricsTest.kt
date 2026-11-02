@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer

import androidx.media3.common.Timeline
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.tplayer.mediaplayer.playback.PlaybackPerformance
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PlaybackMetricsTest {
    private val event = AnalyticsListener.EventTime(0, Timeline.EMPTY, 0, null, 0, Timeline.EMPTY, 0, null, 0, 0)
    @Test fun reusedDecoderCountersExcludeFramesDroppedBeforeThisOpen() {
        val metrics = PlaybackPerformance()
        val counters = DecoderCounters().apply { droppedBufferCount = 12 }
        metrics.onVideoEnabled(event, counters)
        metrics.begin("next-file")
        counters.droppedBufferCount = 15
        assertEquals(3, metrics.snapshot().getInt("dropped_frames"))
        metrics.begin("third-file")
        counters.droppedBufferCount = 17
        assertEquals(2, metrics.snapshot().getInt("dropped_frames"))
    }
    @Test fun aNewDecoderStartsItsOwnDropCounterAndReleaseBalancesIt() {
        val metrics = PlaybackPerformance()
        metrics.onVideoEnabled(event, DecoderCounters().apply { droppedBufferCount = 12 })
        metrics.begin("next-file")
        metrics.onVideoEnabled(event, DecoderCounters().apply { droppedBufferCount = 2 })
        metrics.onVideoDecoderInitialized(event, "test-decoder", 0, 0)
        assertEquals(2, metrics.snapshot().getInt("dropped_frames"))
        assertEquals(1, metrics.snapshot().getInt("active_video"))
        metrics.onVideoDecoderReleased(event, "test-decoder")
        assertEquals(0, metrics.snapshot().getInt("active_video"))
    }
}
