package dev.abdus.apps.immich.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import dev.abdus.apps.immich.ui.screens.ImmichSettingsScreen

class ImmichSettingsActivity : ComponentActivity() {
    private val viewModel: ImmichSettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface {
                    ImmichSettingsScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload cached data when returning from picker activities
        viewModel.reloadCachedData()
    }
}

