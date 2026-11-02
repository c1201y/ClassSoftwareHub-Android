package com.classsoftwarehub.android.data.model

/** 下载任务状态 */
enum class DownloadStatus(val label: String) {
    PENDING("等待中"),
    DOWNLOADING("下载中"),
    COMPLETED("已完成"),
    FAILED("失败"),
    CANCELED("已取消");

    companion object {
        fun fromName(value: String?): DownloadStatus =
            entries.firstOrNull { it.name == value } ?: PENDING
    }
}

/** 下载任务（Room 持久化 + 前台服务执行） */
data class DownloadTask(
    val id: Long,
    val softwareId: String,
    val softwareName: String,
    val sourceName: String,
    val sourceUrl: String,
    val fileName: String,
    val filePath: String? = null,
    val fileUri: String? = null,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val error: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val progress: Float
        get() = when {
            totalBytes > 0 -> (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
            status == DownloadStatus.COMPLETED -> 1f
            else -> 0f
        }

    val isActive: Boolean
        get() = status == DownloadStatus.PENDING || status == DownloadStatus.DOWNLOADING
}

/** 顶部下载入口使用的汇总信息 */
data class DownloadSummary(
    val activeCount: Int = 0,
    val overallProgress: Float = 0f,
    val currentFileName: String? = null,
)
