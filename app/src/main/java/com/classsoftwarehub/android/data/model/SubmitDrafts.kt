package com.classsoftwarehub.android.data.model

import kotlinx.serialization.Serializable

/** 提交软件表单里的一个下载项（与服务端 downloads[] 字段一一对应） */
@Serializable
data class DownloadItemDraft(
    val key: Long = 0L,
    val platform: String = "",
    val note: String = "",
    val size: String = "",
    val url: String = "",
    val hash: String = "",
)

/** 提交软件表单草稿（字段与桌面端 SubmitPage 的 payload 对齐） */
@Serializable
data class SoftwareSubmitDraft(
    val id: String = "",
    val name: String = "",
    val category: String = "",
    val tagline: String = "",
    val description: String = "",
    val version: String = "",
    val size: String = "",
    val system: String = "",
    val website: String = "",
    val github: String = "",
    val notice: String = "",
    val store: String = "",
    val sort: String = "",
    val icon: String = "",
    val contact: String = "",
    val downloads: List<DownloadItemDraft> = listOf(DownloadItemDraft()),
) {
    companion object {
        /** 站点仓库 软件数据/categories.json 的四个分类 key 与中文名 */
        val CATEGORIES = listOf(
            SubmitOption("system", "系统工具"),
            SubmitOption("schedule", "教学软件"),
            SubmitOption("teaching", "教学辅助"),
            SubmitOption("other", "其他工具"),
        )

        fun categoryLabel(key: String): String = CATEGORIES.firstOrNull { it.key == key }?.label ?: key
    }
}

/** 通用「key + 中文名」选项 */
data class SubmitOption(val key: String, val label: String)

/** 问题反馈表单草稿（与 /api/feedback 的字段对齐） */
@Serializable
data class FeedbackDraft(
    val kind: String = "report",
    val subKind: String = "",
    val appId: String = "",
    val title: String = "",
    val detail: String = "",
    val contact: String = "",
)

/** 反馈一级类型 */
data class FeedbackKindOption(val key: String, val label: String, val description: String)

/** 反馈二级类型（仅「报告问题」时出现） */
data class FeedbackSubKindOption(val key: String, val label: String)

object FeedbackOptions {
    val KINDS = listOf(
        FeedbackKindOption("report", "报告问题", "下载安装失败、链接失效、内容有误等功能性问题"),
        FeedbackKindOption("suggestion", "提出建议", "希望新增的软件、功能改进或其他想法"),
    )

    val SUB_KINDS = listOf(
        FeedbackSubKindOption("interaction", "逻辑交互"),
        FeedbackSubKindOption("link", "链接失效"),
        FeedbackSubKindOption("other", "其他问题"),
    )

    const val TITLE_MAX = 80
    const val DETAIL_MAX = 1800
}
