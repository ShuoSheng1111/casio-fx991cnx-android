package com.example.casio.core

import kotlin.math.abs

/**
 * 比例计算 —— 对应 fx-991CN X 的比例模式。
 *
 * 手册能力:
 *  · 求 a : b = X : d 中的 X;以及 a : b = c : X 中的 X
 *  · 形式 a : b = X : d 与 a : b = c : X
 *  · 比例 a : b = c : d 中**已知任意三项求第四项**
 */
object Ratio {

    /** 求解结果 */
    sealed interface Result {
        data class Ok(val value: Double, val text: String) : Result
        data class Failed(val reason: String) : Result
    }

    /**
     * 已知 a、b、c,求 a : b = c : X 中的 X。
     * 由 a/b = c/X 得 X = b·c/a。
     */
    fun solveFourth(a: Double, b: Double, c: Double): Result {
        if (abs(a) < 1e-300) return Result.Failed("a 不能为 0(否则比例无意义)")
        val x = b * c / a
        if (!x.isFinite()) return Result.Failed("结果超出可表示范围")
        return Result.Ok(x, "$a : $b = $c : ${Display.fmt(x)}")
    }

    /**
     * 已知 a、b、d,求 a : b = X : d 中的 X。
     * 由 a/b = X/d 得 X = a·d/b。
     */
    fun solveThird(a: Double, b: Double, d: Double): Result {
        if (abs(b) < 1e-300) return Result.Failed("b 不能为 0(否则比例无意义)")
        val x = a * d / b
        if (!x.isFinite()) return Result.Failed("结果超出可表示范围")
        return Result.Ok(x, "$a : $b = ${Display.fmt(x)} : $d")
    }

    /** 求比例式的文本输出 */
    fun format(r: Result): String = when (r) {
        is Result.Ok -> Display.box("比例", listOf(r.text, "", "X = ${Display.fmt(r.value)}"))
        is Result.Failed -> Display.box("比例", listOf("无法求解", r.reason))
    }
}

/**
 * 不等式求解 —— 对应 fx-991CN X 的不等式模式。
 *
 * 手册能力:
 *  · 二次 / 三次 / 四次不等式
 *  · 四种关系:< 0、> 0、≤ 0、≥ 0
 *  · 解以区间形式给出(手册里会给出 a≤x≤b 这类形式)
 */
object Ineq {

    /** 不等式关系 */
    enum class Rel(val label: String) {
        LT("< 0"),
        GT("> 0"),
        LE("≤ 0"),
        GE("≥ 0")
    }

    /** 解的结果 */
    sealed interface Result {
        /** 解集为若干区间;each pair 是 (lower, upper),含端点由 loose 决定 */
        data class Intervals(
            val parts: List<Triple<Double, Double, Boolean>>,  // (下界, 上界, 是否开区间)
            val text: String
        ) : Result

        data class Failed(val reason: String) : Result
    }

    /**
     * 解多项式不等式。coeffs 按**降幂**给出,例如 x²−3x+2 写作 [1, -3, 2]。
     * 次数由系数个数决定(2~5 个系数 → 1~4 次)。
     */
    fun solve(coeffs: List<Double>, rel: Rel): Result {
        val deg = coeffs.size - 1
        if (deg < 1) return Result.Failed("至少要有一个非常数项")
        if (deg > 4) return Result.Failed("手册支持最高四次不等式(当前 $deg 次)")

        // 首项为 0 时降次
        val trimmed = coeffs.dropWhile { abs(it) < 1e-300 }
        if (trimmed.size < 2) {
            // 常数:要么恒成立,要么无解
            val c = trimmed.firstOrNull() ?: 0.0
            val holds = when (rel) {
                Rel.LT -> c < 0
                Rel.LE -> c <= 0
                Rel.GT -> c > 0
                Rel.GE -> c >= 0
            }
            return if (holds) {
                Result.Intervals(emptyList(), "全体实数")
            } else {
                Result.Intervals(emptyList(), "无解")
            }
        }

        // 求实根
        val roots = realRoots(trimmed)
        if (roots.isEmpty()) {
            // 无实根:符号恒定,取一个测试点判断
            val test = 0.0
            val v = evalPoly(trimmed, test)
            val holds = holdsRel(v, rel)
            return if (holds) {
                Result.Intervals(emptyList(), "全体实数")
            } else {
                Result.Intervals(emptyList(), "无解")
            }
        }

        // 用根把实轴分段,逐段取中点判断符号
        val sorted = roots.sorted()
        val cuts = mutableListOf<Double>()
        cuts.add(Double.NEGATIVE_INFINITY)
        sorted.forEach { if (abs(it) < 1e-12) cuts.add(0.0) else cuts.add(it) }
        cuts.add(Double.POSITIVE_INFINITY)

        val parts = mutableListOf<Triple<Double, Double, Boolean>>()
        for (i in 0 until cuts.size - 1) {
            val lo = cuts[i]
            val hi = cuts[i + 1]
            val mid = when {
                lo == Double.NEGATIVE_INFINITY && hi == Double.POSITIVE_INFINITY -> 0.0
                lo == Double.NEGATIVE_INFINITY -> hi - 1.0
                hi == Double.POSITIVE_INFINITY -> lo + 1.0
                else -> (lo + hi) / 2.0
            }
            // 跳过根上的采样点(中点不会落在根上,除非区间退化)
            if (abs(mid - lo) < 1e-15 || abs(mid - hi) < 1e-15) continue
            if (holdsRel(evalPoly(trimmed, mid), rel)) {
                parts.add(Triple(lo, hi, false))
            }
        }

        // 端点:若关系含等号,根本身也是解
        val inclusive = rel == Rel.LE || rel == Rel.GE
        val text = if (parts.isEmpty()) {
            // 可能只有孤立的根满足(如 x² ≤ 0 → x = 0)
            if (inclusive) {
                val zeroRoots = sorted.filter { abs(evalPoly(trimmed, it)) < 1e-9 }
                if (zeroRoots.isNotEmpty()) {
                    zeroRoots.joinToString(" 或 ") { "x = ${Display.fmt(it)}" }
                } else "无解"
            } else "无解"
        } else {
            // 含等号时把相邻区间的端点合并描述
            if (inclusive) describeClosed(parts, sorted) else describeOpen(parts)
        }

        return Result.Intervals(parts, text)
    }

    private fun holdsRel(v: Double, rel: Rel): Boolean = when (rel) {
        Rel.LT -> v < 0
        Rel.LE -> v <= 0
        Rel.GT -> v > 0
        Rel.GE -> v >= 0
    }

    private fun evalPoly(coeffs: List<Double>, x: Double): Double {
        var s = 0.0
        for (c in coeffs) s = s * x + c
        return s
    }

    /**
     * 求实根。用引擎的扫描+二分法(numericalRoots),它同时覆盖了
     * 有理根与无理根,并且不会把复根混进来。
     * 传入系数按**降幂**,内部转成求值函数。
     */
    private fun realRoots(coeffsHi: List<Double>): List<Double> {
        return try {
            val f: (Double) -> Double = { x ->
                var s = 0.0
                for (c in coeffsHi) s = s * x + c
                s
            }
            NumericRoots.numericalRoots(f, span = 100.0, samples = 4000, wide = true)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun describeOpen(parts: List<Triple<Double, Double, Boolean>>): String =
        parts.joinToString(" 或 ") { (lo, hi, _) ->
            when {
                lo == Double.NEGATIVE_INFINITY -> "x < ${Display.fmt(hi)}"
                hi == Double.POSITIVE_INFINITY -> "x > ${Display.fmt(lo)}"
                else -> "${Display.fmt(lo)} < x < ${Display.fmt(hi)}"
            }
        }

    private fun describeClosed(
        parts: List<Triple<Double, Double, Boolean>>,
        roots: List<Double>
    ): String {
        // 把区间按含端点的形式拼接;相邻区间只隔一个根时合并成 a ≤ x ≤ b
        val segs = parts.map { (lo, hi, _) -> lo to hi }
        val merged = mutableListOf<Pair<Double, Double>>()
        for (s in segs) {
            val last = merged.lastOrNull()
            if (last != null && abs(last.second - s.first) < 1e-9) {
                merged[merged.size - 1] = last.first to s.second
            } else {
                merged.add(s)
            }
        }
        return merged.joinToString(" 或 ") { (lo, hi) ->
            when {
                lo == Double.NEGATIVE_INFINITY -> "x ≤ ${Display.fmt(hi)}"
                hi == Double.POSITIVE_INFINITY -> "x ≥ ${Display.fmt(lo)}"
                else -> "${Display.fmt(lo)} ≤ x ≤ ${Display.fmt(hi)}"
            }
        }
    }

    fun format(coeffs: List<Double>, rel: Rel, r: Result): String {
        val poly = polyText(coeffs)
        return when (r) {
            is Result.Intervals -> Display.box(
                "解不等式",
                listOf("$poly ${rel.label}", "", r.text)
            )
            is Result.Failed -> Display.box("解不等式", listOf("$poly ${rel.label}", "", "无法求解", r.reason))
        }
    }

    private fun polyText(coeffs: List<Double>): String {
        val n = coeffs.size
        val sb = StringBuilder()
        coeffs.forEachIndexed { i, c ->
            if (abs(c) < 1e-300) return@forEachIndexed
            val power = n - 1 - i
            val sign = if (c < 0) "−" else if (sb.isEmpty()) "" else "+"
            val mag = abs(c)
            val magText = if (abs(mag - 1.0) < 1e-12 && power > 0) "" else Display.fmt(mag)
            val xPart = when (power) {
                0 -> ""
                1 -> "x"
                else -> "x^$power"
            }
            sb.append(sign).append(magText).append(xPart)
        }
        return if (sb.isEmpty()) "0" else sb.toString()
    }
}
