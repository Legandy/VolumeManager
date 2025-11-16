package io.github.legandy.volumemanager.main

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.legandy.volumemanager.core.MyApplication
import io.github.legandy.volumemanager.main.MainActivity.OnboardingStep
import io.github.legandy.volumemanager.settings.ui.SettingsScreen
import io.github.legandy.volumemanager.setup.SetupScreen
import io.github.legandy.volumemanager.setup.WaitingForShizukuScreen

@Composable
fun MainScreen(
    uiState: MainViewModel.MainUiState,
    isLaunchedFromLauncher: Boolean,
    onOpenAccessibilityClick: () -> Unit,
    onOpenNotificationAccessClick: () -> Unit,
    onGrantShizukuClickFromActivity: () -> Unit,
    onGrantAllPermissionsClick: () -> Unit,
    onNavigateToOnboardingStep: (OnboardingStep) -> Unit,
    onOnboardingComplete: () -> Unit,
    viewModel: MainViewModel
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // Permission launchers (remain in composable context due to rememberLauncherForActivityResult)
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Update ViewModel after permission result
        viewModel.onActivityResume()
    }

    // Lifecycle observer to refresh state on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onActivityResume()
                viewModel.onRefreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Routing Logic
    when {
        !uiState.hasShizukuReady -> WaitingForShizukuScreen()
        uiState.forceOnboardingRestart || (!uiState.isOnboardingCompleted && isLaunchedFromLauncher) -> {
            SetupScreen(
                currentStep = uiState.currentOnboardingStep,
                onNavigateTo = onNavigateToOnboardingStep,
                onOnboardingComplete = onOnboardingComplete,
                hasShizukuPermission = uiState.shizukuPermission,
                isAccessibilityEnabled = uiState.isAccessibilityEnabled,
                hasNotificationAccess = uiState.hasNotificationAccess,
                hasBluetooth = uiState.hasBluetoothPermission,
                onGrantShizukuClick = onGrantShizukuClickFromActivity,
                onOpenAccessibilityClick = onOpenAccessibilityClick,
                onOpenNotificationAccessClick = onOpenNotificationAccessClick,
                onGrantBluetoothClick = { bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
                onGrantAllPermissionsClick = onGrantAllPermissionsClick,
            )
        }
        else -> { // Onboarding completed or not launched from launcher
            SettingsScreen(
                settingsDataStore = MyApplication.settings,
                manager = MyApplication.manager,
            )
        }
    }
}
