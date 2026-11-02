package com.classsoftwarehub.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 教室喊话的已配对设备（code = 对方配对码，一码一机） */
@Entity(tableName = "announce_devices")
data class AnnounceDeviceEntity(
    @PrimaryKey val code: String,
    val name: String,
    val selected: Boolean,
)
