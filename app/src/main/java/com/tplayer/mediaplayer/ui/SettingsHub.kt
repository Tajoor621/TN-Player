package com.tplayer.mediaplayer.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.tplayer.mediaplayer.R

data class HubAction(val title: String, val icon: Int, val detail: String = "", val action: (() -> Unit)? = null)

fun Context.settingsHub(tiles: List<HubAction>, rows: List<HubAction>): ScrollView {
    val surface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.WHITE)
    val accent = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.BLUE)
    val scroll = ScrollView(this).apply { setBackgroundColor(MaterialColors.layer(surface, accent, .04f)) }
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(16), dp(8), dp(20)) }
    scroll.addView(box)
    box.addView(TextView(this).apply { text = "Me"; textSize = 32f; setPadding(dp(8), 0, 0, dp(6)); androidx.core.view.ViewCompat.setAccessibilityHeading(this, true) })
    box.addView(TextView(this).apply { text = "Make it yours"; textSize = 16f; alpha = .7f; setPadding(dp(8), 0, 0, dp(20)) })
    fun card(child: android.view.View): MaterialCardView = MaterialCardView(this).apply {
        radius = dp(20).toFloat(); cardElevation = dp(1).toFloat(); setCardBackgroundColor(surface)
        addView(child); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) }
    }
    val grid = GridLayout(this).apply { columnCount = if (resources.configuration.screenWidthDp < 340) 2 else 3; setPadding(dp(6), dp(12), dp(6), dp(12)) }
    tiles.forEachIndexed { index, tile ->
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; minimumHeight = dp(132); setPadding(dp(4), dp(12), dp(4), dp(12))
            isFocusable = tile.action != null; isClickable = tile.action != null
            contentDescription = "${tile.title}, ${tile.detail}"
            tile.action?.let { action -> setOnClickListener { action() } }
        }
        cell.addView(ImageView(this).apply { setImageResource(tile.icon); imageTintList = ColorStateList.valueOf(accent); importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(36), dp(36)))
        cell.addView(TextView(this).apply { text = tile.title; textSize = 14f; gravity = Gravity.CENTER; setPadding(0, dp(12), 0, 0); maxLines = 2 }, LinearLayout.LayoutParams(-1, -2))
        if (tile.detail.isNotEmpty()) cell.addView(TextView(this).apply { text = tile.detail; textSize = 11f; gravity = Gravity.CENTER; alpha = .65f; maxLines = 2 }, LinearLayout.LayoutParams(-1, -2))
        if (tile.action == null) cell.alpha = .5f
        grid.addView(cell, GridLayout.LayoutParams(GridLayout.spec(index / grid.columnCount), GridLayout.spec(index % grid.columnCount, 1f)).apply { width = 0; height = -2 })
    }
    box.addView(card(grid))
    val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    rows.forEach { item ->
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(76); setPadding(dp(18), dp(14), dp(12), dp(14))
            isFocusable = true; isClickable = true; contentDescription = "${item.title}, ${item.detail}"
            setOnClickListener { item.action?.invoke() }
        }
        row.addView(ImageView(this).apply { setImageResource(item.icon); imageTintList = ColorStateList.valueOf(accent); importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(18) })
        val label = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        label.addView(TextView(this).apply { text = item.title; textSize = 18f })
        if (item.detail.isNotEmpty()) label.addView(TextView(this).apply { text = item.detail; textSize = 12f; alpha = .65f })
        row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(this).apply { text = "›"; textSize = 26f; alpha = .6f; importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO })
        list.addView(row)
    }
    box.addView(card(list))
    box.addView(TextView(this).apply { text = "TN-Player 1.0.0 · Your media, your device"; textSize = 12f; alpha = .65f; gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(12)) })
    return scroll
}
