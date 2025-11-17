package io.github.legandy.volumemanager.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.legandy.volumemanager.core.MyApplication
import io.github.legandy.volumemanager.settings.SettingsScreen
import io.github.legandy.volumemanager.setup.SetupScreen
import io.github.legandy.volumemanager.setup.SetupViewModel

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
            hasNotificationAccess = uiState.hasNotificationAccess,
            onGrantShizukuClick = { /* Handled by ShizukuPermissionScreen directly opening app */ },
            onOpenAccessibilityClick = { /* Handled by AccessibilityPermissionScreen directly opening settings */ },
            onOpenNotificationAccessClick = { /* Handled by NotificationPermissionScreen directly opening settings */ },
            onGrantAllPermissionsClick = setupViewModel::grantAllPermissionsWithShizuku
        )
    }
}
