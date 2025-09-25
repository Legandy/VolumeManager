package io.github.legandy.volumemanager

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class ThemeMode { LIGHT, DARK, SYSTEM }
enum class AppFilterMode { SHOW_ALL, BLACKLIST, WHITELIST }

class SettingsDataStore(private val ds: DataStore<Preferences>) {

    private object Keys {
        val SHOW_OVERLAY_ON_VOLUME = booleanPreferencesKey("show_overlay_on_volume")
        val SHOW_OVERLAY_ON_LOCKSCREEN = booleanPreferencesKey("show_overlay_on_lockscreen")
        val OVERLAY_TIMEOUT = intPreferencesKey("overlay_timeout")
        val THEME = stringPreferencesKey("theme_mode")
        val APP_FILTER_MODE = stringPreferencesKey("app_filter_mode")
        val APP_BLACKLIST = stringSetPreferencesKey("app_blacklist")
        val APP_WHITELIST = stringSetPreferencesKey("app_whitelist")
        // NEW: Key for back gesture setting
        val CLOSE_OVERLAY_ON_BACK = booleanPreferencesKey("close_overlay_on_back")
    }

    val showOverlayOnVolumeKey: Flow<Boolean> = ds.data.map { it[Keys.SHOW_OVERLAY_ON_VOLUME] ?: true }.distinctUntilChanged()
    val showOverlayOnLockscreen: Flow<Boolean> = ds.data.map { it[Keys.SHOW_OVERLAY_ON_LOCKSCREEN] ?: false }.distinctUntilChanged()
    val overlayTimeout: Flow<Int> = ds.data.map { it[Keys.OVERLAY_TIMEOUT] ?: 4000 }.distinctUntilChanged()
    val themeMode: Flow<ThemeMode> = ds.data.map { it[Keys.THEME]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.SYSTEM }.distinctUntilChanged()
    val appFilterMode: Flow<AppFilterMode> = ds.data.map { it[Keys.APP_FILTER_MODE]?.let { AppFilterMode.valueOf(it) } ?: AppFilterMode.SHOW_ALL }.distinctUntilChanged()
    val appBlacklist: Flow<Set<String>> = ds.data.map { it[Keys.APP_BLACKLIST] ?: emptySet() }.distinctUntilChanged()
    val appWhitelist: Flow<Set<String>> = ds.data.map { it[Keys.APP_WHITELIST] ?: emptySet() }.distinctUntilChanged()
    val closeOverlayOnBack: Flow<Boolean> = ds.data.map { it[Keys.CLOSE_OVERLAY_ON_BACK] ?: true }.distinctUntilChanged()

    suspend fun setShowOverlayOnVolumeKey(v: Boolean) = ds.edit { it[Keys.SHOW_OVERLAY_ON_VOLUME] = v }
    suspend fun setShowOverlayOnLockscreen(v: Boolean) = ds.edit { it[Keys.SHOW_OVERLAY_ON_LOCKSCREEN] = v }
    suspend fun setOverlayTimeout(ms: Int) = ds.edit { it[Keys.OVERLAY_TIMEOUT] = ms }
    suspend fun setThemeMode(mode: ThemeMode) = ds.edit { it[Keys.THEME] = mode.name }
    suspend fun setAppFilterMode(mode: AppFilterMode) = ds.edit { it[Keys.APP_FILTER_MODE] = mode.name }
    suspend fun addToBlacklist(packageName: String) = ds.edit { val set = it[Keys.APP_BLACKLIST] ?: emptySet(); it[Keys.APP_BLACKLIST] = set + packageName }
    suspend fun removeFromBlacklist(packageName: String) = ds.edit { val set = it[Keys.APP_BLACKLIST] ?: emptySet(); it[Keys.APP_BLACKLIST] = set - packageName }
    suspend fun addToWhitelist(packageName: String) = ds.edit { val set = it[Keys.APP_WHITELIST] ?: emptySet(); it[Keys.APP_WHITELIST] = set + packageName }
    suspend fun removeFromWhitelist(packageName: String) = ds.edit { val set = it[Keys.APP_WHITELIST] ?: emptySet(); it[Keys.APP_WHITELIST] = set - packageName }
    suspend fun setCloseOverlayOnBack(v: Boolean) = ds.edit { it[Keys.CLOSE_OVERLAY_ON_BACK] = v }
}