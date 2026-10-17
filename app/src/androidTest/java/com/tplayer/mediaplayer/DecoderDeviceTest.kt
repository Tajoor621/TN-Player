@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer

import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tplayer.mediaplayer.data.Settings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DecoderDeviceTest {
    @Test fun switchingCodecPreferenceKeepsPausedQueuePositionThenResumesVideo() {
        val settings = Settings(DevicePlayback.context); val original = settings.decoderPreference
        val uri = DevicePlayback.fixture("video-fixture.mp4")
        val scenario = DevicePlayback.launch(uri, "video/mp4")
        val player = DevicePlayback.controller()
        try {
            DevicePlayback.await("Video not ready") { DevicePlayback.main { player.isPlaying && player.playbackState == Player.STATE_READY } }
            DevicePlayback.main { player.pause(); player.seekTo(5000) }
            val result = DevicePlayback.main { player.sendCustomCommand(SessionCommand("DECODER_PREFERENCE", Bundle.EMPTY), Bundle().apply { putInt("mode", 2) }) }.get(10, TimeUnit.SECONDS)
            assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
            DevicePlayback.await("Decoder reinitialization lost paused position") { DevicePlayback.main { player.playbackState == Player.STATE_READY && !player.isPlaying && player.currentPosition in 4800..5200 } }
            assertEquals(2, settings.decoderPreference)
            assertEquals(uri.toString(), DevicePlayback.main { player.currentMediaItem?.mediaId })
            DevicePlayback.main { player.play() }
            DevicePlayback.await("Video did not resume with software preference") { DevicePlayback.main { player.isPlaying && player.currentPosition > 5300 && player.videoSize.width > 0 } }
            val invalid = DevicePlayback.main { player.sendCustomCommand(SessionCommand("DECODER_PREFERENCE", Bundle.EMPTY), Bundle().apply { putInt("mode", 99) }) }.get(10, TimeUnit.SECONDS)
            assertTrue(invalid.resultCode != SessionResult.RESULT_SUCCESS); assertEquals(2, settings.decoderPreference)
        } finally { scenario.close(); DevicePlayback.stop(player); settings.decoderPreference = original }
    }
}
