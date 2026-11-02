package com.classsoftwarehub.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import com.classsoftwarehub.android.BuildConfig
import com.classsoftwarehub.android.ui.components.GlassCard
import com.classsoftwarehub.android.ui.icons.FluentIcons
import com.classsoftwarehub.android.util.IssueUrlBuilder

/** 关于页：版本信息、项目链接与开源许可 */
@Composable
fun AboutScreen() {
    val context = LocalContext.current
    var showLicenses by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                FluentIcons.ArrowDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("ClassSoftwareHub", style = MaterialTheme.typography.headlineSmall)
        Text(
            "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))

        GlassCard(contentPadding = PaddingValues(0.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "关于本应用",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "面向班级电教委员的软件下载站客户端，由 ClassSoftwareHub 桌面版重构而来的原生 Android 应用：" +
                        "浏览软件清单、查看详情并直接下载到本机，内置取色、抽号、计时、编码与二维码等课堂常用工具。\n\n" +
                        "本应用以 GNU GPL-3.0-or-later 发布，源码见项目主页；" +
                        "清单数据来自 classsoftwarehub.cn，收录软件的版权归各软件厂商。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(contentPadding = PaddingValues(0.dp)) {
            Column(Modifier.padding(vertical = 4.dp)) {
                LinkRow("项目主页（GitHub）", BuildConfig.PROJECT_URL, onClick = {
                    IssueUrlBuilder.openUrl(context, BuildConfig.PROJECT_URL)
                })
                LinkRow("软件下载站", BuildConfig.WEBSITE_URL, onClick = {
                    IssueUrlBuilder.openUrl(context, BuildConfig.WEBSITE_URL)
                })
                LinkRow("问题反馈", "https://github.com/${BuildConfig.GITHUB_REPO}/issues", onClick = {
                    IssueUrlBuilder.openUrl(context, "https://github.com/${BuildConfig.GITHUB_REPO}/issues")
                })
                LinkRow("开源许可", onClick = { showLicenses = true })
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "本项目由人类构建\nClassSoftwareHub Android · 桌面版重构",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text("开源许可") },
            text = {
                Text(
                    buildString {
                        appendLine("本应用（ClassSoftwareHub Android）以 GNU General Public License v3.0 或更高版本发布，完整条款见源码仓库的 LICENSE 文件。")
                        appendLine()
                        appendLine("本应用使用了以下开源项目，感谢它们的作者：")
                        appendLine()
                        appendLine("· Jetpack Compose / Material 3 — Apache License 2.0")
                        appendLine("· AndroidX（Room、DataStore、WorkManager、Navigation、Lifecycle）— Apache License 2.0")
                        appendLine("· Retrofit / OkHttp — Apache License 2.0")
                        appendLine("· kotlinx.serialization / Kotlin Coroutines — Apache License 2.0")
                        appendLine("· Coil — Apache License 2.0")
                        appendLine("· ZXing（二维码生成）— Apache License 2.0")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun LinkRow(label: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
