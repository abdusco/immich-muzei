package dev.abdus.apps.immich.ui

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import dev.abdus.apps.immich.data.ImmichPreferences
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

class ImmichImageLoader {
    companion object {
        fun create(context: Context): ImageLoader {
            val prefs = ImmichPreferences(context)
            val config = prefs.current()

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(ImmichHeaderInterceptor(config.apiKey))
                .build()
            
            return ImageLoader.Builder(context)
                .components {
                    add(OkHttpNetworkFetcherFactory(okHttpClient))
                }
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizePercent(context, 0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(context.cacheDir.resolve("immich_image_cache"))
                        .maxSizeBytes(50L * 1024 * 1024) // 50MB
                        .build()
                }
                .crossfade(true)
                .build()
        }
    }
}

private class ImmichHeaderInterceptor(private val apiKey: String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder().apply {
            apiKey?.let {
                addHeader("x-api-key", it)
            }
        }.build()
        return chain.proceed(request)
    }
}

