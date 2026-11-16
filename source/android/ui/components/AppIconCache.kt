package com.blocker.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * High-performance global LRU cache for downscaled app icons (96x96px).
 * Eliminates repetitive Drawable-to-Bitmap decoding on the UI thread when scrolling
 * through large lists of User and System apps.
 */
object AppIconCache {
    private val cache = LruCache<String, ImageBitmap>(250)

    fun get(packageName: String): ImageBitmap? {
        return cache.get(packageName)
    }

    fun getOrCreate(packageName: String, drawable: Drawable?): ImageBitmap? {
        if (drawable == null) return null
        val existing = cache.get(packageName)
        if (existing != null) return existing

        return runCatching {
            val targetSize = 96
            val bm = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bm)
            drawable.setBounds(0, 0, targetSize, targetSize)
            drawable.draw(canvas)
            val imgBm = bm.asImageBitmap()
            cache.put(packageName, imgBm)
            imgBm
        }.getOrNull()
    }

    fun clear() {
        cache.evictAll()
    }
}
