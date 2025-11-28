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
import dev.abdus.apps.immich.api.ImmichService
import dev.abdus.apps.immich.data.ImmichPreferences
import dev.abdus.apps.immich.data.ImmichRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.IOException

class ImmichArtProvider : MuzeiArtProvider() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository = ImmichRepository()

    companion object {
        private const val TAG = "ImmichArtProvider"
        private const val EXTRA_ASSET_ID = "asset_id"
    }

    override fun onLoadRequested(initial: Boolean) {
        Log.d(TAG, "onLoadRequested called, initial=$initial")
        val context = context ?: run {
            Log.e(TAG, "Context is null")
            return
        }
        val prefs = ImmichPreferences(context)
        val config = prefs.current()

        if (!config.isConfigured) {
            Log.w(TAG, "Not configured")
            return
        }

        scope.launch {
            try {
                Log.d(TAG, "Creating Immich service with baseUrl=${config.apiBaseUrl}")
                val service = ImmichService.create(
                    baseUrl = checkNotNull(config.apiBaseUrl),
                    apiKey = config.apiKey!!
                )

                // Determine which album(s) to fetch from based on selection
                val selectedAlbums = config.selectedAlbumIds.toList()
                val albumList = when {
                    selectedAlbums.isEmpty() -> {
                        // No albums selected = use all albums
                        Log.d(TAG, "No albums selected, using all albums")
                        null
                    }
                    selectedAlbums.size == 1 -> {
                        // Single album = use it directly
                        Log.d(TAG, "Single album selected: ${selectedAlbums[0]}")
                        selectedAlbums
                    }
                    else -> {
                        // Multiple albums = round-robin
                        val index = prefs.getNextAlbumIndex(selectedAlbums.size)
                        val currentAlbum = selectedAlbums[index]
                        Log.d(TAG, "Round-robin: album ${index + 1}/${selectedAlbums.size}: $currentAlbum")
                        listOf(currentAlbum)
                    }
                }

                val tagList = config.selectedTagIds.toList().ifEmpty { null }
                Log.d(TAG, "Fetching random asset with ${albumList?.size ?: "all"} album(s), ${tagList?.size ?: "all"} tag(s), favoritesOnly: ${config.favoritesOnly}")
                val asset = repository.fetchRandomAsset(service, albumList, tagList, config.favoritesOnly)

                if (asset == null) {
                    Log.w(TAG, "No asset returned from API")
                    return@launch
                }

                Log.d(TAG, "Got asset: id=${asset.id}, filename=${asset.originalFileName}")
                val imageUrl = buildAssetUrl(config.serverUrl!!, asset.id, config.apiKey!!)
                Log.d(TAG, "Built image URL: $imageUrl")

                val byline = asset.fileCreatedAt?.let { raw ->
                    // Extract YYYY-MM-DD from various ISO-like timestamps safely without java.time
                    val match = Regex("^(\\d{4}-\\d{2}-\\d{2})").find(raw)
                    match?.groupValues?.getOrNull(1) ?: run {
                        if (raw.length >= 10) raw.substring(0, 10) else null
                    }
                }

                addArtwork(
                    Artwork(
                        token = asset.id,
                        title = asset.originalFileName,
                        byline = byline,
                        persistentUri = imageUrl.toUri(),
                        webUri = imageUrl.toUri()
                    )
                )
                Log.d(TAG, "Artwork added successfully")
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
        val prefs = ImmichPreferences(context)
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

    private fun buildAssetUrl(server: String, assetId: String, apiKey: String): String =
        server.removeSuffix("/") + "/api/assets/$assetId/original?apiKey=$apiKey"

    class FavoriteReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val assetId = intent.getStringExtra(EXTRA_ASSET_ID) ?: return
            val prefs = ImmichPreferences(context)
            val config = prefs.current()

            if (!config.isConfigured) {
                Toast.makeText(context, R.string.immich_favorite_error, Toast.LENGTH_SHORT).show()
                return
            }

            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
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
