package com.example.casio.core

import kotlin.math.abs

/**
 * 矩阵计算 —— 对应 fx-991CN X 的矩阵模式。
 *
 * 手册能力:
 *  · 最大 4 行 × 4 列
 *  · 矩阵变量 MatA / MatB / MatC / MatD,以及结果存储器 MatAns
 *  · 运算:加、减、乘、纯量乘、行列式 Det、单位矩阵 Identity、
 *          转置 Trn、逆、每个元素取绝对值 Abs、幂(MatA²、MatA³)
 *  · 结果给 MatAns,可再参与运算
 *
 * 数值用 Double;行列式与逆用带部分主元的高斯消元,并做奇异判定。
 */
data class Matrix(val rows: Int, val cols: Int, val data: DoubleArray) {

    init {
        require(rows in 1..4 && cols in 1..4) { "矩阵维度必须在 1~4 之间:$rows×$cols" }
        require(data.size == rows * cols) { "元素个数与维度不符" }
    }

    operator fun get(r: Int, c: Int): Double = data[r * cols + c]

    fun copy(): Matrix = Matrix(rows, cols, data.copyOf())

    val isSquare: Boolean get() = rows == cols

    companion object {
        /** 最大维度(手册:最多 4×4) */
        const val MAX_DIM = 4

        fun zeros(rows: Int, cols: Int) = Matrix(rows, cols, DoubleArray(rows * cols))

        fun of(vararg values: Double): Matrix {
            require(values.size == 4) { "of(...) 只用于 2×2" }
            return Matrix(2, 2, doubleArrayOf(values[0], values[1], values[2], values[3]))
        }

        /** 单位矩阵 */
        fun identity(n: Int): Matrix {
            require(n in 1..MAX_DIM) { "单位矩阵维度必须在 1~$MAX_DIM:$n" }
            val m = zeros(n, n)
            for (i in 0 until n) m.data[i * n + i] = 1.0
            return m
        }

        /** 从二维列表构造 */
        fun fromRows(rowsIn: List<List<Double>>): Matrix {
            val r = rowsIn.size
            val c = rowsIn.firstOrNull()?.size ?: 0
            require(r in 1..MAX_DIM && c in 1..MAX_DIM) { "矩阵维度必须在 1~$MAX_DIM 之间:$r×$c" }
            require(rowsIn.all { it.size == c }) { "各行列数不一致" }
            val d = DoubleArray(r * c)
            rowsIn.forEachIndexed { i, row -> row.forEachIndexed { j, v -> d[i * c + j] = v } }
            return Matrix(r, c, d)
        }
    }
}

/** 矩阵运算。每个运算都做维度校验,失败抛 IllegalArgumentException(带中文原因)。 */
object Mat {

    private const val EPS = 1e-12

    fun add(a: Matrix, b: Matrix): Matrix {
        require(a.rows == b.rows && a.cols == b.cols) {
            "维度不一致,无法相加:${a.rows}×${a.cols} 与 ${b.rows}×${b.cols}"
        }
        return Matrix(a.rows, a.cols, DoubleArray(a.data.size) { a.data[it] + b.data[it] })
    }

    fun sub(a: Matrix, b: Matrix): Matrix {
        require(a.rows == b.rows && a.cols == b.cols) {
            "维度不一致,无法相减:${a.rows}×${a.cols} 与 ${b.rows}×${b.cols}"
        }
        return Matrix(a.rows, a.cols, DoubleArray(a.data.size) { a.data[it] - b.data[it] })
    }

    fun mul(a: Matrix, b: Matrix): Matrix {
        require(a.cols == b.rows) {
            "维度不满足乘法条件:${a.rows}×${a.cols} 乘 ${b.rows}×${b.cols}(左侧列数须等于右侧行数)"
        }
        val out = Matrix.zeros(a.rows, b.cols)
        for (i in 0 until a.rows) {
            for (j in 0 until b.cols) {
                var s = 0.0
                for (k in 0 until a.cols) s += a[i, k] * b[k, j]
                out.data[i * b.cols + j] = s
            }
        }
        return out
    }

    fun scale(a: Matrix, k: Double): Matrix =
        Matrix(a.rows, a.cols, DoubleArray(a.data.size) { a.data[it] * k })

    /** 每个元素取绝对值(Abs(MatB)) */
    fun absAll(a: Matrix): Matrix =
        Matrix(a.rows, a.cols, DoubleArray(a.data.size) { abs(a.data[it]) })

    fun transpose(a: Matrix): Matrix {
        val out = Matrix.zeros(a.cols, a.rows)
        for (i in 0 until a.rows) for (j in 0 until a.cols) out.data[j * a.rows + i] = a[i, j]
        return out
    }

    /** 行列式:带部分主元的高斯消元 */
    fun det(a: Matrix): Double {
        require(a.isSquare) { "只有方阵才能求行列式(当前 ${a.rows}×${a.cols})" }
        val n = a.rows
        val m = a.data.copyOf()
        var sign = 1.0
        var det = 1.0
        for (col in 0 until n) {
            var piv = col
            for (r in col + 1 until n) if (abs(m[r * n + col]) > abs(m[piv * n + col])) piv = r
            if (abs(m[piv * n + col]) < EPS) return 0.0
            if (piv != col) {
                for (c in 0 until n) {
                    val t = m[col * n + c]; m[col * n + c] = m[piv * n + c]; m[piv * n + c] = t
                }
                sign = -sign
            }
            val p = m[col * n + col]
            det *= p
            for (r in col + 1 until n) {
                val f = m[r * n + col] / p
                for (c in col until n) m[r * n + c] -= f * m[col * n + c]
            }
        }
        return sign * det
    }

    /** 逆矩阵:高斯-约当消元;奇异时报错 */
    fun inverse(a: Matrix): Matrix {
        require(a.isSquare) { "只有方阵才能求逆(当前 ${a.rows}×${a.cols})" }
        val n = a.rows
        // 增广矩阵 [A | I]
        val m = Array(n) { i -> DoubleArray(2 * n) { j ->
            when {
                j < n -> a[i, j]
                j - n == i -> 1.0
                else -> 0.0
            }
        } }
        for (col in 0 until n) {
            var piv = col
            for (r in col + 1 until n) if (abs(m[r][col]) > abs(m[piv][col])) piv = r
            if (abs(m[piv][col]) < EPS) throw IllegalArgumentException("矩阵不可逆(行列式为 0)")
            val t = m[col]; m[col] = m[piv]; m[piv] = t
            val p = m[col][col]
            for (c in 0 until 2 * n) m[col][c] /= p
            for (r in 0 until n) {
                if (r == col) continue
                val f = m[r][col]
                if (f == 0.0) continue
                for (c in 0 until 2 * n) m[r][c] -= f * m[col][c]
            }
        }
        val out = Matrix.zeros(n, n)
        for (i in 0 until n) for (j in 0 until n) out.data[i * n + j] = m[i][n + j]
        return out
    }

    /** 整数次幂(仅方阵;指数须为非负整数,与手册一致) */
    fun pow(a: Matrix, n: Int): Matrix {
        require(a.isSquare) { "只有方阵才能求幂" }
        require(n >= 0) { "矩阵幂的指数必须是非负整数:$n" }
        var result = Matrix.identity(a.rows)
        if (n == 0) return result
        // 快速幂
        var base = a.copy()
        var e = n
        while (e > 0) {
            if (e and 1 == 1) result = mul(result, base)
            e = e shr 1
            if (e > 0) base = mul(base, base)
        }
        return result
    }

    /** 判定是否为 1~4 维的合法矩阵文本形式,便于显示 */
    fun format(a: Matrix): String {
        val rows = (0 until a.rows).map { i ->
            (0 until a.cols).joinToString("  ") { j -> Display.fmt(a[i, j]) }
        }
        return rows.joinToString("\n")
    }
}
