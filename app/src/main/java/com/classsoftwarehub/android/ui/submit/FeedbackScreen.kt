package com.classsoftwarehub.android.ui.submit

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.classsoftwarehub.android.appContainer
import com.classsoftwarehub.android.data.model.FeedbackOptions
import com.classsoftwarehub.android.ui.components.GlassCard
import com.classsoftwarehub.android.util.IssueUrlBuilder

/**
 * 问题反馈：走站点提交服务 /api/feedback（与桌面端 FeedbackFormPage、网页端 FeedbackPage.vue 对齐）。
 *
 *   · 一级类型：报告问题 / 提出建议；报告问题再选二级类型；
 *   · 「涉及软件」从本机清单里选（可选）；
 *   · 联系方式选填，但一旦填写必须 age 加密（服务端拒收明文）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeedbackScreen() {
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

    val draft by viewModel.feedbackDraft.collectAsStateWithLifecycle()
    val phase by viewModel.feedbackPhase.collectAsStateWithLifecycle()
    val apps by viewModel.feedbackApps.collectAsStateWithLifecycle()

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
                Text("反馈说明", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "反馈会直接送到站点提交服务并自动建立工单（无需登录 GitHub）。联系方式在本地用 age 公钥加密后提交；需要贴截图的复杂问题，建议按首页指引发到 QQ 群相册。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard("反馈类型 *") {
            FeedbackOptions.KINDS.forEach { kind ->
                val selected = draft.kind == kind.key
                Card(
                    onClick = {
                        viewModel.updateFeedbackDraft {
                            it.copy(kind = kind.key, subKind = if (kind.key == "report") it.subKind else "")
                        }
                    },
                    border = BorderStroke(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(kind.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                kind.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        RadioButton(selected = selected, onClick = null)
                    }
                }
            }
        }

        if (draft.kind == "report") {
            SectionCard("问题类型 *") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeedbackOptions.SUB_KINDS.forEach { subKind ->
                        FilterChip(
                            selected = draft.subKind == subKind.key,
                            onClick = { viewModel.updateFeedbackDraft { it.copy(subKind = subKind.key) } },
                            label = { Text(subKind.label) },
                        )
                    }
                }
            }
        }

        SectionCard("涉及软件（可选）") {
            var expanded by remember { mutableStateOf(false) }
            val selectedApp = apps.firstOrNull { it.id == draft.appId }
            val display = selectedApp?.let { "${it.name}（${it.id}）" } ?: "未指定"

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = display,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("反馈的是哪一款软件") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.heightIn(max = 320.dp),
                ) {
                    DropdownMenuItem(
                        text = { Text("未指定") },
                        onClick = {
                            viewModel.updateFeedbackDraft { it.copy(appId = "") }
                            expanded = false
                        },
                    )
                    apps.forEach { app ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${app.name}（${app.id}）",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            onClick = {
                                viewModel.updateFeedbackDraft { it.copy(appId = app.id) }
                                expanded = false
                            },
                        )
                    }
                }
            }
            if (apps.isEmpty()) {
                Text(
                    "本机清单还没同步到软件列表，可以先不指定。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard("填写内容") {
            OutlinedTextField(
                value = draft.title,
                onValueChange = { value ->
                    viewModel.updateFeedbackDraft { it.copy(title = value.take(FeedbackOptions.TITLE_MAX)) }
                },
                label = { Text("标题 *") },
                supportingText = {
                    Text(
                        "${draft.title.length}/${FeedbackOptions.TITLE_MAX}",
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = draft.detail,
                onValueChange = { value ->
                    viewModel.updateFeedbackDraft { it.copy(detail = value.take(FeedbackOptions.DETAIL_MAX)) }
                },
                label = { Text("详细描述 *") },
                supportingText = {
                    Text(
                        "${draft.detail.length}/${FeedbackOptions.DETAIL_MAX}",
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = draft.contact,
                onValueChange = { value -> viewModel.updateFeedbackDraft { it.copy(contact = value) } },
                label = { Text("联系方式（可选）") },
                supportingText = { Text("QQ / 邮箱等；本地 age 加密后提交，仅审核可见") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = viewModel::submitFeedback,
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
                    Text("提交反馈")
                }
            }
            OutlinedButton(
                onClick = viewModel::clearFeedbackDraft,
                enabled = !busy,
                modifier = Modifier.height(48.dp),
            ) {
                Text("清空")
            }
        }

        Text(
            "表单自动存为草稿 · 提交后自动建立工单，结果会在本页弹出提示",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    FeedbackResultDialog(
        phase = phase,
        onDismiss = viewModel::acknowledgeFeedbackResult,
        onRetry = viewModel::submitFeedback,
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
private fun FeedbackResultDialog(
    phase: SubmitPhase,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenFallback: (String) -> Unit,
) {
    when (phase) {
        is SubmitPhase.Success -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("反馈已提交") },
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
