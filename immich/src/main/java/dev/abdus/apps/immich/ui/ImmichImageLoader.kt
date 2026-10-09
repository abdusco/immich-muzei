package dev.abdus.apps.immich.ui

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dev.abdus.apps.immich.api.ImmichClient

class ImmichImageLoader {
    companion object {
        // Thumbnail URLs carry the API key as a query parameter, so no auth header is needed.
        fun create(context: Context): ImageLoader {
            return ImageLoader.Builder(context)
                .components {
                    add(OkHttpNetworkFetcherFactory(ImmichClient.http))
                }
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizePercent(context, 0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(context.cacheDir.resolve("immich_image_cache"))
                        .maxSizeBytes(20L * 1024 * 1024) // 20MB
                        .build()
                }
                .crossfade(true)
                .build()
        }
    }
}
