package dev.abdus.apps.immich.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.abdus.apps.immich.api.ImmichService
import dev.abdus.apps.immich.data.AlbumSortBy
import dev.abdus.apps.immich.data.ImmichAlbumMapper
import dev.abdus.apps.immich.data.ImmichAlbumUiModel
import dev.abdus.apps.immich.data.ImmichConfig
import dev.abdus.apps.immich.data.ImmichPreferences
import dev.abdus.apps.immich.data.ImmichRepository
import dev.abdus.apps.immich.provider.MuzeiProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class AlbumPickerUiState(
    val config: ImmichConfig = ImmichConfig(null, null, emptySet(), emptySet(), false),
    val albums: List<ImmichAlbumUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val sortBy: AlbumSortBy = AlbumSortBy.ASSET_COUNT,
    val sortReversed: Boolean = true
)

class AlbumPickerViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = ImmichPreferences(application)
    private val repository = ImmichRepository()
    private val muzeiProvider = MuzeiProvider(application)

    private val _state = MutableStateFlow(AlbumPickerUiState())
    val state: StateFlow<AlbumPickerUiState> = _state

    private var loadJob: Job? = null

    companion object {
        private const val TAG = "AlbumPickerVM"
    }

    init {
        // Load cached data immediately
        loadCachedData()

        viewModelScope.launch {
            prefs.configFlow.collectLatest { config ->
                Log.d(TAG, "Config changed: serverUrl=${config.serverUrl}, hasApiKey=${!config.apiKey.isNullOrBlank()}")
                val oldConfig = _state.value.config
                _state.value = _state.value.copy(config = config)

                // Clear cached data if credentials changed
                if (config.serverUrl != oldConfig.serverUrl || config.apiKey != oldConfig.apiKey) {
                    if (!config.isConfigured) {
                        _state.value = _state.value.copy(albums = emptyList())
                    }
                }
            }
        }
    }

    private fun loadCachedData() {
        val cachedAlbums = prefs.getCachedAlbums()
        Log.d(TAG, "Loaded ${cachedAlbums.size} cached albums")
        _state.value = _state.value.copy(albums = cachedAlbums)
        applySorting()
    }

    fun refreshFromApi() {
        val config = _state.value.config
        if (!config.isConfigured) return
        loadAlbums(config)
    }

    fun toggleAlbum(id: String) {
        val currentSelection = _state.value.config.selectedAlbumIds
        val newSelection = currentSelection.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
        Log.d(TAG, "Toggling album $id, new selection size: ${newSelection.size}")
        prefs.updateSelectedAlbums(newSelection)
        clearPhotos()
    }

    fun setSortBy(sortBy: AlbumSortBy) {
        _state.value = _state.value.copy(sortBy = sortBy)
        applySorting()
    }

    fun toggleSortReversed() {
        _state.value = _state.value.copy(sortReversed = !_state.value.sortReversed)
        applySorting()
    }

    private fun clearPhotos() {
        Log.d(TAG, "Clearing all photos")
        viewModelScope.launch {
            muzeiProvider.clearPhotos()
        }
    }

    private fun applySorting() {
        val sorted = when (_state.value.sortBy) {
            AlbumSortBy.NAME -> _state.value.albums.sortedBy { it.title }
            AlbumSortBy.ASSET_COUNT -> _state.value.albums.sortedBy { it.assetCount }
            AlbumSortBy.UPDATED_AT -> _state.value.albums.sortedBy { it.updatedAt ?: "" }
            AlbumSortBy.MOST_RECENT_PHOTO -> _state.value.albums.sortedBy { it.lastModifiedAssetTimestamp ?: "" }
        }
        _state.value = _state.value.copy(
            albums = if (_state.value.sortReversed) sorted.reversed() else sorted
        )
    }

    private fun loadAlbums(config: ImmichConfig) {
        if (!config.isConfigured) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                Log.d(TAG, "Loading albums from ${config.apiBaseUrl}")
                val service = ImmichService.create(
                    baseUrl = checkNotNull(config.apiBaseUrl),
                    apiKey = config.apiKey!!
                )

                val albums = repository.fetchAlbums(service)
                Log.d(TAG, "Fetched ${albums.size} albums")
                val uiAlbums = albums.map { album ->
                    val mapped = ImmichAlbumMapper.toUiModel(album, config.serverUrl!!, config.apiKey!!)
                    Log.d(TAG, "Album: ${album.albumName}, coverUrl=${mapped.coverUrl}")
                    mapped
                }

                prefs.saveCachedAlbums(uiAlbums)
                Log.d(TAG, "Cached ${uiAlbums.size} albums")

                _state.value = _state.value.copy(
                    albums = uiAlbums,
                    isLoading = false,
                    errorMessage = null
                )
                applySorting()
            } catch (t: Throwable) {
                Log.e(TAG, "Error loading albums", t)
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = t.message
                )
            }
        }
    }
}

