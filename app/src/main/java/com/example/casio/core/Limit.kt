package com.example.casio.core

import kotlin.math.abs

/**
 * 求极限(数值法)。fx-991CN X 本身没有这个功能(说明书目录与函数计算章节均无 lim),
 * 这里按需求补上。
 *
 * 思路:
 *  · 取一列逐步减小的步长 h,计算 f(a±h),得到值序列
 *  · 用 **Richardson 外推** 抵消 h 的一阶/二阶误差项,提高精度
 *  · 判定:值序列收敛 → 给出极限;震荡或发散 → 明确报错,不乱给数字
 *
 * 支持的趋近方式:
 *  · 有限点双侧 / 左侧 / 右侧
 *  · x → ±∞
 */
object Limit {

    /** 趋近方向 */
    enum class Side(val label: String) {
        BOTH("双侧"),
        LEFT("左侧(x→a⁻)"),
        RIGHT("右侧(x→a⁺)")
    }

    /** 求极限的结果 */
    sealed interface Result {
        /** 求得极限值 */
        data class Ok(val value: Double, val side: Side, val detail: String) : Result

        /** 未收敛(震荡或发散) */
        data class Diverged(val reason: String, val side: Side, val samples: List<Double>) : Result
    }

    private const val MAX_STEP = 1e-3
    private const val MIN_STEP = 1e-9
    private const val STEPS = 7

    /**
     * 求 f 在 x → toward 处的极限。
     *
     * @param f 以变量值求函数值;返回 null 表示该点算不出(如 log 负值、除零)
     * @param toward 趋近的有限值
     * @param infinite 是否趋于无穷(此时 toward 只用于取符号)
     */
    fun compute(
        f: (Double) -> Double?,
        toward: Double,
        side: Side,
        infinite: Boolean = false
    ): Result {
        if (infinite) return infiniteLimit(f, toward >= 0, side)

        // 有限点:按方向取一列取样点
        val signs = when (side) {
            Side.BOTH -> listOf(1.0, -1.0)
            Side.LEFT -> listOf(-1.0)
            Side.RIGHT -> listOf(1.0)
        }

        // 双侧时两侧结果都要收敛且接近,才算极限存在
        val perSide = signs.map { s -> oneSided(f, toward, s) }

        if (side == Side.BOTH) {
            // 两侧分别看是否收敛
            val (v1, seq1) = perSide[0]
            val (v2, seq2) = perSide[1]
            if (v1 == null && v2 == null) {
                return Result.Diverged("左右两侧都无法逼近(函数在 a 附近算不出值)", side, seq1 + seq2)
            }
            if (v1 == null) return Result.Diverged("左侧不收敛", side, seq2)
            if (v2 == null) return Result.Diverged("右侧不收敛", side, seq1)
            // 两侧极限必须一致
            val diff = abs(v1 - v2)
            val scale = maxOf(1.0, abs(v1), abs(v2))
            if (diff > 1e-6 * scale) {
                return Result.Diverged(
                    "左右极限不相等(左 ${Display.fmt(v1)},右 ${Display.fmt(v2)}),极限不存在",
                    side, seq1 + seq2
                )
            }
            return Result.Ok((v1 + v2) / 2.0, side, "左右极限一致")
        }

        val (v, seq) = perSide[0]
        return if (v == null) {
            Result.Diverged("该侧不收敛(可能震荡或发散)", side, seq)
        } else {
            Result.Ok(v, side, "单侧收敛")
        }
    }

    /**
     * 单侧取样 + Richardson 外推。
     * 返回 (极限值 或 null, 取样序列)。
     */
    private fun oneSided(
        f: (Double) -> Double?,
        a: Double,
        sign: Double
    ): Pair<Double?, List<Double>> {
        val seq = mutableListOf<Double>()
        var h = MAX_STEP
        repeat(STEPS) {
            val x = a + sign * h
            val y = f(x)
            if (y != null && y.isFinite()) seq.add(y)
            h /= 10.0
        }
        if (seq.size < 3) return null to seq

        // Richardson 外推:对相邻两项做 (10·f(h/10) − f(h)) / 9,消掉一阶误差
        var ext: List<Double> = seq
        repeat(2) {
            if (ext.size < 2) return@repeat
            ext = (0 until ext.size - 1).map { i -> (10.0 * ext[i + 1] - ext[i]) / 9.0 }
        }
        if (ext.isEmpty()) return null to seq

        val candidate = ext.last()
        // 收敛判定:最后三个外推值要互相接近
        val tail = ext.takeLast(3)
        val spread = tail.max() - tail.min()
        val scale = maxOf(1.0, abs(candidate))
        if (spread > 1e-5 * scale) return null to seq

        // 发散判定:值在明显变大(如 1/x 在 x→0⁺)
        if (abs(candidate) > 1e12) return null to seq

        return candidate to seq
    }

    /** x → ±∞ 的极限 */
    private fun infiniteLimit(
        f: (Double) -> Double?,
        positive: Boolean,
        side: Side
    ): Result {
        val sign = if (positive) 1.0 else -1.0
        val seq = mutableListOf<Double>()
        for (e in 2..9) {
            val x = sign * Math.pow(10.0, e.toDouble())
            val y = f(x) ?: continue
            if (y.isFinite()) seq.add(y)
        }
        if (seq.size < 4) {
            return Result.Diverged("取样点不足,无法判断", side, seq)
        }
        // 趋于无穷:绝对值持续增大
        val growing = seq.zipWithNext().all { (p, q) -> abs(q) > abs(p) * 1.05 }
        if (growing) {
            val dir = if (seq.last() > 0) "+∞" else "−∞"
            return Result.Diverged("函数趋于 $dir(发散,不存在有限极限)", side, seq)
        }
        val tail = seq.takeLast(3)
        val spread = tail.max() - tail.min()
        val scale = maxOf(1.0, abs(tail.last()))
        if (spread > 1e-4 * scale) {
            return Result.Diverged("值未稳定(可能震荡)", side, seq)
        }
        return Result.Ok(tail.last(), side, "趋于∞时收敛")
    }

    /** 极限结果的文本输出 */
    fun format(expr: String, variable: String, towardText: String, side: Side, r: Result): String {
        val head = "lim  $expr"
        val sub = "$variable → $towardText" + (if (side == Side.BOTH) "" else "  (${side.label})")
        return when (r) {
            is Result.Ok -> Display.box(
                "求极限",
                listOf(head, sub, "", "= ${Display.fmt(r.value)}", "", r.detail)
            )
            is Result.Diverged -> Display.box(
                "求极限",
                listOf(
                    head, sub, "",
                    "极限不存在",
                    r.reason,
                    "",
                    "取样: " + r.samples.takeLast(4).joinToString(", ") { Display.fmt(it) }
                )
            )
        }
    }
}
