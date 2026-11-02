package com.classsoftwarehub.android.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.classsoftwarehub.android.data.local.dao.DownloadTaskDao
import com.classsoftwarehub.android.data.local.entity.DownloadTaskEntity
import com.classsoftwarehub.android.data.local.toModel
import com.classsoftwarehub.android.data.model.DownloadSource
import com.classsoftwarehub.android.data.model.DownloadStatus
import com.classsoftwarehub.android.data.model.DownloadSummary
import com.classsoftwarehub.android.data.model.DownloadTask
import com.classsoftwarehub.android.data.model.Software
import com.classsoftwarehub.android.download.DownloadNotifications
import com.classsoftwarehub.android.download.DownloadService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 下载引擎：
 * - Room 记录任务，前台服务（DownloadService）保证后台下载不被中断
 * - OkHttp 流式写入 + Range 断点续传（.part 临时文件）
 * - 完成/失败更新数据库并发送通知；支持取消、重试、清理
 */
class DownloadRepository(
    private val context: Context,
    private val dao: DownloadTaskDao,
    private val settingsRepository: SettingsRepository,
    private val notifications: DownloadNotifications,
) {

    companion object {
        private const val USER_AGENT = "ClassSoftwareHub-Android/1.0 (Android)"
        private const val PROGRESS_INTERVAL_MS = 500L
        const val MAX_PARALLEL_DOWNLOADS = 2
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val calls = ConcurrentHashMap<Long, Call>()
    private val started = AtomicBoolean(false)
    private val serviceRunning = AtomicBoolean(false)

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val allTasks: Flow<List<DownloadTask>> =
        dao.observeAll().map { list -> list.map { it.toModel() } }

    val activeCount: Flow<Int> = dao.observeActiveCount()

    private val _summary = MutableStateFlow(DownloadSummary())
    val summary: StateFlow<DownloadSummary> = _summary.asStateFlow()

    /** 应用启动时调用一次：监听数据库中的待办任务并调度执行 */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        notifications.ensureChannels()
        scope.launch {
            dao.observeActive().collect { entities ->
                // 清理已不在进行中的任务协程
                val activeIds = entities.map { it.id }.toSet()
                jobs.keys.filter { it !in activeIds }.forEach { id -> jobs.remove(id) }

                // 调度新的待办任务（并发上限内）
                var running = jobs.size
                entities
                    .filter { it.status == DownloadStatus.PENDING.name && jobs[it.id] == null }
                    .forEach { entity ->
                        if (running < MAX_PARALLEL_DOWNLOADS) {
                            val job = scope.launch { runTask(entity.id) }
                            jobs[entity.id] = job
                            running += 1
                        }
                    }

                val active = entities.filter {
                    it.status == DownloadStatus.PENDING.name || it.status == DownloadStatus.DOWNLOADING.name
                }
                _summary.value = DownloadSummary(
                    activeCount = active.size,
                    overallProgress = if (active.isEmpty()) 1f else active
                        .map { if (it.totalBytes > 0) (it.downloadedBytes.toFloat() / it.totalBytes) else 0f }
                        .average().toFloat(),
                    currentFileName = active.firstOrNull()?.fileName,
                )
                updateServiceState(active.isNotEmpty())
            }
        }
    }

    fun onServiceStopped() {
        serviceRunning.set(false)
    }

    /** 加入下载队列；同一软件同一来源若已在下载或已完成则直接返回已有任务 */
    suspend fun enqueue(software: Software, source: DownloadSource): Long = withContext(Dispatchers.IO) {
        val existing = dao.findExisting(software.id, source.url)
        if (existing != null) return@withContext existing.id
        val now = System.currentTimeMillis()
        val taskId = dao.insert(
            DownloadTaskEntity(
                softwareId = software.id,
                softwareName = software.name,
                sourceName = source.name,
                sourceUrl = source.url,
                fileName = guessFileName(source.url, software),
                filePath = null,
                fileUri = null,
                totalBytes = 0L,
                downloadedBytes = 0L,
                status = DownloadStatus.PENDING.name,
                error = null,
                createdAt = now,
                updatedAt = now,
            )
        )
        startServiceIfPossible()
        taskId
    }

    suspend fun cancel(id: Long) = withContext(Dispatchers.IO) {
        calls.remove(id)?.cancel()
        jobs.remove(id)?.cancel()
        dao.updateStatus(id, DownloadStatus.CANCELED.name, null, System.currentTimeMillis())
    }

    suspend fun retry(id: Long) = withContext(Dispatchers.IO) {
        val task = dao.getById(id) ?: return@withContext
        val status = DownloadStatus.fromName(task.status)
        if (status == DownloadStatus.FAILED || status == DownloadStatus.CANCELED) {
            dao.updateStatus(id, DownloadStatus.PENDING.name, null, System.currentTimeMillis())
            startServiceIfPossible()
        }
    }

    suspend fun remove(id: Long) = withContext(Dispatchers.IO) {
        calls.remove(id)?.cancel()
        jobs.remove(id)?.cancel()
        dao.delete(id)
    }

    suspend fun clearCompleted() = withContext(Dispatchers.IO) { dao.clearCompleted() }

    suspend fun clearFinished() = withContext(Dispatchers.IO) { dao.clearFinished() }

    // ---------------- 内部实现 ----------------

    private fun now(): Long = System.currentTimeMillis()

    private suspend fun runTask(id: Long) {
        val task = dao.getById(id) ?: return
        val dir = File(context.getExternalFilesDir(null), "downloads").apply { mkdirs() }
        val finalFile = File(dir, task.fileName)
        val partFile = File(dir, "${task.fileName}.part")

        try {
            dao.updateStatus(id, DownloadStatus.DOWNLOADING.name, null, now())

            val alreadyComplete = !partFile.exists() && finalFile.exists() && finalFile.length() > 0
            if (alreadyComplete) {
                dao.markCompleted(id, finalFile.absolutePath, null, finalFile.length(), now())
                dao.getById(id)?.let { notifications.notifyCompleted(it.toModel()) }
                return
            }

            val existingBytes = if (partFile.exists()) partFile.length() else 0L
            val requestBuilder = Request.Builder()
                .url(task.sourceUrl)
                .header("User-Agent", USER_AGENT)
            if (existingBytes > 0L) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
            }
            val call = client.newCall(requestBuilder.build())
            calls[id] = call

            try {
                call.execute().use { response ->
                    if (!response.isSuccessful && response.code != 416) {
                        throw IOException("服务器返回 HTTP ${response.code}")
                    }
                    val body = response.body
                    if (response.code == 416) {
                        // .part 已完整，直接进入完成流程
                        if (body != null) body.close()
                    } else if (body != null) {
                        val append = response.code == 206
                        val startOffset = if (append) existingBytes else 0L
                        val contentLength = body.contentLength()
                        val totalBytes = if (append) startOffset + contentLength else contentLength

                        RandomAccessFile(partFile, "rw").use { raf ->
                            if (append) {
                                raf.seek(startOffset)
                            } else {
                                raf.setLength(0)
                            }
                            var downloaded = startOffset
                            var lastUpdate = 0L
                            body.byteStream().use { input ->
                                val buffer = ByteArray(128 * 1024)
                                while (true) {
                                    coroutineContext.ensureActive()
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    raf.write(buffer, 0, read)
                                    downloaded += read
                                    val ts = System.currentTimeMillis()
                                    if (ts - lastUpdate > PROGRESS_INTERVAL_MS) {
                                        lastUpdate = ts
                                        dao.updateProgress(id, downloaded, if (totalBytes > 0) totalBytes else downloaded, ts)
                                    }
                                }
                            }
                            dao.updateProgress(
                                id,
                                downloaded,
                                if (totalBytes > 0) totalBytes else downloaded,
                                now(),
                            )
                        }
                    }
                }
            } finally {
                calls.remove(id)
            }

            // 落地：.part → 正式文件
            if (finalFile.exists()) finalFile.delete()
            if (!partFile.renameTo(finalFile)) {
                partFile.copyTo(finalFile, overwrite = true)
                partFile.delete()
            }

            // 若设置了自定义保存目录（SAF），复制一份过去并记录 URI
            var savedUri: String? = null
            val settings = settingsRepository.current()
            if (!settings.downloadDirUri.isNullOrBlank()) {
                savedUri = copyToTree(settings.downloadDirUri, finalFile)
            }

            dao.markCompleted(id, finalFile.absolutePath, savedUri, finalFile.length(), now())
            dao.getById(id)?.let { notifications.notifyCompleted(it.toModel()) }
        } catch (t: Throwable) {
            if (t is CancellationException || !coroutineContext.isActive) {
                // 已取消：状态由 cancel()/remove() 写入
            } else {
                dao.updateStatus(
                    id,
                    DownloadStatus.FAILED.name,
                    t.message ?: t.javaClass.simpleName,
                    now(),
                )
            }
        } finally {
            jobs.remove(id)
        }
    }

    private fun copyToTree(treeUri: String, file: File): String? = runCatching {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return null
        var target = tree.findFile(file.name)
        if (target == null) {
            target = tree.createFile("application/octet-stream", file.name)
        }
        val uri = target?.uri ?: return null
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        }
        uri.toString()
    }.getOrNull()

    private fun guessFileName(url: String, software: Software): String {
        val rawSegment = runCatching { Uri.parse(url).lastPathSegment }.getOrNull() ?: ""
        val decoded = runCatching { URLDecoder.decode(rawSegment, "UTF-8") }.getOrDefault(rawSegment)
        val cleaned = decoded
            .substringBefore('?')
            .replace(Regex("[\\\\/:*?\"<>|\\s]"), "_")
            .trim('_')
        val invalid = Regex("[\\\\/:*?\"<>|]")
        if (cleaned.isNotEmpty() && cleaned.contains('.')) return cleaned
        val extension = when {
            url.contains(".msi", true) -> "msi"
            url.contains(".zip", true) -> "zip"
            url.contains(".7z", true) -> "7z"
            url.contains(".rar", true) -> "rar"
            url.contains(".x64", true) -> "exe"
            url.contains(".exe", true) -> "exe"
            else -> "bin"
        }
        val base = "${software.name}-${software.version}".replace(invalid, "_")
        return "$base.$extension"
    }

    private fun updateServiceState(hasActive: Boolean) {
        if (hasActive) {
            startServiceIfPossible()
        } else if (serviceRunning.get()) {
            serviceRunning.set(false)
            runCatching { context.stopService(Intent(context, DownloadService::class.java)) }
        }
    }

    private fun startServiceIfPossible() {
        if (serviceRunning.compareAndSet(false, true)) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
            }.onFailure {
                serviceRunning.set(false)
            }
        }
    }
}
