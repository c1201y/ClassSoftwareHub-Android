package com.classsoftwarehub.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val softwareId: String,
    val softwareName: String,
    val sourceName: String,
    val sourceUrl: String,
    val fileName: String,
    val filePath: String?,
    val fileUri: String?,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: String,
    val error: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
