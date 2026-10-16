package com.tplayer.mediaplayer.ui

import android.content.Context
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache

@GlideModule
class ThumbnailModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.setMemoryCache(LruResourceCache(16L * 1024 * 1024))
            .setDiskCache(InternalCacheDiskCacheFactory(context, "thumbnails", 64L * 1024 * 1024))
    }
    override fun isManifestParsingEnabled() = false
}
