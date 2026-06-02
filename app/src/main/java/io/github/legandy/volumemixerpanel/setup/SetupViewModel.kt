package io.github.legandy.volumemixerpanel.setup

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.snapshotFlow
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.legandy.volumemixerpanel.core.PermissionManager
import io.github.legandy.volumemixerpanel.core.ShizukuManager
import io.github.legandy.volumemixerpanel.core.VolumeManager
import io.github.legandy.volumemixerpanel.di.SetupPreferencesDataStore
import io.github.legandy.volumemixerpanel.overlay.OverlayService
import io.github.legandy.volumemixerpanel.utils.isAccessibilityServiceEnabled
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    @SetupPreferencesDataStore private val dataStore: DataStore<Preferences>,
    private val shizukuManager: ShizukuManager,
    private val volumeManager: VolumeManager,
    private val permissionManager: PermissionManager
) : ViewModel() {

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
                _uiState.value = _uiState.value.copy(
                    isOnboardingCompleted = isOnboardingCompletedFromDataStore,
                    isLoaded = true
                )
            }
        }
        viewModelScope.launch {
            snapshotFlow { shizukuManager.shizukuReady }.collect { isReady ->
                _uiState.value = _uiState.value.copy(hasShizukuReady = isReady)
                updateCanGoNext(_uiState.value.currentOnboardingStep)
            }
        }
        viewModelScope.launch {
            snapshotFlow { shizukuManager.shizukuPermission }.collect { hasPermission ->
                _uiState.value = _uiState.value.copy(shizukuPermission = hasPermission)
                updateCanGoNext(_uiState.value.currentOnboardingStep)
            }
        }
        onActivityResume() // Initial check
    }

    fun onActivityResume() {
        shizukuManager.refreshState()
        _uiState.value = _uiState.value.copy(
            isAccessibilityEnabled = isAccessibilityServiceEnabled(context),
            hasNotificationPolicyAccess = checkNotificationPolicyAccess(),
            hasShizukuReady = shizukuManager.shizukuReady,
            shizukuPermission = shizukuManager.shizukuPermission,
            hasOverlayPermission = checkOverlayPermission()
        )
        updateCanGoNext(_uiState.value.currentOnboardingStep)
    }

    private fun checkNotificationPolicyAccess(): Boolean {
        return (context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).isNotificationPolicyAccessGranted
    }

    private fun checkOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(context)
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
                permissionManager.grantWriteSecureSettingsPermission()
                permissionManager.enableAccessibilityService(ComponentName(context.packageName, OverlayService::class.java.name))
                permissionManager.grantNotificationPolicyPermission() // Grant Notification Policy Access
                permissionManager.grantSystemAlertWindowPermission() // Grant Overlay Permission
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
                permissionManager.grantNotificationPolicyPermission()
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
        val isLoaded: Boolean = false,
        val currentOnboardingStep: OnboardingStep = OnboardingStep.Welcome,
        val canGoNext: Boolean = true // Default to true for Welcome screen
    )

    companion object {
        // Keys for SavedStateHandle
        private const val CURRENT_ONBOARDING_STEP_KEY = "currentOnboardingStep"
        val IS_ONBOARDING_COMPLETED_DATASTORE_KEY = booleanPreferencesKey("is_onboarding_completed")
    }
}
