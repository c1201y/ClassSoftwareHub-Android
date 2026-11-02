package com.classsoftwarehub.android.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classsoftwarehub.android.ui.components.FilterPill
import com.classsoftwarehub.android.ui.components.GlassCard
import com.classsoftwarehub.android.util.math.AngleUnit
import com.classsoftwarehub.android.util.math.CompiledExpression
import com.classsoftwarehub.android.util.math.ExpressionParser
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private class PlotViewport {
    var centerX by mutableStateOf(0.0)
    var centerY by mutableStateOf(0.0)
    var xSpan by mutableStateOf(20.0)

    fun reset() {
        centerX = 0.0
        centerY = 0.0
        xSpan = 20.0
    }

    fun ySpan(width: Int, height: Int): Double =
        if (width > 0) xSpan * height / width else xSpan

    fun pan(dxPx: Float, dyPx: Float, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        centerX -= dxPx / width * xSpan
        centerY += dyPx / height * ySpan(width, height)
    }

    fun zoom(factor: Float, cxPx: Float, cyPx: Float, width: Int, height: Int) {
        if (width <= 0 || height <= 0 || factor <= 0f) return
        val oldSpan = xSpan
        val newSpan = (xSpan / factor).coerceIn(1e-3, 1e6)
        if (newSpan == oldSpan) return

        val xMin = centerX - oldSpan / 2
        val yMin = centerY - ySpan(width, height) / 2
        val wx = xMin + cxPx / width * oldSpan
        val wy = yMin + cyPx / height * ySpan(width, height)

        val ratio = newSpan / oldSpan
        centerX = wx + (centerX - wx) * ratio
        centerY = wy + (centerY - wy) * ratio
        xSpan = newSpan
    }
}

private val plotPresets = listOf(
    "sin(x)",
    "x^2",
    "1/x",
    "tan(x)",
    "e^x",
    "ln(x)",
    "x^3 - 3x",
    "sqrt(x)",
)

/** 函数图像：输入含 x 的表达式，实时绘制，支持拖动与双指缩放 */
@Composable
fun FunctionPlotScreen() {
    val cs = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()

    var expression by rememberSaveable { mutableStateOf("sin(x)") }
    var angleName by rememberSaveable { mutableStateOf(AngleUnit.RADIANS.name) }
    val angleUnit = AngleUnit.valueOf(angleName)
    val viewport = remember { PlotViewport() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val parsed = remember(expression, angleName) {
        ExpressionParser.tryCompile(expression, angleUnit)
    }
    val function = parsed.getOrNull()
    val error = parsed.exceptionOrNull()?.message

    val labelStyle = TextStyle(
        fontSize = 9.5.sp,
        color = cs.onSurfaceVariant.copy(alpha = 0.85f),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = expression,
            onValueChange = { expression = it },
            label = { Text("函数表达式（变量为 x）") },
            singleLine = true,
            isError = error != null,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = cs.error,
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text("角度", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            FilterPill(
                text = "弧度",
                selected = angleUnit == AngleUnit.RADIANS,
                onClick = { angleName = AngleUnit.RADIANS.name },
            )
            FilterPill(
                text = "角度",
                selected = angleUnit == AngleUnit.DEGREES,
                onClick = { angleName = AngleUnit.DEGREES.name },
            )
        }

        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(plotPresets) { preset ->
                FilterPill(
                    text = preset,
                    selected = expression == preset,
                    onClick = {
                        expression = preset
                        viewport.reset()
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(
            contentPadding = PaddingValues(0.dp),
            shape = RoundedCornerShape(24.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val bounds = canvasSize
                            viewport.pan(pan.x, pan.y, bounds.width, bounds.height)
                            if (zoom != 1f) {
                                viewport.zoom(zoom, centroid.x, centroid.y, bounds.width, bounds.height)
                            }
                        }
                    },
            ) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas

                val spanX = viewport.xSpan
                val spanY = viewport.ySpan(w.toInt(), h.toInt())
                val xMin = viewport.centerX - spanX / 2
                val xMax = viewport.centerX + spanX / 2
                val yMin = viewport.centerY - spanY / 2
                val yMax = viewport.centerY + spanY / 2

                fun sx(x: Double) = ((x - xMin) / spanX * w).toFloat()
                fun sy(y: Double) = ((yMax - y) / spanY * h).toFloat()

                drawRect(color = cs.surfaceContainerLowest)

                val gridColor = cs.outlineVariant.copy(alpha = 0.34f)
                val axisColor = cs.onSurfaceVariant.copy(alpha = 0.55f)
                val stepX = niceStep(spanX, 8)
                val stepY = niceStep(spanY, 8)
                val py0 = sy(0.0)
                val px0 = sx(0.0)

                // 横向网格 + x 轴刻度
                for (tx in ticks(xMin, xMax, stepX)) {
                    val px = sx(tx)
                    drawLine(gridColor, Offset(px, 0f), Offset(px, h), 1f)
                }
                // 纵向网格
                for (ty in ticks(yMin, yMax, stepY)) {
                    val py = sy(ty)
                    drawLine(gridColor, Offset(0f, py), Offset(w, py), 1f)
                }

                // 坐标轴
                if (py0 in 0f..h) {
                    drawLine(axisColor, Offset(0f, py0), Offset(w, py0), 1.6f)
                }
                if (px0 in 0f..w) {
                    drawLine(axisColor, Offset(px0, 0f), Offset(px0, h), 1.6f)
                }

                // 刻度文字：贴着坐标轴；轴不在视口时退到边缘
                val labelY = if (py0 in 0f..h - 14f) py0 + 2f else h - 14f
                for (tx in ticks(xMin, xMax, stepX)) {
                    if (abs(tx) < stepX * 1e-6) continue
                    val px = sx(tx)
                    if (px < 2f || px > w - 26f) continue
                    drawText(
                        textMeasurer = textMeasurer,
                        text = formatTick(tx, stepX),
                        topLeft = Offset(px + 3f, labelY),
                        style = labelStyle,
                    )
                }
                val labelX = if (px0 in 2f..w - 26f) px0 + 4f else 4f
                for (ty in ticks(yMin, yMax, stepY)) {
                    if (abs(ty) < stepY * 1e-6) continue
                    val py = sy(ty)
                    if (py < 12f || py > h - 12f) continue
                    drawText(
                        textMeasurer = textMeasurer,
                        text = formatTick(ty, stepY),
                        topLeft = Offset(labelX, py - 12f),
                        style = labelStyle,
                    )
                }

                if (function == null) return@Canvas

                // 采样成若干段；遇到不连续 / 渐近线必须断开，否则 tan(x) 会多出竖直假线
                val segments = ArrayList<MutableList<Offset>>()
                var current: MutableList<Offset>? = null
                val sampleCount = max(2, min(w.toInt(), 2000))
                val dx = spanX / (sampleCount - 1)
                val jumpLimit = spanY * 3
                var prevY = Double.NaN

                for (i in 0 until sampleCount) {
                    val x = xMin + i * dx
                    val y = evalFunction(function, x)

                    var ok = y.isFinite()
                    if (ok && prevY.isFinite() && abs(y - prevY) > jumpLimit) {
                        val crossed = (y > 0) != (prevY > 0)
                        val bothAbove = y > yMax && prevY > yMax
                        val bothBelow = y < yMin && prevY < yMin
                        if (crossed || bothAbove || bothBelow) ok = false
                    }

                    if (!ok) {
                        current = null
                        prevY = y
                        continue
                    }

                    val point = Offset(
                        sx(x),
                        sy(y).coerceIn(-h * 0.5f, h * 1.5f),
                    )
                    val segment = current ?: ArrayList<Offset>().also {
                        current = it
                        segments.add(it)
                    }
                    segment.add(point)
                    prevY = y
                }

                val path = Path()
                for (segment in segments) {
                    if (segment.size < 2) continue
                    path.moveTo(segment[0].x, segment[0].y)
                    for (i in 1 until segment.size) {
                        path.lineTo(segment[i].x, segment[i].y)
                    }
                }
                drawPath(
                    path = path,
                    color = cs.primary,
                    style = Stroke(
                        width = 2.2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "x ∈ [${formatRange(viewport.centerX - viewport.xSpan / 2)}, " +
                        "${formatRange(viewport.centerX + viewport.xSpan / 2)}]",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
                Text(
                    "单指拖动平移 · 双指捏合缩放",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
            OutlinedButton(onClick = { viewport.reset() }) {
                Text("恢复视图")
            }
        }

        Spacer(Modifier.height(10.dp))

        GlassCard {
            Text(
                "支持的写法",
                style = MaterialTheme.typography.titleSmall,
                color = cs.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "运算符 + - * / ^，隐式乘法 2x / 3(x+1)；常数 pi、e；函数 sin cos tan cot sec csc asin acos atan、" +
                    "sinh cosh tanh、ln lg log2 log(x, base)、sqrt cbrt abs exp floor ceil round min max mod hypot。",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun evalFunction(function: CompiledExpression, x: Double): Double =
    try {
        function.eval(x)
    } catch (_: Exception) {
        Double.NaN
    }

private fun niceStep(range: Double, targetTicks: Int): Double {
    if (!range.isFinite() || range <= 0 || targetTicks <= 0) return 1.0
    val raw = range / targetTicks
    val magnitude = 10.0.pow(floor(log10(raw)))
    if (!magnitude.isFinite() || magnitude <= 0) return 1.0
    val normalized = raw / magnitude
    val multiplier = when {
        normalized <= 1 -> 1.0
        normalized <= 2 -> 2.0
        normalized <= 5 -> 5.0
        else -> 10.0
    }
    return magnitude * multiplier
}

private fun ticks(min: Double, max: Double, step: Double): List<Double> {
    if (!step.isFinite() || step <= 0 || !min.isFinite() || !max.isFinite() || max <= min) {
        return emptyList()
    }
    val first = ceil(min / step).toLong()
    val last = floor(max / step).toLong()
    if (last - first > 2000) return emptyList()
    return (first..last).map { it * step }
}

private fun formatTick(value: Double, step: Double): String {
    if (abs(value) < abs(step) * 1e-6) return "0"
    val absValue = abs(value)
    if (absValue >= 1e5 || absValue < 1e-4) {
        return String.format(Locale.US, "%.1e", value)
    }
    val exponent = floor(log10(abs(step))).toInt()
    val decimals = if (exponent >= 0) 0 else min(-exponent, 6)
    return String.format(Locale.US, "%.${decimals}f", value)
}

private fun formatRange(value: Double): String {
    val absValue = abs(value)
    val text = when {
        absValue >= 1e5 || (absValue in 1e-9..1e-3) -> String.format(Locale.US, "%.2e", value)
        absValue >= 100 -> String.format(Locale.US, "%.1f", value)
        else -> String.format(Locale.US, "%.2f", value)
    }
    return text.trimEnd('0').trimEnd('.')
}
