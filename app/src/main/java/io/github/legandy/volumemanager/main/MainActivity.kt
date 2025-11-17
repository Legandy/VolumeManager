package io.github.legandy.volumemanager.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.legandy.volumemanager.overlay.OverlayService
import io.github.legandy.volumemanager.setup.SetupActivity
import io.github.legandy.volumemanager.setup.SetupViewModel
import io.github.legandy.volumemanager.ui.theme.VolumeManagerTheme
import io.github.legandy.volumemanager.core.MyApplication
import io.github.legandy.volumemanager.setup.SetupViewModelFactory

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
            VolumeManagerTheme {
                // Observe the onboarding status
                val uiState by setupViewModel.uiState.collectAsState()

                LaunchedEffect(uiState.isOnboardingCompleted) {
                    if (!uiState.isOnboardingCompleted) {
                        // Launch SetupActivity if onboarding is not completed
                        val intent = Intent(this@MainActivity, SetupActivity::class.java)
                        startActivity(intent)
                        finish() // Finish MainActivity to prevent it from showing up in the back stack
                    }
                }

                // Only show MainScreen if onboarding is completed
                if (uiState.isOnboardingCompleted) {
                    MainScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure that the onboarding status is always up-to-date when returning to MainActivity
        // This is important in case the user navigates back from SetupActivity after completing onboarding
        setupViewModel.onActivityResume()
    }

    private fun isShortcutAction(action: String) = action.startsWith("io.github.legandy.volumemanager.action")

    private fun handleShortcutIntent(intent: Intent) {
        startService(Intent(this, OverlayService::class.java).setAction(intent.action))
        finish()
    }
}