package dev.abdus.apps.immich.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.abdus.apps.immich.ui.screens.AlbumPickerScreen

class AlbumPickerActivity : ComponentActivity() {
    private val viewModel: AlbumPickerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Refresh albums from API when picker is opened
        viewModel.refreshFromApi()

        setContent {
            ImmichTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val imageLoader = ImmichImageLoaderProvider.get(this)
                AlbumPickerScreen(
                    state = state,
                    imageLoader = imageLoader,
                    onAlbumClick = viewModel::toggleAlbum,
                    onSortByChange = viewModel::setSortBy,
                    onToggleReversed = viewModel::toggleSortReversed,
                    onRefresh = viewModel::refreshFromApi,
                    onBack = { finish() }
                )
            }
        }
    }
}
