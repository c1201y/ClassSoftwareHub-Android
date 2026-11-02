package com.classsoftwarehub.android.ui.submit

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classsoftwarehub.android.data.model.DownloadItemDraft
import com.classsoftwarehub.android.data.model.FeedbackDraft
import com.classsoftwarehub.android.data.model.SoftwareSubmitDraft
import com.classsoftwarehub.android.data.remote.SubmitClient
import com.classsoftwarehub.android.data.remote.SubmitPayload
import com.classsoftwarehub.android.data.repository.DraftRepository
import com.classsoftwarehub.android.data.repository.SoftwareRepository
import com.classsoftwarehub.android.util.IssueUrlBuilder
import com.classsoftwarehub.android.util.crypto.AgeEncryption
import com.classsoftwarehub.android.util.PickedUpload
import com.classsoftwarehub.android.util.Uploads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** 上传状态（图标 / 软件包共用一套文案） */
sealed interface UploadState {

    /** 没有正在进行的上传 */
    data object Idle : UploadState

    /** 直传 OSS 中：[sent]/[total] 字节 */
    data class Working(val sent: Long, val total: Long) : UploadState {
        val fraction: Float
            get() = if (total > 0) (sent.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    /** 已上传；[orphanMinutes] 分钟内不提交会被自动回收 */
    data class Done(val orphanMinutes: Int) : UploadState

    data class Failed(val message: String) : UploadState
}

/** 提交阶段（软件提交 / 反馈共用） */
sealed interface SubmitPhase {

    data object Idle : SubmitPhase

    /** 加密 + 网络请求中 */
    data object Working : SubmitPhase

    /** 服务端已受理（此时表单已清空） */
    data class Success(val message: String) : SubmitPhase

    /** 服务端明确拒绝 */
    data class Rejected(val message: String) : SubmitPhase

    /** 所有入口都不通：可改用浏览器提预填 Issue */
    data class Unreachable(val message: String, val fallbackUrl: String?) : SubmitPhase
}

/** 反馈页「涉及软件」下拉项 */
data class FeedbackAppOption(val id: String, val name: String)

/**
 * 提交软件 / 问题反馈：表单状态、草稿持久化、age 联系方式加密、OSS 直传、Worker 提交。
 *
 * 与桌面端行为对齐：
 *   · 联系方式只在内存里加密成 age 密文后离开本机（服务端也拒收明文）；
 *   · 上传成功要先记住「等待提交」的语义（orphanMinutes 内不提交会被回收）；
 *   · 所有入口都不通时，拼一份 GitHub 预填 Issue 作为兜底（密文联系信息，非明文）。
 */
class SubmitViewModel(
    private val app: Application,
    private val submitClient: SubmitClient,
    private val draftRepository: DraftRepository,
    private val softwareRepository: SoftwareRepository,
    private val json: Json,
) : ViewModel() {

    private val _softwareDraft = MutableStateFlow(SoftwareSubmitDraft())
    val softwareDraft: StateFlow<SoftwareSubmitDraft> = _softwareDraft.asStateFlow()

    private val _feedbackDraft = MutableStateFlow(FeedbackDraft())
    val feedbackDraft: StateFlow<FeedbackDraft> = _feedbackDraft.asStateFlow()

    private val _softwarePhase = MutableStateFlow<SubmitPhase>(SubmitPhase.Idle)
    val softwarePhase: StateFlow<SubmitPhase> = _softwarePhase.asStateFlow()

    private val _feedbackPhase = MutableStateFlow<SubmitPhase>(SubmitPhase.Idle)
    val feedbackPhase: StateFlow<SubmitPhase> = _feedbackPhase.asStateFlow()

    private val _iconUpload = MutableStateFlow<UploadState>(UploadState.Idle)
    val iconUpload: StateFlow<UploadState> = _iconUpload.asStateFlow()

    private val _downloadUploads = MutableStateFlow<Map<Long, UploadState>>(emptyMap())
    val downloadUploads: StateFlow<Map<Long, UploadState>> = _downloadUploads.asStateFlow()

    /** 反馈页可选「涉及软件」（取本机已同步的清单） */
    val feedbackApps: StateFlow<List<FeedbackAppOption>> = softwareRepository.observeAll()
        .map { list -> list.map { FeedbackAppOption(it.id, it.name) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var softwareSaveJob: Job? = null
    private var feedbackSaveJob: Job? = null
    private var iconJob: Job? = null
    private val downloadJobs = mutableMapOf<Long, Job>()

    init {
        loadDrafts()
    }

    // ── 草稿加载与持久化 ──

    private fun loadDrafts() {
        viewModelScope.launch {
            draftRepository.load(DraftRepository.TYPE_SOFTWARE_SUBMIT)?.let { raw ->
                runCatching { json.decodeFromString(SoftwareSubmitDraft.serializer(), raw) }
                    .getOrNull()
                    ?.let { _softwareDraft.value = normalizeSoftwareDraft(it) }
            }
            draftRepository.load(DraftRepository.TYPE_FEEDBACK)?.let { raw ->
                runCatching { json.decodeFromString(FeedbackDraft.serializer(), raw) }
                    .getOrNull()
                    ?.let { _feedbackDraft.value = it }
            }
        }
    }

    /** 兼容旧草稿：分类 key 洗白 + 下载项补齐稳定的列表 key */
    private fun normalizeSoftwareDraft(draft: SoftwareSubmitDraft): SoftwareSubmitDraft {
        val category = if (SoftwareSubmitDraft.CATEGORIES.any { it.key == draft.category }) draft.category else ""
        val downloads = draft.downloads.mapIndexed { index, item -> item.copy(key = (index + 1).toLong()) }
        return draft.copy(
            category = category,
            downloads = downloads.ifEmpty { listOf(DownloadItemDraft(key = 1)) },
        )
    }

    fun updateSoftwareDraft(transform: (SoftwareSubmitDraft) -> SoftwareSubmitDraft) {
        _softwareDraft.value = transform(_softwareDraft.value)
        softwareSaveJob?.cancel()
        softwareSaveJob = viewModelScope.launch {
            delay(500)
            draftRepository.save(
                DraftRepository.TYPE_SOFTWARE_SUBMIT,
                json.encodeToString(SoftwareSubmitDraft.serializer(), _softwareDraft.value),
            )
        }
    }

    fun updateFeedbackDraft(transform: (FeedbackDraft) -> FeedbackDraft) {
        _feedbackDraft.value = transform(_feedbackDraft.value)
        feedbackSaveJob?.cancel()
        feedbackSaveJob = viewModelScope.launch {
            delay(500)
            draftRepository.save(
                DraftRepository.TYPE_FEEDBACK,
                json.encodeToString(FeedbackDraft.serializer(), _feedbackDraft.value),
            )
        }
    }

    /** 下载项：按 key 局部更新 */
    fun updateDownloadItem(key: Long, transform: (DownloadItemDraft) -> DownloadItemDraft) {
        updateSoftwareDraft { draft ->
            draft.copy(downloads = draft.downloads.map { if (it.key == key) transform(it) else it })
        }
    }

    fun addDownloadItem() {
        updateSoftwareDraft { draft ->
            val nextKey = (draft.downloads.maxOfOrNull { it.key } ?: 0L) + 1
            draft.copy(downloads = draft.downloads + DownloadItemDraft(key = nextKey))
        }
    }

    fun removeDownloadItem(key: Long) {
        cancelDownloadUpload(key)
        _downloadUploads.value = _downloadUploads.value - key
        updateSoftwareDraft { draft ->
            val remaining = draft.downloads.filterNot { it.key == key }
            draft.copy(downloads = remaining.ifEmpty { listOf(DownloadItemDraft(key = 1)) })
        }
    }

    fun clearSoftwareDraft() {
        cancelIconUpload()
        downloadJobs.values.forEach { it.cancel() }
        downloadJobs.clear()
        _downloadUploads.value = emptyMap()
        _softwareDraft.value = SoftwareSubmitDraft()
        viewModelScope.launch { draftRepository.clear(DraftRepository.TYPE_SOFTWARE_SUBMIT) }
    }

    fun clearFeedbackDraft() {
        _feedbackDraft.value = FeedbackDraft()
        viewModelScope.launch { draftRepository.clear(DraftRepository.TYPE_FEEDBACK) }
    }

    // ── OSS 直传 ──

    /** 上传图标：先缩到 128×128 PNG（失败则原图），再走 purpose=icon 直传 */
    fun uploadIcon(picked: PickedUpload) {
        if (picked.size > Uploads.ICON_INPUT_MAX_BYTES) {
            _iconUpload.value = UploadState.Failed(
                "图片不能超过 ${Uploads.humanSize(Uploads.ICON_INPUT_MAX_BYTES)}，请换一张小一些的",
            )
            return
        }

        iconJob?.cancel()
        iconJob = viewModelScope.launch {
            _iconUpload.value = UploadState.Working(0, picked.size.coerceAtLeast(0))
            val prepared = withContext(Dispatchers.IO) {
                Uploads.materialize(app, Uploads.shrinkIcon(app, picked))
            }

            val result = submitClient.upload(prepared, purpose = "icon") { sent, total ->
                _iconUpload.value = UploadState.Working(sent, total)
            }

            when (result) {
                is SubmitClient.UploadOutcome.Done -> {
                    updateSoftwareDraft { it.copy(icon = result.url) }
                    _iconUpload.value = UploadState.Done(result.orphanMinutes)
                }

                is SubmitClient.UploadOutcome.Failed -> _iconUpload.value = UploadState.Failed(result.message)
            }
        }
    }

    fun cancelIconUpload() {
        iconJob?.cancel()
        iconJob = null
        _iconUpload.value = UploadState.Idle
    }

    /** 上传软件包：purpose=file，成功后把 `oss://键` 写进该下载项的直链并锁住 */
    fun uploadDownloadFile(key: Long, picked: PickedUpload) {
        downloadJobs[key]?.cancel()
        downloadJobs[key] = viewModelScope.launch {
            _downloadUploads.value = _downloadUploads.value + (key to UploadState.Working(0, picked.size.coerceAtLeast(0)))
            val prepared = withContext(Dispatchers.IO) { Uploads.materialize(app, picked) }

            var lastEmit = 0L
            val result = submitClient.upload(prepared, purpose = "file") { sent, total ->
                // 大文件按 ~0.5% 粒度刷新即可，避免每 64 KB 都推一次状态
                val step = (total / 200).coerceAtLeast(256 * 1024L)
                if (sent >= total || sent - lastEmit >= step) {
                    lastEmit = sent
                    _downloadUploads.value = _downloadUploads.value + (key to UploadState.Working(sent, total))
                }
            }

            when (result) {
                is SubmitClient.UploadOutcome.Done -> {
                    updateDownloadItem(key) { it.copy(url = result.url) }
                    _downloadUploads.value = _downloadUploads.value + (key to UploadState.Done(result.orphanMinutes))
                }

                is SubmitClient.UploadOutcome.Failed -> {
                    _downloadUploads.value = _downloadUploads.value + (key to UploadState.Failed(result.message))
                }
            }
        }
    }

    fun cancelDownloadUpload(key: Long) {
        downloadJobs.remove(key)?.cancel()
        _downloadUploads.value = _downloadUploads.value + (key to UploadState.Idle)
    }

    // ── 提交 ──

    fun submitSoftware() {
        if (_softwarePhase.value is SubmitPhase.Working) return
        val draft = _softwareDraft.value

        viewModelScope.launch {
            val cipher = if (draft.contact.isBlank()) {
                ""
            } else {
                val result = runCatching { encryptContact(draft.contact) }
                result.getOrElse {
                    _softwarePhase.value = SubmitPhase.Rejected("联系方式加密失败，请重试后提交")
                    return@launch
                }
            }

            SubmitPayload.validateSoftware(draft, cipher)?.let {
                _softwarePhase.value = SubmitPhase.Rejected(it)
                return@launch
            }

            _softwarePhase.value = SubmitPhase.Working
            when (val outcome = submitClient.post("/api/submit", SubmitPayload.buildSoftware(draft, cipher))) {
                null -> _softwarePhase.value = SubmitPhase.Unreachable(
                    message = "连不上提交服务，请检查网络后重试；也可以改用浏览器提交（预填工单、联系方式保持密文）",
                    fallbackUrl = buildSoftwareFallback(draft, cipher),
                )

                is SubmitClient.PostOutcome.Accepted -> {
                    val message = outcome.message.ifBlank { "已提交，审核通过后就会上架。" }
                    clearSoftwareDraft()
                    _softwarePhase.value = SubmitPhase.Success(message)
                }

                is SubmitClient.PostOutcome.Rejected -> _softwarePhase.value = SubmitPhase.Rejected(
                    outcome.error.ifBlank { "提交被拒绝了，请稍后重试" },
                )
            }
        }
    }

    fun submitFeedback() {
        if (_feedbackPhase.value is SubmitPhase.Working) return
        val draft = _feedbackDraft.value

        viewModelScope.launch {
            SubmitPayload.validateFeedback(draft)?.let {
                _feedbackPhase.value = SubmitPhase.Rejected(it)
                return@launch
            }

            val cipher = if (draft.contact.isBlank()) {
                ""
            } else {
                val result = runCatching { encryptContact(draft.contact) }
                result.getOrElse {
                    _feedbackPhase.value = SubmitPhase.Rejected("联系方式加密失败，请重试后提交")
                    return@launch
                }
            }

            _feedbackPhase.value = SubmitPhase.Working
            when (val outcome = submitClient.post("/api/feedback", SubmitPayload.buildFeedback(draft, cipher))) {
                null -> _feedbackPhase.value = SubmitPhase.Unreachable(
                    message = "连不上提交服务，请检查网络后重试；也可以改用浏览器提交（预填工单、联系方式保持密文）",
                    fallbackUrl = buildFeedbackFallback(draft, cipher),
                )

                is SubmitClient.PostOutcome.Accepted -> {
                    val message = outcome.message.ifBlank { "反馈已提交，感谢你的支持。" }
                    clearFeedbackDraft()
                    _feedbackPhase.value = SubmitPhase.Success(message)
                }

                is SubmitClient.PostOutcome.Rejected -> _feedbackPhase.value = SubmitPhase.Rejected(
                    outcome.error.ifBlank { "提交被拒绝了，请稍后重试" },
                )
            }
        }
    }

    /** 关闭提交结果提示（成功后表单已清空，失败保留现场） */
    fun acknowledgeSoftwareResult() {
        _softwarePhase.value = SubmitPhase.Idle
    }

    fun acknowledgeFeedbackResult() {
        _feedbackPhase.value = SubmitPhase.Idle
    }

    private suspend fun encryptContact(plain: String): String =
        withContext(Dispatchers.Default) { AgeEncryption.encryptToArmor(plain.trim()) }

    // ── 兜底：GitHub 预填 Issue（联系方式用密文，绝不外发明文） ──

    internal fun buildSoftwareFallback(draft: SoftwareSubmitDraft, contactCipher: String): String {
        val body = buildString {
            appendLine("## 软件 ID")
            appendLine(draft.id)
            appendLine()
            appendLine("## 软件名称")
            appendLine(draft.name)
            appendLine()
            appendLine("## 分类")
            appendLine(SoftwareSubmitDraft.categoryLabel(draft.category))
            appendLine()
            if (draft.tagline.isNotBlank()) {
                appendLine("## 一句话简介")
                appendLine(draft.tagline)
                appendLine()
            }
            if (draft.description.isNotBlank()) {
                appendLine("## 详细介绍")
                appendLine(draft.description)
                appendLine()
            }
            if (draft.version.isNotBlank()) {
                appendLine("## 版本号")
                appendLine(draft.version)
                appendLine()
            }
            if (draft.system.isNotBlank()) {
                appendLine("## 系统限制")
                appendLine(draft.system)
                appendLine()
            }
            draft.downloads.filter { it.url.isNotBlank() }.forEachIndexed { index, item ->
                appendLine("## 下载项 ${index + 1}")
                if (item.platform.isNotBlank()) appendLine("- 平台：${item.platform}")
                if (item.size.isNotBlank()) appendLine("- 体积：${item.size}")
                appendLine("- 地址：${item.url}")
                appendLine()
            }
            appendLine("## 联系方式（age 密文，仅审核可解）")
            appendLine(contactCipher)
            appendLine()
            appendLine("---")
            appendLine(IssueUrlBuilder.deviceInfo())
        }
        return IssueUrlBuilder.softwareSubmissionUrl("【软件提交】${draft.name}", body)
    }

    internal fun buildFeedbackFallback(draft: FeedbackDraft, contactCipher: String): String {
        val kindLabel = if (draft.kind == "report") "报告问题" else "提出建议"
        val subKindLabel = if (draft.kind == "report") {
            when (draft.subKind) {
                "interaction" -> "逻辑交互"
                "link" -> "链接失效"
                else -> "其他问题"
            }
        } else {
            ""
        }
        val body = buildString {
            appendLine("## 反馈类型")
            appendLine(if (subKindLabel.isNotEmpty()) "$kindLabel · $subKindLabel" else kindLabel)
            appendLine()
            if (draft.appId.isNotBlank()) {
                appendLine("## 涉及软件")
                appendLine(draft.appId)
                appendLine()
            }
            appendLine("## 标题")
            appendLine(draft.title)
            appendLine()
            appendLine("## 详细描述")
            appendLine(draft.detail)
            appendLine()
            if (contactCipher.isNotEmpty()) {
                appendLine("## 联系方式（age 密文，仅审核可解）")
                appendLine(contactCipher)
                appendLine()
            }
            appendLine("---")
            appendLine(IssueUrlBuilder.deviceInfo())
        }
        return IssueUrlBuilder.feedbackUrl("【反馈 · $kindLabel】${draft.title}", body)
    }
}
