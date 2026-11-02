package com.classsoftwarehub.android.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.classsoftwarehub.android.ui.components.GradientIconTile
import com.classsoftwarehub.android.ui.components.ScreenHeader
import com.classsoftwarehub.android.ui.components.SectionLabel
import com.classsoftwarehub.android.ui.main.bottomBarClearance
import com.classsoftwarehub.android.ui.navigation.Routes

private data class ToolEntry(
    val route: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
)

private data class ToolGroup(
    val title: String,
    val color: Color,
    val tools: List<ToolEntry>,
)

private val toolGroups = listOf(
    ToolGroup(
        title = "课堂互动",
        color = Color(0xFF0F766E),
        tools = listOf(
            ToolEntry(
                Routes.TOOL_ANNOUNCE,
                "教室喊话",
                "给教室电脑发一句话，全屏弹窗 + 语音播报",
                Icons.Default.Campaign,
            ),
            ToolEntry(
                Routes.TOOL_RANDOM,
                "随机抽号",
                "班级名单点名 / 抽奖，多份名单随时切换",
                Icons.Default.Casino,
            ),
            ToolEntry(
                Routes.TOOL_TIMER,
                "课堂计时",
                "倒计时、秒表与全屏时钟，投影也看得清",
                Icons.Default.Timer,
            ),
        ),
    ),
    ToolGroup(
        title = "学科工具",
        color = Color(0xFF2563EB),
        tools = listOf(
            ToolEntry(
                Routes.TOOL_FUNCTION_PLOT,
                "函数图像",
                "输入含 x 的表达式，实时绘制函数曲线",
                Icons.Default.Functions,
            ),
            ToolEntry(
                Routes.TOOL_EQUATION,
                "方程求解",
                "输入方程求根，支持常见函数与数值解",
                Icons.Default.Calculate,
            ),
            ToolEntry(
                Routes.TOOL_CHEMISTRY,
                "化学配平",
                "输入 A + B = C，算出最小整数系数",
                Icons.Default.Science,
            ),
            ToolEntry(
                Routes.TOOL_UNITS,
                "单位换算",
                "长度、质量、温度、数据等常用换算",
                Icons.Default.Straighten,
            ),
        ),
    ),
    ToolGroup(
        title = "实用工具",
        color = Color(0xFF0E7490),
        tools = listOf(
            ToolEntry(
                Routes.TOOL_COLOR,
                "图片取色",
                "从图片提取颜色，转换 HEX / RGB / HSL",
                Icons.Default.Colorize,
            ),
            ToolEntry(
                Routes.TOOL_QRCODE,
                "二维码生成",
                "文本转二维码，可保存到相册或分享",
                Icons.Default.QrCode2,
            ),
            ToolEntry(
                Routes.TOOL_ENCODER,
                "编码 / 哈希",
                "Base64、URL 编码与 MD5 / SHA 哈希",
                Icons.Default.Code,
            ),
            ToolEntry(
                Routes.TOOL_MIRROR,
                "系统镜像下载",
                "纯净原版 Windows / Office 镜像入口",
                Icons.Default.CloudDownload,
            ),
        ),
    ),
)

/** 工具箱：按场景分组的双列卡片 */
@Composable
fun ToolsScreen(onOpenTool: (String) -> Unit) {
    val bottomClearance = bottomBarClearance()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 10.dp, bottom = bottomClearance),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader(title = "工具箱")
        }

        toolGroups.forEach { group ->
            item(key = "label-${group.title}") {
                SectionLabel(group.title, Modifier.padding(horizontal = 20.dp))
            }
            item(key = "group-${group.title}") {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    group.tools.chunked(2).forEach { rowTools ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowTools.forEach { tool ->
                                ToolCard(
                                    tool = tool,
                                    color = group.color,
                                    onClick = { onOpenTool(tool.route) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (rowTools.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCard(
    tool: ToolEntry,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .height(152.dp)
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = cs.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            cs.outlineVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            GradientIconTile(
                icon = tool.icon,
                color = color,
                size = 42.dp,
                iconSize = 21.dp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                tool.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
