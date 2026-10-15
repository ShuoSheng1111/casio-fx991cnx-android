package com.example.casio.core

import java.math.BigInteger

/**
 * 精确有理数(对应 Python 版的 fractions.Fraction)。
 * 用于多项式系数、精确根、方程组消元,避免浮点误差。
 */
class Rational private constructor(
    val num: BigInteger,
    val den: BigInteger
) : Comparable<Rational> {

    companion object {
        val ZERO = Rational(BigInteger.ZERO, BigInteger.ONE)
        val ONE = Rational(BigInteger.ONE, BigInteger.ONE)

        fun of(n: Long): Rational = of(BigInteger.valueOf(n))
        fun of(n: BigInteger): Rational = Rational(n, BigInteger.ONE)

        fun of(n: Long, d: Long): Rational = of(BigInteger.valueOf(n), BigInteger.valueOf(d))

        fun of(n: BigInteger, d: BigInteger): Rational {
            require(d.signum() != 0) { "分母不能为 0" }
            var nn = n
            var dd = d
            if (dd.signum() < 0) {
                nn = nn.negate()
                dd = dd.negate()
            }
            val g = nn.gcd(dd)
            if (g > BigInteger.ONE) {
                nn = nn.divide(g)
                dd = dd.divide(g)
            }
            return Rational(nn, dd)
        }

        /**
         * 由浮点数还原成简洁分数(连分数 + 半收敛项,对应 Python 的 limit_denominator)。
         * 例:0.5 -> 1/2,0.333... -> 1/3,-2.0 -> -2
         */
        fun fromDouble(x: Double, maxDen: Long = 1_000_000L): Rational {
            if (x.isNaN() || x.isInfinite()) throw IllegalArgumentException("不是有限数")
            if (x == 0.0) return ZERO
            val neg = x < 0
            var v = kotlin.math.abs(x)
            var h1 = BigInteger.ONE
            var h0 = BigInteger.ZERO
            var k1 = BigInteger.ZERO
            var k0 = BigInteger.ONE
            var value = v
            var iter = 0
            while (iter++ < 64) {
                val a = kotlin.math.floor(value).toLong()
                val h2 = BigInteger.valueOf(a) * h1 + h0
                val k2 = BigInteger.valueOf(a) * k1 + k0
                if (k2 > BigInteger.valueOf(maxDen)) {
                    // 用半收敛项收尾,取更接近的那个
                    val t = (BigInteger.valueOf(maxDen) - k0).divide(k1)
                    val hh = t * h1 + h0
                    val kk = t * k1 + k0
                    val r1 = Rational.of(h1, k1)
                    val r2 = Rational.of(hh, kk)
                    val target = v
                    val pick = if (kotlin.math.abs(r1.toDouble() - target) <=
                        kotlin.math.abs(r2.toDouble() - target)
                    ) r1 else r2
                    val e = kotlin.math.abs(target - kotlin.math.floor(target))
                    if (e < 1e-12) return of(BigInteger.valueOf(kotlin.math.floor(target).toLong()))
                    return if (neg) -pick else pick
                }
                h0 = h1; h1 = h2
                k0 = k1; k1 = k2
                val frac = value - a
                if (frac < 1e-12) break
                value = 1.0 / frac
            }
            val r = Rational.of(h1, k1)
            return if (neg) -r else r
        }
    }

    val isZero: Boolean get() = num.signum() == 0
    val isOne: Boolean get() = num == BigInteger.ONE && den == BigInteger.ONE
    val isInteger: Boolean get() = den == BigInteger.ONE
    val signum: Int get() = num.signum()

    operator fun plus(o: Rational): Rational =
        of(num * o.den + o.num * den, den * o.den)

    operator fun minus(o: Rational): Rational =
        of(num * o.den - o.num * den, den * o.den)

    operator fun times(o: Rational): Rational = of(num * o.num, den * o.den)

    operator fun div(o: Rational): Rational {
        require(!o.isZero) { "除数不能为 0" }
        return of(num * o.den, den * o.num)
    }

    operator fun unaryMinus(): Rational = Rational(num.negate(), den)

    /** 整数次幂(负次幂取倒数) */
    fun pow(e: Int): Rational {
        if (e == 0) return ONE
        if (e < 0) {
            require(!isZero) { "0 不能取负次幂" }
            return of(den.pow(-e), num.pow(-e))
        }
        return of(num.pow(e), den.pow(e))
    }

    fun abs(): Rational = if (signum < 0) -this else this

    fun toDouble(): Double = num.toDouble() / den.toDouble()

    val isPerfectSquare: Boolean
        get() = num.signum() >= 0 && isPerfectSquare(num) && isPerfectSquare(den)

    /** 整数平方根(要求完全平方) */
    fun exactSqrt(): Rational {
        require(isPerfectSquare) { "不是完全平方数" }
        return of(num.sqrt(), den.sqrt())
    }

    override fun compareTo(other: Rational): Int =
        (num * other.den).compareTo(other.num * den)

    override fun equals(other: Any?): Boolean =
        other is Rational && num == other.num && den == other.den

    override fun hashCode(): Int = 31 * num.hashCode() + den.hashCode()

    /** 显示成 "3" 或 "1/2" */
    override fun toString(): String =
        if (isInteger) num.toString() else "$num/$den"

    private fun BigInteger.sqrt(): BigInteger {
        var x = BigInteger.ONE.shiftLeft((bitLength() + 1) / 2)
        while (true) {
            val y = x.add(this.divide(x)).shiftRight(1)
            if (y >= x) break
            x = y
        }
        return x
    }

    private fun isPerfectSquare(v: BigInteger): Boolean {
        if (v.signum() < 0) return false
        if (v.signum() == 0) return true
        val r = v.sqrt()
        return r * r == v
    }
}
