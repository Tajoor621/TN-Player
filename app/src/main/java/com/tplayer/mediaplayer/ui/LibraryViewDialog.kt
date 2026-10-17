package com.tplayer.mediaplayer.ui

import android.content.Context
import android.view.View
import android.widget.*
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tplayer.mediaplayer.data.*

/** Edits a draft; Cancel never changes stored preferences. */
fun Context.libraryViewDialog(initial: LibraryDisplay, hidden: Boolean, apply: (LibraryDisplay, Boolean) -> Unit) {
    var draft = initial
    var showHidden = hidden
    val scroll = ScrollView(this)
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), dp(16)) }
    scroll.addView(box)
    fun heading(title: String) { box.addView(TextView(this).apply { text = title; textSize = 18f; setPadding(0, dp(20), 0, dp(8)); androidx.core.view.ViewCompat.setAccessibilityHeading(this, true) }) }
    fun choices(labels: List<String>, selected: Int, action: (Int) -> Unit) {
        val group = ChipGroup(this).apply { isSingleSelection = true; isSelectionRequired = true; chipSpacingHorizontal = dp(6); chipSpacingVertical = dp(4) }
        labels.forEachIndexed { index, label -> group.addView(Chip(this).apply {
            id = View.generateViewId(); text = label; isCheckable = true; minHeight = dp(48); isChecked = index == selected
            setOnCheckedChangeListener { _, checked -> if (checked) action(index) }
        }) }
        box.addView(group)
    }
    fun field(parent: LinearLayout, title: String, checked: Boolean, action: (Boolean) -> Unit) {
        parent.addView(CheckBox(this).apply { text = title; minHeight = dp(48); isChecked = checked; setOnCheckedChangeListener { _, value -> action(value) } })
    }
    heading("View mode")
    choices(MediaViewMode.values().map { it.label }, draft.mode.ordinal) { draft = draft.copy(mode = MediaViewMode.values()[it]) }
    heading("Layout")
    choices(listOf("List", "Grid"), if (draft.grid) 1 else 0) { draft = draft.copy(grid = it == 1) }
    heading("Sort")
    choices(MediaSort.values().map { it.label }, draft.sort.ordinal) { draft = draft.copy(sort = MediaSort.values()[it]) }
    choices(listOf("↑ Ascending", "↓ Descending"), if (draft.descending) 1 else 0) { draft = draft.copy(descending = it == 1) }
    val fields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
    box.addView(button("Fields ⌄") { fields.visibility = if (fields.visibility == View.VISIBLE) View.GONE else View.VISIBLE })
    box.addView(fields)
    field(fields, "Duration", draft.duration) { draft = draft.copy(duration = it) }
    field(fields, "File size", draft.size) { draft = draft.copy(size = it) }
    field(fields, "Resolution", draft.resolution) { draft = draft.copy(resolution = it) }
    field(fields, "Folder path", draft.path) { draft = draft.copy(path = it) }
    field(fields, "Modified date", draft.date) { draft = draft.copy(date = it) }
    field(fields, "Playback status", draft.played) { draft = draft.copy(played = it) }
    field(fields, "Capture frame rate", draft.frameRate) { draft = draft.copy(frameRate = it) }
    field(fields, "Media type", draft.type) { draft = draft.copy(type = it) }
    val advanced = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
    box.addView(button("Advanced ⌄") { advanced.visibility = if (advanced.visibility == View.VISIBLE) View.GONE else View.VISIBLE })
    box.addView(advanced)
    field(advanced, "Show hidden indexed media / chosen-folder entries", showHidden) { showHidden = it }
    advanced.addView(TextView(this).apply { text = "Minimum duration"; setPadding(0, dp(12), 0, 0) })
    val minimum = EditText(this).apply { setSingleLine(); inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(draft.minimumSeconds.toString()); hint = "Seconds (0–3600)"; minHeight = dp(48); contentDescription = "Minimum duration in seconds" }
    advanced.addView(minimum)
    advanced.addView(TextView(this).apply {
        text = "Folders contain accessible indexed media only. Android may omit .nomedia and protected locations. Use Files → Choose folder for other provider storage.\n\nDate means last modified. Played time/status use this app's retained history (up to 200 items). Folder length/size are totals; date/played time use the newest member. Capture fps is Android's indexed capture rate, not a guaranteed encoded frame rate; unavailable values sort as zero."
        textSize = 13f; setPadding(0, dp(12), 0, 0)
    })
    val dialog = MaterialAlertDialogBuilder(this).setTitle("View & layout").setView(scroll)
        .setNegativeButton("Cancel", null).setPositiveButton("Done", null).create()
    dialog.setOnShowListener {
        dialog.getButton(-1).setOnClickListener {
            val seconds = minimum.text.toString().toIntOrNull()
            if (seconds == null || seconds !in 0..3600) { advanced.visibility = View.VISIBLE; minimum.error = "Enter 0–3600 seconds"; return@setOnClickListener }
            apply(draft.copy(minimumSeconds = seconds), showHidden); dialog.dismiss()
        }
    }
    dialog.show()
}
