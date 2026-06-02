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
import io.github.legandy.volumemixerpanel.core.PermissionManager
import io.github.legandy.volumemixerpanel.main.MainActivity
import io.github.legandy.volumemixerpanel.ui.theme.VolumeMixerPanelTheme
import io.github.legandy.volumemixerpanel.settings.SettingsDataStore
import io.github.legandy.volumemixerpanel.settings.ThemeMode
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SetupActivity : ComponentActivity() {

    private val setupViewModel: SetupViewModel by viewModels()

    @Inject
    lateinit var permissionManager: PermissionManager

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // onResume will handle the state update
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            val uiState by setupViewModel.uiState.collectAsState()
            val themeMode by settingsDataStore.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

            VolumeMixerPanelTheme(themeMode = themeMode) {
                if (!uiState.hasShizukuReady) {
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
                        onGrantShizukuClick = { permissionManager.requestShizukuPermission(this) },
                        onOpenAccessibilityClick = { openAccessibilitySettings() },
                        onOpenNotificationPolicyAccessClick = {
                            setupViewModel.onOpenNotificationPolicyAccessClick()
                            openNotificationPolicyAccessSettings()
                        },
                        onOpenOverlayPermissionClick = { openOverlayPermissionSettings() },
                        onGrantAllPermissionsClick = { setupViewModel.grantAllPermissionsWithShizuku() },
                        canGoNext = uiState.canGoNext
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