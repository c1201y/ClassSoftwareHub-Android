package com.classsoftwarehub.android.data.remote.dto

import com.classsoftwarehub.android.data.model.DownloadSource
import com.classsoftwarehub.android.data.model.Software
import com.classsoftwarehub.android.data.model.SoftwareCategory
import kotlinx.serialization.Serializable

@Serializable
data class DownloadSourceDto(
    val name: String = "下载",
    val url: String = "",
    val size: String? = null,
    val version: String? = null,
)

/**
 * 远程清单 / 内置种子数据的统一结构。
 * 远程 API 返回形如 `[{ id, name, category, description, version, size, requirements, sources: [...] }]` 的 JSON 数组。
 */
@Serializable
data class SoftwareDto(
    val id: String? = null,
    val name: String = "",
    val category: String = "",
    val description: String = "",
    val version: String = "",
    val size: String = "",
    val requirements: String = "",
    val iconUrl: String? = null,
    val tags: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
    val updatedAt: Long = 0L,
    val sources: List<DownloadSourceDto> = emptyList(),
)

fun SoftwareDto.toSoftwareOrNull(): Software? {
    val normalizedName = name.trim()
    if (normalizedName.isEmpty()) return null
    return Software(
        id = id?.takeIf { it.isNotBlank() }
            ?: normalizedName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'),
        name = normalizedName,
        category = SoftwareCategory.fromLabel(category),
        description = description.trim(),
        version = version.trim().ifBlank { "未知" },
        size = size.trim().ifBlank { "未知" },
        requirements = requirements.trim().ifBlank { "Windows" },
        iconUrl = iconUrl?.takeIf { it.isNotBlank() },
        tags = tags.filter { it.isNotBlank() },
        platforms = platforms.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            .ifEmpty { inferPlatforms(requirements.trim()) },
        updatedAt = updatedAt,
        sources = sources
            .filter { it.url.isNotBlank() }
            .map { DownloadSource(it.name.ifBlank { "下载" }, it.url.trim(), it.size, it.version) },
    )
}
