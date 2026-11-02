package com.classsoftwarehub.android.util.math

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

/** 三角函数使用的角度单位 */
enum class AngleUnit(val label: String) {
    RADIANS("弧度"),
    DEGREES("角度"),
}

class MathExpressionException(message: String) : Exception(message)

/** 解析好的表达式，可反复代入 x 求值 */
class CompiledExpression internal constructor(
    private val root: (Double) -> Double,
) {
    fun eval(x: Double): Double = root(x)
}

/**
 * 轻量数学表达式解析器（递归下降）。
 *
 * 支持：+ - * / ^、隐式乘法（2x、3(x+1)）、括号、常数 pi / e / tau，
 * 以及常用一元 / 二元函数；变量固定为 x。
 */
object ExpressionParser {

    fun compile(expression: String, angleUnit: AngleUnit = AngleUnit.RADIANS): CompiledExpression {
        val text = normalize(expression)
        if (text.isBlank()) throw MathExpressionException("表达式不能为空")
        return CompiledExpression(Parser(text, angleUnit).parse())
    }

    fun tryCompile(
        expression: String,
        angleUnit: AngleUnit = AngleUnit.RADIANS,
    ): Result<CompiledExpression> = runCatching { compile(expression, angleUnit) }

    private fun normalize(raw: String): String {
        val sb = StringBuilder(raw.length + 8)
        for (ch in raw) {
            when (ch) {
                in '０'..'９' -> sb.append('0' + (ch - '０'))
                in 'Ａ'..'Ｚ' -> sb.append('A' + (ch - 'Ａ'))
                in 'ａ'..'ｚ' -> sb.append('a' + (ch - 'ａ'))
                '（' -> sb.append('(')
                '）' -> sb.append(')')
                '，' -> sb.append(',')
                '＋' -> sb.append('+')
                '－', '−', '–', '—' -> sb.append('-')
                '×', '·', '∙' -> sb.append('*')
                '÷', '／' -> sb.append('/')
                'π' -> sb.append("pi")
                '²' -> sb.append("^2")
                '³' -> sb.append("^3")
                '⁴' -> sb.append("^4")
                '⁵' -> sb.append("^5")
                '⁰' -> sb.append("^0")
                '¹' -> sb.append("^1")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private class Parser(
        private val src: String,
        private val angleUnit: AngleUnit,
    ) {
        private var pos = 0

        fun parse(): (Double) -> Double {
            val fn = parseExpr()
            skipSpace()
            if (pos < src.length) throw error("无法识别「${src.substring(pos).take(12)}」")
            return fn
        }

        private fun parseExpr(): (Double) -> Double {
            var value = parseTerm()
            while (true) {
                skipSpace()
                when {
                    consume('+') -> {
                        val lhs = value
                        val rhs = parseTerm()
                        value = { x -> lhs(x) + rhs(x) }
                    }

                    consume('-') -> {
                        val lhs = value
                        val rhs = parseTerm()
                        value = { x -> lhs(x) - rhs(x) }
                    }

                    else -> return value
                }
            }
        }

        private fun parseTerm(): (Double) -> Double {
            var value = parseUnary()
            while (true) {
                skipSpace()
                when {
                    consume('*') -> {
                        val lhs = value
                        val rhs = parseUnary()
                        value = { x -> lhs(x) * rhs(x) }
                    }

                    consume('/') -> {
                        val lhs = value
                        val rhs = parseUnary()
                        value = { x -> lhs(x) / rhs(x) }
                    }

                    startsPrimary() -> {
                        val lhs = value
                        val rhs = parseUnary()
                        value = { x -> lhs(x) * rhs(x) }
                    }

                    else -> return value
                }
            }
        }

        private fun parseUnary(): (Double) -> Double {
            skipSpace()
            if (consume('-')) {
                val inner = parseUnary()
                return { x -> -inner(x) }
            }
            if (consume('+')) return parseUnary()
            return parsePower()
        }

        private fun parsePower(): (Double) -> Double {
            var base = parsePrimary()
            skipSpace()
            if (consume('^')) {
                val lhs = base
                val exponent = parseUnary()
                base = { x -> lhs(x).pow(exponent(x)) }
            }
            return base
        }

        private fun parsePrimary(): (Double) -> Double {
            skipSpace()
            if (pos >= src.length) throw error("表达式不完整")

            val ch = src[pos]
            if (ch.isDigit() || ch == '.') return parseNumber()
            if (ch == '(') {
                pos++
                val inner = parseExpr()
                skipSpace()
                expect(')')
                return inner
            }
            if (ch.isLetter()) return parseIdentifier()

            throw error("这里需要数字、字母或括号，而不是「$ch」")
        }

        private fun parseNumber(): (Double) -> Double {
            val start = pos
            while (pos < src.length && src[pos].isDigit()) pos++
            if (pos < src.length && src[pos] == '.') {
                pos++
                while (pos < src.length && src[pos].isDigit()) pos++
            }
            if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
                val save = pos
                pos++
                if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
                if (pos < src.length && src[pos].isDigit()) {
                    while (pos < src.length && src[pos].isDigit()) pos++
                } else {
                    pos = save
                }
            }
            val literal = src.substring(start, pos)
            val value = literal.toDoubleOrNull() ?: throw error("数字「$literal」看不懂")
            return { value }
        }

        private fun parseIdentifier(): (Double) -> Double {
            val start = pos
            while (pos < src.length && (src[pos].isLetterOrDigit() || src[pos] == '_')) pos++
            val name = src.substring(start, pos).lowercase()

            skipSpace()
            if (pos < src.length && src[pos] == '(') {
                return parseFunction(name)
            }

            return when (name) {
                "x" -> { x: Double -> x }
                "pi" -> { _: Double -> Math.PI }
                "e" -> { _: Double -> Math.E }
                "tau" -> { _: Double -> 2 * Math.PI }
                else -> throw error("不认识的名称「$name」，变量请用 x")
            }
        }

        private fun parseFunction(name: String): (Double) -> Double {
            val args = ArrayList<(Double) -> Double>()
            pos++
            skipSpace()
            if (!consume(')')) {
                while (true) {
                    args.add(parseExpr())
                    skipSpace()
                    if (consume(',')) continue
                    expect(')')
                    break
                }
            }

            fun require(count: Int) {
                if (args.size != count) throw error("函数 $name 需要 $count 个参数")
            }

            fun single(fn: (Double) -> Double): (Double) -> Double {
                require(1)
                val a = args[0]
                return { x -> fn(a(x)) }
            }

            fun toRad(v: Double) = if (angleUnit == AngleUnit.DEGREES) Math.toRadians(v) else v
            fun fromRad(v: Double) = if (angleUnit == AngleUnit.DEGREES) Math.toDegrees(v) else v

            return when (name) {
                "sin" -> single { sin(toRad(it)) }
                "cos" -> single { cos(toRad(it)) }
                "tan" -> single { tan(toRad(it)) }
                "cot" -> single { 1.0 / tan(toRad(it)) }
                "sec" -> single { 1.0 / cos(toRad(it)) }
                "csc" -> single { 1.0 / sin(toRad(it)) }
                "asin" -> single { fromRad(asin(it)) }
                "acos" -> single { fromRad(acos(it)) }
                "atan" -> single { fromRad(atan(it)) }
                "sinh" -> single { sinh(it) }
                "cosh" -> single { cosh(it) }
                "tanh" -> single { tanh(it) }
                "sqrt" -> single { sqrt(it) }
                "cbrt" -> single { cbrt(it) }
                "abs" -> single { abs(it) }
                "exp" -> single { exp(it) }
                "floor" -> single { floor(it) }
                "ceil" -> single { ceil(it) }
                "round" -> single { round(it) }
                "trunc" -> single { if (it >= 0) floor(it) else ceil(it) }
                "sign" -> single { sign(it) }
                "ln" -> single { ln(it) }
                "lg", "log10" -> single { log10(it) }
                "log2", "lb" -> single { log2(it) }
                "log" -> when (args.size) {
                    1 -> single { log10(it) }
                    2 -> {
                        val a = args[0]
                        val b = args[1]
                        { x -> ln(a(x)) / ln(b(x)) }
                    }

                    else -> throw error("函数 log 需要 1 或 2 个参数")
                }

                "pow" -> {
                    require(2)
                    val a = args[0]
                    val b = args[1]
                    { x -> a(x).pow(b(x)) }
                }

                "min" -> {
                    require(2)
                    val a = args[0]
                    val b = args[1]
                    { x -> min(a(x), b(x)) }
                }

                "max" -> {
                    require(2)
                    val a = args[0]
                    val b = args[1]
                    { x -> max(a(x), b(x)) }
                }

                "mod" -> {
                    require(2)
                    val a = args[0]
                    val b = args[1]
                    { x -> a(x) % b(x) }
                }

                "hypot" -> {
                    require(2)
                    val a = args[0]
                    val b = args[1]
                    { x -> hypot(a(x), b(x)) }
                }

                else -> throw error("不认识的函数「$name」")
            }
        }

        private fun startsPrimary(): Boolean {
            if (pos >= src.length) return false
            val ch = src[pos]
            return ch.isDigit() || ch == '.' || ch.isLetter() || ch == '('
        }

        private fun consume(ch: Char): Boolean {
            skipSpace()
            if (pos < src.length && src[pos] == ch) {
                pos++
                return true
            }
            return false
        }

        private fun expect(ch: Char) {
            if (!consume(ch)) {
                val actual = if (pos < src.length) "「${src[pos]}」" else "结尾"
                throw error("这里应该是「$ch」，实际是$actual")
            }
        }

        private fun skipSpace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        private fun error(message: String) = MathExpressionException(message)
    }
}
