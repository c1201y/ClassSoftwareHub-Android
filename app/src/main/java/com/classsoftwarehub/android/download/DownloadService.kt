package com.classsoftwarehub.android.download

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.classsoftwarehub.android.appContainer
import com.classsoftwarehub.android.data.repository.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 下载前台服务：保持进程存活，向用户展示下载进度通知。
 * 真正的下载逻辑在 [DownloadRepository]，服务只负责前台存活与通知更新。
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val repository: DownloadRepository
        get() = appContainer.downloadRepository

    override fun onCreate() {
        super.onCreate()
        val notifications = appContainer.downloadNotifications
        try {
            ServiceCompat.startForeground(
                this,
                DownloadNotifications.PROGRESS_NOTIFICATION_ID,
                notifications.buildProgressNotification(repository.summary.value),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } catch (t: Throwable) {
            // 前台服务启动失败（如后台启动受限）：停止自身，任务将在下次进入应用时继续
            stopSelf()
            return
        }
        scope.launch {
            repository.summary.collect { summary ->
                if (summary.activeCount > 0) {
                    notifications.notifyProgress(summary)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        repository.onServiceStopped()
        super.onDestroy()
    }
}
