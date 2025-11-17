package io.github.legandy.volumemanager.setup

import android.content.ComponentName
import android.content.Context
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
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit

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
            hasShizukuReady = manager.shizukuReady,
            shizukuPermission = manager.shizukuPermission
        )
    }

    private fun checkNotificationAccess(): Boolean {
        return (getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).isNotificationPolicyAccessGranted
    }

    fun navigateToOnboardingStep(newStep: OnboardingStep) {
        _uiState.value = _uiState.value.copy(currentOnboardingStep = newStep)
        savedStateHandle[CURRENT_ONBOARDING_STEP_KEY] = newStep
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
        Complete
    }

    data class SetupUiState(
        val hasShizukuReady: Boolean = false,
        val shizukuPermission: Boolean = false,
        val isAccessibilityEnabled: Boolean = false,
        val hasNotificationAccess: Boolean = false,
        val isOnboardingCompleted: Boolean = false,
        val currentOnboardingStep: OnboardingStep = OnboardingStep.Welcome
    )

    companion object {
        // Keys for SavedStateHandle
        private const val CURRENT_ONBOARDING_STEP_KEY = "currentOnboardingStep"
        val IS_ONBOARDING_COMPLETED_DATASTORE_KEY = booleanPreferencesKey("is_onboarding_completed")
    }
}
