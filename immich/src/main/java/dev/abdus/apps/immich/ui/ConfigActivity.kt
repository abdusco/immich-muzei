package dev.abdus.apps.immich.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.abdus.apps.immich.ui.screens.ConfigScreen

class ConfigActivity : ComponentActivity() {
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                ConfigScreen(
                    serverUrl = state.config.serverUrl ?: "",
                    apiKey = state.config.apiKey ?: "",
                    onBack = { finish() },
                    onSave = { url, key ->
                        viewModel.updateCredentials(url, key)
                        finish()
                    },
                    onTest = viewModel::testCredentials
                )
            }
        }
    }
}

