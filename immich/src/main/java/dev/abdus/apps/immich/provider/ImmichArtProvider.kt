package dev.abdus.apps.immich.provider

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
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
import dev.abdus.apps.immich.api.UpdateAssetsRequest
import dev.abdus.apps.immich.data.AppPreferences
import dev.abdus.apps.immich.data.ImmichRepository
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

        val immichClient = ImmichClient.create(
            baseUrl = checkNotNull(config.apiBaseUrl),
            apiKey = checkNotNull(config.apiKey),
        )

        repository = ImmichRepository(immichClient)

        // Determine which album(s) to fetch from based on selection
        val selectedAlbumIds = config.selectedAlbumIds.toList()
        val albumIds = when {
            selectedAlbumIds.isEmpty() -> {
                // No albums selected = use all albums
                null
            }

            selectedAlbumIds.size == 1 -> {
                // Single album = use it directly
                selectedAlbumIds
            }

            else -> {
                // Multiple albums = round-robin
                val index = prefs.getNextAlbumIndex(selectedAlbumIds.size)
                val currentAlbum = selectedAlbumIds[index]
                Log.d(TAG, "Round-robin: album ${index + 1}/${selectedAlbumIds.size}: $currentAlbum")
                listOf(currentAlbum)
            }
        }

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
                    createdBefore = null
                )

                if (assets.isEmpty()) {
                    return@launch
                }

                val MAX_ARTWORKS = minOf(assets.size, 6)
                val artworks = assets.map { asset ->
                    Artwork(
                        token = asset.id,
                        title = asset.originalFileName,
                        byline = asset.createdDate(),
                        attribution = asset.id,
                        persistentUri = asset.downloadUrl?.toUri(),
                        webUri = asset.viewUrl?.toUri(),
                    )
                }.take(MAX_ARTWORKS)

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

        // Add "Open in Immich" action if server is configured
        val prefs = AppPreferences(context)
        val server = prefs.current().serverUrl
        val assetId = artwork.token
        if (server != null && assetId != null) {
            val uri = "$server/photos/$assetId".toUri()
            return listOf(
                createOpenInImmichAction(uri),
                createFavoriteAction(assetId)
            )
        }

        return emptyList()
    }

    @SuppressLint("InlinedApi")
    private fun createOpenInImmichAction(uri: android.net.Uri): RemoteActionCompat {
        val context = context ?: throw IllegalStateException("Context missing")
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
    private fun createFavoriteAction(assetId: String): RemoteActionCompat {
        val context = context ?: throw IllegalStateException("Context missing")
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

    private fun buildAssetViewUrl(server: String, assetId: String): String =
        server.removeSuffix("/") + "/photos/$assetId"

    class FavoriteReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val assetId = intent.getStringExtra(EXTRA_ASSET_ID) ?: return
            val prefs = AppPreferences(context)
            val config = prefs.current()

            if (!config.isConfigured) {
                Toast.makeText(context, R.string.immich_favorite_error, Toast.LENGTH_SHORT).show()
                return
            }

            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val service = ImmichClient.create(
                        baseUrl = checkNotNull(config.apiBaseUrl),
                        apiKey = checkNotNull(config.apiKey)
                    )
                    service.updateAssets(
                        UpdateAssetsRequest(
                            ids = listOf(assetId),
                            isFavorite = true
                        )
                    )
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, R.string.immich_favorite_success, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to favorite asset", e)
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, R.string.immich_favorite_error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
