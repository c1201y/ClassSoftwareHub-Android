package com.classsoftwarehub.android.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.classsoftwarehub.android.data.model.DownloadTask
import java.io.File

object FileOpener {

    fun mimeTypeOf(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "apk" -> "application/vnd.android.package-archive"
        "exe" -> "application/vnd.microsoft.portable-executable"
        "msi" -> "application/x-msi"
        "zip" -> "application/zip"
        "7z" -> "application/x-7z-compressed"
        "rar" -> "application/vnd.rar"
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "txt" -> "text/plain"
        else -> "*/*"
    }

    fun buildOpenIntent(context: Context, task: DownloadTask): Intent? {
        val uri: Uri = when {
            !task.fileUri.isNullOrBlank() -> Uri.parse(task.fileUri)
            !task.filePath.isNullOrBlank() -> {
                val file = File(task.filePath)
                if (!file.exists()) return null
                runCatching {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }.getOrNull() ?: return null
            }
            else -> return null
        }
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeTypeOf(task.fileName))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** 打开已下载的文件；返回是否成功唤起 */
    fun open(context: Context, task: DownloadTask): Boolean {
        val intent = buildOpenIntent(context, task) ?: return false
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
