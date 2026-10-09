package dev.abdus.apps.immich.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.abdus.apps.immich.api.ImmichClient
import dev.abdus.apps.immich.api.ImmichMinServerVersion
import dev.abdus.apps.immich.data.AppPreferences
import dev.abdus.apps.immich.data.ImmichAlbumUiModel
import dev.abdus.apps.immich.data.ImmichConfig
import dev.abdus.apps.immich.data.ImmichTagUiModel
import dev.abdus.apps.immich.provider.MuzeiNavigator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val config: ImmichConfig,
    val albums: List<ImmichAlbumUiModel>,
    val tags: List<ImmichTagUiModel>
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = AppPreferences(application)
    private val muzeiNavigator = MuzeiNavigator(application)

    // Album/tag metadata is cached in prefs by the pickers, so any pref change refreshes it here.
    val state: StateFlow<SettingsUiState> = prefs.changes
        .map { readState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), readState())

    private fun readState() = SettingsUiState(prefs.current(), prefs.getCachedAlbums(), prefs.getCachedTags())

    companion object {
        private const val TAG = "ImmichSettingsVM"
        private val prettyJson = kotlinx.serialization.json.Json { prettyPrint = true }
    }

    fun toggleFavoritesOnly() {
        prefs.updateFavoritesOnly(!state.value.config.favoritesOnly)
    }

    fun updateFilterDaysBack(days: Int?) {
        prefs.updateFilterDaysBack(days)
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

        prefs.updateServer(serverUrl, apiKey)
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

    fun isImmichActiveSource(): Boolean {
        return muzeiNavigator.isImmichActiveSource()
    }

    fun launchChooseMuzeiSource(context: Context) {
        muzeiNavigator.launchChooseMuzeiSource(context)
    }
}
