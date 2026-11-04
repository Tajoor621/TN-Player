@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tplayer.mediaplayer.security

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import android.net.Uri

object PrivateMediaPolicy {
    fun marked(item: MediaItem?) = item?.mediaId?.startsWith("tnprivate:") == true
    suspend fun protect(items: List<MediaItem>, privacy: PrivateFolder): List<MediaItem> {
        val classified = items.map { it to privacy.isPrivate(it.localConfiguration?.uri?.toString() ?: it.mediaId) }
        if (classified.any { it.second }) {
            check(privacy.isUnlocked()) { "Open and unlock the private folder first." }
            require(classified.all { it.second }) { "Private and public files cannot be mixed in one queue." }
        }
        return classified.map { (item, hidden) -> if (!hidden) item else {
            val uri = item.localConfiguration?.uri?.toString() ?: item.mediaId
            item.buildUpon().setMediaId(privacy.privateId(uri)).setMediaMetadata(MediaMetadata.Builder()
                .setTitle("Private media").setDisplayTitle("Private media").setArtist("").setAlbumArtist("").setAlbumTitle("")
                .setDescription("").setSubtitle("").setArtworkUri(Uri.EMPTY).setArtworkData(ByteArray(0), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                .setExtras(Bundle().apply { putBoolean("private", true) }).build()).build()
        } }
    }
}
