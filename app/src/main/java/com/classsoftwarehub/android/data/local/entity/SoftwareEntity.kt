package com.classsoftwarehub.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "software")
data class SoftwareEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val description: String,
    val version: String,
    val size: String,
    val requirements: String,
    val iconUrl: String?,
    val tagsJson: String,
    val platformsJson: String,
    val sourcesJson: String,
    val updatedAt: Long,
    val orderIndex: Int,
)
