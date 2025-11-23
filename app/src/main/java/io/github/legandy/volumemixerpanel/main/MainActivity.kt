package io.github.legandy.volumemixerpanel.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.legandy.volumemixerpanel.overlay.OverlayService
import io.github.legandy.volumemixerpanel.setup.SetupActivity
import io.github.legandy.volumemixerpanel.setup.SetupViewModel
import io.github.legandy.volumemixerpanel.ui.theme.VolumeMixerPanelTheme
import io.github.legandy.volumemixerpanel.setup.SetupViewModelFactory
import io.github.legandy.volumemixerpanel.settings.SettingsScreen
import io.github.legandy.volumemixerpanel.core.MyApplication

class MainActivity : ComponentActivity() {

    private val setupViewModel: SetupViewModel by viewModels { SetupViewModelFactory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action?.let { isShortcutAction(it) } == true) {
            handleShortcutIntent(intent)
            return
        }

        enableEdgeToEdge()
        setContent {
            VolumeMixerPanelTheme {
                // Observe the onboarding status
                val uiState by setupViewModel.uiState.collectAsState()

                LaunchedEffect(uiState.isOnboardingCompleted) {
                    if (!uiState.isOnboardingCompleted) {
                        // Launch SetupActivity if onboarding is not completed
                        val intent = Intent(this@MainActivity, SetupActivity::class.java)
                        startActivity(intent)
                        // Temporarily removed finish() to test if the onboarding state updates correctly
                        // finish() // Finish MainActivity to prevent it from showing up in the back stack
                    }
                }

                // Only show SettingsScreen if onboarding is completed
                if (uiState.isOnboardingCompleted) {
                    SettingsScreen(settingsDataStore = MyApplication.settings, manager = MyApplication.manager)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupViewModel.onActivityResume()
    }

    private fun isShortcutAction(action: String) = action.startsWith("io.github.legandy.volumemixerpanel.action")

    private fun handleShortcutIntent(intent: Intent) {
        startService(Intent(this, OverlayService::class.java).setAction(intent.action))
        finish()
    }
}