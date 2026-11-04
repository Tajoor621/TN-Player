package com.tplayer.mediaplayer.ui

import android.os.Build
import android.view.View
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** One inset owner so Material navigation and content do not pad twice. */
internal fun applyActivityInsets(window: Window, root: View, includeIme: Boolean = false,
    includeBars: () -> Boolean = { true }, includeCutout: () -> Boolean = { true }) {
    if (Build.VERSION.SDK_INT < 35) return
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val left = root.paddingLeft
    val top = root.paddingTop
    val right = root.paddingRight
    val bottom = root.paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
        var types = 0
        if (includeBars()) types = types or WindowInsetsCompat.Type.systemBars()
        if (includeCutout()) types = types or WindowInsetsCompat.Type.displayCutout()
        if (includeIme) types = types or WindowInsetsCompat.Type.ime()
        val safe = insets.getInsets(types)
        view.setPadding(left + safe.left, top + safe.top, right + safe.right, bottom + safe.bottom)
        WindowInsetsCompat.CONSUMED
    }
    ViewCompat.requestApplyInsets(root)
}
