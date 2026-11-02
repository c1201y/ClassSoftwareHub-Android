package com.classsoftwarehub.android.data.model

import kotlinx.serialization.Serializable

/** 软件分类：覆盖班级电教场景。操作系统归类走 [PlatformTab]，不混在业务分类里 */
enum class SoftwareCategory(val label: String) {
    SYSTEM_MAINTENANCE("系统维护"),
    TEACHING("教学管理"),
    OFFICE("办公通讯"),
    REMOTE("远程协助"),
    OTHER("其他");

    companion object {
        fun fromLabel(value: String?): SoftwareCategory {
            if (value.isNullOrBlank()) return OTHER
            val v = value.trim()
            entries.firstOrNull { it.name.equals(v, ignoreCase = true) }?.let { return it }
            entries.firstOrNull { it.label == v }?.let { return it }
            return when {
                v.contains("维护") || v.contains("清理") || v.contains("组件") ||
                    v.contains("运行库") || v.contains("系统") -> SYSTEM_MAINTENANCE
                v.contains("教学") || v.contains("课堂") || v.contains("教育") ||
                    v.contains("学习") -> TEACHING
                v.contains("办公") || v.contains("通讯") || v.contains("沟通") -> OFFICE
                v.contains("远程") || v.contains("协助") || v.contains("远控") -> REMOTE
                else -> OTHER
            }
        }
    }
}

/** 一个下载来源（同一软件可提供多个下载地址） */
@Serializable
data class DownloadSource(
    val name: String,
    val url: String,
    val size: String? = null,
    val version: String? = null,
)

/** 软件清单条目 */
data class Software(
    val id: String,
    val name: String,
    val category: SoftwareCategory,
    val description: String,
    val version: String,
    val size: String,
    val requirements: String,
    val iconUrl: String? = null,
    val tags: List<String> = emptyList(),
    /** 支持的平台 key（windows / macos / linux / android / ios / web），对齐桌面版 platforms 字段 */
    val platforms: List<String> = emptyList(),
    val updatedAt: Long = 0L,
    val sources: List<DownloadSource> = emptyList(),
)

/** 一个操作系统平台（对齐桌面版 platforms.json 的顶层项） */
@Serializable
data class PlatformTab(val key: String, val label: String)

/** 移动端默认停留在安卓 */
const val DefaultPlatformKey = "android"

/** platforms.json 拉不到时的兜底清单，顺序与仓库一致 */
val DefaultPlatformTabs = listOf(
    PlatformTab("windows", "Windows"),
    PlatformTab("macos", "macOS"),
    PlatformTab("linux", "Linux"),
    PlatformTab(DefaultPlatformKey, "Android"),
    PlatformTab("ios", "iOS"),
    PlatformTab("web", "网页版"),
)
