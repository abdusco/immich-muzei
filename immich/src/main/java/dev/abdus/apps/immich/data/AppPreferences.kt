package dev.abdus.apps.immich.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import androidx.core.content.edit

private const val PREFS_NAME = "immich_prefs"
private const val KEY_SERVER_URL = "server_url"
private const val KEY_API_KEY = "api_key"
private const val KEY_SELECTED_ALBUM = "selected_album"  // Deprecated, kept for migration
private const val KEY_SELECTED_ALBUMS = "selected_albums"  // New: multiple albums
private const val KEY_SELECTED_TAGS = "selected_tags"
private const val KEY_FAVORITES_ONLY = "favorites_only"
private const val KEY_FILTER_DAYS_BACK = "filter_days_back"  // New: store days-back directly
private const val KEY_CACHED_ALBUMS = "cached_albums_json"  // Cached album metadata
private const val KEY_CACHED_TAGS = "cached_tags_json"  // Cached tag metadata
private const val KEY_LOADED_ARTWORK_FILTER = "loaded_artwork_filter"

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Emits once on collection and again whenever any preference changes. */
    val changes: Flow<Unit> = prefs.onChangeFlow()

    val configFlow: Flow<ImmichConfig> = changes
        .map { readConfig() }
        .distinctUntilChanged()

    fun current(): ImmichConfig = readConfig()

    fun updateServer(serverUrl: String, apiKey: String) {
        prefs.edit {
            putString(KEY_SERVER_URL, serverUrl.trim())
            putString(KEY_API_KEY, apiKey.trim())
        }
    }

    fun updateSelectedAlbums(ids: Set<String>) {
        prefs.edit { putStringSet(KEY_SELECTED_ALBUMS, ids) }
    }

    fun updateSelectedTags(ids: Set<String>) {
        prefs.edit { putStringSet(KEY_SELECTED_TAGS, ids) }
    }

    fun updateFavoritesOnly(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_FAVORITES_ONLY, enabled) }
    }

    fun updateFilterDaysBack(days: Int?) {
        prefs.edit {
            if (days == null) remove(KEY_FILTER_DAYS_BACK) else putInt(KEY_FILTER_DAYS_BACK, days)
        }
    }

    /** [ImmichConfig.artworkFilterKey] of the filters Muzei's artwork queue was last loaded with. */
    var loadedArtworkFilterKey: String?
        get() = prefs.getString(KEY_LOADED_ARTWORK_FILTER, null)
        set(value) = prefs.edit { putString(KEY_LOADED_ARTWORK_FILTER, value) }

    /**
     * Save album metadata for local caching
     */
    fun saveCachedAlbums(albums: List<ImmichAlbumUiModel>) {
        val json = Json.encodeToString(albums)
        prefs.edit { putString(KEY_CACHED_ALBUMS, json) }
    }

    /**
     * Load cached album metadata
     */
    fun getCachedAlbums(): List<ImmichAlbumUiModel> {
        val json = prefs.getString(KEY_CACHED_ALBUMS, null) ?: return emptyList()
        return try {
            Json.decodeFromString(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Save tag metadata for local caching
     */
    fun saveCachedTags(tags: List<ImmichTagUiModel>) {
        val json = Json.encodeToString(tags)
        prefs.edit { putString(KEY_CACHED_TAGS, json) }
    }

    /**
     * Load cached tag metadata
     */
    fun getCachedTags(): List<ImmichTagUiModel> {
        val json = prefs.getString(KEY_CACHED_TAGS, null) ?: return emptyList()
        return try {
            Json.decodeFromString(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun readConfig(): ImmichConfig {
        // Migration: if old single album key exists, migrate to new format
        val oldAlbumId = prefs.getString(KEY_SELECTED_ALBUM, null)
        val newAlbums = prefs.getStringSet(KEY_SELECTED_ALBUMS, null)

        val selectedAlbums = when {
            newAlbums != null -> newAlbums
            oldAlbumId != null -> {
                // Migrate old single album to new format
                val albums = setOf(oldAlbumId)
                prefs.edit {
                    putStringSet(KEY_SELECTED_ALBUMS, albums)
                    remove(KEY_SELECTED_ALBUM)  // Clean up old key
                }
                albums
            }
            else -> emptySet()
        }

        return ImmichConfig(
             serverUrl = prefs.getString(KEY_SERVER_URL, null)?.normalizeUrl(),
             apiKey = prefs.getString(KEY_API_KEY, null)?.trim().orEmpty().ifBlank { null },
             selectedAlbumIds = selectedAlbums,
             selectedTagIds = prefs.getStringSet(KEY_SELECTED_TAGS, emptySet()) ?: emptySet(),
             favoritesOnly = prefs.getBoolean(KEY_FAVORITES_ONLY, false),
             filterPresetDaysBack = prefs.getInt(KEY_FILTER_DAYS_BACK, -1).let { if (it == -1) null else it }
         )
     }

    private fun String.normalizeUrl(): String = trim().removeSuffix("/")
}

data class ImmichConfig(
    val serverUrl: String?,
    val apiKey: String?,
    val selectedAlbumIds: Set<String> = emptySet(),
    val selectedTagIds: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
    // persisted days-back value for the Taken-at slider (e.g. 7 = last week)
    val filterPresetDaysBack: Int? = null
) {
    val isConfigured: Boolean get() = !serverUrl.isNullOrBlank() && !apiKey.isNullOrBlank()

    /** Changes whenever the set of photos to show changes. */
    val artworkFilterKey: String
        get() = listOf(
            serverUrl,
            selectedAlbumIds.sorted(),
            selectedTagIds.sorted(),
            favoritesOnly,
            filterPresetDaysBack
        ).toString()
    val apiBaseUrl: String?
        get() = serverUrl?.let { base ->
            val withApi = if (base.endsWith("/api")) base else "$base/api"
            if (withApi.endsWith('/')) withApi else "$withApi/"
        }
}

private fun SharedPreferences.onChangeFlow(): Flow<Unit> = callbackFlow {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        trySend(Unit).isSuccess
    }
    registerOnSharedPreferenceChangeListener(listener)
    trySend(Unit)
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}
