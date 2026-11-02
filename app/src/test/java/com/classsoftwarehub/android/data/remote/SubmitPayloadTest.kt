package com.classsoftwarehub.android.data.remote

import com.classsoftwarehub.android.data.model.DownloadItemDraft
import com.classsoftwarehub.android.data.model.FeedbackDraft
import com.classsoftwarehub.android.data.model.SoftwareSubmitDraft
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 提交载荷拼装与校验的纯函数测试（对齐桌面端 / 网页端契约） */
class SubmitPayloadTest {

    private fun draft(
        id: String = "class-tools",
        name: String = "班级工具箱",
        category: String = "system",
        tagline: String = "一个工具",
        description: String = "详细说明",
        system: String = "Windows 7+",
        downloads: List<DownloadItemDraft> = listOf(DownloadItemDraft(url = "https://example.com/a.exe")),
        sort: String = "",
    ) = SoftwareSubmitDraft(
        id = id,
        name = name,
        category = category,
        tagline = tagline,
        description = description,
        system = system,
        downloads = downloads,
        sort = sort,
    )

    // ── normalizeHash / isHashLike ──

    @Test
    fun `normalizeHash 去掉算法前缀与分隔符`() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", SubmitPayload.normalizeHash("MD5: d41d8cd98f00b204e9800998ecf8427e"))
        // 大小写原样保留、只剥离空白与冒号（与桌面端 NormalizeHash 一致）
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", SubmitPayload.normalizeHash("  Sha-256 = D41D8CD9 8F00B204 E9800998 ECF8427E  "))
        assertEquals("ABCD-EF", SubmitPayload.normalizeHash("ABCD-EF"))
        assertEquals("ABCDEF", SubmitPayload.normalizeHash("0xABCDEF"))
        assertEquals("", SubmitPayload.normalizeHash("   "))
    }

    @Test
    fun `isHashLike 只有对应算法的十六进制位数才通过`() {
        assertTrue(SubmitPayload.isHashLike("d41d8cd98f00b204e9800998ecf8427e")) // 32 = MD5
        assertTrue(SubmitPayload.isHashLike("A".repeat(128))) // 512 = SHA-512
        assertFalse(SubmitPayload.isHashLike("d41d8cd98f00b204e9800998ecf8427e00")) // 34 位不合法
        assertFalse(SubmitPayload.isHashLike("z".repeat(32))) // 非十六进制
        assertFalse(SubmitPayload.isHashLike(""))
    }

    // ── suggestId ──

    @Test
    fun `suggestId 只保留小写字母数字与单个连字符`() {
        assertEquals("class-tools", SubmitPayload.suggestId("Class Tools"))
        assertEquals("a-b-c", SubmitPayload.suggestId("A--B__C"))
        assertEquals("v1-2", SubmitPayload.suggestId("--V1.2--"))
        assertEquals("", SubmitPayload.suggestId("班级工具箱"))
    }

    // ── buildSoftware ──

    @Test
    fun `buildSoftware 写入规范化哈希 空哈希不写键 联系方式写密文 sort 可选`() {
        val payload = SubmitPayload.buildSoftware(
            draft(
                downloads = listOf(
                    DownloadItemDraft(
                        platform = "Windows",
                        size = "10 MB",
                        url = "https://example.com/a.exe",
                        hash = " SHA256: D41D8CD98F00B204E9800998ECF8427E ",
                    ),
                    DownloadItemDraft(url = "  "), // 空直链会被过滤
                    DownloadItemDraft(url = "https://example.com/b.zip"), // 空哈希不写键
                ),
                sort = "12",
            ),
            contactCipher = "-----BEGIN AGE ENCRYPTED FILE-----\nxxx\n-----END AGE ENCRYPTED FILE-----",
        )

        assertEquals("class-tools", payload["id"]!!.jsonPrimitive.content)
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", payload["downloads"]!!.jsonArray[0].jsonObject["hash"]!!.jsonPrimitive.content)

        val downloads = payload["downloads"]!!.jsonArray
        assertEquals(2, downloads.size)
        assertFalse(downloads[1].jsonObject.containsKey("hash"))

        assertTrue(payload["_联系方式"]!!.jsonPrimitive.content.startsWith("-----BEGIN AGE ENCRYPTED FILE-----"))
        assertEquals(12L, payload["sort"]!!.jsonPrimitive.content.toLong())
    }

    @Test
    fun `buildSoftware 空 sort 留空时不下发 sort`() {
        val payload = SubmitPayload.buildSoftware(draft(sort = ""), "cipher")
        assertFalse(payload.containsKey("sort"))
    }

    // ── validateSoftware ──

    @Test
    fun `validateSoftware 必填项与 ID 规则`() {
        assertNull(SubmitPayload.validateSoftware(draft(), "cipher"))

        assertTrue(SubmitPayload.validateSoftware(draft(name = " "), "cipher")!!.contains("软件名称"))
        assertTrue(SubmitPayload.validateSoftware(draft(id = "Bad_ID"), "cipher")!!.contains("软件 ID"))
        assertTrue(SubmitPayload.validateSoftware(draft(id = "-abc"), "cipher")!!.contains("软件 ID"))
        assertTrue(SubmitPayload.validateSoftware(draft(category = ""), "cipher")!!.contains("分类"))
        assertTrue(
            SubmitPayload.validateSoftware(
                draft(downloads = listOf(DownloadItemDraft(url = " "))),
                "cipher",
            )!!.contains("下载直链"),
        )
    }

    @Test
    fun `validateSoftware 联系方式必须已加密 哈希格式要合法 排序值要整数`() {
        assertTrue(SubmitPayload.validateSoftware(draft(), "")!!.contains("联系方式"))

        val badHash = draft(downloads = listOf(DownloadItemDraft(url = "https://e.com/a", hash = "hello")))
        assertTrue(SubmitPayload.validateSoftware(badHash, "cipher")!!.contains("校验值"))

        assertTrue(SubmitPayload.validateSoftware(draft(sort = "abc"), "cipher")!!.contains("排序值"))
        assertNull(SubmitPayload.validateSoftware(draft(sort = "3"), "cipher"))
    }

    // ── buildFeedback / validateFeedback ──

    @Test
    fun `buildFeedback 建议类型不带子类型 联系方式可为空串`() {
        val report = SubmitPayload.buildFeedback(
            FeedbackDraft(kind = "report", subKind = "link", appId = "a-1", title = "标题", detail = "详情", contact = "x"),
            contactCipher = "cipher",
        )
        assertEquals("report", report["kind"]!!.jsonPrimitive.content)
        assertEquals("link", report["subKind"]!!.jsonPrimitive.content)
        assertEquals("a-1", report["appId"]!!.jsonPrimitive.content)
        assertEquals("cipher", report["contact"]!!.jsonPrimitive.content)

        val suggestion = SubmitPayload.buildFeedback(
            FeedbackDraft(kind = "suggestion", subKind = "link", title = "标题", detail = "详情"),
            contactCipher = "",
        )
        assertEquals("suggestion", suggestion["kind"]!!.jsonPrimitive.content)
        assertEquals("", suggestion["subKind"]!!.jsonPrimitive.content)
        assertEquals("", suggestion["contact"]!!.jsonPrimitive.content)
    }

    @Test
    fun `validateFeedback 报告必须有子类型 字数上限 建议不限子类型`() {
        assertNull(
            SubmitPayload.validateFeedback(FeedbackDraft(kind = "report", subKind = "other", title = "t", detail = "d")),
        )
        assertTrue(
            SubmitPayload.validateFeedback(FeedbackDraft(kind = "report", subKind = "", title = "t", detail = "d"))!!
                .contains("问题类型"),
        )
        assertTrue(
            SubmitPayload.validateFeedback(FeedbackDraft(kind = "suggestion", title = "t".repeat(81), detail = "d"))!!
                .contains("标题"),
        )
        assertTrue(
            SubmitPayload.validateFeedback(FeedbackDraft(kind = "suggestion", title = "t", detail = "d".repeat(1801)))!!
                .contains("详细描述"),
        )
        assertTrue(
            SubmitPayload.validateFeedback(FeedbackDraft(kind = "suggestion", title = " ", detail = "d"))!!
                .contains("标题"),
        )
    }
}
