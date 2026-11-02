package com.classsoftwarehub.android.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.classsoftwarehub.android.data.model.AppSettings
import com.classsoftwarehub.android.data.model.ThemeMode
import com.classsoftwarehub.android.data.model.UpdateChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "csh_settings")

class SettingsRepository(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val updateChannelKey = stringPreferencesKey("update_channel")
    private val downloadDirKey = stringPreferencesKey("download_dir_uri")
    private val randomNamesKey = stringPreferencesKey("random_names")
    private val randomListsKey = stringPreferencesKey("random_lists")

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[themeModeKey]
                ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: ThemeMode.SYSTEM,
            dynamicColorEnabled = prefs[dynamicColorKey] ?: true,
            updateChannel = prefs[updateChannelKey]
                ?.let { name -> UpdateChannel.entries.firstOrNull { it.name == name } }
                ?: UpdateChannel.STABLE,
            downloadDirUri = prefs[downloadDirKey],
            randomNameList = prefs[randomNamesKey] ?: "",
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[dynamicColorKey] = enabled }
    }

    suspend fun setUpdateChannel(channel: UpdateChannel) {
        context.settingsDataStore.edit { it[updateChannelKey] = channel.name }
    }

    suspend fun setDownloadDirUri(uri: String?) {
        context.settingsDataStore.edit { prefs ->
            if (uri.isNullOrBlank()) prefs.remove(downloadDirKey) else prefs[downloadDirKey] = uri
        }
    }

    /** 抽取工具的多份名单（JSON 文本，由抽取页负责编解码） */
    suspend fun randomListsJson(): String? = context.settingsDataStore.data.first()[randomListsKey]

    suspend fun setRandomListsJson(json: String) {
        context.settingsDataStore.edit { it[randomListsKey] = json }
    }
}
