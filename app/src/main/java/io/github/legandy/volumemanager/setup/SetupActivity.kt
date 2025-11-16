package io.github.legandy.volumemanager.setup

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.legandy.volumemanager.core.MyApplication
import io.github.legandy.volumemanager.ui.theme.VolumeManagerTheme
import android.provider.Settings
import io.github.legandy.volumemanager.main.MainActivity

class SetupActivity : ComponentActivity() {

    private val setupViewModel: SetupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            val uiState by setupViewModel.uiState.collectAsState()

            VolumeManagerTheme {
                if (!uiState.hasShizukuReady) {
                    WaitingForShizukuScreen()
                } else {
                    SetupScreen(
                        currentStep = uiState.currentOnboardingStep,
                        onNavigateTo = setupViewModel::navigateToOnboardingStep,
                        onOnboardingComplete = {
                            setupViewModel.completeOnboarding()
                            // Navigate to MainActivity after onboarding is complete
                            val intent = Intent(this, MainActivity::class.java)
                            startActivity(intent)
                            finish() // Finish SetupActivity so it's not on the back stack
                        },
                        hasShizukuPermission = uiState.shizukuPermission,
                        isAccessibilityEnabled = uiState.isAccessibilityEnabled,
                        hasNotificationAccess = uiState.hasNotificationAccess,
                        hasBluetooth = uiState.hasBluetoothPermission,
                        onGrantShizukuClick = { MyApplication.manager.requestShizukuPermission(this) },
                        onOpenAccessibilityClick = { openAccessibilitySettings() },
                        onOpenNotificationAccessClick = { openNotificationAccessSettings() },
                        onGrantBluetoothClick = { setupViewModel.requestBluetoothPermission() },
                        onGrantAllPermissionsClick = { setupViewModel.grantAllPermissionsWithShizuku() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupViewModel.onActivityResume()
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openNotificationAccessSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
