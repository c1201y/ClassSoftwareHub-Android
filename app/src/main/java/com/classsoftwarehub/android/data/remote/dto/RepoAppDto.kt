package com.classsoftwarehub.android.data.remote.dto

import com.classsoftwarehub.android.data.model.DownloadSource
import com.classsoftwarehub.android.data.model.Software
import com.classsoftwarehub.android.data.model.SoftwareCategory
import kotlinx.serialization.Serializable

/**
 * 站点仓库「软件数据」目录里 apps 子目录的单条结构（与桌面端 SoftwareApp 对齐）。
 *
 * 例：{"id":"7-zip","name":"7-Zip","icon":"https://…","category":"system",
 *      "tagline":"…","description":"…","version":"26.04","size":"1.6 MB",
 *      "system":"Windows XP ~ 11","downloads":[{"platform":"…","note":"…","size":"…","url":"…"}]}
 */
@Serializable
data class RepoAppDto(
    val id: String? = null,
    val name: String = "",
    val icon: String? = null,
    val category: String = "",
    val tagline: String = "",
    val description: String = "",
    val version: String = "",
    val size: String = "",
    val system: String = "",
    val platforms: List<String> = emptyList(),
    val downloads: List<RepoDownloadDto> = emptyList(),
    val sort: Int? = null,
)

@Serializable
data class RepoDownloadDto(
    val platform: String = "",
    val note: String = "",
    val size: String = "",
    val url: String = "",
)

/** 仓库分类（软件数据/categories.json）：{"key":"system","name":"系统工具","icon":"…"} */
@Serializable
data class RepoCategoryDto(
    val key: String = "",
    val name: String = "",
    val icon: String = "",
)

/** 平台清单（软件数据/platforms.json 顶层项）：{"key":"windows","name":"Windows","versions":[…]} */
@Serializable
data class RepoPlatformDto(
    val key: String = "",
    val name: String = "",
)

/** 仓库条目 → 应用内模型；分类名经 categories.json 映射后再落到固定枚举上 */
fun RepoAppDto.toSoftwareOrNull(categoryNameOf: (String) -> String?): Software? {
    val normalizedName = name.trim()
    if (normalizedName.isEmpty()) return null

    val categoryKey = category.trim()
    val categoryLabel = if (categoryKey.isEmpty()) "" else categoryNameOf(categoryKey) ?: categoryKey

    return Software(
        id = id?.takeIf { it.isNotBlank() }
            ?: normalizedName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                .ifBlank { normalizedName },
        name = normalizedName,
        category = resolveCategory(categoryKey, categoryLabel),
        description = description.trim().ifBlank { tagline.trim() },
        version = version.trim().ifBlank { "未知" },
        size = size.trim().ifBlank { "未知" },
        requirements = system.trim().ifBlank { "Windows" },
        iconUrl = icon?.trim()?.takeIf { it.isNotBlank() },
        tags = emptyList(),
        platforms = platforms.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            .ifEmpty { inferPlatforms(system) },
        updatedAt = 0L,
        sources = downloads.mapNotNull { download ->
            val url = download.url.trim()
            if (url.isEmpty()) return@mapNotNull null
            val label = listOf(download.platform.trim(), download.note.trim())
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { "下载" }
            DownloadSource(
                name = label,
                url = url,
                size = download.size.trim().ifBlank { null },
                version = null,
            )
        },
    )
}

private fun resolveCategory(key: String, label: String): SoftwareCategory {
    if (label.isNotBlank()) {
        val byLabel = SoftwareCategory.fromLabel(label)
        if (byLabel != SoftwareCategory.OTHER) return byLabel
    }
    return when (key.lowercase()) {
        "system" -> SoftwareCategory.SYSTEM_MAINTENANCE
        "schedule", "teaching" -> SoftwareCategory.TEACHING
        "office" -> SoftwareCategory.OFFICE
        "remote" -> SoftwareCategory.REMOTE
        else -> SoftwareCategory.fromLabel(key)
    }
}

/**
 * 清单没写 platforms 时，从系统要求文案里推断支持的平台 key。
 * 两个 DTO（仓库条目、内置种子）共用。
 */
internal fun inferPlatforms(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    val out = mutableListOf<String>()
    if (raw.contains("Windows", true) || raw.contains("Win", true)) out += "windows"
    if (raw.contains("macOS", true) || raw.contains("Mac", true)) out += "macos"
    if (raw.contains("Linux", true) || raw.contains("Ubuntu", true) || raw.contains("Debian", true)) out += "linux"
    if (raw.contains("Android", true) || raw.contains("安卓", true)) out += "android"
    if (raw.contains("iOS", true) || raw.contains("iPad", true)) out += "ios"
    if (raw.contains("Web", true) || raw.contains("网页", true) || raw.contains("浏览器", true)) out += "web"
    return out
}
