package io.github.legandy.volumemixerpanel.di

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppVolumesDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SetupPreferencesDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppSettingsDataStore

private val Context.appVolumesDataStore by preferencesDataStore(name = "app_volumes")
private val Context.setupPreferencesDataStore by preferencesDataStore(name = "setup_preferences")
private val Context.appSettingsDataStore by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    @AppVolumesDataStore
    fun provideAppVolumesDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.appVolumesDataStore
    }

    @Provides
    @Singleton
    @SetupPreferencesDataStore
    fun provideSetupPreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.setupPreferencesDataStore
    }

    @Provides
    @Singleton
    @AppSettingsDataStore
    fun provideAppSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.appSettingsDataStore
    }

    @Provides
    @Singleton
    fun provideAudioManager(@ApplicationContext context: Context): AudioManager {
        return context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    @Provides
    @Singleton
    fun providePackageManager(@ApplicationContext context: Context): PackageManager {
        return context.packageManager
    }
}
