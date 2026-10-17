package com.tplayer.mediaplayer.ui

import android.Manifest
import android.app.DownloadManager
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.view.Gravity
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.tplayer.mediaplayer.R
import com.tplayer.mediaplayer.data.*
import com.tplayer.mediaplayer.playback.PlaybackPolicy
import com.tplayer.mediaplayer.playback.StreamFormat
import kotlinx.coroutines.*

class BrowserState : ViewModel() {
    var folder: DocumentFile? = null
    val parents = mutableListOf<DocumentFile>()
    var clipboard: Pair<DocumentFile, Boolean>? = null
    val searches = mutableMapOf<Screen, String>()
    val mediaFolders = mutableMapOf<Boolean, IndexedFolder?>()
    var settingsDetails = false
    val selection = FileSelection()
}
class MainActivity : AppCompatActivity() {
    private val settings by lazy { Settings(this) }
    private val mediaHandler = Handler(Looper.getMainLooper())
    private val refreshLibrary = Runnable { if (screen == Screen.VIDEOS || screen == Screen.MUSIC) renderScreen() }
    private var observingMedia = false
    private val mediaObserver = object : android.database.ContentObserver(mediaHandler) {
        override fun onChange(selfChange: Boolean) {
            mediaHandler.removeCallbacks(refreshLibrary); mediaHandler.postDelayed(refreshLibrary, 750)
        }
    }
    private val exportBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) launchUi {
            val values = settings.backupValues()
            withContext(Dispatchers.IO) {
                val backup = BackupRepository(MediaDatabase.get(this@MainActivity)).snapshot(values)
                val bytes = BackupFormat.encode(backup)
                checkNotNull(contentResolver.openOutputStream(uri, "wt")) { "Cannot write this backup." }.use { it.write(bytes) }
            }
            info("Backup saved", "Local favorites, playlists, bookmarks and preferences were exported. Network sources, credentials and media bytes were excluded.")
        }
    }
    private val importBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) launchUi {
            val backup = withContext(Dispatchers.IO) {
                checkNotNull(contentResolver.openInputStream(uri)).use { BackupFormat.decode(BackupFormat.read(it)) }
            }
            MaterialAlertDialogBuilder(this@MainActivity).setTitle("Restore backup?").setMessage(backup.summary())
                .setNegativeButton("Cancel", null).setPositiveButton("Merge") { _, _ -> launchUi {
                    BackupRepository(MediaDatabase.get(this@MainActivity)).merge(backup)
                    withContext(Dispatchers.IO) { settings.restoreValues(backup.preferences) }
                    AppCompatDelegate.setDefaultNightMode(settings.theme)
                    Toast.makeText(applicationContext, "Collections restored. Rechoose media if access has changed.", Toast.LENGTH_LONG).show()
                    recreate()
                } }.show()
        }
    }
    private val dao by lazy { MediaDatabase.get(this).media() }
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var adapter: MediaAdapter
    private var tab = 1
    private val navigationId = android.view.View.generateViewId()
    private var job: Job? = null
    private val browser by viewModels<BrowserState>()
    private val transfers by viewModels<FileTransferModel>()
    private val deletions by viewModels<FileDeleteModel>()
    private var selectionCounter: TextView? = null
    private var selectionActions: android.view.View? = null
    private var selectAllHandler: (() -> Unit)? = null
    private val deleteConsent = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { deletions.result(it.resultCode == RESULT_OK) }
    private val legacyWrite = registerForActivityResult(ActivityResultContracts.RequestPermission()) { deletions.result(it) }
    private var folder: DocumentFile?
        get() = browser.folder
        set(value) { browser.folder = value }
    private val parents get() = browser.parents
    private var screen = Screen.VIDEOS
    private var searchHandler: ((String) -> Unit)? = null
    private var documents: Map<String, DocumentFile> = emptyMap()
    private var clipboard: Pair<DocumentFile, Boolean>?
        get() = browser.clipboard
        set(value) { browser.clipboard = value }
    private var activePlaylist: Long? = null
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { showTab(tab) }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        Toast.makeText(this, if (it) "Notifications enabled" else "Notification access was not granted", Toast.LENGTH_SHORT).show()
    }
    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            retain(it, false)
            launchUi {
                val name = withContext(Dispatchers.IO) {
                    contentResolver.query(it, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: "Media"
                }
                play(LibraryItem(it.toString(), name))
            }
        }
    }
    private val openTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { retain(it, true); settings.tree = it.toString(); folder = DocumentFile.fromTreeUri(applicationContext, it); parents.clear(); showTab(3) }
    }
    private fun retain(uri: Uri, write: Boolean) {
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0) }
        catch (_: SecurityException) {
            if (write) try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) { }
        }
    }
    private fun launchUi(action: suspend CoroutineScope.() -> Unit): Job = lifecycleScope.launch {
        try { action() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { info("Action unavailable", e.message ?: "Check storage access and try again.") }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        if (settings.amoled) setTheme(R.style.Theme_TNPlayer_Amoled)
        super.onCreate(savedInstanceState)
        tab = savedInstanceState?.getInt("tab") ?: when (intent.action) { "com.tplayer.OPEN_STREAMS" -> 4; "com.tplayer.OPEN_FILES" -> 3; else -> 1 }
        screen = if (savedInstanceState != null) Screen.restore(savedInstanceState.getString("screen")) else Screen.forTab(tab)
        activePlaylist = savedInstanceState?.getLong("playlist", -1L)?.takeIf { it >= 0 }
        savedInstanceState?.let { state ->
            browser.settingsDetails = state.getBoolean("settingsDetails", browser.settingsDetails)
            for (video in listOf(true, false)) {
                val volume = state.getString("mediaVolume$video")
                val path = state.getString("mediaPath$video")
                if (volume != null && path != null) browser.mediaFolders[video] = IndexedFolder(volume, path)
            }
        }
        if (folder == null) folder = settings.tree?.let { DocumentFile.fromTreeUri(applicationContext, Uri.parse(it)) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = MaterialToolbar(this).apply {
            title = "TN-Player"; subtitle = getString(R.string.tagline)
            val brandBitmap = android.graphics.BitmapFactory.decodeResource(resources, R.drawable.tn_logo,
                android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })
            logo = android.graphics.drawable.BitmapDrawable(resources,
                android.graphics.Bitmap.createScaledBitmap(brandBitmap, dp(40), dp(40), true))
            menu.add("Library").setOnMenuItemClickListener { collections(); true }
            menu.add("Help").setOnMenuItemClickListener { help(); true }
        }
        root.addView(toolbar)
        val transferBanner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), dp(8)); visibility = android.view.View.GONE }
        val transferLabel = TextView(this).apply { textSize = 14f }
        val transferProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        transferBanner.addView(transferLabel); transferBanner.addView(transferProgress)
        transferBanner.addView(button("Cancel transfer") { transfers.cancel() }); root.addView(transferBanner)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                transfers.state.collect { state ->
                    transferBanner.visibility = if (state.running) android.view.View.VISIBLE else android.view.View.GONE
                    transferLabel.text = "${state.phase} · ${state.bytes / 1024} KB" + if (state.total > 0) " / ${state.total / 1024} KB" else ""
                    transferProgress.isIndeterminate = state.total <= 0
                    if (state.total > 0) transferProgress.progress = (state.bytes * 100 / state.total).toInt().coerceIn(0, 100)
                    state.notice?.let {
                        if (state.clearClipboard) clipboard = null
                        if (screen == Screen.FILES) showFiles()
                        transfers.acknowledge(); info("File transfer", it)
                    }
                }
            }
        }
        val deleteBanner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = android.view.View.GONE; setPadding(dp(16), 0, dp(16), dp(8)) }
        val deleteLabel = TextView(this).apply { readable() }
        val cancelDelete = button("Cancel deletion") { deletions.cancel() }
        deleteBanner.addView(deleteLabel); deleteBanner.addView(cancelDelete); root.addView(deleteBanner)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                deletions.state.collect { state ->
                    deleteBanner.visibility = if (state.running) android.view.View.VISIBLE else android.view.View.GONE
                    deleteLabel.text = "Deleting ${state.done} / ${state.total}" + if (state.sender != null || state.permission) " · Waiting for Android approval" else ""
                    cancelDelete.isEnabled = state.sender == null && !state.permission
                    if (state.running && !state.dispatched) {
                        if (state.permission) { deletions.dispatched(); legacyWrite.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
                        else state.sender?.let { sender ->
                            deletions.dispatched()
                            try { deleteConsent.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build()) }
                            catch (_: Exception) { deletions.launchFailed() }
                        }
                    }
                    state.notice?.let { notice ->
                        browser.selection.remove(state.removed); deletions.acknowledge()
                        if (::content.isInitialized) renderScreen()
                        info("File deletion", notice)
                    }
                }
            }
        }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), 0) }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = BottomNavigationView(this).apply {
            id = navigationId
            labelVisibilityMode = BottomNavigationView.LABEL_VISIBILITY_LABELED
            menu.add(0, 1, 0, "Videos").setIcon(android.R.drawable.ic_media_play)
            menu.add(0, 2, 1, "Music").setIcon(android.R.drawable.ic_media_ff)
            menu.add(0, 3, 2, "Files").setIcon(android.R.drawable.ic_menu_agenda)
            menu.add(0, 4, 3, "Streams").setIcon(android.R.drawable.ic_menu_share)
            menu.add(0, 5, 4, "Settings").setIcon(android.R.drawable.ic_menu_preferences)

        }
        root.addView(NowPlayingBar(this))
        root.addView(nav); setContentView(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    browser.selection.active -> { browser.selection.clear(); adapter.refreshSelection(); updateSelection() }
                    screen in setOf(Screen.VIDEOS, Screen.MUSIC) && browser.mediaFolders[screen == Screen.VIDEOS] != null -> {
                        val video = screen == Screen.VIDEOS
                        browser.mediaFolders[video] = if (settings.libraryDisplay.mode == MediaViewMode.ALL_FOLDERS) browser.mediaFolders[video]?.parent() else null
                        browser.searches[screen] = ""; showMedia(video)
                    }
                    screen == Screen.SETTINGS && browser.settingsDetails -> { browser.settingsDetails = false; showSettings() }
                    screen == Screen.FILES && parents.isNotEmpty() -> { folder = parents.removeAt(parents.lastIndex); showFiles() }
                    screen.tab == 0 -> showTab(tab)
                    else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
                }
            }
        })
        nav.selectedItemId = tab
        nav.setOnItemSelectedListener { showTab(it.itemId); true }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("tab", tab); outState.putString("screen", screen.name); outState.putLong("playlist", activePlaylist ?: -1)
        outState.putBoolean("settingsDetails", browser.settingsDetails)
        for ((video, selected) in browser.mediaFolders) selected?.let {
            outState.putString("mediaVolume$video", it.volume); outState.putString("mediaPath$video", it.path)
        }
        super.onSaveInstanceState(outState)
    }
    override fun onStart() {
        super.onStart()
        try {
            contentResolver.registerContentObserver(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
            contentResolver.registerContentObserver(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
            observingMedia = true
        } catch (_: SecurityException) { contentResolver.unregisterContentObserver(mediaObserver) }
    }
    override fun onStop() {
        mediaHandler.removeCallbacks(refreshLibrary)
        if (observingMedia) { contentResolver.unregisterContentObserver(mediaObserver); observingMedia = false }
        super.onStop()
    }
    override fun onResume() { super.onResume(); if (::content.isInitialized) renderScreen() }
    private fun renderScreen() {
        when (screen) {
            Screen.FAVORITES -> showFavorites()
            Screen.HISTORY -> showHistory()
            Screen.DOWNLOADS -> showDownloads()
            Screen.PLAYLIST -> activePlaylist?.let { showPlaylist(it) } ?: showTab(1)
            else -> showTab(screen.tab)
        }
    }
    private fun setup(title: String): LinearLayout {
        job?.cancel(); content.removeAllViews(); searchHandler = null; selectAllHandler = null
        val selectionScope = "$screen:${if (screen == Screen.FILES) folder?.uri else browser.mediaFolders[screen == Screen.VIDEOS]}"
        browser.selection.scope(selectionScope)
        selectionCounter = null; selectionActions = null
        val heading = TextView(this).apply { text = title; textSize = 28f; readable(); setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(dp(8), dp(16), 0, dp(12)); accessibilityHeadingCompat() }
        content.addView(heading)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(actions) }
        content.addView(scroll)
        status = TextView(this).apply { textSize = 13f; readable(); alpha = .7f; setPadding(dp(8), dp(8), dp(8), dp(8)); accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE }
        content.addView(status)
        val search = EditText(this).apply { hint = "Search this list"; contentDescription = "Search this list"; setSingleLine(); minHeight = dp(48); setText(browser.searches[screen].orEmpty()) }
        content.addView(search)
        val selectableScreen = screen in setOf(Screen.VIDEOS, Screen.MUSIC, Screen.FILES)
        if (selectableScreen) {
            val selectionBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val selectionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            selectionCounter = TextView(this).apply { readable(); textSize = 14f; setPadding(dp(8), 0, dp(8), 0) }
            selectionRow.addView(selectionCounter, LinearLayout.LayoutParams(0, -2, 1f))
            selectionRow.addView(button("Delete") { confirmDelete(browser.selection.items) }.apply { contentDescription = "Delete selected" })
            selectionRow.addView(button("Done") { browser.selection.clear(); adapter.refreshSelection(); updateSelection() })
            selectionBox.addView(selectionRow)
            val bulk = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            bulk.addView(button("Select all") { selectAllHandler?.invoke() ?: run {
                try { browser.selection.all(adapter.selectable()); adapter.refreshSelection(); updateSelection() }
                catch (e: IllegalArgumentException) { info("Selection", e.message.orEmpty()) }
            } })
            bulk.addView(button("Share selected") { shareSelection() })
            selectionBox.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(bulk) })
            selectionActions = selectionBox
            content.addView(selectionActions)
            actions.addView(button("Select files") { browser.selection.enter(); adapter.refreshSelection(); updateSelection() })
        }
        adapter = MediaAdapter({ item ->
            if (item.indexedFolder != null) {
                browser.mediaFolders[screen == Screen.VIDEOS] = item.indexedFolder
                browser.searches[screen] = ""; showMedia(screen == Screen.VIDEOS)
            } else if (screen == Screen.FILES && item.detail == "Folder") {
                folder?.let { parents += it }; folder = documents[item.uri]; showFiles()
            } else play(item)
        }, { item ->
            if (item.indexedFolder != null) info(item.title, item.detail)
            else if (screen == Screen.FILES) fileOptions(item) else mediaOptions(item)
        }, grid = screen in setOf(Screen.VIDEOS, Screen.MUSIC) && settings.libraryDisplay.grid, selection = if (selectableScreen) browser.selection else null, selected = { updateSelection() })
        val list = RecyclerView(this).apply {
            layoutManager = if (screen in setOf(Screen.VIDEOS, Screen.MUSIC) && settings.libraryDisplay.grid)
                GridLayoutManager(this@MainActivity, maxOf(2, resources.configuration.screenWidthDp / 180)) else LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
        content.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        adapter.filter(browser.searches[screen].orEmpty()); updateSelection()
        search.doAfterTextChanged { value ->
            val query = value?.toString().orEmpty()
            browser.searches[screen] = query
            val handler = searchHandler
            if (handler != null) handler(query) else { adapter.filter(query); updateSelection() }
        }
        return actions
    }
    private fun TextView.accessibilityHeadingCompat() { androidx.core.view.ViewCompat.setAccessibilityHeading(this, true) }
    private fun showTab(value: Int) {
        if (value != tab) browser.selection.clear()
        tab = value; screen = Screen.forTab(value); activePlaylist = null
        when (value) { 1, 2 -> showMedia(value == 1); 3 -> showFiles(); 4 -> showStreams(); 5 -> showSettings() }
    }
    private fun showMedia(video: Boolean) {
        val display = settings.libraryDisplay
        val current = browser.mediaFolders[video]
        val actions = setup(current?.name ?: if (display.mode == MediaViewMode.FILES) (if (video) "Videos" else "Music") else "Folders")
        actions.addView(button("View & layout") {
            libraryViewDialog(settings.libraryDisplay, settings.hidden) { value, hidden ->
                if (value.mode != settings.libraryDisplay.mode) browser.mediaFolders.clear()
                settings.libraryDisplay = value; settings.hidden = hidden; showMedia(video)
            }
        })
        if (current != null) actions.addView(button("Up") {
            browser.mediaFolders[video] = if (display.mode == MediaViewMode.ALL_FOLDERS) current.parent() else null
            browser.searches[screen] = ""; showMedia(video)
        })
        actions.addView(button("Open file") { openFile.launch(arrayOf("video/*", "audio/*")) })
        actions.addView(button("Refresh") { showMedia(video) })
        actions.addView(button("Resume last") { startActivity(Intent(this, PlayerActivity::class.java).putExtra("resume", true)) })
        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val quickIcons = listOf(R.drawable.hub_music, R.drawable.hub_playlist, android.R.drawable.ic_menu_recent_history,
            android.R.drawable.stat_sys_download_done, R.drawable.hub_files, R.drawable.hub_settings)
        listOf<Pair<String, () -> Unit>>(
            "Music" to { selectTab(2) }, "Playlists" to { listPlaylists() }, "History" to { showHistory() },
            "Downloads" to { showDownloads() }, "Files" to { selectTab(3) }, "Settings" to { selectTab(5) }
        ).forEachIndexed { index, (label, action) ->
            quick.addView(button(label, action).apply {
                setIconResource(quickIcons[index]); iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_TOP
                iconSize = dp(28); textSize = 12f; insetTop = 0; insetBottom = 0
                setAllCaps(false); letterSpacing = 0f; maxLines = 1; setPadding(dp(4), dp(4), dp(4), dp(4))
                elevation = 0f; stateListAnimator = null
                backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                val accent = com.google.android.material.color.MaterialColors.getColor(this@MainActivity, com.google.android.material.R.attr.colorPrimary, android.graphics.Color.BLUE)
                setTextColor(accent); iconTint = android.content.res.ColorStateList.valueOf(accent)
                layoutParams = LinearLayout.LayoutParams(dp(88), dp(76)).apply { marginEnd = dp(6) }
            })
        }
        content.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(quick) }, 2)
        val required = if (Build.VERSION.SDK_INT >= 33) {
            if (video) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_MEDIA_AUDIO
        } else Manifest.permission.READ_EXTERNAL_STORAGE
        val hasFull = ContextCompat.checkSelfPermission(this, required) == PackageManager.PERMISSION_GRANTED
        val hasPartial = video && Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        val access = button(if (hasFull) "Library access" else "Grant access") {
            permissions.launch(if (video && Build.VERSION.SDK_INT >= 34) arrayOf(required, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) else arrayOf(required))
        }
        actions.addView(access, if (hasFull) actions.childCount else 0)
        if (!hasFull && !hasPartial) { status.text = "Grant library access to see media folders and file counts, or open an individual file. Files → Choose folder also supports SD/USB and document providers."; return }
        val loaded = mutableListOf<LibraryItem>()
        var offset = 0
        var query = browser.searches[screen].orEmpty()
        val more = button("Load more") {}
        actions.addView(more)
        fun load(reset: Boolean) {
            job?.cancel(); more.isEnabled = false; status.text = "Reading your media folders…"
            if (reset) { offset = 0; loaded.clear(); adapter.submit(emptyList()) }
            job = launchUi {
                try {
                    if (reset && query.isNotBlank()) delay(250)
                    // Local history cannot be blocked by an unavailable network-secret key.
                    val history = MediaDatabase.get(this@MainActivity).raw().history().filter { it.uri.startsWith("content:") || it.uri.startsWith("file:") }
                    val page = MediaCatalog(this@MainActivity).page(video, display, current, query, settings.hidden, history, offset)
                    offset = page.nextOffset; loaded.addAll(page.entries.map { it.item(display) })
                    adapter.submit(loaded.toList()); updateSelection(); more.isEnabled = page.hasMore
                    status.text = if (loaded.isEmpty()) "No matching accessible ${if (video) "video" else "audio"} media. Try Refresh, adjust filters or choose a folder in Files."
                        else "${loaded.size} of ${page.total} · ${display.sort.label} ${if (display.descending) "↓" else "↑"}" +
                            (current?.let { "\n${it.displayPath}" } ?: "") + if (hasPartial && !hasFull) " · Selected videos only" else ""
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { status.text = "Library unavailable. Open a file or grant access again." }
            }
        }
        selectAllHandler = { launchUi {
            if (current == null && display.mode != MediaViewMode.FILES) { info("Select files", "Open a media folder or choose Files view first."); return@launchUi }
            val version = browser.selection.version; val snapshotQuery = query
            val picked = mutableListOf<LibraryItem>(); var next = 0
            val history = MediaDatabase.get(this@MainActivity).raw().history()
            do {
                val page = MediaCatalog(this@MainActivity).page(video, display.copy(mode = MediaViewMode.FILES), current, snapshotQuery, settings.hidden, history, next)
                picked += page.entries.filter { !it.directory }.map { it.item(display) }
                require(picked.size <= FileSelection.LIMIT) { "More than 1,000 matching files. Narrow the search or choose a smaller folder." }
                next = page.nextOffset
            } while (page.hasMore)
            if (browser.selection.version == version) { browser.selection.all(picked); adapter.refreshSelection(); updateSelection() }
        } }
        more.setOnClickListener { load(false) }
        searchHandler = { value -> browser.selection.clear(); updateSelection(); query = value; load(true) }
        load(true)
    }
    private fun selectTab(value: Int) {
        findViewById<BottomNavigationView>(navigationId).selectedItemId = value
    }

    private fun showFiles() {
        val actions = setup("Files")
        actions.addView(button("Refresh") { showFiles() })
        actions.addView(button("Choose folder") { openTree.launch(null) })
        actions.addView(button("Up") { if (parents.isNotEmpty()) { folder = parents.removeAt(parents.lastIndex); showFiles() } })
        actions.addView(button("New folder") {
            input("Folder name") { name -> if (DocumentOperations.validName(name)) runFile { checkNotNull(folder?.createDirectory(name)) { "Cannot create folder." } } else info("Invalid name", "Use a nonempty name without slashes.") }
        })
        clipboard?.let { clip -> actions.addView(button(if (clip.second) "Move here" else "Paste here") {
            val destination = folder ?: return@button
            transfers.start(clip.first, destination, clip.second)
        }) }
        val target = folder
        if (target == null) { status.text = "Choose a folder to browse files, including SD/USB storage exposed by Android."; return }
        job = launchUi {
            try {
                val recent = MediaDatabase.get(this@MainActivity).raw().history().associateBy { it.uri }
                val snapshot = withContext(Dispatchers.IO) {
                    val entries = target.listFiles().map { doc ->
                        ensureActive()
                        val name = doc.name ?: "Untitled"; val directory = doc.isDirectory
                        doc to LibraryItem(doc.uri.toString(), name, if (directory) "Folder" else "${doc.length() / 1024} KB" + (recent[doc.uri.toString()]?.let { " · Resume ${mediaTime(PlaybackPolicy.resumePosition(it.position, it.duration))}" } ?: ""), doc.type.orEmpty())
                    }.filter { settings.hidden || !it.second.title.startsWith('.') }
                        .sortedWith(compareBy<Pair<DocumentFile, LibraryItem>> { it.second.detail != "Folder" }.thenBy { it.second.title.lowercase() })
                    Triple(entries, target.name ?: "Folder", target.canWrite())
                }
                documents = snapshot.first.associate { it.first.uri.toString() to it.first }
                adapter.submit(snapshot.first.map { it.second }); updateSelection()
                status.text = "${snapshot.second} · ${snapshot.first.size} items · ${if (snapshot.third) "Read/write" else "Read only"}"
            } catch (e: Exception) { if (e is CancellationException) throw e; status.text = "Folder unavailable. Choose it again to restore access." }
        }
    }
    private fun runFile(action: () -> Unit) {
        launchUi {
            try { withContext(Dispatchers.IO) { action() }; showFiles() }
            catch (e: Exception) { if (e is CancellationException) throw e; info("File operation", e.message ?: "Operation failed.") }
        }
    }
    private fun fileOptions(item: LibraryItem) {
        val doc = documents[item.uri] ?: return
        val options = if (item.detail == "Folder") arrayOf("Rename", "Delete", "Properties", "Create ZIP") else arrayOf("Play", "Copy", "Move", "Rename", "Delete", "Share", "Properties", "Create ZIP", "Extract ZIP")
        MaterialAlertDialogBuilder(this).setTitle(item.title).setItems(options) { _, index ->
            when (options[index]) {
                "Create ZIP", "Extract ZIP" -> {
                    val parent = folder ?: return@setItems
                    val extracting = options[index] == "Extract ZIP"
                    MaterialAlertDialogBuilder(this).setTitle(options[index])
                        .setMessage("Writes a new output beside the source; never overwrites or removes the original. Limits: 5,000 entries, 512 MB expanded/input bytes and 24 folder levels. A process interruption may leave a hidden .tn-archive staging output.")
                        .setPositiveButton("Continue") { _, _ -> transfers.archive(doc, parent, extracting) }.setNegativeButton("Cancel", null).show()
                }
                "Play" -> play(item)
                "Copy", "Move" -> { clipboard = doc to (options[index] == "Move"); Toast.makeText(this, "Open a destination folder and tap Paste/Move here.", Toast.LENGTH_LONG).show(); showFiles() }
                "Rename" -> input("Rename", item.title) { name ->
                    if (DocumentOperations.validName(name)) runFile { check(doc.renameTo(name)) { "Cannot rename file." } }
                    else info("Invalid name", "Use a nonempty name without slashes.")
                }
                "Delete" -> MaterialAlertDialogBuilder(this).setTitle("Delete ${item.title}?").setMessage("This removes the selected item. Deleting a folder may remove its contents.")
                    .setPositiveButton("Delete") { _, _ -> runFile { check(doc.delete()) { "Cannot delete item." } } }.setNegativeButton("Cancel", null).show()
                "Share" -> try {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(item.mimeType.ifBlank { "application/octet-stream" }).putExtra(Intent.EXTRA_STREAM, doc.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share media"))
                } catch (_: ActivityNotFoundException) { info("Share", "No compatible app is installed.") }
                "Properties" -> details(item)
            }
        }.show()
    }
    private fun updateSelection() {
        if (!::adapter.isInitialized) return
        selectionActions?.visibility = if (browser.selection.active) android.view.View.VISIBLE else android.view.View.GONE
        selectionCounter?.text = "${browser.selection.size} / ${maxOf(browser.selection.size, adapter.selectable().size)} selected"
    }
    private fun confirmDelete(items: List<LibraryItem>) {
        if (items.isEmpty()) { info("Select files", "Select one or more files first."); return }
        if (deletions.state.value.running) { info("File deletion", "Wait for the current deletion to finish."); return }
        MaterialAlertDialogBuilder(this).setTitle("Delete ${items.size} ${if (items.size == 1) "file" else "files"}?")
            .setMessage(items.take(5).joinToString("\n") { it.title } + (if (items.size > 5) "\n…and ${items.size - 5} more" else "") +
                "\n\nThis removes the original files permanently. TN-Player has no recycle bin. Android may ask for additional approval. Folders are not included.")
            .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ ->
                try { deletions.start(items) } catch (e: Exception) { info("File deletion", e.message.orEmpty()) }
            }.show()
    }
    private fun details(item: LibraryItem) { launchUi {
        val history = MediaDatabase.get(this@MainActivity).raw().historyFor(item.uri)
        info("File information", MediaDetails(this@MainActivity).describe(item, history))
    } }
    private fun shareSelection() {
        val files = browser.selection.items
        if (files.isEmpty()) { info("Share files", "Select one or more files first."); return }
        if (files.any { Uri.parse(it.uri).scheme != "content" }) { info("Share files", "Choose these files through Android's folder picker to share content URIs securely."); return }
        val uris = ArrayList(files.map { Uri.parse(it.uri) })
        val share = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (uris.size == 1) share.putExtra(Intent.EXTRA_STREAM, uris.first()) else share.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        share.clipData = ClipData.newRawUri("Selected files", uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        try { startActivity(Intent.createChooser(share, "Share selected files")) } catch (_: ActivityNotFoundException) { info("Share files", "No compatible app is installed.") }
    }
    private fun showStreams() {
        val actions = setup("Network streams")
        actions.addView(button("Add stream") { streamDialog() })
        status.text = "HTTPS · HLS · DASH · SmoothStreaming · RTSP"
        job = launchUi { adapter.submit(dao.streams().map { LibraryItem(it.uri, it.title, StreamFormat.fromMime(it.mimeType).label, it.mimeType) }) }
    }
    private fun streamDialog(initial: LibraryItem? = null) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), 0, dp(24), 0) }
        val title = EditText(this).apply { hint = "Stream name"; setSingleLine(); setText(initial?.title.orEmpty()) }
        val url = EditText(this).apply { hint = "https://… or rtsp://…"; setSingleLine(); inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI }
        val format = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, StreamFormat.values().map { it.label }) }
        url.setText(initial?.uri.orEmpty()); format.setSelection(StreamFormat.fromMime(initial?.mimeType).ordinal)
        box.addView(title); box.addView(url); box.addView(format)
        val dialog = MaterialAlertDialogBuilder(this).setTitle("Add a stream").setView(box).setPositiveButton("Save & play", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
            val value = url.text.toString().trim(); val error = PlaybackPolicy.validateStream(value)
            if (error != null) { url.error = error; return@setOnClickListener }
            val name = title.text.toString().trim().ifEmpty { Uri.parse(value).host ?: "Stream" }
            launchUi { dao.saveStream(SavedStream(value, name, StreamFormat.values()[format.selectedItemPosition].mimeType)); dialog.dismiss(); play(LibraryItem(value, name, mimeType = StreamFormat.values()[format.selectedItemPosition].mimeType)); showStreams() }
        } }; dialog.show()
    }
    private fun play(item: LibraryItem) {
        val uri = Uri.parse(item.uri)
        val intent = Intent(this, PlayerActivity::class.java).setData(uri).putExtra("title", item.title).putExtra("mime", item.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(intent)
    }
    private fun mediaOptions(item: LibraryItem) {
        launchUi {
            val favorite = dao.isFavorite(item.uri)
            val options = mutableListOf(if (favorite) "Remove favorite" else "Add favorite", "Add to playlist", "Properties")
            if (screen in setOf(Screen.VIDEOS, Screen.MUSIC)) options += listOf("Select file", "Delete file")
            if (screen == Screen.STREAMS) options += listOf("Edit stream", "Download HTTPS file", "Remove saved stream")
            if (screen == Screen.PLAYLIST && activePlaylist != null) options += "Remove from playlist"
            MaterialAlertDialogBuilder(this@MainActivity).setTitle(item.title).setItems(options.toTypedArray()) { _, index ->
                launchUi {
                    when (options[index]) {
                        "Select file" -> { browser.selection.toggle(item); adapter.refreshSelection(); updateSelection() }
                        "Delete file" -> confirmDelete(listOf(item))
                        "Add favorite" -> dao.addFavorite(Favorite(item.uri, item.title, item.mimeType))
                        "Remove favorite" -> dao.removeFavorite(item.uri)
                        "Add to playlist" -> choosePlaylist(item)
                        "Properties" -> details(item)
                        "Edit stream" -> streamDialog(item)
                        "Remove saved stream" -> { dao.removeStream(item.uri); showStreams() }
                        "Download HTTPS file" -> download(item)
                        "Remove from playlist" -> { activePlaylist?.let { dao.removeFromPlaylist(it, item.uri); showPlaylist(it) } }
                    }
                }
            }.show()
        }
    }
    private fun download(item: LibraryItem) {
        if (Uri.parse(item.uri).scheme != "https") { info("Downloads", "Only direct HTTPS files can be downloaded."); return }
        if (Uri.parse(item.uri).path?.lowercase()?.let { it.endsWith(".m3u8") || it.endsWith(".mpd") } == true) {
            info("Downloads", "Adaptive/offline stream downloads are not implemented. Use a direct media file."); return
        }
        try {
            val fileName = Uri.parse(item.uri).lastPathSegment?.replace(Regex("[^A-Za-z0-9._-]"), "_")?.take(100)?.ifEmpty { "media" } ?: "media"
            val request = DownloadManager.Request(Uri.parse(item.uri)).setTitle(item.title)
                .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "${System.currentTimeMillis()}-$fileName")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            info("Download queued", "Android Download Manager will download this direct file. It is stored in TN-Player's app files and is removed on uninstall.")
        } catch (e: Exception) { info("Download failed", e.message ?: "Cannot start download.") }
    }
    private fun collections() {
        MaterialAlertDialogBuilder(this).setTitle("Your library").setItems(arrayOf("Favorites", "History", "Playlists", "Downloads")) { _, index ->
            when (index) {
                0 -> showFavorites()
                1 -> showHistory()
                2 -> listPlaylists()
                3 -> showDownloads()
            }
        }.show()
    }
    private fun showFavorites() {
        screen = Screen.FAVORITES; activePlaylist = null; setup("Favorites"); status.text = "Saved on this device"
        job = launchUi { adapter.submit(dao.favorites().map { LibraryItem(it.uri, it.title, mimeType = it.mimeType) }) }
    }
    private fun showHistory() {
        screen = Screen.HISTORY; activePlaylist = null
        val actions = setup("Recently played"); status.text = "Tap a file to resume playback"
        actions.addView(button("Clear history") { launchUi { dao.clearHistory(); adapter.submit(emptyList()) } })
        job = launchUi { adapter.submit(dao.history().map { LibraryItem(it.uri, it.title, "Resume at ${it.position / 1000}s", it.mimeType) }) }
    }
    private fun showDownloads() {
        screen = Screen.DOWNLOADS; activePlaylist = null
        setup("Downloads")
        status.text = "Completed app downloads · Use Android notifications for active downloads"
        job = launchUi {
            val items = withContext(Dispatchers.IO) {
                val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                val values = mutableListOf<LibraryItem>()
                manager.query(DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL))?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                        val uri = manager.getUriForDownloadedFile(id) ?: continue
                        values += LibraryItem(uri.toString(), cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ?: "Download", "Completed download")
                    }
                }
                values
            }
            adapter.submit(items)
        }
    }
    private fun listPlaylists() {
        launchUi {
            val lists = dao.playlists()
            MaterialAlertDialogBuilder(this@MainActivity).setTitle("Playlists").setItems(lists.map { it.name }.toTypedArray()) { _, i -> showPlaylist(lists[i].id) }
                .setPositiveButton("Create") { _, _ -> input("Playlist name") { name -> if (name.isNotBlank()) launchUi { dao.createPlaylist(Playlist(name = name)); listPlaylists() } } }.setNegativeButton("Close", null).show()
        }
    }
    private fun showPlaylist(id: Long) {
        screen = Screen.PLAYLIST; activePlaylist = id
        val actions = setup("Playlist")
        job = launchUi {
            val items = dao.playlistItems(id); status.text = "${items.size} tracks"
            adapter.submit(items.map { LibraryItem(it.uri, it.title, mimeType = it.mimeType) })
            actions.addView(button("Play all") {
                if (items.isNotEmpty()) startActivity(Intent(this@MainActivity, PlayerActivity::class.java)
                    .putStringArrayListExtra("queue", ArrayList(items.map { it.uri }))
                    .putStringArrayListExtra("titles", ArrayList(items.map { it.title }))
                    .putStringArrayListExtra("mimes", ArrayList(items.map { it.mimeType })))
            })
            actions.addView(button("Delete playlist") {
                MaterialAlertDialogBuilder(this@MainActivity).setTitle("Delete playlist?").setMessage("Media files are retained.")
                    .setPositiveButton("Delete") { _, _ -> launchUi { dao.deletePlaylist(id); showTab(1) } }.setNegativeButton("Cancel", null).show()
            })
        }
    }
    private suspend fun choosePlaylist(item: LibraryItem) {
        val lists = dao.playlists()
        if (lists.isEmpty()) { input("New playlist name") { name -> if (name.isNotBlank()) launchUi { val id = dao.createPlaylist(Playlist(name = name)); dao.addToPlaylist(PlaylistItem(id, item.uri, item.title, mimeType = item.mimeType)) } }; return }
        MaterialAlertDialogBuilder(this).setTitle("Add to playlist").setItems(lists.map { it.name }.toTypedArray()) { _, i ->
            launchUi { dao.addToPlaylist(PlaylistItem(lists[i].id, item.uri, item.title, mimeType = item.mimeType)); Toast.makeText(this@MainActivity, "Added to playlist", Toast.LENGTH_SHORT).show() }
        }.show()
    }
    private fun showSettings() {
        if (browser.settingsDetails) { showSettingsDetails(); return }
        job?.cancel(); content.removeAllViews(); searchHandler = null
        content.addView(settingsHub(listOf(
            HubAction("File Transfer", R.drawable.hub_transfer, "Local copy / move") { selectTab(3) },
            HubAction("Private Folder", R.drawable.hub_lock, "Not included"),
            HubAction("Video Playlists", R.drawable.hub_playlist, "On this device") { listPlaylists() },
            HubAction("Media Manager", R.drawable.hub_files, "Browse files") { selectTab(3) },
            HubAction("Local Network", R.drawable.hub_network, "Not included"),
            HubAction("Music Player", R.drawable.hub_music, "Your audio") { selectTab(2) },
            HubAction("Cloud Drive", R.drawable.hub_cloud, "Android providers") {
                MaterialAlertDialogBuilder(this).setTitle("Cloud document provider")
                    .setMessage("Choose a folder from an installed Android document provider. Its app controls sign-in, availability and offline access. TN-Player browses that granted folder; cloud sync is not included.")
                    .setPositiveButton("Choose folder") { _, _ -> openTree.launch(null) }.setNegativeButton("Cancel", null).show()
            },
            HubAction("Recycle Bin", R.drawable.hub_trash, "Not included")
        ), listOf(
            HubAction("App Theme", R.drawable.hub_theme, "System, light, dark or AMOLED") { themeDialog() },
            HubAction("Settings", R.drawable.hub_settings, "Playback, gestures, permissions and backup") { browser.settingsDetails = true; showSettings() },
            HubAction("Custom Pop-up Play", R.drawable.hub_popup, "Picture-in-picture controls") { popupPreferences() },
            HubAction("View & layout", R.drawable.hub_files, "Folders, list/grid, sort and visible fields") {
                libraryViewDialog(settings.libraryDisplay, settings.hidden) { value, hidden ->
                    if (value.mode != settings.libraryDisplay.mode) browser.mediaFolders.clear()
                    settings.libraryDisplay = value; settings.hidden = hidden
                }
            },
            HubAction("Help & supported features", R.drawable.hub_help) { help() }
        )))
    }
    private fun themeDialog() {
        MaterialAlertDialogBuilder(this).setTitle("App Theme").setItems(arrayOf("System", "Light", "Dark", "AMOLED black")) { _, i ->
            settings.amoled = i == 3
            settings.theme = listOf(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_NO,
                AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.MODE_NIGHT_YES)[i]
            AppCompatDelegate.setDefaultNightMode(settings.theme); recreate()
        }.show()
    }
    private fun popupPreferences() {
        if (Build.VERSION.SDK_INT < 26 || !packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            info("Pop-up play", "Picture-in-picture requires Android 8 or newer and device support."); return
        }
        MaterialAlertDialogBuilder(this).setTitle("Custom Pop-up Play")
            .setMultiChoiceItems(arrayOf("Auto PiP when leaving a playing video"), booleanArrayOf(settings.autoPip)) { _, _, checked -> settings.autoPip = checked }
            .setNeutralButton("Android PiP access") { _, _ ->
                try { startActivity(Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS", Uri.parse("package:$packageName"))) }
                catch (_: ActivityNotFoundException) { info("PiP access", "Manage picture-in-picture in Android app settings.") }
            }.setPositiveButton("Done", null).show()
    }
    private fun showSettingsDetails() {

        job?.cancel(); content.removeAllViews()
        val scroll = ScrollView(this); val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(16), dp(8), 0) }
        scroll.addView(box); content.addView(scroll)
        box.addView(button("Back to Me") { browser.settingsDetails = false; showSettings() })
        box.addView(TextView(this).apply { text = "Settings"; textSize = 28f; setPadding(0, 0, 0, dp(16)) })
        fun toggle(label: String, value: Boolean, change: (Boolean) -> Unit) {
            box.addView(MaterialSwitch(this).apply {
                text = label
                // This screen labels the switch beside its thumb. Some MaterialComponents
                // themes enable thumb text without supplying on/off strings.
                textOn = ""
                textOff = ""
                showText = false
                minHeight = dp(56)
                isChecked = value
                setOnCheckedChangeListener { _, enabled -> change(enabled) }
            })
        }
        toggle("Resume unfinished media", settings.resume) { settings.resume = it }
        toggle("Auto picture-in-picture for videos", settings.autoPip) { settings.autoPip = it }
        toggle("Show hidden files in chosen folders", settings.hidden) { settings.hidden = it }
        toggle("Player swipe and pinch gestures", settings.gestures) { settings.gestures = it }
        toggle("AMOLED black theme", settings.amoled) {
            settings.amoled = it
            if (it) { settings.theme = AppCompatDelegate.MODE_NIGHT_YES; AppCompatDelegate.setDefaultNightMode(settings.theme) }
            recreate()
        }
        box.addView(button("Theme") { MaterialAlertDialogBuilder(this).setTitle("Theme").setItems(arrayOf("System", "Light", "Dark")) { _, i ->
            settings.amoled = false
            settings.theme = listOf(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES)[i]
            AppCompatDelegate.setDefaultNightMode(settings.theme); recreate()
        }.show() })
        box.addView(button("Seek interval: ${settings.seekMs / 1000}s") { MaterialAlertDialogBuilder(this).setTitle("Seek interval").setItems(arrayOf("5 seconds", "10 seconds", "30 seconds")) { _, i ->
            settings.seekMs = listOf(5000L, 10000L, 30000L)[i]; showSettings()
        }.show() })
        box.addView(button("Enable notifications") { if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else info("Notifications", "Manage TN-Player notifications in Android Settings.") })
        box.addView(button("App permissions") {
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        box.addView(button("Export library backup") {
            MaterialAlertDialogBuilder(this).setTitle("Export local collections")
                .setMessage("The JSON backup contains collection names, local media paths and preferences. It is not encrypted. Network sources, credentials, media bytes and storage grants are excluded.")
                .setPositiveButton("Choose destination") { _, _ -> exportBackup.launch("TN-Player-backup.json") }.setNegativeButton("Cancel", null).show()
        })
        box.addView(button("Restore library backup") { importBackup.launch(arrayOf("application/json", "text/plain")) })
        box.addView(button("Gesture guide") { info("Player gestures", "Enable gestures, then use two fingers on the video: swipe horizontally to seek, vertically on the left for volume, or on the right for this window's brightness. Pinch to zoom up to 3×. Double-tap either side to seek. Standard player buttons remain available; gestures are disabled while controls are locked or PiP is active.") })
        box.addView(button("Help & supported features") { help() })
        box.addView(button("Privacy") { info("Privacy", "Media, history, favorites and settings remain on your device. No analytics, ads, crash upload, account or cloud sync is included. Saved network addresses and playback checkpoints are encrypted using local Android Keystore-protected keys. Streams contact the URL you choose. Android backups are disabled. Clearing app data or uninstalling removes this app's database and downloads. RTSP is supported but is not encrypted; use trusted sources.") })
        box.addView(ImageView(this).apply { setImageResource(R.drawable.tn_logo); contentDescription = "TN-Player logo" }, LinearLayout.LayoutParams(dp(96), dp(96)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        box.addView(TextView(this).apply { text = "TN-Player 1.0.0\ncom.tplayer.mediaplayer\nAndroid 5.0+ · Native Media3 playback\n\nCodec support depends on device and source. FFmpeg, cast, cloud sync and private vault are not included in this build."; textSize = 13f; setPadding(dp(8), dp(20), dp(8), dp(20)) })
    }
    private fun help() { info("TN-Player help", "Videos/Music: grant library access to see media folders and counts, tap a folder to open it, or use Open file. View & layout offers All folders, Files, Folders, list/grid, sorting, fields and minimum duration. Folder counts include only accessible indexed media; Files → Choose folder opens other document-provider folders. Selected-video access is supported on Android 14.\n\nFiles: choose a folder, tap a folder to enter, or use ⋮ for copy, move, rename, share and delete. Copy/move supports individual files. Android controls which folders are accessible.\n\nPlayer: use built-in controls for seeking, audio/subtitle tracks and speed. More offers A–B repeat, bookmarks, external SRT/VTT subtitles, aspect ratio and sleep timer. PiP requires Android 8+ and device support. Audio continues in the background.\n\nStreams: add a direct HTTPS or RTSP media URL. Web page links and DRM-locked services are not media URLs. Downloads support direct HTTPS files only.\n\nLibrary menu: favorites, history, playlists and downloads. No sample files are shown as your own media.\n\nThis is the native core build. Universal FFmpeg decoding, SMB/FTP, casting, encrypted vault, advanced PCM processing and additional widget types require further development.") }
}
