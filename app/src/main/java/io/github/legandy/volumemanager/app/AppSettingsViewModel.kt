package io.github.legandy.volumemanager.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.legandy.volumemanager.settings.SettingsDataStore
import io.github.legandy.volumemanager.settings.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppSettingsViewModel(application: Application, private val settingsDataStore: SettingsDataStore) : AndroidViewModel(application) {

    // UI State
    private val _uiState = MutableStateFlow(AppSettingsUiState())
    val uiState: StateFlow<AppSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.themeMode.collect { themeMode ->
                _uiState.value = _uiState.value.copy(themeMode = themeMode)
            }
        }
    }

    fun showThemeSelectionDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showThemeDialog = show)
    }

    fun selectThemeMode(newTheme: ThemeMode) {
        viewModelScope.launch {
            settingsDataStore.setThemeMode(newTheme)
        }
        _uiState.value = _uiState.value.copy(showThemeDialog = false) // Dismiss dialog after selection
    }

    data class AppSettingsUiState(
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val showThemeDialog: Boolean = false
    )
}
