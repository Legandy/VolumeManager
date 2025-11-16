package io.github.legandy.volumemanager.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.legandy.volumemanager.core.MyApplication
import io.github.legandy.volumemanager.core.ShizukuManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch


class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val manager: ShizukuManager = MyApplication.manager

    private val _uiState = MutableStateFlow(
        MainUiState()
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            snapshotFlow { manager.shizukuReady }.collect { isReady ->
                _uiState.value = _uiState.value.copy(hasShizukuReady = isReady)
            }
        }
        viewModelScope.launch {
            snapshotFlow { manager.shizukuPermission }.collect { hasPermission ->
                _uiState.value = _uiState.value.copy(shizukuPermission = hasPermission)
            }
        }
    }

    data class MainUiState(
        val hasShizukuReady: Boolean = false,
        val shizukuPermission: Boolean = false
    )
}
