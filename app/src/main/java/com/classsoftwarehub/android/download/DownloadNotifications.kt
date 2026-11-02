package com.classsoftwarehub.android.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.classsoftwarehub.android.MainActivity
import com.classsoftwarehub.android.R
import com.classsoftwarehub.android.data.model.DownloadSummary
import com.classsoftwarehub.android.data.model.DownloadTask
import com.classsoftwarehub.android.ui.navigation.Routes
import com.classsoftwarehub.android.util.FileOpener
import com.classsoftwarehub.android.util.Formatters

/** 下载相关通知：前台服务进度通知 + 完成提醒 + 计时器提醒 */
class DownloadNotifications(private val context: Context) {

    companion object {
        const val CHANNEL_PROGRESS = "download_progress"
        const val CHANNEL_DONE = "download_done"
        const val CHANNEL_TIMER = "timer"
        const val PROGRESS_NOTIFICATION_ID = 1001
        private const val DONE_BASE_ID = 2000
        const val TIMER_NOTIFICATION_ID = 3001
    }

    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, "下载进度", NotificationManager.IMPORTANCE_LOW).apply {
                description = "显示正在进行的下载任务"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "下载完成", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "下载完成后提醒"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TIMER, "课堂计时", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "倒计时结束提醒"
            }
        )
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun buildProgressNotification(summary: DownloadSummary): Notification {
        val progress = (summary.overallProgress * 100).toInt().coerceIn(0, 100)
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(
                if (summary.activeCount > 0) "正在下载 ${summary.activeCount} 个任务" else "下载任务"
            )
            .setContentText(summary.currentFileName ?: "ClassSoftwareHub")
            .setProgress(100, progress, summary.activeCount == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(downloadsActivityIntent())
            .build()
    }

    fun notifyProgress(summary: DownloadSummary) {
        if (!canNotify()) return
        runCatching { manager.notify(PROGRESS_NOTIFICATION_ID, buildProgressNotification(summary)) }
    }

    fun cancelProgress() {
        runCatching { manager.cancel(PROGRESS_NOTIFICATION_ID) }
    }

    fun notifyCompleted(task: DownloadTask) {
        if (!canNotify()) return
        val builder = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("下载完成：${task.softwareName}")
            .setContentText("${task.fileName} · ${Formatters.formatBytes(task.totalBytes)}")
            .setAutoCancel(true)
            .setContentIntent(downloadsActivityIntent())
        val openIntent = FileOpener.buildOpenIntent(context, task)
        if (openIntent != null) {
            val pending = PendingIntent.getActivity(
                context,
                (task.id * 31 + 7).toInt(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "打开", pending)
        }
        runCatching { manager.notify(DONE_BASE_ID + task.id.toInt(), builder.build()) }
    }

    /** 倒计时结束时提醒（应用退到后台也能收到） */
    fun notifyTimerFinished(label: String) {
        if (!canNotify()) return
        runCatching {
            manager.notify(
                TIMER_NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL_TIMER)
                    .setSmallIcon(R.drawable.ic_stat_download)
                    .setContentTitle("时间到！")
                    .setContentText(label)
                    .setAutoCancel(true)
                    .setContentIntent(downloadsActivityIntent())
                    .build(),
            )
        }
    }

    private fun downloadsActivityIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_ROUTE, Routes.DOWNLOADS)
        }
        return PendingIntent.getActivity(
            context,
            100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
