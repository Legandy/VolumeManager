package io.github.legandy.volumemixerpanel.core

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
import android.app.NotificationManager

@SuppressLint("PrivateApi")
class ShizukuManager(
    private val context: Context,
    private val volumesDataStore: DataStore<Preferences>
) {
    companion object {
        private const val TAG = "VolumeMixerPanel.ShizukuManager"
        private const val SHIZUKU_REQ_CODE = 42
        private const val SERVICE_NAME_SEPARATOR = ":"

        // Defined methods
        private var getClientPidMethod: Method? = null
        private var getPlayerProxyMethod: Method? = null
        private var playerProxySetVolumeMethod: Method? = null
        private var getPlayerInterfaceIdMethod: Method? = null // <--- ADD THIS

        init {
            try {
                val audioPlaybackCls = AudioPlaybackConfiguration::class.java
                getClientPidMethod = audioPlaybackCls.getDeclaredMethod("getClientPid")
                getPlayerProxyMethod = audioPlaybackCls.getDeclaredMethod("getPlayerProxy")

                // <--- ADD THIS LINE
                // getPlayerInterfaceId is public API 24+, but we use reflection to be safe and consistent
                getPlayerInterfaceIdMethod = audioPlaybackCls.getDeclaredMethod("getPlayerInterfaceId")

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

    fun setSilent() {
        setRingerMode(AudioManager.RINGER_MODE_SILENT)
        Log.d(TAG, "Set ringer mode to SILENT via ShizukuManager.setSilent()")
    }

    fun getCurrentDndMode(): Int {
        return try {
            Settings.Global.getInt(context.contentResolver, "zen_mode", 0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current DND mode.", e)
            0 // Assume DND is off on failure
        }
    }

    fun setDndShizuku(enable: Boolean) {
        // 1. Check Shizuku availability
        if (!Shizuku.pingBinder()) {
            Log.e("DND", "Shizuku not running. Falling back to API.")
            setDndAPI()
            return
        }

        // 2. Check Permission
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(0)
            return
        }

        // "priority" = DND ON (InterruptionFilter 2)
        // "all" = DND OFF (InterruptionFilter 1)
        val mode = if (enable) "priority" else "all"

        val command = "cmd notification set_dnd $mode"

        try {
            val process = Reflect.onClass(Shizuku::class.java)
                .call("newProcess", arrayOf("sh", "-c", command), null, null)
                .get<Process>()

            val exitCode = process.waitFor()

            if (exitCode == 0) {
                Log.d("DND", "Successfully set DND via Shizuku cmd.")
            } else {
                Log.e("DND", "Shizuku command failed with exit code: $exitCode")
                setDndAPI() // Fallback
            }
        } catch (e: Exception) {
            Log.e("DND", "Exception executing Shizuku command", e)
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

            //Reading the state immediately might still show the old value due to system lag.
            Log.d(TAG, "Toggled DND via API. Old: $currentFilter, New Target: $newFilter")

            // Read the system state immediately (might be flaky):
            Log.d(TAG, "Immediate system state check: ${getCurrentDndMode()}")

        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to toggle DND via fallback due to SecurityException.", e)
        }
    }
    
    @SuppressLint("NewApi")
    fun grantSystemAlertWindowPermission() {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, android.os.Process.myUid(), context.packageName)
        if (mode == android.app.AppOpsManager.MODE_ALLOWED) {
            Log.d(TAG, "SYSTEM_ALERT_WINDOW already granted.")
            return
        }

        try {
            val process = Reflect.onClass(Shizuku::class.java).call("newProcess", arrayOf("appops", "set", context.packageName, "SYSTEM_ALERT_WINDOW", "allow"), null, null).get<Process>()
            val exitValue = process.waitFor()
            Log.i(TAG, "appops set SYSTEM_ALERT_WINDOW allow exit value: $exitValue")

            val newMode = appOpsManager.checkOpNoThrow(android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, android.os.Process.myUid(), context.packageName)
            if (newMode == android.app.AppOpsManager.MODE_ALLOWED) {
                Log.d(TAG, "SYSTEM_ALERT_WINDOW granted successfully via Shizuku.")
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to grant SYSTEM_ALERT_WINDOW via Shizuku: ${e.message}", e)
            throw SecurityException("Can't grant SYSTEM_ALERT_WINDOW permission via Shizuku.")
        }

        throw SecurityException("Can't grant SYSTEM_ALERT_WINDOW permission.")
    }

    @SuppressLint("MissingPermission")
    fun grantWriteSecureSettingsPermission() {
        var state = context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
        if (state == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "WRITE_SECURE_SETTINGS already granted.")
            return
        }

        try {
            val process = Reflect.onClass(Shizuku::class.java).call("newProcess", arrayOf("pm", "grant", context.packageName, android.Manifest.permission.WRITE_SECURE_SETTINGS), null, null).get<Process>()
            val exitValue = process.waitFor()
            Log.i(TAG, "pm grant WRITE_SECURE_SETTINGS exit value: $exitValue")

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


    @SuppressLint("MissingPermission")
    fun grantNotificationPolicyPermission(): Boolean {
        // Check if permission is already granted via public API
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.isNotificationPolicyAccessGranted) {
            Log.d(TAG, "Notification Policy Access already granted.")
            return true
        }

        if (shizukuNotificationManager == null) {
            Log.e(TAG, "Shizuku Notification Manager not initialized. Cannot grant permission.")
            return false
        }

        return try {
            shizukuNotificationManager?.call("setNotificationPolicyAccessGranted", context.packageName, true)
            Log.d(TAG, "Notification Policy Access granted successfully via Shizuku.")
            // Verify if permission is truly granted after the call
            notificationManager.isNotificationPolicyAccessGranted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to grant Notification Policy Access via Shizuku: ${e.message}", e)
            false
        }
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


    private fun handlePlaybackConfigs(configs: List<AudioPlaybackConfiguration>) {
        scope.launch {
            // 1. Get running processes
            val runningProcesses = withContext(Dispatchers.Default) {
                try {
                    shizukuActivityManager?.call("getRunningAppProcesses")?.get<List<ActivityManager.RunningAppProcessInfo>>()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get running processes via Shizuku", e)
                    null
                }
            }

            if (runningProcesses.isNullOrEmpty()) return@launch

            // 2. Identify active players
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

            // 3. Update State on Main Thread
            withContext(Dispatchers.Main) {
                // A. Cleanup inactive apps
                apps.keys.forEach { pkg ->
                    if (!activeProxies.containsKey(pkg)) {
                        apps[pkg]?.players?.clear()
                    }
                }

                // B. Update active apps
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
                            AppState(pkgName, pkgName, ImageBitmap(1,1), initialVolume = 1f)
                        }
                    }

                    val newPlayerList = mutableListOf<PlayerEntry>()

                    playerConfigPairs.forEach { (proxy, cfg) ->
                        // Get the ID via reflection
                        val currentId = try {
                            getPlayerInterfaceIdMethod?.invoke(cfg) as? Int
                        } catch (_: Exception) { -1 }

                        // Compare using the ID
                        val existingPlayer = appState.players.find { entry ->
                            val entryId = try {
                                getPlayerInterfaceIdMethod?.invoke(entry.config) as? Int
                            } catch (_: Exception) { -2 }

                            // Match if IDs are valid and equal
                            entryId != null && currentId != null && entryId == currentId
                        }

                        if (existingPlayer != null) {
                            // CASE 1: Existing Player -> Update reference only
                            newPlayerList.add(existingPlayer.copy(config = cfg, proxy = proxy))
                        } else {
                            // CASE 2: New Player -> Apply Volume
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

    // It must have both 'config' and 'proxy'
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
