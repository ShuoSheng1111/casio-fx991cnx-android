package com.example.casio.core

import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 数值求根:
 *   - rationalRoots   有理根定理(精确)
 *   - durandKerner    同时求全部实根与复根
 *   - newtonPolish    牛顿法磨精确
 *   - numericalRoots  区间扫描 + 多起点牛顿法(用于超越方程)
 * 对应 Python 版的 durand_kerner / rational_roots / numerical_roots。
 */
object NumericRoots {

    /** 有理根定理:返回所有有理根(精确)。coeffs 为 [c0, c1, ...](常数项在前) */
    fun rationalRoots(coeffs: List<Rational>): List<Rational> {
        var cs = coeffs.toMutableList()
        while (cs.size > 1 && cs.last().isZero) cs.removeAt(cs.size - 1)
        if (cs.size < 2) return emptyList()
        if (cs.first().isZero) return listOf(Rational.ZERO)

        // 清分母,转成整数系数
        var lcm = BigInteger.ONE
        for (c in cs) {
            val d = c.den
            lcm = lcm / lcm.gcd(d) * d
        }
        val ints = cs.map { it.num * (lcm / it.den) }
        val a0 = ints.first()
        val an = ints.last()

        val ps = divisors(a0)
        val qs = divisors(an)
        if (ps.size.toLong() * qs.size.toLong() > 200_000L) return emptyList()

        val found = mutableListOf<Rational>()
        for (p in ps) for (q in qs) for (sign in listOf(1, -1)) {
            val cand = Rational.of(BigInteger.valueOf(sign.toLong()) * p, q)
            if (cand in found) continue
            if (evalExact(ints, cand).isZero) found.add(cand)
        }
        return found.sorted()
    }

    fun divisors(n: BigInteger): List<BigInteger> {
        val v = n.abs()
        if (v.signum() == 0) return listOf(BigInteger.ONE)
        val small = mutableListOf<BigInteger>()
        val large = mutableListOf<BigInteger>()
        var d = BigInteger.ONE
        val cap = 20_000
        while (d * d <= v) {
            if (v.mod(d).signum() == 0) {
                small.add(d)
                val other = v.divide(d)
                if (other != d) large.add(other)
            }
            if (small.size + large.size > cap) break
            d = d + BigInteger.ONE
        }
        large.reverse()
        return small + large
    }

    private fun evalExact(ints: List<BigInteger>, x: Rational): Rational {
        var acc = Rational.ZERO
        for (c in ints) acc = acc * x + Rational.of(c)
        return acc
    }

    // ---------------- Durand-Kerner ----------------

    /** coeffs 为 [c0, c1, ...](常数项在前),返回全部复根 */
    fun durandKerner(coeffsLow: List<Double>, iters: Int = 2000, tol: Double = 1e-14): List<Cplx> {
        var hi = coeffsLow.reversed().map { Cplx.of(it) }.toMutableList()
        while (hi.size > 1 && hi.first().abs < 1e-300) hi.removeAt(0)
        val n = hi.size - 1
        if (n <= 0) return emptyList()
        val lead = hi.first()
        val cs = hi.map { it / lead }
        if (n == 1) return listOf(-cs[1])
        if (n == 2) {
            val b = cs[1]
            val c = cs[2]
            val d = b * b - Cplx.of(4.0) * c
            val sq = d.sqrt()
            return listOf((-b + sq) / Cplx.of(2.0), (-b - sq) / Cplx.of(2.0))
        }

        fun p(z: Cplx): Cplx {
            var acc = Cplx.ZERO
            for (c in cs) acc = acc * z + c
            return acc
        }

        val radius = 1.0 + cs.drop(1).maxOf { it.abs }
        val seed = Cplx(0.4, 0.9)
        val roots = MutableList(n) { i ->
            var z = Cplx.ONE
            repeat(i) { z *= seed }
            z.scale(Math.pow(radius, 1.0 / n))
        }

        for (iter in 0 until iters) {
            var maxDelta = 0.0
            for (i in 0 until n) {
                var den = Cplx.ONE
                for (j in 0 until n) if (i != j) den *= (roots[i] - roots[j])
                if (den.abs < 1e-300) {
                    roots[i] = roots[i] + Cplx(1e-8, 1e-8)
                    continue
                }
                val delta = p(roots[i]) / den
                roots[i] = roots[i] - delta
                maxDelta = max(maxDelta, delta.abs)
            }
            if (maxDelta < tol) break
        }
        return roots.map { newtonPolish(cs, it) }
    }

    /** 用牛顿法把根磨精确 */
    fun newtonPolish(coeffsHi: List<Cplx>, z0: Cplx, iters: Int = 60): Cplx {
        if (coeffsHi.size < 2) return z0
        val n = coeffsHi.size - 1
        val d = List(n) { i -> coeffsHi[i].scale((n - i).toDouble()) }
        var z = z0
        repeat(iters) {
            val p = evalC(coeffsHi, z)
            val dp = evalC(d, z)
            if (dp.abs < 1e-300) return z
            val step = p / dp
            z -= step
            if (step.abs < 1e-16 * max(1.0, z.abs)) return z
        }
        return z
    }

    fun evalC(coeffs: List<Cplx>, z: Cplx): Cplx {
        var acc = Cplx.ZERO
        for (c in coeffs) acc = acc * z + c
        return acc
    }

    data class Classified(val real: List<Double>, val complexPairs: List<Pair<Double, Double>>)

    /** 把复根分类成实根与复数对,并剔除伪根与重复根 */
    fun classify(rootsIn: List<Cplx>, coeffsHi: List<Cplx>, tol: Double = 1e-7): Classified {
        val real = mutableListOf<Double>()
        val comp = mutableListOf<Pair<Double, Double>>()
        val seen = mutableListOf<Cplx>()
        val scale = coeffsHi.maxOfOrNull { it.abs } ?: 1.0

        for (zRaw in rootsIn) {
            val z = newtonPolish(coeffsHi, zRaw)
            if (seen.any { (z - it).abs < 1e-6 * max(1.0, z.abs) }) continue
            seen.add(z)
            val p = evalC(coeffsHi, z).abs
            val bound = 1e-4 * scale * Math.pow(max(1.0, z.abs), (coeffsHi.size - 1).toDouble())
            if (p > bound) continue                       // 伪根
            if (abs(z.im) <= tol * max(1.0, abs(z.re))) real.add(z.re)
            else if (z.im > 0) comp.add(z.re to abs(z.im))
        }
        real.sort()
        comp.sortBy { it.first }
        return Classified(real, comp)
    }

    // ---------------- 超越方程:扫描 + 牛顿 ----------------

    private val X_START = listOf(0.0, 1.0, -1.0, 2.0, -2.0, 3.0, -3.0, 5.0, -5.0, 10.0, -10.0)

    /**
     * 求 f(x)=0 在 [-span, span] 内的所有实根。
     * wide=true 时逐级加宽窗口(用于根离原点很远的方程);
     * 默认不加宽,因为 sin/tan 这类周期函数会让「根在边界」恒成立。
     */
    fun numericalRoots(
        f: (Double) -> Double,
        span: Double = 100.0,
        samples: Int = 2000,
        wide: Boolean = false,
        maxSpan: Double = 1e6
    ): List<Double> {
        val roots = mutableListOf<Double>()

        fun add(r: Double) {
            if (r.isNaN() || r.isInfinite()) return
            if (roots.none { abs(it - r) < 1e-8 * max(1.0, abs(r)) }) roots.add(r)
        }

        fun derivative(x: Double): Double {
            val h = 1e-6 * max(1.0, abs(x))
            return (f(x + h) - f(x - h)) / (2 * h)
        }

        var cur = span
        val rounds = if (wide) 6 else 1
        repeat(rounds) {
            val grid = DoubleArray(samples + 1) { i -> -cur + 2 * cur * i / samples }
            var prevX = Double.NaN
            var prevY = Double.NaN
            for (x in grid) {
                val y = runCatching { f(x) }.getOrNull()
                if (y == null || y.isNaN() || y.isInfinite()) {
                    prevX = Double.NaN; prevY = Double.NaN; continue
                }
                if (y == 0.0) add(x)
                if (!prevY.isNaN() && prevY * y < 0) {
                    bisect(f, prevX, x)?.let { add(it) }
                }
                prevX = x; prevY = y
            }
            for (x0 in X_START) newton(f, ::derivative, x0)?.let { if (abs(it) <= cur * 1.01) add(it) }
            var i = 0
            while (i < grid.size) {
                newton(f, ::derivative, grid[i])?.let { if (abs(it) <= cur * 1.01) add(it) }
                i += max(1, samples / 60)
            }
            cur = minOf(cur * 10, maxSpan)
        }
        val bound = if (wide) cur * 1.01 else span * 1.01
        return roots.filter { abs(it) <= bound }.sorted()
    }

    fun bisect(f: (Double) -> Double, a0: Double, b0: Double, iters: Int = 200): Double? {
        var a = a0
        var b = b0
        var fa = runCatching { f(a) }.getOrNull() ?: return null
        val fb = runCatching { f(b) }.getOrNull() ?: return null
        if (fa == 0.0) return a
        if (fb == 0.0) return b
        if (fa * fb > 0) return null
        repeat(iters) {
            val m = (a + b) / 2
            val fm = runCatching { f(m) }.getOrNull() ?: return null
            if (fm == 0.0 || abs(b - a) < 1e-15 * max(1.0, abs(m))) return m
            if (fa * fm < 0) {
                b = m
            } else {
                a = m; fa = fm
            }
        }
        return (a + b) / 2
    }

    fun newton(
        f: (Double) -> Double,
        df: (Double) -> Double,
        x0: Double,
        iters: Int = 200,
        tol: Double = 1e-14
    ): Double? {
        var x = x0
        repeat(iters) {
            val y = runCatching { f(x) }.getOrNull() ?: return null
            if (y.isNaN() || y.isInfinite()) return null
            val d = runCatching { df(x) }.getOrNull() ?: return null
            if (d == 0.0 || d.isNaN()) {
                x += 1e-6
                return@repeat
            }
            val xn = x - y / d
            if (abs(xn - x) < tol * max(1.0, abs(xn))) {
                x = xn
                return finish(f, x)
            }
            if (abs(xn) > 1e12) return null
            x = xn
        }
        return finish(f, x)
    }

    private fun finish(f: (Double) -> Double, x: Double): Double? {
        val y = runCatching { f(x) }.getOrNull() ?: return null
        if (y.isNaN()) return null
        if (abs(y) > 1e-6 * max(1.0, abs(x))) return null
        return x
    }

    /** 把数值根还原成简洁分数(如 2.0 -> 2,0.5 -> 1/2);不够干净返回 null */
    fun toNiceRational(x: Double, maxDen: Long = 1000): Rational? {
        val f = runCatching { Rational.fromDouble(x, maxDen) }.getOrNull() ?: return null
        if (abs(f.toDouble() - x) > 1e-9 * max(1.0, abs(x))) return null
        if (f.den.toLong() > maxDen) return null
        return f
    }

    fun sqrtDouble(v: Double): Double = sqrt(v)
}
