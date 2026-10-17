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
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
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
import com.tplayer.mediaplayer.subtitle.*
import java.io.File

data class ExternalCaptions(val mediaId: String, val name: String, val document: SubtitleDocument, val delayMs: Long = 0)
class PlayerUiState : ViewModel() {
    var screenshot: android.graphics.Bitmap? = null
    var captions: ExternalCaptions? = null
    var subtitleGestures = false
    var pendingSubtitle: Uri? = null
    var pendingSubtitleMediaId: String? = null
}
class PlayerActivity : AppCompatActivity() {
    private lateinit var video: PlayerView
    private lateinit var gestures: PlayerGestures
    private lateinit var top: LinearLayout
    private lateinit var title: TextView
    private lateinit var shortcuts: HorizontalScrollView
    private lateinit var bottom: LinearLayout
    private lateinit var unlock: android.view.View
    private var chromeVisibility = android.view.View.VISIBLE
    private var fullscreen = false
    private val ui by viewModels<PlayerUiState>()
    private val saveScreenshot = registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val bitmap = ui.screenshot; ui.screenshot = null
        if (uri != null && bitmap != null) launchUi {
            withContext(Dispatchers.IO) { checkNotNull(contentResolver.openOutputStream(uri, "wt")).use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) } }
            Toast.makeText(this@PlayerActivity, "Screenshot saved", Toast.LENGTH_SHORT).show()
        }
    }
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingIntent: Intent? = null
    private var inputJob: Job? = null
    private var subtitleJob: Job? = null
    private var pinRequested = false
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
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            loopA = null
            if (ui.captions?.mediaId != item?.mediaId) ui.captions = null
            if (ui.pendingSubtitleMediaId != null && ui.pendingSubtitleMediaId != item?.mediaId) { subtitleJob?.cancel(); ui.pendingSubtitle = null; ui.pendingSubtitleMediaId = null }
        }
    }
    private val subtitlePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) { }
            ui.pendingSubtitle = uri
            if (controller != null) attachSubtitle(uri)
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
        fullscreen = savedInstanceState?.getBoolean("fullscreen") ?: false
        pinRequested = savedInstanceState?.getBoolean("pinRequested") ?: false
        applyOrientation()
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
            { settings.gestures && !locked && !inPip() }, { settings.seekMs }, { settings.mirror to settings.verticalFlip },
            { ui.subtitleGestures }, { delta -> jumpCaption(delta) },
            { delta -> settings.subtitlePosition += delta; applySubtitleStyle() },
            { delta -> settings.subtitleSize += delta; applySubtitleStyle() })
        buildChrome(frame)
        setContentView(frame)
        applyFullscreen()
        video.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            chromeVisibility = visibility; updateChrome()
        })
        video.setOnLongClickListener {
            if (locked) setLocked(false)
            true
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("pinRequested", pinRequested); outState.putBoolean("fullscreen", fullscreen); outState.putBoolean("consumed", pendingIntent == null); outState.putBoolean("locked", locked); outState.putBoolean("resumeRequested", resumeRequested)
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
        super.onNewIntent(intent); inputJob?.cancel(); setIntent(intent); pendingIntent = intent
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
                ui.pendingSubtitle?.let { attachSubtitle(it) }
                title.text = player.mediaMetadata.title?.toString() ?: "TN-Player"; applyDisplay(); updateAwake(); updatePip()
                updateChrome()
            } catch (e: Exception) { info("Player unavailable", e.cause?.message ?: e.message ?: "Cannot connect to playback service.") }
        }, ContextCompat.getMainExecutor(this))
    }
    override fun onStop() {
        inputJob?.cancel(); subtitleJob?.cancel()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (!settings.backgroundPlayback && !isChangingConfigurations && !inPip()) controller?.pause()
        video.player = null
        controller?.removeListener(listener); controller = null
        future?.let { MediaController.releaseFuture(it) }; future = null
        super.onStop()
    }
    private fun consumeIntent() {
        val source = pendingIntent ?: return
        inputJob?.cancel()
        inputJob = launchUi {
            try {
                consumeSource(source)
                if (pendingIntent === source) pendingIntent = null
            } catch (e: CancellationException) { throw e }
            catch (_: android.os.BadParcelableException) { pendingIntent = null; info("Invalid media request", "The sharing app supplied invalid media data.") }
            catch (_: ClassCastException) { pendingIntent = null; info("Invalid media request", "Choose a valid media file or stream.") }
            catch (_: IllegalArgumentException) { pendingIntent = null; info("Invalid media request", "The playlist or source is invalid or too large.") }
        }
    }
    private suspend fun consumeSource(source: Intent) {
        val player = controller ?: return
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
                withContext(Dispatchers.IO) { try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) { } }
                makeItem(uri, withContext(Dispatchers.IO) { displayName(uri) }, source.type?.takeUnless { it.contains('*') })
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
        val item = makeItem(uri, source.getStringExtra("title") ?: withContext(Dispatchers.IO) { displayName(uri) }, source.getStringExtra("mime")?.ifBlank { source.type } ?: source.type) ?: return
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
    private fun chromeButton(label: String, action: () -> Unit) = button(label, action).apply {
        minWidth = 0; minimumWidth = 0; textSize = 12f; setAllCaps(false); letterSpacing = 0f
        insetTop = 0; insetBottom = 0; setPadding(dp(3), 0, dp(3), 0)
        setTextColor(android.graphics.Color.WHITE); backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
        elevation = 0f; stateListAnimator = null
        layoutParams = LinearLayout.LayoutParams(dp(56), dp(48))
    }
    private fun buildChrome(frame: FrameLayout) {
        top = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xD9000000.toInt()) }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(chromeButton("Back") { finish() })
        title = TextView(this).apply { text = "TN-Player"; textSize = 15f; setTextColor(-1); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
        row.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(chromeButton("PiP") { enterPip() }); row.addView(chromeButton("More") { menu() })
        top.addView(row)
        shortcuts = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        top.addView(shortcuts); renderShortcuts()
        frame.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        bottom = LinearLayout(this).apply { gravity = Gravity.CENTER; setBackgroundColor(0xD9000000.toInt()) }
        listOf<Pair<String, () -> Unit>>("Rotate" to { orientationMenu() }, "Lock" to { setLocked(true) }, "Mute" to { controller?.let { it.volume = if (it.volume > 0) 0f else 1f } }, "Full screen" to { toggleFullscreen() }).forEach { (label, action) ->
            bottom.addView(chromeButton(label, action).apply { layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f) })
        }
        frame.addView(bottom, FrameLayout.LayoutParams(-1, dp(48), Gravity.BOTTOM))
        unlock = chromeButton("Unlock") { setLocked(false) }.apply { visibility = View.GONE; contentDescription = "Unlock player controls" }
        frame.addView(unlock, FrameLayout.LayoutParams(dp(88), dp(48), Gravity.BOTTOM or Gravity.END))
        video.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_controller)?.setPadding(0, 0, 0, dp(52))
        applyDisplay()
    }
    private fun renderShortcuts() {
        if (!::shortcuts.isInitialized) return
        shortcuts.removeAllViews()
        val row = LinearLayout(this)
        PlayerShortcut.values().filter { it in settings.playerShortcuts }.forEach { shortcut ->
            row.addView(chromeButton(shortcut.label) { this@PlayerActivity.shortcut(shortcut) }.apply { maxLines = 2; layoutParams = LinearLayout.LayoutParams(dp(94), dp(56)) })
        }
        shortcuts.addView(row); shortcuts.visibility = if (settings.showPlayerShortcuts) View.VISIBLE else View.GONE
    }
    private fun updateChrome() {
        val hide = inPip() || locked
        top.visibility = if (hide) View.GONE else chromeVisibility
        bottom.visibility = if (hide) View.GONE else chromeVisibility
        unlock.visibility = if (locked && !inPip()) View.VISIBLE else View.GONE
        video.useController = !hide
    }
    private fun setLocked(value: Boolean) {
        if (!value && pinRequested) {
            try { stopLockTask() } catch (_: Exception) { }
            pinRequested = false
        }
        locked = value; video.useController = !value
        if (!value) { updatePip(); chromeVisibility = View.VISIBLE; video.showController() }
        updateChrome()
    }
    private fun applyOrientation() {
        requestedOrientation = when (settings.playerOrientation) { 1 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT; 2 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE; else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    private fun orientationMenu() {
        MaterialAlertDialogBuilder(this).setTitle("Screen rotation").setSingleChoiceItems(arrayOf("Follow device setting", "Portrait", "Landscape"), settings.playerOrientation) { dialog, index ->
            settings.playerOrientation = index; applyOrientation(); dialog.dismiss()
        }.show()
    }
    private fun toggleFullscreen() {
        fullscreen = !fullscreen; applyFullscreen()
    }
    private fun applyFullscreen() {
        val insets = androidx.core.view.WindowCompat.getInsetsController(window, video)
        insets.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (fullscreen) insets.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars()) else insets.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }
    private fun applyDisplay() {
        if (!::video.isInitialized) return
        video.videoSurfaceView?.visibility = if (settings.videoDisplay) View.VISIBLE else View.INVISIBLE
        video.videoSurfaceView?.alpha = if (settings.playerNight) .65f else 1f
        if (::gestures.isInitialized) gestures.refreshTransform()
        controller?.let { player ->
            if (player.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !settings.videoDisplay).build()
        }
    }
    private fun menu() {
        val player = controller ?: return
        playerTools(listOf(
            PlayerTool("Audio Track", R.drawable.hub_music) { trackDialog(C.TRACK_TYPE_AUDIO) },
            PlayerTool("Subtitle", R.drawable.player_subtitle) { subtitleMenu() },
            PlayerTool("Playing Queue", R.drawable.hub_playlist) { queueMenu() },
            PlayerTool("Aspect Ratio", R.drawable.player_aspect) { control(1) },
            PlayerTool("Display Settings", R.drawable.hub_settings) { displayMenu() },
            PlayerTool("Information", R.drawable.player_info) { currentInformation() },
            PlayerTool("Share", R.drawable.player_share) { shareCurrent() },
            PlayerTool("Tutorial", R.drawable.hub_help) { info("Player guide", "Rotate chooses portrait, landscape or your device setting. Full screen hides system bars; swipe from an edge to reveal them. Lock hides controls; use Unlock or long-press the video to restore them. Two-finger swipes adjust seek/volume/brightness; pinch zooms. Subtitle tools add external caption timing, previous/next cue seeking and a separate two-finger subtitle gesture mode. Pin player in More offers Android screen pinning. Tools includes track selection, queue, per-file details and custom shortcuts. Resume and last-played history are saved locally.") },
            PlayerTool("More", R.drawable.hub_settings) { advancedMenu() }
        ), settings.videoDisplay, settings.showPlayerShortcuts,
            { settings.videoDisplay = it; applyDisplay(); updatePip() }, { settings.showPlayerShortcuts = it; renderShortcuts() }, { customizeShortcuts() })
    }
    private fun trackDialog(type: Int) {
        val player = controller ?: return
        if (player.currentTracks.groups.none { it.type == type }) { info("Tracks", "No embedded track of this type is available. External subtitles can be loaded from Subtitle."); return }
        TrackSelectionDialogBuilder(this, if (type == C.TRACK_TYPE_AUDIO) "Audio Track" else "Subtitle", player, type).setShowDisableOption(type == C.TRACK_TYPE_TEXT).build().show()
    }
    private fun subtitleMenu() {
        val options = arrayOf("Embedded tracks", "Load external subtitle", "Subtitle style", "Subtitle timing", "Previous caption", "Next caption", if (ui.subtitleGestures) "Subtitle gestures: on" else "Subtitle gestures: off", "Remove external subtitle", "Supported subtitle formats")
        MaterialAlertDialogBuilder(this).setTitle("Subtitle").setItems(options) { _, index -> when (index) {
            0 -> trackDialog(C.TRACK_TYPE_TEXT); 1 -> control(2); 2 -> control(14)
            3 -> subtitleTiming(); 4 -> jumpCaption(-1); 5 -> jumpCaption(1)
            6 -> { ui.subtitleGestures = !ui.subtitleGestures; info("Subtitle gestures", if (ui.subtitleGestures) "Use two fingers: swipe horizontally to the previous/next external caption, vertically to move captions, or pinch to change text size. Normal video zoom returns when subtitle gestures are off. Embedded or styled captions may define their own position." else "Video seek, brightness, volume, zoom and pan gestures restored.") }
            7 -> { subtitleJob?.cancel(); ui.pendingSubtitle = null; ui.captions = null; ui.subtitleGestures = false; controller?.let { p -> p.currentMediaItem?.let { item -> replaceCaptions(p, item, emptyList()) } } }
            8 -> info("Subtitle formats", "Timed import: SRT, WebVTT, MPL2 (.mpl), PJS, MicroDVD (.sub with frame-rate header), SubViewer 2.0 (.sub), TMPlayer (.txt). Native rendering: ASS/SSA and TTML/DFXP. Text must use UTF-8 or BOM-marked UTF-16, up to 2 MB. Delay and cue navigation apply to timed imports; advanced WebVTT and legacy styling is not retained.")
        } }.show()
    }
    private fun subtitleTiming() {
        val captions = ui.captions?.takeIf { it.mediaId == controller?.currentMediaItem?.mediaId }
        if (captions == null) { info("Subtitle timing", "Load a timed text subtitle first. Embedded, ASS/SSA and TTML tracks use native timing."); return }
        val input = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED; setText(captions.delayMs.toString()); hint = "Milliseconds; positive means later" }
        val dialog = MaterialAlertDialogBuilder(this).setTitle("Subtitle delay (ms)").setMessage("Positive displays later; negative displays earlier. Range: −600000 to 600000 ms. Original files are kept.").setView(input).setNegativeButton("Cancel", null).setPositiveButton("Apply", null).create()
        dialog.setOnShowListener { dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val delayMs = input.text.toString().toLongOrNull()
            if (delayMs == null || delayMs !in -600000L..600000L) input.error = "Enter milliseconds within ±600000"
            else { publishCaptions(captions.copy(delayMs = delayMs)); dialog.dismiss() }
        } }; dialog.show()
    }
    private fun jumpCaption(direction: Int) {
        val player = controller ?: return
        val captions = ui.captions?.takeIf { it.mediaId == player.currentMediaItem?.mediaId }
        if (captions == null || !player.isCurrentMediaItemSeekable) { Toast.makeText(this, "Load a timed external subtitle on seekable media first", Toast.LENGTH_SHORT).show(); return }
        val target = captions.document.adjacent(player.currentPosition, direction, captions.delayMs)
        if (target == null) Toast.makeText(this, "No more captions in this direction", Toast.LENGTH_SHORT).show()
        else player.seekTo(target.coerceAtMost(player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
    }
    private fun decoderMenu() {
        MaterialAlertDialogBuilder(this).setTitle("Decoder preference").setMessage("Uses codecs available on this device. A preference keeps compatible fallback decoders; it does not add FFmpeg or HW+. Applying briefly reinitializes playback at the current position.")
            .setSingleChoiceItems(arrayOf("Automatic", "Prefer hardware codecs", "Prefer platform software codecs"), settings.decoderPreference) { dialog, mode ->
                controller?.let { player -> sendCommand(player, "DECODER_PREFERENCE", Bundle().apply { putInt("mode", mode) }, "Decoder preference applied") }; dialog.dismiss()
            }.setNegativeButton("Cancel", null).show()
    }
    private fun kidsLock() {
        MaterialAlertDialogBuilder(this).setTitle("Pin player · kids lock").setMessage("Android will ask to pin TN-Player on screen. Enable screen pinning in your device settings if needed. Android controls approval, emergency access and the unpin gesture. Unlock player controls also requests unpinning. This is not a parental-control password.")
            .setNegativeButton("Cancel", null).setPositiveButton("Pin player") { _, _ ->
                try { pinRequested = true; startLockTask(); setLocked(true); updatePip() }
                catch (_: Exception) { pinRequested = false; info("Screen pinning unavailable", "Enable screen pinning in Android's security settings and try again.") }
            }.show()
    }
    private fun queueMenu() {
        val player = controller ?: return
        val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        if (items.isEmpty()) { info("Playing Queue", "No media is queued."); return }
        MaterialAlertDialogBuilder(this).setTitle("Playing Queue · ${items.size}")
            .setSingleChoiceItems(items.map { it.mediaMetadata.title?.toString() ?: "Media" }.toTypedArray(), player.currentMediaItemIndex) { dialog, index ->
                if (index != player.currentMediaItemIndex) launchUi {
                    val saved = com.tplayer.mediaplayer.playback.PlaybackPersistence.get(this@PlayerActivity).historyFor(items[index].mediaId)
                    player.seekTo(index, if (settings.resume && saved != null) PlaybackPolicy.resumePosition(saved.position, saved.duration) else 0)
                    startPlayback(player)
                }
                dialog.dismiss()
            }.setNegativeButton("Close", null).show()
    }
    private fun displayMenu() {
        MaterialAlertDialogBuilder(this).setTitle("Display Settings").setItems(arrayOf("Screen rotation", "Aspect ratio", "Night display", "Mirror mode", "Vertical flip", "Reset zoom")) { _, index ->
            when (index) { 0 -> orientationMenu(); 1 -> control(1); 2 -> shortcut(PlayerShortcut.NIGHT); 3 -> shortcut(PlayerShortcut.MIRROR); 4 -> shortcut(PlayerShortcut.FLIP); 5 -> control(16) }
        }.show()
    }
    private fun currentInformation() { controller?.let { player ->
        val item = player.currentMediaItem ?: return
        launchUi { info("File information", MediaDetails(this@PlayerActivity).describe(LibraryItem(item.mediaId, title.text.toString(), mimeType = player.mediaMetadata.extras?.getString("mime").orEmpty()), com.tplayer.mediaplayer.playback.PlaybackPersistence.get(this@PlayerActivity).historyFor(item.mediaId))) }
    } }
    private fun shareCurrent() {
        val item = controller?.currentMediaItem ?: return
        val uri = Uri.parse(item.mediaId)
        val share = if (uri.scheme == "content") Intent(Intent.ACTION_SEND).setType(item.mediaMetadata.extras?.getString("mime")?.ifBlank { "*/*" } ?: "*/*").putExtra(Intent.EXTRA_STREAM, uri).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); clipData = ClipData.newRawUri("Media", uri)
        } else if (uri.scheme in setOf("https", "rtsp")) Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, uri.toString())
        else { info("Share", "Open this file through Android's picker to share a content URI securely."); return }
        try { startActivity(Intent.createChooser(share, "Share media")) } catch (_: Exception) { info("Share", "Android could not share this source. Check that access is still available.") }
    }
    private fun customizeShortcuts() {
        val checked = settings.playerShortcuts.toMutableSet()
        val scroll = ScrollView(this)
        val grid = GridLayout(this).apply { columnCount = 2; setPadding(dp(12), 0, dp(12), dp(8)) }
        PlayerShortcut.values().forEachIndexed { index, shortcut ->
            grid.addView(CheckBox(this).apply { text = shortcut.label; minHeight = dp(56); isChecked = shortcut in checked; setOnCheckedChangeListener { _, value -> if (value) checked.add(shortcut) else checked.remove(shortcut) } },
                GridLayout.LayoutParams(GridLayout.spec(index / 2), GridLayout.spec(index % 2, 1f)).apply { width = 0; height = -2 })
        }
        scroll.addView(grid)
        MaterialAlertDialogBuilder(this).setTitle("Customise items").setView(scroll).setNegativeButton("Cancel", null).setPositiveButton("Apply") { _, _ -> settings.playerShortcuts = checked; renderShortcuts() }.show()
    }
    private fun shortcut(action: PlayerShortcut) {
        val player = controller ?: return
        when (action) {
            PlayerShortcut.ROTATION -> orientationMenu()
            PlayerShortcut.SPEED -> control(0)
            PlayerShortcut.BACKGROUND -> { settings.backgroundPlayback = !settings.backgroundPlayback; Toast.makeText(this, "Background play ${if (settings.backgroundPlayback) "on" else "off"}", Toast.LENGTH_SHORT).show() }
            PlayerShortcut.MUTE -> player.volume = if (player.volume > 0) 0f else 1f
            PlayerShortcut.EQUALIZER, PlayerShortcut.EFFECTS -> control(15)
            PlayerShortcut.SLEEP -> control(10)
            PlayerShortcut.LOOP -> control(9)
            PlayerShortcut.SHUFFLE -> player.shuffleModeEnabled = !player.shuffleModeEnabled
            PlayerShortcut.AB -> control(7)
            PlayerShortcut.NIGHT -> { settings.playerNight = !settings.playerNight; applyDisplay() }
            PlayerShortcut.SCREENSHOT -> captureFrame()
            PlayerShortcut.MIRROR -> { settings.mirror = !settings.mirror; applyDisplay() }
            PlayerShortcut.FLIP -> { settings.verticalFlip = !settings.verticalFlip; applyDisplay() }
        }
    }
    private fun captureFrame() {
        val surface = video.videoSurfaceView as? android.view.TextureView
        if (surface == null || !surface.isAvailable || controller?.videoSize?.width?.let { it > 0 } != true || !settings.videoDisplay) { info("Screenshot", "Play a visible video first."); return }
        try {
            val scale = minOf(1f, 1920f / maxOf(surface.width, surface.height).coerceAtLeast(1))
            ui.screenshot = surface.getBitmap((surface.width * scale).toInt().coerceAtLeast(1), (surface.height * scale).toInt().coerceAtLeast(1)) ?: error("The source did not expose a frame.")
            saveScreenshot.launch("TN-Player-frame-${System.currentTimeMillis()}.png")
        } catch (_: Exception) { ui.screenshot = null; info("Screenshot", "This source cannot provide a frame. Protected video may prevent capture.") }
    }
    private fun advancedMenu() {
        val choices = arrayOf("Playback speed", "Aspect ratio", "External subtitle", "Subtitle size", "Add/remove favorite", "Add bookmark", "Bookmarks", "Set A–B repeat", "Clear A–B repeat", "Loop / shuffle", "Sleep timer", "Rotate screen", "Lock controls", "Stop playback", "Subtitle style", "Audio enhancements", "Reset zoom", "Pin player · kids lock", "Decoder preference")
        MaterialAlertDialogBuilder(this).setTitle("Playback controls").setItems(choices) { _, index -> when (index) { 17 -> kidsLock(); 18 -> decoderMenu(); else -> control(index) } }.show()
    }
    private fun control(index: Int) {
        val player = controller ?: return
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
                if (!player.isCurrentMediaItemSeekable) { info("A–B repeat", "This stream does not allow seeking."); return }
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
            11 -> orientationMenu()
            12 -> setLocked(true)
            13 -> { player.stop(); player.clearMediaItems(); finish() }
            14 -> subtitleStyleMenu()
            15 -> audioEnhancements(player)
            16 -> gestures.resetZoom()
        }
    }

    private fun applySubtitleStyle() {
        video.subtitleView?.apply {
            setApplyEmbeddedStyles(settings.embeddedSubtitleStyles)
            setFractionalTextSize(floatArrayOf(.035f, .0533f, .075f)[settings.subtitleSize])
            setBottomPaddingFraction(settings.subtitlePosition / 100f)
            setStyle(CaptionStyleCompat(settings.subtitleColor,
                if (settings.subtitleBackground) 0xB0000000.toInt() else android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK,
                arrayOf(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.SERIF, android.graphics.Typeface.MONOSPACE)[settings.subtitleFont]))
        }
    }
    private fun subtitleStyleMenu() {
        MaterialAlertDialogBuilder(this).setTitle("Subtitle style").setItems(arrayOf("Text size", "Text color", "Typeface", "Toggle background", "Position", if (settings.embeddedSubtitleStyles) "Embedded styles: on" else "Embedded styles: off")) { _, choice ->
            when (choice) {
                0 -> MaterialAlertDialogBuilder(this).setTitle("Text size").setSingleChoiceItems(arrayOf("Small", "Medium", "Large"), settings.subtitleSize) { dialog, index -> settings.subtitleSize = index; applySubtitleStyle(); dialog.dismiss() }.show()
                1 -> MaterialAlertDialogBuilder(this).setTitle("Text color").setItems(arrayOf("White", "Yellow", "Cyan")) { _, index -> settings.subtitleColor = intArrayOf(-1, -256, -16711681)[index]; applySubtitleStyle() }.show()
                2 -> MaterialAlertDialogBuilder(this).setTitle("Typeface").setSingleChoiceItems(arrayOf("Sans", "Serif", "Monospace"), settings.subtitleFont) { dialog, index -> settings.subtitleFont = index; applySubtitleStyle(); dialog.dismiss() }.show()
                3 -> { settings.subtitleBackground = !settings.subtitleBackground; applySubtitleStyle() }
                4 -> MaterialAlertDialogBuilder(this).setTitle("Position").setItems(arrayOf("Low", "Raised", "High")) { _, index -> settings.subtitlePosition = intArrayOf(8, 16, 28)[index]; applySubtitleStyle() }.show()
                5 -> { settings.embeddedSubtitleStyles = !settings.embeddedSubtitleStyles; applySubtitleStyle() }
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
    internal fun attachSubtitle(uri: Uri) {
        val player = controller ?: return
        val item = player.currentMediaItem ?: return
        subtitleJob?.cancel()
        ui.pendingSubtitle = uri; ui.pendingSubtitleMediaId = item.mediaId
        subtitleJob = launchUi {
            try {
            val imported = withContext(Dispatchers.IO) {
                require(uri.scheme in setOf("content", "file")) { "Choose a local subtitle file." }
                val name = displayName(uri)
                val text = checkNotNull(contentResolver.openInputStream(uri)).use { SubtitleImport.read(it) }
                Triple(name, text, name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT))
            }
            if (controller !== player || player.currentMediaItem?.mediaId != item.mediaId) return@launchUi
            if (imported.third in setOf("ass", "ssa", "ttml", "dfxp")) {
                // Keep native styling; never label this as full libass support.
                val file = withContext(Dispatchers.IO) { captionFile(imported.second) }
                if (controller !== player || player.currentMediaItem?.mediaId != item.mediaId) return@launchUi
                val mime = if (imported.third in setOf("ass", "ssa")) MimeTypes.TEXT_SSA else MimeTypes.APPLICATION_TTML
                val config = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file)).setMimeType(mime).setLanguage("und").setLabel(imported.first).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
                replaceCaptions(player, item, listOf(config)); ui.captions = null; ui.pendingSubtitle = null
            } else {
                val document = withContext(Dispatchers.Default) { SubtitleImport.parse(imported.second, imported.first) }
                if (controller === player && player.currentMediaItem?.mediaId == item.mediaId) applyCaptions(player, item, ExternalCaptions(item.mediaId, imported.first, document))
            }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { ui.pendingSubtitle = null; ui.pendingSubtitleMediaId = null; throw e }
        }
    }
    private fun captionFile(text: String): File {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val key = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val folder = File(cacheDir, "subtitles").apply { check(mkdirs() || isDirectory) }
        val file = File(folder, "$key.subtitle")
        if (!file.exists()) {
            val stage = File.createTempFile("caption-", ".part", folder)
            try { stage.writeBytes(bytes); check(stage.renameTo(file)) } finally { stage.delete() }
        }
        file.setLastModified(System.currentTimeMillis())
        return file
    }
    private fun publishCaptions(captions: ExternalCaptions) {
        val player = controller ?: return
        val item = player.currentMediaItem ?: return
        subtitleJob?.cancel()
        subtitleJob = launchUi { applyCaptions(player, item, captions) }
    }
    private suspend fun applyCaptions(player: MediaController, item: MediaItem, captions: ExternalCaptions) {
        val file = withContext(Dispatchers.IO) { captionFile(captions.document.srt(captions.delayMs)) }
        if (controller !== player || player.currentMediaItem?.mediaId != captions.mediaId) return
        val config = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file)).setMimeType(MimeTypes.APPLICATION_SUBRIP)
            .setLanguage("und").setLabel(captions.name).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        replaceCaptions(player, item, listOf(config)); ui.captions = captions; ui.pendingSubtitle = null
    }
    private fun replaceCaptions(player: MediaController, item: MediaItem, captions: List<MediaItem.SubtitleConfiguration>) {
        val position = player.currentPosition; val playing = player.playWhenReady
        player.replaceMediaItem(player.currentMediaItemIndex, item.buildUpon().setUri(item.mediaId).setMimeType(item.mediaMetadata.extras?.getString("mime")?.ifBlank { null }).setSubtitleConfigurations(captions).build())
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
        if (Build.VERSION.SDK_INT >= 31) builder.setAutoEnterEnabled(settings.autoPip && !pinRequested && !locked && player?.isPlaying == true && player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO))
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
        if (Build.VERSION.SDK_INT < 31 && settings.autoPip && !pinRequested && !locked && controller?.isPlaying == true && controller?.currentTracks?.isTypeSelected(C.TRACK_TYPE_VIDEO) == true) enterPip()
    }
    @androidx.annotation.RequiresApi(26)
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        video.useController = !isInPictureInPictureMode && !locked
        updateChrome()
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (locked && keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return true
        val player = controller ?: return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> if (player.isPlaying) player.pause() else startPlayback(player)
            KeyEvent.KEYCODE_DPAD_LEFT -> player.seekTo((player.currentPosition - settings.seekMs).coerceAtLeast(0))
            KeyEvent.KEYCODE_DPAD_RIGHT -> player.seekTo(player.currentPosition + settings.seekMs)
            KeyEvent.KEYCODE_M -> player.volume = if (player.volume > 0) 0f else 1f
            KeyEvent.KEYCODE_F -> toggleFullscreen()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }
}
