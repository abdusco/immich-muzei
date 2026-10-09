package dev.abdus.apps.immich.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.abdus.apps.immich.api.ImmichClient
import dev.abdus.apps.immich.data.AppPreferences
import dev.abdus.apps.immich.data.ImmichAlbumUiModel
import dev.abdus.apps.immich.data.ImmichConfig
import dev.abdus.apps.immich.data.ImmichRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AlbumSortBy {
    NAME,
    UPDATED_AT,
    ASSET_COUNT,
    MOST_RECENT_PHOTO
}

data class AlbumPickerUiState(
    val config: ImmichConfig,
    val albums: List<ImmichAlbumUiModel>,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val sortBy: AlbumSortBy = AlbumSortBy.ASSET_COUNT,
    val sortReversed: Boolean = true
)

class AlbumPickerViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = AppPreferences(application)

    private val _state = MutableStateFlow(AlbumPickerUiState(prefs.current(), prefs.getCachedAlbums()))
    val state: StateFlow<AlbumPickerUiState> = _state

    private var loadJob: Job? = null

    companion object {
        private const val TAG = "AlbumPickerVM"
    }

    init {
        viewModelScope.launch {
            prefs.configFlow.collect { config -> _state.update { it.copy(config = config) } }
        }
    }

    fun refreshFromApi() {
        val client = ImmichClient.fromConfig(prefs.current()) ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val albums = ImmichRepository(client).fetchAlbums()
                prefs.saveCachedAlbums(albums)
                _state.update { it.copy(albums = albums, isLoading = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading albums", e)
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun toggleAlbum(id: String) {
        val selection = _state.value.config.selectedAlbumIds
        prefs.updateSelectedAlbums(if (id in selection) selection - id else selection + id)
    }

    fun setSortBy(sortBy: AlbumSortBy) {
        _state.update { it.copy(sortBy = sortBy) }
    }

    fun toggleSortReversed() {
        _state.update { it.copy(sortReversed = !it.sortReversed) }
    }
}
