package io.github.legandy.volumemanager.setup

import android.Manifest
import android.content.Intent
//import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
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
import io.github.legandy.volumemanager.R
import io.github.legandy.volumemanager.main.MainActivity


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingFlow(
    currentStep: MainActivity.OnboardingStep,
    onNavigateTo: (MainActivity.OnboardingStep) -> Unit,
    onOnboardingComplete: () -> Unit,
    hasShizukuPermission: Boolean,
    isAccessibilityEnabled: Boolean,
    hasNotificationAccess: Boolean,
    hasBluetooth: Boolean,
    onGrantShizukuClick: () -> Unit,
    onOpenAccessibilityClick: () -> Unit,
    onOpenNotificationAccessClick: () -> Unit,
    onGrantBluetoothClick: () -> Unit,
    onGrantAllPermissionsClick: () -> Unit // New parameter
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
                MainActivity.OnboardingStep.Welcome -> WelcomeScreen(onGetStartedClick = {
                    onNavigateTo(MainActivity.OnboardingStep.Shizuku)
                })
                MainActivity.OnboardingStep.Shizuku -> ShizukuPermissionScreen(
                    hasShizuku = hasShizukuPermission,
                    onGrantShizukuClick = onGrantShizukuClick,
                    onNextClick = { onNavigateTo(MainActivity.OnboardingStep.Accessibility) },
                    onGrantAllPermissionsClick = onGrantAllPermissionsClick // Pass the new parameter
                )
                MainActivity.OnboardingStep.Accessibility -> AccessibilityPermissionScreen(
                    hasAccessibility = isAccessibilityEnabled,
                    onOpenAccessibilityClick = onOpenAccessibilityClick,
                    onSkipClick = { onNavigateTo(MainActivity.OnboardingStep.Notification) },
                    onNextClick = { onNavigateTo(MainActivity.OnboardingStep.Notification) }
                )
                MainActivity.OnboardingStep.Notification -> NotificationPermissionScreen(
                    hasNotificationAccess = hasNotificationAccess,
                    onOpenNotificationAccessClick = onOpenNotificationAccessClick,
                    onSkipClick = { onNavigateTo(MainActivity.OnboardingStep.Bluetooth) },
                    onNextClick = { onNavigateTo(MainActivity.OnboardingStep.Bluetooth) }
                )
                MainActivity.OnboardingStep.Bluetooth -> BluetoothPermissionScreen(
                    hasBluetooth = hasBluetooth,
                    onGrantBluetoothClick = onGrantBluetoothClick,
                    onSkipClick = { onNavigateTo(MainActivity.OnboardingStep.Complete) },
                    onNextClick = { onNavigateTo(MainActivity.OnboardingStep.Complete) }
                )
                MainActivity.OnboardingStep.Complete -> OnboardingCompleteScreen(onGoToAppClick = {onOnboardingComplete()})
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
    onGrantAllPermissionsClick: () -> Unit // New parameter
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
            Text(stringResource(R.string.grant_all_permissions_button)) // This string needs to be added
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
fun NotificationPermissionScreen(
    hasNotificationAccess: Boolean,
    onOpenNotificationAccessClick: () -> Unit,
    onSkipClick: () -> Unit,
    onNextClick: () -> Unit
) {
    val context = LocalContext.current
    val intent = remember { Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }

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
            stringResource(R.string.notification_access_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.notification_access_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StatusCard(
            title = stringResource(R.string.notification_card_title),
            description = stringResource(R.string.notification_card_description),
            granted = hasNotificationAccess
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                onOpenNotificationAccessClick()
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !hasNotificationAccess
        ) {
            Text(stringResource(R.string.notification_grant_button))
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
                enabled = hasNotificationAccess
            ) {
                Text(stringResource(R.string.shizuku_next_button))
            }
        }
    }
}

@Composable
fun BluetoothPermissionScreen(
    hasBluetooth: Boolean,
    onGrantBluetoothClick: () -> Unit,
    onSkipClick: () -> Unit,
    onNextClick: () -> Unit
) {
    val bluetoothPermissionLauncher: ManagedActivityResultLauncher<String, Boolean> =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (isGranted) {
                    onGrantBluetoothClick()
                } else {
                    // Handle permission denied
                }
            }
        )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Filled.Bluetooth, // Example icon
            contentDescription = null, // Decorative icon
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            stringResource(R.string.bluetooth_permission_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.bluetooth_permission_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StatusCard(
            title = stringResource(R.string.bluetooth_card_title),
            description = stringResource(R.string.bluetooth_card_description),
            granted = hasBluetooth
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !hasBluetooth
        ) {
            Text(stringResource(R.string.bluetooth_grant_button))
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
                enabled = hasBluetooth
            ) {
                Text(stringResource(R.string.bluetooth_finish_button))
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
            style = MaterialTheme. typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}