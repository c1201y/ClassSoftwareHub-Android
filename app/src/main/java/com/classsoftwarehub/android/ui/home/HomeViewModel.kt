package com.classsoftwarehub.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classsoftwarehub.android.data.model.DefaultPlatformKey
import com.classsoftwarehub.android.data.model.DefaultPlatformTabs
import com.classsoftwarehub.android.data.model.PlatformTab
import com.classsoftwarehub.android.data.model.Software
import com.classsoftwarehub.android.data.model.SoftwareCategory
import com.classsoftwarehub.android.data.repository.RefreshResult
import com.classsoftwarehub.android.data.repository.SoftwareRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val query: String = "",
    val category: SoftwareCategory? = null,
    val platforms: List<PlatformTab> = DefaultPlatformTabs,
    /** 选中的平台 key；空串 = 全部平台 */
    val platform: String = DefaultPlatformKey,
    val items: List<Software> = emptyList(),
    val totalCount: Int = 0,
    val refreshing: Boolean = false,
    val message: String? = null,
)

private data class Catalog(
    val all: List<Software>,
    val platforms: List<PlatformTab>,
    val platform: String,
)

class HomeViewModel(
    private val repository: SoftwareRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<SoftwareCategory?>(null)

    /** 移动端默认停在安卓，和桌面版一样按 platforms.json 的平台筛选 */
    private val platform = MutableStateFlow(DefaultPlatformKey)

    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private val catalog = combine(
        repository.observeAll(),
        repository.platforms,
        platform,
    ) { all, platforms, platformKey ->
        Catalog(all, platforms, platformKey)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        catalog,
        query,
        category,
        refreshing,
        message,
    ) { cat, q, c, r, m ->
        // 平台清单更新后选中的平台没了（数据源改过）→ 退回「全部」
        val activePlatform = cat.platform.takeIf { key ->
            key.isNotBlank() && cat.platforms.any { it.key == key }
        }
        val filtered = cat.all.filter { item ->
            val categoryMatched = c == null || item.category == c
            val platformMatched = activePlatform == null || item.platforms.contains(activePlatform)
            val queryMatched = q.isBlank() ||
                item.name.contains(q, ignoreCase = true) ||
                item.description.contains(q, ignoreCase = true) ||
                item.tags.any { it.contains(q, ignoreCase = true) } ||
                item.category.label.contains(q, ignoreCase = true)
            categoryMatched && platformMatched && queryMatched
        }
        HomeUiState(
            query = q,
            category = c,
            platforms = cat.platforms,
            platform = activePlatform ?: "",
            items = filtered,
            totalCount = cat.all.size,
            refreshing = r,
            message = m,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refresh()
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onCategoryChange(value: SoftwareCategory?) {
        category.value = value
    }

    /** key 为空串 = 全部平台 */
    fun onPlatformChange(key: String) {
        platform.value = key
    }

    fun consumeMessage() {
        message.value = null
    }

    /** 手动刷新传 force：跳过本地缓存时效，仍走提交号快路径 */
    fun refresh(force: Boolean = false) {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            message.value = when (val result = repository.refresh(force)) {
                is RefreshResult.Success -> null
                is RefreshResult.NoChange ->
                    if (result.verified) "清单已是最新（${result.count} 款）" else null
                is RefreshResult.Offline -> "网络不可用，已使用内置目录（${result.count} 款）"
                is RefreshResult.Failed -> "刷新失败：${result.message ?: "未知错误"}"
            }
            refreshing.value = false
        }
    }
}
