package io.github.legandy.volumemixerpanel.core

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.legandy.volumemixerpanel.di.AppVolumesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.reflect.Method
import javax.inject.Inject
import javax.inject.Singleton

@SuppressLint("PrivateApi")
@Singleton
class VolumeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager,
    @AppVolumesDataStore private val volumesDataStore: DataStore<Preferences>
) {
    companion object {
        private const val TAG = "VolumeMixerPanel.VolumeManager"

        private var getClientPidMethod: Method? = null
        private var getClientPackageNameMethod: Method? = null
        private var getPlayerProxyMethod: Method? = null
        private var playerProxySetVolumeMethod: Method? = null
        private var getPlayerInterfaceIdMethod: Method? = null

        init {
            try {
                val audioPlaybackCls = AudioPlaybackConfiguration::class.java
                getClientPidMethod = audioPlaybackCls.getDeclaredMethod("getClientPid")
                try {
                    getClientPackageNameMethod = audioPlaybackCls.getDeclaredMethod("getClientPackageName")
                } catch (_: NoSuchMethodException) {
                    Log.d(TAG, "getClientPackageName not available on this Android version")
                }
                getPlayerProxyMethod = audioPlaybackCls.getDeclaredMethod("getPlayerProxy")
                getPlayerInterfaceIdMethod = audioPlaybackCls.getDeclaredMethod("getPlayerInterfaceId")
                playerProxySetVolumeMethod = Class.forName("android.media.PlayerProxy").getDeclaredMethod("setVolume", Float::class.javaPrimitiveType)
            } catch (t: Throwable) {
                Log.w(TAG, "Critical reflection init failed: ${t.message}")
            }
        }
    }

    private val pm: PackageManager = context.packageManager
    private var audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isStarted = false

    val apps = mutableStateMapOf<String, AppState>()

    // --- Reactive System Flows ---
    private val _ringerMode = MutableStateFlow(AudioManager.RINGER_MODE_NORMAL)
    val ringerMode = _ringerMode.asStateFlow()

    private val _isDndOn = MutableStateFlow(false)
    val isDndOn = _isDndOn.asStateFlow()

    private val _volumes = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val volumes = _volumes.asStateFlow()

    // --- Observers ---
    private val volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            updateVolumes()
        }
    }

    private val audioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                    _ringerMode.value = getRingerModeInternal()
                }
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                    _isDndOn.value = getCurrentDndMode() != 0
                }
            }
        }
    }

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            if (shizukuManager.shizukuReady) {
                configs?.let { handlePlaybackConfigs(it) }
            }
        }
    }

    fun start() {
        if (isStarted) {
            Log.d(TAG, "VolumeManager already started, skipping.")
            return
        }
        isStarted = true
        Log.i(TAG, "Starting VolumeManager services...")

        // Initial state seeding
        _ringerMode.value = getRingerModeInternal()
        _isDndOn.value = getCurrentDndMode() != 0
        updateVolumes()

        // Register ContentObserver
        try {
            context.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                volumeObserver
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register ContentObserver", e)
        }

        // Register Receiver for Ringer/DND
        val filter = IntentFilter().apply {
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        try {
            context.registerReceiver(audioReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register audioReceiver", e)
        }

        try {
            audioManager.registerAudioPlaybackCallback(playbackCallback, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register playbackCallback", e)
        }

        scope.launch {
            loadPersistedVolumes()
            try {
                val initialConfigs = audioManager.activePlaybackConfigurations
                if (initialConfigs.isNotEmpty()) {
                    Log.d(TAG, "Processing ${initialConfigs.size} initial playback configs")
                    handlePlaybackConfigs(initialConfigs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get initial active playback configurations", e)
            }
        }
    }

    fun destroy() {
        Log.i(TAG, "Destroying VolumeManager...")
        isStarted = false
        try {
            context.contentResolver.unregisterContentObserver(volumeObserver)
            context.unregisterReceiver(audioReceiver)
        } catch (_: Exception) {}
        try {
            audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        } catch (_: Exception) {}
        scope.cancel()
    }

    private fun updateVolumes() {
        val streams = listOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_VOICE_CALL
        )
        val currentVolumes = streams.associateWith { audioManager.getStreamVolume(it) }
        _volumes.value = currentVolumes
    }

    fun getRingerModeInternal(): Int {
        return try {
            val result = shizukuManager.shizukuAudioManager?.call("getRingerModeInternal")?.get<Int>()
            result ?: audioManager.ringerMode
        } catch (_: Exception) {
            audioManager.ringerMode
        }
    }

    fun setRingerMode(mode: Int) {
        try {
            shizukuManager.shizukuAudioManager?.call("setRingerModeInternal", mode, context.packageName)
        } catch (_: Exception) {
            try { audioManager.ringerMode = mode } catch (_: Exception) {}
        }
    }

    fun setSilent() = setRingerMode(AudioManager.RINGER_MODE_SILENT)

    fun getCurrentDndMode(): Int {
        return try {
            Settings.Global.getInt(context.contentResolver, "zen_mode", 0)
        } catch (_: Exception) { 0 }
    }

    fun setDndShizuku(enable: Boolean) {
        val mode = if (enable) "priority" else "all"
        val command = "cmd notification set_dnd $mode"
        shizukuManager.executeShizukuCommand(command)
    }

    // --- App Volume Logic ---
    private fun handlePlaybackConfigs(configs: List<AudioPlaybackConfiguration>) {
        if (!shizukuManager.shizukuReady) {
            Log.v(TAG, "handlePlaybackConfigs: Shizuku not ready, skipping.")
            return
        }

        scope.launch {
            val runningProcesses = withContext(Dispatchers.Default) {
                try {
                    shizukuManager.shizukuActivityManager?.call("getRunningAppProcesses")?.get<List<ActivityManager.RunningAppProcessInfo>>()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get running processes via Shizuku", e)
                    null
                }
            }

            val activeProxies = mutableMapOf<String, MutableList<Pair<Any, AudioPlaybackConfiguration>>>()
            configs.forEach { cfg ->
                var pkgName: String? = try { getClientPackageNameMethod?.invoke(cfg) as? String } catch (_: Throwable) { null }
                
                if (pkgName == null) {
                    val pid = try { getClientPidMethod?.invoke(cfg) as? Int } catch (_: Throwable) { null }
                    pkgName = runningProcesses?.find { it.pid == pid }?.processName?.split(":")?.firstOrNull()
                }

                if (pkgName != null) {
                    try {
                        val playerProxy = getPlayerProxyMethod?.invoke(cfg)
                        if (playerProxy != null) {
                            activeProxies.getOrPut(pkgName) { mutableListOf() }.add(playerProxy to cfg)
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "Failed to get player proxy for $pkgName", e)
                    }
                }
            }

            if (activeProxies.isEmpty() && configs.isNotEmpty()) {
                Log.d(TAG, "No package names found for ${configs.size} configs")
            }

            withContext(Dispatchers.Main) {
                apps.keys.retainAll(activeProxies.keys)
                activeProxies.forEach { (pkgName, playerConfigPairs) ->
                    val appState = apps.getOrPut(pkgName) {
                        Log.d(TAG, "New app detected: $pkgName")
                        try {
                            val appInfo = pm.getApplicationInfo(pkgName, 0)
                            AppState(
                                packageName = pkgName,
                                label = appInfo.loadLabel(pm).toString(),
                                icon = appInfo.loadIcon(pm).toBitmap().asImageBitmap(),
                                initialVolume = volumesDataStore.data.first()[floatPreferencesKey("vol_$pkgName")] ?: 1f
                            )
                        } catch (_: Exception) {
                            AppState(pkgName, pkgName, ImageBitmap(1, 1), initialVolume = 1f)
                        }
                    }
                    
                    val newPlayerList = mutableListOf<PlayerEntry>()
                    playerConfigPairs.forEach { (proxy, cfg) ->
                        val currentId = try { getPlayerInterfaceIdMethod?.invoke(cfg) as? Int } catch (_: Exception) { -1 }
                        val existingPlayer = appState.players.find { entry ->
                            val entryId = try { getPlayerInterfaceIdMethod?.invoke(entry.config) as? Int } catch (_: Exception) { -2 }
                            entryId != null && currentId != null && entryId == currentId
                        }

                        if (existingPlayer != null) {
                            newPlayerList.add(existingPlayer.copy(config = cfg, proxy = proxy))
                        } else {
                            try {
                                playerProxySetVolumeMethod?.invoke(proxy, appState.volume)
                            } catch (_: Throwable) {}
                            newPlayerList.add(PlayerEntry(cfg, proxy))
                        }
                    }
                    appState.players.clear()
                    appState.players.addAll(newPlayerList)
                }
            }
        }
    }

    fun setAppVolume(packageName: String, volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        val app = apps[packageName] ?: return
        app.volume = v
        app.players.forEach { entry ->
            try {
                playerProxySetVolumeMethod?.invoke(entry.proxy, v)
            } catch (_: Throwable) {}
        }
        scope.launch {
            volumesDataStore.edit { prefs -> prefs[floatPreferencesKey("vol_$packageName")] = v }
        }
    }

    private suspend fun loadPersistedVolumes() {
        volumesDataStore.data.first().asMap().forEach { (key, value) ->
            if (key.name.startsWith("vol_") && value is Float) {
                val pkg = key.name.removePrefix("vol_")
                if (apps[pkg] == null) {
                    try {
                        val appInfo = pm.getApplicationInfo(pkg, 0)
                        apps[pkg] = AppState(
                            pkg,
                            appInfo.loadLabel(pm).toString(),
                            appInfo.loadIcon(pm).toBitmap().asImageBitmap(),
                            initialVolume = value
                        )
                    } catch (_: Exception) {}
                } else {
                    apps[pkg]?.volume = value
                }
            }
        }
    }

    data class PlayerEntry(val config: AudioPlaybackConfiguration, val proxy: Any?)

    data class AppState(
        val packageName: String,
        val label: String,
        val icon: ImageBitmap,
        val players: MutableList<PlayerEntry> = mutableListOf(),
        val initialVolume: Float = 1f
    ) {
        var volume by mutableFloatStateOf(initialVolume)
    }
}
