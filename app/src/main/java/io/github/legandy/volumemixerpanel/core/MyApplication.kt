package io.github.legandy.volumemixerpanel.core

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuProvider
import javax.inject.Inject

@HiltAndroidApp
class MyApplication : Application() {

    @Inject
    lateinit var shizukuManager: ShizukuManager

    @Inject
    lateinit var volumeManager: VolumeManager

    override fun onCreate() {
        super.onCreate()
        
        shizukuManager.onShizukuReady = { volumeManager.start() }
        
        // If Shizuku is already ready (sticky listener triggered during lazy init), start manually
        if (shizukuManager.shizukuReady && shizukuManager.shizukuPermission) {
            volumeManager.start()
        }
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        try {
            ShizukuProvider.enableMultiProcessSupport(true)
            HiddenApiBypass.addHiddenApiExemptions("")
        } catch (_: Throwable) {}
    }
}
