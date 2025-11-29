package dev.abdus.apps.immich.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.abdus.apps.immich.api.ImmichService
import dev.abdus.apps.immich.data.ImmichConfig
import dev.abdus.apps.immich.data.ImmichPreferences
import dev.abdus.apps.immich.data.ImmichRepository
import dev.abdus.apps.immich.data.ImmichTagUiModel
import dev.abdus.apps.immich.provider.MuzeiProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class TagPickerUiState(
    val config: ImmichConfig = ImmichConfig(null, null, emptySet(), emptySet(), false),
    val tags: List<ImmichTagUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class TagPickerViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = ImmichPreferences(application)
    private val repository = ImmichRepository()
    private val muzeiProvider = MuzeiProvider(application)

    private val _state = MutableStateFlow(TagPickerUiState())
    val state: StateFlow<TagPickerUiState> = _state

    private var loadJob: Job? = null

    companion object {
        private const val TAG = "TagPickerVM"
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
                        _state.value = _state.value.copy(tags = emptyList())
                    }
                }
            }
        }
    }

    private fun loadCachedData() {
        val cachedTags = prefs.getCachedTags()
        Log.d(TAG, "Loaded ${cachedTags.size} cached tags")
        _state.value = _state.value.copy(tags = cachedTags)
    }

    fun refreshFromApi() {
        val config = _state.value.config
        if (!config.isConfigured) return
        loadTags(config)
    }

    fun toggleTag(id: String) {
        val currentSelection = _state.value.config.selectedTagIds
        val newSelection = currentSelection.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
        Log.d(TAG, "Toggling tag $id, new selection size: ${newSelection.size}")
        prefs.updateSelectedTags(newSelection)
        clearPhotos()
    }

    fun updateCreatedAfter(value: String?) {
        Log.d(TAG, "Updating createdAfter: $value")
        prefs.updateCreatedAfter(value)
        clearPhotos()
    }

    fun updateCreatedBefore(value: String?) {
        Log.d(TAG, "Updating createdBefore: $value")
        prefs.updateCreatedBefore(value)
        clearPhotos()
    }

    private fun clearPhotos() {
        Log.d(TAG, "Clearing all photos")
        viewModelScope.launch {
            muzeiProvider.clearPhotos()
        }
    }

    private fun loadTags(config: ImmichConfig) {
        if (!config.isConfigured) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                Log.d(TAG, "Loading tags from ${config.apiBaseUrl}")
                val service = ImmichService.create(
                    baseUrl = checkNotNull(config.apiBaseUrl),
                    apiKey = config.apiKey!!
                )

                val tags = repository.fetchTags(service)
                Log.d(TAG, "Fetched ${tags.size} tags")
                val uiTags = tags.map { tag ->
                    ImmichTagUiModel(
                        id = tag.id,
                        name = tag.name
                    )
                }

                prefs.saveCachedTags(uiTags)
                Log.d(TAG, "Cached ${uiTags.size} tags")

                _state.value = _state.value.copy(
                    tags = uiTags,
                    isLoading = false,
                    errorMessage = null
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Error loading tags", t)
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = t.message
                )
            }
        }
    }
}
