package io.github.legandy.volumemixerpanel.settings

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.legandy.volumemixerpanel.app.AppSettingsActivity
import io.github.legandy.volumemixerpanel.core.ShizukuManager
import kotlinx.coroutines.launch
import io.github.legandy.volumemixerpanel.R
import io.github.legandy.volumemixerpanel.core.VolumeManager

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    settingsDataStore: SettingsDataStore,
    volumeManager: VolumeManager,
    shizukuManager: ShizukuManager
) {
    val viewModel: SettingsViewModel = viewModel()
    val scope = rememberCoroutineScope() // Added rememberCoroutineScope

    val tabs = listOf(
        SettingsTabItem(
            "Volume Control",
            Icons.AutoMirrored.Outlined.VolumeUp,
            Icons.AutoMirrored.Filled.VolumeUp
        ),
        SettingsTabItem("Apps", Icons.Outlined.Apps, Icons.Filled.Apps),
        SettingsTabItem("Overlay", Icons.Outlined.Visibility, Icons.Filled.Visibility)
    )

    val pagerState = rememberPagerState(pageCount = { tabs.size })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    val statusColor = when {
                        shizukuManager.shizukuReady && shizukuManager.shizukuPermission -> MaterialTheme.colorScheme.primary
                        shizukuManager.shizukuReady -> Color(0xFFFFA500) // Orange for warning
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
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = pagerState.currentPage == index
                    Tab(
                        selected = isSelected,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        }
                    ) {
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
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Fill remaining space
            ) { page ->
                when (page) {
                    0 -> VolumeControlTab(volumeManager = volumeManager, settingsDataStore = settingsDataStore)
                    1 -> AppFilteringTab(viewModel = viewModel)
                    2 -> OverlaySettingsTab(settingsDataStore = settingsDataStore)
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
fun VolumeControlTab(volumeManager: VolumeManager, settingsDataStore: SettingsDataStore) {
    val activeApps =
        volumeManager.apps.values.filter { it.players.isNotEmpty() }.sortedBy { it.label.lowercase() }
    val lastAppVolumes by settingsDataStore.lastAppVolumes.collectAsState(initial = emptyMap())


    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (activeApps.isNotEmpty()) {
            items(activeApps, key = { it.packageName }) { app ->
                AppVolumeCardInSettings(app, volumeManager, lastAppVolumes, settingsDataStore)
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
private fun AppVolumeCardInSettings(
    app: VolumeManager.AppState,
    volumeManager: VolumeManager,
    lastAppVolumes: Map<String, Float>,
    settingsDataStore: SettingsDataStore
) {
    val scope = rememberCoroutineScope()
    var lastVolume by remember(app.packageName) {
        mutableFloatStateOf(lastAppVolumes[app.packageName] ?: (if (app.volume > 0.05f) app.volume else 0.7f))
    }
    val isMuted = app.volume < 0.01f

    val iconModifier = if (isMuted) Modifier.alpha(0.5f) else Modifier
    val colorFilter = if (isMuted) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null

    Row(
        modifier = Modifier.fillMaxWidth().height(72.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        IconButton(
            onClick = {
                scope.launch {
                    if (isMuted) {
                        volumeManager.setAppVolume(app.packageName, lastVolume)
                        settingsDataStore.setLastAppVolume(app.packageName, lastVolume)
                    } else {
                        settingsDataStore.setLastAppVolume(app.packageName, app.volume)
                        volumeManager.setAppVolume(app.packageName, 0f)
                    }
                }
            }
        ) {
            Image(
                bitmap = app.icon, // Uses ImageBitmap
                contentDescription = app.label,
                modifier = iconModifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                colorFilter = colorFilter
            )
        }

        Column(Modifier.weight(1f)) {
            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Slider(
                value = app.volume,
                onValueChange = { newVol ->
                    volumeManager.setAppVolume(app.packageName, newVol)
                    if (newVol > 0f) {
                        lastVolume = newVol
                        scope.launch { settingsDataStore.setLastAppVolume(app.packageName, newVol) }
                    }
                },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text(
            text = "${(app.volume * 100).toInt()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
            textAlign = TextAlign.End
        )
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
                        enabled = true,
                        //enabled = overlayTimeout != 0, // Only enabled if timeout is not disabled
                        onCheckedChange = {
                            scope.launch {
                                settingsDataStore.setCloseOverlayOnBack(it)
                            }
                        }
                    )
                    SettingClickableItem(
                        title = "Overlay Timeout",
                        subtitle = when (overlayTimeout.toLong()) {
                            0L -> "Disabled"
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
    var sliderPosition by remember { mutableFloatStateOf(currentTimeout.toFloat()) }

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
                    val timeoutText = if (sliderPosition.toLong() == 0L) {
                        "Disabled"
                    } else {
                        "${sliderPosition.toLong() / 1000L} seconds"
                    }
                    Text(
                        text = timeoutText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = sliderPosition,
                    onValueChange = { newPosition -> sliderPosition = newPosition },
                    valueRange = 0f..30000f,
                    steps = 29,
                    onValueChangeFinished = {

                        onTimeoutSelected(sliderPosition.toLong())
                    }
                )
                Text(
                    text = "Slide to select how long the overlay remains visible. Set to Disabled for indefinite display.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            IconButton(onClick = {
                onTimeoutSelected(sliderPosition.toLong())
                onDismissRequest()
            }) {
                Icon(Icons.Default.Check, contentDescription = "Confirm Timeout")
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
