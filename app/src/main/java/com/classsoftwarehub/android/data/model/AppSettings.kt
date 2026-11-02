package com.classsoftwarehub.android.data.model

/** 主题模式 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色");
}

/** 更新通道 */
enum class UpdateChannel(val label: String) {
    STABLE("稳定版"),
    INSIDER("Insider");
}

/** 应用设置（DataStore 持久化） */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = true,
    val updateChannel: UpdateChannel = UpdateChannel.STABLE,
    /** 自定义下载目录（SAF 持久化 URI），为空表示使用应用私有下载目录 */
    val downloadDirUri: String? = null,
    /** 随机抽号工具的历史名单 */
    val randomNameList: String = "",
)
