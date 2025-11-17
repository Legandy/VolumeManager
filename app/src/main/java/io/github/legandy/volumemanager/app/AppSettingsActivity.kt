package io.github.legandy.volumemanager.app

import android.app.Application
import android.content.Intent
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
import io.github.legandy.volumemanager.settings.SettingsDataStore
import io.github.legandy.volumemanager.core.MyApplication
import io.github.legandy.volumemanager.ui.theme.VolumeManagerTheme
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.legandy.volumemanager.main.MainActivity // Import MainActivity
import io.github.legandy.volumemanager.setup.SetupViewModel // Import SetupViewModel
import io.github.legandy.volumemanager.setup.SetupViewModelFactory // Import SetupViewModelFactory

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
            VolumeManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by appSettingsViewModel.uiState.collectAsState()

                    AppSettingsScreen(
                        uiState = uiState,
                        onShowSetupRequested = { 
                            val intent = Intent(this, MainActivity::class.java).apply {
                                putExtra("EXTRA_RESTART_ONBOARDING", true)
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                            finish() 
                        },
                        onThemeSettingClick = { appSettingsViewModel.showThemeSelectionDialog(true) },
                        onThemeSelected = { newTheme -> appSettingsViewModel.selectThemeMode(newTheme) },
                        onDismissThemeDialog = { appSettingsViewModel.showThemeSelectionDialog(false) },
                        onResetOnboardingRequested = {
                            setupViewModel.resetOnboarding()
                            finish() // Close AppSettingsActivity after resetting onboarding
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