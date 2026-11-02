package com.classsoftwarehub.android.download

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.classsoftwarehub.android.ClassSoftwareHubApp
import com.classsoftwarehub.android.data.model.DownloadStatus

/**
 * 进程重启后的下载恢复：
 * 把数据库中残留的 DOWNLOADING 任务重置为 PENDING，并尝试拉起前台服务继续下载。
 * 若后台启动前台服务被系统限制，任务保持 PENDING，下次进入应用时自动继续。
 */
class DownloadRecoveryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_NAME = "download_recovery"
    }

    override suspend fun doWork(): Result {
        val container = (applicationContext as ClassSoftwareHubApp).container
        val dao = container.database.downloadTaskDao()
        val orphaned = dao.getByStatus(DownloadStatus.DOWNLOADING.name)
        if (orphaned.isEmpty()) return Result.success()

        val now = System.currentTimeMillis()
        orphaned.forEach { dao.updateStatus(it.id, DownloadStatus.PENDING.name, it.error, now) }

        runCatching {
            ContextCompat.startForegroundService(
                applicationContext,
                Intent(applicationContext, DownloadService::class.java),
            )
        }
        return Result.success()
    }
}
