package io.github.legandy.volumemanager.settings.ui

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.legandy.volumemanager.R
import io.github.legandy.volumemanager.core.Manager
import io.github.legandy.volumemanager.settings.AppFilterMode
import io.github.legandy.volumemanager.settings.InstalledAppData
import io.github.legandy.volumemanager.settings.SettingsDataStore
import io.github.legandy.volumemanager.settings.SettingsViewModel
import io.github.legandy.volumemanager.settings.ui.AppSettingsActivity
import kotlinx.coroutines.launch

@Composable
fun SettingSwitch(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    leading: (@Composable (() -> Unit))? = null,
    showDivider: Boolean = true
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading?.let {
                Box(modifier = Modifier.padding(end = 12.dp)) { it() }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
        if (showDivider) {
            HorizontalDivider()
        }
    }
}

@Composable
fun SettingClickableItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
    leading: (@Composable (() -> Unit))? = null,
    showDivider: Boolean = true
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onClick() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading?.let {
                Box(modifier = Modifier.padding(end = 12.dp)) { it() }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (showDivider) {
            HorizontalDivider()
        }
    }
}

@Composable
fun SettingSlider(
    modifier: Modifier = Modifier,
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    valueLabel: @Composable (Float) -> Unit = { Text("${(it * 100).toInt()}%") },
    subtitle: String? = null,
    enabled: Boolean = true
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.width(12.dp))
            valueLabel(value)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsDataStore: SettingsDataStore,
    manager: Manager
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val viewModel: SettingsViewModel = viewModel()

    val tabs = listOf(
        SettingsTabItem(
            "Volume Control",
            Icons.AutoMirrored.Outlined.VolumeUp,
            Icons.AutoMirrored.Filled.VolumeUp
        ),
        SettingsTabItem("Overlay", Icons.Outlined.Visibility, Icons.Filled.Visibility),
        SettingsTabItem("Apps", Icons.Outlined.Apps, Icons.Filled.Apps)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    val statusColor = when {
                        manager.shizukuReady && manager.shizukuPermission -> MaterialTheme.colorScheme.primary
                        manager.shizukuReady -> Color(0xFFFFA500) // Orange for warning
                        else -> MaterialTheme.colorScheme.error
                    }
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Service Status",
                        tint = statusColor,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                },
                actions = {
                    AppSettingsButton()
                }
            )
        },
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = selectedTabIndex == index
                    Tab(selected = isSelected, onClick = { selectedTabIndex = index }) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val contentColor =
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.icon,
                                contentDescription = tab.title,
                                tint = contentColor
                            )
                            Text(text = tab.title, color = contentColor)
                        }
                    }
                }
            }
            AnimatedContent(targetState = selectedTabIndex, label = "tab-content") { targetIndex ->
                when (targetIndex) {
                    0 -> VolumeControlTab(manager = manager)
                    1 -> OverlaySettingsTab(settingsDataStore = settingsDataStore)
                    2 -> AppFilteringTab(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun AppSettingsButton() {
    val context = LocalContext.current
    IconButton(onClick = {
        context.startActivity(Intent(context, AppSettingsActivity::class.java))
    }) {
        Icon(Icons.Default.Settings, contentDescription = "App Settings")
    }
}

@Composable
fun VolumeControlTab(manager: Manager) {
    val activeApps =
        manager.apps.values.filter { it.players.isNotEmpty() }.sortedBy { it.label.lowercase() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (activeApps.isNotEmpty()) {
            items(activeApps, key = { it.packageName }) { app ->
                AppVolumeCardInSettings(app, manager)
            }
        } else {
            item {
                EmptyState(
                    Icons.AutoMirrored.Outlined.VolumeOff,
                    stringResource(R.string.no_active_audio_title),
                    stringResource(R.string.no_active_audio_description)
                )
            }
        }
    }
}


@Composable
private fun AppVolumeCardInSettings(app: Manager.AppState, manager: Manager) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(
            bitmap = app.icon, // Uses ImageBitmap
            contentDescription = app.label,
            Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Slider(
                value = app.volume,
                onValueChange = { manager.setAppVolume(app.packageName, it) })
        }
    }
}


@Composable
fun OverlaySettingsTab(settingsDataStore: SettingsDataStore) {
    val scope = rememberCoroutineScope()
    val showVolumeKey by settingsDataStore.showOverlayOnVolumeKey.collectAsState(initial = true)
    val showLock by settingsDataStore.showOverlayOnLockscreen.collectAsState(initial = false)
    val closeOnBack by settingsDataStore.closeOverlayOnBack.collectAsState(initial = true)
    val overlayTimeout by settingsDataStore.overlayTimeout.collectAsState(initial = 5000) // Default 5 seconds

    var showTimeoutDialog by remember { mutableStateOf(false) }


    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Card(modifier = Modifier.padding(16.dp)) {
                Column {
                    SettingSwitch(
                        title = "Trigger on Volume Key",
                        subtitle = "Show overlay when volume buttons are pressed",
                        checked = showVolumeKey,
                        onCheckedChange = {
                            scope.launch {
                                settingsDataStore.setShowOverlayOnVolumeKey(it)
                            }
                        })
                    SettingSwitch(
                        title = "Allow on Lock Screen",
                        subtitle = "Permit overlay while device is locked",
                        checked = showLock,
                        enabled = showVolumeKey,
                        onCheckedChange = {
                            scope.launch {
                                settingsDataStore.setShowOverlayOnLockscreen(it)
                            }
                        })
                    SettingSwitch(
                        title = "Close on Back Gesture",
                        subtitle = "Hide the overlay with the back button or gesture",
                        checked = closeOnBack,
                        onCheckedChange = {
                            scope.launch {
                                settingsDataStore.setCloseOverlayOnBack(it)
                            }
                        }
                    )
                    SettingClickableItem(
                        title = "Overlay Timeout",
                        subtitle = when (overlayTimeout.toLong()) {
                            0L -> "Never"
                            else -> "${overlayTimeout.toLong() / 1000L} seconds"
                        },
                        onClick = { showTimeoutDialog = true },
                        showDivider = false
                    )
                }
            }
        }
    }

    if (showTimeoutDialog) {
        TimeoutSelectionSlider(
            currentTimeout = overlayTimeout.toLong(),
            onDismissRequest = { showTimeoutDialog = false },
            onTimeoutSelected = { newTimeout ->
                scope.launch {
                    settingsDataStore.setOverlayTimeout(newTimeout.toInt())
                }
                showTimeoutDialog = false
            }
        )
    }
}

@Composable
private fun TimeoutSelectionSlider(
    currentTimeout: Long,
    onDismissRequest: () -> Unit,
    onTimeoutSelected: (Long) -> Unit
) {
    val timeoutOptions: List<Pair<Long, String>> = remember {
        listOf(
            0L to "Never",
            5000L to "5 seconds",
            10000L to "10 seconds",
            30000L to "30 seconds"
        )
    }

    // Determine initial slider position based on currentTimeout
    var sliderPosition by remember {
        val initialIndex = timeoutOptions.indexOfFirst { it.first == currentTimeout }.toFloat()
        mutableFloatStateOf(initialIndex.coerceAtLeast(0f)) // Ensure it's not -1 if not found
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Overlay Timeout") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Timeout", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = timeoutOptions[sliderPosition.toInt()].second,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = sliderPosition,
                    onValueChange = { newPosition -> sliderPosition = newPosition },
                    valueRange = 0f..(timeoutOptions.size - 1).toFloat(),
                    steps = timeoutOptions.size - 2,
                    onValueChangeFinished = {
                        val selectedTimeout = timeoutOptions[sliderPosition.toInt()].first
                        onTimeoutSelected(selectedTimeout)
                    }
                )
                Text(
                    text = "Slide to select how long the overlay remains visible.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        }
    )
}

@Composable
fun AppFilteringTab(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val appFilterMode by viewModel.appFilterMode.collectAsState(initial = AppFilterMode.SHOW_ALL)
    val appBlacklist by viewModel.appBlacklist.collectAsState(initial = emptySet())
    val appWhitelist by viewModel.appWhitelist.collectAsState(initial = emptySet())

    val filteredApps = remember(uiState.searchQuery, uiState.allApps) {
        if (uiState.searchQuery.isBlank()) {
            uiState.allApps
        } else {
            uiState.allApps.filter {
                it.name.contains(
                    uiState.searchQuery,
                    true
                ) || it.packageName.contains(uiState.searchQuery, true)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card {
                Column(Modifier.padding(16.dp).selectableGroup()) {
                    Text("Filter Mode", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    FilterModeOption(
                        "Show All Apps",
                        "All apps with audio will appear",
                        appFilterMode == AppFilterMode.SHOW_ALL
                    ) { viewModel.setAppFilterMode(AppFilterMode.SHOW_ALL) }
                    FilterModeOption(
                        "Blacklist",
                        "Hide selected apps from the overlay",
                        appFilterMode == AppFilterMode.BLACKLIST
                    ) { viewModel.setAppFilterMode(AppFilterMode.BLACKLIST) }
                    FilterModeOption(
                        "Whitelist",
                        "Only show selected apps in the overlay",
                        appFilterMode == AppFilterMode.WHITELIST
                    ) { viewModel.setAppFilterMode(AppFilterMode.WHITELIST) }
                }
            }
        }

        if (appFilterMode != AppFilterMode.SHOW_ALL) {
            item {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) IconButton(onClick = {
                            viewModel.onSearchQueryChanged(
                                ""
                            )
                        }) { Icon(Icons.Default.Clear, null) }
                    },
                    singleLine = true
                )
            }

            if (uiState.isLoading) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
            } else {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isSelected =
                        if (appFilterMode == AppFilterMode.BLACKLIST) app.packageName in appBlacklist else app.packageName in appWhitelist
                    AppFilterItem(
                        app = app,
                        isSelected = isSelected,
                        onSelectionChanged = { checked ->
                            when (appFilterMode) {
                                AppFilterMode.BLACKLIST -> viewModel.updateBlacklist(
                                    app.packageName,
                                    checked
                                )

                                AppFilterMode.WHITELIST -> viewModel.updateWhitelist(
                                    app.packageName,
                                    checked
                                )

                                else -> {}
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterModeOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AppFilterItem(
    app: InstalledAppData,
    isSelected: Boolean,
    onSelectionChanged: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            bitmap = app.icon, // Uses ImageBitmap
            contentDescription = app.name,
            Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(app.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Checkbox(checked = isSelected, onCheckedChange = onSelectionChanged)
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, description: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                icon,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}