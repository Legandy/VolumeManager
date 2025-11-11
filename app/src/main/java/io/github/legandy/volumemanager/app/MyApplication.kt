package io.github.legandy.volumemanager.app

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import io.github.legandy.volumemanager.core.Manager
import io.github.legandy.volumemanager.settings.SettingsDataStore
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuProvider

private val Context.settingsDataStore by preferencesDataStore(name = "settings")
private val Context.appVolumesDataStore by preferencesDataStore(name = "app_volumes")

class MyApplication : android.app.Application() {

    companion object {
        private lateinit var instance: MyApplication

        val settings: SettingsDataStore by lazy {
            SettingsDataStore(instance.settingsDataStore)
        }

        val manager: Manager by lazy {
            Manager(
                instance.applicationContext,
                instance.appVolumesDataStore
            )
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