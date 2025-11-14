package io.github.legandy.volumemanager.appsettings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
    