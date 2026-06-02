package io.github.legandy.volumemixerpanel.overlay

import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.legandy.volumemixerpanel.core.VolumeManager
import io.github.legandy.volumemixerpanel.settings.AppFilterMode
import io.github.legandy.volumemixerpanel.settings.SettingsDataStore
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SystemAudioUiState(
    val volumes: Map<Int, Int> = emptyMap(),
    val maxVolumes: Map<Int, Int> = emptyMap(),
    val mutedStreams: Set<Int> = emptySet(),
    val ringerMode: Int = AudioManager.RINGER_MODE_NORMAL,
    val isDndOn: Boolean = false,
    val deviceType: Int = AudioDeviceInfo.TYPE_UNKNOWN,
    val activeApps: List<VolumeManager.AppState> = emptyList()
)

@HiltViewModel
class OverlayViewModel @Inject constructor(
    private val audioManager: AudioManager,
    private val volumeManager: VolumeManager,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val appsFlow = snapshotFlow { volumeManager.apps.values.toList() }

    // Reactive filtering of apps moved to a separate Flow to keep 'combine' clean and avoid API limits.
    private val filteredAppsFlow = combine(
        appsFlow,
        settingsDataStore.appFilterMode,
        settingsDataStore.appBlacklist,
        settingsDataStore.appWhitelist,
        settingsDataStore.lastAppVolumes
    ) { apps, filterMode, blacklist, whitelist, lastVolumes ->
        apps.filter { it.players.isNotEmpty() }
            .filter { app ->
                when (filterMode) {
                    AppFilterMode.WHITELIST -> app.packageName in whitelist
                    AppFilterMode.BLACKLIST -> app.packageName !in blacklist
                    else -> true
                }
            }
            // Only show apps currently making sound or recently adjusted
            .filter { it.volume > 0.01f || lastVolumes.containsKey(it.packageName) }
    }

    val uiState: StateFlow<SystemAudioUiState> = combine(
        volumeManager.ringerMode,
        volumeManager.isDndOn,
        volumeManager.volumes,
        filteredAppsFlow
    ) { ringer, dnd, volumes, activeApps ->
        
        val maxVolumes = mutableMapOf<Int, Int>()
        val mutedStreams = mutableSetOf<Int>()
        val streams = listOf(
            AudioManager.STREAM_MUSIC, 
            AudioManager.STREAM_RING, 
            AudioManager.STREAM_ALARM, 
            AudioManager.STREAM_VOICE_CALL
        )

        streams.forEach { stream ->
            maxVolumes[stream] = audioManager.getStreamMaxVolume(stream)
            if (audioManager.isStreamMute(stream)) {
                mutedStreams.add(stream)
            }
        }

        SystemAudioUiState(
            volumes = volumes,
            maxVolumes = maxVolumes,
            mutedStreams = mutedStreams,
            ringerMode = ringer,
            isDndOn = dnd,
            deviceType = getMediaOutputDeviceType(),
            activeApps = activeApps
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SystemAudioUiState()
    )

    private fun getMediaOutputDeviceType(): Int {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bluetoothDevice = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        if (bluetoothDevice != null) return bluetoothDevice.type
        val wiredHeadset = devices.firstOrNull { 
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET 
        }
        if (wiredHeadset != null) return wiredHeadset.type
        val speaker = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        if (speaker != null) return speaker.type
        return AudioDeviceInfo.TYPE_UNKNOWN
    }

    fun setStreamVolume(streamType: Int, volume: Int) {
        audioManager.setStreamVolume(streamType, volume, 0)
    }

    fun adjustStreamVolume(streamType: Int, direction: Int) {
        audioManager.adjustStreamVolume(streamType, direction, 0)
    }

    fun setRingerMode(mode: Int) {
        volumeManager.setRingerMode(mode)
    }

    fun setDndShizuku(enabled: Boolean) {
        volumeManager.setDndShizuku(enabled)
    }
}
