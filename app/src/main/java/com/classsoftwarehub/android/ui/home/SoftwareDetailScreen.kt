package com.classsoftwarehub.android.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.classsoftwarehub.android.appContainer
import com.classsoftwarehub.android.data.model.DownloadSource
import com.classsoftwarehub.android.data.model.Software
import com.classsoftwarehub.android.ui.components.GlassCard
import com.classsoftwarehub.android.ui.components.MetaChip
import com.classsoftwarehub.android.ui.components.SoftwareIcon
import com.classsoftwarehub.android.ui.icons.FluentIcons
import com.classsoftwarehub.android.util.Formatters
import com.classsoftwarehub.android.util.IconPalette
import kotlinx.coroutines.launch

/** 软件详情页：简介、版本、体积、系统要求与多来源下载 */
@Composable
fun SoftwareDetailScreen(
    softwareId: String,
    onOpenDownloads: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember { context.appContainer }
    val viewModel: SoftwareDetailViewModel = viewModel(
        key = "software-detail-$softwareId",
        factory = viewModelFactory {
            initializer { SoftwareDetailViewModel(softwareId, container.softwareRepository) }
        },
    )
    val software by viewModel.software.collectAsStateWithLifecycle()
    val installed by viewModel.installedMatches.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var checkedOnce by rememberSaveable { mutableStateOf(false) }

    val current = software
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // 色带颜色取自图标主色（同桌面端）；没取到就先顶主题强调色，取到后平滑过渡过去
    var heroPicked by remember(current.iconUrl) { mutableStateOf(IconPalette.peek(current.iconUrl)) }
    LaunchedEffect(current.iconUrl) {
        heroPicked = IconPalette.pick(context, current.iconUrl)
    }
    val heroTint by animateColorAsState(
        targetValue = heroPicked ?: MaterialTheme.colorScheme.primary,
        animationSpec = tween(durationMillis = 260),
        label = "heroTint",
    )

    fun enqueueDownload(source: DownloadSource) {
        scope.launch {
            container.downloadRepository.enqueue(current, source)
            val result = snackbarHostState.showSnackbar("已加入下载队列", actionLabel = "查看")
            if (result == SnackbarResult.ActionPerformed) onOpenDownloads()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            HeroHeader(current, heroTint)

            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(6.dp))

                DetailSection(title = "软件简介") {
                    Text(current.description, style = MaterialTheme.typography.bodyMedium)
                    if (current.tags.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            current.tags.take(6).forEach { tag ->
                                MetaChip(
                                    text = "#$tag",
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                DetailSection(title = "基本信息") {
                    InfoRow("版本", current.version)
                    InfoRow("体积", current.size)
                    InfoRow("系统要求", current.requirements)
                    if (current.updatedAt > 0) {
                        InfoRow("更新日期", Formatters.formatDate(current.updatedAt))
                    }
                }

                Spacer(Modifier.height(12.dp))

                DetailSection(title = "下载来源（${current.sources.size}）") {
                    if (current.sources.isEmpty()) {
                        Text(
                            "暂无可用下载来源，可尝试反馈补充",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        current.sources.forEachIndexed { index, source ->
                            if (index > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp))
                            SourceRow(source = source, onDownload = { enqueueDownload(source) })
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                DetailSection(title = "本机核实") {
                    Text(
                        "检查本机是否已安装同名应用（Android 应用列表与本清单为不同平台软件时仅供参考）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            checkedOnce = true
                            viewModel.checkInstalled(context)
                        },
                        enabled = !checking,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.FactCheck,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (checking) "检测中…" else "开始检测")
                    }
                    if (checkedOnce && !checking) {
                        Spacer(Modifier.height(10.dp))
                        if (installed.isEmpty()) {
                            Text(
                                "未在本机检测到相关应用",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            installed.forEach { app ->
                                Text(
                                    "· ${app.label}  ${app.versionName ?: ""}  (${app.packageName})",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * 顶部色带：照桌面端详情页 —— 满宽、颜色取自图标主色、竖直向下淡出到页面底色，
 * 色带上只留图标 / 名称 / 分类与版本，不放下载按钮（下方有完整的下载来源）。
 */
@Composable
private fun HeroHeader(software: Software, tint: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(IconPalette.heroGradient(tint))
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 38.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SoftwareIcon(software, Modifier.size(72.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    software.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        software.category.label,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "v${software.version} · ${software.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    GlassCard {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SourceRow(source: DownloadSource, onDownload: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Link,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(source.name, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(1.dp))
            Text(
                source.url,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onDownload,
            shape = RoundedCornerShape(13.dp),
        ) {
            Icon(FluentIcons.ArrowDownload, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text("下载")
        }
    }
}
