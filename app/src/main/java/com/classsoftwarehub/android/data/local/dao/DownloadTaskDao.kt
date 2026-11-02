package com.classsoftwarehub.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.classsoftwarehub.android.data.local.entity.DownloadTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {

    @Query("SELECT * FROM download_tasks ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE status IN ('PENDING', 'DOWNLOADING') ORDER BY createdAt ASC")
    fun observeActive(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT COUNT(*) FROM download_tasks WHERE status IN ('PENDING', 'DOWNLOADING')")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT * FROM download_tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE status = :status")
    suspend fun getByStatus(status: String): List<DownloadTaskEntity>

    @Query(
        "SELECT * FROM download_tasks WHERE softwareId = :softwareId AND sourceUrl = :sourceUrl " +
            "AND status IN ('PENDING', 'DOWNLOADING', 'COMPLETED') ORDER BY id DESC LIMIT 1"
    )
    suspend fun findExisting(softwareId: String, sourceUrl: String): DownloadTaskEntity?

    @Insert
    suspend fun insert(task: DownloadTaskEntity): Long

    @Query("UPDATE download_tasks SET status = :status, error = :error, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, error: String?, updatedAt: Long)

    @Query(
        "UPDATE download_tasks SET downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, " +
            "updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateProgress(id: Long, downloadedBytes: Long, totalBytes: Long, updatedAt: Long)

    @Query(
        "UPDATE download_tasks SET status = 'COMPLETED', filePath = :filePath, fileUri = :fileUri, " +
            "totalBytes = :totalBytes, downloadedBytes = :totalBytes, error = NULL, updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun markCompleted(id: Long, filePath: String?, fileUri: String?, totalBytes: Long, updatedAt: Long)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM download_tasks WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()

    @Query("DELETE FROM download_tasks WHERE status IN ('FAILED', 'CANCELED')")
    suspend fun clearFinished()
}
