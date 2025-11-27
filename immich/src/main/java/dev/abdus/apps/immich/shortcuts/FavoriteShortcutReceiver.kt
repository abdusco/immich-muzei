package dev.abdus.apps.immich.shortcuts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.apps.muzei.api.MuzeiContract
import com.google.android.apps.muzei.api.provider.ProviderContract
import dev.abdus.apps.immich.R
import dev.abdus.apps.immich.api.ImmichService
import dev.abdus.apps.immich.data.ImmichPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * BroadcastReceiver that favorites the current Immich artwork
 * This approach avoids any visible UI flash
 */
class FavoriteShortcutReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "FavoriteShortcutReceiver"
        private const val IMMICH_AUTHORITY = "dev.abdus.apps.immich"
        const val ACTION_FAVORITE = "dev.abdus.apps.immich.ACTION_FAVORITE_CURRENT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FAVORITE) return

        Log.d(TAG, "Favorite shortcut triggered via BroadcastReceiver")

        // Use goAsync to allow background work
        val pendingResult = goAsync()

        // Use application context to ensure toast persists
        val appContext = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        scope.launch {
            try {
                favoriteCurrentArtwork(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "Error favoriting artwork", e)
                withContext(Dispatchers.Main) {
                    showToast(appContext, appContext.getString(R.string.immich_favorite_error))
                }
            } finally {
                // Small delay before finishing receiver
                kotlinx.coroutines.delay(200)
                pendingResult.finish()
            }
        }
    }

    private suspend fun favoriteCurrentArtwork(context: Context) {
        Log.d(TAG, "Starting favoriteCurrentArtwork")

        // Get the current artwork from Muzei and its ID
        val artworkData = withContext(Dispatchers.IO) {
            context.contentResolver.query(
                MuzeiContract.Artwork.CONTENT_URI,
                null,
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    Log.d(TAG, "Found current artwork in cursor")
                    val artwork = com.google.android.apps.muzei.api.Artwork.fromCursor(cursor)
                    val idIndex = cursor.getColumnIndex("_id")
                    val id = if (idIndex >= 0) cursor.getLong(idIndex) else null
                    Log.d(TAG, "Artwork: authority=${artwork.providerAuthority}, id=$id")
                    Pair(artwork, id)
                } else {
                    Log.w(TAG, "No artwork found in cursor")
                    null
                }
            }
        }

        if (artworkData == null) {
            Log.e(TAG, "artworkData is null")
            showError(context, "No artwork currently displayed")
            return
        }

        val (currentArtwork, artworkId) = artworkData

        if (artworkId == null) {
            Log.e(TAG, "artworkId is null")
            showError(context, "Could not get artwork ID")
            return
        }

        // Check if the current artwork is from Immich provider
        if (currentArtwork.providerAuthority != IMMICH_AUTHORITY) {
            Log.e(TAG, "Current provider is ${currentArtwork.providerAuthority}, not Immich")
            showError(context, "Current wallpaper is not from Immich")
            return
        }

        Log.d(TAG, "Querying Immich provider for asset ID")
        // Query the Immich provider directly to get the token (asset ID)
        val assetId = withContext(Dispatchers.IO) {
            getAssetIdFromProvider(context, artworkId)
        }

        if (assetId == null) {
            Log.e(TAG, "Failed to get asset ID from provider")
            showError(context, "Could not get asset ID")
            return
        }

        Log.d(TAG, "Got asset ID: $assetId")

        // Get config and favorite the asset
        val prefs = ImmichPreferences(context)
        val config = prefs.current()

        Log.d(TAG, "Config: isConfigured=${config.isConfigured}, serverUrl=${config.serverUrl}, apiBaseUrl=${config.apiBaseUrl}")

        if (!config.isConfigured) {
            Log.e(TAG, "Immich is not configured")
            showError(context, "Immich is not configured")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Creating service and calling updateAssets API")
                val service = ImmichService.create(
                    baseUrl = checkNotNull(config.apiBaseUrl),
                    apiKey = checkNotNull(config.apiKey)
                )
                service.updateAssets(
                    ImmichService.UpdateAssetsRequest(
                        ids = listOf(assetId),
                        isFavorite = true
                    )
                )
                Log.d(TAG, "Successfully favorited asset")
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Showing success toast via ToastActivity")
                    showToast(context, context.getString(R.string.immich_favorite_success))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to favorite asset: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    showToast(context, "API error: ${e.message ?: "Unknown error"}")
                }
            }
        }
    }

    private fun getAssetIdFromProvider(context: Context, artworkId: Long): String? {
        return try {
            Log.d(TAG, "Getting asset ID using ProviderClient (ignoring artworkId $artworkId)")

            // Use ProviderClient to get artwork from the Immich provider
            val client = ProviderContract.getProviderClient(context, IMMICH_AUTHORITY)

            // Get the last added artwork (should be the current one)
            val lastArtwork = client.lastAddedArtwork
            Log.d(TAG, "Last artwork from ProviderClient: token=${lastArtwork?.token}, title=${lastArtwork?.title}")

            if (lastArtwork?.token != null) {
                Log.d(TAG, "Successfully found token via ProviderClient: ${lastArtwork.token}")
                return lastArtwork.token
            } else {
                Log.w(TAG, "ProviderClient returned null or artwork has no token")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get artwork from ProviderClient: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }

    private fun showError(context: Context, message: String) {
        showToast(context, message)
    }

    private fun showToast(context: Context, message: String) {
        val intent = Intent(context, ToastActivity::class.java).apply {
            putExtra(ToastActivity.EXTRA_MESSAGE, message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

