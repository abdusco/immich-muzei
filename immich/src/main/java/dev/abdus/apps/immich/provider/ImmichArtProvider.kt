package dev.abdus.apps.immich.provider

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.app.RemoteActionCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import com.google.android.apps.muzei.api.provider.Artwork
import com.google.android.apps.muzei.api.provider.MuzeiArtProvider
import dev.abdus.apps.immich.R
import dev.abdus.apps.immich.api.ImmichClient
import dev.abdus.apps.immich.api.ImmichClientProvider
import dev.abdus.apps.immich.data.AppPreferences
import dev.abdus.apps.immich.data.ImmichRepository
import dev.abdus.apps.immich.shortcuts.FavoriteReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ImmichArtProvider : MuzeiArtProvider() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: ImmichRepository
    private lateinit var prefs: AppPreferences

    companion object {
        private const val TAG = "ImmichArtProvider"
        private const val EXTRA_ASSET_ID = "asset_id"
        // Immich's /search/random "size" accepts at most 1000 results per request.
        private const val MAX_API_SIZE = 1000
    }

    override fun onLoadRequested(initial: Boolean) {
        val context = context ?: run {
            return
        }
        prefs = AppPreferences(context)
        val config = prefs.current()

        if (!config.isConfigured) {
            Toast.makeText(context, "Immich server is not configured", Toast.LENGTH_SHORT).show()
            return
        }

        val immichClient = ImmichClientProvider.fromConfig(config) ?: run {
            Toast.makeText(context, "Immich server is not configured", Toast.LENGTH_SHORT).show()
            return
        }

        repository = ImmichRepository(immichClient)

        // Fetch from all selected albums at once (server filters with OR semantics).
        // Empty selection means no album filter = use all albums.
        val albumIds = config.selectedAlbumIds.toList().ifEmpty { null }

        val tagIds = config.selectedTagIds.toList().ifEmpty { null }
        // Include advanced filters for taken-at if configured
        // Map stored days-back preference to an ISO date string (if present). Prefer days-back when available.
        val createdAfterIso: String? = config.filterPresetDaysBack?.let { days ->
            try {
                LocalDate.now().minusDays(days.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (_: Exception) {
                null
            }
        }

        scope.launch {
            try {
                val assets = repository.fetchRandomAssets(
                    albumIds = albumIds,
                    tagIds = tagIds,
                    favoritesOnly = config.favoritesOnly,
                    createdAfter = createdAfterIso,
                    createdBefore = null,
                    size = MAX_API_SIZE
                )

                if (assets.isEmpty()) {
                    return@launch
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

                val addedUris = addArtwork(artworks)
                Log.d(TAG, "Added ${addedUris.size} artwork URIs")
            } catch (e: IOException) {
                Log.e(TAG, "IOException while fetching artwork", e)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error while fetching artwork", e)
            }
        }
    }

    @SuppressLint("Recycle")
    override fun getDescription(): String {
        val context = context ?: return super.getDescription()
        return context.getString(R.string.description)
    }

    override fun getCommandActions(artwork: Artwork): List<RemoteActionCompat> {
        val context = context ?: return emptyList()

        if (artwork.token.isNullOrEmpty()) {
            return emptyList()
        }

        val config = AppPreferences(context).current()
        if (!config.isConfigured) {
            return emptyList()
        }

        return listOf(
            createOpenInImmichAction(context, artwork),
            createFavoriteAction(context, artwork)
        )
    }

    @SuppressLint("InlinedApi")
    private fun createOpenInImmichAction(context: Context, artwork: Artwork): RemoteActionCompat {
        val assetId = artwork.token!!

        // Add "Open in Immich" action if server is configured
        val immichClient = checkNotNull(getClient(context))
        val uri = immichClient.buildAssetViewUrl(assetId).toUri()

        val intent = Intent(Intent.ACTION_VIEW, uri)
        val title = context.getString(R.string.immich_action_open)
        return RemoteActionCompat(
            IconCompat.createWithResource(
                context,
                com.google.android.apps.muzei.api.R.drawable.muzei_launch_command
            ),
            title,
            title,
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        ).apply {
            setShouldShowIcon(false)
        }
    }

    @SuppressLint("InlinedApi")
    private fun createFavoriteAction(context: Context, artwork: Artwork): RemoteActionCompat {
        val assetId = artwork.token!!

        val intent = Intent(context, FavoriteReceiver::class.java).apply {
            putExtra(EXTRA_ASSET_ID, assetId)
        }
        val title = context.getString(R.string.immich_action_favorite)
        return RemoteActionCompat(
            IconCompat.createWithResource(
                context,
                android.R.drawable.star_big_on
            ),
            title,
            title,
            PendingIntent.getBroadcast(
                context,
                assetId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        ).apply {
            setShouldShowIcon(false)
        }
    }

    private fun getClient(context: Context): ImmichClient? {
        val config = AppPreferences(context).current()
        return ImmichClientProvider.fromConfig(config)
    }
}
