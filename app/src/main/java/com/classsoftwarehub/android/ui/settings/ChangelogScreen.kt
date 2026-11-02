package com.classsoftwarehub.android.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.classsoftwarehub.android.BuildConfig
import com.classsoftwarehub.android.appContainer
import com.classsoftwarehub.android.data.model.UpdateChannel
import com.classsoftwarehub.android.util.IssueUrlBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Serializable
private data class ChangelogFile(val versions: List<ChangelogEntry> = emptyList())

@Serializable
private data class ChangelogEntry(
    val version: String = "",
    val date: String = "",
    val channel: String = "stable",
    val highlights: List<String> = emptyList(),
)

private data class ReleaseInfo(
    val tag: String,
    val name: String,
    val url: String,
    val publishedAt: String,
)

/**
 * GitHub Releases 接口的候选入口：镜像优先（国内可达），失败再直连。
 * 与目录镜像（RepoCatalogSource）同一套思路。
 */
private val RELEASE_API_MIRRORS = listOf(
    "https://gh-proxy.com/https://api.github.com",
    "https://ghfast.top/https://api.github.com",
    "https://api.github.com",
)

/** 更新日志：支持只看正式版 / 查看全部版本，并可检查上游最新发布 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen() {
    val context = LocalContext.current
    val container = remember { context.appContainer }
    val scope = rememberCoroutineScope()

    var showAll by rememberSaveable { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<ChangelogEntry>>(emptyList()) }
    var checking by remember { mutableStateOf(false) }
    var releaseResult by remember { mutableStateOf<ReleaseInfo?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 镜像逐个试：单次别等太久
    val checkClient = remember {
        container.okHttpClient.newBuilder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    LaunchedEffect(Unit) {
        entries = loadChangelog(context, container.json)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !showAll,
                onClick = { showAll = false },
                label = { Text("正式版") },
            )
            FilterChip(
                selected = showAll,
                onClick = { showAll = true },
                label = { Text("全部版本") },
            )
            Spacer(Modifier.weight(1f))
            if (checking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                TextButton(onClick = {
                    checking = true
                    scope.launch {
                        val includePrerelease =
                            container.settingsRepository.current().updateChannel == UpdateChannel.INSIDER
                        val result = withContext(Dispatchers.IO) {
                            fetchLatestRelease(checkClient, container.json, includePrerelease)
                        }
                        checking = false
                        result.fold(
                            onSuccess = { releaseResult = it },
                            onFailure = { errorMessage = it.message ?: "未知错误" },
                        )
                    }
                }) { Text("检查更新") }
            }
        }

        val filtered = if (showAll) entries else entries.filter { it.channel == "stable" }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(filtered, key = { it.version }) { entry ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "v${entry.version}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (entry.channel == "stable") "正式版" else "Insider",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (entry.channel == "stable") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                },
                            )
                        }
                        Text(
                            entry.date,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        entry.highlights.forEach { line ->
                            Text(
                                "· $line",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    releaseResult?.let { release ->
        AlertDialog(
            onDismissRequest = { releaseResult = null },
            title = { Text("检查更新") },
            text = {
                Text(
                    buildString {
                        appendLine("当前应用版本：${BuildConfig.VERSION_NAME}")
                        appendLine("上游最新版本：${release.tag.ifBlank { release.name }}")
                        if (release.publishedAt.isNotBlank()) {
                            appendLine("发布时间：${release.publishedAt.take(10)}")
                        }
                        appendLine()
                        append("如需最新版本与完整发布说明，可前往 GitHub Releases 查看。")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (release.url.isNotBlank()) {
                        IssueUrlBuilder.openUrl(context, release.url)
                    }
                    releaseResult = null
                }) { Text("前往查看") }
            },
            dismissButton = {
                TextButton(onClick = { releaseResult = null }) { Text("关闭") }
            },
        )
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("检查更新失败") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) { Text("知道了") }
            },
        )
    }
}

private fun loadChangelog(context: Context, json: Json): List<ChangelogEntry> = runCatching {
    val text = context.assets.open("changelog.json").bufferedReader().use { it.readText() }
    json.decodeFromString(ChangelogFile.serializer(), text).versions
}.getOrDefault(emptyList())

private fun fetchLatestRelease(
    client: OkHttpClient,
    json: Json,
    includePrerelease: Boolean,
): Result<ReleaseInfo> {
    var lastError: Throwable? = null
    for (mirror in RELEASE_API_MIRRORS) {
        val attempt = runCatching {
            val request = Request.Builder()
                .url("$mirror/repos/${BuildConfig.GITHUB_REPO}/releases?per_page=20")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "ClassSoftwareHub-Android")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val bodyText = response.body?.string() ?: error("响应为空")
                val releases = json.parseToJsonElement(bodyText).jsonArray
                    .mapNotNull { element -> element as? JsonObject }
                val chosen = releases.firstOrNull { obj ->
                    val draft = obj["draft"]?.jsonPrimitive?.booleanOrNull ?: false
                    val prerelease = obj["prerelease"]?.jsonPrimitive?.booleanOrNull ?: false
                    !draft && (includePrerelease || !prerelease)
                } ?: error("上游仓库暂无可用版本")
                ReleaseInfo(
                    tag = chosen["tag_name"]?.jsonPrimitive?.contentOrNull ?: "",
                    name = chosen["name"]?.jsonPrimitive?.contentOrNull ?: "",
                    url = chosen["html_url"]?.jsonPrimitive?.contentOrNull ?: "",
                    publishedAt = chosen["published_at"]?.jsonPrimitive?.contentOrNull ?: "",
                )
            }
        }
        attempt.onSuccess { return Result.success(it) }
        lastError = attempt.exceptionOrNull()
    }
    return Result.failure(lastError ?: IllegalStateException("检查更新失败"))
}
