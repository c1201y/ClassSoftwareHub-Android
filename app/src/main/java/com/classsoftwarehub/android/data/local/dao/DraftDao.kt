package com.classsoftwarehub.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.classsoftwarehub.android.data.local.entity.DraftEntity

@Dao
interface DraftDao {

    @Query("SELECT * FROM drafts WHERE type = :type LIMIT 1")
    suspend fun get(type: String): DraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: DraftEntity)

    @Query("DELETE FROM drafts WHERE type = :type")
    suspend fun delete(type: String)
}
