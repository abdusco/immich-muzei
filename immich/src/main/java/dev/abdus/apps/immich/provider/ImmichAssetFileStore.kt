package dev.abdus.apps.immich.provider

import android.content.Context
import android.os.CancellationSignal
import android.util.Log
import dev.abdus.apps.immich.api.ImmichClient
import dev.abdus.apps.immich.data.AppPreferences
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.Request

class ImmichAssetFileStore(private val context: Context) {
    companion object {
        private const val TAG = "ImmichAssetFileStore"
        private const val CACHE_DIR = "immich_assets"
        private const val VERSION_MARKER = ".cache_version"

        // Bump to wipe the cache once on upgrade (older versions could leave truncated files).
        private const val CACHE_VERSION = 2

        private const val ORIGINAL_TIMEOUT_SECONDS = 120L
        private const val THUMBNAIL_TIMEOUT_SECONDS = 30L

        // One lock per (asset, variant); entries are tiny, so they are never evicted.
        private val locks = ConcurrentHashMap<String, Any>()
    }

    private fun cacheFile(assetId: String, thumbnail: Boolean): File {
        val suffix = if (thumbnail) "thumbnail" else "original"
        // No extension: the real type comes from the document's MIME type, not the file name.
        return File(cacheDir(), "immich_${assetId}_$suffix")
    }

    /** The cached original, or null. Never downloads. */
    fun cachedOriginal(assetId: String): File? =
        cacheFile(assetId, thumbnail = false).takeIf { it.exists() }

    fun getOrDownload(
        assetId: String,
        thumbnail: Boolean,
        signal: CancellationSignal? = null
    ): File {
        val cacheFile = cacheFile(assetId, thumbnail)

        if (cacheFile.exists()) return cacheFile

        synchronized(locks.getOrPut(cacheFile.name) { Any() }) {
            if (!cacheFile.exists()) downloadAsset(assetId, cacheFile, thumbnail, signal)
        }
        return cacheFile
    }

    private fun cacheDir(): File {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val marker = File(dir, VERSION_MARKER)
        if (marker.readTextOrNull()?.trim() != CACHE_VERSION.toString()) {
            dir.listFiles()?.forEach { it.delete() }
            marker.writeText(CACHE_VERSION.toString())
        }
        return dir
    }

    private fun File.readTextOrNull(): String? = if (exists()) readText() else null

    private fun downloadAsset(
        assetId: String,
        targetFile: File,
        thumbnail: Boolean,
        signal: CancellationSignal?
    ) {
        val config = AppPreferences(context).current()
        val client = ImmichClient.fromConfig(config) ?: throw IOException("Not configured")

        val url = if (thumbnail) {
            client.buildAssetThumbnailUrl(assetId)
        } else {
            client.buildAssetDownloadUrl(assetId)
        }

        val tempFile = File(targetFile.parentFile, "${targetFile.name}.${System.nanoTime()}.tmp")
        try {
            val request = Request.Builder().url(url).build()
            val http = ImmichClient.http.newBuilder()
                .callTimeout(if (thumbnail) THUMBNAIL_TIMEOUT_SECONDS else ORIGINAL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
            val call = http.newCall(request)
            signal?.setOnCancelListener { call.cancel() }
            signal?.throwIfCanceled()
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                val body = response.body ?: throw IOException("Empty response body")
                val written = body.byteStream().use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                val expected = body.contentLength()
                if (expected >= 0 && written != expected) {
                    throw IOException("Truncated download: got $written of $expected bytes")
                }
            }
            Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally {
            signal?.setOnCancelListener(null)
            tempFile.delete()
        }
    }

    fun safeGetOrDownload(
        assetId: String,
        thumbnail: Boolean,
        signal: CancellationSignal? = null
    ): File? {
        return try {
            getOrDownload(assetId, thumbnail, signal)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download asset $assetId", e)
            null
        }
    }
}
