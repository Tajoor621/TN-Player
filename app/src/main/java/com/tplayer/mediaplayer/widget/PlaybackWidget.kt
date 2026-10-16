@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.os.*
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.*
import com.tplayer.mediaplayer.R
import com.tplayer.mediaplayer.playback.MediaPlaybackService
import com.tplayer.mediaplayer.ui.MainActivity
import java.util.concurrent.atomic.AtomicBoolean

object PlaybackActions {
    fun pending(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(context, action.hashCode(),
        Intent(context, PlaybackActionReceiver::class.java).setAction("com.tplayer.$action"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
class PlaybackActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action?.removePrefix("com.tplayer.") ?: return
        if (action !in setOf("TOGGLE_PLAYBACK", "PREVIOUS", "NEXT")) return
        val pending = goAsync(); val done = AtomicBoolean(false)
        val future = MediaController.Builder(context.applicationContext,
            SessionToken(context, ComponentName(context, MediaPlaybackService::class.java))).buildAsync()
        val handler = Handler(Looper.getMainLooper())
        fun finish() { if (done.compareAndSet(false, true)) { MediaController.releaseFuture(future); pending.finish() } }
        val timeout = Runnable { finish() }; handler.postDelayed(timeout, 8000)
        future.addListener({
            if (done.get()) return@addListener
            try {
                val player = future.get()
                if (action == "TOGGLE_PLAYBACK") {
                    val result = player.sendCustomCommand(SessionCommand("TOGGLE_PLAYBACK", Bundle.EMPTY), Bundle.EMPTY)
                    result.addListener({ handler.removeCallbacks(timeout); finish() }, ContextCompat.getMainExecutor(context))
                } else {
                    if (action == "PREVIOUS" && player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS)) player.seekToPrevious()
                    if (action == "NEXT" && player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT)) player.seekToNext()
                    handler.post { handler.removeCallbacks(timeout); finish() }
                }
            } catch (_: Exception) { handler.removeCallbacks(timeout); finish() }
        }, ContextCompat.getMainExecutor(context))
    }
}
class PlaybackWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, null, ids)
    }
    companion object {
        fun render(context: Context, player: Player?, targets: IntArray? = null) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = targets ?: manager.getAppWidgetIds(ComponentName(context, PlaybackWidget::class.java))
            if (ids.isEmpty()) return
            val title = player?.mediaMetadata?.title?.toString()?.replace(Regex("(?i)(?:https?|rtsp)://\\S+"), "[network source]")?.take(128) ?: "Open TN-Player to choose media"
            val views = RemoteViews(context.packageName, R.layout.playback_widget)
            views.setTextViewText(R.id.widget_title, title)
            views.setImageViewResource(R.id.widget_play, if (player?.isPlaying == true) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            views.setContentDescription(R.id.widget_play, if (player?.isPlaying == true) "Pause" else "Resume saved playback")
            views.setOnClickPendingIntent(R.id.widget_play, PlaybackActions.pending(context, "TOGGLE_PLAYBACK"))
            views.setOnClickPendingIntent(R.id.widget_previous, PlaybackActions.pending(context, "PREVIOUS"))
            views.setOnClickPendingIntent(R.id.widget_next, PlaybackActions.pending(context, "NEXT"))
            views.setOnClickPendingIntent(R.id.widget_title, PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            val duration = player?.duration?.takeIf { it > 0 } ?: 0
            views.setProgressBar(R.id.widget_progress, 100, if (duration > 0) ((player!!.currentPosition.coerceAtLeast(0).toDouble() / duration) * 100).toInt().coerceIn(0, 100) else 0, false)
            manager.updateAppWidget(ids, views)
        }
    }
}
