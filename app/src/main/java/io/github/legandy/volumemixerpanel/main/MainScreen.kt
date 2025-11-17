package io.github.legandy.volumemixerpanel.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.legandy.volumemixerpanel.core.MyApplication
import io.github.legandy.volumemixerpanel.settings.SettingsScreen
import io.github.legandy.volumemixerpanel.setup.SetupScreen
import io.github.legandy.volumemixerpanel.setup.SetupViewModel

@Composable
fun MainScreen(
    setupViewModel: SetupViewModel = viewModel()
) {
    val uiState by setupViewModel.uiState.collectAsState()

    if (uiState.isOnboardingCompleted) {
        SettingsScreen(
            settingsDataStore = MyApplication.settings,
            manager = MyApplication.manager
        )
    } else {
        SetupScreen(
            currentStep = uiState.currentOnboardingStep,
            onNavigateTo = setupViewModel::navigateToOnboardingStep,
            onOnboardingComplete = setupViewModel::completeOnboarding,
            hasShizukuPermission = uiState.shizukuPermission,
            isAccessibilityEnabled = uiState.isAccessibilityEnabled,
            hasNotificationPolicyAccess = uiState.hasNotificationPolicyAccess, // Changed parameter
            onGrantShizukuClick = { /* Handled by ShizukuPermissionScreen directly opening app */ },
            onOpenAccessibilityClick = { /* Handled by AccessibilityPermissionScreen directly opening settings */ },
            onOpenNotificationPolicyAccessClick = setupViewModel::onOpenNotificationPolicyAccessClick, // Changed parameter
            onGrantAllPermissionsClick = setupViewModel::grantAllPermissionsWithShizuku
        )
    }
}
