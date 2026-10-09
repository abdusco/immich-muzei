package dev.abdus.apps.immich.provider

import android.content.Context
import android.util.Log
import dev.abdus.apps.immich.api.ImmichClient
import dev.abdus.apps.immich.data.AppPreferences
import java.io.File
import java.io.IOException
import okhttp3.Request

class ImmichAssetFileStore(private val context: Context) {
    companion object {
        private const val TAG = "ImmichAssetFileStore"
    }

    fun getOrDownload(assetId: String, thumbnail: Boolean): File {
        val suffix = if (thumbnail) "thumbnail" else "original"
        val cacheDir = File(context.cacheDir, "immich_assets").apply { mkdirs() }
        val cacheFile = File(cacheDir, "immich_${assetId}_$suffix.jpg")

        if (cacheFile.exists()) return cacheFile

        downloadAsset(assetId, cacheFile, thumbnail)
        return cacheFile
    }

    private fun downloadAsset(assetId: String, targetFile: File, thumbnail: Boolean) {
        val config = AppPreferences(context).current()
        val client = ImmichClient.fromConfig(config) ?: throw IOException("Not configured")

        val url = if (thumbnail) {
            client.buildAssetThumbnailUrl(assetId)
        } else {
            client.buildAssetDownloadUrl(assetId)
        }

        val request = Request.Builder().url(url).build()
        ImmichClient.http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            response.body?.byteStream()?.use { inputStream ->
                targetFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
    }

    fun safeGetOrDownload(assetId: String, thumbnail: Boolean): File? {
        return try {
            getOrDownload(assetId, thumbnail)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download asset $assetId", e)
            null
        }
    }
}
