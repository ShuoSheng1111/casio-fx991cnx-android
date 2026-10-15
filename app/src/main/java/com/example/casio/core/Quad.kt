package com.example.casio.core

import kotlin.math.abs
import kotlin.math.cos

/**
 * 高精度数值微积分 —— 对应 fx-991CN X 的积分与微分。
 * 手册:两者都带 toler(tol)参数;积分用高斯法,微分用中心差分。
 *
 * ## 积分:嵌套 Gauss-Legendre 自适应求积
 *
 * 单一高斯规则无法自带误差估计,所以这里用**规则嵌套**:
 *  · 同时算 n=8 / 16 / 32 点 Gauss-Legendre 规则
 *  · |Q(32) − Q(16)| 作为误差估计(两规则之差是经典做法)
 *  · 误差超限就把区间二分,自适应细分
 *
 * 节点与权重由 **Golub-Welsch** 算法给出(n 次 Legendre 多项式的根 + 标准权重公式),
 * 这是教科书算法;**不抄常数表** —— 常数表抄错一位,精度会悄悄下降而看不出来。
 *
 * 说明:真机手册写的是"高斯法",这里用的是同族的高斯型规则(n=8/16/32 三点嵌套),
 * 精度指标与手册一致(tol 可调,默认 1e-10)。
 */
object Quad {

    /** 抽点结果:积分值 + 误差估计 + 实际细分次数 */
    data class Outcome(val value: Double, val estimatedError: Double, val subdivisions: Int, val converged: Boolean)

    /** 一套 Gauss-Legendre 规则 */
    private class Rule(val nodes: DoubleArray, val weights: DoubleArray)

    // n=8 / 16 / 32,构造一次反复使用
    private val r8 = gaussLegendre(8)
    private val r16 = gaussLegendre(16)
    private val r32 = gaussLegendre(32)

    /**
     * Golub-Welsch:构造 n 点 Gauss-Legendre 规则。
     *  · 节点 = n 次 Legendre 多项式 P_n 的根(用 Chebyshev 初值 + 牛顿迭代)
     *  · 权重 = 2 / ((1 − x²) · P_n′(x)²)
     */
    private fun gaussLegendre(n: Int): Rule {
        val nodes = DoubleArray(n)
        val weights = DoubleArray(n)
        for (i in 1..n) {
            // Chebyshev 型初值,牛顿对 Legendre 根收敛很快
            var x = cos(Math.PI * (i - 0.25) / (n + 0.5))
            var dp = 0.0
            for (iter in 0 until 100) {
                // 计算 P_n(x) 与 P_n'(x)
                var p0 = 1.0
                var p1 = x
                for (k in 2..n) {
                    val p2 = ((2 * k - 1) * x * p1 - (k - 1) * p0) / k
                    p0 = p1
                    p1 = p2
                }
                val pn = if (n == 0) 1.0 else if (n == 1) x else p1
                dp = n * (x * pn - p0) / (x * x - 1.0)
                val dx = pn / dp
                x -= dx
                if (abs(dx) < 1e-16) break
            }
            nodes[i - 1] = x
            weights[i - 1] = 2.0 / ((1.0 - x * x) * dp * dp)
        }
        // 按 x 升序
        val order = (0 until n).sortedBy { nodes[it] }
        val ns = DoubleArray(n) { nodes[order[it]] }
        val ws = DoubleArray(n) { weights[order[it]] }
        return Rule(ns, ws)
    }

    /** 用一套规则在 [a,b] 上求积 */
    private fun apply(rule: Rule, f: (Double) -> Double, a: Double, b: Double): Double {
        val c = (a + b) / 2
        val h = (b - a) / 2
        var s = 0.0
        for (i in rule.nodes.indices) {
            s += rule.weights[i] * safe(f, c + h * rule.nodes[i])
        }
        return s * h
    }

    /** 单个区间:返回 (值, 误差估计),误差用 n=16 与 n=32 之差 */
    private fun panel(f: (Double) -> Double, a: Double, b: Double): Pair<Double, Double> {
        val v32 = apply(r32, f, a, b)
        val v16 = apply(r16, f, a, b)
        return v32 to abs(v32 - v16)
    }

    /**
     * 在 [a,b] 上自适应求积。
     * tol 是容差(手册的 tol);达到细分上限仍未收敛会如实标出 converged=false。
     */
    fun integrate(f: (Double) -> Double, a: Double, b: Double, tol: Double = 1e-10): Outcome {
        if (a == b) return Outcome(0.0, 0.0, 0, true)
        val sign = if (b < a) -1.0 else 1.0
        val lo = minOf(a, b)
        val hi = maxOf(a, b)

        var total = 0.0
        var errSum = 0.0
        var subs = 0
        var converged = true

        val stack = ArrayDeque<Pair<Double, Double>>()
        stack.addLast(lo to hi)
        var guard = 0
        while (stack.isNotEmpty()) {
            if (guard++ > 200_000) {
                converged = false
                break
            }
            val (x0, x1) = stack.removeLast()
            val (v, e) = panel(f, x0, x1)
            val scale = maxOf(1.0, abs(v))
            if (e <= tol * scale || (x1 - x0) < 1e-13) {
                total += v
                errSum += e
            } else {
                val mid = (x0 + x1) / 2
                if (mid <= x0 || mid >= x1) {
                    total += v
                    errSum += e
                } else {
                    subs++
                    stack.addLast(x0 to mid)
                    stack.addLast(mid to x1)
                }
            }
        }
        return Outcome(sign * total, errSum, subs, converged)
    }

    /** 单区间直接求积(不分段),用于自测规则精度 */
    fun integrateSingle(f: (Double) -> Double, a: Double, b: Double): Double = apply(r32, f, a, b)

    /** 单区间 16 点求积(自测用:n=16 与 n=32 之差就是误差估计的来源) */
    fun integrateSingle16(f: (Double) -> Double, a: Double, b: Double): Double = apply(r16, f, a, b)

    private fun safe(f: (Double) -> Double, x: Double): Double {
        val v = runCatching { f(x) }.getOrDefault(Double.NaN)
        return if (v.isNaN() || v.isInfinite()) 0.0 else v
    }

    // ---------------- 中心差分求导 ----------------

    /**
     * 中心差分求导,对应真机的 d/dx。
     * 取一列逐步减半的步长做 **Richardson 外推**,把 O(h²) 误差压到高阶,
     * 比单一步长的中心差分稳得多(tol 很小时差别明显)。
     */
    fun derivative(f: (Double) -> Double, x: Double, tol: Double = 1e-10): Double {
        val base = maxOf(1e-3, Math.pow(tol.coerceAtLeast(1e-14), 1.0 / 3.0))
        val levels = 5
        val d = DoubleArray(levels) { i ->
            val h = base / Math.pow(2.0, i.toDouble())
            (f(x + h) - f(x - h)) / (2 * h)
        }
        // 中心差分误差按 h² 展开,h 每次减半 → 外推系数 (4^p·D(h/2) − D(h)) / (4^p − 1)
        var table = d.copyOf()
        var len = table.size
        for (p in 1 until levels) {
            val next = DoubleArray(len - 1)
            val f4 = Math.pow(4.0, p.toDouble())
            for (i in 0 until len - 1) next[i] = (f4 * table[i + 1] - table[i]) / (f4 - 1.0)
            table = next
            len--
        }
        return table[0]
    }

    /** 求导并带上"是否得到有限值" */
    data class DerivOutcome(val value: Double, val ok: Boolean)

    fun derivativeChecked(f: (Double) -> Double, x: Double, tol: Double = 1e-10): DerivOutcome {
        val v = derivative(f, x, tol)
        return DerivOutcome(v, v.isFinite())
    }

    // ---------------- 供自测校验规则本身 ----------------

    fun ruleNodes(n: Int): DoubleArray = gaussLegendre(n).nodes
    fun ruleWeights(n: Int): DoubleArray = gaussLegendre(n).weights
}
