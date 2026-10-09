package dev.abdus.apps.immich.provider

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.google.android.apps.muzei.api.isSelected
import com.google.android.apps.muzei.api.provider.Artwork
import com.google.android.apps.muzei.api.provider.ProviderClient
import com.google.android.apps.muzei.api.provider.ProviderContract
import dev.abdus.apps.immich.api.ImmichClientProvider
import dev.abdus.apps.immich.data.AppPreferences
import dev.abdus.apps.immich.data.ImmichRepository

/**
 * Keeps Muzei's artwork queue in line with the current filters. The filters the queue was loaded
 * with are remembered; when they change, the whole queue is replaced instead of appended to.
 */
object ArtworkSync {
    private const val TAG = "ArtworkSync"

    /** Fetches a batch of photos and adds it to the queue, or replaces the queue if the filters changed. */
    suspend fun load(context: Context, provider: ProviderClient) {
        val prefs = AppPreferences(context)
        val config = prefs.current()
        val client = ImmichClientProvider.fromConfig(config) ?: return

        val assets = try {
            ImmichRepository(client).fetchRandomAssets(config)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch artwork", e)
            return
        }
        if (assets.isEmpty()) {
            Log.d(TAG, "No photos match the filters; keeping the current queue")
            return
        }

        val artworks = assets.map { asset ->
            Artwork(
                token = asset.id,
                title = asset.originalFileName,
                byline = asset.createdDate(),
                attribution = asset.id,
                persistentUri = asset.downloadUrl?.toUri(),
                webUri = asset.viewUrl?.toUri(),
            )
        }

        val filterKey = config.artworkFilterKey
        if (prefs.loadedArtworkFilterKey == filterKey) {
            provider.addArtwork(artworks)
            Log.d(TAG, "Added ${artworks.size} photos")
        } else {
            provider.setArtwork(artworks)
            prefs.loadedArtworkFilterKey = filterKey
            Log.d(TAG, "Filters changed; replaced queue with ${artworks.size} photos")
        }
    }

    /** Replaces the queue if the filters changed since it was loaded. */
    suspend fun syncIfFiltersChanged(context: Context) {
        val prefs = AppPreferences(context)
        if (prefs.loadedArtworkFilterKey == prefs.current().artworkFilterKey) return

        val provider = ProviderContract.getProviderClient(context, ImmichAuthorities.IMMICH_AUTHORITY)
        // If Immich isn't the active source, the next onLoadRequested will replace the queue.
        if (!provider.isSelected(context)) return
        load(context, provider)
    }
}
