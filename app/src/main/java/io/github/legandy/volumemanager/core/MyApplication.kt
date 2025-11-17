package io.github.legandy.volumemanager.core

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import io.github.legandy.volumemanager.app.appSettingsDataStore
import io.github.legandy.volumemanager.settings.SettingsDataStore
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

        val manager: ShizukuManager by lazy {
            ShizukuManager(
                instance.applicationContext,
                instance.appVolumesDataStore
            )
        }

        val setupDataStore by lazy {
            instance.setupPreferencesDataStore
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        try {
            ShizukuProvider.enableMultiProcessSupport(true)
            HiddenApiBypass.addHiddenApiExemptions("")
        } catch (_: Throwable) {}
    }
}