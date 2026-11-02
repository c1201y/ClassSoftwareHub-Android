package com.classsoftwarehub.android.ui.navigation

/** 全局路由表 */
object Routes {

    // 底部 Tab（一级页面）
    const val HOME = "home"
    const val TOOLS = "tools"
    const val DOWNLOADS = "downloads"
    const val SUBMIT = "submit"
    const val SETTINGS = "settings"

    // 二级页面
    const val ARG_SOFTWARE_ID = "softwareId"
    const val SOFTWARE_DETAIL = "software/{$ARG_SOFTWARE_ID}"

    fun softwareDetail(id: String): String = "software/$id"

    // 工具
    const val TOOL_COLOR = "tools/color"
    const val TOOL_RANDOM = "tools/random"
    const val TOOL_TIMER = "tools/timer"
    const val TOOL_ENCODER = "tools/encoder"
    const val TOOL_QRCODE = "tools/qrcode"
    const val TOOL_UNITS = "tools/units"
    const val TOOL_ANNOUNCE = "tools/announce"
    const val TOOL_MIRROR = "tools/mirror"
    const val TOOL_FUNCTION_PLOT = "tools/function-plot"
    const val TOOL_EQUATION = "tools/equation"
    const val TOOL_CHEMISTRY = "tools/chemistry"

    // 提交
    const val SUBMIT_SOFTWARE = "submit/software"
    const val SUBMIT_FEEDBACK = "submit/feedback"

    // 设置
    const val CHANGELOG = "settings/changelog"
    const val ABOUT = "settings/about"

    val topLevel = listOf(HOME, TOOLS, DOWNLOADS, SUBMIT, SETTINGS)

    fun titleOf(route: String?): String = when (route) {
        HOME -> "软件库"
        TOOLS -> "工具箱"
        DOWNLOADS -> "下载"
        SUBMIT -> "提交中心"
        SETTINGS -> "设置"
        SOFTWARE_DETAIL -> "软件详情"
        TOOL_COLOR -> "图片取色"
        TOOL_RANDOM -> "随机抽号"
        TOOL_TIMER -> "课堂计时"
        TOOL_ENCODER -> "编码 / 哈希"
        TOOL_QRCODE -> "二维码生成"
        TOOL_UNITS -> "单位换算"
        TOOL_ANNOUNCE -> "教室喊话"
        TOOL_MIRROR -> "系统镜像下载"
        TOOL_FUNCTION_PLOT -> "函数图像"
        TOOL_EQUATION -> "方程求解"
        TOOL_CHEMISTRY -> "化学方程式配平"
        SUBMIT_SOFTWARE -> "提交软件"
        SUBMIT_FEEDBACK -> "问题反馈"
        CHANGELOG -> "更新日志"
        ABOUT -> "关于"
        else -> "ClassSoftwareHub"
    }
}
