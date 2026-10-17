package com.tplayer.mediaplayer.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.materialswitch.MaterialSwitch

 data class PlayerTool(val title: String, val icon: Int, val action: () -> Unit)
fun Context.playerTools(tools: List<PlayerTool>, videoDisplay: Boolean, shortcuts: Boolean,
    changeVideo: (Boolean) -> Unit, changeShortcuts: (Boolean) -> Unit, customize: () -> Unit) {
    val dialog = BottomSheetDialog(this)
    var nextAction: (() -> Unit)? = null
    dialog.setOnDismissListener { nextAction?.invoke() }
    fun activate(action: () -> Unit) { nextAction = action; dialog.dismiss() }
    val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(14, 20, 29)) }
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(16)) }
    scroll.addView(box)
    box.addView(TextView(this).apply { text = "Playback tools"; textSize = 22f; setTextColor(Color.WHITE); setPadding(dp(8), 0, 0, dp(12)) })
    val grid = GridLayout(this).apply { columnCount = if (resources.configuration.screenWidthDp >= 600) 5 else if (resources.configuration.screenWidthDp >= 380) 4 else 3 }
    tools.forEachIndexed { index, tool ->
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; minimumHeight = dp(100); setPadding(dp(4), dp(8), dp(4), dp(8))
            isClickable = true; isFocusable = true; contentDescription = tool.title
            setOnClickListener { activate(tool.action) }
        }
        cell.addView(ImageView(this).apply { setImageResource(tool.icon); imageTintList = ColorStateList.valueOf(Color.WHITE); importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(30), dp(30)))
        cell.addView(TextView(this).apply { text = tool.title; textSize = 13f; maxLines = 2; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setPadding(0, dp(10), 0, 0); setOnClickListener { activate(tool.action) }; importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(-1, -2))
        grid.addView(cell, GridLayout.LayoutParams(GridLayout.spec(index / grid.columnCount), GridLayout.spec(index % grid.columnCount, 1f)).apply { width = 0; height = -2 })
    }
    box.addView(grid)
    fun toggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
        box.addView(MaterialSwitch(this).apply { text = label; textOn = ""; textOff = ""; showText = false; setTextColor(Color.WHITE); minHeight = dp(56); isChecked = checked; setOnCheckedChangeListener { _, value -> change(value) } })
    }
    toggle("Video display", videoDisplay, changeVideo)
    toggle("Show shortcuts", shortcuts, changeShortcuts)
    box.addView(button("Customise items") { activate(customize) })
    box.addView(button("Close tools") { dialog.dismiss() })
    dialog.setContentView(scroll)
    dialog.setOnShowListener {
        val sheet = dialog.findViewById<android.widget.FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
        sheet?.let { it.layoutParams = it.layoutParams.apply { height = (resources.displayMetrics.heightPixels * .82f).toInt() }; BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED }
    }
    dialog.show()
}
