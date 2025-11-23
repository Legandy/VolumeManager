package io.github.legandy.volumemixerpanel.setup

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import io.github.legandy.volumemixerpanel.core.MyApplication
import io.github.legandy.volumemixerpanel.main.MainActivity
import io.github.legandy.volumemixerpanel.ui.theme.VolumeMixerPanelTheme

class SetupActivity : ComponentActivity() {

    private val setupViewModel: SetupViewModel by viewModels { SetupViewModelFactory() }

    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // onResume will handle the state update
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            val uiState by setupViewModel.uiState.collectAsState()

            VolumeMixerPanelTheme {
                if (uiState.isOnboardingCompleted) {
                    // This is handled by the onOnboardingComplete callback,
                    // but this check prevents the SetupScreen from briefly flashing
                    // before navigating away.
                    // We just need to make sure we don't navigate twice.
                } else if (!uiState.hasShizukuReady) {
                    WaitingForShizukuScreen()
                } else {
                    SetupScreen(
                        currentStep = uiState.currentOnboardingStep,
                        onNavigateTo = setupViewModel::navigateToOnboardingStep,
                        onOnboardingComplete = {
                            setupViewModel.completeOnboarding()
                            val intent = Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(intent)
                            finish()
                        },
                        hasShizukuPermission = uiState.shizukuPermission,
                        isAccessibilityEnabled = uiState.isAccessibilityEnabled,
                        hasNotificationPolicyAccess = uiState.hasNotificationPolicyAccess,
                        hasOverlayPermission = uiState.hasOverlayPermission,
                        onGrantShizukuClick = { MyApplication.manager.requestShizukuPermission(this) },
                        onOpenAccessibilityClick = { openAccessibilitySettings() },
                        onOpenNotificationPolicyAccessClick = {
                            setupViewModel.onOpenNotificationPolicyAccessClick()
                            openNotificationPolicyAccessSettings()
                        },
                        onOpenOverlayPermissionClick = { openOverlayPermissionSettings() },
                        onGrantAllPermissionsClick = { setupViewModel.grantAllPermissionsWithShizuku() },
                        canGoNext = uiState.canGoNext // Pass the new state
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

    private fun openNotificationPolicyAccessSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openOverlayPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:$packageName".toUri()
        )
        overlayPermissionLauncher.launch(intent)
    }
}