@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer.ui

import android.app.PictureInPictureParams
import android.content.*
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.*
import android.provider.OpenableColumns
import android.util.Rational
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.*
import androidx.media3.session.*
import androidx.media3.session.MediaController
import androidx.media3.ui.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.ListenableFuture
import com.tplayer.mediaplayer.R
import com.tplayer.mediaplayer.data.*
import com.tplayer.mediaplayer.playback.MediaPlaybackService
import com.tplayer.mediaplayer.playback.PlaybackPolicy
import kotlinx.coroutines.*

class PlayerActivity : AppCompatActivity() {
    private lateinit var video: PlayerView
    private lateinit var gestures: PlayerGestures
    private lateinit var top: LinearLayout
    private lateinit var title: TextView
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingIntent: Intent? = null
    private var pendingSubtitle: Uri? = null
    private var loopA: Long? = null
    private var locked = false
    private var resumeRequested = false
    private val dao by lazy { MediaDatabase.get(this).media() }
    private val settings by lazy { Settings(this) }
    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            info("Unable to play", "${error.errorCodeName}\n\nThis file may use an unsupported codec, be damaged, require DRM, or be unreachable. Try another source. Universal FFmpeg decoding is not included.")
        }
        override fun onEvents(player: Player, events: Player.Events) {
            title.text = player.mediaMetadata.title?.toString() ?: "TN-Player"
            if (resumeRequested && player.mediaItemCount > 0) { resumeRequested = false; startPlayback(player) }
            updateAwake(); updatePip()
        }
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) { loopA = null }
    }
    private val subtitlePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) { }
            if (controller == null) pendingSubtitle = uri else attachSubtitle(uri)
        }
    }
    private fun sendCommand(player: MediaController, action: String, args: Bundle, success: String) {
        val result = player.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
        result.addListener({
            if (!isFinishing && !isDestroyed) {
                val accepted = try { result.get().resultCode == SessionResult.RESULT_SUCCESS } catch (_: Exception) { false }
                if (accepted) Toast.makeText(this, success, Toast.LENGTH_SHORT).show()
                else info("Playback setting", "The player could not apply this setting. Check that the media is still available and supports this action.")
            }
        }, ContextCompat.getMainExecutor(this))
    }
    private fun launchUi(action: suspend CoroutineScope.() -> Unit): Job = lifecycleScope.launch {
        try { action() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { info("Action unavailable", e.message ?: "Check storage access and try again.") }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        if (settings.amoled) setTheme(R.style.Theme_TNPlayer_Amoled)
        super.onCreate(savedInstanceState)
        pendingIntent = if (savedInstanceState?.getBoolean("consumed") == true) null else intent
        locked = savedInstanceState?.getBoolean("locked") ?: false
        resumeRequested = savedInstanceState?.getBoolean("resumeRequested") ?: false
        val frame = FrameLayout(this)
        video = (layoutInflater.inflate(R.layout.player_surface, frame, false) as PlayerView).apply {
            setShowSubtitleButton(true); setShowNextButton(true); setShowPreviousButton(true)
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            controllerShowTimeoutMs = 4000
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        frame.addView(video, FrameLayout.LayoutParams(-1, -1))
        video.resizeMode = intArrayOf(AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_FILL, AspectRatioFrameLayout.RESIZE_MODE_ZOOM)[settings.aspectRatio]
        applySubtitleStyle()
        val gestureOverlay = TextView(this).apply {
            textSize = 20f; setTextColor(-1); setBackgroundColor(0xCC081D21.toInt())
            setPadding(dp(18), dp(12), dp(18), dp(12)); visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        frame.addView(gestureOverlay, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        gestures = PlayerGestures(this, video, gestureOverlay, { controller },
            { settings.gestures && !locked && !inPip() }, { settings.seekMs })
        top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(4))
            setBackgroundColor(0xCC081D21.toInt())
        }
        top.addView(button("Back") { finish() })
        title = TextView(this).apply { text = "TN-Player"; textSize = 16f; setTextColor(-1); maxLines = 2 }
        top.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(button("PiP") { enterPip() })
        top.addView(button("More") { menu() })
        frame.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        setContentView(frame)
        video.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            if (!inPip()) top.visibility = if (locked) View.GONE else visibility
        })
        video.setOnLongClickListener {
            if (locked) { locked = false; video.useController = true; video.showController(); top.visibility = View.VISIBLE; Toast.makeText(this, "Controls unlocked", Toast.LENGTH_SHORT).show() }
            true
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("consumed", pendingIntent == null); outState.putBoolean("locked", locked); outState.putBoolean("resumeRequested", resumeRequested)
        super.onSaveInstanceState(outState)
    }
    private fun startPlayback(player: Player) {
        if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }
    private fun updateAwake() {
        if (controller?.isPlaying == true) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); pendingIntent = intent
        if (controller != null) consumeIntent()
    }
    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, MediaPlaybackService::class.java))
        val connecting = MediaController.Builder(this, token).buildAsync()
        future = connecting
        connecting.addListener({
            if (future !== connecting) return@addListener
            try {
                val player = connecting.get(); controller = player; video.player = player
                player.addListener(listener); consumeIntent()
                pendingSubtitle?.let { attachSubtitle(it); pendingSubtitle = null }
                title.text = player.mediaMetadata.title?.toString() ?: "TN-Player"; updateAwake(); updatePip()
                if (locked) { video.useController = false; top.visibility = View.GONE }
            } catch (e: Exception) { info("Player unavailable", e.cause?.message ?: e.message ?: "Cannot connect to playback service.") }
        }, ContextCompat.getMainExecutor(this))
    }
    override fun onStop() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        video.player = null
        controller?.removeListener(listener); controller = null
        future?.let { MediaController.releaseFuture(it) }; future = null
        super.onStop()
    }
    private fun consumeIntent() {
        val source = pendingIntent ?: return
        try { consumeSource(source) }
        catch (_: android.os.BadParcelableException) { pendingIntent = null; info("Invalid media request", "The sharing app supplied invalid media data.") }
        catch (_: ClassCastException) { pendingIntent = null; info("Invalid media request", "Choose a valid media file or stream.") }
        catch (_: IllegalArgumentException) { pendingIntent = null; info("Invalid media request", "The playlist or source is invalid or too large.") }
    }
    private fun consumeSource(source: Intent) {
        val player = controller ?: return
        pendingIntent = null
        val queue = source.getStringArrayListExtra("queue")
        val titles = source.getStringArrayListExtra("titles")
        val mimes = source.getStringArrayListExtra("mimes")
        if (!queue.isNullOrEmpty()) {
            require(queue.size <= 1000)
            val items = queue.mapIndexedNotNull { index, value -> makeItem(Uri.parse(value), titles?.getOrNull(index) ?: "Media", mimes?.getOrNull(index)) }
            if (items.isNotEmpty()) { player.setMediaItems(items); player.prepare(); player.play() }
            return
        }
        if (source.action == Intent.ACTION_SEND_MULTIPLE) {
            @Suppress("DEPRECATION")
            val shared = source.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            require(shared.size in 1..1000)
            val items = shared.mapNotNull { uri ->
                require(uri.scheme?.lowercase() in setOf("content", "file"))
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) { }
                makeItem(uri, displayName(uri), source.type?.takeUnless { it.contains('*') })
            }
            if (items.isNotEmpty()) { resumeRequested = false; player.setMediaItems(items); player.prepare(); player.play() }
            return
        }
        @Suppress("DEPRECATION")
        val uri = source.data ?: source.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (uri == null) {
            if (source.getBooleanExtra("resume", false)) {
                if (player.mediaItemCount > 0) startPlayback(player) else resumeRequested = true
            }
            return
        }
        val item = makeItem(uri, source.getStringExtra("title") ?: displayName(uri), source.getStringExtra("mime")?.ifBlank { source.type } ?: source.type) ?: return
        resumeRequested = false
        if (player.currentMediaItem?.mediaId == item.mediaId && player.mediaMetadata.extras?.getString("mime").orEmpty() == item.mediaMetadata.extras?.getString("mime").orEmpty()) { startPlayback(player); return }
        player.setMediaItem(item); player.prepare(); player.play()
    }
    private fun makeItem(uri: Uri, name: String, mime: String? = null): MediaItem? {
        require(uri.toString().length <= 32768)
        val scheme = uri.scheme?.lowercase()
        if (scheme !in setOf("content", "file", "https", "rtsp")) {
            info("Unsupported source", "Use a local file, content URI, HTTPS media URL or RTSP stream. HTTP is blocked by the secure default.")
            return null
        }
        if (scheme == "https" || scheme == "rtsp") {
            PlaybackPolicy.validateStream(uri.toString())?.let { info("Invalid stream", it); return null }
        }
        return MediaItem.Builder().setUri(uri).setMediaId(uri.toString()).setMimeType(mime?.ifBlank { null })
            .setMediaMetadata(MediaMetadata.Builder().setTitle(name).setExtras(Bundle().apply { putString("mime", mime.orEmpty()) }).build()).build()
    }
    private fun displayName(uri: Uri): String {
        if (uri.scheme?.lowercase() in setOf("https", "rtsp")) return uri.host ?: "Network stream"
        if (uri.scheme == "content") try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) return it.getString(0) ?: "Media"
            }
        } catch (_: Exception) { }
        return uri.lastPathSegment ?: "Media"
    }
    private fun menu() {
        val player = controller ?: return
        val choices = arrayOf("Playback speed", "Aspect ratio", "External subtitle", "Subtitle size", "Add/remove favorite", "Add bookmark", "Bookmarks", "Set A–B repeat", "Clear A–B repeat", "Loop / shuffle", "Sleep timer", "Rotate screen", "Lock controls", "Stop playback", "Subtitle style", "Audio enhancements", "Reset zoom")
        MaterialAlertDialogBuilder(this).setTitle("Playback controls").setItems(choices) { _, index ->
            when (index) {
                0 -> { val values = floatArrayOf(.25f, .5f, .75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f)
                    MaterialAlertDialogBuilder(this).setTitle("Playback speed").setItems(values.map { "${it}×" }.toTypedArray()) { _, i -> player.setPlaybackSpeed(values[i]) }.show() }
                1 -> MaterialAlertDialogBuilder(this).setTitle("Aspect ratio").setItems(arrayOf("Fit", "Fill / stretch", "Crop / zoom")) { _, i -> settings.aspectRatio = i; video.resizeMode = intArrayOf(AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_FILL, AspectRatioFrameLayout.RESIZE_MODE_ZOOM)[i] }.show()
                2 -> subtitlePicker.launch(arrayOf("*/*"))
                3 -> MaterialAlertDialogBuilder(this).setTitle("Subtitle size").setItems(arrayOf("Small", "Medium", "Large")) { _, i -> settings.subtitleSize = i; applySubtitleStyle() }.show()
                4 -> launchUi {
                    val item = player.currentMediaItem ?: return@launchUi
                    if (dao.isFavorite(item.mediaId)) { dao.removeFavorite(item.mediaId); Toast.makeText(this@PlayerActivity, "Favorite removed", Toast.LENGTH_SHORT).show() }
                    else { dao.addFavorite(Favorite(item.mediaId, title.text.toString(), player.mediaMetadata.extras?.getString("mime").orEmpty())); Toast.makeText(this@PlayerActivity, "Favorite saved", Toast.LENGTH_SHORT).show() }
                }
                5 -> launchUi { val item = player.currentMediaItem ?: return@launchUi; dao.addBookmark(Bookmark(uri = item.mediaId, title = title.text.toString(), position = player.currentPosition)); Toast.makeText(this@PlayerActivity, "Bookmark saved", Toast.LENGTH_SHORT).show() }
                6 -> launchUi {
                    val values = dao.bookmarks(player.currentMediaItem?.mediaId ?: "")
                    if (values.isEmpty()) info("Bookmarks", "No bookmarks for this file yet.")
                    else MaterialAlertDialogBuilder(this@PlayerActivity).setTitle("Bookmarks").setItems(values.map { "${it.position / 60000}:${((it.position / 1000) % 60).toString().padStart(2, '0')}" }.toTypedArray()) { _, i -> player.seekTo(values[i].position) }.show()
                }
                7 -> {
                    if (!player.isCurrentMediaItemSeekable) { info("A–B repeat", "This stream does not allow seeking."); return@setItems }
                    val a = loopA
                    if (a == null) { loopA = player.currentPosition; Toast.makeText(this, "A set. Seek forward and select Set A–B repeat again.", Toast.LENGTH_LONG).show() }
                    else if (PlaybackPolicy.validLoop(a, player.currentPosition)) {
                        sendCommand(player, "SET_LOOP", Bundle().apply { putLong("a", a); putLong("b", player.currentPosition) }, "A–B repeat enabled"); loopA = null
                    } else info("A–B repeat", "B must be at least half a second after A.")
                }
                8 -> { loopA = null; player.sendCustomCommand(SessionCommand("SET_LOOP", Bundle.EMPTY), Bundle().apply { putLong("a", -1); putLong("b", -1) }) }
                9 -> MaterialAlertDialogBuilder(this).setTitle("Loop / shuffle").setItems(arrayOf("Off", "Repeat one", "Repeat all", "Shuffle")) { _, i ->
                    player.repeatMode = when (i) { 1 -> Player.REPEAT_MODE_ONE; 2 -> Player.REPEAT_MODE_ALL; else -> Player.REPEAT_MODE_OFF }; player.shuffleModeEnabled = i == 3
                }.show()
                10 -> MaterialAlertDialogBuilder(this).setTitle("Sleep timer").setItems(arrayOf("Off", "15 minutes", "30 minutes", "60 minutes")) { _, i ->
                    sendCommand(player, "SLEEP_TIMER", Bundle().apply { putLong("minutes", longArrayOf(0, 15, 30, 60)[i]) }, "Sleep timer updated")
                }.show()
                11 -> requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                12 -> { locked = true; top.visibility = View.GONE; video.useController = false; Toast.makeText(this, "Long press video to unlock controls", Toast.LENGTH_LONG).show() }
                13 -> { player.stop(); player.clearMediaItems(); finish() }
                14 -> subtitleStyleMenu()
                15 -> audioEnhancements(player)
                16 -> gestures.resetZoom()
            }
        }.show()
    }
    private fun applySubtitleStyle() {
        video.subtitleView?.apply {
            setApplyEmbeddedStyles(false)
            setFractionalTextSize(floatArrayOf(.035f, .0533f, .075f)[settings.subtitleSize])
            setBottomPaddingFraction(settings.subtitlePosition / 100f)
            setStyle(CaptionStyleCompat(settings.subtitleColor,
                if (settings.subtitleBackground) 0xB0000000.toInt() else android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK,
                arrayOf(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.SERIF, android.graphics.Typeface.MONOSPACE)[settings.subtitleFont]))
        }
    }
    private fun subtitleStyleMenu() {
        MaterialAlertDialogBuilder(this).setTitle("Subtitle style").setItems(arrayOf("Text size", "Text color", "Typeface", "Toggle background", "Position")) { _, choice ->
            when (choice) {
                0 -> MaterialAlertDialogBuilder(this).setTitle("Text size").setSingleChoiceItems(arrayOf("Small", "Medium", "Large"), settings.subtitleSize) { dialog, index -> settings.subtitleSize = index; applySubtitleStyle(); dialog.dismiss() }.show()
                1 -> MaterialAlertDialogBuilder(this).setTitle("Text color").setItems(arrayOf("White", "Yellow", "Cyan")) { _, index -> settings.subtitleColor = intArrayOf(-1, -256, -16711681)[index]; applySubtitleStyle() }.show()
                2 -> MaterialAlertDialogBuilder(this).setTitle("Typeface").setSingleChoiceItems(arrayOf("Sans", "Serif", "Monospace"), settings.subtitleFont) { dialog, index -> settings.subtitleFont = index; applySubtitleStyle(); dialog.dismiss() }.show()
                3 -> { settings.subtitleBackground = !settings.subtitleBackground; applySubtitleStyle() }
                4 -> MaterialAlertDialogBuilder(this).setTitle("Position").setItems(arrayOf("Low", "Raised", "High")) { _, index -> settings.subtitlePosition = intArrayOf(8, 16, 28)[index]; applySubtitleStyle() }.show()
            }
        }.show()
    }
    private fun audioEnhancements(player: MediaController) {
        val result = player.sendCustomCommand(SessionCommand("AUDIO_EFFECTS_STATE", Bundle.EMPTY), Bundle.EMPTY)
        result.addListener({
            if (isFinishing || isDestroyed) return@addListener
            val response = try { result.get() } catch (_: Exception) { null }
            if (response?.resultCode != SessionResult.RESULT_SUCCESS) { info("Audio enhancements", "Effects are unavailable for this audio output."); return@addListener }
            val state = response.extras
            if (!state.getBoolean("eq") && !state.getBoolean("bass") && !state.getBoolean("virtualizer")) { info("Audio enhancements", "Start audio playback first. Device effects may be unavailable for this output or passthrough path."); return@addListener }
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(12)) }
            val enabled = CheckBox(this).apply { text = "Enable equalizer"; isChecked = state.getBoolean("enabled"); isEnabled = state.getBoolean("eq") }
            box.addView(enabled)
            val frequencies = state.getIntArray("frequencies") ?: intArrayOf()
            val levels = state.getIntArray("levels") ?: intArrayOf()
            val low = state.getInt("min"); val high = state.getInt("max")
            fun slider(label: String, min: Int, max: Int, value: Int): SeekBar {
                val heading = TextView(this).apply { text = "$label: $value" }; box.addView(heading)
                val seek = SeekBar(this).apply { this.max = (max - min).coerceAtLeast(1); progress = (value - min).coerceIn(0, this.max); contentDescription = label; minimumHeight = dp(48) }
                seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) { heading.text = "$label: ${progress + min}" }
                    override fun onStartTrackingTouch(bar: SeekBar) {}
                    override fun onStopTrackingTouch(bar: SeekBar) {}
                })
                box.addView(seek); return seek
            }
            val bands = frequencies.indices.map { index -> slider("${frequencies[index]} Hz · millibels", low, high, levels.getOrElse(index) { 0 }) }
            box.addView(button("Reset bands to flat") { bands.forEach { it.progress = -low } })
            val bass = if (state.getBoolean("bass")) slider("Bass strength", 0, 1000, state.getInt("bassStrength")) else null
            val virtual = if (state.getBoolean("virtualizer")) slider("Virtualizer strength", 0, 1000, state.getInt("virtualizerStrength")) else null
            MaterialAlertDialogBuilder(this).setTitle("Audio · ${bands.size} device EQ bands")
                .setView(ScrollView(this).apply { addView(box) }).setNegativeButton("Cancel", null)
                .setPositiveButton("Apply") { _, _ ->
                    sendCommand(player, "AUDIO_EFFECTS_SET", Bundle().apply {
                        putBoolean("enabled", enabled.isChecked); putIntArray("levels", bands.map { it.progress + low }.toIntArray())
                        putInt("bassStrength", bass?.progress ?: 0); putInt("virtualizerStrength", virtual?.progress ?: 0)
                    }, "Audio effects applied")
                }.show()
        }, ContextCompat.getMainExecutor(this))
    }
    private fun attachSubtitle(uri: Uri) {
        val player = controller ?: return
        val item = player.currentMediaItem ?: return
        val name = displayName(uri).lowercase()
        val mime = when {
            name.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
            name.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            else -> { info("Subtitle format", "This build accepts external SRT and WebVTT files."); return }
        }
        val subtitle = MediaItem.SubtitleConfiguration.Builder(uri).setMimeType(mime)
            .setLanguage("und").setLabel(displayName(uri)).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        val position = player.currentPosition; val playing = player.playWhenReady
        player.replaceMediaItem(player.currentMediaItemIndex, item.buildUpon().setUri(item.mediaId).setSubtitleConfigurations(listOf(subtitle)).build())
        player.seekTo(position); player.prepare(); player.playWhenReady = playing
    }
    private fun inPip() = Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode
    @androidx.annotation.ChecksSdkIntAtLeast(api = 26)
    private fun pipSupported() = Build.VERSION.SDK_INT >= 26 && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    @androidx.annotation.RequiresApi(26)
    private fun pipParams(): PictureInPictureParams {
        val player = controller
        val size = player?.videoSize
        val width = (size?.width ?: 0).takeIf { it > 0 } ?: 16
        val height = (size?.height ?: 0).takeIf { it > 0 } ?: 9
        val ratio = (width.toFloat() / height).coerceIn(0.42f, 2.39f)
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational((ratio * 1000).toInt(), 1000))
        if (player != null) {
            val specs = listOf(Triple("PREVIOUS", "Previous", android.R.drawable.ic_media_previous),
                Triple("TOGGLE_PLAYBACK", if (player.isPlaying) "Pause" else "Play", if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play),
                Triple("NEXT", "Next", android.R.drawable.ic_media_next))
            builder.setActions(specs.map { (action, label, icon) -> android.app.RemoteAction(
                android.graphics.drawable.Icon.createWithResource(this, icon), label, label,
                com.tplayer.mediaplayer.widget.PlaybackActions.pending(this, action)).apply {
                    isEnabled = when (action) { "PREVIOUS" -> player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS); "NEXT" -> player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT); else -> true }
                } })
        }
        if (Build.VERSION.SDK_INT >= 31) builder.setAutoEnterEnabled(settings.autoPip && player?.isPlaying == true && player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO))
        return builder.build()
    }
    private fun updatePip() { if (pipSupported()) try { setPictureInPictureParams(pipParams()) } catch (_: IllegalArgumentException) { } }
    private fun enterPip() {
        if (!pipSupported()) { info("Picture-in-picture", "Requires Android 8+ and device support."); return }
        if (controller?.currentTracks?.isTypeSelected(C.TRACK_TYPE_VIDEO) != true) { info("Picture-in-picture", "Play a video first."); return }
        try { enterPictureInPictureMode(pipParams()) } catch (_: IllegalStateException) { info("Picture-in-picture", "Enable PiP for TN-Player in Android Settings.") }
    }
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31 && settings.autoPip && controller?.isPlaying == true && controller?.currentTracks?.isTypeSelected(C.TRACK_TYPE_VIDEO) == true) enterPip()
    }
    @androidx.annotation.RequiresApi(26)
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        top.visibility = if (isInPictureInPictureMode || locked) View.GONE else View.VISIBLE
        video.useController = !isInPictureInPictureMode && !locked
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val player = controller ?: return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> if (player.isPlaying) player.pause() else startPlayback(player)
            KeyEvent.KEYCODE_DPAD_LEFT -> player.seekTo((player.currentPosition - settings.seekMs).coerceAtLeast(0))
            KeyEvent.KEYCODE_DPAD_RIGHT -> player.seekTo(player.currentPosition + settings.seekMs)
            KeyEvent.KEYCODE_M -> player.volume = if (player.volume > 0) 0f else 1f
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }
}
