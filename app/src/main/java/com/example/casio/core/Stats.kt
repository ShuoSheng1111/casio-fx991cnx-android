package com.example.casio.core

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 统计计算(对齐卡西欧 ClassWiz CW 系列的统计模式)。
 *
 * 支持:
 *  · 1 变量(x):n、Σx、Σx²、均值、总体/样本标准差、最值、四分位数
 *  · 2 变量(x, y):上述两套,外加 Σxy、Σy、Σy²、相关系数
 *  · 回归模型:线性 y=A+Bx、二次 y=A+Bx+Cx²、对数 y=A+B·ln x、
 *              指数 y=A·e^(Bx)、幂 y=A·x^B、倒数 y=A+B/x
 *
 * 数值用 Double(统计量本身多为无理数);输入数据先经 Parser 求值,
 * 所以可以直接写 sqrt(2) 这类表达式。
 */
object Stats {

    /** 回归模型 */
    enum class Model(val label: String) {
        LINEAR("线性 y=A+Bx"),
        QUADRATIC("二次 y=A+Bx+Cx²"),
        LOG("对数 y=A+B·ln x"),
        EXP("指数 y=A·e^(Bx)"),
        POWER("幂 y=A·x^B"),
        INVERSE("倒数 y=A+B/x")
    }

    /** 1 变量的统计量 */
    data class OneVar(
        val n: Int,
        val sumX: Double,
        val sumX2: Double,
        val sumX3: Double,     // Σx³(二次回归用)
        val sumX4: Double,     // Σx⁴(二次回归用)
        val mean: Double,
        val popVar: Double,    // 总体方差 σ²x
        val sampleVar: Double, // 样本方差 s²x
        val popSd: Double,     // 总体标准差 σx
        val sampleSd: Double,  // 样本标准差 sx
        val min: Double,
        val q1: Double,
        val median: Double,
        val q3: Double,
        val max: Double
    )

    /** 2 变量的统计量 */
    data class TwoVar(
        val one: OneVar,       // x 的统计量
        val sumY: Double,
        val sumY2: Double,
        val sumXY: Double,
        val sumX2Y: Double,    // Σx²y(二次回归用)
        val meanY: Double,
        val popVarY: Double,   // σ²y
        val sampleVarY: Double,// s²y
        val popSdY: Double,
        val sampleSdY: Double,
        val minY: Double,
        val maxY: Double,
        val corr: Double       // 相关系数 r
    )

    /** 回归结果 */
    data class Regression(
        val model: Model,
        val coeffs: List<Double>,     // 按模型顺序:A, B, C
        val coeffNames: List<String>, // 与 coeffs 对应的名字
        val corr: Double
    )

    // ---------------- 输入解析 ----------------

    /**
     * 把一段文本解析成数列。支持逗号或空白分隔,元素可以是表达式(经 Parser 求值)。
     * 返回 null 表示某项无法求值,message 里给出原因。
     */
    fun parseList(text: String): Pair<List<Double>?, String?> {
        val tokens = text.split(Regex("[,\\s]+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null to "没有数据"
        val out = mutableListOf<Double>()
        for (t in tokens) {
            val v = try {
                Parser.parse(t).eval(emptyMap())
            } catch (e: Exception) {
                return null to "无法解析「$t」: ${e.message}"
            }
            if (v.isNaN() || v.isInfinite()) return null to "「$t」的值不是有限数"
            out.add(v)
        }
        return out to null
    }

    // ---------------- 计算 ----------------

    /** 四分位数:采用卡西欧口径(中位数法,对半分后再各自取中位数) */
    private fun quartiles(sorted: List<Double>): Triple<Double, Double, Double> {
        val n = sorted.size
        if (n == 0) return Triple(Double.NaN, Double.NaN, Double.NaN)
        val median = medianOf(sorted)
        // 去掉中间元素后再对半
        val half = n / 2
        val lower = sorted.subList(0, half)
        val upperStart = if (n % 2 == 0) half else half + 1
        val upper = sorted.subList(upperStart, n)
        val q1 = if (lower.isEmpty()) median else medianOf(lower)
        val q3 = if (upper.isEmpty()) median else medianOf(upper)
        return Triple(q1, median, q3)
    }

    private fun medianOf(sorted: List<Double>): Double {
        val n = sorted.size
        if (n == 0) return Double.NaN
        return if (n % 2 == 1) sorted[n / 2]
        else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
    }

    fun oneVar(data: List<Double>): OneVar {
        require(data.isNotEmpty()) { "没有数据" }
        val n = data.size
        val sum = data.sum()
        val sum2 = data.sumOf { it * it }
        val sum3 = data.sumOf { it * it * it }
        val sum4 = data.sumOf { it * it * it * it }
        val mean = sum / n
        val ss = data.sumOf { (it - mean).pow(2) }
        val popVar = ss / n
        val sampleVar = if (n > 1) ss / (n - 1) else Double.NaN
        val sorted = data.sorted()
        val (q1, med, q3) = quartiles(sorted)
        return OneVar(
            n = n, sumX = sum, sumX2 = sum2, sumX3 = sum3, sumX4 = sum4, mean = mean,
            popVar = popVar, sampleVar = sampleVar,
            popSd = sqrt(popVar), sampleSd = if (sampleVar.isNaN()) Double.NaN else sqrt(sampleVar),
            min = sorted.first(), q1 = q1, median = med, q3 = q3, max = sorted.last()
        )
    }

    fun twoVar(xs: List<Double>, ys: List<Double>): TwoVar {
        require(xs.size == ys.size) { "x 与 y 的个数不一致(${xs.size} vs ${ys.size})" }
        require(xs.isNotEmpty()) { "没有数据" }
        val n = xs.size
        val one = oneVar(xs)
        val sumY = ys.sum()
        val sumY2 = ys.sumOf { it * it }
        val sumXY = xs.indices.sumOf { xs[it] * ys[it] }
        val sumX2Y = xs.indices.sumOf { xs[it] * xs[it] * ys[it] }
        val meanY = sumY / n
        val ssY = ys.sumOf { (it - meanY).pow(2) }
        val popVarY = ssY / n
        val sampleVarY = if (n > 1) ssY / (n - 1) else Double.NaN
        // 相关系数
        val cov = xs.indices.sumOf { (xs[it] - one.mean) * (ys[it] - meanY) }
        val dx = sqrt(xs.sumOf { (it - one.mean).pow(2) })
        val dy = sqrt(ssY)
        val corr = if (dx == 0.0 || dy == 0.0) Double.NaN else cov / (dx * dy)
        return TwoVar(
            one = one, sumY = sumY, sumY2 = sumY2, sumXY = sumXY, sumX2Y = sumX2Y,
            meanY = meanY, popVarY = popVarY, sampleVarY = sampleVarY,
            popSdY = sqrt(popVarY),
            sampleSdY = if (sampleVarY.isNaN()) Double.NaN else sqrt(sampleVarY),
            minY = ys.min(), maxY = ys.max(),
            corr = corr
        )
    }

    // ---------------- 回归 ----------------

    /** 最小二乘:解正规方程(高斯消元,部分主元) */
    private fun solveNormal(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = b.size
        val m = Array(n) { i -> DoubleArray(n + 1) { j -> if (j < n) a[i][j] else b[i] } }
        for (col in 0 until n) {
            var piv = col
            for (r in col + 1 until n) if (abs(m[r][col]) > abs(m[piv][col])) piv = r
            if (abs(m[piv][col]) < 1e-12) return null
            val t = m[col]; m[col] = m[piv]; m[piv] = t
            for (r in 0 until n) {
                if (r == col) continue
                val f = m[r][col] / m[col][col]
                for (c in col..n) m[r][c] -= f * m[col][c]
            }
        }
        return DoubleArray(n) { m[it][n] / m[it][it] }
    }

    /**
     * 按模型做回归。内部把模型线性化为 y' = A + B·u(x) 的形式再最小二乘。
     * 二次回归是三元最小二乘。
     */
    fun regress(model: Model, xs: List<Double>, ys: List<Double>): Regression {
        require(xs.size == ys.size) { "x 与 y 的个数不一致" }
        require(xs.size >= 2) { "至少需要 2 组数据" }
        val tv = twoVar(xs, ys)
        fun fail(msg: String): Nothing = throw IllegalArgumentException(msg)

        return when (model) {
            Model.LINEAR -> {
                val n = xs.size.toDouble()
                val sx = tv.one.sumX; val sy = tv.sumY
                val sxx = tv.one.sumX2; val sxy = tv.sumXY
                val den = n * sxx - sx * sx
                if (abs(den) < 1e-12) fail("x 全相同,无法拟合线性回归")
                val b = (n * sxy - sx * sy) / den
                val a = (sy - b * sx) / n
                Regression(model, listOf(a, b), listOf("A", "B"), tv.corr)
            }
            Model.QUADRATIC -> {
                // y = A + Bx + Cx²  → 三元正规方程
                val n = xs.size
                var s0 = 0.0; var s1 = 0.0; var s2 = 0.0; var s3 = 0.0; var s4 = 0.0
                var t0 = 0.0; var t1 = 0.0; var t2 = 0.0
                for (i in 0 until n) {
                    val x = xs[i]; val y = ys[i]
                    val x2 = x * x
                    s0 += 1.0; s1 += x; s2 += x2; s3 += x2 * x; s4 += x2 * x2
                    t0 += y; t1 += x * y; t2 += x2 * y
                }
                val a3 = arrayOf(
                    doubleArrayOf(s0, s1, s2),
                    doubleArrayOf(s1, s2, s3),
                    doubleArrayOf(s2, s3, s4)
                )
                val sol = solveNormal(a3, doubleArrayOf(t0, t1, t2))
                    ?: fail("数据不足以拟合二次回归(可能 x 取值过少)")
                Regression(model, listOf(sol[0], sol[1], sol[2]), listOf("A", "B", "C"), tv.corr)
            }
            Model.LOG -> {
                if (xs.any { it <= 0 }) fail("对数回归要求所有 x > 0")
                val u = xs.map { ln(it) }
                val r = regress(Model.LINEAR, u, ys)
                Regression(model, r.coeffs, listOf("A", "B"), tv.corr)
            }
            Model.EXP -> {
                if (ys.any { it <= 0 }) fail("指数回归要求所有 y > 0")
                val v = ys.map { ln(it) }
                val r = regress(Model.LINEAR, xs, v)
                val a = Math.exp(r.coeffs[0])
                Regression(model, listOf(a, r.coeffs[1]), listOf("A", "B"), tv.corr)
            }
            Model.POWER -> {
                if (xs.any { it <= 0 } || ys.any { it <= 0 }) fail("幂回归要求所有 x > 0 且 y > 0")
                val u = xs.map { ln(it) }
                val v = ys.map { ln(it) }
                val r = regress(Model.LINEAR, u, v)
                val a = Math.exp(r.coeffs[0])
                Regression(model, listOf(a, r.coeffs[1]), listOf("A", "B"), tv.corr)
            }
            Model.INVERSE -> {
                if (xs.any { it == 0.0 }) fail("倒数回归要求所有 x ≠ 0")
                val u = xs.map { 1.0 / it }
                val r = regress(Model.LINEAR, u, ys)
                Regression(model, r.coeffs, listOf("A", "B"), tv.corr)
            }
        }
    }

    /** 按回归模型估算 y(供绘图或预测用) */
    fun predict(model: Model, coeffs: List<Double>, x: Double): Double = when (model) {
        Model.LINEAR -> coeffs[0] + coeffs[1] * x
        Model.QUADRATIC -> coeffs[0] + coeffs[1] * x + coeffs[2] * x * x
        Model.LOG -> coeffs[0] + coeffs[1] * ln(x)
        Model.EXP -> coeffs[0] * Math.exp(coeffs[1] * x)
        Model.POWER -> coeffs[0] * x.pow(coeffs[1])
        Model.INVERSE -> coeffs[0] + coeffs[1] / x
    }

    // ---------------- 统计编辑器数据(表格式列表) ----------------

    /**
     * 统计编辑器的一行。对应真机的统计编辑器单元格:
     *  · 单变量:x( + 可选频率)
     *  · 双变量:x、y( + 可选频率)
     */
    data class StatRow(
        var x: Double? = null,
        var y: Double? = null,
        var freq: Double? = null
    )

    /**
     * 统计编辑器的数据表。手册:
     *  · 单变量 x 列最多 160 行;x+Freq 或 x,y 80 行;x,y+Freq 53 行
     *  · 支持改单元格、删除行、插入行、全部删除
     *  · 频率列由设置项决定是否显示;频率表示该数据项重复的次数
     */
    class StatData(val twoVar: Boolean) {
        val rows = mutableListOf<StatRow>()

        /** 手册规定的最大行数 */
        val maxRows: Int
            get() = if (twoVar) 80 else 160

        fun addRow(initial: StatRow? = null): Int {
            if (rows.size >= maxRows) return -1
            rows.add(initial ?: StatRow())
            return rows.size - 1
        }

        /** 在 index 处插入一行 */
        fun insertRow(index: Int, initial: StatRow? = null): Int {
            if (rows.size >= maxRows) return -1
            val i = index.coerceIn(0, rows.size)
            rows.add(i, initial ?: StatRow())
            return i
        }

        /** 删除某行;返回是否删成功 */
        fun deleteRow(index: Int): Boolean {
            if (index !in rows.indices) return false
            rows.removeAt(index)
            return true
        }

        fun clear() = rows.clear()

        /** 修改单元格 */
        fun setX(index: Int, v: Double?) {
            rows.getOrNull(index)?.x = v
        }

        fun setY(index: Int, v: Double?) {
            if (twoVar) rows.getOrNull(index)?.y = v
        }

        fun setFreq(index: Int, v: Double?) {
            rows.getOrNull(index)?.freq = v
        }

        /**
         * 展开成实际参与计算的数据。
         * 频率 f 表示该数据项重复 f 次;f 为空视为 1;
         * f 必须是非负整数(真机也只接受整数频率)。
         */
        fun expandSingle(useFreq: Boolean): Pair<List<Double>, String?> {
            val out = mutableListOf<Double>()
            for ((i, r) in rows.withIndex()) {
                val x = r.x ?: continue
                val f = if (useFreq) (r.freq ?: 1.0) else 1.0
                if (f < 0 || f != Math.floor(f)) return Pair<List<Double>, String?>(emptyList(), "第 ${i + 1} 行频率必须是非负整数: $f")
                if (f > 1e6) return Pair<List<Double>, String?>(emptyList(), "第 ${i + 1} 行频率过大")
                repeat(f.toInt()) { out.add(x) }
            }
            if (out.isEmpty()) return Pair<List<Double>, String?>(emptyList(), "没有有效数据")
            return out to null
        }

        /** 双变量展开:返回 (x列表, y列表);频率同样展开 */
        fun expandTwo(useFreq: Boolean): Triple<List<Double>, List<Double>, String?> {
            val xs = mutableListOf<Double>()
            val ys = mutableListOf<Double>()
            for ((i, r) in rows.withIndex()) {
                val x = r.x ?: continue
                val y = r.y ?: continue
                val f = if (useFreq) (r.freq ?: 1.0) else 1.0
                if (f < 0 || f != Math.floor(f)) {
                    return Triple(emptyList(), emptyList(), "第 ${i + 1} 行频率必须是非负整数: $f")
                }
                if (f > 1e6) return Triple(emptyList(), emptyList(), "第 ${i + 1} 行频率过大")
                repeat(f.toInt()) {
                    xs.add(x)
                    ys.add(y)
                }
            }
            if (xs.isEmpty()) return Triple(emptyList(), emptyList(), "没有有效数据")
            return Triple(xs, ys, null)
        }

        /** 已填数据的行数(用于界面显示) */
        fun filledCount(): Int = rows.count { it.x != null }
    }

    // ---------------- 正态分布(仅单变量统计) ----------------

    /**
     * 标准正态分布下侧概率 P(t) = ∫_{-∞}^{t} φ(x)dx。
     *
     * 用连分式/级数混合实现 erf,精度优于 1e-12,足够显示到 9 位小数。
     * 手册里 P/Q/R 的定义:
     *   P(t) = 下侧概率(从 -∞ 积到 t)
     *   Q(t) = 上侧概率(从 t 积到 +∞) = 1 - P(t)
     *   R(t) = 中间概率(从 -t 积到 t)      = 2P(t) - 1
     */
    fun normalP(t: Double): Double {
        if (t.isNaN()) return Double.NaN
        return 0.5 * (1.0 + erf(t / SQRT2))
    }

    /** 上侧概率 Q(t) = 1 - P(t) */
    fun normalQ(t: Double): Double = 1.0 - normalP(t)

    /** 中间概率 R(t) = P(t) - Q(t) = 2P(t) - 1 */
    fun normalR(t: Double): Double = 2.0 * normalP(t) - 1.0

    /**
     * 标准化变量 't = (x - x̄) / (σx 或 sx)。
     * 与真机一致:默认按**样本**标准差 sx 标准化。
     */
    fun standardize(x: Double, data: List<Double>, useSample: Boolean = true): Double {
        val s = oneVar(data)
        val sd = if (useSample) s.sampleSd else s.popSd
        if (sd.isNaN() || sd == 0.0) return Double.NaN
        return (x - s.mean) / sd
    }

    /**
     * 正态分布的概率密度 φ(t)(供可能的显示用)。
     */
    fun normalPdf(t: Double): Double = Math.exp(-t * t / 2.0) / SQRT_2PI

    private val SQRT2 = Math.sqrt(2.0)
    private val SQRT_2PI = Math.sqrt(2.0 * Math.PI)

    /**
     * 误差函数。t 较小时用泰勒级数,较大时用连分式渐近展开,
     * 两段在 |t| ≈ 2 附近衔接,整体精度优于 1e-12。
     */
    fun erf(x: Double): Double {
        if (x == 0.0) return 0.0
        val sign = if (x < 0) -1.0 else 1.0
        val a = abs(x)
        val v = if (a < 2.0) erfSeries(a) else 1.0 - erfcContinued(a)
        return sign * v
    }

    /** erf 的泰勒级数(|x| < 2 收敛良好) */
    private fun erfSeries(x: Double): Double {
        // erf(x) = 2/√π · Σ (-1)^n x^(2n+1) / (n! (2n+1))
        var sum = 0.0
        var term = x
        var n = 0
        while (n < 60) {
            sum += term / (2 * n + 1)
            n++
            term *= -x * x / n
            if (abs(term / (2 * n + 1)) < 1e-18) break
        }
        return 2.0 / Math.sqrt(Math.PI) * sum
    }

    /** 1 - erf(x) 的连分式(用于 x ≥ 2) */
    private fun erfcContinued(x: Double): Double {
        // erfc(x) = exp(-x²)/√π · 1/(x + 1/(2x + 2/(x + 3/(2x + ...))))
        var cf = 0.0
        for (k in 60 downTo 1) {
            cf = (k / 2.0) / (x + cf)
        }
        return Math.exp(-x * x) / Math.sqrt(Math.PI) / (x + cf)
    }

    /** 正态分布的文本结果 */
    fun normalText(t: Double, data: List<Double>): String {
        val s = oneVar(data)
        val rows = mutableListOf<String>()
        rows.add("x̄           ${f(s.mean)}")
        rows.add("样本 sx     ${f(s.sampleSd)}")
        rows.add("总体 σx     ${f(s.popSd)}")
        rows.add("")
        rows.add("t           ${f(t)}")
        rows.add("P(t)        ${f(normalP(t))}")
        rows.add("Q(t)        ${f(normalQ(t))}")
        rows.add("R(t)        ${f(normalR(t))}")
        rows.add("'t          ${f(standardize(t, data, useSample = true))}")
        return Display.box("正态分布", rows)
    }

    // ---------------- 文本输出 ----------------

    private fun f(v: Double): String = Display.fmt(v)

    /** 四舍五入到指定小数位(供自测做稳定比较) */
    fun round(v: Double, digits: Int): Double {
        if (v.isNaN()) return v
        val p = Math.pow(10.0, digits.toDouble())
        return Math.round(v * p) / p
    }

    /** 1 变量统计的文本表 */
    fun oneVarText(data: List<Double>, mode: String = "1"): String {
        val s = oneVar(data)
        val rows = mutableListOf<String>()
        rows.add("数据个数 n   ${s.n}")
        rows.add("Σx          ${f(s.sumX)}")
        rows.add("Σx²         ${f(s.sumX2)}")
        // 高阶和:二次回归与部分统计量要用(手册的"求和"组共 8 项)
        rows.add("Σx³         ${f(s.sumX3)}")
        rows.add("Σx⁴         ${f(s.sumX4)}")
        rows.add("均值 x̄      ${f(s.mean)}")
        rows.add("总体 σ²x     ${f(s.popVar)}")
        rows.add("样本 s²x     ${f(s.sampleVar)}")
        rows.add("总体 σx     ${f(s.popSd)}")
        rows.add("样本 sx     ${f(s.sampleSd)}")
        rows.add("最小值      ${f(s.min)}")
        rows.add("Q1          ${f(s.q1)}")
        rows.add("中位数      ${f(s.median)}")
        rows.add("Q3          ${f(s.q3)}")
        rows.add("最大值      ${f(s.max)}")
        return Display.box("1 变量统计", rows)
    }

    /** 2 变量统计 + 回归的文本表 */
    fun twoVarText(xs: List<Double>, ys: List<Double>, model: Model): String {
        val t = twoVar(xs, ys)
        val rows = mutableListOf<String>()
        rows.add("数据个数 n   ${t.one.n}")
        rows.add("Σx          ${f(t.one.sumX)}")
        rows.add("Σx²         ${f(t.one.sumX2)}")
        rows.add("Σy          ${f(t.sumY)}")
        rows.add("Σy²         ${f(t.sumY2)}")
        rows.add("Σxy         ${f(t.sumXY)}")
        rows.add("Σx²y        ${f(t.sumX2Y)}")
        rows.add("均值 x̄      ${f(t.one.mean)}")
        rows.add("均值 ȳ      ${f(t.meanY)}")
        rows.add("总体 σ²x     ${f(t.one.popVar)}")
        rows.add("样本 s²x     ${f(t.one.sampleVar)}")
        rows.add("总体 σ²y     ${f(t.popVarY)}")
        rows.add("样本 s²y     ${f(t.sampleVarY)}")
        rows.add("总体 σx     ${f(t.one.popSd)}")
        rows.add("样本 sx     ${f(t.one.sampleSd)}")
        rows.add("总体 σy     ${f(t.popSdY)}")
        rows.add("样本 sy     ${f(t.sampleSdY)}")
        rows.add("最小/最大 y ${f(t.minY)} / ${f(t.maxY)}")
        rows.add("相关系数 r  ${f(t.corr)}")
        rows.add("")
        rows.add("回归模型    ${model.label}")
        val r = regress(model, xs, ys)
        r.coeffs.forEachIndexed { i, c -> rows.add("${r.coeffNames[i]}          ${f(c)}") }
        return Display.box("2 变量统计", rows)
    }
}
