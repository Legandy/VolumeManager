@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.legandy.volumemanager.app

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.* 
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.legandy.volumemanager.ui.theme.VolumeManagerTheme
import io.github.legandy.volumemanager.core.Manager
import io.github.legandy.volumemanager.overlay.OverlayService
import io.github.legandy.volumemanager.settings.ui.SettingsScreen
import io.github.legandy.volumemanager.utils.isAccessibilityServiceEnabled

class MainActivity : ComponentActivity() {

    private val manager: Manager by lazy { MyApplication.manager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action?.let { isShortcutAction(it) } == true) {
            handleShortcutIntent(intent)
            return
        }

        enableEdgeToEdge()
        setContent {
            VolumeManagerTheme {
                MainScreen()
            }
        }
    }

    @Composable
    private fun MainScreen() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        fun checkBluetoothPermission(): Boolean {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        }

        fun checkNotificationAccess(): Boolean {
            return (context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted
        }

        var isAccessibilityEnabled by remember { mutableStateOf(
            isAccessibilityServiceEnabled(
                context
            )
        ) }
        var hasNotificationAccess by remember { mutableStateOf(checkNotificationAccess()) }
        var hasBluetoothPermission by remember { mutableStateOf(checkBluetoothPermission()) }
        var checkCounter by remember { mutableIntStateOf(0) }

        val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                hasBluetoothPermission = true
                checkCounter++
            }
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    isAccessibilityEnabled =
                        isAccessibilityServiceEnabled(
                            context
                        )
                    hasNotificationAccess = checkNotificationAccess()
                    hasBluetoothPermission = checkBluetoothPermission()
                    checkCounter++
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        val hasShizukuPermission = manager.shizukuPermission
        val allPermissionsGranted = hasShizukuPermission && isAccessibilityEnabled && hasNotificationAccess && hasBluetoothPermission

        when {
            manager.shizukuReady && allPermissionsGranted -> {
                SettingsScreen(
                    settingsDataStore = MyApplication.settings,
                    manager = manager
                )
            }
            manager.shizukuReady -> {
                PermissionScreen(
                    hasShizuku = hasShizukuPermission,
                    hasAccessibility = isAccessibilityEnabled,
                    hasNotificationAccess = hasNotificationAccess,
                    hasBluetooth = hasBluetoothPermission,
                    onGrantShizukuClick = { manager.requestShizukuPermission(this@MainActivity) },
                    onOpenAccessibilityClick = { openAccessibilitySettings() },
                    onOpenNotificationAccessClick = { openNotificationAccessSettings() },
                    onGrantBluetoothClick = {
                        bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                )
            }
            else -> WaitingForShizukuScreen()
        }
    }

    @Composable
    private fun PermissionScreen(
        hasShizuku: Boolean,
        hasAccessibility: Boolean,
        hasNotificationAccess: Boolean,
        hasBluetooth: Boolean,
        onGrantShizukuClick: () -> Unit,
        onOpenAccessibilityClick: () -> Unit,
        onOpenNotificationAccessClick: () -> Unit,
        onGrantBluetoothClick: () -> Unit
    ) {
        Scaffold(topBar = { TopAppBar(title = { Text("VolumeManager Setup") }) }) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Filled.Security,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Permissions Required",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                StatusCard(
                    title = "Shizuku Permission",
                    description = "For per-app audio control.",
                    granted = hasShizuku
                )
                StatusCard(
                    title = "Accessibility Service",
                    description = "To show the volume overlay.",
                    granted = hasAccessibility
                )
                StatusCard(
                    title = "Notification Access",
                    description = "To change Ringer Mode & DND.",
                    granted = hasNotificationAccess
                )

                StatusCard(
                    title = "Bluetooth Permission",
                    description = "To detect Bluetooth devices.",
                    granted = hasBluetooth
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (!hasShizuku) {
                    Button(onClick = onGrantShizukuClick, modifier = Modifier.fillMaxWidth()) {
                        Text("1. Grant Shizuku Permission")
                    }
                }
                if (!hasAccessibility) {
                    Button(onClick = onOpenAccessibilityClick, modifier = Modifier.fillMaxWidth()) {
                        Text("2. Enable Accessibility Service")
                    }
                }
                if (!hasNotificationAccess) {
                    Button(onClick = onOpenNotificationAccessClick, modifier = Modifier.fillMaxWidth()) {
                        Text("3. Grant Notification Access")
                    }
                }
                if (!hasBluetooth) {
                    Button(onClick = onGrantBluetoothClick, modifier = Modifier.fillMaxWidth()) {
                        Text("4. Grant Bluetooth Permission")
                    }
                }
            }
        }
    }

    @Composable
    private fun StatusCard(title: String, description: String, granted: Boolean) {
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
                    contentDescription = null,
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

    @Composable
    private fun WaitingForShizukuScreen() { /* ... */ }
}
