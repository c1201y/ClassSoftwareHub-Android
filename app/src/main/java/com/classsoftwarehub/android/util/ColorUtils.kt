package com.classsoftwarehub.android.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 颜色解析与格式转换（取色工具使用） */
object ColorUtils {

    /** 支持 #RGB / #RRGGBB / #AARRGGBB（可省略 #） */
    fun parseHex(input: String): Color? {
        val cleaned = input.trim().removePrefix("#").removePrefix("0x").replace(" ", "")
        val hex = when (cleaned.length) {
            3 -> cleaned.map { "$it$it" }.joinToString("")
            6, 8 -> cleaned
            else -> return null
        }
        val value = hex.toLongOrNull(16) ?: return null
        return if (hex.length == 8) Color(value) else Color(0xFF000000L or value)
    }

    fun toHex(color: Color, withAlpha: Boolean = false): String {
        val argb = color.toArgb().toLong() and 0xFFFFFFFFL
        return if (withAlpha) {
            String.format(Locale.US, "#%08X", argb)
        } else {
            String.format(Locale.US, "#%06X", argb and 0xFFFFFFL)
        }
    }

    fun toRgb(color: Color): String {
        val argb = color.toArgb()
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return "rgb($r, $g, $b)"
    }

    fun toHsl(color: Color): String {
        val argb = color.toArgb()
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val maxV = max(r, max(g, b))
        val minV = min(r, min(g, b))
        val l = (maxV + minV) / 2f
        val d = maxV - minV
        val s = if (d == 0f) 0f else d / (1f - abs(2f * l - 1f))
        val h = when {
            d == 0f -> 0f
            maxV == r -> 60f * (((g - b) / d) % 6f)
            maxV == g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }
        val hue = ((h + 360f) % 360f).roundToInt()
        return "hsl($hue, ${(s * 100).roundToInt()}%, ${(l * 100).roundToInt()}%)"
    }

    /** 根据背景颜色返回可读的前景色（亮色 / 暗色） */
    fun contrastingTextColor(background: Color): Color {
        val argb = background.toArgb()
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return if (luminance > 0.6) Color(0xFF1C1B1F) else Color(0xFFFFFFFF)
    }
}
