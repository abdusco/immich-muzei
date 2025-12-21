package dev.abdus.apps.immich.provider

import android.content.Context
import android.util.Log
import com.google.android.apps.muzei.api.provider.ProviderContract
import dev.abdus.apps.immich.BuildConfig

/**
 * Wrapper around the artwork content provider for maintenance operations.
 */
class ArtworkStore(private val context: Context) {
    companion object {
        private const val TAG = "ArtworkStore"
    }

    fun clearAll(): Int {
        return try {
            val contentUri = ProviderContract.getContentUri(BuildConfig.IMMICH_AUTHORITY)
            val deletedCount = context.contentResolver.delete(contentUri, null, null)
            Log.d(TAG, "Deleted $deletedCount photos")
            deletedCount
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing photos", e)
            0
        }
    }
}
