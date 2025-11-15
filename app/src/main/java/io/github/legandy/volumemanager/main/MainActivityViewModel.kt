package io.github.legandy.volumemanager.main

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.legandy.volumemanager.core.Manager
import io.github.legandy.volumemanager.core.MyApplication
import io.github.legandy.volumemanager.main.MainActivity.OnboardingStep
import io.github.legandy.volumemanager.utils.isAccessibilityServiceEnabled
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow

class MainActivityViewModel(application: Application, private val savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {

    private val manager: Manager = MyApplication.manager

    // Keys for SavedStateHandle
    private val CURRENT_ONBOARDING_STEP_KEY = "currentOnboardingStep"
    private val IS_ONBOARDING_COMPLETED_KEY = "isOnboardingCompleted"

    // UI State
    private val _uiState = MutableStateFlow(
        MainUiState(
            currentOnboardingStep = savedStateHandle.get<OnboardingStep>(CURRENT_ONBOARDING_STEP_KEY) ?: OnboardingStep.Welcome,
            isOnboardingCompleted = savedStateHandle.get<Boolean>(IS_ONBOARDING_COMPLETED_KEY) ?: false
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        // Initial state checks
        onActivityResume() // Call the new function for initial state setup

        // Observe Shizuku readiness
        viewModelScope.launch {
            snapshotFlow { manager.shizukuReady }.collect { isReady ->
                _uiState.value = _uiState.value.copy(hasShizukuReady = isReady)
                if (isReady && !_uiState.value.shizukuPermission) {
                    // If Shizuku becomes ready, but permission wasn't granted yet, refresh state.
                    // The actual permission request is triggered by the Activity.
                    onActivityResume() // Use the new function here as well
                }
            }
        }
        viewModelScope.launch {
            snapshotFlow { manager.shizukuPermission }.collect { hasPermission ->
                _uiState.value = _uiState.value.copy(shizukuPermission = hasPermission)
            }
        }
    }

    // Renamed and refactored from checkPermissionsAndRefreshState
    fun onActivityResume() {
        _uiState.value = _uiState.value.copy(
            isAccessibilityEnabled = isAccessibilityServiceEnabled(getApplication()),
            hasNotificationAccess = checkNotificationAccess(),
            hasBluetoothPermission = checkBluetoothPermission(),
            hasShizukuReady = manager.shizukuReady, // Ensure shizukuReady and shizukuPermission are also up-to-date
            shizukuPermission = manager.shizukuPermission
        )
    }

    // New function to specifically refresh dynamic statuses without affecting onboarding step
    fun onRefreshStatus() {
        _uiState.value = _uiState.value.copy(
            isAccessibilityEnabled = isAccessibilityServiceEnabled(getApplication()),
            hasNotificationAccess = checkNotificationAccess(),
            hasBluetoothPermission = checkBluetoothPermission()
        )
    }

    private fun checkBluetoothPermission(): Boolean {
        return ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkNotificationAccess(): Boolean {
        return (getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted
    }

    fun navigateToOnboardingStep(newStep: OnboardingStep) {
        _uiState.value = _uiState.value.copy(currentOnboardingStep = newStep)
        savedStateHandle[CURRENT_ONBOARDING_STEP_KEY] = newStep
    }

    fun completeOnboarding() {
        _uiState.value = _uiState.value.copy(isOnboardingCompleted = true)
        savedStateHandle[IS_ONBOARDING_COMPLETED_KEY] = true
        if (_uiState.value.forceOnboardingRestart) {
            onboardingRestartHandled()
        }
        // In a real app, you'd save this preference to DataStore here
        // For now, it's just in-memory state
    }

    fun requestOnboardingRestart() {
        _uiState.value = _uiState.value.copy(forceOnboardingRestart = true)
    }

    fun onboardingRestartHandled() {
        // This function should only be called when a deliberate restart is requested
        // and needs to reset everything for a fresh start of onboarding.
        _uiState.value = _uiState.value.copy(
            forceOnboardingRestart = false,
            isOnboardingCompleted = false,
            currentOnboardingStep = OnboardingStep.Welcome
        )
        savedStateHandle[IS_ONBOARDING_COMPLETED_KEY] = false
        savedStateHandle[CURRENT_ONBOARDING_STEP_KEY] = OnboardingStep.Welcome
    }

    fun grantAllPermissionsWithShizuku() {
        viewModelScope.launch {
            manager.grantAccessibilityPermission(getApplication())
            manager.grantNotificationPermission(getApplication())
            onRefreshStatus()
            completeOnboarding()
        }
    }

    // This data class represents all the UI state for MainActivity
    data class MainUiState(
        val hasShizukuReady: Boolean = false,
        val shizukuPermission: Boolean = false,
        val isAccessibilityEnabled: Boolean = false,
        val hasNotificationAccess: Boolean = false,
        val hasBluetoothPermission: Boolean = false,
        val isOnboardingCompleted: Boolean = false, // In a real app, this would come from DataStore
        val currentOnboardingStep: OnboardingStep = OnboardingStep.Welcome,
        val forceOnboardingRestart: Boolean = false // For the "show setup" button
    )
}