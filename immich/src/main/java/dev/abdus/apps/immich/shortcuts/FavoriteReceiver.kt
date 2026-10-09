package dev.abdus.apps.immich.shortcuts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.apps.muzei.api.MuzeiContract
import dev.abdus.apps.immich.R
import dev.abdus.apps.immich.api.ImmichClient
import dev.abdus.apps.immich.api.UpdateAssetsRequest
import dev.abdus.apps.immich.data.AppPreferences
import dev.abdus.apps.immich.provider.ImmichArtProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles favoriting assets via direct ID or current artwork.
 * Broadcasts ACTION_ASSET_FAVORITED on success.
 */
class FavoriteReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "FavoriteReceiver"

        const val ACTION_FAVORITE_CURRENT = "dev.abdus.apps.immich.ACTION_FAVORITE_CURRENT"
        const val ACTION_ASSET_FAVORITED = "dev.abdus.apps.immich.ACTION_ASSET_FAVORITED"
        const val EXTRA_ASSET_ID = "asset_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FAVORITE_CURRENT -> favoriteCurrent(context)
            else -> favoriteAssetId(context, intent.getStringExtra(EXTRA_ASSET_ID), null)
        }
    }

    private fun favoriteAssetId(context: Context, assetId: String?, title: String?) {
        if (assetId == null) return
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val client = ImmichClient.fromConfig(AppPreferences(context).current()) ?: run {
                    showToast(context, context.getString(R.string.immich_favorite_error))
                    return@launch
                }

                client.updateAssets(UpdateAssetsRequest(ids = listOf(assetId), isFavorite = true))

                broadcastFavorited(context, assetId, client.buildAssetDownloadUrl(assetId), title)
                showToast(context, context.getString(R.string.immich_favorite_success))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to favorite asset: $assetId", e)
                showToast(context, context.getString(R.string.immich_favorite_error))
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun favoriteCurrent(context: Context) {
        val artwork = MuzeiContract.Artwork.getCurrentArtwork(context)

        if (artwork == null) {
            showToast(context, "No artwork currently displayed")
            return
        }

        if (artwork.providerAuthority != ImmichArtProvider.AUTHORITY) {
            showToast(context, "Current wallpaper is not from Immich")
            return
        }

        val assetId = artwork.attribution
        if (assetId == null) {
            showToast(context, "Could not get asset ID")
            return
        }

        return favoriteAssetId(context, assetId, artwork.title)
    }

    private fun broadcastFavorited(context: Context, assetId: String, downloadUrl: String, title: String? = null) {
        context.sendBroadcast(Intent(ACTION_ASSET_FAVORITED).apply {
            putExtra("asset_id", assetId)
            putExtra("asset_download_url", downloadUrl)
            title?.let { putExtra("asset_original_file_name", it) }
        })
    }

    private fun showToast(context: Context, message: String) {
        context.startActivity(Intent(context, ToastActivity::class.java).apply {
            putExtra("message", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
