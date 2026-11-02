package com.classsoftwarehub.android.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** 用户选中的待上传文件（经 SAF 选择，随时可读） */
data class PickedUpload(
    val uri: Uri,
    val name: String,
    val size: Long,
    val contentType: String,
)

/**
 * 本地文件处理：SAF 元信息查询、图标压缩、上传请求体。
 * 与网页端 ossUpload.ts 的分工一致 —— 只负责"把本地文件变成可直传 OSS 的请求体"。
 */
object Uploads {

    /** 图标本地输入上限（与桌面端 IconInputMaxBytes 一致） */
    const val ICON_INPUT_MAX_BYTES = 8L * 1024 * 1024

    private const val DEFAULT_TYPE = "application/octet-stream"

    /** 查询 DISPLAY_NAME / SIZE / MIME；拿不到的字段用兜底值 */
    fun resolve(context: Context, uri: Uri, fallbackType: String = DEFAULT_TYPE): PickedUpload {
        val resolver = context.contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "upload.bin" }
        var size = -1L

        runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let { name = it }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }

        val rawType = resolver.getType(uri)?.substringBefore(';')?.trim()?.lowercase()
        val type = rawType?.takeIf { it.isNotBlank() } ?: fallbackType
        return PickedUpload(uri, name, size, type)
    }

    /**
     * 尺寸未知（少数文件提供者）时先落一份缓存文件，让上传有确定的长度。
     * 正常情况下（能查到 SIZE）原样返回，不拷贝。
     */
    fun materialize(context: Context, picked: PickedUpload): PickedUpload {
        if (picked.size > 0) return picked
        val file = File(context.cacheDir, "upload-${System.currentTimeMillis()}-${picked.name}")
        try {
            context.contentResolver.openInputStream(picked.uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output, 256 * 1024) }
            } ?: return picked
        } catch (_: Throwable) {
            file.delete()
            return picked
        }
        return picked.copy(uri = Uri.fromFile(file), size = file.length())
    }

    /**
     * 图标压缩：解码后缩到 128×128 PNG。
     * 与桌面端 IconResize 同口径：压不了、或压完反而更大 → 用原文件（压缩只是优化，不是门槛）。
     */
    fun shrinkIcon(context: Context, picked: PickedUpload): PickedUpload {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(picked.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return picked

            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds, 256) }
            val bitmap = context.contentResolver.openInputStream(picked.uri)
                ?.use { BitmapFactory.decodeStream(it, null, options) } ?: return picked

            val scaled = Bitmap.createScaledBitmap(bitmap, 128, 128, true)
            if (scaled !== bitmap) bitmap.recycle()

            val file = File(context.cacheDir, "icon-upload-${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            scaled.recycle()

            val shrunk = PickedUpload(Uri.fromFile(file), "icon.png", file.length(), "image/png")
            if (shrunk.size <= 0 || (picked.size > 0 && shrunk.size >= picked.size)) {
                file.delete()
                picked
            } else {
                shrunk
            }
        } catch (_: Throwable) {
            picked
        }
    }

    private fun sampleSize(bounds: BitmapFactory.Options, target: Int): Int {
        var sample = 1
        var width = bounds.outWidth
        var height = bounds.outHeight
        while (width / 2 >= target && height / 2 >= target) {
            width /= 2
            height /= 2
            sample *= 2
        }
        return sample
    }

    /** 人类可读体积（上传进度文案） */
    fun humanSize(bytes: Long): String = when {
        bytes >= 1L shl 30 -> "%.2f GB".format(bytes / 1073741824.0)
        bytes >= 1L shl 20 -> "%.1f MB".format(bytes / 1048576.0)
        bytes >= 1L shl 10 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}

/**
 * 从 ContentResolver 流式读取的请求体（不整份进内存），顺带上报进度。
 * 与网页端 XHR 的 upload.onprogress 对齐。
 */
class UriRequestBody(
    private val resolver: ContentResolver,
    private val picked: PickedUpload,
    private val onProgress: ((sent: Long, total: Long) -> Unit)? = null,
) : RequestBody() {

    override fun contentType(): MediaType? = picked.contentType.toMediaTypeOrNull()

    override fun contentLength(): Long = picked.size

    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(picked.uri) ?: throw IOException("无法读取所选文件")
        input.use { stream ->
            val buffer = ByteArray(64 * 1024)
            var sent = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                sink.write(buffer, 0, read)
                sent += read
                onProgress?.invoke(sent, picked.size)
            }
        }
    }
}
