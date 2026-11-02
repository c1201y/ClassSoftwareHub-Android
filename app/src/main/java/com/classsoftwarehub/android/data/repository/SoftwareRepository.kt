package com.classsoftwarehub.android.data.repository

import android.content.Context
import com.classsoftwarehub.android.data.local.dao.SoftwareDao
import com.classsoftwarehub.android.data.local.toEntity
import com.classsoftwarehub.android.data.local.toSoftware
import com.classsoftwarehub.android.data.model.DefaultPlatformTabs
import com.classsoftwarehub.android.data.model.PlatformTab
import com.classsoftwarehub.android.data.model.Software
import com.classsoftwarehub.android.data.remote.ClassSoftwareHubApi
import com.classsoftwarehub.android.data.remote.RepoCatalogSource
import com.classsoftwarehub.android.data.remote.RepoFetch
import com.classsoftwarehub.android.data.remote.dto.SoftwareDto
import com.classsoftwarehub.android.data.remote.dto.toSoftwareOrNull
import com.classsoftwarehub.android.util.KeyValueStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 清单刷新结果 */
sealed interface RefreshResult {
    /** 远程刷新成功 */
    data class Success(val count: Int) : RefreshResult

    /**
     * 沿用本地缓存。
     * @param verified false = 还在本地时效内、本次没问过网络（不宜对用户宣称"已是最新"）
     */
    data class NoChange(val count: Int, val verified: Boolean = true) : RefreshResult

    /** 远程失败，已退回内置目录 */
    data class Offline(val count: Int) : RefreshResult

    /** 远程失败且本地已有缓存，继续使用缓存 */
    data class Failed(val message: String?) : RefreshResult
}

class SoftwareRepository(
    private val context: Context,
    private val dao: SoftwareDao,
    private val api: ClassSoftwareHubApi,
    private val repoSource: RepoCatalogSource,
    private val json: Json,
    private val prefs: KeyValueStore,
) {

    /** 操作系统平台清单（仓库 platforms.json），软件库顶部平台大分类用 */
    private val _platforms = MutableStateFlow(readPlatforms())
    val platforms: StateFlow<List<PlatformTab>> = _platforms.asStateFlow()

    fun observeAll(): Flow<List<Software>> =
        dao.observeAll().map { list -> list.map { it.toSoftware(json) } }

    fun observeById(id: String): Flow<Software?> =
        dao.observeById(id).map { it?.toSoftware(json) }

    /**
     * 刷新软件清单，链路（对齐桌面端的"仓库即权威"口径）：
     * 1. 本地缓存时效内 → 直接用，不发请求（手动刷新可强制绕过）；
     * 2. 站点仓库镜像（自建 gh 反代 / jsDelivr / gh-proxy，手机可达）—— 提交号未变则直接跳过；
     * 3. 站点接口 api.classsoftwarehub.cn/api/software（兜底，接口目前 404，仅保留通道）；
     * 4. 都失败：本地为空 → 载入内置目录；本地有缓存 → 继续用缓存。
     */
    suspend fun refresh(force: Boolean = false): RefreshResult = withContext(Dispatchers.IO) {
        // 首次启动先把内置目录落库：界面立刻有内容，远程拉到后再覆盖
        seedIfEmpty()

        when (val repo = repoSource.fetchCatalog(hasLocalData = dao.count() > 0, force = force)) {
            is RepoFetch.Fetched -> {
                storePlatforms(repo.platforms)
                if (repo.software.isNotEmpty()) {
                    dao.replaceAll(repo.software.mapIndexed { index, item -> item.toEntity(json, index) })
                    return@withContext RefreshResult.Success(repo.software.size)
                }
            }
            is RepoFetch.UpToDate -> return@withContext RefreshResult.NoChange(
                dao.count(),
                verified = !repo.fromCache,
            )
            is RepoFetch.Failed -> Unit
        }

        try {
            val remote = api.getCatalog()
            val models = remote.mapNotNull { it.toSoftwareOrNull() }
            if (models.isEmpty()) error("远程清单为空")
            dao.replaceAll(models.mapIndexed { index, item -> item.toEntity(json, index) })
            RefreshResult.Success(models.size)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val seeded = seedIfEmpty()
            if (seeded > 0) {
                RefreshResult.Offline(seeded)
            } else {
                RefreshResult.Failed(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    /** 首次启动 / 离线时，将打包在内置资源中的目录写入数据库 */
    suspend fun seedIfEmpty(): Int = withContext(Dispatchers.IO) {
        if (dao.count() > 0) return@withContext 0
        val seed = loadSeed()
        if (seed.isEmpty()) return@withContext 0
        dao.replaceAll(seed.mapIndexed { index, item -> item.toEntity(json, index) })
        seed.size
    }

    private fun loadSeed(): List<Software> = runCatching {
        val text = context.assets.open("catalog.json").bufferedReader().use { it.readText() }
        json.decodeFromString<List<SoftwareDto>>(text)
            .mapNotNull { it.toSoftwareOrNull() }
    }.getOrDefault(emptyList())

    private fun readPlatforms(): List<PlatformTab> {
        val raw = prefs.getString(KEY_PLATFORMS) ?: return DefaultPlatformTabs
        return runCatching {
            json.decodeFromString(ListSerializer(PlatformTab.serializer()), raw)
        }.getOrDefault(emptyList()).ifEmpty { DefaultPlatformTabs }
    }

    private fun storePlatforms(list: List<PlatformTab>) {
        if (list.isEmpty()) return
        prefs.putString(KEY_PLATFORMS, json.encodeToString(ListSerializer(PlatformTab.serializer()), list))
        _platforms.value = list
    }

    private companion object {
        const val KEY_PLATFORMS = "catalog_platforms"
    }
}
