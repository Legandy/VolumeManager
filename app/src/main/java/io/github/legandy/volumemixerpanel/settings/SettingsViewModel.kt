package io.github.legandy.volumemixerpanel.settings

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.legandy.volumemixerpanel.core.MyApplication

data class AppFilterUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val allApps: List<InstalledAppData> = emptyList()
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsDataStore = MyApplication.settings
    private val packageManager = application.packageManager

    private val _uiState = MutableStateFlow(AppFilterUiState())
    val uiState: StateFlow<AppFilterUiState> = _uiState.asStateFlow()

    val appFilterMode = settingsDataStore.appFilterMode
    val appBlacklist = settingsDataStore.appBlacklist
    val appWhitelist = settingsDataStore.appWhitelist

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val apps = withContext(Dispatchers.IO) {
                packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM == 0) || packageManager.getLaunchIntentForPackage(it.packageName) != null }
                    .mapNotNull {
                        try {
                            // Convert the icon to a stable ImageBitmap here
                            InstalledAppData(
                                it.packageName,
                                it.loadLabel(packageManager).toString(),
                                it.loadIcon(packageManager).toBitmap().asImageBitmap()
                            )
                        } catch (e: Exception) { null }
                    }
                    .sortedBy { it.name.lowercase() }
            }
            _uiState.update { it.copy(isLoading = false, allApps = apps) }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setAppFilterMode(mode: AppFilterMode) {
        viewModelScope.launch { settingsDataStore.setAppFilterMode(mode) }
    }

    fun updateBlacklist(packageName: String, shouldAdd: Boolean) {
        viewModelScope.launch {
            if (shouldAdd) settingsDataStore.addToBlacklist(packageName) else settingsDataStore.removeFromBlacklist(packageName)
        }
    }

    fun updateWhitelist(packageName: String, shouldAdd: Boolean) {
        viewModelScope.launch {
            if (shouldAdd) settingsDataStore.addToWhitelist(packageName) else settingsDataStore.removeFromWhitelist(packageName)
        }
    }
}