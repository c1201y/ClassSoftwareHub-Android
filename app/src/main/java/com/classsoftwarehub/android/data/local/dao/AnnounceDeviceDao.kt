package com.classsoftwarehub.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.classsoftwarehub.android.data.local.entity.AnnounceDeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnounceDeviceDao {

    @Query("SELECT * FROM announce_devices ORDER BY rowid ASC")
    fun observeAll(): Flow<List<AnnounceDeviceEntity>>

    @Query("SELECT COUNT(*) FROM announce_devices WHERE code = :code")
    suspend fun count(code: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: AnnounceDeviceEntity)

    @Query("DELETE FROM announce_devices WHERE code = :code")
    suspend fun delete(code: String)

    @Query("UPDATE announce_devices SET selected = :selected WHERE code = :code")
    suspend fun setSelected(code: String, selected: Boolean)

    @Query("UPDATE announce_devices SET selected = :selected")
    suspend fun setAllSelected(selected: Boolean)

    @Query("UPDATE announce_devices SET name = :name WHERE code = :code")
    suspend fun updateName(code: String, name: String)
}
