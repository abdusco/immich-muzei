package dev.abdus.apps.immich.provider

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.apps.muzei.api.createChooseProviderIntent
import com.google.android.apps.muzei.api.isSelected
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
            val isActive = client.isSelected(context)
            Log.d(TAG, "Immich provider selected: $isActive")
            isActive
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Muzei source", e)
            false
        }
    }

    /**
     * Build an intent that deep links into Muzei's source picker for Immich.
     */
    fun createChooseProviderIntent(): Intent? {
        return try {
            ProviderContract
                .getProviderClient(context, BuildConfig.IMMICH_AUTHORITY)
                .createChooseProviderIntent()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating choose provider intent", e)
            null
        }
    }
}
