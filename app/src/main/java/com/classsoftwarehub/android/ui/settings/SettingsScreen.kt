package com.classsoftwarehub.android.ui.settings

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.classsoftwarehub.android.BuildConfig
import com.classsoftwarehub.android.R
import com.classsoftwarehub.android.appContainer
import com.classsoftwarehub.android.data.model.ThemeMode
import com.classsoftwarehub.android.data.model.UpdateChannel
import com.classsoftwarehub.android.ui.components.GlassCard
import com.classsoftwarehub.android.ui.components.ScreenHeader
import com.classsoftwarehub.android.ui.components.SectionLabel
import com.classsoftwarehub.android.ui.main.bottomBarClearance

/** 设置页：主题、更新通道、下载路径、更新日志与关于 */
@Composable
fun SettingsScreen(
    onOpenChangelog: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember { context.appContainer }
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(container.settingsRepository) }
        },
    )
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var themeDialog by remember { mutableStateOf(false) }
    var channelDialog by remember { mutableStateOf(false) }
    var downloadDirDialog by remember { mutableStateOf(false) }
    val bottomClearance = bottomBarClearance()

    val dirPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.setDownloadDir(uri.toString())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = bottomClearance),
    ) {
        ScreenHeader(
            title = "设置",
            modifier = Modifier.padding(top = 10.dp),
        )

        BrandBanner()

        Spacer(Modifier.height(16.dp))
        SectionLabel("外观", Modifier.padding(horizontal = 24.dp))
        SettingsSection {
            SettingsRow(
                icon = Icons.Default.DarkMode,
                iconColor = MaterialTheme.colorScheme.primary,
                title = "主题",
                subtitle = settings.themeMode.label,
                onClick = { themeDialog = true },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                RowDivider()
                SettingsRow(
                    icon = Icons.Default.ColorLens,
                    iconColor = MaterialTheme.colorScheme.primary,
                    title = "动态取色",
                    subtitle = "跟随系统壁纸配色（Android 12+）",
                    trailing = {
                        Switch(
                            checked = settings.dynamicColorEnabled,
                            onCheckedChange = { viewModel.setDynamicColor(it) },
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionLabel("下载", Modifier.padding(horizontal = 24.dp))
        SettingsSection {
            SettingsRow(
                icon = Icons.Default.Folder,
                iconColor = MaterialTheme.colorScheme.primary,
                title = "下载路径",
                subtitle = if (settings.downloadDirUri.isNullOrBlank()) {
                    "应用下载目录（推荐，支持断点续传）"
                } else {
                    "自定义文件夹（完成后自动复制一份）"
                },
                onClick = { downloadDirDialog = true },
            )
        }

        Spacer(Modifier.height(14.dp))
        SectionLabel("更新", Modifier.padding(horizontal = 24.dp))
        SettingsSection {
            SettingsRow(
                icon = Icons.Default.SystemUpdateAlt,
                iconColor = MaterialTheme.colorScheme.primary,
                title = "更新通道",
                subtitle = settings.updateChannel.label,
                onClick = { channelDialog = true },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Default.History,
                iconColor = MaterialTheme.colorScheme.primary,
                title = "更新日志",
                subtitle = "查看各版本更新内容",
                onClick = onOpenChangelog,
            )
        }

        Spacer(Modifier.height(14.dp))
        SectionLabel("关于", Modifier.padding(horizontal = 24.dp))
        SettingsSection {
            SettingsRow(
                icon = Icons.Default.Info,
                iconColor = MaterialTheme.colorScheme.primary,
                title = "关于 ClassSoftwareHub",
                subtitle = "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                onClick = onOpenAbout,
            )
        }

        Spacer(Modifier.height(26.dp))
        Text(
            "ClassSoftwareHub Android · 由桌面版重构",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            textAlign = TextAlign.Center,
        )
    }

    if (themeDialog) {
        AlertDialog(
            onDismissRequest = { themeDialog = false },
            title = { Text("主题") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setThemeMode(mode)
                                    themeDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = settings.themeMode == mode,
                                onClick = {
                                    viewModel.setThemeMode(mode)
                                    themeDialog = false
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(mode.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { themeDialog = false }) { Text("关闭") }
            },
        )
    }

    if (channelDialog) {
        AlertDialog(
            onDismissRequest = { channelDialog = false },
            title = { Text("更新通道") },
            text = {
                Column {
                    UpdateChannel.entries.forEach { channel ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setUpdateChannel(channel)
                                    channelDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = settings.updateChannel == channel,
                                onClick = {
                                    viewModel.setUpdateChannel(channel)
                                    channelDialog = false
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when (channel) {
                                    UpdateChannel.STABLE -> "稳定版（仅接收正式版本）"
                                    UpdateChannel.INSIDER -> "Insider（新功能优先体验，稳定性略低）"
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { channelDialog = false }) { Text("关闭") }
            },
        )
    }

    if (downloadDirDialog) {
        AlertDialog(
            onDismissRequest = { downloadDirDialog = false },
            title = { Text("下载路径") },
            text = {
                Text(
                    if (settings.downloadDirUri.isNullOrBlank()) {
                        "当前使用应用下载目录（Android/data/${context.packageName}/files/downloads），支持断点续传与暂停恢复。\n\n也可以选择一个自定义文件夹，下载完成后自动复制一份到该目录（不影响续传）。"
                    } else {
                        "当前自定义目录：\n${settings.downloadDirUri}\n\n下载完成后会自动复制一份到该目录。"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    downloadDirDialog = false
                    dirPickerLauncher.launch(null)
                }) { Text("选择文件夹") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.setDownloadDir(null)
                    downloadDirDialog = false
                }) { Text("恢复默认") }
            },
        )
    }
}

/**
 * 设置页顶部的版本横幅：直接用桌面端 1.2 的 Assets/dv1.2.png（1400×659），
 * 按屏宽等比缩放、四角裁圆。
 */
@Composable
private fun BrandBanner() {
    Image(
        painter = painterResource(R.drawable.banner_v1_2),
        contentDescription = "ClassSoftwareHub 桌面版 1.2 横幅",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .aspectRatio(1400f / 659f)
            .clip(RoundedCornerShape(24.dp)),
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 66.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    )
}

@Composable
private fun SettingsSection(content: @Composable () -> Unit) {
    GlassCard(
        modifier = Modifier.padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Spacer(Modifier.height(1.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
