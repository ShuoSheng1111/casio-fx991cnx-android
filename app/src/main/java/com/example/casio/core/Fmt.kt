package com.example.casio.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * 结果格式化,对应 fx-991CN X 的显示规则:
 *  · Fix(小数位数) / Sci(有效位数) / Norm1 / Norm2
 *  · 工程符号(指数为 3 的倍数,1 ≤ |尾数| < 1000)
 *  · 分数结果(带分数 / 假分数)
 *  · 数字分隔符、小数点用句点还是逗号
 *  · 数学输出时可给根式/π 形式(见 [exactForm])
 *
 * 说明:真机内部用 15 位有效数字运算,这里用 Double(约 15~17 位),
 * 显示规则按手册示例实现,但无法保证与真机逐位一致(见评估文档的风险章节)。
 */
object Fmt {

    private const val MAX_DEN = 1_000_000L

    // ---------------- 主入口 ----------------

    /**
     * 把一个数值格式化为显示字符串。
     * @param mathExact 是否允许分数形式(对应"数学输出"且结果可精确表示)
     */
    fun number(
        v: Double,
        s: Settings,
        mathExact: Boolean = false,
        forceDecimal: Boolean = false
    ): String {
        if (v.isNaN()) return "Math ERROR"
        if (v.isInfinite()) return "Math ERROR"

        // 1) 工程符号优先(指数为 3 的倍数)
        if (s.engineering) return engineering(v, s, 10)

        return when (val d = s.display) {
            is DisplayMode.Fix -> fixString(v, d.digits, s)
            is DisplayMode.Sci -> sciString(v, d.digits, s)
            is DisplayMode.Norm -> {
                // 2) 数学输出且可精确表示 → 分数
                if (mathExact && !forceDecimal && s.mathOutput) {
                    fractionString(v, s)?.let { return it }
                }
                normString(v, d.variant, s)
            }
        }
    }

    // ---------------- Fix / Sci ----------------

    /** 位数(Fix) n:四舍五入到 n 位小数 */
    fun fixString(v: Double, digits: Int, s: Settings): String {
        val n = digits.coerceIn(0, 9)
        val rounded = round(v, n)
        val body = String.format("%.${n}f", rounded)
        return decorate(body, s)
    }

    /** 科学(Sci) n:n 位**有效数字**,形如 1.4286×10⁻¹(n=5) */
    fun sciString(v: Double, digits: Int, s: Settings): String {
        val n = digits.coerceIn(0, 9)
        if (v == 0.0) return decorate("0", s)
        val exp = floor(log10(abs(v))).toInt()
        val mant = v / 10.0.pow(exp)
        // n 位有效数字 = 小数点后 n-1 位
        val dec = (n - 1).coerceAtLeast(0)
        var mantStr = String.format("%.${dec}f", round(mant, dec))
        var e2 = exp
        // 舍入后可能进位到 10(如 9.99→10.0)
        if (abs(mantStr.toDouble()) >= 10.0) {
            mantStr = String.format("%.${dec}f", mantStr.toDouble() / 10.0)
            e2 = exp + 1
        }
        return decorate(mantStr, s) + "×10" + superscriptExp(e2)
    }

    // ---------------- Norm ----------------

    /** 常规(Norm):超出范围时用指数形式 */
    fun normString(v: Double, variant: Int, s: Settings): String {
        if (v == 0.0) return "0"
        val a = abs(v)
        val lower = if (variant == 2) 1e-9 else 1e-2
        val useExp = a < lower || a >= 1e10
        if (!useExp) {
            return decorate(trimNumber(v), s)
        }
        // 指数形式:10 位有效数字(真机 Norm 下按内部精度显示)
        val exp = floor(log10(a)).toInt()
        val mant = v / 10.0.pow(exp)
        val mStr = trimNumber(mant, 10)
        return decorate(mStr, s) + "×10" + superscriptExp(exp)
    }

    /** 工程符号:指数取 3 的倍数 */
    fun engineering(v: Double, s: Settings, sig: Int): String {
        if (v == 0.0) return "0"
        val exp3 = floor(log10(abs(v)) / 3.0).toInt() * 3
        val mant = v / 10.0.pow(exp3)
        val mStr = trimNumber(mant, sig)
        val prefix = engPrefix(exp3)
        return if (prefix != null && exp3 != 0) {
            decorate(mStr, s) + prefix
        } else {
            decorate(mStr, s) + "×10" + superscriptExp(exp3)
        }
    }

    private fun engPrefix(exp: Int): String? = when (exp) {
        3 -> "k"; 6 -> "M"; 9 -> "G"; 12 -> "T"; 15 -> "P"; 18 -> "E"
        -3 -> "m"; -6 -> "μ"; -9 -> "n"; -12 -> "p"; -15 -> "f"; -18 -> "a"
        else -> null
    }

    private fun superscriptExp(e: Int): String {
        val map = mapOf(
            '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
            '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
            '-' to '⁻'
        )
        return e.toString().map { map[it] ?: it }.joinToString("")
    }

    // ---------------- 分数 ----------------

    /**
     * 尝试把数值表示成分数。成功返回字符串,失败返回 null。
     * 用连分数逼近,分母上限 10^6;误差需小于 1e-12 的相对量级。
     */
    fun fractionString(v: Double, s: Settings): String? {
        val (num, den) = bestFraction(v) ?: return null
        if (den == 1L) return null          // 整数不需要分数形式
        val sign = if (num < 0) "-" else ""
        val an = abs(num)
        return if (s.mixedFraction && an > den) {
            // 带分数:整数 分子/分母
            val whole = an / den
            val rem = an % den
            if (rem == 0L) "$sign$whole"
            else "$sign$whole⌐$rem/$den"
        } else {
            "$sign$an/$den"
        }
    }

    /** 连分数法求最佳有理逼近 */
    fun bestFraction(v: Double, maxDen: Long = MAX_DEN): Pair<Long, Long>? {
        if (!v.isFinite()) return null
        val sign = if (v < 0) -1 else 1
        var x = abs(v)
        if (x == 0.0) return 0L to 1L
        // 连分数展开
        var h0 = 1L; var h1 = 0L
        var k0 = 0L; var k1 = 1L
        var value = x
        var iter = 0
        while (iter < 40) {
            iter++
            val a = floor(value)
            if (a > 1e15) return null
            val ai = a.toLong()
            val h2 = ai * h0 + h1
            val k2 = ai * k0 + k1
            if (k2 > maxDen) return null
            h1 = h0; h0 = h2
            k1 = k0; k0 = k2
            val approx = h0.toDouble() / k0.toDouble()
            if (abs(approx - x) < 1e-13 * maxOf(1.0, x)) {
                return (sign * h0) to k0
            }
            val frac = value - a
            if (frac < 1e-15) break
            value = 1.0 / frac
        }
        val approx = h0.toDouble() / k0.toDouble()
        return if (abs(approx - x) < 1e-12 * maxOf(1.0, x)) (sign * h0) to k0 else null
    }

    /** 有限小数判定:分母只含因子 2 和 5 → 返回小数字符串;否则 null */
    fun terminatingDecimal(num: Long, den: Long): String? {
        if (den == 0L) return null
        var d = den
        var twos = 0; var fives = 0
        while (d % 2L == 0L) { d /= 2; twos++ }
        while (d % 5L == 0L) { d /= 5; fives++ }
        if (d != 1L) return null
        val digits = maxOf(twos, fives)
        if (digits > 15) return null
        val scaled = num.toDouble() / den.toDouble()
        return String.format("%.${digits}f", scaled)
    }

    // ---------------- 辅助 ----------------

    /**
     * 紧凑表示:先按 12 位有效数字归整(消除 Double 的尾数噪声),
     * 再去掉尾随零。这样 1200.0 显示为 "1200" 而不是 "1200.000000"。
     */
    fun trimNumber(v: Double, sig: Int = 10): String {
        if (v == 0.0) return "0"
        val a = abs(v)
        if (a >= 1e10 || a < 1e-9) {
            val exp = floor(log10(a)).toInt()
            val mant = v / 10.0.pow(exp)
            return trimNumber(mant, sig) + "×10" + superscriptExp(exp)
        }
        val bd = java.math.BigDecimal(v)
            .round(java.math.MathContext(12))
            .stripTrailingZeros()
        val plain = bd.toPlainString()
        // 极少数情况下 BigDecimal 仍会给科学计数法的尾巴,统一为普通写法
        return if (plain.contains('E') || plain.contains('e')) {
            java.math.BigDecimal(v).round(java.math.MathContext(12)).toPlainString()
        } else plain
    }

    private fun round(v: Double, digits: Int): Double {
        val p = 10.0.pow(digits)
        return Math.round(v * p) / p
    }

    /** 按设定加千位分隔符、替换小数点字符 */
    private fun decorate(body: String, s: Settings): String {
        var out = body
        if (s.digitSeparator) {
            val neg = out.startsWith("-")
            if (neg) out = out.substring(1)
            val dot = out.indexOf('.')
            val intPart = if (dot >= 0) out.substring(0, dot) else out
            val rest = if (dot >= 0) out.substring(dot) else ""
            val grouped = intPart.reversed().chunked(3).joinToString(",").reversed()
            out = grouped + rest
            if (neg) out = "-$out"
        }
        if (s.decimalComma) {
            // 千位分隔符此时用空格,小数点用逗号
            out = out.replace(',', ' ').replace('.', ',')
        }
        return out
    }

    /**
     * 精确形式:π 的整数/半整数倍、或 a√b 形式的根式。
     * 仅在「数学输出」下启用。返回 null 表示该值没有可用的精确形式。
     */
    fun exactForm(v: Double, s: Settings): String? {
        if (!s.mathOutput) return null
        if (v == 0.0 || !v.isFinite()) return null

        // 1) π 的有理倍数(分子/分母均 ≤ 12)
        val piRatio = v / Math.PI
        val pf = bestFraction(piRatio, 12L)
        if (pf != null) {
            val (n, d) = pf
            if (n != 0L && abs(n) <= 12 && d <= 12) {
                val exact = n.toDouble() / d.toDouble() * Math.PI
                if (abs(exact - v) <= 1e-12 * maxOf(1.0, abs(v))) {
                    val sign = if (n < 0) "-" else ""
                    val an = abs(n)
                    return when {
                        an == 1L && d == 1L -> sign + "π"
                        d == 1L -> sign + an + "π"
                        an == 1L -> sign + "π/" + d
                        else -> sign + an + "π/" + d
                    }
                }
            }
        }

        // 2) a√b 形式
        val sign = if (v < 0) "-" else ""
        val absV = abs(v)
        for (a in 1..99L) {
            val m = absV / a
            if (m <= 0.0) continue
            val innerSq = m * m
            val b = Math.round(innerSq)
            if (b < 2L || b > 9999L) continue
            if (!isSquareFree(b)) continue
            val exact = a.toDouble() * Math.sqrt(b.toDouble())
            if (abs(exact - absV) <= 1e-12 * maxOf(1.0, absV)) {
                return if (a == 1L) sign + "√" + b else sign + a + "√" + b
            }
        }
        return null
    }

    private fun isSquareFree(n: Long): Boolean {
        if (n < 2L) return false
        var i = 2L
        while (i * i <= n) {
            if (n % (i * i) == 0L) return false
            i++
        }
        return true
    }
}
