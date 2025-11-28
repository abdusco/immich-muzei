package dev.abdus.apps.immich.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.abdus.apps.immich.ui.screens.TagPickerScreen

class TagPickerActivity : ComponentActivity() {
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Refresh tags from API when picker is opened
        viewModel.refreshFromApi()

        setContent {
            MaterialTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                TagPickerScreen(
                    state = state,
                    onTagClick = viewModel::toggleTag,
                    onRefresh = viewModel::refreshFromApi,
                    onBack = { finish() }
                )
            }
        }
    }
}

