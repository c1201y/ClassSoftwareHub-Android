package com.classsoftwarehub.android.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 从软件图标里取一个能当主题色用的颜色（详情页顶部色带用，算法与桌面端 IconPalette 一致）。
 *
 * 不是简单求平均：图标常常是大面积白色背景 + 一块彩色，直接平均会得到一片灰。
 * 逐像素转 HSV，只统计有饱和度、亮度居中的像素，按「饱和度 × 亮度居中程度」加权。
 *
 * 拿不到就返回 null，调用方退回主题强调色 —— 详情页不能因为一个图标加载失败就白屏等待。
 * 结果按 URL 缓存，同一次运行里反复进出同一个软件不重复解码。
 */
object IconPalette {

    /** 取色的解码边长：48 已经足够统计主色，再大是浪费。 */
    private const val SampleSize = 48

    private val lock = Any()
    private val cache = HashMap<String, Color?>()
    private val inFlight = HashMap<String, Deferred<Color?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 缓存里已有的颜色（同步给）。让详情页一进来就是最终色，别先刷强调色再跳。 */
    fun peek(url: String?): Color? {
        val key = normalize(url) ?: return null
        synchronized(lock) { return cache[key] }
    }

    /** 同一张图同时被列表与详情页要时只解一次。 */
    suspend fun pick(context: Context, url: String?): Color? {
        val key = normalize(url) ?: return null

        val deferred = synchronized(lock) {
            if (cache.containsKey(key)) return cache[key]

            inFlight[key] ?: run {
                val appContext = context.applicationContext
                val created = scope.async(start = CoroutineStart.LAZY) { compute(appContext, key) }
                inFlight[key] = created
                created.start()
                created
            }
        }

        return deferred.await()
    }

    /**
     * 把主色做成页头色带：竖直由上到下淡出（照桌面端 DetailPage 的做法）。
     * 别做成斜向 —— 斜着渐变会在左上角堆出一块浓色，看着像贴了张图。
     * 最下一档 alpha 落到 0，色带下缘没有可见的边，直接化进页面底色。
     */
    fun heroGradient(color: Color): Brush = Brush.verticalGradient(
        0f to color.copy(alpha = 0xB4 / 255f),
        0.52f to color.copy(alpha = 0x66 / 255f),
        1f to color.copy(alpha = 0f),
    )

    private suspend fun compute(context: Context, url: String): Color? {
        val picked = try {
            sample(context, url)
        } catch (t: Throwable) {
            null
        }

        synchronized(lock) {
            if (cache.size > 128) cache.clear()
            cache[url] = picked
            inFlight.remove(url)
        }

        return picked
    }

    private suspend fun sample(context: Context, url: String): Color? {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(SampleSize)
            .allowHardware(false)
            .build()

        val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable ?: return null
        val srcWidth = drawable.intrinsicWidth
        val srcHeight = drawable.intrinsicHeight
        if (srcWidth <= 0 || srcHeight <= 0) return null

        val bitmap = Bitmap.createBitmap(SampleSize, SampleSize, Bitmap.Config.ARGB_8888)
        try {
            // 等比塞进 48×48 的正中间，四周留透明 —— 跟桌面端 Fant 缩放的行为一致
            val width = min(srcWidth, SampleSize)
            val height = min(srcHeight, SampleSize)
            val left = (SampleSize - width) / 2
            val top = (SampleSize - height) / 2

            drawable.setBounds(left, top, left + width, top + height)
            drawable.draw(Canvas(bitmap))

            return sampleColor(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun sampleColor(bitmap: Bitmap): Color? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var sumWeight = 0.0

        for (pixel in pixels) {
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha < 128) continue

            val r = ((pixel ushr 16) and 0xFF).toDouble()
            val g = ((pixel ushr 8) and 0xFF).toDouble()
            val b = (pixel and 0xFF).toDouble()

            val (_, s, v) = toHsv(r, g, b)

            if (s < 0.18) continue                    // 灰白 / 灰黑：不是「主题色」
            if (v < 0.12 || v > 0.96) continue

            // 亮度离 0.55 越远越不像配色，权重越低
            val weight = s * (1 - abs(v - 0.55) * 1.4)
            if (weight <= 0) continue

            sumR += r * weight
            sumG += g * weight
            sumB += b * weight
            sumWeight += weight
        }

        if (sumWeight < 1e-6) return null

        val (hue, rawSat, rawValue) = toHsv(sumR / sumWeight, sumG / sumWeight, sumB / sumWeight)

        // 统一拉到「能当主题色」的区间：够饱和、别太亮也别太暗，深浅主题上都压得住
        val saturation = max(rawSat, 0.42).coerceIn(0.42, 0.85)
        val value = rawValue.coerceIn(0.46, 0.72)

        return fromHsv(hue, saturation, value)
    }

    private fun toHsv(red: Double, green: Double, blue: Double): Triple<Double, Double, Double> {
        val r = red / 255.0
        val g = green / 255.0
        val b = blue / 255.0

        val maxChannel = max(r, max(g, b))
        val minChannel = min(r, min(g, b))
        val delta = maxChannel - minChannel

        val value = maxChannel
        val saturation = if (maxChannel <= 0) 0.0 else delta / maxChannel

        if (delta <= 0) return Triple(0.0, saturation, value)

        val hue = when {
            maxChannel == r -> 60 * (((g - b) / delta) % 6)
            maxChannel == g -> 60 * ((b - r) / delta + 2)
            else -> 60 * ((r - g) / delta + 4)
        }

        return Triple(if (hue < 0) hue + 360 else hue, saturation, value)
    }

    private fun fromHsv(hue: Double, saturation: Double, value: Double): Color {
        val c = value * saturation
        val x = c * (1 - abs((hue / 60) % 2 - 1))
        val m = value - c

        val (r, g, b) = when {
            hue < 60 -> Triple(c, x, 0.0)
            hue < 120 -> Triple(x, c, 0.0)
            hue < 180 -> Triple(0.0, c, x)
            hue < 240 -> Triple(0.0, x, c)
            hue < 300 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }

        return Color(
            red = ((r + m) * 255).roundToInt().coerceIn(0, 255),
            green = ((g + m) * 255).roundToInt().coerceIn(0, 255),
            blue = ((b + m) * 255).roundToInt().coerceIn(0, 255),
            alpha = 255,
        )
    }

    private fun normalize(url: String?): String? {
        val trimmed = url?.trim()
        if (trimmed.isNullOrEmpty()) return null
        if (!trimmed.startsWith("http", ignoreCase = true)) return null
        if (trimmed.endsWith(".svg", ignoreCase = true)) return null    // Coil 默认解不了 SVG
        return trimmed
    }
}
