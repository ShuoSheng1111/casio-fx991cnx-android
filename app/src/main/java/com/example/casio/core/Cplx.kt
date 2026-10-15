package com.example.casio.core

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * 极简复数运算(仅供 Durand-Kerner 等数值求根使用)。
 * 不追求功能全面,只要够算多项式复根。
 */
data class Cplx(val re: Double, val im: Double) {

    companion object {
        val ZERO = Cplx(0.0, 0.0)
        val ONE = Cplx(1.0, 0.0)

        fun of(re: Double) = Cplx(re, 0.0)
    }

    val isReal: Boolean get() = im == 0.0
    val abs: Double get() = hypot(re, im)

    operator fun plus(o: Cplx) = Cplx(re + o.re, im + o.im)
    operator fun minus(o: Cplx) = Cplx(re - o.re, im - o.im)
    operator fun unaryMinus() = Cplx(-re, -im)

    operator fun times(o: Cplx) = Cplx(re * o.re - im * o.im, re * o.im + im * o.re)

    operator fun div(o: Cplx): Cplx {
        val d = o.re * o.re + o.im * o.im
        if (d == 0.0) throw ArithmeticException("复数除零")
        return Cplx((re * o.re + im * o.im) / d, (im * o.re - re * o.im) / d)
    }

    fun scale(k: Double) = Cplx(re * k, im * k)

    /**
     * 复数开平方(主值)。用于二次方程判别式为负的情形。
     */
    fun sqrt(): Cplx {
        val r = abs
        if (r == 0.0) return ZERO
        val sr = sqrt((r + re) / 2.0)
        val si = if (im < 0) -sqrt((r - re) / 2.0) else sqrt((r - re) / 2.0)
        return Cplx(sr, si)
    }

    override fun toString(): String = "%.10g%+.10gi".format(re, im)
}

/** 复数小工具 */
object CplxMath {
    /** 是否已经把根磨到足够精确 */
    fun close(a: Cplx, b: Cplx, tol: Double): Boolean = (a - b).abs <= tol

    fun almostReal(z: Cplx, tol: Double = 1e-7): Boolean =
        abs(z.im) <= tol * maxOf(1.0, abs(z.re))
}
