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
import android.net.Uri
import android.provider.Settings

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
                val uiState by setupViewModel.uiState.collectAsState()

                LaunchedEffect(uiState.isOnboardingCompleted, uiState.hasShizukuReady, uiState.shizukuPermission, uiState.isAccessibilityEnabled, uiState.hasNotificationPolicyAccess, uiState.hasOverlayPermission) {
                    val allPermissionsGranted = uiState.hasShizukuReady &&
                            uiState.shizukuPermission &&
                            uiState.isAccessibilityEnabled &&
                            uiState.hasNotificationPolicyAccess &&
                            uiState.hasOverlayPermission

                    if (!uiState.isOnboardingCompleted || !allPermissionsGranted) {
                        // Launch SetupActivity if onboarding is not completed OR if any required permission is not granted
                        val intent = Intent(this@MainActivity, SetupActivity::class.java)
                        startActivity(intent)
                        finish() // Finish MainActivity to prevent it from showing up in the back stack
                    } else {
                        // All conditions met, show SettingsScreen
                        // This block will only be reached if isOnboardingCompleted is true AND allPermissionsGranted is true
                    }
                }

                // Only show SettingsScreen if onboarding is completed AND all permissions are granted
                if (uiState.isOnboardingCompleted &&
                    uiState.hasShizukuReady &&
                    uiState.shizukuPermission &&
                    uiState.isAccessibilityEnabled &&
                    uiState.hasNotificationPolicyAccess &&
                    uiState.hasOverlayPermission) {
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