package com.classsoftwarehub.android.ui.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.classsoftwarehub.android.ui.components.GlassCard
import com.classsoftwarehub.android.ui.components.GradientIconTile
import com.classsoftwarehub.android.ui.components.SectionLabel
import org.json.JSONObject

private data class MirrorSite(
    val name: String,
    val desc: String,
    val url: String,
    val color: String,
)

private data class MirrorData(
    val title: String,
    val subtitle: String,
    val note: String,
    val disclaimer: String,
    val sites: List<MirrorSite>,
)

private val fallbackMirrorData = MirrorData(
    title = "系统镜像下载",
    subtitle = "纯净原版 Windows 系统镜像的官方 / 可信下载入口",
    note = "重装系统前请先备份资料。",
    disclaimer = "请认准官方域名，谨防仿冒下载站捆绑的垃圾软件。",
    sites = listOf(
        MirrorSite(
            "微软官方网站",
            "Windows 10 / 11 官方镜像与安装工具",
            "https://www.microsoft.com/zh-cn/software-download",
            "#0078D4",
        ),
        MirrorSite("next.itellyou", "原版系统镜像索引", "https://next.itellyou.cn", "#0067B8"),
        MirrorSite("MSDN 我告诉你", "含 Windows 7 等旧系统镜像", "https://msdn.itellyou.cn", "#68217A"),
        MirrorSite("HelloWindows", "原版镜像归档，含旧版本系统", "https://hellowindows.cn", "#107C10"),
    ),
)

private fun loadMirrorData(context: Context): MirrorData = runCatching {
    val text = context.assets.open("mirror-sites.json").bufferedReader().use { it.readText() }
    val json = JSONObject(text)
    val array = json.optJSONArray("sites") ?: return@runCatching fallbackMirrorData
    val sites = buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            add(
                MirrorSite(
                    name = item.optString("name"),
                    desc = item.optString("desc"),
                    url = item.optString("url"),
                    color = item.optString("color", "#0F766E"),
                ),
            )
        }
    }
    MirrorData(
        title = json.optString("title", "系统镜像下载"),
        subtitle = json.optString("subtitle", ""),
        note = json.optString("note", ""),
        disclaimer = json.optString("disclaimer", ""),
        sites = sites.ifEmpty { fallbackMirrorData.sites },
    )
}.getOrElse { fallbackMirrorData }

/** 系统镜像下载：官方 / 可信站点入口，点击用浏览器打开 */
@Composable
fun MirrorScreen() {
    val context = LocalContext.current
    val data = remember { loadMirrorData(context) }
    val cs = MaterialTheme.colorScheme

    fun open(url: String) {
        val ok = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.isSuccess
        if (!ok) Toast.makeText(context, "没有可用的浏览器打开该链接", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        GlassCard {
            Text(
                data.subtitle.ifBlank { "原版系统镜像下载入口" },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (data.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = cs.tertiary,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        data.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        SectionLabel("推荐站点", Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(4.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            data.sites.forEach { site ->
                val color = remember(site.color) {
                    runCatching { Color(android.graphics.Color.parseColor(site.color)) }
                        .getOrElse { cs.primary }
                }
                GlassCard(onClick = { open(site.url) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GradientIconTile(
                            icon = Icons.Default.Language,
                            color = color,
                            size = 44.dp,
                            iconSize = 21.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                site.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                site.desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                site.url,
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.primary.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.OpenInNew,
                            contentDescription = "打开",
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = cs.error,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(17.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    data.disclaimer.ifBlank { "请认准官方域名。" },
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
