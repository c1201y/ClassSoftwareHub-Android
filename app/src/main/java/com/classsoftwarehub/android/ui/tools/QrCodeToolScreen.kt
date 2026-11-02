package com.classsoftwarehub.android.ui.tools

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.classsoftwarehub.android.ui.components.GlassCard
import com.classsoftwarehub.android.util.QrUtils
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 二维码生成工具：文本转二维码，支持容错级别、保存与分享 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrCodeToolScreen() {
    val context = LocalContext.current

    var content by rememberSaveable { mutableStateOf("") }
    var eccName by rememberSaveable { mutableStateOf(ErrorCorrectionLevel.M.name) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(content, eccName) {
        if (content.isBlank()) {
            bitmap = null
            errorText = null
            return@LaunchedEffect
        }
        delay(250)
        val result = withContext(Dispatchers.Default) {
            QrUtils.generate(content, 1024, ErrorCorrectionLevel.valueOf(eccName))
        }
        bitmap = result
        errorText = if (result == null) "生成失败：内容可能过长或包含不支持的字符" else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("输入要生成二维码的文本 / 链接") },
            minLines = 3,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("容错级别", style = MaterialTheme.typography.labelMedium)
            listOf(
                ErrorCorrectionLevel.L to "L 7%",
                ErrorCorrectionLevel.M to "M 15%",
                ErrorCorrectionLevel.Q to "Q 25%",
                ErrorCorrectionLevel.H to "H 30%",
            ).forEach { (level, label) ->
                FilterChip(
                    selected = eccName == level.name,
                    onClick = { eccName = level.name },
                    label = { Text(label) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        val currentBitmap = bitmap
        if (currentBitmap != null) {
            GlassCard(contentPadding = PaddingValues(0.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = currentBitmap.asImageBitmap(),
                        contentDescription = "生成的二维码",
                        modifier = Modifier.size(260.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    val saved = QrUtils.saveToGallery(
                        context,
                        currentBitmap,
                        "csh_qr_${System.currentTimeMillis()}",
                    )
                    Toast.makeText(
                        context,
                        if (saved) "已保存到相册（Pictures/ClassSoftwareHub）" else "保存失败，可尝试分享",
                        Toast.LENGTH_SHORT,
                    ).show()
                }) {
                    Text("保存到相册")
                }
                OutlinedButton(onClick = {
                    val uri = QrUtils.saveForShare(context, currentBitmap, "csh_qr_share")
                    if (uri != null) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "分享二维码"))
                    } else {
                        Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("分享")
                }
            }
        } else if (errorText != null) {
            Text(
                errorText ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (content.isBlank()) {
            Text(
                "输入内容后将自动生成二维码",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}
