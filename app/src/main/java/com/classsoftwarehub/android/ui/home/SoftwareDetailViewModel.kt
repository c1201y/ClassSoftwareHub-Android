package com.classsoftwarehub.android.ui.home

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classsoftwarehub.android.data.model.Software
import com.classsoftwarehub.android.data.repository.SoftwareRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val label: String,
    val packageName: String,
    val versionName: String?,
)

class SoftwareDetailViewModel(
    private val softwareId: String,
    private val repository: SoftwareRepository,
) : ViewModel() {

    val software: StateFlow<Software?> = repository.observeById(softwareId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _installedMatches = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedMatches: StateFlow<List<InstalledAppInfo>> = _installedMatches.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    /** 本机核实：调用 PackageManager 查找与当前软件同名的已安装应用 */
    fun checkInstalled(context: Context) {
        val name = software.value?.name ?: return
        if (_checking.value) return
        viewModelScope.launch {
            _checking.value = true
            _installedMatches.value = withContext(Dispatchers.IO) { queryInstalled(context, name) }
            _checking.value = false
        }
    }

    private fun queryInstalled(context: Context, name: String): List<InstalledAppInfo> {
        val pm = context.packageManager
        val key = name.lowercase().replace(" ", "")
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .mapNotNull { app ->
                val label = runCatching { pm.getApplicationLabel(app).toString() }
                    .getOrDefault(app.packageName)
                val matched = label.contains(name, ignoreCase = true) ||
                    name.contains(label, ignoreCase = true) ||
                    app.packageName.lowercase().replace(".", "").contains(key)
                if (!matched) return@mapNotNull null
                val version = runCatching {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(app.packageName, 0).versionName
                }.getOrNull()
                InstalledAppInfo(label = label, packageName = app.packageName, versionName = version)
            }
            .sortedBy { it.label }
            .take(10)
    }
}
