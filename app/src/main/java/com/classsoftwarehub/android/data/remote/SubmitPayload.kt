package com.classsoftwarehub.android.data.remote

import com.classsoftwarehub.android.data.model.FeedbackDraft
import com.classsoftwarehub.android.data.model.FeedbackOptions
import com.classsoftwarehub.android.data.model.SoftwareSubmitDraft
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 提交载荷拼装与校验（纯函数，便于单元测试）。
 *
 * 与桌面端 SubmitPage.BuildPayload / Validate、网页端 SubmitPage.vue 的规则逐条对齐：
 *   · 软件 ID 会拼进 `软件数据/apps/<id>.json`，必须满足 ^[a-z0-9][a-z0-9-]*$（防路径穿越）；
 *   · 校验值归一化后为空的键不写入（避免 `"hash": ""`）；
 *   · 联系方式（`_联系方式`）是本地 age 加密后的密文，服务端拒收明文。
 */
object SubmitPayload {

    private val ID_REGEX = Regex("^[a-z0-9][a-z0-9-]*$")
    private val HASH_PREFIX = Regex("^(md5|sha-?1|sha-?224|sha-?256|sha-?384|sha-?512)\\s*[:=]?\\s*", RegexOption.IGNORE_CASE)
    private val HASH_LENGTHS = setOf(32, 40, 56, 64, 96, 128)

    /** 归一化校验值：去掉算法前缀 / 0x / 空白与冒号（与桌面端 NormalizeHash 一致） */
    fun normalizeHash(raw: String): String = raw
        .trim()
        .replace(HASH_PREFIX, "")
        .replace(Regex("^0x", RegexOption.IGNORE_CASE), "")
        .replace(Regex("[\\s:]"), "")

    /** 合法的十六进制校验值（位数需匹配一种算法） */
    fun isHashLike(value: String): Boolean =
        value.isNotEmpty() && value.length in HASH_LENGTHS &&
            value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    /** 由软件名称建议一个 ID（小写、非字母数字换连字符）；中文名会得到空串，需手填 */
    fun suggestId(name: String): String = name.trim().lowercase()
        .map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }
        .joinToString("")
        .replace(Regex("-{2,}"), "-")
        .trim('-')
        .take(48)

    /** 组装 /api/submit 的 payload；[contactCipher] 必须是 age 密文 */
    fun buildSoftware(draft: SoftwareSubmitDraft, contactCipher: String): JsonObject = buildJsonObject {
        put("id", draft.id.trim())
        put("name", draft.name.trim())
        put("icon", draft.icon.trim())
        put("category", draft.category)
        put("tagline", draft.tagline.trim())
        put("description", draft.description.trim())
        put("version", draft.version.trim())
        put("size", draft.size.trim())
        put("system", draft.system.trim())
        put("website", draft.website.trim())
        put("github", draft.github.trim())
        put("notice", draft.notice.trim())
        put("store", draft.store.trim())
        put(
            "downloads",
            buildJsonArray {
                draft.downloads.filter { it.url.isNotBlank() }.forEach { item ->
                    addJsonObject {
                        put("platform", item.platform.trim())
                        put("note", item.note.trim())
                        put("size", item.size.trim())
                        put("url", item.url.trim())
                        normalizeHash(item.hash).takeIf { it.isNotEmpty() }?.let { put("hash", it) }
                    }
                }
            },
        )
        // 下划线开头 = 只给审核工单看的元数据，合并时会被剥掉
        put("_联系方式", contactCipher)
        draft.sort.trim().toLongOrNull()?.let { put("sort", it) }
    }

    /** 组装 /api/feedback 的 payload；[contactCipher] 可为空串（未填联系方式） */
    fun buildFeedback(draft: FeedbackDraft, contactCipher: String): JsonObject = buildJsonObject {
        put("kind", draft.kind)
        put("subKind", if (draft.kind == "report") draft.subKind else "")
        put("appId", draft.appId.trim())
        put("title", draft.title.trim())
        put("detail", draft.detail.trim())
        put("contact", contactCipher)
    }

    /** 提交软件必填校验；返回中文错误文案，null 表示通过 */
    fun validateSoftware(draft: SoftwareSubmitDraft, contactCipher: String): String? {
        val missing = buildList {
            if (draft.id.isBlank()) add("软件 ID")
            if (draft.name.isBlank()) add("软件名称")
            if (draft.category.isBlank()) add("分类")
            if (draft.tagline.isBlank()) add("一句话简介")
            if (draft.description.isBlank()) add("详细介绍")
            if (draft.system.isBlank()) add("系统限制")
        }
        if (missing.isNotEmpty()) return "请先填写必填项：${missing.joinToString("、")}"

        if (!ID_REGEX.matches(draft.id.trim())) {
            return "软件 ID 只能用小写字母、数字和连字符（-），且以字母或数字开头"
        }
        if (draft.downloads.none { it.url.isNotBlank() }) return "至少需要一条下载直链（可点「上传」把文件传到本站存储）"

        draft.downloads.forEachIndexed { index, item ->
            val hash = normalizeHash(item.hash)
            if (hash.isNotEmpty() && !isHashLike(hash)) {
                return "第 ${index + 1} 个下载项的校验值格式不正确（纯十六进制，32/40/56/64/96/128 位）"
            }
        }

        val sortText = draft.sort.trim()
        if (sortText.isNotEmpty() && sortText.toLongOrNull() == null) return "排序值必须是整数（可留空）"
        if (contactCipher.isEmpty()) return "请填写联系方式（会本地加密后提交，仅审核可见）"
        return null
    }

    /** 反馈必填校验；返回中文错误文案，null 表示通过 */
    fun validateFeedback(draft: FeedbackDraft): String? {
        if (draft.kind.isBlank()) return "请选择反馈类型"
        if (draft.kind == "report" && draft.subKind.isBlank()) return "请选择问题类型"
        if (draft.title.isBlank() || draft.detail.isBlank()) return "请填写标题与详细描述"
        if (draft.title.trim().length > FeedbackOptions.TITLE_MAX) {
            return "标题不能超过 ${FeedbackOptions.TITLE_MAX} 字"
        }
        if (draft.detail.trim().length > FeedbackOptions.DETAIL_MAX) {
            return "详细描述不能超过 ${FeedbackOptions.DETAIL_MAX} 字"
        }
        return null
    }
}
