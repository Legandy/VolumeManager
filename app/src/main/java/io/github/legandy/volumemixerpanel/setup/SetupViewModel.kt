package io.github.legandy.volumemixerpanel.setup

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.legandy.volumemixerpanel.core.MyApplication
import io.github.legandy.volumemixerpanel.core.ShizukuManager
import io.github.legandy.volumemixerpanel.overlay.OverlayService
import io.github.legandy.volumemixerpanel.utils.isAccessibilityServiceEnabled
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import android.provider.Settings
import android.os.Build

class SetupViewModel(application: Application, private val savedStateHandle: SavedStateHandle, private val dataStore: DataStore<Preferences>) : AndroidViewModel(application) {

    private val manager: ShizukuManager = MyApplication.manager

    // UI State
    private val _uiState = MutableStateFlow(
        SetupUiState(
            currentOnboardingStep = savedStateHandle.get<OnboardingStep>(CURRENT_ONBOARDING_STEP_KEY) ?: OnboardingStep.Welcome,
            isOnboardingCompleted = false // Temporary initial value, will be loaded from DataStore
        )
    )
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Continuously collect isOnboardingCompleted from DataStore
            dataStore.data.collect { preferences ->
                val isOnboardingCompletedFromDataStore = preferences[IS_ONBOARDING_COMPLETED_DATASTORE_KEY] ?: false
                _uiState.value = _uiState.value.copy(isOnboardingCompleted = isOnboardingCompletedFromDataStore)
            }
        }
        viewModelScope.launch {
            snapshotFlow { manager.shizukuReady }.collect { isReady ->
                _uiState.value = _uiState.value.copy(hasShizukuReady = isReady)
                updateCanGoNext(_uiState.value.currentOnboardingStep)
            }
        }
        viewModelScope.launch {
            snapshotFlow { manager.shizukuPermission }.collect { hasPermission ->
                _uiState.value = _uiState.value.copy(shizukuPermission = hasPermission)
                updateCanGoNext(_uiState.value.currentOnboardingStep)
            }
        }
        onActivityResume() // Initial check
    }

    fun onActivityResume() {
        _uiState.value = _uiState.value.copy(
            isAccessibilityEnabled = isAccessibilityServiceEnabled(getApplication()),
            hasNotificationPolicyAccess = checkNotificationPolicyAccess(),
            hasShizukuReady = manager.shizukuReady,
            shizukuPermission = manager.shizukuPermission,
            hasOverlayPermission = checkOverlayPermission()
        )
        updateCanGoNext(_uiState.value.currentOnboardingStep)
    }

    private fun checkNotificationPolicyAccess(): Boolean {
        return (getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).isNotificationPolicyAccessGranted
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(getApplication())
        } else {
            true // Permission not needed on older Android versions
        }
    }

    fun navigateToOnboardingStep(newStep: OnboardingStep) {
        _uiState.value = _uiState.value.copy(currentOnboardingStep = newStep)
        savedStateHandle[CURRENT_ONBOARDING_STEP_KEY] = newStep
        updateCanGoNext(newStep)
    }

    private fun updateCanGoNext(step: OnboardingStep) {
        val canGoNext = when (step) {
            OnboardingStep.Welcome -> true
            OnboardingStep.Shizuku -> _uiState.value.shizukuPermission
            OnboardingStep.Accessibility -> _uiState.value.isAccessibilityEnabled
            OnboardingStep.NotificationPolicyAccess -> _uiState.value.hasNotificationPolicyAccess
            OnboardingStep.OverlayPermission -> _uiState.value.hasOverlayPermission
            OnboardingStep.Complete -> true
        }
        _uiState.value = _uiState.value.copy(canGoNext = canGoNext)
    }


    fun completeOnboarding() {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[IS_ONBOARDING_COMPLETED_DATASTORE_KEY] = true
            }
            _uiState.value = _uiState.value.copy(isOnboardingCompleted = true)
        }
    }

    fun resetOnboarding() {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[IS_ONBOARDING_COMPLETED_DATASTORE_KEY] = false
            }
            _uiState.value = _uiState.value.copy(isOnboardingCompleted = false)
            _uiState.value = _uiState.value.copy(currentOnboardingStep = OnboardingStep.Welcome)
            savedStateHandle[CURRENT_ONBOARDING_STEP_KEY] = OnboardingStep.Welcome
        }
    }

    fun grantAllPermissionsWithShizuku() {
        viewModelScope.launch {
            try {
                manager.grantWriteSecureSettingsPermission()
                manager.enableAccessibilityService(ComponentName(getApplication<Application>().packageName, OverlayService::class.java.name))
                manager.grantNotificationPolicyPermission() // Grant Notification Policy Access
                manager.grantSystemAlertWindowPermission() // Grant Overlay Permission
                navigateToOnboardingStep(OnboardingStep.Complete) // Navigate to overlay permission step
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
            onActivityResume()
        }
    }

    fun onOpenNotificationPolicyAccessClick() {
        viewModelScope.launch {
            try {
                manager.grantNotificationPolicyPermission()
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
            // onActivityResume will be called from the Activity's onResume
        }
    }

    enum class OnboardingStep {
        Welcome,
        Shizuku,
        Accessibility,
        NotificationPolicyAccess,
        OverlayPermission, // New step for SYSTEM_ALERT_WINDOW
        Complete
    }

    data class SetupUiState(
        val hasShizukuReady: Boolean = false,
        val shizukuPermission: Boolean = false,
        val isAccessibilityEnabled: Boolean = false,
        val hasNotificationPolicyAccess: Boolean = false,
        val hasOverlayPermission: Boolean = false, // New field
        val isOnboardingCompleted: Boolean = false,
        val currentOnboardingStep: OnboardingStep = OnboardingStep.Welcome,
        val canGoNext: Boolean = true // Default to true for Welcome screen
    )

    companion object {
        // Keys for SavedStateHandle
        private const val CURRENT_ONBOARDING_STEP_KEY = "currentOnboardingStep"
        val IS_ONBOARDING_COMPLETED_DATASTORE_KEY = booleanPreferencesKey("is_onboarding_completed")
    }
}