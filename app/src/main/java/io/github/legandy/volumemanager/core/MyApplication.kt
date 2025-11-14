package io.github.legandy.volumemanager.core

import android.app.Application
import android.content.Context
import io.github.legandy.volumemanager.app.appSettingsDataStore
import io.github.legandy.volumemanager.settings.SettingsDataStore
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuProvider
import androidx.datastore.preferences.preferencesDataStore

private val Context.appVolumesDataStore by preferencesDataStore(name = "app_volumes")

class MyApplication : Application() {

    companion object {
        private lateinit var instance: MyApplication

        val settings: SettingsDataStore by lazy {
            SettingsDataStore(instance.appSettingsDataStore)
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