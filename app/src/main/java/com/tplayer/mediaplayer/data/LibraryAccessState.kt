package com.tplayer.mediaplayer.data

internal enum class LibraryAccessState { FULL, PARTIAL, NEEDS_REQUEST, DENIED_PERMANENTLY }

/** Re-evaluates a permission snapshot; has no platform or persistence dependencies. */
internal class LibraryAccessMachine {
    fun state(fullGranted: Boolean, partialGranted: Boolean, permanentlyDenied: Boolean, apiLevel: Int): LibraryAccessState =
        when {
            fullGranted -> LibraryAccessState.FULL
            apiLevel >= 34 && partialGranted -> LibraryAccessState.PARTIAL
            permanentlyDenied -> LibraryAccessState.DENIED_PERMANENTLY
            else -> LibraryAccessState.NEEDS_REQUEST
        }
}
