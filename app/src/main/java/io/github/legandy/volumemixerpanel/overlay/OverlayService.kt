package io.github.legandy.volumemixerpanel.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.legandy.volumemixerpanel.core.MyApplication
import io.github.legandy.volumemixerpanel.core.VolumeManager
import io.github.legandy.volumemixerpanel.settings.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.legandy.volumemixerpanel.actions.ACTION_SHOW_OVERLAY
import io.github.legandy.volumemixerpanel.actions.ACTION_HIDE_OVERLAY
import io.github.legandy.volumemixerpanel.actions.ACTION_TOGGLE_OVERLAY
import android.view.WindowInsets
import androidx.compose.ui.unit.dp

class OverlayService : AccessibilityService() {
    companion object {
        private const val TAG = "VolumeMixerPanel.Service"
    }

    private val windowManager: WindowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val audioManager: AudioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private val keyguardManager: KeyguardManager by lazy { getSystemService(KEYGUARD_SERVICE) as KeyguardManager }

    private val volumeManager: VolumeManager by lazy { MyApplication.volumeManager }
    private val settingsDataStore: SettingsDataStore by lazy { MyApplication.settings }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isOverlayVisible by mutableStateOf(false)
    private var view: View? = null
    private var idleJob: Job? = null

    private var cachedShowOnKey = true
    private var cachedShowOnLock = false
    private var cachedTimeout = 3000
    private var cachedCloseOnBack = true

    private var serviceLifecycleOwner: ServiceLifecycleOwner? = null
    private var navBarHeight by mutableStateOf(0.dp) // State to hold nav height

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch { settingsDataStore.showOverlayOnVolumeKey.collect { cachedShowOnKey = it } }
        serviceScope.launch { settingsDataStore.showOverlayOnLockscreen.collect { cachedShowOnLock = it } }
        serviceScope.launch { settingsDataStore.overlayTimeout.collect { cachedTimeout = it } }
        serviceScope.launch { settingsDataStore.closeOverlayOnBack.collect { cachedCloseOnBack = it } }
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
            val shouldShowOverlay = !(keyguardManager.isKeyguardLocked && !cachedShowOnLock)

            if (event.action == KeyEvent.ACTION_DOWN) {
                if (!cachedShowOnKey) {
                    return false
                }
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
        serviceLifecycleOwner?.destroy()
        serviceLifecycleOwner = null
    }

    private fun pauseIdleTimer() {
        idleJob?.cancel()
        Log.d(TAG, "Idle timer paused.")
    }

    private fun resumeIdleTimer() {
        idleJob?.cancel()
        if (cachedTimeout > 0) {
            idleJob = serviceScope.launch {
                delay(cachedTimeout.toLong())
                hideView()
            }
        }
        Log.d(TAG, "Idle timer resumed. Timeout: $cachedTimeout")
    }

    private fun resetIdleTimer() {
        resumeIdleTimer()
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun createView(): View = ComposeView(this).apply {
        serviceLifecycleOwner = ServiceLifecycleOwner()
        setViewTreeLifecycleOwner(serviceLifecycleOwner)
        setViewTreeSavedStateRegistryOwner(serviceLifecycleOwner)
        setViewTreeViewModelStoreOwner(serviceLifecycleOwner)
        serviceLifecycleOwner?.resume()

        // 1. LISTENER: Calculate the actual height of the navigation bar/pill
        setOnApplyWindowInsetsListener { view, insets ->
            val navInsets = insets.getInsets(WindowInsets.Type.navigationBars())
            val density = view.resources.displayMetrics.density
            navBarHeight = (navInsets.bottom / density).dp // Convert px to dp

            // Return CONSUMED so the view knows we handled it
            WindowInsets.CONSUMED
        }

        setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                hideView()
                true
            } else {
                false
            }
        }

        setContent {
            val overlayViewModel: OverlayViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = OverlayViewModelFactory(application))
            OverlayScreen(
                overlayViewModel = overlayViewModel,
                isOverlayVisible = isOverlayVisible,
                onOverlayHidden = ::onOverlayHidden,
                hideView = ::hideView,
                resetTimer = ::resetIdleTimer,
                pauseTimer = ::pauseIdleTimer,
                resumeTimer = ::resumeIdleTimer,
                volumeManager = volumeManager,
                settingsDataStore = settingsDataStore,
                bottomPadding = navBarHeight
            )
        }
    }

    private fun createLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        y = 0
    }



    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}

private class ServiceLifecycleOwner : SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val _viewModelStore = ViewModelStore()
    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }
    fun resume() { lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        _viewModelStore.clear()
    }
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = _viewModelStore
}