package io.github.legandy.volumemanager.core

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import io.github.legandy.volumemanager.overlay.OverlayService
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
        private const val SERVICE_NAME_SEPARATOR = ":"
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
            return Reflect.onClass("$type${'$'}Stub").call("asInterface", wrapper).get()
        }
    }

    private val pm: PackageManager = context.packageManager
    private var audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false

    private var shizukuActivityManager: Reflect? = null
    private var shizukuAudioManager: Reflect? = null
    private var shizukuNotificationManager: Reflect? = null


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
                shizukuNotificationManager = null
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
            shizukuNotificationManager = Reflect.on(getShizukuService(Context.NOTIFICATION_SERVICE, "android.app.INotificationManager"))
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

    @SuppressLint("MissingPermission")
    fun grantWriteSecureSettingsPermission() {
        var state = context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
        if (state == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "WRITE_SECURE_SETTINGS already granted.")
            return
        }

        try {
            val process = Reflect.onClass(Shizuku::class.java).call(
                "newProcess", arrayOf("pm", "grant", context.packageName, android.Manifest.permission.WRITE_SECURE_SETTINGS), null, null
            ).get<Process>() // Changed from ShizukuRemoteProcess to Process
            process.waitFor()

            state = context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
            if (state == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "WRITE_SECURE_SETTINGS granted successfully via Shizuku.")
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to grant WRITE_SECURE_SETTINGS via Shizuku: ${e.message}", e)
            throw SecurityException("Can't grant WRITE_SECURE_SETTINGS permission via Shizuku.")
        }

        throw SecurityException("Can't grant WRITE_SECURE_SETTINGS permission.")
    }

    fun enableAccessibilityService(componentName: ComponentName) {
        try {
            Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)

            var enabledAccessibilityServices = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )

            val serviceName = componentName.flattenToString()

            if (enabledAccessibilityServices.isNullOrBlank()) {
                enabledAccessibilityServices = serviceName
            } else if (!enabledAccessibilityServices.contains(serviceName)) {
                enabledAccessibilityServices += SERVICE_NAME_SEPARATOR + serviceName
            } else {
                Log.d(TAG, "Accessibility service $serviceName already enabled.")
                return // Already enabled
            }

            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                enabledAccessibilityServices
            )

            val finalEnabledServices = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (finalEnabledServices == null || !finalEnabledServices.contains(serviceName)) {
                throw SecurityException("Can't enable accessibility service $serviceName")
            }
            Log.d(TAG, "Accessibility service $serviceName enabled successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable accessibility service: ${e.message}", e)
            throw SecurityException("Failed to enable accessibility service: ${e.message}", e)
        }
    }

    fun enableNotificationListener(componentName: ComponentName) {
        try {
            var enabledNotificationListeners = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            )

            val serviceName = componentName.flattenToString()

            if (enabledNotificationListeners.isNullOrBlank()) {
                enabledNotificationListeners = serviceName
            } else if (!enabledNotificationListeners.contains(serviceName)) {
                enabledNotificationListeners += SERVICE_NAME_SEPARATOR + serviceName
            } else {
                Log.d(TAG, "Notification listener $serviceName already enabled.")
                return // Already enabled
            }

            Settings.Secure.putString(
                context.contentResolver,
                "enabled_notification_listeners",
                enabledNotificationListeners
            )

            val finalEnabledListeners = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            )
            if (finalEnabledListeners == null || !finalEnabledListeners.contains(serviceName)) {
                throw SecurityException("Can't enable notification listener $serviceName")
            }
            Log.d(TAG, "Notification listener $serviceName enabled successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable notification listener: ${e.message}", e)
            throw SecurityException("Failed to enable notification listener: ${e.message}", e)
        }
    }

    private fun handlePlaybackConfigs(configs: List<AudioPlaybackConfiguration>) {
        scope.launch {
            val runningProcesses = withContext(Dispatchers.Default) {
                try {
                    shizukuActivityManager?.call("getRunningAppProcesses")?.get<List<ActivityManager.RunningAppProcessInfo>>()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get running processes via Shizuku", e)
                    null
                }
            }

            if (runningProcesses.isNullOrEmpty()) {
                Log.w(TAG, "Could not retrieve running process list.")
                return@launch
            }

            // Clear players for all currently tracked apps to ensure fresh state and re-application of volume
            apps.values.forEach { it.players.clear() }

            // Map to hold (packageName -> list of playerProxies and their configs) for currently active players
            val currentActivePlayersMap = mutableMapOf<String, MutableList<Pair<Any, AudioPlaybackConfiguration>>>()

            withContext(Dispatchers.Default) {
                configs.forEach { cfg ->
                    val pid = try { getClientPidMethod?.invoke(cfg) as? Int } catch (_: Throwable) { null } ?: return@forEach
                    val pkgName = runningProcesses.find { it.pid == pid }?.processName?.split(":")?.firstOrNull() ?: return@forEach
                    val playerProxy = try { getPlayerProxyMethod?.invoke(cfg) } catch (_: Throwable) { null }

                    if (playerProxy != null) {
                        currentActivePlayersMap.getOrPut(pkgName) { mutableListOf() }.add(playerProxy to cfg)
                    }
                }
            }

            // Now, update the 'apps' map and their 'players' lists on the Main dispatcher
            withContext(Dispatchers.Main) {
                currentActivePlayersMap.forEach { (pkgName, playerConfigPairs) ->
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
                            Log.e(TAG, "Failed to get app info for $pkgName, creating dummy AppState", e)
                            AppState(pkgName, pkgName, ImageBitmap(1,1), initialVolume = 1f)
                        }
                    }

                    // Create a new list of players for this app based on current active configs
                    val newPlayersListForApp = mutableListOf<PlayerEntry>()
                    playerConfigPairs.forEach { (playerProxy, cfg) ->
                        // Apply volume immediately, mirroring Manager_old.kt behavior
                        try {
                            playerProxySetVolumeMethod?.invoke(playerProxy, appState.volume)
                        } catch (t: Throwable) {
                            Log.w(TAG, "PlayerProxy.setVolume failed for $pkgName", t)
                        }
                        newPlayersListForApp.add(PlayerEntry(cfg, playerProxy))
                    }

                    // Replace the app's players list with the new one
                    appState.players.addAll(newPlayersListForApp)
                }
            }
        }
    }

    fun setAppVolume(packageName: String, volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        val app = apps[packageName] ?: return

        app.volume = v // This will now trigger the custom setter in AppState and apply volume to players

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
        private val initialVolume: Float = 1f
    ) {
        private var _volume by mutableFloatStateOf(initialVolume)

        var volume: Float
            get() = _volume
            set(value) {
                _volume = value
                players.forEach { entry ->
                    try {
                        playerProxySetVolumeMethod?.invoke(entry.proxy, value)
                    } catch (t: Throwable) {
                        Log.w(TAG, "PlayerProxy.setVolume failed for ${packageName}", t)
                    }
                }
            }
    }
}