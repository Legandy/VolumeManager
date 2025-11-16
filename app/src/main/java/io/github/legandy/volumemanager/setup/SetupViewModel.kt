package io.github.legandy.volumemanager.setup

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.legandy.volumemanager.core.MyApplication
import io.github.legandy.volumemanager.core.ShizukuManager
import io.github.legandy.volumemanager.overlay.OverlayService
import io.github.legandy.volumemanager.utils.isAccessibilityServiceEnabled
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import android.app.Application

class SetupViewModel(application: Application, private val savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {

    private val manager: ShizukuManager = MyApplication.manager

    // UI State
    private val _uiState = MutableStateFlow(
        SetupUiState(
            currentOnboardingStep = savedStateHandle.get<OnboardingStep>(CURRENT_ONBOARDING_STEP_KEY) ?: OnboardingStep.Welcome,
            isOnboardingCompleted = savedStateHandle.get<Boolean>(IS_ONBOARDING_COMPLETED_KEY) ?: false
        )
    )
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        onActivityResume()

        viewModelScope.launch {
            snapshotFlow { manager.shizukuReady }.collect { isReady ->
                _uiState.value = _uiState.value.copy(hasShizukuReady = isReady)
                if (isReady && !_uiState.value.shizukuPermission) {
                    onActivityResume()
                }
            }
        }
        viewModelScope.launch {
            snapshotFlow { manager.shizukuPermission }.collect { hasPermission ->
                _uiState.value = _uiState.value.copy(shizukuPermission = hasPermission)
            }
        }
    }

    fun onActivityResume() {
        _uiState.value = _uiState.value.copy(
            isAccessibilityEnabled = isAccessibilityServiceEnabled(getApplication()),
            hasNotificationAccess = checkNotificationAccess(),
            hasBluetoothPermission = checkBluetoothPermission(),
            hasShizukuReady = manager.shizukuReady,
            shizukuPermission = manager.shizukuPermission
        )
    }

    private fun checkBluetoothPermission(): Boolean {
        return ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkNotificationAccess(): Boolean {
        return (getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).isNotificationPolicyAccessGranted
    }

    fun navigateToOnboardingStep(newStep: OnboardingStep) {
        _uiState.value = _uiState.value.copy(currentOnboardingStep = newStep)
        savedStateHandle[CURRENT_ONBOARDING_STEP_KEY] = newStep
    }

    fun completeOnboarding() {
        _uiState.value = _uiState.value.copy(isOnboardingCompleted = true)
        savedStateHandle[IS_ONBOARDING_COMPLETED_KEY] = true
    }

    fun requestBluetoothPermission() {
        // This needs to be handled by the Activity, as ViewModel cannot directly launch permission requests
        // The SetupActivity will use rememberLauncherForActivityResult to handle this.
        // For now, we'll just refresh the status.
        onActivityResume()
    }

    fun grantAllPermissionsWithShizuku() {
        viewModelScope.launch {
            try {
                manager.grantWriteSecureSettingsPermission()
                manager.enableAccessibilityService(ComponentName(getApplication<Application>().packageName, OverlayService::class.java.name))
                manager.enableNotificationListener(ComponentName(getApplication<Application>().packageName, "io.github.legandy.volumemanager.notification.NotificationListener"))
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
            onActivityResume()
            completeOnboarding()
        }
    }

    enum class OnboardingStep {
        Welcome,
        Shizuku,
        Accessibility,
        Notification,
        Bluetooth,
        Complete
    }

    data class SetupUiState(
        val hasShizukuReady: Boolean = false,
        val shizukuPermission: Boolean = false,
        val isAccessibilityEnabled: Boolean = false,
        val hasNotificationAccess: Boolean = false,
        val hasBluetoothPermission: Boolean = false,
        val isOnboardingCompleted: Boolean = false,
        val currentOnboardingStep: OnboardingStep = OnboardingStep.Welcome
    )

    companion object {
        // Keys for SavedStateHandle
        private const val CURRENT_ONBOARDING_STEP_KEY = "currentOnboardingStep"
        private const val IS_ONBOARDING_COMPLETED_KEY = "isOnboardingCompleted"
    }
}