package io.github.legandy.volumemixerpanel.app

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
import io.github.legandy.volumemixerpanel.settings.ThemeMode
import io.github.legandy.volumemixerpanel.ui.theme.VolumeMixerPanelTheme
import io.github.legandy.volumemixerpanel.setup.SetupViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AppSettingsActivity : ComponentActivity() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    private val appSettingsViewModel: AppSettingsViewModel by viewModels()

    private val setupViewModel: SetupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by settingsDataStore.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            VolumeMixerPanelTheme(themeMode = themeMode) {
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
