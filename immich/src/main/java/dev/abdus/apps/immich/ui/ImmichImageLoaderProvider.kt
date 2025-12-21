package dev.abdus.apps.immich.ui

import android.content.Context
import coil3.ImageLoader

object ImmichImageLoaderProvider {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        val cached = instance
        if (cached != null) return cached

        return synchronized(this) {
            val existing = instance
            if (existing != null) {
                existing
            } else {
                val created = ImmichImageLoader.create(context.applicationContext)
                instance = created
                created
            }
        }
    }
}
