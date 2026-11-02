package com.classsoftwarehub.android.data

import com.classsoftwarehub.android.data.remote.NetworkModule
import com.classsoftwarehub.android.data.remote.dto.SoftwareDto
import com.classsoftwarehub.android.data.remote.dto.toSoftwareOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 内置目录（离线兜底）与打包文件清单的一致性校验 —— 这两个资产文件是远程全部失败时用户能看到的东西。
 */
class BundledCatalogTest {

    private val json: Json = NetworkModule.json

    private fun asset(name: String): File =
        listOf("src/main/assets/$name", "app/src/main/assets/$name")
            .map(::File)
            .firstOrNull { it.isFile }
            ?: error("找不到资产文件 $name")

    @Test
    fun `seed catalog decodes and every entry is downloadable`() {
        val seed = json.decodeFromString(ListSerializer(SoftwareDto.serializer()), asset("catalog.json").readText())
        val models = seed.mapNotNull { it.toSoftwareOrNull() }

        assertEquals(seed.size, models.size)
        assertTrue("内置目录条目过少：${seed.size}", seed.size >= 100)
        assertTrue(models.all { it.name.isNotBlank() && it.sources.isNotEmpty() })
        // 直链两种形态：http(s) 外链，或站点自有存储的 oss:// 键
        assertTrue(
            models.all { software ->
                software.sources.all { it.url.startsWith("http") || it.url.startsWith("oss://") }
            },
        )
    }

    @Test
    fun `bundled file index matches the seed and stays repo-shaped`() {
        val paths = json.decodeFromString(
            ListSerializer(String.serializer()),
            asset("catalog-index.json").readText(),
        )
        val seed = json.decodeFromString(ListSerializer(SoftwareDto.serializer()), asset("catalog.json").readText())

        assertEquals(seed.size, paths.size)
        assertTrue(paths.all { it.startsWith("软件数据/apps/") && it.endsWith(".json") })
        assertTrue(paths.all { !it.substringAfterLast('/').startsWith("_") })
    }
}
