package com.classsoftwarehub.android.ui.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import com.classsoftwarehub.android.ui.components.GlassCard
import com.classsoftwarehub.android.util.ColorUtils
import kotlin.math.max
import kotlin.math.min

/** 图片取色 / 颜色转换工具 */
@Composable
fun ColorPickerToolScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pickedColor by remember { mutableStateOf<Color?>(null) }
    var hexInput by rememberSaveable { mutableStateOf("#3F51B5") }
    var imageLayoutSize by remember { mutableStateOf(IntSize.Zero) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            bitmap = loadBitmap(context, uri)
            pickedColor = null
        }
    }

    fun copy(text: String) {
        clipboard.setText(AnnotatedString(text))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Button(onClick = {
            pickImageLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }) {
            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (bitmap == null) "选择图片取色" else "重新选择图片")
        }

        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                "点击图片任意位置取色（原图 ${currentBitmap.width}×${currentBitmap.height}）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = "待取色图片",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .onSizeChanged { imageLayoutSize = it }
                    .pointerInput(currentBitmap) {
                        detectTapGestures { offset: Offset ->
                            val color = resolvePixelColor(
                                bitmap = currentBitmap,
                                layoutSize = imageLayoutSize,
                                offset = offset,
                            )
                            if (color != null) {
                                pickedColor = color
                                hexInput = ColorUtils.toHex(color)
                            }
                        }
                    },
            )
        }

        val currentColor = pickedColor
        if (currentColor != null) {
            Spacer(Modifier.height(16.dp))
            GlassCard(contentPadding = PaddingValues(0.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(currentColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            ColorUtils.toHex(currentColor, withAlpha = true),
                            color = ColorUtils.contrastingTextColor(currentColor),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    ColorValueRow("HEX", ColorUtils.toHex(currentColor)) { copy(it) }
                    ColorValueRow("RGB", ColorUtils.toRgb(currentColor)) { copy(it) }
                    ColorValueRow("HSL", ColorUtils.toHsl(currentColor)) { copy(it) }
                    ColorValueRow("ARGB", "#${"%08X".format(currentColor.toArgb())}") { copy(it) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        GlassCard(contentPadding = PaddingValues(0.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("颜色值转换", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { hexInput = it },
                    label = { Text("输入 #RRGGBB / #AARRGGBB") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                val parsed = ColorUtils.parseHex(hexInput)
                if (parsed != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(parsed),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "${ColorUtils.toRgb(parsed)}  ·  ${ColorUtils.toHsl(parsed)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else if (hexInput.isNotBlank()) {
                    Text(
                        "无法解析该颜色值",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ColorValueRow(label: String, value: String, onCopy: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = { onCopy(value) }) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "复制 $label",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 将点击坐标（布局坐标系）换算为图片像素并取色 */
private fun resolvePixelColor(bitmap: Bitmap, layoutSize: IntSize, offset: Offset): Color? {
    if (layoutSize.width == 0 || layoutSize.height == 0) return null
    val scale = min(
        layoutSize.width.toFloat() / bitmap.width,
        layoutSize.height.toFloat() / bitmap.height,
    )
    if (scale <= 0f) return null
    val displayedWidth = bitmap.width * scale
    val displayedHeight = bitmap.height * scale
    val left = (layoutSize.width - displayedWidth) / 2f
    val top = (layoutSize.height - displayedHeight) / 2f
    val px = ((offset.x - left) / scale).toInt()
    val py = ((offset.y - top) / scale).toInt()
    if (px !in 0 until bitmap.width || py !in 0 until bitmap.height) return null
    return Color(bitmap.getPixel(px, py))
}

/** 解码图片并按需下采样，避免占用过多内存 */
private fun loadBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sampleSize = 1
    var dimension = max(bounds.outWidth, bounds.outHeight)
    while (dimension / sampleSize > 1600) sampleSize *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }
}.getOrNull()
