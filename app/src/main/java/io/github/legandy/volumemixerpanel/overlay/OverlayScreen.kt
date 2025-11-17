package io.github.legandy.volumemixerpanel.overlay

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpeakerGroup
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.legandy.volumemixerpanel.main.MainActivity
import io.github.legandy.volumemixerpanel.core.ShizukuManager
import io.github.legandy.volumemixerpanel.settings.AppFilterMode
import io.github.legandy.volumemixerpanel.settings.SettingsDataStore
import io.github.legandy.volumemixerpanel.ui.theme.VolumeMixerPanelTheme
import kotlinx.coroutines.launch
import io.github.legandy.volumemixerpanel.R
import androidx.compose.ui.res.stringResource

private enum class OverlayTab { SYSTEM, APPS }

@Composable
fun OverlayScreen(
    overlayViewModel: OverlayViewModel,
    isOverlayVisible: Boolean,
    onOverlayHidden: () -> Unit,
    hideView: () -> Unit,
    resetTimer: () -> Unit,
    pauseTimer: () -> Unit,
    resumeTimer: () -> Unit,
    manager: ShizukuManager,
    settingsDataStore: SettingsDataStore
) {
    VolumeMixerPanelTheme {
        AnimatedVisibility(
            visible = isOverlayVisible,
            enter = slideInVertically(animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium), initialOffsetY = { -it / 2 }) + fadeIn(animationSpec = spring()),
            exit = slideOutVertically(animationSpec = spring(stiffness = Spring.StiffnessMedium), targetOffsetY = { -it / 2 }) + fadeOut(animationSpec = spring())
        ) {
            DisposableEffect(Unit) { onDispose { if (!isOverlayVisible) onOverlayHidden() } }
            OverlayContent(
                overlayViewModel = overlayViewModel,
                hideView = hideView,
                resetTimer = resetTimer,
                pauseTimer = pauseTimer,
                resumeTimer = resumeTimer,
                manager = manager,
                settingsDataStore = settingsDataStore
            )
        }
    }
}

@Composable
private fun OverlayContent(
    overlayViewModel: OverlayViewModel,
    hideView: () -> Unit,
    resetTimer: () -> Unit,
    pauseTimer: () -> Unit,
    resumeTimer: () -> Unit,
    manager: ShizukuManager,
    settingsDataStore: SettingsDataStore
) {
    var selectedTab by remember { mutableStateOf(OverlayTab.SYSTEM) }
    val context = LocalContext.current

    val uiState by overlayViewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 8.dp,
    ) {
        Column(Modifier.padding(top = 16.dp)) {
            Box(
                modifier = Modifier
                    .height(260.dp)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    label = "overlay-tab-content"
                ) { tab ->
                    when (tab) {
                        OverlayTab.SYSTEM -> SystemVolumeSliders(overlayViewModel, pauseTimer, resumeTimer)
                        OverlayTab.APPS -> AppVolumeSliders(manager, settingsDataStore, pauseTimer, resumeTimer)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Group
                Row {
                    IconButton(onClick = {
                        val intent = Intent(context, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }
                        context.startActivity(intent)
                        hideView()
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = {
                        try {
                            val intent = Intent("android.media.action.SHOW_AUDIO_OUTPUT_SWITCHER")
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            hideView()
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, R.string.output_switcher_not_available, Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.SpeakerGroup, contentDescription = "Media Output")
                    }
                }

                // Center Group
                Row {
                    val isSystemSelected = selectedTab == OverlayTab.SYSTEM
                    if (isSystemSelected) {
                        FilledTonalIconButton(onClick = { /* Already selected */ }) {
                            Icon(Icons.Filled.Podcasts, "System Tab", modifier = Modifier.size(32.dp))
                        }
                    } else {
                        IconButton(onClick = { selectedTab = OverlayTab.SYSTEM; resetTimer() }) {
                            Icon(Icons.Outlined.Podcasts, "System Tab", modifier = Modifier.size(32.dp))
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    val isAppsSelected = selectedTab == OverlayTab.APPS
                    if (isAppsSelected) {
                        FilledTonalIconButton(onClick = { /* Already selected */ }) {
                            Icon(Icons.Filled.Apps, "App Tab", modifier = Modifier.size(32.dp))
                        }
                    } else {
                        IconButton(onClick = { selectedTab = OverlayTab.APPS; resetTimer() }) {
                            Icon(Icons.Outlined.Apps, "App Tab", modifier = Modifier.size(32.dp))
                        }
                    }
                }

                // Right Group
                Row {
                    IconButton(onClick = {
                        overlayViewModel.setDnd(!uiState.isDndOn)
                        resetTimer()
                    }) {
                        Icon(
                            imageVector = if (uiState.isDndOn) Icons.Filled.DoNotDisturbOn else Icons.Outlined.DoNotDisturbOn,
                            contentDescription = "Do not disturb mode",
                            tint = if(uiState.isDndOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { hideView() }) {
                        Icon(Icons.Default.Check, contentDescription = "Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemVolumeSliders(overlayViewModel: OverlayViewModel, pauseTimer: () -> Unit, resumeTimer: () -> Unit) {
    val uiState by overlayViewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StreamSliderRow(AudioManager.STREAM_MUSIC, uiState, overlayViewModel, pauseTimer, resumeTimer)
        StreamSliderRow(AudioManager.STREAM_RING, uiState, overlayViewModel, pauseTimer, resumeTimer)
        StreamSliderRow(AudioManager.STREAM_ALARM, uiState, overlayViewModel, pauseTimer, resumeTimer)
        StreamSliderRow(AudioManager.STREAM_VOICE_CALL, uiState, overlayViewModel, pauseTimer, resumeTimer)
    }
}

@Composable
private fun AppVolumeSliders(manager: ShizukuManager, settingsDataStore: SettingsDataStore, pauseTimer: () -> Unit, resumeTimer: () -> Unit) {
    val filterMode by settingsDataStore.appFilterMode.collectAsState(initial = AppFilterMode.SHOW_ALL)
    val blacklist by settingsDataStore.appBlacklist.collectAsState(initial = emptySet())
    val whitelist by settingsDataStore.appWhitelist.collectAsState(initial = emptySet())
    val lastAppVolumes by settingsDataStore.lastAppVolumes.collectAsState(initial = emptyMap())


    val activeApps = manager.apps.values
        .filter { it.players.isNotEmpty() }
        .filter { app ->
            when (filterMode) {
                AppFilterMode.WHITELIST -> app.packageName in whitelist
                AppFilterMode.BLACKLIST -> app.packageName !in blacklist
                else -> true
            }
        }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (activeApps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.No_apps_are_currently_playing_audio),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                activeApps.forEach { app -> AppSliderRow(app = app, manager = manager, settingsDataStore = settingsDataStore, lastAppVolumes = lastAppVolumes, pauseTimer = pauseTimer, resumeTimer = resumeTimer) }
            }
        }
    }
}

@Composable
private fun StreamSliderRow(
    streamType: Int,
    uiState: SystemAudioUiState,
    overlayViewModel: OverlayViewModel,
    pauseTimer: () -> Unit,
    resumeTimer: () -> Unit
) {
    val maxVolume = uiState.maxVolumes[streamType] ?: 15
    var currentVolume by remember(uiState.volumes[streamType]) { mutableIntStateOf(uiState.volumes[streamType] ?: 0) }
    val interactionSource = remember { MutableInteractionSource() }
    val isMuted = uiState.mutedStreams.contains(streamType)
    val ringerMode = uiState.ringerMode

    val initialLastVolume = remember(streamType) {
        if (currentVolume > 0) currentVolume else (maxVolume * 0.7).toInt().coerceAtLeast(1)
    }
    var lastKnownVolume by remember(streamType) { mutableIntStateOf(initialLastVolume) }

    LaunchedEffect(currentVolume) {
        if (currentVolume > 0) {
            lastKnownVolume = currentVolume
        }
    }

    val displayVolume = if (isMuted && streamType != AudioManager.STREAM_RING) lastKnownVolume else currentVolume

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> pauseTimer()
                is DragInteraction.Stop -> resumeTimer()
                is DragInteraction.Cancel -> resumeTimer()
            }
        }
    }

    var sliderEnabled: Boolean
    val icon: ImageVector
    val onIconClick: (() -> Unit)?
    val name: String

    when (streamType) {
        AudioManager.STREAM_RING -> {
            name = "Ring"
            when (ringerMode) {
                AudioManager.RINGER_MODE_VIBRATE -> {
                    icon = Icons.Default.Vibration
                    sliderEnabled = false
                    onIconClick = {
                        overlayViewModel.setRingerMode(AudioManager.RINGER_MODE_SILENT)
                    }
                }
                AudioManager.RINGER_MODE_SILENT -> {
                    icon = Icons.Default.NotificationsOff
                    sliderEnabled = false
                    onIconClick = {
                        overlayViewModel.setRingerMode(AudioManager.RINGER_MODE_NORMAL)
                    }
                }
                else -> { // RINGER_MODE_NORMAL
                    icon = Icons.Default.RingVolume
                    sliderEnabled = true
                    onIconClick = {
                        overlayViewModel.setRingerMode(AudioManager.RINGER_MODE_VIBRATE)
                    }
                }
            }
        }
        AudioManager.STREAM_MUSIC -> {
            name = "Media"
            val baseIcon = when (uiState.deviceType) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> Icons.Default.BluetoothAudio
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> Icons.Default.Headphones
                else -> Icons.AutoMirrored.Filled.VolumeUp
            }
            icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else baseIcon
            sliderEnabled = true
            onIconClick = {
                val direction = if (isMuted) AudioManager.ADJUST_UNMUTE else AudioManager.ADJUST_MUTE
                overlayViewModel.adjustStreamVolume(streamType, direction)
            }
        }
        AudioManager.STREAM_ALARM -> {
            name = "Alarm"
            icon = Icons.Default.Alarm
            sliderEnabled = true
            onIconClick = null
        }
        else -> { // Handles STREAM_VOICE_CALL
            name = "Call"
            sliderEnabled = !isMuted
            icon = if (isMuted) Icons.Default.PhoneDisabled else Icons.Default.Phone
            onIconClick = {
                val direction = if (isMuted) AudioManager.ADJUST_UNMUTE else AudioManager.ADJUST_MUTE
                overlayViewModel.adjustStreamVolume(streamType, direction)
            }
        }
    }

    val sliderColors = if (!sliderEnabled) {
        SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            activeTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
    } else if (streamType == AudioManager.STREAM_MUSIC && isMuted) {
        SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
    } else {
        SliderDefaults.colors()
    }

    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (onIconClick != null) {
            IconButton(onClick = onIconClick, modifier = Modifier.padding(start = 16.dp)) {
                Icon(imageVector = icon, contentDescription = name)
            }
        } else {
            IconButton(onClick = {}, enabled = false, modifier = Modifier.padding(start = 16.dp)) {
                Icon(
                    imageVector = icon,
                    contentDescription = name,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Slider(
            value = displayVolume.toFloat(),
            onValueChange = { newVol -> currentVolume = newVol.toInt() },
            onValueChangeFinished = {
                try {
                    if (streamType == AudioManager.STREAM_RING) {
                        if (currentVolume == 0) {
                            overlayViewModel.setRingerMode(AudioManager.RINGER_MODE_SILENT)
                        } else {
                            overlayViewModel.setStreamVolume(streamType, currentVolume)
                            if (uiState.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                                overlayViewModel.setRingerMode(AudioManager.RINGER_MODE_NORMAL)
                            }
                        }
                    } else {
                        if (sliderEnabled) {
                            overlayViewModel.setStreamVolume(streamType, currentVolume)
                        }
                    }
                } catch (e: Exception) { Log.e("VolumeManager.Service", "onValueChangeFinished failed", e) }
                resumeTimer()
            },
            interactionSource = interactionSource,
            valueRange = 0f..maxVolume.toFloat(),
            modifier = Modifier.weight(1f),
            enabled = sliderEnabled,
            colors = sliderColors
        )
        Text(
            text = "$displayVolume",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp).width(24.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun AppSliderRow(
    app: ShizukuManager.AppState,
    manager: ShizukuManager,
    settingsDataStore: SettingsDataStore,
    lastAppVolumes: Map<String, Float>,
    pauseTimer: () -> Unit,
    resumeTimer: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    var lastVolume by remember(app.packageName) {
        mutableFloatStateOf(lastAppVolumes[app.packageName] ?: (if (app.volume > 0.05f) app.volume else 0.7f))
    }
    val isMuted = app.volume < 0.01f

    LaunchedEffect(app.volume) {
        if (app.volume > 0.05f) {
            lastVolume = app.volume
            scope.launch { settingsDataStore.setLastAppVolume(app.packageName, app.volume) }
        }
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> pauseTimer()
                is DragInteraction.Stop -> resumeTimer()
                is DragInteraction.Cancel -> resumeTimer()
            }
        }
    }

    val iconModifier = if (isMuted) Modifier.alpha(0.5f) else Modifier
    val colorFilter = if (isMuted) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null

    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        IconButton(
            onClick = {
                scope.launch {
                    if (isMuted) {
                        manager.setAppVolume(app.packageName, lastVolume)
                        settingsDataStore.setLastAppVolume(app.packageName, lastVolume)
                    } else {
                        settingsDataStore.setLastAppVolume(app.packageName, app.volume)
                        manager.setAppVolume(app.packageName, 0f)
                    }
                }
                resumeTimer()
            },
            modifier = Modifier.padding(start = 16.dp)
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = iconModifier.size(24.dp).clip(RoundedCornerShape(4.dp)),
                colorFilter = colorFilter
            )
        }

        Slider(
            value = app.volume,
            onValueChange = { newVol ->
                manager.setAppVolume(app.packageName, newVol)
                if (newVol > 0f) {
                    lastVolume = newVol
                    scope.launch { settingsDataStore.setLastAppVolume(app.packageName, newVol) }
                }
            },
            onValueChangeFinished = { resumeTimer() },
            interactionSource = interactionSource,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = "${(app.volume * 100).toInt()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp).width(32.dp),
            textAlign = TextAlign.End
        )
    }
}