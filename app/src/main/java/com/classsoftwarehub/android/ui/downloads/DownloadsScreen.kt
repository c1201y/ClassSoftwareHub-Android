package com.classsoftwarehub.android.ui.downloads

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.classsoftwarehub.android.appContainer
import com.classsoftwarehub.android.data.model.DownloadStatus
import com.classsoftwarehub.android.data.model.DownloadTask
import com.classsoftwarehub.android.ui.components.EmptyState
import com.classsoftwarehub.android.ui.components.FilterPill
import com.classsoftwarehub.android.ui.components.GlassCard
import com.classsoftwarehub.android.ui.components.ScreenHeader
import com.classsoftwarehub.android.ui.icons.FluentIcons
import com.classsoftwarehub.android.ui.main.bottomBarClearance
import com.classsoftwarehub.android.util.FileOpener
import com.classsoftwarehub.android.util.Formatters

/** 下载管理：进行中 / 已完成 / 失败（一级页面） */
@Composable
fun DownloadsScreen() {
    val context = LocalContext.current
    val container = remember { context.appContainer }
    val viewModel: DownloadsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DownloadsViewModel(container.downloadRepository) }
        },
    )
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val bottomClearance = bottomBarClearance()

    val active = tasks.filter { it.isActive }
    val completed = tasks.filter { it.status == DownloadStatus.COMPLETED }
    val failed = tasks.filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELED }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "下载",
            subtitle = "任务会在后台继续，完成后发通知",
            modifier = Modifier.padding(top = 10.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterPill("进行中 ${active.size}", selected = selectedTab == 0, onClick = { selectedTab = 0 })
            FilterPill("已完成 ${completed.size}", selected = selectedTab == 1, onClick = { selectedTab = 1 })
            FilterPill("失败 ${failed.size}", selected = selectedTab == 2, onClick = { selectedTab = 2 })
        }

        Spacer(Modifier.height(10.dp))

        when (selectedTab) {
            0 -> TaskList(
                tasks = active,
                emptyTitle = "暂无进行中的下载",
                emptySubtitle = "在首页选择软件并点击下载后会出现在这里",
                bottomPadding = bottomClearance,
                onCancel = viewModel::cancel,
                onRetry = viewModel::retry,
                onRemove = viewModel::remove,
            )

            1 -> Column(Modifier.fillMaxSize()) {
                if (completed.isNotEmpty()) {
                    ClearRow(text = "清理已完成记录", onClick = viewModel::clearCompleted)
                }
                TaskList(
                    tasks = completed,
                    emptyTitle = "暂无已完成任务",
                    emptySubtitle = "下载完成的文件会保存在这里",
                    bottomPadding = bottomClearance,
                    onCancel = viewModel::cancel,
                    onRetry = viewModel::retry,
                    onRemove = viewModel::remove,
                )
            }

            else -> Column(Modifier.fillMaxSize()) {
                if (failed.isNotEmpty()) {
                    ClearRow(text = "清理失败记录", onClick = viewModel::clearFinished)
                }
                TaskList(
                    tasks = failed,
                    emptyTitle = "没有失败的任务",
                    emptySubtitle = "网络不稳时可以在这里重试",
                    bottomPadding = bottomClearance,
                    onCancel = viewModel::cancel,
                    onRetry = viewModel::retry,
                    onRemove = viewModel::remove,
                )
            }
        }
    }
}

@Composable
private fun ClearRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onClick) {
            Icon(
                Icons.Default.DeleteSweep,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(text)
        }
    }
}

@Composable
private fun TaskList(
    tasks: List<DownloadTask>,
    emptyTitle: String,
    emptySubtitle: String?,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onCancel: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onRemove: (Long) -> Unit,
) {
    if (tasks.isEmpty()) {
        EmptyState(title = emptyTitle, subtitle = emptySubtitle)
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(tasks, key = { it.id }) { task ->
            DownloadTaskCard(
                task = task,
                onCancel = { onCancel(task.id) },
                onRetry = { onRetry(task.id) },
                onRemove = { onRemove(task.id) },
            )
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val statusColor = when (task.status) {
        DownloadStatus.FAILED, DownloadStatus.CANCELED -> MaterialTheme.colorScheme.error
        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
    val statusIcon = when (task.status) {
        DownloadStatus.COMPLETED -> Icons.Default.CheckCircle
        DownloadStatus.FAILED, DownloadStatus.CANCELED -> Icons.Default.ErrorOutline
        DownloadStatus.PENDING -> Icons.Default.HourglassEmpty
        else -> FluentIcons.ArrowDownload
    }

    GlassCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(statusColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.softwareName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${task.sourceName} · ${task.fileName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.10f))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            ) {
                Text(
                    task.status.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                )
            }
        }

        if (task.isActive) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { task.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    buildString {
                        append(Formatters.formatBytes(task.downloadedBytes))
                        if (task.totalBytes > 0) {
                            append(" / ${Formatters.formatBytes(task.totalBytes)}")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (task.totalBytes > 0) {
                    Text(
                        "${(task.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (task.status == DownloadStatus.COMPLETED) {
            Spacer(Modifier.height(6.dp))
            Text(
                "已保存 · ${Formatters.formatBytes(task.totalBytes)}" +
                    if (!task.fileUri.isNullOrBlank()) "（自定义目录）" else "（应用下载目录）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!task.error.isNullOrBlank() && task.status == DownloadStatus.FAILED) {
            Spacer(Modifier.height(6.dp))
            Text(
                task.error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            when (task.status) {
                DownloadStatus.PENDING, DownloadStatus.DOWNLOADING -> {
                    TextButton(onClick = onCancel) { Text("取消") }
                }

                DownloadStatus.FAILED, DownloadStatus.CANCELED -> {
                    TextButton(onClick = onRetry) { Text("重试") }
                    TextButton(onClick = onRemove) { Text("删除") }
                }

                DownloadStatus.COMPLETED -> {
                    TextButton(onClick = {
                        val ok = FileOpener.open(context, task)
                        if (!ok) {
                            Toast.makeText(context, "无法打开文件，可能已被移动或删除", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }) { Text("打开") }
                    TextButton(onClick = onRemove) { Text("删除") }
                }
            }
        }
    }
}
