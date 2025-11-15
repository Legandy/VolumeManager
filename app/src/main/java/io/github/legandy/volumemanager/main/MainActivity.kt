package io.github.legandy.volumemanager.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.github.legandy.volumemanager.core.MyApplication
import io.github.legandy.volumemanager.overlay.OverlayService
import io.github.legandy.volumemanager.ui.theme.VolumeManagerTheme
import android.provider.Settings

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainActivityViewModel by viewModels() // Instantiate ViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action?.let { isShortcutAction(it) } == true) {
            handleShortcutIntent(intent)
            return
        }

        if (intent?.getBooleanExtra("EXTRA_RESTART_ONBOARDING", false) == true) {
            mainViewModel.requestOnboardingRestart()
            intent?.removeExtra("EXTRA_RESTART_ONBOARDING") // Clear the extra
        }

        enableEdgeToEdge()
        setContent {
            val isLaunchedFromLauncher = remember { // Moved remember block here
                intent?.action == Intent.ACTION_MAIN && intent?.categories?.contains(Intent.CATEGORY_LAUNCHER) == true
            }

            val uiState by mainViewModel.uiState.collectAsState()

            VolumeManagerTheme {
                MainScreen(
                    uiState = uiState,
                    isLaunchedFromLauncher = isLaunchedFromLauncher,
                    onOpenAccessibilityClick = { openAccessibilitySettings() },
                    onOpenNotificationAccessClick = { openNotificationAccessSettings() },
                    onGrantShizukuClickFromActivity = { MyApplication.manager.requestShizukuPermission(this) },
                    onNavigateToOnboardingStep = mainViewModel::navigateToOnboardingStep,
                    onOnboardingComplete = mainViewModel::completeOnboarding,
                    viewModel = mainViewModel
                )
            }
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

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openNotificationAccessSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun isShortcutAction(action: String) = action.startsWith("io.github.legandy.volumemanager.action")

    private fun handleShortcutIntent(intent: Intent) {
        startService(Intent(this, OverlayService::class.java).setAction(intent.action))
        finish()
    }
}
