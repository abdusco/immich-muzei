package dev.abdus.apps.immich.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.abdus.apps.immich.api.ImmichClient
import dev.abdus.apps.immich.api.ImmichMinServerVersion
import dev.abdus.apps.immich.data.AppPreferences
import dev.abdus.apps.immich.data.ImmichUiState
import dev.abdus.apps.immich.provider.MuzeiNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = AppPreferences(application)
    private val muzeiNavigator = MuzeiNavigator(application)

    private val _state = MutableStateFlow(ImmichUiState())
    val state: StateFlow<ImmichUiState> = _state

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
    }

    fun toggleFavoritesOnly() {
        val newValue = !_state.value.config.favoritesOnly
        Log.d(TAG, "Toggling favorites only: $newValue")
        prefs.updateFavoritesOnly(newValue)
    }

    /**
     * Test credentials by calling the server info endpoint.
     * Returns prettified JSON response or error message.
     */
    suspend fun testCredentials(serverUrl: String, apiKey: String): String {
        return try {
            val service = ImmichClient.create(normalizeBaseUrl(serverUrl), apiKey.trim())
            val response = service.getServerInfo()

            // Prettify JSON response
            prettyJson.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), response)
        } catch (e: Exception) {
            Log.e(TAG, "Error testing credentials", e)
            "Error: ${e.message ?: e.javaClass.simpleName}\n\n${e.stackTraceToString()}"
        }
    }

    /**
     * Verifies the server meets the minimum supported version before saving credentials.
     * Returns an error message to show the user, or null on success (credentials are saved).
     */
    suspend fun verifyAndSaveCredentials(serverUrl: String, apiKey: String): String? {
        val service = ImmichClient.create(normalizeBaseUrl(serverUrl), apiKey.trim())
        val version = try {
            service.getServerVersion()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking server version", e)
            return "Could not connect to server: ${e.message ?: e.javaClass.simpleName}"
        }

        if (!ImmichMinServerVersion.isSupported(version)) {
            return "Server version v${version.major}.${version.minor}.${version.patch} is not supported. " +
                "Please upgrade your Immich server to ${ImmichMinServerVersion.label()} or later."
        }

        updateCredentials(serverUrl, apiKey)
        return null
    }

    private fun normalizeBaseUrl(serverUrl: String): String {
        val normalizedUrl = serverUrl.trim().removeSuffix("/")
        val baseUrl = if (normalizedUrl.endsWith("/api")) {
            normalizedUrl
        } else {
            "$normalizedUrl/api"
        }
        return if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/"
    }

    /**
     * Check if Immich is currently the active Muzei source.
     * Returns true if Immich is active, false otherwise.
     */
    fun isImmichActiveSource(): Boolean {
        return muzeiNavigator.isImmichActiveSource()
    }

    fun launchChooseMuzeiSource(context: Context) {
        muzeiNavigator.launchChooseMuzeiSource(context)
    }


    fun updateFilterDaysBack(days: Int?) {
        Log.d(TAG, "Updating filter days-back: $days")
        prefs.updateFilterDaysBack(days)
    }
}
