package dev.abdus.apps.immich.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import dev.abdus.apps.immich.api.ImmichClient
import dev.abdus.apps.immich.api.ImmichMinServerVersion
import dev.abdus.apps.immich.data.AppPreferences
import dev.abdus.apps.immich.data.ImmichConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class ConfigViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = AppPreferences(application)

    val config: ImmichConfig get() = prefs.current()

    companion object {
        private const val TAG = "ConfigVM"
        private val prettyJson = Json { prettyPrint = true }
    }

    /** Calls the server info endpoint. Returns the pretty-printed response or the error. */
    suspend fun testCredentials(serverUrl: String, apiKey: String): String {
        return try {
            val info = clientFor(serverUrl, apiKey).getServerInfo()
            prettyJson.encodeToString(JsonObject.serializer(), info)
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
        val version = try {
            clientFor(serverUrl, apiKey).getServerVersion()
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

    private fun clientFor(serverUrl: String, apiKey: String): ImmichClient {
        val config = ImmichConfig(serverUrl.trim().removeSuffix("/"), apiKey.trim())
        return ImmichClient.create(checkNotNull(config.apiBaseUrl), apiKey.trim())
    }
}
