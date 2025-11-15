package io.github.legandy.volumemanager.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONObject

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
        val CLOSE_OVERLAY_ON_BACK = booleanPreferencesKey("close_overlay_on_back")
        val LAST_APP_VOLUMES = stringPreferencesKey("last_app_volumes")
    }

    val showOverlayOnVolumeKey: Flow<Boolean> = ds.data.map { it[Keys.SHOW_OVERLAY_ON_VOLUME] ?: true }.distinctUntilChanged()
    val showOverlayOnLockscreen: Flow<Boolean> = ds.data.map { it[Keys.SHOW_OVERLAY_ON_LOCKSCREEN] ?: false }.distinctUntilChanged()
    val overlayTimeout: Flow<Int> = ds.data.map { it[Keys.OVERLAY_TIMEOUT] ?: 4000 }.distinctUntilChanged()
    val themeMode: Flow<ThemeMode> = ds.data.map { preferences -> preferences[Keys.THEME]?.let { themeName -> ThemeMode.valueOf(themeName) } ?: ThemeMode.SYSTEM }.distinctUntilChanged()
    val appFilterMode: Flow<AppFilterMode> = ds.data.map { preferences -> preferences[Keys.APP_FILTER_MODE]?.let { filterModeName -> AppFilterMode.valueOf(filterModeName) } ?: AppFilterMode.SHOW_ALL }.distinctUntilChanged()
    val appBlacklist: Flow<Set<String>> = ds.data.map { it[Keys.APP_BLACKLIST] ?: emptySet() }.distinctUntilChanged()
    val appWhitelist: Flow<Set<String>> = ds.data.map { it[Keys.APP_WHITELIST] ?: emptySet() }.distinctUntilChanged()
    val closeOverlayOnBack: Flow<Boolean> = ds.data.map { it[Keys.CLOSE_OVERLAY_ON_BACK] ?: true }.distinctUntilChanged()

    val lastAppVolumes: Flow<Map<String, Float>> = ds.data.map { preferences ->
        val jsonString = preferences[Keys.LAST_APP_VOLUMES] ?: "{}"
        parseVolumeMap(jsonString)
    }.distinctUntilChanged()

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

    suspend fun setLastAppVolume(packageName: String, volume: Float) = ds.edit { preferences ->
        val currentJsonString = preferences[Keys.LAST_APP_VOLUMES] ?: "{}"
        val currentMap = parseVolumeMap(currentJsonString).toMutableMap()
        currentMap[packageName] = volume
        preferences[Keys.LAST_APP_VOLUMES] = serializeVolumeMap(currentMap)
    }

    private fun parseVolumeMap(jsonString: String): Map<String, Float> {
        return try {
            if (jsonString.isBlank() || jsonString == "{}") return emptyMap()
            val jsonObject = JSONObject(jsonString)
            jsonObject.keys().asSequence().associateWith { key ->
                jsonObject.getString(key).toFloatOrNull() ?: 0f
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun serializeVolumeMap(map: Map<String, Float>): String {
        return JSONObject(map.mapValues { it.value.toString() }).toString()
    }
}