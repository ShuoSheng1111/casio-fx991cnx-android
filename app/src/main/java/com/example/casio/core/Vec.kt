package com.example.casio.core

import kotlin.math.acos
import kotlin.math.sqrt

/**
 * 向量计算 —— 对应 fx-991CN X 的向量模式。
 *
 * 手册能力:
 *  · 二维 / 三维向量
 *  · 向量变量 VctA / VctB / VctC / VctD,以及结果存储器 VctAns
 *  · 运算:加、减、纯量乘、**内积**(点积)、**叉积**(仅三维)、
 *          **绝对值(模)**、**两向量夹角**、**单位向量**
 */
data class Vector(val dim: Int, val data: DoubleArray) {

    init {
        require(dim == 2 || dim == 3) { "向量维度必须是 2 或 3,当前:$dim" }
        require(data.size == dim) { "元素个数与维度不符" }
    }

    operator fun get(i: Int): Double = data[i]

    fun copy(): Vector = Vector(dim, data.copyOf())

    companion object {
        fun of2(x: Double, y: Double) = Vector(2, doubleArrayOf(x, y))
        fun of3(x: Double, y: Double, z: Double) = Vector(3, doubleArrayOf(x, y, z))
        fun zeros(dim: Int) = Vector(dim, DoubleArray(dim))
    }
}

/** 向量运算。维度不符时抛 IllegalArgumentException,原因用中文写清。 */
object Vec {

    private const val EPS = 1e-12

    private fun checkSame(a: Vector, b: Vector) {
        require(a.dim == b.dim) { "维度不一致:${a.dim} 维与 ${b.dim} 维不能这样运算" }
    }

    fun add(a: Vector, b: Vector): Vector {
        checkSame(a, b)
        return Vector(a.dim, DoubleArray(a.dim) { a[it] + b[it] })
    }

    fun sub(a: Vector, b: Vector): Vector {
        checkSame(a, b)
        return Vector(a.dim, DoubleArray(a.dim) { a[it] - b[it] })
    }

    fun scale(a: Vector, k: Double): Vector =
        Vector(a.dim, DoubleArray(a.dim) { a[it] * k })

    /** 内积(点积):可对所有维度 */
    fun dot(a: Vector, b: Vector): Double {
        checkSame(a, b)
        var s = 0.0
        for (i in 0 until a.dim) s += a[i] * b[i]
        return s
    }

    /** 叉积:手册规定仅三维向量支持 */
    fun cross(a: Vector, b: Vector): Vector {
        require(a.dim == 3 && b.dim == 3) {
            "叉积只支持三维向量(当前 ${a.dim} 维与 ${b.dim} 维)"
        }
        return Vector.of3(
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]
        )
    }

    /** 模 |Vct| */
    fun norm(a: Vector): Double {
        var s = 0.0
        for (i in 0 until a.dim) s += a[i] * a[i]
        return sqrt(s)
    }

    /**
     * 两向量的夹角(角度按当前角度单位返回由调用方处理,这里返回弧度)。
     * 零向量没有方向,报错。
     */
    fun angle(a: Vector, b: Vector): Double {
        checkSame(a, b)
        val na = norm(a)
        val nb = norm(b)
        require(na > EPS && nb > EPS) { "零向量没有方向,无法计算夹角" }
        val cos = (dot(a, b) / (na * nb)).coerceIn(-1.0, 1.0)
        return acos(cos)
    }

    /** 单位向量 */
    fun unit(a: Vector): Vector {
        val n = norm(a)
        require(n > EPS) { "零向量无法单位化" }
        return Vector(a.dim, DoubleArray(a.dim) { a[it] / n })
    }

    /** 显示用:(1, 2) 或 (1, 2, 3) */
    fun format(a: Vector): String =
        "(" + (0 until a.dim).joinToString(", ") { Display.fmt(a[it]) } + ")"

    /** 把弧度转成当前设定的角度单位 */
    fun toAngleUnit(rad: Double, unit: AngleUnit): Double = when (unit) {
        AngleUnit.DEGREE -> Math.toDegrees(rad)
        AngleUnit.RADIAN -> rad
        AngleUnit.GRADIAN -> rad / Math.PI * 200.0
    }
}
