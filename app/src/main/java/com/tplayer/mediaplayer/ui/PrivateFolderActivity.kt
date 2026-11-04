package com.tplayer.mediaplayer.ui

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tplayer.mediaplayer.playback.MediaPlaybackService
import com.tplayer.mediaplayer.playback.PlaybackPersistence
import com.tplayer.mediaplayer.security.PrivateFolder
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/** No media is imported or copied. The PIN gates this app's view of a user-granted local folder. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PrivateFolderActivity : AppCompatActivity() {
    private val privacy by lazy { PrivateFolder.get(this) }
    private lateinit var box: LinearLayout
    private var current: DocumentFile? = null
    private val parents = mutableListOf<DocumentFile>()
    private var openingPlayer = false
    private var work: Job? = null
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) { }
            askPin("Create PIN · 6–32 digits") { first ->
                askPin("Repeat PIN") { second ->
                    if (!first.contentEquals(second)) { first.fill('\u0000'); second.fill('\u0000'); info("PIN mismatch", "The two PINs differ. Choose the folder again.") }
                    else launchWork {
                        try {
                            stopPlayback()
                            Glide.get(applicationContext).clearMemory()
                            withContext(Dispatchers.IO) { Glide.get(applicationContext).clearDiskCache() }
                            privacy.configure(uri, first)
                            privacy.authenticate(first)
                            render()
                        } finally { first.fill('\u0000'); second.fill('\u0000') }
                    }
                }
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (savedInstanceState == null) privacy.lock()
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        setContentView(box)
        lifecycleScope.launch { privacy.unlocked.collect { if (!it) { current = null; parents.clear(); render() } } }
    }
    private fun launchWork(action: suspend () -> Unit) { work?.cancel(); work = lifecycleScope.launch {
        try { action() } catch (e: CancellationException) { throw e }
        catch (_: Exception) { info("Private folder unavailable", "Check the storage grant, PIN or Android Keystore state. No media files were moved.") }
    } }
    private fun askPin(title: String, action: (CharArray) -> Unit) {
        val input = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD; contentDescription = title; minHeight = dp(48) }
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle(title).setView(input).setNegativeButton("Cancel", null).setPositiveButton("Continue", null).create()
        dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
            val pin = input.text.toString().toCharArray(); input.text.clear()
            if (pin.size !in 6..32 || pin.any { it !in '0'..'9' }) { pin.fill('\u0000'); input.error = "Use 6–32 digits" }
            else { dialog.dismiss(); action(pin) }
        } }; dialog.show()
    }
    override fun onResume() { super.onResume(); openingPlayer = false; render() }
    private fun render() {
        if (!::box.isInitialized) return
        box.removeAllViews()
        box.addView(button("Back") { privacy.lock(); finish() })
        box.addView(TextView(this).apply { text = getString(com.tplayer.mediaplayer.R.string.private_folder_title); textSize = 24f })
        launchWork {
            val root = privacy.configuration()
            if (root == null) {
                box.addView(TextView(this@PrivateFolderActivity).apply { text = getString(com.tplayer.mediaplayer.R.string.private_folder_scope) })
                box.addView(button("Choose local folder and set PIN") { picker.launch(null) }); return@launchWork
            }
            if (!privacy.isUnlocked()) {
                box.addView(button("Unlock with PIN") { askPin("Private folder PIN") { pin -> launchWork {
                    try {
                        if (privacy.authenticate(pin)) render() else info("Not unlocked", "Incorrect PIN or retry delay active. After five failures, wait at least 30 seconds.")
                    } finally { pin.fill('\u0000') }
                } } }); return@launchWork
            }
            box.addView(button("Lock") { privacy.lock() })
            box.addView(button("Remove folder protection") {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@PrivateFolderActivity).setTitle("Remove protection?")
                    .setMessage("The folder will become visible in public library views. Media files remain in place.")
                    .setNegativeButton("Keep protected", null).setPositiveButton("Remove") { _, _ -> launchWork { privacy.removeProtection(); render() } }.show()
            })
            if (parents.isNotEmpty()) box.addView(button("Up") { current = parents.removeAt(parents.lastIndex); render() })
            val target = current ?: if (Uri.parse(root.uri).scheme == "file") DocumentFile.fromFile(java.io.File(checkNotNull(Uri.parse(root.uri).path))) else DocumentFile.fromTreeUri(this@PrivateFolderActivity, Uri.parse(root.uri))
            current = target
            val entries = withContext(Dispatchers.IO) { target?.listFiles()?.mapNotNull { file ->
                ensureActive()
                if (!privacy.isPrivate(file.uri.toString())) null else file to com.tplayer.mediaplayer.data.LibraryItem(file.uri.toString(), file.name ?: "Private file", if (file.isDirectory) "Folder" else "Private media", file.type.orEmpty())
            }?.sortedWith(compareBy<Pair<DocumentFile, com.tplayer.mediaplayer.data.LibraryItem>> { it.second.detail != "Folder" }.thenBy { it.second.title.lowercase() }).orEmpty() }
            if (!privacy.isUnlocked()) return@launchWork
            val adapter = MediaAdapter({ item ->
                val selected = entries.firstOrNull { it.second.uri == item.uri }?.first ?: return@MediaAdapter
                if (selected.isDirectory) { current?.let { parents += it }; current = selected; render() }
                else {
                    openingPlayer = true
                    startActivity(Intent(this@PrivateFolderActivity, PlayerActivity::class.java).setData(selected.uri).putExtra("title", "Private media").putExtra("mime", item.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            }, { item -> info("Private file", item.title) })
            adapter.submit(entries.map { it.second })
            box.addView(RecyclerView(this@PrivateFolderActivity).apply { layoutManager = LinearLayoutManager(this@PrivateFolderActivity); this.adapter = adapter }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }
    private suspend fun stopPlayback() {
        val future = MediaController.Builder(this, SessionToken(this, ComponentName(this, MediaPlaybackService::class.java))).buildAsync()
        try {
            val player = withContext(Dispatchers.IO) { future.get(10, TimeUnit.SECONDS) }
            player.stop(); player.clearMediaItems()
            PlaybackPersistence.get(this).save(null); PlaybackPersistence.get(this).flush()
        } finally { MediaController.releaseFuture(future) }
    }
    override fun onStop() { if (!isChangingConfigurations && !openingPlayer) privacy.lock(); work?.cancel(); super.onStop() }
    override fun onDestroy() { if (isFinishing && !openingPlayer) privacy.lock(); super.onDestroy() }
}
