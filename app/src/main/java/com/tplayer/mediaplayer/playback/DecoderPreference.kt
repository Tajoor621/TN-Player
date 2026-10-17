package com.tplayer.mediaplayer.playback

/** Stable preference ordering, never removes secure/tunneling-compatible fallback candidates. */
object DecoderPreference {
    fun <T> order(candidates: List<T>, mode: Int, hardware: (T) -> Boolean, software: (T) -> Boolean): List<T> =
        when (mode) {
            1 -> candidates.sortedBy { if (hardware(it)) 0 else 1 }
            2 -> candidates.sortedBy { if (software(it)) 0 else 1 }
            else -> candidates.toList()
        }
}
