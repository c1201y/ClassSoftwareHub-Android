package com.classsoftwarehub.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classsoftwarehub.android.data.model.AppSettings
import com.classsoftwarehub.android.data.model.ThemeMode
import com.classsoftwarehub.android.data.model.UpdateChannel
import com.classsoftwarehub.android.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun setUpdateChannel(channel: UpdateChannel) {
        viewModelScope.launch { settingsRepository.setUpdateChannel(channel) }
    }

    fun setDownloadDir(uri: String?) {
        viewModelScope.launch { settingsRepository.setDownloadDirUri(uri) }
    }
}
