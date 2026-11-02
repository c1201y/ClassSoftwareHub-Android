package com.classsoftwarehub.android.ui.tools

import android.util.Base64
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.classsoftwarehub.android.ui.components.FilterPill
import com.classsoftwarehub.android.ui.components.GlassCard
import com.classsoftwarehub.android.util.HashUtils
import java.net.URLDecoder
import java.net.URLEncoder

private enum class EncoderMode(val label: String, val reversible: Boolean) {
    BASE64("Base64", true),
    URL("URL 编码", true),
    TEXT_HEX("文本→十六进制", false),
    HEX_TEXT("十六进制→文本", false),
    MD5("MD5", false),
    SHA1("SHA-1", false),
    SHA256("SHA-256", false),
}

/** 编码 / 哈希转换工具 */
@Composable
fun EncoderToolScreen() {
    val clipboard = LocalClipboardManager.current

    var mode by rememberSaveable { mutableStateOf(EncoderMode.BASE64.name) }
    var decode by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    val currentMode = EncoderMode.valueOf(mode)

    val output: String = remember(input, mode, decode) {
        if (input.isEmpty()) {
            ""
        } else {
            when (currentMode) {
                EncoderMode.BASE64 -> if (decode) {
                    runCatching {
                        String(Base64.decode(input.trim(), Base64.DEFAULT), Charsets.UTF_8)
                    }.getOrElse { "解码失败：${it.message ?: "无效的 Base64 内容"}" }
                } else {
                    Base64.encodeToString(input.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                }

                EncoderMode.URL -> if (decode) {
                    runCatching { URLDecoder.decode(input, "UTF-8") }
                        .getOrElse { "解码失败：${it.message ?: "无效的 URL 编码"}" }
                } else {
                    URLEncoder.encode(input, "UTF-8")
                }

                EncoderMode.TEXT_HEX ->
                    input.toByteArray(Charsets.UTF_8).joinToString(" ") { "%02x".format(it) }

                EncoderMode.HEX_TEXT -> runCatching {
                    val cleaned = input.replace("0x", "").replace(" ", "").replace("\n", "")
                    require(cleaned.isNotEmpty()) { "内容为空" }
                    require(cleaned.length % 2 == 0) { "十六进制长度需为偶数" }
                    cleaned.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                        .toString(Charsets.UTF_8)
                }.getOrElse { "转换失败：${it.message}" }

                EncoderMode.MD5 -> HashUtils.md5(input)
                EncoderMode.SHA1 -> HashUtils.sha1(input)
                EncoderMode.SHA256 -> HashUtils.sha256(input)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(EncoderMode.entries.toList()) { m ->
                FilterPill(
                    text = m.label,
                    selected = mode == m.name,
                    onClick = {
                        mode = m.name
                        decode = false
                    },
                )
            }
        }

        if (currentMode.reversible) {
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "方向",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilterPill(
                    text = "编码",
                    selected = !decode,
                    onClick = { decode = false },
                )
                FilterPill(
                    text = "解码",
                    selected = decode,
                    onClick = { decode = true },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("输入内容") },
            minLines = 4,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "输出结果",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        if (output.isNotEmpty()) {
                            clipboard.setText(AnnotatedString(output))
                        }
                    },
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "复制结果",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (currentMode.reversible) {
                    IconButton(
                        onClick = {
                            if (output.isNotEmpty()) {
                                input = output
                                decode = !decode
                            }
                        },
                    ) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = "交换输入输出",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                output.ifEmpty { "结果将在这里显示" },
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = if (output.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { input = "" },
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("清空输入")
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
