package com.tplayer.mediaplayer.ui

enum class Screen(val tab: Int) {
    VIDEOS(1), MUSIC(2), FILES(3), STREAMS(4), SETTINGS(5), FAVORITES(0), HISTORY(0), PLAYLIST(0), DOWNLOADS(0);
    companion object {
        fun forTab(tab: Int) = values().firstOrNull { it.tab == tab && tab != 0 } ?: VIDEOS
        fun restore(value: String?) = values().firstOrNull { it.name == value } ?: VIDEOS
    }
}
