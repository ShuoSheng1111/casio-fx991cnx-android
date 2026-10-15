package com.example.casio.core

import java.math.BigInteger

/**
 * 二次方程的精确根式显示。
 * 目标:把 ax²+bx+c=0 的解写成「1/2 + √5/2」这种人类可读的精确形式,
 * 而不是 1.6180339887。判别式为负时给复数精确式。
 * 对应 Python 版的 exact_quadratic_text / _radical_str。
 */
object ExactRoots {

    data class Root(val constant: Rational, val radicalCoef: Rational, val radicalRadicand: Int,
                    val imaginary: Boolean)

    /**
     * 把 a±b 形式的解拆成「有理部分 + 根式部分」。
     * 返回 (常数部分, 根式系数, 根号内的数, 是否虚数)
     */
    fun decompose(a: Rational, b: Rational, c: Rational): Root {
        val disc = b * b - Rational.of(4) * a * c
        val den = Rational.of(2) * a
        val neg = disc.signum < 0
        val absDisc = disc.abs()
        // 统一符号:结果 = base/denAbs ± sqrt(|D|)/denAbs
        val base = if (den.signum < 0) b else -b
        val denAbs = den.abs()
        val constPart = base / denAbs

        if (disc.isZero) return Root(constPart, Rational.ZERO, 1, false)

        // 判别式为完全平方:根是有理数(radicalRadicand = 1 表示纯有理)
        if (absDisc.isPerfectSquare) {
            val sq = absDisc.exactSqrt() / denAbs
            return if (neg) Root(constPart, sq, -1, true)   // 纯虚数
            else Root(constPart, sq, 1, false)
        }

        // sqrt(p/q) = sqrt(p*q)/q
        val (k, m) = simplifySqrt(absDisc.num * absDisc.den)
        val rad = Rational.of(k) / (Rational.of(absDisc.den) * denAbs)
        return Root(constPart, rad, m, neg)
    }

    /** 把 sqrt(D) 写成 k*sqrt(m):从 D 里抽出最大的平方因子 */
    fun simplifySqrt(d: BigInteger): Pair<BigInteger, Int> {
        if (d.signum() < 0) throw IllegalArgumentException("负数")
        var k = BigInteger.ONE
        var m = d
        var i = BigInteger.valueOf(2)
        val iMax = BigInteger.valueOf(100_000)
        while (i * i <= m && i <= iMax) {
            val sq = i * i
            while (m.mod(sq).signum() == 0) {
                m = m.divide(sq)
                k = k * i
            }
            i = i + BigInteger.ONE
        }
        return k to m.toInt()
    }

    /** 生成解的文本,形如「x = 1/2 + √5/2  或  x = 1/2 - √5/2」 */
    fun format(variable: String, a: Rational, b: Rational, c: Rational): List<String> {
        val disc = b * b - Rational.of(4) * a * c
        if (disc.isZero) {
            val r = (-b) / (Rational.of(2) * a)
            return listOf("$variable = $r   (重根)")
        }
        val root = decompose(a, b, c)
        val constText = root.constant.toString()

        // 情形一:radicalRadicand == 1 且非虚 —— 两个有理根
        if (root.radicalRadicand == 1 && !root.imaginary) {
            val r1 = root.constant - root.radicalCoef
            val r2 = root.constant + root.radicalCoef
            return if (r1 == r2) listOf("$variable = $r1   (重根)")
            else listOf("$variable = $r1  或  $variable = $r2")
        }

        // 情形二:radicalRadicand == -1 —— 判别式为完全平方的负数,纯虚数
        if (root.radicalRadicand == -1) {
            val im = imagText(root.radicalCoef)
            return if (root.constant.isZero) {
                listOf("$variable = $im  或  $variable = -$im")
            } else {
                listOf("$variable = $constText + $im  或  $variable = $constText - $im")
            }
        }

        // 情形三:需要含根号的精确式
        val radText = radicalText(root.radicalCoef, root.radicalRadicand)
        return when {
            root.imaginary -> {
                if (root.constant.isZero) {
                    listOf("$variable = ${radText}i  或  $variable = -${radText}i")
                } else {
                    listOf(
                        "$variable = $constText + ${radText}i  或  " +
                            "$variable = $constText - ${radText}i"
                    )
                }
            }
            root.constant.isZero -> {
                listOf("$variable = $radText  或  $variable = -$radText")
            }
            else -> {
                listOf(
                    "$variable = $constText + $radText  或  " +
                        "$variable = $constText - $radText"
                )
            }
        }
    }

    /** 纯虚数的文本:1/2 -> "1/2i",2 -> "2i",1 -> "i" */
    private fun imagText(im: Rational): String {
        val body = im.abs().toString()
        return if (body == "1") "i" else "${body}i"
    }

    /** 形如「√5」「3√2/2」「2」「1/2」的根式文本 */
    fun radicalText(coef: Rational, radicand: Int, imaginary: Boolean = false): String {
        val suffix = if (imaginary) "i" else ""
        if (radicand == 1) {
            return coef.toString() + suffix
        }
        val k = coef.num
        val d = coef.den
        val g = k.gcd(d)
        val kn = k.divide(g)
        val dd = d.divide(g)
        val body = when {
            kn == BigInteger.ONE -> "√$radicand"
            else -> "${kn}√$radicand"
        }
        return when {
            dd == BigInteger.ONE -> body + suffix
            else -> "$body/$dd" + suffix
        }
    }
}
