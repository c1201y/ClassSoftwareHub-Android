package com.classsoftwarehub.android.util.math

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

data class EquationResult(
    val solutions: List<String> = emptyList(),
    val note: String? = null,
    val error: String? = null,
) {
    val ok: Boolean get() = error == null
}

/**
 * 一元方程数值求根。
 *
 * 先按接近原点的尺度扫描，再逐步放大范围：周期函数有无穷多个根，
 * 若从大范围扫起，列表里会塞满离用户很远的值。
 */
object EquationSolver {

    private const val ROOT_CAP = 64
    private const val SOFT_CAP = 24

    fun solve(input: String, angleUnit: AngleUnit = AngleUnit.RADIANS): EquationResult {
        val lines = input.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        if (lines.isEmpty()) return fail("请输入方程，例如 x^2 - 4 = 0")
        if (lines.size > 1) return fail("当前版本一次解一个一元方程，请只保留一行")

        val line = lines.single()
        val parts = line.split('=')
        if (parts.size != 2) {
            return fail(if (parts.size < 2) "还差一个等号，写成「左边 = 右边」" else "等号只能有一个")
        }
        val left = parts[0].trim()
        val right = parts[1].trim()
        if (left.isEmpty() || right.isEmpty()) return fail("等号两侧都要有内容")

        val compiled = try {
            ExpressionParser.compile("($left) - ($right)", angleUnit)
        } catch (e: MathExpressionException) {
            return fail("式子看不懂：${e.message}")
        }

        val found = findRoots { x -> compiled.eval(x) }

        if (found.identity) {
            return EquationResult(note = "这是一个恒等式：任意实数都是它的解。")
        }
        if (found.roots.isEmpty()) {
            return EquationResult(note = "在 ±10000 范围内没有找到实根（可能无解，或根非常稀疏）。")
        }

        val roots = found.roots.sorted()
        val dense = found.capped || roots.size > 12
        val shown = if (dense) {
            roots.sortedBy { abs(it) }.take(6).sorted()
        } else {
            roots
        }

        val labels = shown.map(::formatRoot)
        val note = buildString {
            if (dense) {
                append("该方程有大量实根（周期函数通常有无穷多组），这里只列出离原点最近的若干个。")
            } else {
                append(if (roots.size == 1) "共 1 个实根。" else "共 ${roots.size} 个实根。")
            }
            if (labels.any { it.contains("≈") }) {
                append(" 带 ≈ 的为数值近似（六位小数）。")
            }
        }

        return EquationResult(solutions = labels, note = note)
    }

    private fun fail(message: String) = EquationResult(error = message)

    private class RootSet(
        val roots: List<Double>,
        val capped: Boolean,
        val identity: Boolean,
    )

    private fun findRoots(f: (Double) -> Double): RootSet {
        val roots = ArrayList<Double>()
        var capped = false
        var identity = false

        fun push(v: Double) {
            if (!v.isFinite()) return
            for (existing in roots) {
                if (abs(existing - v) < 1e-6 * max(1.0, abs(existing))) return
            }
            if (roots.size < ROOT_CAP) roots.add(v)
        }

        fun scan(from: Double, to: Double, steps: Int) {
            val step = (to - from) / steps
            val ys = DoubleArray(steps + 1)
            var magnitude = 0.0
            var finite = 0

            for (i in 0..steps) {
                val y = eval(f, from + step * i)
                ys[i] = y
                if (!y.isFinite()) continue
                finite++
                val a = abs(y)
                if (a < 1e300 && a > magnitude) magnitude = a
            }

            if (finite == 0) return
            if (magnitude == 0.0) {
                identity = true
                return
            }

            for (i in 1..steps) {
                if (roots.size >= SOFT_CAP) return
                val x = from + step * i
                val y = ys[i]
                val prevY = ys[i - 1]
                val prevX = x - step

                if (!y.isFinite()) continue

                if (y == 0.0) {
                    push(x)
                } else if (prevY.isFinite() && prevY != 0.0 && (y > 0) != (prevY > 0)) {
                    bisect(f, prevX, x, prevY)?.let(::push)
                } else if (i >= 2) {
                    // 偶重根（x²、(x-1)⁴ …）两侧不变号，只能找 |f| 的局部极小
                    val prevPrevY = ys[i - 2]
                    if (!prevPrevY.isFinite()) continue
                    if ((prevY > 0) != (prevPrevY > 0) || (prevY > 0) != (y > 0)) continue

                    val ay = abs(prevY)
                    val a0 = abs(prevPrevY)
                    val a2 = abs(y)
                    if (!(ay <= a0 && ay <= a2 && (ay < a0 || ay < a2))) continue

                    val valley = minimizeAbs(f, x - 2 * step, x) ?: continue
                    val fv = abs(eval(f, valley))
                    if (fv <= max(a0, a2) * 1e-6) push(valley)
                }
            }
        }

        for (range in doubleArrayOf(10.0, 100.0, 1000.0, 10000.0)) {
            scan(-range, range, 2000)
            if (identity) break
            if (roots.size >= SOFT_CAP) {
                capped = true
                break
            }
        }

        return RootSet(roots, capped, identity)
    }

    private fun eval(f: (Double) -> Double, x: Double): Double =
        try {
            f(x)
        } catch (_: Exception) {
            Double.NaN
        }

    private fun bisect(f: (Double) -> Double, a: Double, b: Double, fa: Double): Double? {
        var lo = a
        var hi = b
        var flo = fa
        repeat(100) {
            val mid = (lo + hi) / 2
            val fm = eval(f, mid)
            if (!fm.isFinite()) return null
            if (fm == 0.0) return mid
            if ((fm > 0) == (flo > 0)) {
                lo = mid
                flo = fm
            } else {
                hi = mid
            }
            if (abs(hi - lo) <= 1e-12 * max(1.0, abs(mid))) return (lo + hi) / 2
        }
        return (lo + hi) / 2
    }

    private fun minimizeAbs(f: (Double) -> Double, a: Double, b: Double): Double? {
        var lo = a
        var hi = b
        val phi = 0.6180339887498949
        var x1 = hi - phi * (hi - lo)
        var x2 = lo + phi * (hi - lo)
        var f1 = abs(eval(f, x1))
        var f2 = abs(eval(f, x2))
        if (!f1.isFinite() || !f2.isFinite()) return null

        repeat(120) {
            if (f1 <= f2) {
                hi = x2
                x2 = x1
                f2 = f1
                x1 = hi - phi * (hi - lo)
                f1 = abs(eval(f, x1))
            } else {
                lo = x1
                x1 = x2
                f1 = f2
                x2 = lo + phi * (hi - lo)
                f2 = abs(eval(f, x2))
            }
            if (!f1.isFinite() || !f2.isFinite()) return null
        }
        return (lo + hi) / 2
    }

    /** 常见特殊值（π 的有理倍数）用手写形式展示，比 0.523599 友好 */
    private fun formatRoot(v: Double): String {
        specialForm(v)?.let { return "x = $it" }

        val rounded = round(v * 1e6) / 1e6
        if (abs(rounded - round(rounded)) < 1e-9) {
            return "x = ${round(rounded).toLong()}"
        }
        val text = String.format(Locale.US, "%.6f", rounded).trimEnd('0').trimEnd('.')
        return "x ≈ $text"
    }

    private fun specialForm(v: Double): String? {
        for (denominator in intArrayOf(1, 2, 3, 4, 6, 12)) {
            for (k in -24..24) {
                if (k == 0) continue
                val candidate = k * Math.PI / denominator
                if (abs(v - candidate) > 1e-7 * max(1.0, abs(v))) continue

                val g = gcd(abs(k), denominator)
                val num = k / g
                val den = denominator / g
                val head = if (abs(num) == 1) "π" else "${abs(num)}π"
                val core = if (den == 1) head else "$head/$den"
                return if (num < 0) "-$core" else core
            }
        }
        if (abs(v) < 1e-9) return "0"
        return null
    }

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
}
