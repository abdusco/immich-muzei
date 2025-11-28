package dev.abdus.apps.immich.provider

import android.content.Context
import android.util.Log
import com.google.android.apps.muzei.api.provider.ProviderContract
import dev.abdus.apps.immich.BuildConfig

/**
 * Wrapper for Muzei API operations to avoid direct Muzei API access in ViewModels.
 */
class MuzeiProvider(private val context: Context) {

    companion object {
        private const val TAG = "MuzeiProvider"
    }

    /**
     * Clear all photos from Muzei provider.
     * Returns the number of deleted photos.
     */
    fun clearPhotos(): Int {
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

    /**
     * Check if Immich is currently the active Muzei source.
     * Returns true if Immich is active, false otherwise.
     */
    fun isImmichActiveSource(): Boolean {
        return try {
            val client = ProviderContract.getProviderClient(context, BuildConfig.IMMICH_AUTHORITY)
            val lastArtwork = client.lastAddedArtwork
            val isActive = lastArtwork != null
            Log.d(TAG, "Immich provider active: $isActive")
            isActive
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Muzei source", e)
            false
        }
    }
}

