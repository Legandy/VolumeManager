package io.github.legandy.volumemanager.appsettings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

// This is the *single* instance of the DataStore for settings
// that will be used throughout the application.
val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
    