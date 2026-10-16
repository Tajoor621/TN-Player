package com.tplayer.mediaplayer.ui

import android.content.Context
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
        val labels = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(ctx).apply { textSize = 16f; maxLines = 2 }
        val detail = TextView(ctx).apply { textSize = 12f; alpha = .7f }
        labels.addView(title); labels.addView(detail)
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        val menu = ctx.button("⋮") {}.apply { minWidth = ctx.dp(48) }
        row.addView(menu, LinearLayout.LayoutParams(ctx.dp(56), ctx.dp(56)))
        return Holder(row, title, detail, menu)
    }
    override fun getItemCount() = rows.size
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = rows[position]
        holder.title.text = item.title; holder.detail.text = item.detail
        holder.itemView.setOnClickListener { click(item) }
        holder.itemView.setOnLongClickListener { more(item); true }
        holder.menu.contentDescription = "Options for ${item.title}"
        holder.menu.setOnClickListener { more(item) }
    }
    class Holder(row: LinearLayout, val title: TextView, val detail: TextView, val menu: MaterialButton) : RecyclerView.ViewHolder(row)
}
