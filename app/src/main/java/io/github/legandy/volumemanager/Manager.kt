package io.github.legandy.volumemanager

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
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
import org.joor.Reflect
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method

@SuppressLint("PrivateApi")
class Manager(
    private val context: Context,
    private val volumesDataStore: DataStore<Preferences>
) {
    companion object {
        private const val TAG = "VolumeManager.Manager"
        private const val SHIZUKU_REQ_CODE = 42
        private var getClientPidMethod: Method? = null
        private var getPlayerProxyMethod: Method? = null
        private var playerProxySetVolumeMethod: Method? = null

        init {
            try {
                val audioPlaybackCls = AudioPlaybackConfiguration::class.java
                getClientPidMethod = audioPlaybackCls.getDeclaredMethod("getClientPid")
                getPlayerProxyMethod = audioPlaybackCls.getDeclaredMethod("getPlayerProxy")
                playerProxySetVolumeMethod = Class.forName("android.media.PlayerProxy").getDeclaredMethod("setVolume", Float::class.javaPrimitiveType)
            } catch (t: Throwable) {
                Log.w(TAG, "Critical reflection init failed: ${t.message}")
            }
        }

        private fun getShizukuService(name: String, type: String): Any {
            val binder = SystemServiceHelper.getSystemService(name)
            val wrapper = ShizukuBinderWrapper(binder)
            return Reflect.onClass("$type\$Stub").call("asInterface", wrapper).get()
        }
    }

    private val pm: PackageManager = context.packageManager
    private var audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false

    private var shizukuActivityManager: Reflect? = null
    private var shizukuAudioManager: Reflect? = null

    val apps = mutableStateMapOf<String, AppState>()
    var shizukuReady by mutableStateOf(false); private set
    var shizukuPermission by mutableStateOf(false); private set

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            if (shizukuActivityManager != null) {
                configs?.let { handlePlaybackConfigs(it) }
            }
        }
    }

    init {
        val listener = object : Shizuku.OnBinderReceivedListener,
            Shizuku.OnBinderDeadListener,
            Shizuku.OnRequestPermissionResultListener {
            override fun onBinderReceived() {
                shizukuReady = true
                shizukuPermission = (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
                if (shizukuPermission) start()
            }
            override fun onBinderDead() {
                shizukuReady = false; shizukuPermission = false
                shizukuActivityManager = null
                shizukuAudioManager = null
                started = false
            }
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode == SHIZUKU_REQ_CODE) {
                    shizukuPermission = (grantResult == PackageManager.PERMISSION_GRANTED)
                    if (shizukuPermission) start()
                }
            }
        }
        Shizuku.addBinderReceivedListenerSticky(listener)
        Shizuku.addBinderDeadListener(listener)
        Shizuku.addRequestPermissionResultListener(listener)
    }

    fun requestShizukuPermission(activity: Activity) {
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(SHIZUKU_REQ_CODE)
        }
    }

    private fun start() {
        if (started) return

        try {
            val audioService = getShizukuService(Context.AUDIO_SERVICE, "android.media.IAudioService")
            shizukuAudioManager = Reflect.on(audioService)
            Reflect.onClass(AudioManager::class.java).set("sService", audioService)

            audioManager = context.getSystemService(AudioManager::class.java)!!
            shizukuActivityManager = Reflect.on(getShizukuService(Context.ACTIVITY_SERVICE, "android.app.IActivityManager"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject Shizuku services", e)
            return
        }

        started = true
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
            shizukuAudioManager?.call("setRingerModeInternal", mode, context.packageName)
            Log.d(TAG, "Set ringer mode to $mode using internal API")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set ringer mode via Shizuku, falling back to public API", e)
            // Fallback for safety, though it might cause DND issues
            try {
                audioManager.ringerMode = mode
            } catch (fe: Exception) {
                Log.e(TAG, "Public API fallback also failed", fe)
            }
        }
    }

    private fun handlePlaybackConfigs(configs: List<AudioPlaybackConfiguration>) {
        apps.values.forEach { it.players.clear() }

        scope.launch {
            withContext(Dispatchers.Default) {
                val runningProcesses = try {
                    shizukuActivityManager?.call("getRunningAppProcesses")?.get<List<ActivityManager.RunningAppProcessInfo>>()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get running processes via Shizuku", e)
                    null
                }

                if (runningProcesses.isNullOrEmpty()) {
                    Log.w(TAG, "Could not retrieve running process list.")
                    return@withContext
                }

                configs.forEach { cfg ->
                    val pid = try { getClientPidMethod?.invoke(cfg) as? Int } catch (_: Throwable) { null } ?: return@forEach
                    val pkgName = runningProcesses.find { it.pid == pid }?.processName?.split(":")?.firstOrNull() ?: return@forEach
                    val playerProxy = try { getPlayerProxyMethod?.invoke(cfg) } catch (_: Throwable) { null }

                    launch(Dispatchers.Main) {
                        val appState = apps.getOrPut(pkgName) {
                            try {
                                val appInfo = pm.getApplicationInfo(pkgName, 0)
                                AppState(
                                    packageName = pkgName,
                                    label = appInfo.loadLabel(pm).toString(),
                                    icon = appInfo.loadIcon(pm).toBitmap().asImageBitmap(),
                                    initialVolume = volumesDataStore.data.first()[floatPreferencesKey("vol_$pkgName")] ?: 1f
                                )
                            } catch (e: Exception) { return@launch }
                        }

                        if (playerProxy != null) {
                            try {
                                playerProxySetVolumeMethod?.invoke(playerProxy, appState.volume)
                            } catch (t: Throwable) {
                                Log.w(TAG, "Initial PlayerProxy.setVolume failed for $pkgName", t)
                            }
                            if (appState.players.none { it.proxy === playerProxy }) {
                                appState.players.add(PlayerEntry(cfg, playerProxy))
                            }
                        }
                    }
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

    data class PlayerEntry(val config: AudioPlaybackConfiguration, val proxy: Any?)

    data class AppState(
        val packageName: String,
        val label: String,
        val icon: ImageBitmap, // Use stable ImageBitmap instead of Drawable
        val players: MutableList<PlayerEntry> = mutableListOf(),
        val initialVolume: Float = 1f
    ) {
        var volume by mutableStateOf(initialVolume)
    }
}
