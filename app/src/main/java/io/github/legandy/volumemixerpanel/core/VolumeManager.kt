package io.github.legandy.volumemixerpanel.core

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.reflect.Method

@SuppressLint("PrivateApi")
class VolumeManager(
    private val context: Context,
    private val shizukuManager: ShizukuManager,
    private val volumesDataStore: DataStore<Preferences>
) {
    companion object {
        private const val TAG = "VolumeMixerPanel.VolumeManager"

        // Defined methods
        private var getClientPidMethod: Method? = null
        private var getPlayerProxyMethod: Method? = null
        private var playerProxySetVolumeMethod: Method? = null
        private var getPlayerInterfaceIdMethod: Method? = null

        init {
            try {
                val audioPlaybackCls = AudioPlaybackConfiguration::class.java
                getClientPidMethod = audioPlaybackCls.getDeclaredMethod("getClientPid")
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

    val apps = mutableStateMapOf<String, AppState>()

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            if (shizukuManager.shizukuReady) {
                configs?.let { handlePlaybackConfigs(it) }
            }
        }
    }

    fun start() {
        audioManager.registerAudioPlaybackCallback(playbackCallback, null)
        scope.launch {
            loadPersistedVolumes()
            try {
                val initialConfigs = audioManager.activePlaybackConfigurations
                if (initialConfigs.isNotEmpty()) {
                    handlePlaybackConfigs(initialConfigs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get initial active playback configurations", e)
            }
        }
    }

    fun destroy() {
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        scope.cancel()
    }

    fun setRingerMode(mode: Int) {
        try {
            shizukuManager.shizukuAudioManager?.call("setRingerModeInternal", mode, context.packageName)
            Log.d(TAG, "Set ringer mode to $mode using internal API")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set ringer mode via Shizuku, falling back to public API", e)
            try {
                audioManager.ringerMode = mode
            } catch (fe: Exception) {
                Log.e(TAG, "Public API fallback also failed", fe)
            }
        }
    }

    fun setSilent() = setRingerMode(AudioManager.RINGER_MODE_SILENT)


    fun getCurrentDndMode(): Int {
        return try {
            Settings.Global.getInt(context.contentResolver, "zen_mode", 0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current DND mode.", e)
            0 // Assume DND is off on failure
        }
    }

    fun setDndShizuku(enable: Boolean) {
        val mode = if (enable) "priority" else "all"
        val command = "cmd notification set_dnd $mode"

        val exitCode = shizukuManager.executeShizukuCommand(command)

        if (exitCode == 0) {
            Log.d(TAG, "Successfully set DND via Shizuku cmd.")
        } else {
            Log.e(TAG, "Shizuku command failed with exit code: $exitCode. Falling back to API.")
            setDndAPI() // Fallback
        }
    }

    private fun setDndAPI() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            Log.w(TAG, "Cannot toggle DND via fallback: Notification Policy Access not granted.")
            return
        }

        try {
            val currentFilter = notificationManager.currentInterruptionFilter

            val newFilter = if (currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                NotificationManager.INTERRUPTION_FILTER_PRIORITY // Turn ON DND
            } else {
                NotificationManager.INTERRUPTION_FILTER_ALL // Turn OFF DND
            }
            notificationManager.setInterruptionFilter(newFilter)

            Log.d(TAG, "Toggled DND via API. Old: $currentFilter, New Target: $newFilter")
            Log.d(TAG, "Immediate system state check: ${getCurrentDndMode()}")

        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to toggle DND via fallback due to SecurityException.", e)
        }
    }

    private fun handlePlaybackConfigs(configs: List<AudioPlaybackConfiguration>) {
        scope.launch {
            val runningProcesses = withContext(Dispatchers.Default) {
                try {
                    shizukuManager.shizukuActivityManager?.call("getRunningAppProcesses")?.get<List<ActivityManager.RunningAppProcessInfo>>()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get running processes via Shizuku", e)
                    null
                }
            }
            if (runningProcesses.isNullOrEmpty()) return@launch

            val activeProxies = mutableMapOf<String, MutableList<Pair<Any, AudioPlaybackConfiguration>>>()
            withContext(Dispatchers.Default) {
                configs.forEach { cfg ->
                    val pid = try { getClientPidMethod?.invoke(cfg) as? Int } catch (_: Throwable) { null }
                    val pkgName = runningProcesses.find { it.pid == pid }?.processName?.split(":")?.firstOrNull()

                    if (pid != null && pkgName != null) {
                        try {
                            val playerProxy = getPlayerProxyMethod?.invoke(cfg)
                            if (playerProxy != null) {
                                activeProxies.getOrPut(pkgName) { mutableListOf() }.add(playerProxy to cfg)
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed to get PlayerProxy", t)
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                apps.keys.retainAll(activeProxies.keys)
                activeProxies.forEach { (pkgName, playerConfigPairs) ->
                    val appState = apps.getOrPut(pkgName) {
                        try {
                            val appInfo = pm.getApplicationInfo(pkgName, 0)
                            AppState(
                                packageName = pkgName,
                                label = appInfo.loadLabel(pm).toString(),
                                icon = appInfo.loadIcon(pm).toBitmap().asImageBitmap(),
                                initialVolume = volumesDataStore.data.first()[floatPreferencesKey("vol_$pkgName")] ?: 1f
                            )
                        } catch (e: Exception) {
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
                                Log.d(TAG, "Applied volume ${appState.volume} to NEW player (ID: $currentId) for $pkgName")
                            } catch (t: Throwable) {
                                Log.e(TAG, "Failed to set volume for new player", t)
                            }
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
            } catch (t: Throwable) {
                Log.w(TAG, "PlayerProxy.setVolume failed for $packageName", t)
            }
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

    data class PlayerEntry(
        val config: AudioPlaybackConfiguration,
        val proxy: Any?
    )

    data class AppState(
        val packageName: String,
        val label: String,
        val icon: ImageBitmap,
        val players: MutableList<PlayerEntry> = mutableListOf(),
        val initialVolume: Float = 1f
    ) {
        var volume by mutableStateOf(initialVolume)
    }
}
