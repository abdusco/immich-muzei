package dev.abdus.apps.immich.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.abdus.apps.immich.api.ImmichClientProvider
import dev.abdus.apps.immich.data.AppPreferences
import dev.abdus.apps.immich.data.ImmichConfig
import dev.abdus.apps.immich.data.ImmichRepository
import dev.abdus.apps.immich.data.ImmichTagUiModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TagPickerUiState(
    val config: ImmichConfig,
    val tags: List<ImmichTagUiModel>,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class TagPickerViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = AppPreferences(application)

    private val _state = MutableStateFlow(TagPickerUiState(prefs.current(), prefs.getCachedTags()))
    val state: StateFlow<TagPickerUiState> = _state

    private var loadJob: Job? = null

    companion object {
        private const val TAG = "TagPickerVM"
    }

    init {
        viewModelScope.launch {
            prefs.configFlow.collect { config -> _state.update { it.copy(config = config) } }
        }
    }

    fun refreshFromApi() {
        val client = ImmichClientProvider.fromConfig(prefs.current()) ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val tags = ImmichRepository(client).fetchTags()
                prefs.saveCachedTags(tags)
                _state.update { it.copy(tags = tags, isLoading = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading tags", e)
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun toggleTag(id: String) {
        val selection = _state.value.config.selectedTagIds
        prefs.updateSelectedTags(if (id in selection) selection - id else selection + id)
    }
}
