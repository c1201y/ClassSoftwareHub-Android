package com.classsoftwarehub.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 提交表单草稿（type: software_submit / feedback） */
@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val type: String,
    val contentJson: String,
    val updatedAt: Long,
)
