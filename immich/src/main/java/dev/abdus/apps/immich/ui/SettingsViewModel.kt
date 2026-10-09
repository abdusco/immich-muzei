package dev.abdus.apps.immich.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    fun toggleFavoritesOnly() {
        prefs.updateFavoritesOnly(!state.value.config.favoritesOnly)
    }

    fun updateFilterDaysBack(days: Int?) {
        prefs.updateFilterDaysBack(days)
    }

    fun isImmichActiveSource(): Boolean {
        return muzeiNavigator.isImmichActiveSource()
    }

    fun launchChooseMuzeiSource(context: Context) {
        muzeiNavigator.launchChooseMuzeiSource(context)
    }
}
