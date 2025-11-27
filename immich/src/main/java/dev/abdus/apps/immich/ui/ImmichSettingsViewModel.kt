package dev.abdus.apps.immich.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.util.DebugLogger
import dev.abdus.apps.immich.api.ImmichService
import dev.abdus.apps.immich.data.ImmichAlbumMapper
import dev.abdus.apps.immich.data.ImmichConfig
import dev.abdus.apps.immich.data.ImmichPreferences
import dev.abdus.apps.immich.data.ImmichRepository
import dev.abdus.apps.immich.data.ImmichUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class ImmichSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = ImmichPreferences(application)
    private val repository = ImmichRepository()

    private val _state = MutableStateFlow(ImmichUiState())
    val state: StateFlow<ImmichUiState> = _state

    private var loadJob: Job? = null

    companion object {
        private const val TAG = "ImmichSettingsVM"
        private val prettyJson = kotlinx.serialization.json.Json { prettyPrint = true }
    }

    init {
        // Load cached data immediately
        loadCachedData()

        viewModelScope.launch {
            prefs.configFlow.collectLatest { config ->
                Log.d(TAG, "Config changed: serverUrl=${config.serverUrl}, hasApiKey=${!config.apiKey.isNullOrBlank()}")
                val oldConfig = _state.value.config
                _state.value = _state.value.copy(config = config)

                // Don't automatically load from API - let the picker screens handle that
                // Only clear cached data if credentials changed
                if (config.serverUrl != oldConfig.serverUrl || config.apiKey != oldConfig.apiKey) {
                    if (!config.isConfigured) {
                        _state.value = _state.value.copy(
                            albums = emptyList(),
                            tags = emptyList()
                        )
                    }
                }
            }
        }
    }

    private fun loadCachedData() {
        val cachedAlbums = prefs.getCachedAlbums()
        val cachedTags = prefs.getCachedTags()
        Log.d(TAG, "Loaded ${cachedAlbums.size} cached albums and ${cachedTags.size} cached tags")
        _state.value = _state.value.copy(
            albums = cachedAlbums,
            tags = cachedTags
        )
        applySorting()
    }

    /**
     * Refresh albums and tags from API. Called explicitly by picker screens.
     */
    fun refreshFromApi() {
        val config = _state.value.config
        if (!config.isConfigured) return
        loadAlbumsAndTags(config)
    }

    fun updateCredentials(serverUrl: String, apiKey: String) {
        Log.d(TAG, "Updating credentials: serverUrl=$serverUrl")
        prefs.updateServer(serverUrl, apiKey)
    }

    @Deprecated("Use toggleAlbum for multi-select")
    fun selectAlbum(id: String?) {
        Log.d(TAG, "Selecting album: $id")
        prefs.updateSelectedAlbum(id)
    }

    fun toggleAlbum(id: String) {
        val currentSelection = _state.value.config.selectedAlbumIds
        val newSelection = currentSelection.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
        Log.d(TAG, "Toggling album $id, new selection size: ${newSelection.size}")
        prefs.updateSelectedAlbums(newSelection)
    }

    fun toggleTag(id: String) {
        val currentSelection = _state.value.config.selectedTagIds
        val newSelection = currentSelection.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
        Log.d(TAG, "Toggling tag $id, new selection size: ${newSelection.size}")

        // Update preferences which will trigger config flow
        prefs.updateSelectedTags(newSelection)
    }

    fun toggleFavoritesOnly() {
        val newValue = !_state.value.config.favoritesOnly
        Log.d(TAG, "Toggling favorites only: $newValue")
        prefs.updateFavoritesOnly(newValue)
    }

    fun setSortBy(sortBy: dev.abdus.apps.immich.data.AlbumSortBy) {
        _state.value = _state.value.copy(sortBy = sortBy)
        applySorting()
    }

    fun toggleSortReversed() {
        _state.value = _state.value.copy(sortReversed = !_state.value.sortReversed)
        applySorting()
    }

    fun clearPhotos() {
        Log.d(TAG, "Clearing all photos")
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val contentUri = com.google.android.apps.muzei.api.provider.ProviderContract.getContentUri(
                    dev.abdus.apps.immich.BuildConfig.IMMICH_AUTHORITY
                )
                val deletedCount = context.contentResolver.delete(contentUri, null, null)
                Log.d(TAG, "Deleted $deletedCount photos")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing photos", e)
            }
        }
    }

    private fun applySorting() {
        val sorted = when (_state.value.sortBy) {
            dev.abdus.apps.immich.data.AlbumSortBy.NAME -> _state.value.albums.sortedBy { it.title }
            dev.abdus.apps.immich.data.AlbumSortBy.ASSET_COUNT -> _state.value.albums.sortedBy { it.assetCount }
            dev.abdus.apps.immich.data.AlbumSortBy.UPDATED_AT -> _state.value.albums // Keep original order (from API)
        }
        _state.value = _state.value.copy(
            albums = if (_state.value.sortReversed) sorted.reversed() else sorted
        )
    }

    private fun loadAlbumsAndTags(config: ImmichConfig) {
        if (!config.isConfigured) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                Log.d(TAG, "Loading albums and tags from ${config.apiBaseUrl}")
                val service = ImmichService.create(
                    baseUrl = checkNotNull(config.apiBaseUrl),
                    apiKey = config.apiKey!!
                )

                // Fetch albums
                val albums = repository.fetchAlbums(service)
                Log.d(TAG, "Fetched ${albums.size} albums")
                val uiAlbums = albums.map { album ->
                    val mapped = ImmichAlbumMapper.toUiModel(album, config.serverUrl!!, config.apiKey!!)
                    Log.d(TAG, "Album: ${album.albumName}, coverUrl=${mapped.coverUrl}")
                    mapped
                }

                // Fetch tags
                val tags = repository.fetchTags(service)
                Log.d(TAG, "Fetched ${tags.size} tags")
                val uiTags = tags.map { tag ->
                    dev.abdus.apps.immich.data.ImmichTagUiModel(
                        id = tag.id,
                        name = tag.name
                    )
                }

                // Cache the fetched data
                prefs.saveCachedAlbums(uiAlbums)
                prefs.saveCachedTags(uiTags)
                Log.d(TAG, "Cached ${uiAlbums.size} albums and ${uiTags.size} tags")

                _state.value = _state.value.copy(
                    albums = uiAlbums,
                    tags = uiTags,
                    isLoading = false,
                    errorMessage = null
                )
                applySorting()
            } catch (t: Throwable) {
                Log.e(TAG, "Error loading albums and tags", t)
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = t.message
                )
            }
        }
    }

    /**
     * Test credentials by calling the server info endpoint.
     * Returns prettified JSON response or error message.
     */
    suspend fun testCredentials(serverUrl: String, apiKey: String): String {
        return try {
            val normalizedUrl = serverUrl.trim().removeSuffix("/")
            val baseUrl = if (normalizedUrl.endsWith("/api")) {
                normalizedUrl
            } else {
                "$normalizedUrl/api"
            }
            val finalUrl = if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/"

            Log.d(TAG, "Testing credentials with URL: $finalUrl")
            val service = ImmichService.create(finalUrl, apiKey.trim())
            val response = service.getServerInfo()

            // Prettify JSON response
            prettyJson.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), response)
        } catch (e: Exception) {
            Log.e(TAG, "Error testing credentials", e)
            "Error: ${e.message ?: e.javaClass.simpleName}\n\n${e.stackTraceToString()}"
        }
    }
}
