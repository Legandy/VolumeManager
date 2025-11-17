package io.github.legandy.volumemixerpanel.app

import android.app.Application

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.legandy.volumemixerpanel.settings.SettingsDataStore
import io.github.legandy.volumemixerpanel.core.MyApplication
import io.github.legandy.volumemixerpanel.ui.theme.VolumeMixerPanelTheme
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.legandy.volumemixerpanel.setup.SetupViewModel
import io.github.legandy.volumemixerpanel.setup.SetupViewModelFactory

class AppSettingsActivity : ComponentActivity() {

    private lateinit var settingsDataStore: SettingsDataStore

    // Use a ViewModel factory for AppSettingsViewModel
    private val appSettingsViewModel: AppSettingsViewModel by viewModels { 
        AppSettingsViewModelFactory(application, settingsDataStore) 
    }

    private val setupViewModel: SetupViewModel by viewModels { SetupViewModelFactory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settingsDataStore = MyApplication.settings // Initialize SettingsDataStore here

        setContent {
            VolumeMixerPanelTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by appSettingsViewModel.uiState.collectAsState()

                    AppSettingsScreen(
                        uiState = uiState,
                        onThemeSettingClick = { appSettingsViewModel.showThemeSelectionDialog(true) },
                        onThemeSelected = { newTheme -> appSettingsViewModel.selectThemeMode(newTheme) },
                        onDismissThemeDialog = { appSettingsViewModel.showThemeSelectionDialog(false) },
                        onResetOnboardingRequested = {
                            setupViewModel.resetOnboarding()
                            finish()
                        }
                    )
                }
            }
        }
    }
}


class AppSettingsViewModelFactory(private val application: Application, private val settingsDataStore: SettingsDataStore) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppSettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppSettingsViewModel(application, settingsDataStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}