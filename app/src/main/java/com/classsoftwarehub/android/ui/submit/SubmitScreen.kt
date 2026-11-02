package com.classsoftwarehub.android.ui.submit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.classsoftwarehub.android.ui.components.GlassCard
import com.classsoftwarehub.android.ui.components.GradientIconTile
import com.classsoftwarehub.android.ui.components.ScreenHeader
import com.classsoftwarehub.android.ui.main.bottomBarClearance

/** 提交中心：提交软件 / 问题反馈 两个入口卡片 */
@Composable
fun SubmitScreen(
    onSubmitSoftware: () -> Unit,
    onFeedback: () -> Unit,
) {
    val bottomClearance = bottomBarClearance()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = bottomClearance),
    ) {
        ScreenHeader(
            title = "提交中心",
            modifier = Modifier.padding(top = 10.dp),
        )

        GlassCard(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(21.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "无需登录 GitHub",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "联系方式在手机本地加密后提交，明文不会外发",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        SubmitEntryCard(
            title = "提交软件",
            tag = "推荐新软件",
            description = "填名称、简介与下载方式，可直接把安装包传到本站存储；审核通过后自动上架。",
            icon = Icons.Default.Upload,
            color = MaterialTheme.colorScheme.primary,
            onClick = onSubmitSoftware,
        )

        Spacer(Modifier.height(12.dp))

        SubmitEntryCard(
            title = "问题反馈",
            tag = "下载失败 / 功能建议",
            description = "下载失败、链接失效或希望新增功能：选好类型填写即可，提交后自动建立工单。",
            icon = Icons.Default.BugReport,
            color = MaterialTheme.colorScheme.primary,
            onClick = onFeedback,
        )

        Spacer(Modifier.height(14.dp))

        GlassCard(
            modifier = Modifier.padding(horizontal = 20.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "表单会在输入过程中自动存为草稿，误退出后可以接着填。若所有入口都不通，也可以改用浏览器打开预填的 GitHub Issue（联系方式仍只放密文）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SubmitEntryCard(
    title: String,
    tag: String,
    description: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GradientIconTile(
                icon = icon,
                color = color,
                size = 54.dp,
                iconSize = 26.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(color.copy(alpha = 0.12f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
