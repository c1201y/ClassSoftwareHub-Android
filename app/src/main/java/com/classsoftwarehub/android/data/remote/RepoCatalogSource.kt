package com.classsoftwarehub.android.data.remote

import android.content.Context
import com.classsoftwarehub.android.data.model.PlatformTab
import com.classsoftwarehub.android.data.model.Software
import com.classsoftwarehub.android.data.remote.dto.RepoAppDto
import com.classsoftwarehub.android.data.remote.dto.RepoCategoryDto
import com.classsoftwarehub.android.data.remote.dto.RepoPlatformDto
import com.classsoftwarehub.android.data.remote.dto.toSoftwareOrNull
import com.classsoftwarehub.android.util.KeyValueStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

/** 仓库目录镜像取数的结果 */
sealed interface RepoFetch {

    /**
     * 沿用本地缓存。
     * @param fromCache true = 还在时效内、根本没问网络；false = 问过提交号，确认仓库未变。
     */
    data class UpToDate(val fromCache: Boolean) : RepoFetch

    /** 拉到了新清单 */
    data class Fetched(
        val software: List<Software>,
        /** platforms.json 的平台清单；拉不到时为空，沿用本地缓存 */
        val platforms: List<PlatformTab> = emptyList(),
    ) : RepoFetch

    /** 全部入口失败 */
    data class Failed(val message: String) : RepoFetch
}

/**
 * 软件数据源：站点仓库 c1201y/ClassSoftwareHub 的「软件数据/」目录，**全程走国内可达镜像**。
 *
 * 手机大多连不上 GitHub，桌面端的「raw 直连」线路在手机上换成亲和通道：
 *   · 文件内容：自建反代 gh.classsoftwarehub.cn → jsDelivr → gh-proxy / ghfast，记住上次成功的入口；
 *   · 文件清单：gh-proxy / ghfast 代理的 GitHub contents 接口（权威）→ trees 接口 → jsDelivr 数据接口 → 打包清单；
 *   · 快速判定：本地数据还在时效内直接复用，一个请求都不发；过期才问一次分支头提交号。
 */
class RepoCatalogSource(
    private val context: Context,
    private val client: OkHttpClient,
    private val json: Json,
    private val prefs: KeyValueStore,
) {

    companion object {
        private const val OWNER = "c1201y"
        private const val REPO = "ClassSoftwareHub"
        private const val BRANCH = "main"
        private const val DATA_DIR = "软件数据"
        private const val APPS_DIR = "$DATA_DIR/apps"

        /** gh.classsoftwarehub.cn 对非浏览器 UA 一律 403，其余镜像不挑 UA，故整条链路统一用浏览器风格 */
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

        // v4：内容入口换成自建反代优先，重新计一次时效
        private const val KEY_HEAD_SHA = "repo_head_sha_v4"
        private const val KEY_RAW_ENTRY = "repo_raw_entry_v2"
        private const val KEY_REF_ENTRY = "repo_ref_entry"
        private const val KEY_SYNCED_AT = "repo_synced_at_v4"

        /** 一次成功同步能用多久：期间启动只读本地缓存，不再碰网络 */
        private const val SYNC_TTL_MS = 12 * 60 * 60 * 1000L

        /** 分支头接口：镜像代理优先，GitHub 官方兜底（国内多半不通，放最后） */
        private val REF_BASES = listOf(
            "https://gh-proxy.com/https://api.github.com",
            "https://ghfast.top/https://api.github.com",
            "https://api.github.com",
        )

        /** 文件内容模板（%s = 已转义的仓库内路径） */
        private val RAW_TEMPLATES = listOf(
            "https://gh.classsoftwarehub.cn/gh/raw.githubusercontent.com/$OWNER/$REPO/$BRANCH/%s",
            "https://cdn.jsdelivr.net/gh/$OWNER/$REPO@$BRANCH/%s",
            "https://gh-proxy.com/https://raw.githubusercontent.com/$OWNER/$REPO/$BRANCH/%s",
            "https://ghfast.top/https://raw.githubusercontent.com/$OWNER/$REPO/$BRANCH/%s",
        )

        /** 一次性并发上限：别把镜像打爆 */
        private const val FETCH_CONCURRENCY = 6
    }

    /** 打进 APK 的文件清单：所有在线列目录入口都失败时的最后兜底 */
    private val bundledAppPaths: List<String> by lazy {
        runCatching {
            context.assets.open("catalog-index.json").bufferedReader().use { it.readText() }
        }.getOrNull()?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
            .orEmpty()
    }

    /**
     * 拉取最新软件清单。
     * @param hasLocalData 本地已有数据时才允许走「时效内 / 提交号未变 → 跳过」的快路径。
     * @param force 手动刷新：跳过本地时效，仍保留提交号快路径。
     */
    suspend fun fetchCatalog(hasLocalData: Boolean, force: Boolean = false): RepoFetch =
        withContext(Dispatchers.IO) {
            try {
                // 时效内：本地缓存直接用，一个请求都不发
                if (hasLocalData && !force && isCacheFresh()) {
                    return@withContext RepoFetch.UpToDate(fromCache = true)
                }

                // 快路径：本地已有数据且知道上次提交号时，只问一次 ref 接口
                val cachedHead = prefs.getString(KEY_HEAD_SHA)
                var head: String? = null
                if (hasLocalData && !cachedHead.isNullOrBlank()) {
                    head = runCatching { headSha() }.getOrNull()
                    if (head != null && head == cachedHead) {
                        markCacheFresh()
                        return@withContext RepoFetch.UpToDate(fromCache = false)
                    }
                }

                val appPaths = listAppPaths() ?: return@withContext RepoFetch.Failed("无法获取仓库软件清单")
                if (appPaths.isEmpty()) return@withContext RepoFetch.Failed("仓库中未列出软件数据")

                val categoryNames = runCatching { fetchCategories() }.getOrNull()
                    ?.associate { it.key to it.name }
                    ?: emptyMap()
                val platforms = runCatching { fetchPlatforms() }.getOrNull().orEmpty()

                val entries = fetchApps(appPaths, categoryNames)
                if (entries.isEmpty()) return@withContext RepoFetch.Failed("软件数据解析失败")

                // 拉到新数据后把提交号记下；本次没问到就先补一次（刚验证过网络可用）
                val headToStore = head ?: runCatching { headSha() }.getOrNull()
                if (!headToStore.isNullOrBlank()) prefs.putString(KEY_HEAD_SHA, headToStore)
                markCacheFresh()

                RepoFetch.Fetched(
                    software = entries.sortedWith(compareBy({ it.first }, { it.second.name.lowercase() }))
                        .map { it.second },
                    platforms = platforms,
                )
            } catch (t: kotlinx.coroutines.CancellationException) {
                throw t
            } catch (t: Exception) {
                RepoFetch.Failed(t.message ?: t.javaClass.simpleName)
            }
        }

    /** 上次成功同步还在时效内 */
    private fun isCacheFresh(): Boolean {
        val at = prefs.getString(KEY_SYNCED_AT)?.toLongOrNull() ?: return false
        return System.currentTimeMillis() - at in 0 until SYNC_TTL_MS
    }

    private fun markCacheFresh() {
        prefs.putString(KEY_SYNCED_AT, System.currentTimeMillis().toString())
    }

    /** 分支头提交号（可能会话中第一次请求失败后第二次成功，失败不算错） */
    private fun headSha(): String? {
        val apiPath = "/repos/$OWNER/$REPO/git/ref/heads/$BRANCH"
        val start = prefs.getInt(KEY_REF_ENTRY, 0)
        for (step in REF_BASES.indices) {
            val index = (start + step) % REF_BASES.size
            try {
                val text = get(REF_BASES[index] + apiPath)
                prefs.putInt(KEY_REF_ENTRY, index)
                return json.decodeFromString<RefDto>(text).`object`?.sha
            } catch (_: Exception) {
                // 换下一个入口
            }
        }
        return null
    }

    /** 列出「软件数据/apps/」下的 json 文件（仓库内相对路径） */
    private fun listAppPaths(): List<String>? =
        listAppPathsFromContents()
            ?: listAppPathsFromTree()
            ?: listAppPathsFromJsDelivr()
            ?: bundledAppPaths.ifEmpty { null }

    /** 目录接口：一次拿完整目录（当前 130 款） */
    private fun listAppPathsFromContents(): List<String>? {
        val encodedDir = encodePath(APPS_DIR)
        val contentsUrls = listOf(
            "https://gh-proxy.com/https://api.github.com/repos/$OWNER/$REPO/contents/$encodedDir?ref=$BRANCH",
            "https://ghfast.top/https://api.github.com/repos/$OWNER/$REPO/contents/$encodedDir?ref=$BRANCH",
        )
        for (url in contentsUrls) {
            try {
                val entries = json.decodeFromString<List<RepoContentEntry>>(get(url))
                val paths = entries
                    .filter { it.type == "file" && it.path.endsWith(".json") && !it.name.startsWith("_") }
                    .map { it.path }
                if (paths.isNotEmpty()) return paths
            } catch (_: Exception) {
                // 换下一个入口
            }
        }
        return null
    }

    /**
     * git trees 接口：一次请求拿到整棵文件树，目录接口被代理限流 / 截断时兜底。
     * 放在 jsDelivr 前面 —— jsDelivr 的目录数据可能是旧快照（实测只列到 55 个文件），会漏软件。
     */
    private fun listAppPathsFromTree(): List<String>? {
        for (base in REF_BASES) {
            try {
                val tree = json.decodeFromString<RepoTreeDto>(
                    get("$base/repos/$OWNER/$REPO/git/trees/$BRANCH?recursive=1"),
                ).tree
                val paths = tree
                    .filter { it.type == "blob" && it.path.startsWith("$APPS_DIR/") && it.path.endsWith(".json") }
                    .map { it.path }
                    .filter { !it.substringAfterLast('/').startsWith("_") }
                if (paths.isNotEmpty()) return paths
            } catch (_: Exception) {
                // 换下一个入口
            }
        }
        return null
    }

    /** jsDelivr 数据接口兜底（可能是旧快照，排最后） */
    private fun listAppPathsFromJsDelivr(): List<String>? {
        try {
            val listing = json.decodeFromString<JsDelivrListing>(
                get("https://data.jsdelivr.com/v1/packages/gh/$OWNER/$REPO@$BRANCH?structure=flat"),
            )
            val prefix = "/$APPS_DIR/"
            val paths = listing.files
                .map { it.name }
                .filter {
                    it.startsWith(prefix) && it.endsWith(".json") &&
                        !it.substringAfterLast('/').startsWith("_")
                }
                .map { it.removePrefix("/") }
            if (paths.isNotEmpty()) return paths
        } catch (_: Exception) {
            // 全部入口失败
        }
        return null
    }

    /** 并发取回全部 app json 并解析（单个文件失败只跳过该文件） */
    private suspend fun fetchApps(
        paths: List<String>,
        categoryNames: Map<String, String>,
    ): List<Pair<Int, Software>> = coroutineScope {
        val gate = Semaphore(FETCH_CONCURRENCY)
        paths.map { path ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    val text = runCatching { fetchRaw(path) }.getOrNull() ?: return@withPermit null
                    runCatching {
                        val dto = json.decodeFromString<RepoAppDto>(text)
                        dto.toSoftwareOrNull { key -> categoryNames[key] }
                            ?.let { (dto.sort ?: Int.MAX_VALUE) to it }
                    }.getOrNull()
                }
            }
        }.awaitAll().filterNotNull()
    }

    private fun fetchCategories(): List<RepoCategoryDto> =
        json.decodeFromString(fetchRaw("$DATA_DIR/categories.json"))

    /** 平台清单（与桌面版同一个 platforms.json，顺序照搬） */
    private fun fetchPlatforms(): List<PlatformTab> =
        json.decodeFromString<List<RepoPlatformDto>>(fetchRaw("$DATA_DIR/platforms.json"))
            .mapNotNull { dto ->
                val key = dto.key.trim().lowercase()
                if (key.isEmpty()) null else PlatformTab(key, dto.name.trim().ifEmpty { key })
            }

    /** 单个文件内容：raw 模板逐个回退，记住上次成功的入口 */
    private fun fetchRaw(path: String): String {
        val encoded = encodePath(path)
        val start = prefs.getInt(KEY_RAW_ENTRY, 0)
        var last: Exception? = null
        for (step in RAW_TEMPLATES.indices) {
            val index = (start + step) % RAW_TEMPLATES.size
            try {
                val text = get(RAW_TEMPLATES[index].format(encoded))
                prefs.putInt(KEY_RAW_ENTRY, index)
                return text
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("文件内容入口均不可用：$path")
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/vnd.github+json, application/json, */*")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("空响应体")
        }
    }

    /** 仓库内路径逐段转义（中文目录名 → UTF-8 百分号编码），'/' 保留 */
    private fun encodePath(path: String): String =
        path.split('/').joinToString("/") { segment ->
            URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }
}

@Serializable
private data class RefDto(val `object`: RefObject? = null)

@Serializable
private data class RefObject(val sha: String = "")

@Serializable
private data class RepoContentEntry(
    val name: String = "",
    val path: String = "",
    val type: String = "",
)

@Serializable
private data class JsDelivrListing(val files: List<JsDelivrFile> = emptyList())

@Serializable
private data class JsDelivrFile(val name: String = "")

@Serializable
private data class RepoTreeDto(val tree: List<RepoTreeEntry> = emptyList())

@Serializable
private data class RepoTreeEntry(val path: String = "", val type: String = "")
