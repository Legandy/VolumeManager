package io.github.legandy.volumemanager.overlay

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.app.NotificationManager
import androidx.lifecycle.AndroidViewModel
import androidx.core.content.ContextCompat
import io.github.legandy.volumemanager.core.MyApplication
import io.github.legandy.volumemanager.core.ShizukuManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SystemAudioUiState(
    val volumes: Map<Int, Int> = emptyMap(),
    val maxVolumes: Map<Int, Int> = emptyMap(),
    val mutedStreams: Set<Int> = emptySet(),
    val ringerMode: Int = AudioManager.RINGER_MODE_NORMAL,
    val isDndOn: Boolean = false,
    val deviceType: Int = AudioDeviceInfo.TYPE_UNKNOWN
)

class OverlayViewModel(application: Application) : AndroidViewModel(application) {

    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notificationManager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val manager: ShizukuManager = MyApplication.manager

    private val _uiState = MutableStateFlow(SystemAudioUiState())
    val uiState = _uiState.asStateFlow()

    private val audioStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshState()
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction("android.media.action.OUTPUT_DEVICE_CHANGED")
        }
        ContextCompat.registerReceiver(application, audioStateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        refreshState()
    }

    private fun refreshState() {
        val volumes = mutableMapOf<Int, Int>()
        val maxVolumes = mutableMapOf<Int, Int>()
        val mutedStreams = mutableSetOf<Int>()
        val streams = listOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING, AudioManager.STREAM_ALARM, AudioManager.STREAM_VOICE_CALL)

        streams.forEach { stream ->
            volumes[stream] = audioManager.getStreamVolume(stream)
            maxVolumes[stream] = audioManager.getStreamMaxVolume(stream)
            if (audioManager.isStreamMute(stream)) {
                mutedStreams.add(stream)
            }
        }

        _uiState.value = SystemAudioUiState(
            volumes = volumes,
            maxVolumes = maxVolumes,
            mutedStreams = mutedStreams,
            ringerMode = audioManager.ringerMode,
            isDndOn = notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL,
            deviceType = getMediaOutputDeviceType()
        )
    }

    private fun getMediaOutputDeviceType(): Int {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        // Prioritize Bluetooth A2DP
        val bluetoothDevice = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        if (bluetoothDevice != null) {
            return bluetoothDevice.type
        }

        // Then prioritize wired headphones/headset
        val wiredHeadset = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }
        if (wiredHeadset != null) {
            return wiredHeadset.type
        }

        // Finally, default to built-in speaker if no other audio output is found
        val speaker = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        if (speaker != null) {
            return speaker.type
        }

        return AudioDeviceInfo.TYPE_UNKNOWN
    }

    fun setStreamVolume(streamType: Int, volume: Int) {
        audioManager.setStreamVolume(streamType, volume, 0)
    }

    fun adjustStreamVolume(streamType: Int, direction: Int) {
        audioManager.adjustStreamVolume(streamType, direction, 0)
    }

    fun setRingerMode(mode: Int) {
        manager.setRingerMode(mode)
    }

    fun setDnd(enabled: Boolean) {
        notificationManager.setInterruptionFilter(
            if (enabled) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unregisterReceiver(audioStateReceiver)
    }

}