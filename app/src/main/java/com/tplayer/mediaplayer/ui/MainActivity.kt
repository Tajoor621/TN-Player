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
}
class MainActivity : AppCompatActivity() {
    private val settings by lazy { Settings(this) }
    private val dao by lazy { MediaDatabase.get(this).media() }
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var adapter: MediaAdapter
    private var tab = 1
    private var job: Job? = null
    private val browser by viewModels<BrowserState>()
    private val transfers by viewModels<FileTransferModel>()
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
        super.onCreate(savedInstanceState)
        tab = savedInstanceState?.getInt("tab") ?: if (intent.action == "com.tplayer.OPEN_STREAMS") 4 else 1
        screen = if (savedInstanceState != null) Screen.restore(savedInstanceState.getString("screen")) else Screen.forTab(tab)
        activePlaylist = savedInstanceState?.getLong("playlist", -1L)?.takeIf { it >= 0 }
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
                        if (state.succeeded) clipboard = null
                        if (screen == Screen.FILES) showFiles()
                        transfers.acknowledge(); info("File transfer", it)
                    }
                }
            }
        }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), 0) }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = BottomNavigationView(this).apply {
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
        super.onSaveInstanceState(outState)
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
        job?.cancel(); content.removeAllViews(); searchHandler = null
        val heading = TextView(this).apply { text = title; textSize = 28f; setPadding(dp(8), dp(16), 0, dp(12)); accessibilityHeadingCompat() }
        content.addView(heading)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(actions) }
        content.addView(scroll)
        status = TextView(this).apply { textSize = 13f; setPadding(dp(8), dp(8), dp(8), dp(8)); accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE }
        content.addView(status)
        val search = EditText(this).apply { hint = "Search this list"; contentDescription = "Search this list"; setSingleLine(); minHeight = dp(48); setText(browser.searches[screen].orEmpty()) }
        content.addView(search)
        adapter = MediaAdapter({ item ->
            if (screen == Screen.FILES && item.detail == "Folder") {
                folder?.let { parents += it }; folder = documents[item.uri]; showFiles()
            } else play(item)
        }, { item -> if (screen == Screen.FILES) fileOptions(item) else mediaOptions(item) })
        val list = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@MainActivity); adapter = this@MainActivity.adapter }
        content.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        adapter.filter(browser.searches[screen].orEmpty())
        search.doAfterTextChanged { value ->
            val query = value?.toString().orEmpty()
            browser.searches[screen] = query
            val handler = searchHandler
            if (handler != null) handler(query) else adapter.filter(query)
        }
        return actions
    }
    private fun TextView.accessibilityHeadingCompat() { androidx.core.view.ViewCompat.setAccessibilityHeading(this, true) }
    private fun showTab(value: Int) {
        tab = value; screen = Screen.forTab(value); activePlaylist = null
        when (value) { 1, 2 -> showMedia(value == 1); 3 -> showFiles(); 4 -> showStreams(); 5 -> showSettings() }
    }
    private fun showMedia(video: Boolean) {
        val actions = setup(if (video) "Videos" else "Music")
        actions.addView(button("Open file") { openFile.launch(arrayOf("video/*", "audio/*")) })
        actions.addView(button("Refresh") { showTab(tab) })
        actions.addView(button("Resume last") { startActivity(Intent(this, PlayerActivity::class.java).putExtra("resume", true)) })
        val required = if (Build.VERSION.SDK_INT >= 33) {
            if (video) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_MEDIA_AUDIO
        } else Manifest.permission.READ_EXTERNAL_STORAGE
        val hasFull = ContextCompat.checkSelfPermission(this, required) == PackageManager.PERMISSION_GRANTED
        val hasPartial = video && Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        actions.addView(button(if (hasFull) "Library access" else "Grant access") {
            permissions.launch(if (video && Build.VERSION.SDK_INT >= 34) arrayOf(required, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) else arrayOf(required))
        })
        if (!hasFull && !hasPartial) { status.text = "Grant library access, or open an individual file without library permission."; return }
        val loaded = mutableListOf<LibraryItem>()
        var before = Long.MAX_VALUE
        var query = browser.searches[screen].orEmpty()
        val more = button("Load more") {}
        actions.addView(more)
        fun load(reset: Boolean) {
            job?.cancel(); more.isEnabled = false; status.text = "Reading your media…"
            if (reset) { before = Long.MAX_VALUE; loaded.clear(); adapter.submit(emptyList()) }
            job = launchUi {
                try {
                    if (reset && query.isNotBlank()) delay(250)
                    val page = LibraryRepository(this@MainActivity).page(video, before, query)
                    before = page.nextKey; loaded.addAll(page.items); adapter.submit(loaded.toList()); more.isEnabled = page.hasMore
                    status.text = if (loaded.isEmpty()) "No matching media. Try Open file or grant access again."
                        else "${loaded.size}${if (page.hasMore) "+" else ""} loaded · Newest indexed first" + if (hasPartial && !hasFull) " · Selected videos only" else ""
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { status.text = "Library unavailable. Open a file or grant access again." }
            }
        }
        more.setOnClickListener { load(false) }
        searchHandler = { value -> query = value; load(true) }
        load(true)
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
                val snapshot = withContext(Dispatchers.IO) {
                    val entries = target.listFiles().map { doc ->
                        ensureActive()
                        val name = doc.name ?: "Untitled"; val directory = doc.isDirectory
                        doc to LibraryItem(doc.uri.toString(), name, if (directory) "Folder" else "${doc.length() / 1024} KB", doc.type.orEmpty())
                    }.filter { settings.hidden || !it.second.title.startsWith('.') }
                        .sortedWith(compareBy<Pair<DocumentFile, LibraryItem>> { it.second.detail != "Folder" }.thenBy { it.second.title.lowercase() })
                    Triple(entries, target.name ?: "Folder", target.canWrite())
                }
                documents = snapshot.first.associate { it.first.uri.toString() to it.first }
                adapter.submit(snapshot.first.map { it.second })
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
        val options = if (item.detail == "Folder") arrayOf("Rename", "Delete", "Properties") else arrayOf("Play", "Copy", "Move", "Rename", "Delete", "Share", "Properties")
        MaterialAlertDialogBuilder(this).setTitle(item.title).setItems(options) { _, index ->
            when (options[index]) {
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
                "Properties" -> info(item.title, "${item.detail}\n${doc.uri}")
            }
        }.show()
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
            if (screen == Screen.STREAMS) options += listOf("Edit stream", "Download HTTPS file", "Remove saved stream")
            if (screen == Screen.PLAYLIST && activePlaylist != null) options += "Remove from playlist"
            MaterialAlertDialogBuilder(this@MainActivity).setTitle(item.title).setItems(options.toTypedArray()) { _, index ->
                launchUi {
                    when (options[index]) {
                        "Add favorite" -> dao.addFavorite(Favorite(item.uri, item.title, item.mimeType))
                        "Remove favorite" -> dao.removeFavorite(item.uri)
                        "Add to playlist" -> choosePlaylist(item)
                        "Properties" -> info(item.title, "${item.detail}\n${StreamFormat.displayAddress(item.uri)}")
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
        job?.cancel(); content.removeAllViews()
        val scroll = ScrollView(this); val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(16), dp(8), 0) }
        scroll.addView(box); content.addView(scroll)
        box.addView(TextView(this).apply { text = "Make it yours"; textSize = 28f; setPadding(0, 0, 0, dp(16)) })
        fun toggle(label: String, value: Boolean, change: (Boolean) -> Unit) { box.addView(MaterialSwitch(this).apply { text = label; minHeight = dp(56); isChecked = value; setOnCheckedChangeListener { _, enabled -> change(enabled) } }) }
        toggle("Resume unfinished media", settings.resume) { settings.resume = it }
        toggle("Auto picture-in-picture for videos", settings.autoPip) { settings.autoPip = it }
        toggle("Show hidden files in chosen folders", settings.hidden) { settings.hidden = it }
        box.addView(button("Theme") { MaterialAlertDialogBuilder(this).setTitle("Theme").setItems(arrayOf("System", "Light", "Dark")) { _, i ->
            settings.theme = listOf(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES)[i]
            AppCompatDelegate.setDefaultNightMode(settings.theme)
        }.show() })
        box.addView(button("Seek interval: ${settings.seekMs / 1000}s") { MaterialAlertDialogBuilder(this).setTitle("Seek interval").setItems(arrayOf("5 seconds", "10 seconds", "30 seconds")) { _, i ->
            settings.seekMs = listOf(5000L, 10000L, 30000L)[i]; showSettings()
        }.show() })
        box.addView(button("Enable notifications") { if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else info("Notifications", "Manage TN-Player notifications in Android Settings.") })
        box.addView(button("App permissions") {
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        box.addView(button("Help & supported features") { help() })
        box.addView(button("Privacy") { info("Privacy", "Media, history, favorites and settings remain on your device. No analytics, ads, crash upload, account or cloud sync is included. Saved network addresses and playback checkpoints are encrypted using local Android Keystore-protected keys. Streams contact the URL you choose. Android backups are disabled. Clearing app data or uninstalling removes this app's database and downloads. RTSP is supported but is not encrypted; use trusted sources.") })
        box.addView(ImageView(this).apply { setImageResource(R.drawable.tn_logo); contentDescription = "TN-Player logo" }, LinearLayout.LayoutParams(dp(96), dp(96)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        box.addView(TextView(this).apply { text = "TN-Player 1.0.0\ncom.tplayer.mediaplayer\nAndroid 5.0+ · Native Media3 playback\n\nCodec support depends on device and source. FFmpeg, cast, cloud sync and private vault are not included in this build."; textSize = 13f; setPadding(dp(8), dp(20), dp(8), dp(20)) })
    }
    private fun help() { info("TN-Player help", "Videos/Music: grant library access or use Open file. Selected-video access is supported on Android 14.\n\nFiles: choose a folder, tap a folder to enter, or use ⋮ for copy, move, rename, share and delete. Copy/move supports individual files. Android controls which folders are accessible.\n\nPlayer: use built-in controls for seeking, audio/subtitle tracks and speed. More offers A–B repeat, bookmarks, external SRT/VTT subtitles, aspect ratio and sleep timer. PiP requires Android 8+ and device support. Audio continues in the background.\n\nStreams: add a direct HTTPS or RTSP media URL. Web page links and DRM-locked services are not media URLs. Downloads support direct HTTPS files only.\n\nLibrary menu: favorites, history, playlists and downloads. No sample files are shown as your own media.\n\nThis is the native core build. Universal FFmpeg decoding, SMB/FTP, casting, encrypted vault, audio DSP and widgets require further development.") }
}
