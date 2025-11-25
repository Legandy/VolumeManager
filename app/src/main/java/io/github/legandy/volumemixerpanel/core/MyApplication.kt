package io.github.legandy.volumemixerpanel.core

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import io.github.legandy.volumemixerpanel.app.appSettingsDataStore
import io.github.legandy.volumemixerpanel.settings.SettingsDataStore
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuProvider

private val Context.appVolumesDataStore by preferencesDataStore(name = "app_volumes")
private val Context.setupPreferencesDataStore by preferencesDataStore(name = "setup_preferences")

class MyApplication : Application() {

    companion object {
        private lateinit var instance: MyApplication

        val settings: SettingsDataStore by lazy {
            SettingsDataStore(instance.appSettingsDataStore)
        }

        val shizukuManager: ShizukuManager by lazy {
            ShizukuManager(instance.applicationContext)
        }

        val volumeManager: VolumeManager by lazy {
            VolumeManager(
                instance.applicationContext,
                shizukuManager,
                instance.appVolumesDataStore
            )
        }
        
        val permissionManager: PermissionManager by lazy {
            shizukuManager.permissionManager
        }

        val setupDataStore by lazy { instance.setupPreferencesDataStore }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        shizukuManager.onShizukuReady = { volumeManager.start() }
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        try {
            ShizukuProvider.enableMultiProcessSupport(true)
            HiddenApiBypass.addHiddenApiExemptions("")
        } catch (_: Throwable) {}
    }
}