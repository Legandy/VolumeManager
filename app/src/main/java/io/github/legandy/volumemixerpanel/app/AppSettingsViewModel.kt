package io.github.legandy.volumemixerpanel.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.legandy.volumemixerpanel.settings.SettingsDataStore
import io.github.legandy.volumemixerpanel.settings.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

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
