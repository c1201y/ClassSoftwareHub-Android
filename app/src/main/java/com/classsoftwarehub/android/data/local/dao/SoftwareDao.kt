package com.classsoftwarehub.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.classsoftwarehub.android.data.local.entity.SoftwareEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SoftwareDao {

    @Query("SELECT * FROM software ORDER BY orderIndex ASC, name ASC")
    fun observeAll(): Flow<List<SoftwareEntity>>

    @Query("SELECT * FROM software WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<SoftwareEntity?>

    @Query("SELECT COUNT(*) FROM software")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SoftwareEntity>)

    @Query("DELETE FROM software")
    suspend fun clear()

    @androidx.room.Transaction
    suspend fun replaceAll(items: List<SoftwareEntity>) {
        clear()
        upsertAll(items)
    }
}
