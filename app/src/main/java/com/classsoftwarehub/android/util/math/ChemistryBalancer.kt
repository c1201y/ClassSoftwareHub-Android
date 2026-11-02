package com.classsoftwarehub.android.util.math

import java.math.BigInteger

data class BalanceTerm(val formula: String, val coefficient: Int)

data class ElementBalance(val element: String, val left: Int, val right: Int)

data class BalanceResult(
    val ok: Boolean,
    val equation: String = "",
    val terms: List<BalanceTerm> = emptyList(),
    val checks: List<ElementBalance> = emptyList(),
    val note: String? = null,
    val error: String? = null,
)

private class BalanceException(message: String) : Exception(message)

/**
 * 化学方程式配平。
 *
 * 把「每种元素一行、每种物质一列」写成一个齐次线性方程组（反应物为正、生成物为负），
 * 求零空间后取全正向量，再化为最小整数 —— 全程分数 / 大整数运算，不靠试数字。
 */
object ChemistryBalancer {

    fun balance(input: String): BalanceResult = try {
        doBalance(input)
    } catch (e: BalanceException) {
        BalanceResult(ok = false, error = e.message ?: "无法配平这个方程式")
    }

    fun validateFormula(raw: String): String? = try {
        parseFormula(raw.trim())
        null
    } catch (e: BalanceException) {
        e.message
    }

    private fun doBalance(input: String): BalanceResult {
        val text = normalize(input)
        if (text.isBlank()) return fail("请输入化学方程式，例如 Fe + O2 = Fe2O3")

        val sides = text.split('=')
        if (sides.size < 2) return fail("缺少等号或箭头，请写成「反应物 = 生成物」")
        if (sides.size > 2) return fail("等号只能有一个")

        val leftText = sides[0].trim()
        val rightText = sides[1].trim()
        if (leftText.isEmpty() || rightText.isEmpty()) return fail("等号两侧都要有物质")

        val leftTerms = splitTerms(leftText)
        val rightTerms = splitTerms(rightText)
        if (leftTerms.isEmpty() || rightTerms.isEmpty()) return fail("等号两侧都要有物质")

        var chargeIgnored = false
        val species = ArrayList<Pair<String, Map<String, Int>>>()
        for (raw in leftTerms + rightTerms) {
            val (atoms, ignored) = parseFormula(raw)
            if (ignored) chargeIgnored = true
            species.add(raw.trim().let(::displayFormula) to atoms)
        }

        val elements = species.flatMap { it.second.keys }.distinct().sorted()
        if (elements.isEmpty()) return fail("没有识别出任何元素")

        val cols = species.size
        val matrix = Array(elements.size) { r ->
            Array(cols) { c ->
                val count = species[c].second[elements[r]] ?: 0
                val sign = if (c < leftTerms.size) 1 else -1
                Frac.of((sign * count).toLong())
            }
        }

        val basis = nullSpace(matrix, cols)
        if (basis.isEmpty()) {
            return fail("这个方程式无解：等号两侧的元素种类对不上，无法通过调整系数配平。")
        }

        val vector = pickPositive(basis, cols)
            ?: return fail("找不到全为正的系数：请检查等号两侧的元素是否写全（例如漏了生成物里的某种元素）。")

        val ints = toSmallestIntegers(vector)
            ?: return fail("算出的系数过大或无法化为整数，请检查化学式是否写错（例如把 O2 写成了 O）。")

        val terms = species.mapIndexed { i, s -> BalanceTerm(s.first, ints[i]) }

        val equation = buildString {
            species.forEachIndexed { i, s ->
                if (i == leftTerms.size) append(" = ")
                else if (i > 0) append(" + ")
                if (ints[i] != 1) append(ints[i])
                append(s.first)
            }
        }

        val checks = elements.map { element ->
            var leftSum = 0
            var rightSum = 0
            species.forEachIndexed { c, s ->
                val count = s.second[element] ?: 0
                if (c < leftTerms.size) leftSum += count * ints[c] else rightSum += count * ints[c]
            }
            ElementBalance(element, leftSum, rightSum)
        }

        val note = buildString {
            if (basis.size > 1) append("该方程式有多组配平系数（存在歧义），以上为最小正整数解。")
            if (chargeIgnored) {
                if (isNotEmpty()) append(' ')
                append("输入中的离子电荷未参与配平（只按元素守恒计算）。")
            }
        }.ifEmpty { null }

        return BalanceResult(
            ok = true,
            equation = equation,
            terms = terms,
            checks = checks,
            note = note,
        )
    }

    private fun fail(message: String) = BalanceResult(ok = false, error = message)

    // ── 文本归一化 ──────────────────────────────────────────────

    private fun normalize(raw: String): String {
        val sb = StringBuilder(raw.length + 8)
        for (ch in raw) {
            when {
                ch in '\uFF01'..'\uFF5E' -> sb.append((ch - 0xFEE0).toChar())
                ch == '\u3000' || ch == ' ' -> sb.append(' ')
                ch == '（' -> sb.append('(')
                ch == '）' -> sb.append(')')
                ch == '［' -> sb.append('[')
                ch == '］' -> sb.append(']')
                ch == '｛' -> sb.append('{')
                ch == '｝' -> sb.append('}')
                ch == '＋' -> sb.append('+')
                ch == '＝' -> sb.append('=')
                ch == '－' || ch == '−' || ch == '—' -> sb.append('-')
                ch == '→' || ch == '⟶' || ch == '⇌' || ch == '⇄' || ch == '↔' -> sb.append('=')
                ch == '·' || ch == '•' -> sb.append('·')
                ch in '₀'..'₉' -> sb.append('0' + (ch - '₀'))
                ch == '⁰' -> sb.append("^0")
                ch == '¹' -> sb.append("^1")
                ch == '²' -> sb.append("^2")
                ch == '³' -> sb.append("^3")
                ch in '⁴'..'⁹' -> sb.append("^").append('4' + (ch - '⁴'))
                ch == '⁺' -> sb.append('+')
                ch == '⁻' -> sb.append('-')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
            .replace("->", "=")
            .replace("=>", "=")
            .replace("==", "=")
    }

    private fun splitTerms(side: String): List<String> {
        val parts = ArrayList<String>()
        var start = 0
        var i = 0
        while (i < side.length) {
            if (side[i] != '+') {
                i++
                continue
            }
            var j = i + 1
            while (j < side.length && side[j].isWhitespace()) j++
            // 末尾的 + / - 是电荷，不是分隔符
            val isCharge = j >= side.length || side[j] == '+' || side[j] == '-'
            if (!isCharge) {
                parts.add(side.substring(start, i))
                start = i + 1
            }
            i++
        }
        parts.add(side.substring(start))

        return parts.map { part ->
            val trimmed = part.trim()
            if (trimmed.isEmpty()) throw BalanceException("存在空的物质项，请检查是否多写了「+」或漏了物质。")
            // 用户抄来的系数直接剥掉，配平由程序算
            var k = 0
            while (k < trimmed.length && trimmed[k].isDigit()) k++
            val formula = trimmed.substring(k).trim()
            if (formula.isEmpty()) throw BalanceException("存在空的物质项，请检查是否多写了「+」或漏了物质。")
            formula
        }
    }

    private fun displayFormula(raw: String): String {
        val s = raw.trim()
        if (s.length < 3 || s.first() != '[' || s.last() != ']') return s
        val inner = s.substring(1, s.length - 1)
        return if ('[' in inner || '(' in inner) s else inner
    }

    // ── 化学式解析 ─────────────────────────────────────────────

    private fun parseFormula(raw: String): Pair<Map<String, Int>, Boolean> {
        var formula = raw.trim()
        if (formula.isEmpty()) throw BalanceException("存在空的物质项。")

        var chargeIgnored = false
        if (formula.endsWith("+") || formula.endsWith("-")) {
            chargeIgnored = true
            formula = formula.dropLast(1)
            while (formula.isNotEmpty() && formula.last().isDigit()) formula = formula.dropLast(1)
            if (formula.endsWith("^")) formula = formula.dropLast(1)
        }
        if (formula.isEmpty()) throw BalanceException("「$raw」只有一个电荷符号，缺少化学式。")

        val total = LinkedHashMap<String, Int>()
        for (segment in formula.split('·', '*')) {
            val seg = segment.trim()
            if (seg.isEmpty()) continue

            var index = 0
            while (index < seg.length && seg[index].isDigit()) index++
            val multiplier = if (index > 0) {
                seg.substring(0, index).toIntOrNull()?.takeIf { it in 1..100000 }
                    ?: throw BalanceException("「$raw」里的系数太大了")
            } else {
                1
            }

            val counts = FormulaParser(seg.substring(index), raw).parseAll()
            for ((element, count) in counts) {
                total[element] = (total[element] ?: 0) + count * multiplier
            }
        }

        if (total.isEmpty()) throw BalanceException("无法从「$raw」中识别出元素")
        return total to chargeIgnored
    }

    private class FormulaParser(
        private val text: String,
        private val raw: String,
    ) {
        private var pos = 0

        fun parseAll(): Map<String, Int> {
            val map = parseSequence(null)
            if (pos < text.length) {
                throw BalanceException("「$raw」里有多余字符「${text.substring(pos)}」，请检查写法。")
            }
            return map
        }

        private fun parseSequence(closer: Char?): LinkedHashMap<String, Int> {
            val out = LinkedHashMap<String, Int>()
            while (pos < text.length) {
                val ch = text[pos]
                when {
                    ch == closer -> {
                        pos++
                        return out
                    }

                    ch == ')' || ch == ']' || ch == '}' -> {
                        throw BalanceException("括号不匹配：「$raw」")
                    }

                    ch == '(' || ch == '[' || ch == '{' -> {
                        val expected = when (ch) {
                            '(' -> ')'
                            '[' -> ']'
                            else -> '}'
                        }
                        pos++
                        val inner = parseSequence(expected)
                        val multiplier = readNumber().takeIf { it > 0 } ?: 1
                        for ((element, count) in inner) {
                            out[element] = (out[element] ?: 0) + count * multiplier
                        }
                    }

                    ch.isUpperCase() -> {
                        val symbol = readElement()
                        val count = readNumber().takeIf { it > 0 } ?: 1
                        out[symbol] = (out[symbol] ?: 0) + count
                    }

                    ch.isDigit() -> {
                        throw BalanceException("「$raw」中数字前面缺少元素，请检查写法。")
                    }

                    else -> {
                        throw BalanceException("「$raw」里有无法识别的字符「$ch」。")
                    }
                }
            }
            if (closer != null) throw BalanceException("括号没有闭合：「$raw」")
            return out
        }

        private fun readElement(): String {
            val start = pos
            pos++
            while (pos < text.length && text[pos].isLowerCase()) pos++
            val symbol = text.substring(start, pos)
            if (symbol.length > 2) throw BalanceException("无法识别的元素符号「$symbol」")
            if (symbol !in ELEMENTS) throw BalanceException("不认识的元素符号「$symbol」")
            return symbol
        }

        private fun readNumber(): Int {
            val start = pos
            while (pos < text.length && text[pos].isDigit()) pos++
            if (start == pos) return 0
            val literal = text.substring(start, pos)
            val value = literal.toLongOrNull() ?: throw BalanceException("数字「$literal」太大了")
            if (value > 100000) throw BalanceException("数字「$literal」太大了")
            return value.toInt()
        }
    }

    // ── 零空间（分数高斯消元） ─────────────────────────────────

    private fun nullSpace(source: Array<Array<Frac>>, cols: Int): List<Array<Frac>> {
        val rows = source.size
        val m = Array(rows) { r -> Array(cols) { c -> source[r][c] } }
        val pivotCols = ArrayList<Int>()
        var pivotRow = 0

        for (col in 0 until cols) {
            var sel = -1
            for (r in pivotRow until rows) {
                if (!m[r][col].isZero) {
                    sel = r
                    break
                }
            }
            if (sel < 0) continue

            val tmp = m[pivotRow]
            m[pivotRow] = m[sel]
            m[sel] = tmp

            val inv = Frac.ONE.div(m[pivotRow][col])
            for (c in 0 until cols) m[pivotRow][c] = m[pivotRow][c] * inv

            for (r in 0 until rows) {
                if (r != pivotRow && !m[r][col].isZero) {
                    val factor = m[r][col]
                    for (c in 0 until cols) m[r][c] = m[r][c] - factor * m[pivotRow][c]
                }
            }

            pivotCols.add(col)
            pivotRow++
            if (pivotRow == rows) break
        }

        val pivotSet = pivotCols.toHashSet()
        val freeCols = (0 until cols).filter { it !in pivotSet }

        return freeCols.map { free ->
            val vec = Array(cols) { Frac.ZERO }
            vec[free] = Frac.ONE
            pivotCols.forEachIndexed { index, pc ->
                vec[pc] = -m[index][free]
            }
            vec
        }
    }

    private fun pickPositive(basis: List<Array<Frac>>, cols: Int): Array<Frac>? {
        fun allPositive(v: Array<Frac>) = v.all { it.sign > 0 }

        for (b in basis) {
            if (allPositive(b)) return b
            val negated = Array(cols) { -b[it] }
            if (allPositive(negated)) return negated
        }

        if (basis.size > 5) return null

        val options = intArrayOf(-3, -2, -1, 1, 2, 3)
        val combo = IntArray(basis.size)
        var found: Array<Frac>? = null

        fun search(index: Int) {
            if (found != null) return
            if (index == basis.size) {
                val vec = Array(cols) { Frac.ZERO }
                for (i in basis.indices) {
                    val factor = Frac.of(combo[i].toLong())
                    for (j in 0 until cols) vec[j] = vec[j] + basis[i][j] * factor
                }
                if (allPositive(vec)) found = vec
                return
            }
            for (coeff in options) {
                combo[index] = coeff
                search(index + 1)
                if (found != null) return
            }
        }

        search(0)
        return found
    }

    private fun toSmallestIntegers(vec: Array<Frac>): IntArray? {
        var lcm = BigInteger.ONE
        for (f in vec) {
            lcm = lcm.divide(lcm.gcd(f.d)).multiply(f.d)
        }

        val ints = vec.map { it.n.multiply(lcm.divide(it.d)) }
        var gcd = BigInteger.ZERO
        for (v in ints) gcd = gcd.gcd(v.abs())
        if (gcd == BigInteger.ZERO) return null

        val limit = BigInteger.valueOf(10000)
        val reduced = ints.map { it.divide(gcd) }
        if (reduced.any { it.abs() > limit }) return null

        return IntArray(reduced.size) { reduced[it].toInt() }
    }

    /** 精确分数（分子/分母都用 BigInteger，避免配平结果被浮点误差污染） */
    private class Frac private constructor(
        val n: BigInteger,
        val d: BigInteger,
    ) {
        val isZero: Boolean get() = n.signum() == 0
        val sign: Int get() = n.signum()

        operator fun plus(o: Frac): Frac = make(n * o.d + o.n * d, d * o.d)
        operator fun minus(o: Frac): Frac = make(n * o.d - o.n * d, d * o.d)
        operator fun times(o: Frac): Frac = make(n * o.n, d * o.d)
        operator fun div(o: Frac): Frac = make(n * o.d, d * o.n)
        operator fun unaryMinus(): Frac = Frac(n.negate(), d)

        companion object {
            val ZERO = Frac(BigInteger.ZERO, BigInteger.ONE)
            val ONE = Frac(BigInteger.ONE, BigInteger.ONE)

            fun of(value: Long): Frac = Frac(BigInteger.valueOf(value), BigInteger.ONE)

            fun make(numerator: BigInteger, denominator: BigInteger): Frac {
                if (denominator.signum() == 0) throw BalanceException("配平过程中出现了除以 0，请检查化学式。")
                var n = numerator
                var d = denominator
                if (d.signum() < 0) {
                    n = n.negate()
                    d = d.negate()
                }
                val gcd = n.gcd(d)
                if (gcd > BigInteger.ONE) {
                    n = n.divide(gcd)
                    d = d.divide(gcd)
                }
                return Frac(n, d)
            }
        }
    }

    private val ELEMENTS = (
        "H He Li Be B C N O F Ne Na Mg Al Si P S Cl Ar K Ca Sc Ti V Cr Mn Fe Co Ni Cu Zn " +
            "Ga Ge As Se Br Kr Rb Sr Y Zr Nb Mo Tc Ru Rh Pd Ag Cd In Sn Sb Te I Xe Cs Ba La Ce " +
            "Pr Nd Pm Sm Eu Gd Tb Dy Ho Er Tm Yb Lu Hf Ta W Re Os Ir Pt Au Hg Tl Pb Bi Po At Rn " +
            "Fr Ra Ac Th Pa U Np Pu Am Cm Bk Cf Es Fm Md No Lr Rf Db Sg Bh Hs Mt Ds Rg Cn Nh Fl " +
            "Mc Lv Ts Og"
        ).split(' ').toSet()
}
