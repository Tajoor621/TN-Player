package com.tplayer.mediaplayer.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("tplayer", Context.MODE_PRIVATE)
    var theme: Int
        get() = prefs.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) { prefs.edit().putInt("theme", value).apply() }
    var autoPip: Boolean
        get() = prefs.getBoolean("autoPip", true)
        set(value) { prefs.edit().putBoolean("autoPip", value).apply() }
    var resume: Boolean
        get() = prefs.getBoolean("resume", true)
        set(value) { prefs.edit().putBoolean("resume", value).apply() }
    var hidden: Boolean
        get() = prefs.getBoolean("hidden", false)
        set(value) { prefs.edit().putBoolean("hidden", value).apply() }
    var seekMs: Long
        get() = prefs.getLong("seekMs", 10000L)
        set(value) { prefs.edit().putLong("seekMs", value).apply() }
    var tree: String?
        get() = prefs.getString("tree", null)
        set(value) { prefs.edit().putString("tree", value).apply() }
}
