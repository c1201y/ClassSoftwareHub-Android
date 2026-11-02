package com.classsoftwarehub.android.data.local

import com.classsoftwarehub.android.data.local.entity.DownloadTaskEntity
import com.classsoftwarehub.android.data.local.entity.SoftwareEntity
import com.classsoftwarehub.android.data.model.DownloadSource
import com.classsoftwarehub.android.data.model.DownloadStatus
import com.classsoftwarehub.android.data.model.DownloadTask
import com.classsoftwarehub.android.data.model.Software
import com.classsoftwarehub.android.data.model.SoftwareCategory
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val sourceListSerializer = ListSerializer(DownloadSource.serializer())

fun SoftwareEntity.toSoftware(json: Json): Software = Software(
    id = id,
    name = name,
    category = SoftwareCategory.fromLabel(category),
    description = description,
    version = version,
    size = size,
    requirements = requirements,
    iconUrl = iconUrl,
    tags = runCatching { json.decodeFromString(ListSerializer(String.serializer()), tagsJson) }
        .getOrDefault(emptyList()),
    platforms = runCatching { json.decodeFromString(ListSerializer(String.serializer()), platformsJson) }
        .getOrDefault(emptyList()),
    updatedAt = updatedAt,
    sources = runCatching { json.decodeFromString(sourceListSerializer, sourcesJson) }
        .getOrDefault(emptyList()),
)

fun Software.toEntity(json: Json, orderIndex: Int): SoftwareEntity = SoftwareEntity(
    id = id,
    name = name,
    category = category.name,
    description = description,
    version = version,
    size = size,
    requirements = requirements,
    iconUrl = iconUrl,
    tagsJson = json.encodeToString(ListSerializer(String.serializer()), tags),
    platformsJson = json.encodeToString(ListSerializer(String.serializer()), platforms),
    sourcesJson = json.encodeToString(sourceListSerializer, sources),
    updatedAt = updatedAt,
    orderIndex = orderIndex,
)

fun DownloadTaskEntity.toModel(): DownloadTask = DownloadTask(
    id = id,
    softwareId = softwareId,
    softwareName = softwareName,
    sourceName = sourceName,
    sourceUrl = sourceUrl,
    fileName = fileName,
    filePath = filePath,
    fileUri = fileUri,
    totalBytes = totalBytes,
    downloadedBytes = downloadedBytes,
    status = DownloadStatus.fromName(status),
    error = error,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
