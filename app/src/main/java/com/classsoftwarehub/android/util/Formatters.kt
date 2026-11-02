package com.classsoftwarehub.android.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index++
        }
        return if (index == 0) {
            "$bytes B"
        } else {
            String.format(Locale.US, "%.1f %s", value, units[index])
        }
    }

    fun formatSpeed(bytesPerSecond: Long): String = "${formatBytes(bytesPerSecond)}/s"

    /** 剩余时间估算 */
    fun formatEta(remainingBytes: Long, bytesPerSecond: Long): String? {
        if (bytesPerSecond <= 0 || remainingBytes <= 0) return null
        val seconds = remainingBytes / bytesPerSecond
        return when {
            seconds < 60 -> "约 $seconds 秒"
            seconds < 3600 -> "约 ${seconds / 60} 分钟"
            else -> "约 ${seconds / 3600} 小时"
        }
    }

    /** 时长（mm:ss / h:mm:ss），用于计时与秒表 */
    fun formatDuration(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /** 计时展示：mm:ss.d（百分秒） */
    fun formatDurationWithMillis(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val centis = (millis % 1000) / 10
        return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centis)
    }

    fun formatDateTime(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

    fun formatDate(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))
}
