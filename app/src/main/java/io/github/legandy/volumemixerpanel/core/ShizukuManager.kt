package io.github.legandy.volumemixerpanel.core

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.joor.Reflect
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

@SuppressLint("PrivateApi")
class ShizukuManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "VolumeMixerPanel.ShizukuManager"
        private const val SHIZUKU_REQ_CODE = 42

        private fun getShizukuService(name: String, type: String): Any {
            val binder = SystemServiceHelper.getSystemService(name)
            val wrapper = ShizukuBinderWrapper(binder)
            return Reflect.onClass("$type${'$'}Stub").call("asInterface", wrapper).get()
        }
    }

    private var started = false

    var shizukuActivityManager: Reflect? = null; private set
    var shizukuAudioManager: Reflect? = null; private set
    var shizukuNotificationManager: Reflect? = null; private set

    var shizukuReady by mutableStateOf(false); private set
    var shizukuPermission by mutableStateOf(false); private set

    var onShizukuReady: (() -> Unit)? = null

    val permissionManager: PermissionManager = PermissionManager(context) { shizukuNotificationManager }

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

    fun start() {
        if (started) return

        try {
            val audioService = getShizukuService(Context.AUDIO_SERVICE, "android.media.IAudioService")
            shizukuAudioManager = Reflect.on(audioService)
            Reflect.onClass(AudioManager::class.java).set("sService", audioService)

            shizukuActivityManager = Reflect.on(getShizukuService(Context.ACTIVITY_SERVICE, "android.app.IActivityManager"))
            shizukuNotificationManager = Reflect.on(getShizukuService(Context.NOTIFICATION_SERVICE, "android.app.INotificationManager"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject Shizuku services", e)
            return
        }

        started = true
        onShizukuReady?.invoke()
    }

    fun executeShizukuCommand(command: String): Int? {
        if (!shizukuReady || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Shizuku not ready or permission not granted. Cannot execute command: $command")
            return null
        }
        return try {
            val process = Reflect.onClass(Shizuku::class.java)
                .call("newProcess", arrayOf("sh", "-c", command), null, null)
                .get<Process>()
            process.waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "Exception executing Shizuku command: $command", e)
            null
        }
    }
}
