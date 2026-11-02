package com.classsoftwarehub.android.data.model

/** 一台已配对的教室电脑（配对码 = 对方电脑端的 6 位数字，一码一机） */
data class AnnounceDevice(
    val code: String,
    val name: String = "",
    val selected: Boolean = true,
)
