package com.classsoftwarehub.android.ui.submit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.classsoftwarehub.android.appContainer
import com.classsoftwarehub.android.data.model.DownloadItemDraft
import com.classsoftwarehub.android.data.model.SoftwareSubmitDraft
import com.classsoftwarehub.android.data.remote.SubmitClient
import com.classsoftwarehub.android.data.remote.SubmitPayload
import com.classsoftwarehub.android.ui.components.GlassCard
import com.classsoftwarehub.android.util.IssueUrlBuilder
import com.classsoftwarehub.android.util.PickedUpload
import com.classsoftwarehub.android.util.Uploads
import kotlin.math.roundToInt

/**
 * 提交软件：与桌面端 SubmitPage / 网页端 SubmitPage.vue 对齐的完整表单。
 *
 * 三个出口都在自建 Worker 上（无需登录 GitHub）：
 *   · 图标      → /api/oss-sign(purpose=icon) → PUT OSS → 回填 {入口}/api/icon?k=…
 *   · 软件包    → /api/oss-sign(purpose=file) → PUT OSS → 回填 oss://…
 *   · 整个表单  → POST /api/submit（联系方式为 age 密文）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SubmitSoftwareScreen() {
    val context = LocalContext.current
    val container = remember { context.appContainer }
    val viewModel: SubmitViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SubmitViewModel(
                    app = container.application,
                    submitClient = container.submitClient,
                    draftRepository = container.draftRepository,
                    softwareRepository = container.softwareRepository,
                    json = container.json,
                )
            }
        },
    )

    val draft by viewModel.softwareDraft.collectAsStateWithLifecycle()
    val phase by viewModel.softwarePhase.collectAsStateWithLifecycle()
    val iconUpload by viewModel.iconUpload.collectAsStateWithLifecycle()
    val downloadUploads by viewModel.downloadUploads.collectAsStateWithLifecycle()

    var uploadTargetKey by remember { mutableStateOf<Long?>(null) }
    var pendingBigUpload by remember { mutableStateOf<Pair<Long, PickedUpload>?>(null) }
    val packagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val key = uploadTargetKey
        uploadTargetKey = null
        if (uri != null && key != null) {
            val picked = Uploads.resolve(context, uri)
            // 超过 1 GB 只提示不阻止（对齐网页端 UPLOAD_WARN_BYTES）
            if (picked.size >= SubmitClient.UPLOAD_WARN_BYTES) {
                pendingBigUpload = key to picked
            } else {
                viewModel.uploadDownloadFile(key, picked)
            }
        }
    }
    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.uploadIcon(Uploads.resolve(context, uri, "image/*"))
    }

    val busy = phase is SubmitPhase.Working

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GlassCard(contentPadding = PaddingValues(0.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("投稿说明", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "提交会送到站点审核服务（无需登录 GitHub），审核通过后自动上架。联系方式在本地用 age 公钥加密，明文不会离开手机；软件包与图标可以直接传到本站存储。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard("基本信息") {
            OutlinedTextField(
                value = draft.id,
                onValueChange = { value -> viewModel.updateSoftwareDraft { it.copy(id = value.trim()) } },
                label = { Text("软件 ID *") },
                supportingText = { Text("小写字母、数字、连字符，例如 class-tools") },
                singleLine = true,
                trailingIcon = {
                    val suggested = SubmitPayload.suggestId(draft.name)
                    if (draft.id.isBlank() && suggested.isNotEmpty()) {
                        TextButton(onClick = { viewModel.updateSoftwareDraft { it.copy(id = suggested) } }) {
                            Text("生成")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = draft.name,
                onValueChange = { value -> viewModel.updateSoftwareDraft { it.copy(name = value) } },
                label = { Text("软件名称 *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("分类 *", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftwareSubmitDraft.CATEGORIES.forEach { category ->
                    FilterChip(
                        selected = draft.category == category.key,
                        onClick = { viewModel.updateSoftwareDraft { it.copy(category = category.key) } },
                        label = { Text(category.label) },
                    )
                }
            }

            OutlinedTextField(
                value = draft.tagline,
                onValueChange = { value -> viewModel.updateSoftwareDraft { it.copy(tagline = value) } },
                label = { Text("一句话简介 *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = draft.description,
                onValueChange = { value -> viewModel.updateSoftwareDraft { it.copy(description = value) } },
                label = { Text("详细介绍 *") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard("版本与运行") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = draft.version,
                    onValueChange = { value -> viewModel.updateSoftwareDraft { it.copy(version = value) } },
                    label = { Text("版本号") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draft.size,
                    onValueChange = { value -> viewModel.updateSoftwareDraft { it.copy(size = value) } },
                    label = { Text("体积") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = draft.system,
                onValueChange = { value -> viewModel.updateSoftwareDraft { it.copy(system = value) } },
                label = { Text("系统限制 *") },
                supportingText = { Text("例如：Windows 7 及以上 / Android 8+") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = draft.sort,
                onValueChange = { value -> viewModel.updateSoftwareDraft { it.copy(sort = value) } },
                label = { Text("排序值（可选）") },
                supportingText = { Text("数字越小越靠前，留空由站点排") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard("链接与提示") {
            OutlinedTextField(
                value = draft.website,
                onValueChange = { value -> viewModel.updateSoftwareDraft { it.copy(website = value) } },
                label = { Text("官网地址") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.github,
                onValueChange = { value -> viewModel.updateSoftwareDraft { it.copy(github = value) } },
                label = { Text("GitHub 仓库") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.store,
                onValueChange = { value -> viewModel.updateSoftwareDraft { it.copy(store = value) } },
                label = { Text("应用商店地址") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.notice,
                onValueChange = { value -> viewModel.updateSoftwareDraft { it.copy(notice = value) } },
                label = { Text("提示条（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard("图标") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft.icon,
                    onValueChange = { value -> viewModel.updateSoftwareDraft { it.copy(icon = value) } },
                    label = { Text("图标地址") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = { iconPicker.launch("image/*") },
                    enabled = iconUpload !is UploadState.Working,
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("选择图片")
                }
            }
            if (draft.icon.isNotBlank()) {
                AsyncImage(
                    model = draft.icon,
                    contentDescription = "图标预览",
                    modifier = Modifier.size(44.dp),
                )
            }
            UploadStatusBlock(
                state = iconUpload,
                onCancel = viewModel::cancelIconUpload,
                doneHint = { minutes ->
                    if (minutes > 0) "图标已上传，请在 $minutes 分钟内提交，否则会被自动回收。"
                    else "图标已上传，请尽快提交，否则会被自动回收。"
                },
            )
            Text(
                "会自动压到 128×128 PNG 再上传；没有图标可以留空。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("下载项") {
            Text(
                "至少一条直链。没有自己的网盘就点「上传」，把文件传到本站存储；上传成功后这一行会锁住。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            draft.downloads.forEachIndexed { index, item ->
                DownloadItemEditor(
                    index = index,
                    item = item,
                    uploadState = downloadUploads[item.key] ?: UploadState.Idle,
                    canRemove = draft.downloads.size > 1,
                    onUpdate = { transform -> viewModel.updateDownloadItem(item.key, transform) },
                    onRemove = { viewModel.removeDownloadItem(item.key) },
                    onPickFile = {
                        uploadTargetKey = item.key
                        packagePicker.launch(arrayOf("*/*"))
                    },
                    onCancelUpload = { viewModel.cancelDownloadUpload(item.key) },
                )
            }
            OutlinedButton(onClick = viewModel::addDownloadItem) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("添加下载项")
            }
        }

        SectionCard("联系方式") {
            OutlinedTextField(
                value = draft.contact,
                onValueChange = { value -> viewModel.updateSoftwareDraft { it.copy(contact = value) } },
                label = { Text("联系方式 *") },
                supportingText = { Text("QQ / 邮箱等；本地 age 加密后提交，仅审核可见") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = viewModel::submitSoftware,
                enabled = !busy,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("提交中…")
                } else {
                    Text("提交审核")
                }
            }
            OutlinedButton(
                onClick = viewModel::clearSoftwareDraft,
                enabled = !busy,
                modifier = Modifier.height(48.dp),
            ) {
                Text("清空")
            }
        }

        Text(
            "表单自动存为草稿 · 联系方式加密后提交 · 上传的附件在提交时被认领",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    pendingBigUpload?.let { (key, picked) ->
        AlertDialog(
            onDismissRequest = { pendingBigUpload = null },
            title = { Text("文件比较大") },
            text = {
                Text(
                    "这个文件约 ${Uploads.humanSize(picked.size)}，超过 1 GB。上传可能需要较长时间，请保持网络稳定并停留在本页；确定现在上传吗？",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingBigUpload = null
                    viewModel.uploadDownloadFile(key, picked)
                }) { Text("继续上传") }
            },
            dismissButton = {
                TextButton(onClick = { pendingBigUpload = null }) { Text("取消") }
            },
        )
    }

    SoftwareResultDialog(
        phase = phase,
        onDismiss = viewModel::acknowledgeSoftwareResult,
        onRetry = viewModel::submitSoftware,
        onOpenFallback = { url -> IssueUrlBuilder.open(context, url) },
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(contentPadding = PaddingValues(0.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun DownloadItemEditor(
    index: Int,
    item: DownloadItemDraft,
    uploadState: UploadState,
    canRemove: Boolean,
    onUpdate: ((DownloadItemDraft) -> DownloadItemDraft) -> Unit,
    onRemove: () -> Unit,
    onPickFile: () -> Unit,
    onCancelUpload: () -> Unit,
) {
    val locked = item.url.startsWith("oss://")

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "下载项 ${index + 1}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            if (canRemove) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "删除该下载项")
                }
            }
        }

        OutlinedTextField(
            value = item.platform,
            onValueChange = { value -> onUpdate { it.copy(platform = value) } },
            label = { Text("平台") },
            supportingText = { Text("例如：Windows 10/11 / Windows 7 / 安卓") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = item.size,
                onValueChange = { value -> onUpdate { it.copy(size = value) } },
                label = { Text("体积") },
                singleLine = true,
                modifier = Modifier.weight(0.5f),
            )
            OutlinedTextField(
                value = item.hash,
                onValueChange = { value -> onUpdate { it.copy(hash = value) } },
                label = { Text("校验值（可选）") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = item.url,
                onValueChange = { value -> onUpdate { it.copy(url = value) } },
                label = { Text("下载直链 *") },
                readOnly = locked,
                singleLine = true,
                supportingText = {
                    if (locked) Text("已存到本站存储，提交时自动认领")
                },
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(
                onClick = onPickFile,
                enabled = !locked && uploadState !is UploadState.Working,
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("上传")
            }
        }

        UploadStatusBlock(
            state = uploadState,
            onCancel = onCancelUpload,
            doneHint = { minutes ->
                if (minutes > 0) "已上传，请在 $minutes 分钟内提交，否则会被自动回收。"
                else "已上传，请尽快提交，否则会被自动回收。"
            },
        )
    }
}

@Composable
private fun UploadStatusBlock(
    state: UploadState,
    onCancel: () -> Unit,
    doneHint: (Int) -> String,
) {
    when (state) {
        is UploadState.Working -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { state.fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "上传中 ${(state.fraction * 100).roundToInt()}%" +
                            if (state.total > 0) "（${Uploads.humanSize(state.sent)} / ${Uploads.humanSize(state.total)}）" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancel) { Text("取消") }
                }
            }
        }

        is UploadState.Done -> Text(
            doneHint(state.orphanMinutes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        is UploadState.Failed -> Text(
            state.message,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )

        UploadState.Idle -> Unit
    }
}

@Composable
private fun SoftwareResultDialog(
    phase: SubmitPhase,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenFallback: (String) -> Unit,
) {
    when (phase) {
        is SubmitPhase.Success -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("已提交") },
            text = { Text(phase.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
        )

        is SubmitPhase.Rejected -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("无法提交") },
            text = { Text(phase.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
        )

        is SubmitPhase.Unreachable -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("连不上提交服务") },
            text = { Text(phase.message) },
            confirmButton = {
                TextButton(onClick = {
                    onDismiss()
                    onRetry()
                }) { Text("重试") }
            },
            dismissButton = {
                if (phase.fallbackUrl != null) {
                    TextButton(onClick = {
                        onOpenFallback(phase.fallbackUrl)
                        onDismiss()
                    }) { Text("浏览器提交") }
                } else {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            },
        )

        else -> Unit
    }
}
