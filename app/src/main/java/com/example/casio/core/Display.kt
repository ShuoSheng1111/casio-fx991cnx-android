package com.example.casio.core

import kotlin.math.abs
import kotlin.math.max

/**
 * 结果格式化:把求解结果渲染成 Python 版那种框线文本。
 * 宽字符(中文)按 2 列计算,保证框线对齐。
 */
object Display {

    private const val WIDTH = 52

    fun width(s: String): Int {
        var w = 0
        for (ch in s) w += if (isWide(ch)) 2 else 1
        return w
    }

    private fun isWide(ch: Char): Boolean {
        val c = ch.code
        return (c in 0x1100..0x115F) || (c in 0x2E80..0xA4CF) ||
            (c in 0xAC00..0xD7A3) || (c in 0xF900..0xFAFF) ||
            (c in 0xFE30..0xFE6F) || (c in 0xFF00..0xFF60) ||
            (c in 0xFFE0..0xFFE6) || (c in 0x20000..0x3FFFD)
    }

    private fun pad(s: String, target: Int): String {
        val w = width(s)
        return if (w >= target) s else s + " ".repeat(target - w)
    }

    fun box(title: String, rows: List<String>): String {
        val sb = StringBuilder()
        sb.append('┌').append("─".repeat(WIDTH)).append('┐').append('\n')
        if (title.isNotEmpty()) {
            sb.append("│ ").append(pad(title, WIDTH - 2)).append(" │").append('\n')
            sb.append('├').append("─".repeat(WIDTH)).append('┤').append('\n')
        }
        for (r in rows) {
            sb.append("│ ").append(pad(r, WIDTH - 2)).append(" │").append('\n')
        }
        sb.append('└').append("─".repeat(WIDTH)).append('┘')
        return sb.toString()
    }

    /** 与 Python 版 fmt_float 对齐 */
    fun fmt(v: Double): String {
        if (v.isNaN()) return "nan"
        if (v.isInfinite()) return if (v > 0) "∞" else "-∞"
        if (v == 0.0) return "0"
        if (v == Math.floor(v) && abs(v) < 1e16) return v.toLong().toString()
        if (abs(v) >= 1e12 || abs(v) < 1e-4) {
            val s = String.format("%.9e", v)
            val parts = s.split("e")
            val mant = parts[0].trimEnd('0').trimEnd('.')
            return "${mant}×10^${parts[1].toInt()}"
        }
        return String.format("%.10f", v).trimEnd('0').trimEnd('.')
    }

    fun showCalculation(expression: String, value: Double): String =
        box("计算", listOf(expression, "= ${fmt(value)}"))

    fun showEquation(result: Solver.Result, equation: String): String {
        val rows = mutableListOf("方程: $equation", "")
        val v = result.variable

        if (result.isExact) {
            result.exactTexts.forEach { rows.add("  $it") }
            if (result.realRoots.isNotEmpty()) {
                rows.add("")
                rows.add("  小数: " + result.realRoots.joinToString(" , ") { fmt(it) })
            }
        } else if (result.rationalRoots.isNotEmpty()) {
            for (r in result.rationalRoots.distinct()) {
                val cnt = result.multiplicity[r] ?: 1
                val extra = if (cnt > 1) "   (重根 ×$cnt)" else ""
                rows.add("  $v = $r$extra")
            }
        }

        val numericOnly =
            if (result.isExact) emptyList()
            else result.realRoots.filter { r ->
                result.rationalRoots.none { abs(r - it.toDouble()) < 1e-12 }
            }

        if (numericOnly.isNotEmpty()) {
            if (result.rationalRoots.isNotEmpty()) rows.add("")
            rows.add("数值根:")
            val ordered = numericOnly.sortedBy { abs(it) }
            val maxShow = 12
            ordered.take(maxShow).forEach { rows.add("  $v ≈ ${fmt(it)}") }
            if (ordered.size > maxShow) {
                rows.add("  ...(共 ${ordered.size} 个,此处只列出最靠近 0 的 $maxShow 个)")
            }
        }
        if (result.complexPairs.isNotEmpty()) {
            rows.add("")
            rows.add("复数根:")
            result.complexPairs.forEach { (re, im) ->
                rows.add("  $v ≈ ${fmt(re)} ± ${fmt(im)}i")
            }
        }

        val total = when {
            result.isExact -> {
                val joined = result.exactTexts.joinToString("")
                when {
                    joined.contains("重根") -> 1
                    joined.contains("或") -> 2
                    joined.contains("i") -> result.exactTexts.size
                    else -> max(1, result.exactTexts.size)
                }
            }
            result.multiplicity.isNotEmpty() || result.rationalRoots.isNotEmpty() -> {
                // 有理根按重数计,其余实根(数值)各计 1,复数根成对计 2
                val distinctReal = result.realRoots.count { r ->
                    result.rationalRoots.none { abs(r - it.toDouble()) < 1e-12 }
                }
                val multSum = result.multiplicity.values.sum()
                val rationalOnly = result.rationalRoots.count { it !in result.multiplicity.keys }
                multSum + rationalOnly + distinctReal + 2 * result.complexPairs.size
            }
            else -> result.realRoots.size + 2 * result.complexPairs.size
        }

        rows.add("")
        rows.add("共 $total 个根" + if (result.note.isNotEmpty()) "  (${result.note})" else "")
        return box("解方程", rows)
    }

    fun showSystem(result: Solver.SystemResult, equations: List<String>): String {
        val rows = mutableListOf("方程组:")
        equations.forEach { rows.add("  $it") }
        rows.add("")
        rows.add("解:")
        result.variables.forEachIndexed { i, name ->
            val val0 = result.solution[i]
            if (val0.isInteger) {
                rows.add("  $name = $val0")
            } else {
                rows.add("  $name = $val0  (≈ ${fmt(val0.toDouble())})")
            }
        }
        return box("解方程组", rows)
    }

    // ---------------- 上下标(积分限用) ----------------

    private val SUPERSCRIPT = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '-' to '⁻', '+' to '⁺',
        'n' to 'ⁿ', 'i' to 'ⁱ', '(' to '⁽', ')' to '⁾'
        // 注意:不含 '.' 与 '/'。带小数点的限(如 π/2 = 1.5707…)画成上标会
        // 和下标挤在一起变成 ∫₀¹·⁵⁷… 这种没法读的东西,故一律不支持下标画法,
        // 由 integral() 退化成 ∫[a, b] 形式。
    )

    private val SUBSCRIPT = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '-' to '₋', '+' to '₊', 'a' to 'ₐ', 'e' to 'ₑ', 'i' to 'ᵢ',
        'n' to 'ₙ', '/' to 'ᐟ'
    )

    /** 能把整串都画成上标时才返回,否则 null(交给调用方退化处理) */
    fun toSuperscript(s: String): String? {
        val out = StringBuilder()
        for (ch in s) out.append(SUPERSCRIPT[ch] ?: return null)
        return out.toString()
    }

    fun toSubscript(s: String): String? {
        val out = StringBuilder()
        for (ch in s) out.append(SUBSCRIPT[ch] ?: return null)
        return out.toString()
    }

    /**
     * 积分的「撬棍」写法:∫ 右侧上方是上限、下方是下限,形如 ∫₀¹ f(x)dx。
     * 限写不出上下标时(如 π/2)退化成一行的 ∫[a, b] 形式。
     */
    fun integral(lower: Double, upper: Double, body: String, dVar: String): String {
        val lo = fmt(lower)
        val hi = fmt(upper)
        val los = toSubscript(lo)
        val his = toSuperscript(hi)
        return if (los != null && his != null) {
            "\u222b$los$his $body d$dVar"
        } else {
            "\u222b[$lo, $hi] $body d$dVar"
        }
    }

    // ---------------- 积分命令的标注式排版 ----------------

    /**
     * 把被拆开的积分命令排成带标注的形式:
     * 在命令里「上限」「下限」两个空位的左上方各挂一行小字说明。
     * 例如 b0=2, b1=3 时:
     *     上限      下限
     *     [2]  [3]  x^2
     */
    /**
     * 求极限的注解式显示:
     *
     *     lim  sin(x)/x
     *     x → 0
     *
     * 第二行是趋近式;方向为单侧时带上标(→0⁺ / →0⁻)。
     */
    fun limitAnnotated(expr: String, variable: String, toward: String, side: String): String {
        val suffix = when (side.trim().lowercase()) {
            "left", "l", "-" -> "⁻"
            "right", "r", "+" -> "⁺"
            else -> ""
        }
        return "lim  $expr\n$variable → $toward$suffix"
    }

    fun integralAnnotated(before: String, b0: String, b1: String, after: String): String {
        fun padBlock(s: String, w: Int): String {
            val vis = width(s)
            return if (vis >= w) s else s + " ".repeat(w - vis)
        }
        val b0s = if (b0.isBlank()) "◻" else b0
        val b1s = if (b1.isBlank()) "◻" else b1
        val w0 = maxOf(4, width(b0s))          // 「上限」按 4 列宽
        val w1 = maxOf(4, width(b1s))
        // 与命令 ":int 下限 上限 表达式" 的顺序保持一致
        val label = "下限" + " ".repeat((w0 - 4).coerceAtLeast(0)) +
            "   " + "上限"
        val boxes = "[$b0s]" + " ".repeat((w0 - width(b0s)).coerceAtLeast(0)) +
            "  [$b1s]" + " ".repeat((w1 - width(b1s)).coerceAtLeast(0)) +
            "  $after"
        return before + "\n" + label + "\n" + boxes
    }
}
