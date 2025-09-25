package io.github.legandy.volumemanager

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.ColorMatrix
import android.graphics.PixelFormat
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.legandy.volumemanager.ui.theme.VolumeManagerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class OverlayTab { SYSTEM, APPS }

class OverlayService : AccessibilityService() {
    companion object {
        private const val TAG = "VolumeManager.Service"
        const val ACTION_SHOW_OVERLAY = "io.github.legandy.volumemanager.action.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "io.github.legandy.volumemanager.action.HIDE_OVERLAY"
        const val ACTION_TOGGLE_OVERLAY = "io.github.legandy.volumemanager.action.TOGGLE_OVERLAY"
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
    }

    private val windowManager: WindowManager by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private val audioManager: AudioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val keyguardManager: KeyguardManager by lazy { getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
    private val notificationManager: NotificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val manager: Manager by lazy { MyApplication.manager }
    private val settingsDataStore: SettingsDataStore by lazy { MyApplication.settings }
    private val serviceScope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private var isOverlayVisible by mutableStateOf(false)
    private var view: View? = null
    private var idleJob: Job? = null
    private var volumeUpdateTrigger by mutableIntStateOf(0)
    @Volatile private var cachedShowOnKey: Boolean = true
    @Volatile private var cachedShowOnLock: Boolean = false
    @Volatile private var cachedTimeout: Int = 4000
    @Volatile private var cachedCloseOnBack: Boolean = true

    private val systemStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            volumeUpdateTrigger++
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch { settingsDataStore.showOverlayOnVolumeKey.collect { cachedShowOnKey = it } }
        serviceScope.launch { settingsDataStore.showOverlayOnLockscreen.collect { cachedShowOnLock = it } }
        serviceScope.launch { settingsDataStore.overlayTimeout.collect { cachedTimeout = it } }
        serviceScope.launch { settingsDataStore.closeOverlayOnBack.collect { cachedCloseOnBack = it } }

        val filter = IntentFilter().apply {
            addAction(VOLUME_CHANGED_ACTION)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(systemStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(systemStateReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_OVERLAY -> showView()
            ACTION_HIDE_OVERLAY -> hideView()
            ACTION_TOGGLE_OVERLAY -> if (isOverlayVisible) hideView() else showView()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false
        val isVolumeKey = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

        if (isVolumeKey) {
            if (!cachedShowOnKey) {
                return false
            }

            val shouldShowOverlay = !(keyguardManager.isKeyguardLocked && !cachedShowOnLock)

            if (event.action == KeyEvent.ACTION_DOWN) {
                val direction = if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                audioManager.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, 0)

                if (shouldShowOverlay) {
                    showView()
                }
            }
            return shouldShowOverlay
        }

        if (cachedCloseOnBack && event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN && isOverlayVisible) {
            hideView()
            return true
        }
        return false
    }

    private fun showView() {
        if (view == null) {
            view = createView()
            windowManager.addView(view, createLayoutParams())
        }
        isOverlayVisible = true
        resumeIdleTimer()
    }

    private fun hideView() {
        isOverlayVisible = false
        idleJob?.cancel()
    }

    private fun onOverlayHidden() {
        view?.let {
            windowManager.removeView(it)
            view = null
        }
    }

    private fun pauseIdleTimer() {
        idleJob?.cancel()
        Log.d(TAG, "Idle timer paused.")
    }

    private fun resumeIdleTimer() {
        idleJob?.cancel()
        idleJob = serviceScope.launch {
            delay(cachedTimeout.toLong())
            hideView()
        }
        Log.d(TAG, "Idle timer resumed.")
    }

    private fun resetIdleTimer() {
        resumeIdleTimer()
    }

    private fun getMediaOutputDeviceType(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val device = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }
            return device?.type ?: AudioDeviceInfo.TYPE_UNKNOWN
        }
        return AudioDeviceInfo.TYPE_UNKNOWN
    }

    @SuppressLint("InflateParams")
    private fun createView(): View = ComposeView(this).apply {
        val lifecycleOwner = ServiceLifecycleOwner()
        setViewTreeLifecycleOwner(lifecycleOwner)
        setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        lifecycleOwner.resume()

        setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                hideView()
                true
            } else {
                false
            }
        }

        setContent {
            VolumeManagerTheme {
                AnimatedVisibility(
                    visible = isOverlayVisible,
                    enter = slideInVertically(animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium), initialOffsetY = { -it / 2 }) + fadeIn(animationSpec = spring()),
                    exit = slideOutVertically(animationSpec = spring(stiffness = Spring.StiffnessMedium), targetOffsetY = { -it / 2 }) + fadeOut(animationSpec = spring())
                ) {
                    DisposableEffect(Unit) { onDispose { if (!isOverlayVisible) onOverlayHidden() } }
                    OverlayContent(
                        volumeUpdateTrigger = volumeUpdateTrigger,
                        resetTimer = ::resetIdleTimer,
                        pauseTimer = ::pauseIdleTimer,
                        resumeTimer = ::resumeIdleTimer
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun OverlayContent(
        volumeUpdateTrigger: Int,
        resetTimer: () -> Unit,
        pauseTimer: () -> Unit,
        resumeTimer: () -> Unit
    ) {
        var selectedTab by remember { mutableStateOf(OverlayTab.SYSTEM) }
        val context = LocalContext.current

        val isDndOn by remember(volumeUpdateTrigger) {
            mutableStateOf(notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL)
        }

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
                            OverlayTab.SYSTEM -> SystemVolumeSliders(volumeUpdateTrigger, pauseTimer, resumeTimer)
                            OverlayTab.APPS -> AppVolumeSliders(pauseTimer, resumeTimer)
                        }
                    }
                }

                Divider(modifier = Modifier.padding(top = 8.dp))
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
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                try {
                                    val intent = Intent("android.media.action.SHOW_AUDIO_OUTPUT_SWITCHER")
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                    hideView()
                                } catch (e: ActivityNotFoundException) {
                                    Toast.makeText(context, "Output switcher not available", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Feature requires Android 11+", Toast.LENGTH_SHORT).show()
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
                                Icon(Icons.Filled.Podcasts, "System", modifier = Modifier.size(32.dp))
                            }
                        } else {
                            IconButton(onClick = { selectedTab = OverlayTab.SYSTEM; resetTimer() }) {
                                Icon(Icons.Outlined.Podcasts, "System", modifier = Modifier.size(32.dp))
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        val isAppsSelected = selectedTab == OverlayTab.APPS
                        if (isAppsSelected) {
                            FilledTonalIconButton(onClick = { /* Already selected */ }) {
                                Icon(Icons.Filled.Apps, "Apps", modifier = Modifier.size(32.dp))
                            }
                        } else {
                            IconButton(onClick = { selectedTab = OverlayTab.APPS; resetTimer() }) {
                                Icon(Icons.Outlined.Apps, "Apps", modifier = Modifier.size(32.dp))
                            }
                        }
                    }

                    // Right Group
                    Row {
                        IconButton(onClick = {
                            if (isDndOn) {
                                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                            } else {
                                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                            }
                            resetTimer()
                        }) {
                            Icon(
                                imageVector = if (isDndOn) Icons.Filled.DoNotDisturbOn else Icons.Outlined.DoNotDisturbOn,
                                contentDescription = "Do Not Disturb",
                                tint = if(isDndOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
    private fun SystemVolumeSliders(volumeUpdateTrigger: Int, pauseTimer: () -> Unit, resumeTimer: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StreamSliderRow(AudioManager.STREAM_MUSIC, volumeUpdateTrigger, pauseTimer, resumeTimer)
            StreamSliderRow(AudioManager.STREAM_RING, volumeUpdateTrigger, pauseTimer, resumeTimer)
            StreamSliderRow(AudioManager.STREAM_ALARM, volumeUpdateTrigger, pauseTimer, resumeTimer)
            StreamSliderRow(AudioManager.STREAM_VOICE_CALL, volumeUpdateTrigger, pauseTimer, resumeTimer)
        }
    }

    @Composable
    private fun AppVolumeSliders(pauseTimer: () -> Unit, resumeTimer: () -> Unit) {
        val filterMode by settingsDataStore.appFilterMode.collectAsState(initial = AppFilterMode.SHOW_ALL)
        val blacklist by settingsDataStore.appBlacklist.collectAsState(initial = emptySet())
        val whitelist by settingsDataStore.appWhitelist.collectAsState(initial = emptySet())

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
                        text = "No apps are currently playing audio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeApps.forEach { app -> AppSliderRow(app = app, pauseTimer = pauseTimer, resumeTimer = resumeTimer) }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun StreamSliderRow(streamType: Int, triggerChange: Int, pauseTimer: () -> Unit, resumeTimer: () -> Unit) {
        val maxVolume = remember { try { audioManager.getStreamMaxVolume(streamType) } catch (e: Exception) { 15 } }
        var currentVolume by remember(triggerChange) { mutableIntStateOf(try { audioManager.getStreamVolume(streamType) } catch (e: Exception) { 0 }) }
        val interactionSource = remember { MutableInteractionSource() }
        val isMuted by remember(triggerChange) { mutableStateOf(audioManager.isStreamMute(streamType)) }
        val ringerMode by remember(triggerChange) { mutableStateOf(audioManager.ringerMode) }

        val initialLastVolume = remember(streamType) {
            if (currentVolume > 0) currentVolume else (maxVolume * 0.7).toInt().coerceAtLeast(1)
        }
        var lastKnownVolume by remember(streamType) { mutableStateOf(initialLastVolume) }

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
                            manager.setRingerMode(AudioManager.RINGER_MODE_SILENT)
                            volumeUpdateTrigger++
                        }
                    }
                    AudioManager.RINGER_MODE_SILENT -> {
                        icon = Icons.Default.NotificationsOff
                        sliderEnabled = false
                        onIconClick = {
                            manager.setRingerMode(AudioManager.RINGER_MODE_NORMAL)
                            volumeUpdateTrigger++
                        }
                    }
                    else -> { // RINGER_MODE_NORMAL
                        icon = Icons.Default.RingVolume
                        sliderEnabled = true
                        onIconClick = {
                            manager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE)
                            volumeUpdateTrigger++
                        }
                    }
                }
            }
            AudioManager.STREAM_MUSIC -> {
                name = "Media"
                val deviceType by remember(triggerChange) { mutableStateOf(getMediaOutputDeviceType()) }
                val baseIcon = when (deviceType) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> Icons.Default.BluetoothAudio
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> Icons.Default.Headphones
                    else -> Icons.Default.VolumeUp
                }
                icon = if (isMuted) Icons.Default.VolumeOff else baseIcon
                sliderEnabled = true
                onIconClick = {
                    val direction = if (isMuted) AudioManager.ADJUST_UNMUTE else AudioManager.ADJUST_MUTE
                    audioManager.adjustStreamVolume(streamType, direction, 0)
                    volumeUpdateTrigger++
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
                    audioManager.adjustStreamVolume(streamType, direction, 0)
                    volumeUpdateTrigger++
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
                                manager.setRingerMode(AudioManager.RINGER_MODE_SILENT)
                            } else {
                                audioManager.setStreamVolume(streamType, currentVolume, 0)
                                if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                                    manager.setRingerMode(AudioManager.RINGER_MODE_NORMAL)
                                }
                            }
                        } else {
                            if (sliderEnabled) {
                                audioManager.setStreamVolume(streamType, currentVolume, 0)
                            }
                        }
                    } catch (e: Exception) { Log.e(TAG, "onValueChangeFinished failed", e) }
                    volumeUpdateTrigger++
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
    private fun AppSliderRow(app: Manager.AppState, pauseTimer: () -> Unit, resumeTimer: () -> Unit) {
        val interactionSource = remember { MutableInteractionSource() }
        var lastVolume by remember(app.packageName) { mutableStateOf(if (app.volume > 0.05f) app.volume else 0.7f) }
        val isMuted = app.volume < 0.01f

        LaunchedEffect(app.volume) {
            if (app.volume > 0.05f) {
                lastVolume = app.volume
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
        val colorFilter = if (isMuted) ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }) else null

        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(
                onClick = {
                    if (isMuted) {
                        manager.setAppVolume(app.packageName, lastVolume)
                    } else {
                        manager.setAppVolume(app.packageName, 0f)
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
                onValueChange = { newVol -> manager.setAppVolume(app.packageName, newVol) },
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

    private fun createLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        y = 0
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(systemStateReceiver)
        view?.let { windowManager.removeView(it) }
        serviceScope.cancel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}

private class ServiceLifecycleOwner : SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }
    fun resume() { lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
    fun destroy() { lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY) }
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val lifecycle: Lifecycle get() = lifecycleRegistry
}

