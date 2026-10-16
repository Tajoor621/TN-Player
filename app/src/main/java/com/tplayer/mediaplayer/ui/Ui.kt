package com.tplayer.mediaplayer.ui

import android.content.Context
import com.bumptech.glide.Glide
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tplayer.mediaplayer.data.LibraryItem

fun Context.dp(value: Int) = (resources.displayMetrics.density * value).toInt()
fun Context.button(text: String, action: () -> Unit) = MaterialButton(this).apply {
    this.text = text; contentDescription = text; minHeight = dp(48); setOnClickListener { action() }
}
fun Context.info(title: String, text: String) { MaterialAlertDialogBuilder(this).setTitle(title).setMessage(text).setPositiveButton("OK", null).show() }
fun Context.input(title: String, initial: String = "", action: (String) -> Unit) {
    val edit = EditText(this).apply { setText(initial); setSingleLine(); setPadding(dp(24), dp(16), dp(24), dp(16)) }
    MaterialAlertDialogBuilder(this).setTitle(title).setView(edit).setPositiveButton("Save") { _, _ ->
        action(edit.text.toString().trim())
    }.setNegativeButton("Cancel", null).show()
}
class MediaAdapter(private val click: (LibraryItem) -> Unit, private val more: (LibraryItem) -> Unit) : RecyclerView.Adapter<MediaAdapter.Holder>() {
    private var all: List<LibraryItem> = emptyList()
    private var rows: List<LibraryItem> = emptyList()
    private var query = ""
    fun submit(items: List<LibraryItem>) { all = items; filter(query) }
    fun filter(value: String) { query = value; rows = all.filter { it.title.contains(value, true) }; notifyDataSetChanged() }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val ctx = parent.context
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = ctx.dp(76); setPadding(ctx.dp(16), ctx.dp(8), ctx.dp(8), ctx.dp(8))
            isFocusable = true
        }
        val image = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP; importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        row.addView(image, LinearLayout.LayoutParams(ctx.dp(64), ctx.dp(56)).apply { marginEnd = ctx.dp(12) })
        val labels = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(ctx).apply { textSize = 16f; maxLines = 2 }
        val detail = TextView(ctx).apply { textSize = 12f; alpha = .7f }
        labels.addView(title); labels.addView(detail)
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        val menu = ctx.button("⋮") {}.apply { minWidth = ctx.dp(48) }
        row.addView(menu, LinearLayout.LayoutParams(ctx.dp(56), ctx.dp(56)))
        return Holder(row, title, detail, menu, image)
    }
    override fun getItemCount() = rows.size
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = rows[position]
        holder.title.text = item.title; holder.detail.text = item.detail
        val local = android.net.Uri.parse(item.uri).scheme in setOf("content", "file")
        val artwork = item.artworkUri ?: item.uri.takeIf { local && item.mimeType.startsWith("video/") }
        Glide.with(holder.image).clear(holder.image)
        if (artwork != null) Glide.with(holder.image).load(android.net.Uri.parse(artwork))
            .override(holder.itemView.context.dp(96), holder.itemView.context.dp(72)).centerCrop()
            .placeholder(android.R.drawable.ic_media_play).error(android.R.drawable.ic_media_play).into(holder.image)
        else holder.image.setImageResource(if (item.detail == "Folder") android.R.drawable.ic_menu_agenda else android.R.drawable.ic_media_play)
        holder.itemView.setOnClickListener { click(item) }
        holder.itemView.setOnLongClickListener { more(item); true }
        holder.menu.contentDescription = "Options for ${item.title}"
        holder.menu.setOnClickListener { more(item) }
    }
    override fun onViewRecycled(holder: Holder) { Glide.with(holder.image.context.applicationContext).clear(holder.image); super.onViewRecycled(holder) }
    class Holder(row: LinearLayout, val title: TextView, val detail: TextView, val menu: MaterialButton, val image: ImageView) : RecyclerView.ViewHolder(row)
}
