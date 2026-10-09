package dev.abdus.apps.immich.provider

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import dev.abdus.apps.immich.shortcuts.FavoriteReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ImmichArtProvider : MuzeiArtProvider() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val EXTRA_ASSET_ID = "asset_id"
    }

    override fun onLoadRequested(initial: Boolean) {
        val context = context ?: return
        if (!AppPreferences(context).current().isConfigured) {
            Toast.makeText(context, "Immich server is not configured", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch { ArtworkSync.load(context, this@ImmichArtProvider) }
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
