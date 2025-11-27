package dev.abdus.apps.immich.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val PREFS_NAME = "immich_prefs"
private const val KEY_SERVER_URL = "server_url"
private const val KEY_API_KEY = "api_key"
private const val KEY_SELECTED_ALBUM = "selected_album"  // Deprecated, kept for migration
private const val KEY_SELECTED_ALBUMS = "selected_albums"  // New: multiple albums
private const val KEY_SELECTED_TAGS = "selected_tags"
private const val KEY_FAVORITES_ONLY = "favorites_only"
private const val KEY_LAST_ALBUM_INDEX = "last_album_index"  // Round-robin tracking
private const val KEY_CACHED_ALBUMS = "cached_albums_json"  // Cached album metadata
private const val KEY_CACHED_TAGS = "cached_tags_json"  // Cached tag metadata

class ImmichPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val configFlow: Flow<ImmichConfig> = prefs.onChangeFlow()
        .map { readConfig() }
        .distinctUntilChanged()

    fun current(): ImmichConfig = readConfig()

    fun updateServer(serverUrl: String, apiKey: String) {
        prefs.edit().apply {
            putString(KEY_SERVER_URL, serverUrl.trim())
            putString(KEY_API_KEY, apiKey.trim())
        }.apply()
    }

    @Deprecated("Use updateSelectedAlbums instead")
    fun updateSelectedAlbum(id: String?) {
        // Migrate to new multi-album format
        val albums = if (id != null) setOf(id) else emptySet()
        updateSelectedAlbums(albums)
    }

    fun updateSelectedAlbums(ids: Set<String>) {
        prefs.edit().apply {
            putStringSet(KEY_SELECTED_ALBUMS, ids)
            // Reset round-robin index when selection changes
            putInt(KEY_LAST_ALBUM_INDEX, 0)
        }.apply()
    }

    fun updateSelectedTags(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_SELECTED_TAGS, ids).apply()
    }

    fun updateFavoritesOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FAVORITES_ONLY, enabled).apply()
    }

    /**
     * Get the next album index for round-robin selection.
     * Returns the current index and increments it for next time.
     */
    fun getNextAlbumIndex(totalAlbums: Int): Int {
        if (totalAlbums <= 0) return 0

        val current = prefs.getInt(KEY_LAST_ALBUM_INDEX, 0)
        val next = (current + 1) % totalAlbums
        prefs.edit().putInt(KEY_LAST_ALBUM_INDEX, next).apply()
        return current
    }

    /**
     * Save album metadata for local caching
     */
    fun saveCachedAlbums(albums: List<ImmichAlbumUiModel>) {
        val json = Json.encodeToString(albums)
        prefs.edit().putString(KEY_CACHED_ALBUMS, json).apply()
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
        prefs.edit().putString(KEY_CACHED_TAGS, json).apply()
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
                prefs.edit().apply {
                    putStringSet(KEY_SELECTED_ALBUMS, albums)
                    remove(KEY_SELECTED_ALBUM)  // Clean up old key
                }.apply()
                albums
            }
            else -> emptySet()
        }

        return ImmichConfig(
            serverUrl = prefs.getString(KEY_SERVER_URL, null)?.normalizeUrl(),
            apiKey = prefs.getString(KEY_API_KEY, null)?.trim().orEmpty().ifBlank { null },
            selectedAlbumIds = selectedAlbums,
            selectedTagIds = prefs.getStringSet(KEY_SELECTED_TAGS, emptySet()) ?: emptySet(),
            favoritesOnly = prefs.getBoolean(KEY_FAVORITES_ONLY, false)
        )
    }

    private fun String.normalizeUrl(): String = trim().removeSuffix("/")
}

data class ImmichConfig(
    val serverUrl: String?,
    val apiKey: String?,
    val selectedAlbumIds: Set<String> = emptySet(),
    val selectedTagIds: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false
) {
    val isConfigured: Boolean get() = !serverUrl.isNullOrBlank() && !apiKey.isNullOrBlank()
    val apiBaseUrl: String?
        get() = serverUrl?.let { base ->
            val withApi = if (base.endsWith("/api")) base else "$base/api"
            if (withApi.endsWith('/')) withApi else "$withApi/"
        }

    // Helper for backward compatibility
    @Deprecated("Use selectedAlbumIds instead")
    val selectedAlbumId: String? get() = selectedAlbumIds.firstOrNull()
}

private fun SharedPreferences.onChangeFlow(): Flow<Unit> = callbackFlow {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        trySend(Unit).isSuccess
    }
    registerOnSharedPreferenceChangeListener(listener)
    trySend(Unit)
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}

