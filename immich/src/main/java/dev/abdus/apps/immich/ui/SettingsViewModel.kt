package dev.abdus.apps.immich.ui

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.abdus.apps.immich.api.ImmichService
import dev.abdus.apps.immich.data.ImmichPreferences
import dev.abdus.apps.immich.data.ImmichUiState
import dev.abdus.apps.immich.provider.MuzeiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = ImmichPreferences(application)
    private val muzeiProvider = MuzeiProvider(application)

    private val _state = MutableStateFlow(ImmichUiState())
    val state: StateFlow<ImmichUiState> = _state

    // Tracks whether the user made changes that should trigger a photo refresh when they leave
    private var hasPendingChanges: Boolean = false

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

                // Clear cached data if credentials changed
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
    }

    /**
     * Reload cached data from preferences. Called when returning from picker screens.
     */
    fun reloadCachedData() {
        loadCachedData()
    }

    fun updateCredentials(serverUrl: String, apiKey: String) {
        Log.d(TAG, "Updating credentials: serverUrl=$serverUrl")
        prefs.updateServer(serverUrl, apiKey)
        markPendingChanges()
    }

    fun toggleAlbum(id: String) {
        val currentSelection = _state.value.config.selectedAlbumIds
        val newSelection = currentSelection.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
        Log.d(TAG, "Toggling album $id, new selection size: ${newSelection.size}")
        prefs.updateSelectedAlbums(newSelection)
        markPendingChanges()
    }

    fun toggleTag(id: String) {
        val currentSelection = _state.value.config.selectedTagIds
        val newSelection = currentSelection.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
        Log.d(TAG, "Toggling tag $id, new selection size: ${newSelection.size}")
        prefs.updateSelectedTags(newSelection)
        markPendingChanges()
    }

    fun toggleFavoritesOnly() {
        val newValue = !_state.value.config.favoritesOnly
        Log.d(TAG, "Toggling favorites only: $newValue")
        prefs.updateFavoritesOnly(newValue)
        markPendingChanges()
    }

    fun clearPhotos() {
        Log.d(TAG, "Clearing all photos")
        viewModelScope.launch {
            try {
                // perform clearing on IO
                withContext(Dispatchers.IO) {
                    muzeiProvider.clearPhotos()
                }
                // notify user on main thread
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Clearing photos...", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing photos", e)
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

    /**
     * Check if Immich is currently the active Muzei source.
     * Returns true if Immich is active, false otherwise.
     */
    fun isImmichActiveSource(): Boolean {
        return muzeiProvider.isImmichActiveSource()
    }


    fun updateFilterDaysBack(days: Int?) {
        Log.d(TAG, "Updating filter days-back: $days")
        prefs.updateFilterDaysBack(days)
        markPendingChanges()
    }

    /**
     * Apply pending changes if any (clears cached photos once).
     * This should be invoked when the user is leaving the settings UI (e.g., Activity.onPause/onStop).
     */
    fun applyPendingChangesIfAny(clearIfChanged: Boolean = true) {
        if (clearIfChanged && hasPendingChanges) {
            Log.d(TAG, "Settings changed; clearing photos on exit")
            clearPhotos()
            hasPendingChanges = false
        } else {
            Log.d(TAG, "No pending setting changes or clear disabled; skipping clearPhotos on exit")
        }
    }

    private fun markPendingChanges() {
        hasPendingChanges = true
    }
}
