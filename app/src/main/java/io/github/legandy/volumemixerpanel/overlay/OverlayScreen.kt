package io.github.legandy.volumemixerpanel.overlay

import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.legandy.volumemixerpanel.R
import io.github.legandy.volumemixerpanel.core.VolumeManager
import io.github.legandy.volumemixerpanel.main.MainActivity
import io.github.legandy.volumemixerpanel.settings.AppFilterMode
import io.github.legandy.volumemixerpanel.settings.SettingsDataStore
import io.github.legandy.volumemixerpanel.settings.ThemeMode
import io.github.legandy.volumemixerpanel.ui.theme.VolumeMixerPanelTheme
import kotlinx.coroutines.launch

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
    volumeManager: VolumeManager,
    settingsDataStore: SettingsDataStore,
    bottomPadding: Dp
) {
    val themeMode by settingsDataStore.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

    VolumeMixerPanelTheme(themeMode = themeMode) {
        AnimatedVisibility(
            visible = isOverlayVisible,
            enter = slideInVertically(animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium), initialOffsetY = { -it / 2 }) + fadeIn(animationSpec = spring()),
            exit = slideOutVertically(animationSpec = spring(stiffness = Spring.StiffnessMedium), targetOffsetY = { -it / 2 }) + fadeOut(animationSpec = spring())
        ) {
            DisposableEffect(Unit) {
                onDispose {
                    if (!isOverlayVisible) onOverlayHidden()
                }
            }

            OverlayContent(
                overlayViewModel = overlayViewModel,
                hideView = hideView,
                resetTimer = resetTimer,
                pauseTimer = pauseTimer,
                resumeTimer = resumeTimer,
                volumeManager = volumeManager,
                settingsDataStore = settingsDataStore,
                bottomPadding = bottomPadding
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
    volumeManager: VolumeManager,
    settingsDataStore: SettingsDataStore,
    bottomPadding: Dp
) {
    var selectedTab by remember { mutableStateOf(OverlayTab.SYSTEM) }
    val context = LocalContext.current
    val uiState by overlayViewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
        tonalElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .padding(top = 16.dp)
                .padding(bottom = bottomPadding)
        ) {
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
                        OverlayTab.SYSTEM -> SystemVolumeSliders(overlayViewModel, uiState, pauseTimer, resumeTimer, resetTimer, settingsDataStore)
                        OverlayTab.APPS -> AppVolumeSliders(volumeManager, settingsDataStore, pauseTimer, resumeTimer, resetTimer)
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
                Row {
                    IconButton(onClick = {
                        val intent = Intent(context, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }
                        context.startActivity(intent)
                        hideView()
                        resetTimer()
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = {
                        try {
                            val intent = Intent("android.settings.panel.action.MEDIA_OUTPUT")
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            resetTimer()
                        } catch (e: Exception) {
                            try {
                                val btIntent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                                btIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(btIntent)
                                hideView()
                                resetTimer()
                            } catch (e2: Exception) {
                                Toast.makeText(context, R.string.output_switcher_not_available, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(Icons.Default.SpeakerGroup, contentDescription = "Media Output")
                    }
                }

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

                Row {
                    IconButton(onClick = {
                        overlayViewModel.setDndShizuku(!uiState.isDndOn)
                        resetTimer()
                    }) {
                        Icon(
                            imageVector = if (uiState.isDndOn) Icons.Filled.DoNotDisturbOn else Icons.Outlined.DoNotDisturbOn,
                            contentDescription = "Do not disturb mode",
                            tint = if(uiState.isDndOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { hideView(); resetTimer() }) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Hide Panel")
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemVolumeSliders(
    overlayViewModel: OverlayViewModel,
    uiState: SystemAudioUiState,
    pauseTimer: () -> Unit,
    resumeTimer: () -> Unit,
    resetTimer: () -> Unit,
    settingsDataStore: SettingsDataStore
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StreamSliderRow(AudioManager.STREAM_MUSIC, uiState, overlayViewModel, pauseTimer, resumeTimer, resetTimer, settingsDataStore)
        StreamSliderRow(AudioManager.STREAM_RING, uiState, overlayViewModel, pauseTimer, resumeTimer, resetTimer, settingsDataStore)
        StreamSliderRow(AudioManager.STREAM_ALARM, uiState, overlayViewModel, pauseTimer, resumeTimer, resetTimer, settingsDataStore)
        StreamSliderRow(AudioManager.STREAM_VOICE_CALL, uiState, overlayViewModel, pauseTimer, resumeTimer, resetTimer, settingsDataStore)
    }
}

@Composable
private fun AppVolumeSliders(volumeManager: VolumeManager, settingsDataStore: SettingsDataStore, pauseTimer: () -> Unit, resumeTimer: () -> Unit, resetTimer: () -> Unit) {
    val filterMode by settingsDataStore.appFilterMode.collectAsState(initial = AppFilterMode.SHOW_ALL)
    val blacklist by settingsDataStore.appBlacklist.collectAsState(initial = emptySet())
    val whitelist by settingsDataStore.appWhitelist.collectAsState(initial = emptySet())
    val lastAppVolumes by settingsDataStore.lastAppVolumes.collectAsState(initial = emptyMap())

    val activeApps = volumeManager.apps.values
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
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                activeApps.forEach { app -> 
                    AppSliderRow(app = app, volumeManager = volumeManager, settingsDataStore = settingsDataStore, lastAppVolumes = lastAppVolumes, pauseTimer = pauseTimer, resumeTimer = resumeTimer, resetTimer = resetTimer) 
                }
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
    resumeTimer: () -> Unit,
    resetTimer: () -> Unit,
    settingsDataStore: SettingsDataStore
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }
    val scope = rememberCoroutineScope()

    val maxVolume = uiState.maxVolumes[streamType] ?: 15
    val actualVolume = uiState.volumes[streamType] ?: 0
    val isSystemMuted = uiState.mutedStreams.contains(streamType)

    val minVolume = remember(streamType) {
        try { audioManager.getStreamMinVolume(streamType) } catch (_: Exception) { 0 }
    }

    val ringerMode = uiState.ringerMode
    val lastSavedVolume by remember(streamType) {
        settingsDataStore.getLastSystemVolumeFlow(streamType)
    }.collectAsState(initial = null)

    val defaultUnmuteVolume = (maxVolume * 0.7f).toInt().coerceAtLeast(minVolume + 1)
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> pauseTimer()
                is DragInteraction.Stop -> resumeTimer()
                is DragInteraction.Cancel -> resumeTimer()
            }
        }
    }

    val sliderEnabled: Boolean
    val isVisuallyMutedForStream: Boolean
    val icon: ImageVector
    val onIconClick: () -> Unit
    val name: String

    val isMediaMuted = streamType == AudioManager.STREAM_MUSIC && isSystemMuted

    val displayValue = when {
        streamType == AudioManager.STREAM_MUSIC -> actualVolume.toFloat()
        actualVolume <= minVolume -> 0f
        else -> actualVolume.toFloat()
    }

    when (streamType) {
        AudioManager.STREAM_RING -> {
            name = "Ring"
            sliderEnabled = !uiState.isDndOn && ringerMode == AudioManager.RINGER_MODE_NORMAL
            isVisuallyMutedForStream = !sliderEnabled
            icon = when (ringerMode) {
                AudioManager.RINGER_MODE_VIBRATE -> Icons.Default.Vibration
                AudioManager.RINGER_MODE_SILENT -> Icons.Default.NotificationsOff
                else -> Icons.Default.RingVolume
            }
            onIconClick = {
                val nextMode = when (ringerMode) {
                    AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                    AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                    else -> AudioManager.RINGER_MODE_NORMAL
                }
                overlayViewModel.setRingerMode(nextMode)
                resetTimer()
            }
        }
        AudioManager.STREAM_MUSIC -> {
            name = "Media"
            sliderEnabled = true
            isVisuallyMutedForStream = isMediaMuted
            val baseIcon = when (uiState.deviceType) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> Icons.Default.BluetoothAudio
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> Icons.Default.Headphones
                else -> Icons.AutoMirrored.Filled.VolumeUp
            }
            icon = if (isVisuallyMutedForStream) Icons.AutoMirrored.Filled.VolumeOff else baseIcon
            onIconClick = {
                val direction = if (isMediaMuted) AudioManager.ADJUST_UNMUTE else AudioManager.ADJUST_MUTE
                overlayViewModel.adjustStreamVolume(streamType, direction)
            }
        }
        AudioManager.STREAM_ALARM -> {
            name = "Alarm"
            sliderEnabled = true
            isVisuallyMutedForStream = actualVolume <= minVolume
            icon = if (isVisuallyMutedForStream) Icons.Filled.AlarmOff else Icons.Default.Alarm
            onIconClick = {
                if (actualVolume > minVolume) {
                    overlayViewModel.setStreamVolume(streamType, minVolume)
                } else {
                    val target = lastSavedVolume?.takeIf { it > minVolume } ?: defaultUnmuteVolume
                    overlayViewModel.setStreamVolume(streamType, target)
                }
            }
        }
        else -> {
            name = "Call"
            sliderEnabled = true
            isVisuallyMutedForStream = actualVolume <= minVolume
            icon = if (isVisuallyMutedForStream) Icons.Default.PhoneDisabled else Icons.Default.Phone
            onIconClick = {
                if (actualVolume > minVolume) {
                    overlayViewModel.setStreamVolume(streamType, minVolume)
                } else {
                    val target = lastSavedVolume?.takeIf { it > minVolume } ?: defaultUnmuteVolume
                    overlayViewModel.setStreamVolume(streamType, target)
                }
            }
        }
    }

    val sliderColors = when {
        !sliderEnabled -> SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            activeTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
        isVisuallyMutedForStream -> SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
        else -> SliderDefaults.colors()
    }

    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        IconButton(
            onClick = {
                onIconClick()
                resetTimer()
            },
            modifier = Modifier.padding(start = 16.dp)
        ) {
            val isRingStream = streamType == AudioManager.STREAM_RING
            val tint = if (!sliderEnabled && !isRingStream) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            Icon(imageVector = icon, contentDescription = name, tint = tint)
        }

        Slider(
            value = displayValue,
            onValueChange = { newVol ->
                val targetVol = newVol.toInt().coerceAtLeast(minVolume)
                if (streamType == AudioManager.STREAM_MUSIC && isMediaMuted) {
                    overlayViewModel.adjustStreamVolume(streamType, AudioManager.ADJUST_UNMUTE)
                }
                overlayViewModel.setStreamVolume(streamType, targetVol)
                resetTimer()
            },
            onValueChangeFinished = {
                resumeTimer()
                if (displayValue.toInt() > minVolume) {
                    scope.launch {
                        settingsDataStore.setLastSystemVolume(streamType, displayValue.toInt())
                    }
                }
            },
            interactionSource = interactionSource,
            valueRange = 0f..maxVolume.toFloat(),
            modifier = Modifier.weight(1f),
            enabled = sliderEnabled,
            colors = sliderColors
        )

        Box(
            modifier = Modifier.padding(end = 16.dp).width(24.dp).clickable(
                enabled = sliderEnabled,
                onClick = {
                    overlayViewModel.setStreamVolume(streamType, maxVolume)
                    resetTimer()
                }
            ),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = "${displayValue.toInt()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun AppSliderRow(
    app: VolumeManager.AppState,
    volumeManager: VolumeManager,
    settingsDataStore: SettingsDataStore,
    lastAppVolumes: Map<String, Float>,
    pauseTimer: () -> Unit,
    resumeTimer: () -> Unit,
    resetTimer: () -> Unit
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
                        volumeManager.setAppVolume(app.packageName, lastVolume)
                        settingsDataStore.setLastAppVolume(app.packageName, lastVolume)
                    } else {
                        settingsDataStore.setLastAppVolume(app.packageName, app.volume)
                        volumeManager.setAppVolume(app.packageName, 0f)
                    }
                }
                resumeTimer()
                resetTimer()
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
                volumeManager.setAppVolume(app.packageName, newVol)
                if (newVol > 0f) {
                    lastVolume = newVol
                    scope.launch { settingsDataStore.setLastAppVolume(app.packageName, newVol) }
                }
                resetTimer()
            },
            onValueChangeFinished = { resumeTimer(); resetTimer() },
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
