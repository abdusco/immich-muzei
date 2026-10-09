package dev.abdus.apps.immich.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.abdus.apps.immich.ui.screens.ConfigScreen

class ConfigActivity : ComponentActivity() {
    private val viewModel: ConfigViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val config = viewModel.config
        setContent {
            ImmichTheme {
                ConfigScreen(
                    serverUrl = config.serverUrl.orEmpty(),
                    apiKey = config.apiKey.orEmpty(),
                    onBack = { finish() },
                    onSave = { url, key ->
                        val error = viewModel.verifyAndSaveCredentials(url, key)
                        if (error == null) {
                            setResult(RESULT_OK)
                            finish()
                        }
                        error
                    },
                    onTest = viewModel::testCredentials
                )
            }
        }
    }
}
