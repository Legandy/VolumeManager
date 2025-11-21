package io.github.legandy.volumemixerpanel.setup

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.legandy.volumemixerpanel.R
import io.github.legandy.volumemixerpanel.setup.SetupViewModel.OnboardingStep


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    currentStep: OnboardingStep,
    onNavigateTo: (OnboardingStep) -> Unit,
    onOnboardingComplete: () -> Unit,
    hasShizukuPermission: Boolean,
    isAccessibilityEnabled: Boolean,
    hasNotificationPolicyAccess: Boolean,
    hasOverlayPermission: Boolean, // New parameter
    onGrantShizukuClick: () -> Unit,
    onOpenAccessibilityClick: () -> Unit,
    onOpenNotificationPolicyAccessClick: () -> Unit,
    onOpenOverlayPermissionClick: () -> Unit, // New parameter
    onGrantAllPermissionsClick: () -> Unit
) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.onboarding_setup_title)) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (currentStep) {
                OnboardingStep.Welcome -> WelcomeScreen(onGetStartedClick = {
                    onNavigateTo(OnboardingStep.Shizuku)
                })
                OnboardingStep.Shizuku -> ShizukuPermissionScreen(
                    hasShizuku = hasShizukuPermission,
                    onGrantShizukuClick = onGrantShizukuClick,
                    onNextClick = { onNavigateTo(OnboardingStep.Accessibility) },
                    onGrantAllPermissionsClick = onGrantAllPermissionsClick
                )
                OnboardingStep.Accessibility -> AccessibilityPermissionScreen(
                    hasAccessibility = isAccessibilityEnabled,
                    onOpenAccessibilityClick = onOpenAccessibilityClick,
                    onSkipClick = { onNavigateTo(OnboardingStep.NotificationPolicyAccess) },
                    onNextClick = { onNavigateTo(OnboardingStep.NotificationPolicyAccess) }
                )
                OnboardingStep.NotificationPolicyAccess -> NotificationPolicyAccessScreen(
                    hasNotificationPolicyAccess = hasNotificationPolicyAccess,
                    onOpenNotificationPolicyAccessClick = onOpenNotificationPolicyAccessClick,
                    onSkipClick = { onNavigateTo(OnboardingStep.OverlayPermission) }, // Navigate to OverlayPermission
                    onNextClick = { onNavigateTo(OnboardingStep.OverlayPermission) } // Navigate to OverlayPermission
                )
                OnboardingStep.OverlayPermission -> OverlayPermissionScreen( // New step
                    hasOverlayPermission = hasOverlayPermission,
                    onOpenOverlayPermissionClick = onOpenOverlayPermissionClick,
                    onSkipClick = { onNavigateTo(OnboardingStep.Complete) },
                    onNextClick = { onNavigateTo(OnboardingStep.Complete) }
                )
                OnboardingStep.Complete -> OnboardingCompleteScreen(onGoToAppClick = {onOnboardingComplete()})
            }
        }
    }
}

@Composable
fun WelcomeScreen(onGetStartedClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Icon(
            Icons.Filled.Headphones, // Example icon
            contentDescription = null, // Decorative icon
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.welcome_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onGetStartedClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.welcome_get_started_button))
        }
    }
}

@Composable
fun ShizukuPermissionScreen(
    hasShizuku: Boolean,
    onGrantShizukuClick: () -> Unit,
    onNextClick: () -> Unit,
    onGrantAllPermissionsClick: () -> Unit
) {
    val context = LocalContext.current
    val shizukuIntent = remember {
        context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Filled.Key, // Example icon
            contentDescription = null, // Decorative icon
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            stringResource(R.string.shizuku_permission_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.shizuku_permission_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StatusCard(
            title = stringResource(R.string.shizuku_card_title),
            description = stringResource(R.string.shizuku_card_description),
            granted = hasShizuku
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                onGrantShizukuClick()
                shizukuIntent?.let { context.startActivity(it) }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !hasShizuku
        ) {
            Text(stringResource(R.string.shizuku_grant_button))
        }
        Button(
            onClick = onGrantAllPermissionsClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = hasShizuku // Only enabled if Shizuku is granted
        ) {
            Text(stringResource(R.string.grant_all_permissions_button))
        }
        Button(
            onClick = onNextClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = hasShizuku
        ) {
            Text(stringResource(R.string.shizuku_next_button))
        }
    }
}

@Composable
fun AccessibilityPermissionScreen(
    hasAccessibility: Boolean,
    onOpenAccessibilityClick: () -> Unit,
    onSkipClick: () -> Unit,
    onNextClick: () -> Unit
) {
    val context = LocalContext.current
    val intent = remember { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Filled.Accessibility, // Example icon
            contentDescription = null, // Decorative icon
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            stringResource(R.string.accessibility_service_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.accessibility_service_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StatusCard(
            title = stringResource(R.string.accessibility_card_title),
            description = stringResource(R.string.accessibility_card_description),
            granted = hasAccessibility
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                onOpenAccessibilityClick()
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !hasAccessibility
        ) {
            Text(stringResource(R.string.accessibility_enable_button))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onSkipClick) {
                Text(stringResource(R.string.permission_skip_for_now_button))
            }
            Button(
                onClick = onNextClick,
                enabled = hasAccessibility
            ) {
                Text(stringResource(R.string.shizuku_next_button))
            }
        }
    }
}

@Composable
fun NotificationPolicyAccessScreen(
    hasNotificationPolicyAccess: Boolean,
    onOpenNotificationPolicyAccessClick: () -> Unit,
    onSkipClick: () -> Unit,
    onNextClick: () -> Unit
) {
    val context = LocalContext.current
    val intent = remember { Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Filled.Notifications, // Example icon
            contentDescription = null, // Decorative icon
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            stringResource(R.string.notification_policy_access_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.notification_policy_access_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StatusCard(
            title = stringResource(R.string.notification_policy_access_card_title),
            description = stringResource(R.string.notification_policy_access_card_description),
            granted = hasNotificationPolicyAccess
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                onOpenNotificationPolicyAccessClick()
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !hasNotificationPolicyAccess
        ) {
            Text(stringResource(R.string.notification_policy_access_grant_button))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onSkipClick) {
                Text(stringResource(R.string.permission_skip_for_now_button))
            }
            Button(
                onClick = onNextClick,
                enabled = hasNotificationPolicyAccess
            ) {
                Text(stringResource(R.string.shizuku_next_button))
            }
        }
    }
}

@Composable
fun OverlayPermissionScreen(
    hasOverlayPermission: Boolean,
    onOpenOverlayPermissionClick: () -> Unit,
    onSkipClick: () -> Unit,
    onNextClick: () -> Unit
) {
    // Removed unused 'context' and 'intent' variables

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Filled.Widgets, // Icon for overlay permission
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            stringResource(R.string.overlay_permission_title), // New string resource needed
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.overlay_permission_description), // New string resource needed
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StatusCard(
            title = stringResource(R.string.overlay_permission_card_title), // New string resource needed
            description = stringResource(R.string.overlay_permission_card_description), // New string resource needed
            granted = hasOverlayPermission
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                onOpenOverlayPermissionClick()
                // The activity will handle launching the intent via launcher
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !hasOverlayPermission
        ) {
            Text(stringResource(R.string.overlay_permission_grant_button)) // New string resource needed
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onSkipClick) {
                Text(stringResource(R.string.permission_skip_for_now_button))
            }
            Button(
                onClick = onNextClick,
                enabled = hasOverlayPermission
            ) {
                Text(stringResource(R.string.shizuku_next_button))
            }
        }
    }
}

@Composable
fun OnboardingCompleteScreen(onGoToAppClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Icon(
            Icons.Filled.DoneAll, // Example icon
            contentDescription = null, // Decorative icon
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            stringResource(R.string.onboarding_complete_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.onboarding_complete_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onGoToAppClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_complete_go_to_app_button))
        }
    }
}

@Composable
fun StatusCard(title: String, description: String, granted: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (granted)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = if (granted) stringResource(R.string.status_icon_granted) else stringResource(R.string.status_icon_not_granted),
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun WaitingForShizukuScreen() { /* TODO: Implement actual waiting screen */
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.shizuku_waiting_title),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.shizuku_waiting_message),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}