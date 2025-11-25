package io.github.legandy.volumemixerpanel.settings

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.legandy.volumemixerpanel.core.MyApplication
import io.github.legandy.volumemixerpanel.core.ShizukuManager
import io.github.legandy.volumemixerpanel.core.VolumeManager
import io.github.legandy.volumemixerpanel.ui.theme.VolumeMixerPanelTheme

class SettingsActivity : ComponentActivity() {

    private lateinit var settingsDataStore: SettingsDataStore
    private val volumeManager: VolumeManager by lazy { MyApplication.volumeManager }
    private val shizukuManager: ShizukuManager by lazy { MyApplication.shizukuManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsDataStore = SettingsDataStore(provideSettingsDataStore(applicationContext))

        setContent {
            VolumeMixerPanelTheme {
                SettingsScreen(
                    settingsDataStore = settingsDataStore,
                    volumeManager = volumeManager,
                    shizukuManager = shizukuManager
                )
            }
        }
    }

    private fun provideSettingsDataStore(context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("settings") }
        )
    }
}
