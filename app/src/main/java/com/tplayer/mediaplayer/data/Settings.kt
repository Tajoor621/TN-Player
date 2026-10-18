package com.tplayer.mediaplayer.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("tplayer", Context.MODE_PRIVATE)
    var theme: Int
        get() = prefs.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) { prefs.edit().putInt("theme", value).apply() }
    var decoderPreference: Int
        get() = prefs.getInt("decoderPreference", 0).coerceIn(0, 2)
        set(value) { prefs.edit().putInt("decoderPreference", value.coerceIn(0, 2)).apply() }
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
    var gestureVolumeRight: Boolean
        get() = prefs.getBoolean("gestureVolumeRight", false)
        set(value) { prefs.edit().putBoolean("gestureVolumeRight", value).apply() }
    var doubleTapPlayPause: Boolean
        get() = prefs.getBoolean("doubleTapPlayPause", false)
        set(value) { prefs.edit().putBoolean("doubleTapPlayPause", value).apply() }
    var showPlayerClock: Boolean
        get() = prefs.getBoolean("showPlayerClock", false)
        set(value) { prefs.edit().putBoolean("showPlayerClock", value).apply() }
    var showPlayerBattery: Boolean
        get() = prefs.getBoolean("showPlayerBattery", false)
        set(value) { prefs.edit().putBoolean("showPlayerBattery", value).apply() }
    var showRemainingTime: Boolean
        get() = prefs.getBoolean("showRemainingTime", false)
        set(value) { prefs.edit().putBoolean("showRemainingTime", value).apply() }
    var gestures: Boolean
        get() = prefs.getBoolean("gestures", true)
        set(value) { prefs.edit().putBoolean("gestures", value).apply() }
    var amoled: Boolean
        get() = prefs.getBoolean("amoled", false)
        set(value) { prefs.edit().putBoolean("amoled", value).apply() }
    var embeddedSubtitleStyles: Boolean
        get() = prefs.getBoolean("embeddedSubtitleStyles", true)
        set(value) { prefs.edit().putBoolean("embeddedSubtitleStyles", value).apply() }
    var subtitleSize: Int
        get() = prefs.getInt("subtitleSize", 1).coerceIn(0, 2)
        set(value) { prefs.edit().putInt("subtitleSize", value.coerceIn(0, 2)).apply() }
    var subtitleFont: Int
        get() = prefs.getInt("subtitleFont", 0).coerceIn(0, 2)
        set(value) { prefs.edit().putInt("subtitleFont", value.coerceIn(0, 2)).apply() }
    var subtitleColor: Int
        get() = prefs.getInt("subtitleColor", -1)
        set(value) { prefs.edit().putInt("subtitleColor", value).apply() }
    var subtitleBackground: Boolean
        get() = prefs.getBoolean("subtitleBackground", true)
        set(value) { prefs.edit().putBoolean("subtitleBackground", value).apply() }
    var subtitlePosition: Int
        get() = prefs.getInt("subtitlePosition", 8).coerceIn(0, 35)
        set(value) { prefs.edit().putInt("subtitlePosition", value.coerceIn(0, 35)).apply() }
    var aspectRatio: Int
        get() = prefs.getInt("aspectRatio", 0).coerceIn(0, 2)
        set(value) { prefs.edit().putInt("aspectRatio", value.coerceIn(0, 2)).apply() }
    var eqEnabled: Boolean
        get() = prefs.getBoolean("eqEnabled", false)
        set(value) { prefs.edit().putBoolean("eqEnabled", value).apply() }
    var eqLevels: String
        get() = prefs.getString("eqLevels", "").orEmpty()
        set(value) { prefs.edit().putString("eqLevels", value).apply() }
    var bass: Int
        get() = prefs.getInt("bass", 0).coerceIn(0, 1000)
        set(value) { prefs.edit().putInt("bass", value.coerceIn(0, 1000)).apply() }
    var virtualizer: Int
        get() = prefs.getInt("virtualizer", 0).coerceIn(0, 1000)
        set(value) { prefs.edit().putInt("virtualizer", value.coerceIn(0, 1000)).apply() }
    var libraryDisplay: LibraryDisplay
        get() = LibraryDisplay(
            mode = MediaViewMode.values()[prefs.getInt("libraryMode", 2).coerceIn(0, 2)],
            grid = prefs.getBoolean("libraryGrid", false),
            sort = MediaSort.values()[prefs.getInt("librarySort", 0).coerceIn(0, 9)],
            descending = prefs.getBoolean("libraryDescending", false),
            duration = prefs.getBoolean("fieldDuration", true), size = prefs.getBoolean("fieldSize", true),
            resolution = prefs.getBoolean("fieldResolution", false), path = prefs.getBoolean("fieldPath", false),
            date = prefs.getBoolean("fieldDate", false), played = prefs.getBoolean("fieldPlayed", false),
            frameRate = prefs.getBoolean("fieldFrameRate", false), type = prefs.getBoolean("fieldType", false),
            minimumSeconds = prefs.getInt("minimumSeconds", 0).coerceIn(0, 3600))
        set(value) { prefs.edit().putInt("libraryMode", value.mode.ordinal).putBoolean("libraryGrid", value.grid)
            .putInt("librarySort", value.sort.ordinal).putBoolean("libraryDescending", value.descending)
            .putBoolean("fieldDuration", value.duration).putBoolean("fieldSize", value.size)
            .putBoolean("fieldResolution", value.resolution).putBoolean("fieldPath", value.path)
            .putBoolean("fieldDate", value.date).putBoolean("fieldPlayed", value.played)
            .putBoolean("fieldFrameRate", value.frameRate).putBoolean("fieldType", value.type)
            .putInt("minimumSeconds", value.minimumSeconds.coerceIn(0, 3600)).apply() }
    var backgroundPlayback: Boolean
        get() = prefs.getBoolean("backgroundPlayback", true)
        set(value) { prefs.edit().putBoolean("backgroundPlayback", value).apply() }
    var videoDisplay: Boolean
        get() = prefs.getBoolean("videoDisplay", true)
        set(value) { prefs.edit().putBoolean("videoDisplay", value).apply() }
    var showPlayerShortcuts: Boolean
        get() = prefs.getBoolean("showPlayerShortcuts", true)
        set(value) { prefs.edit().putBoolean("showPlayerShortcuts", value).apply() }
    var mirror: Boolean
        get() = prefs.getBoolean("mirror", false)
        set(value) { prefs.edit().putBoolean("mirror", value).apply() }
    var verticalFlip: Boolean
        get() = prefs.getBoolean("verticalFlip", false)
        set(value) { prefs.edit().putBoolean("verticalFlip", value).apply() }
    var playerNight: Boolean
        get() = prefs.getBoolean("playerNight", false)
        set(value) { prefs.edit().putBoolean("playerNight", value).apply() }
    var playerOrientation: Int
        get() = prefs.getInt("playerOrientation", 0).coerceIn(0, 2)
        set(value) { prefs.edit().putInt("playerOrientation", value.coerceIn(0, 2)).apply() }
    var playerShortcuts: Set<PlayerShortcut>
        get() = prefs.getString("playerShortcuts", null)?.split(',')?.mapNotNull { key -> PlayerShortcut.values().firstOrNull { it.name == key } }?.toSet()
            ?: setOf(PlayerShortcut.ROTATION, PlayerShortcut.SPEED, PlayerShortcut.BACKGROUND, PlayerShortcut.EQUALIZER, PlayerShortcut.AB)
        set(value) { prefs.edit().putString("playerShortcuts", PlayerShortcut.values().filter { it in value }.joinToString(",") { it.name }).apply() }
    fun backupValues(): Map<String, Any> = mapOf(
        "decoderPreference" to decoderPreference, "theme" to theme, "gestureVolumeRight" to gestureVolumeRight, "doubleTapPlayPause" to doubleTapPlayPause, "showPlayerClock" to showPlayerClock, "showPlayerBattery" to showPlayerBattery, "showRemainingTime" to showRemainingTime, "autoPip" to autoPip, "resume" to resume, "hidden" to hidden,
        "seekMs" to seekMs, "gestures" to gestures, "amoled" to amoled,
        "embeddedSubtitleStyles" to embeddedSubtitleStyles, "subtitleSize" to subtitleSize, "subtitleFont" to subtitleFont, "subtitleColor" to subtitleColor,
        "subtitleBackground" to subtitleBackground, "subtitlePosition" to subtitlePosition,
        "aspectRatio" to aspectRatio,
        "libraryMode" to libraryDisplay.mode.ordinal, "libraryGrid" to libraryDisplay.grid,
        "librarySort" to libraryDisplay.sort.ordinal, "libraryDescending" to libraryDisplay.descending,
        "fieldDuration" to libraryDisplay.duration, "fieldSize" to libraryDisplay.size,
        "fieldResolution" to libraryDisplay.resolution, "fieldPath" to libraryDisplay.path,
        "fieldDate" to libraryDisplay.date, "fieldPlayed" to libraryDisplay.played,
        "fieldFrameRate" to libraryDisplay.frameRate, "fieldType" to libraryDisplay.type,
        "minimumSeconds" to libraryDisplay.minimumSeconds,
        "backgroundPlayback" to backgroundPlayback, "videoDisplay" to videoDisplay, "showPlayerShortcuts" to showPlayerShortcuts,
        "mirror" to mirror, "verticalFlip" to verticalFlip, "playerNight" to playerNight, "playerOrientation" to playerOrientation,
        "playerShortcuts" to playerShortcuts.joinToString(",") { it.name })
    fun restoreValues(values: Map<String, Any>) {
        val editor = prefs.edit()
        for ((key, value) in values) when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Long -> editor.putLong(key, value)
            is Int -> editor.putInt(key, value)
            is String -> if (key == "playerShortcuts") editor.putString(key, value)
        }
        check(editor.commit()) { "Collections restored, but preferences could not be saved." }
    }
}
