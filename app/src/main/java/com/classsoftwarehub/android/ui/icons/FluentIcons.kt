package com.classsoftwarehub.android.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 与桌面端一致的 Fluent 图标（MIT，microsoft/fluentui-system-icons）。
 * 桌面端导航里的 U+E896 即 ic_fluent_arrow_download_24_regular。
 */
object FluentIcons {

    val ArrowDownload: ImageVector by lazy {
        ImageVector.Builder(
            name = "FluentArrowDownload",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = addPathNodes(ARROW_DOWNLOAD_PATH),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }
}

private const val ARROW_DOWNLOAD_PATH =
    "M18.2498 20.5009C18.664 20.5008 19 20.8365 19 21.2507C19 21.6649 " +
        "18.6644 22.0008 18.2502 22.0009L5.25022 22.0047C4.836 22.0048 4.5 21.6691 " +
        "4.5 21.2549C4.5 20.8407 4.83557 20.5048 5.24978 20.5047L18.2498 20.5009Z" +
        "M11.6482 2.01271L11.75 2.00586C12.1297 2.00586 12.4435 2.28801 12.4932 " +
        "2.65409L12.5 2.75586L12.499 16.4409L16.2208 12.7205C16.4871 12.4543 " +
        "16.9038 12.4301 17.1974 12.648L17.2815 12.7206C17.5477 12.9869 17.5719 " +
        "13.4036 17.354 13.6972L17.2814 13.7813L12.2837 18.7779C12.0176 19.044 " +
        "11.6012 19.0683 11.3076 18.8507L11.2235 18.7782L6.22003 13.7816C5.92694 " +
        "13.4889 5.92661 13.014 6.21931 12.7209C6.48539 12.4545 6.90204 12.43 " +
        "7.1958 12.6477L7.27997 12.7202L10.999 16.4339L11 2.75586C11 2.37616 " +
        "11.2822 2.06237 11.6482 2.01271L11.75 2.00586L11.6482 2.01271Z"
