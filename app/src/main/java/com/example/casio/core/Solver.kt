package com.example.casio.core

import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.max

/**
 * 方程求解总入口。对应 Python 版的 solve_equation / _solve_polynomial /
 * _solve_high_degree / solve_linear_system。
 */
object Solver {

    /** 一元方程的求解结果 */
    data class Result(
        val variable: String,
        val exactTexts: List<String> = emptyList(),      // 精确解文本(二次方程)
        val rationalRoots: List<Rational> = emptyList(), // 有理根
        val multiplicity: Map<Rational, Int> = emptyMap(),
        val realRoots: List<Double> = emptyList(),       // 实根(数值)
        val complexPairs: List<Pair<Double, Double>> = emptyList(),
        val note: String = ""
    ) {
        val isExact: Boolean get() = exactTexts.isNotEmpty()
    }

    /**
     * 解一元方程,如 "2x+3=7"、"x^2-2=0"、"sin(x)=0.5"。
     * mode: auto(默认) / poly(只按多项式解) / num(强制数值解)
     */
    fun solve(equation: String, mode: String = "auto", varHint: String? = null,
              wide: Boolean = false): Result {
        val eqIdx = equation.indexOf('=')
        val leftText: String
        val rightText: String
        if (eqIdx >= 0) {
            leftText = equation.substring(0, eqIdx)
            rightText = equation.substring(eqIdx + 1)
            if (rightText.contains('=')) throw ParseError("一个方程里只能有一个 '='")
        } else {
            leftText = equation
            rightText = "0"
        }
        val left = Parser.parse(leftText)
        val right = Parser.parse(rightText)
        val diff = BinaryNode("-", left, right)

        val names = Parser.collectVariables(diff)
        if (names.size > 1) {
            throw SolveError("方程含多个变量 ${names.joinToString(",")},请用方程组模式(用逗号分隔多个方程)")
        }
        val variable = varHint ?: names.firstOrNull() ?: "x"

        // 拦截残缺/无意义输入:若左右两边对变量不敏感(化简后不含变量),
        // 说明这不是一个真正关于该变量的方程,别丢给牛顿法乱找根。
        if (names.isEmpty()) {
            throw SolveError("式子里没有变量 $variable,无法作为方程求解")
        }

        if (mode != "num") {
            val poly = try {
                PolyBuilder.fromNode(diff)
            } catch (e: SolveError) {
                if (mode == "poly") throw e
                null
            } catch (e: ArithmeticException) {
                throw ParseError(e.message ?: "计算错误")
            }
            if (poly != null && !poly.isZero) {
                val vars = poly.variables()
                if (vars.size > 1) throw SolveError("方程含多个变量,请用方程组模式")
                return solvePolynomial(poly, variable)
            }
        }
        if (mode == "poly") throw SolveError("该方程不是多项式,无法按多项式求解")

        // 敏感性检查:对变量不敏感的式子(化简后与变量无关)不是真方程,
        // 否则牛顿法会在常数式上乱找根,给出成百上千个假根。
        val fProbe: (Double) -> Double = { x ->
            runCatching { diff.eval(mapOf(variable to x)) }.getOrDefault(Double.NaN)
        }
        val y0 = fProbe(1.0)
        val y1 = fProbe(2.0)
        val sensitive = if (y0.isNaN() || y1.isNaN()) true
        else abs(y1 - y0) > 1e-12 * max(1.0, abs(y1))
        if (!sensitive) {
            throw SolveError(
                "这个式子里没有真正含 $variable 的部分(化简后与 $variable 无关),请检查是否少写了运算符"
            )
        }

        // 超越方程:数值求解
        val roots = NumericRoots.numericalRoots(fProbe, wide = wide)
        return Result(
            variable = variable,
            realRoots = roots,
            note = if (wide) "数值求解(牛顿法 + 区间扫描,加宽窗口)" else "数值求解(牛顿法 + 区间扫描)"
        )
    }

    // ---------------- 多项式求解 ----------------

    fun solvePolynomial(poly: Poly, variable: String): Result {
        val cs = poly.coefficients(variable)          // [c0, c1, ...]
        val degree = cs.size - 1
        if (degree <= 0) throw SolveError("方程里没有变量 $variable")

        if (degree == 1) {
            val a = cs[1]
            val b = cs[0]
            val root = (-b) / a
            return Result(
                variable = variable,
                rationalRoots = listOf(root),
                multiplicity = mapOf(root to 1),
                note = "一元一次方程"
            )
        }
        if (degree == 2) return solveQuadratic(cs[2], cs[1], cs[0], variable)
        return solveHighDegree(cs, variable)
    }

    private fun solveQuadratic(a: Rational, b: Rational, c: Rational, variable: String): Result {
        val disc = b * b - Rational.of(4) * a * c
        val exact = ExactRoots.format(variable, a, b, c)

        val realRoots = mutableListOf<Double>()
        val comps = mutableListOf<Pair<Double, Double>>()
        val rational = mutableListOf<Rational>()
        val mult = mutableMapOf<Rational, Int>()

        when {
            disc.signum > 0 -> {
                val sq: Double = if (disc.isPerfectSquare) disc.exactSqrt().toDouble()
                else sqrtDouble(disc.toDouble())
                val base = (-b) / (Rational.of(2) * a)
                val off = sq / (2 * a.toDouble())
                realRoots.add(base.toDouble() - off)
                realRoots.add(base.toDouble() + off)
                realRoots.sort()
            }
            disc.isZero -> {
                val r = (-b) / (Rational.of(2) * a)
                realRoots.add(r.toDouble())
                rational.add(r)
                mult[r] = 2
            }
            else -> {
                val base = (-b) / (Rational.of(2) * a)
                val im = sqrtDouble(-disc.toDouble()) / abs(2 * a.toDouble())
                comps.add(base.toDouble() to im)
            }
        }
        return Result(
            variable = variable,
            exactTexts = exact,
            rationalRoots = rational,
            multiplicity = mult,
            realRoots = realRoots,
            complexPairs = comps,
            note = "一元二次方程"
        )
    }

    private fun solveHighDegree(csIn: List<Rational>, variable: String): Result {
        var cs = csIn.toMutableList()
        while (cs.size > 1 && cs.last().isZero) cs.removeAt(cs.size - 1)
        val degree = cs.size - 1
        if (degree <= 0) throw SolveError("多项式退化为常数,无解")
        if (degree == 1) return solvePolynomial(polyOf(cs), variable)
        if (degree == 2) return solveQuadratic(cs[2], cs[1], cs[0], variable)

        // 1) 有理根定理 + 综合除法降次
        val candidates = NumericRoots.rationalRoots(cs)
        var remaining = cs.toMutableList()
        val found = mutableListOf<Rational>()
        val mult = mutableMapOf<Rational, Int>()
        while (remaining.size > 1) {
            val hit = candidates.firstOrNull { evalExact(remaining, it).isZero } ?: break
            remaining = deflate(remaining, hit).toMutableList()
            found.add(hit)
            mult[hit] = (mult[hit] ?: 0) + 1
        }

        // 2) 剩余部分求数值根
        val realRoots = mutableListOf<Double>()
        val comps = mutableListOf<Pair<Double, Double>>()
        val rational = found.distinct().sorted().toMutableList()

        val rem = remaining
        when {
            rem.size - 1 == 1 -> realRoots.add((-rem[0] / rem[1]).toDouble())
            rem.size - 1 == 2 -> {
                val q = solveQuadratic(rem[2], rem[1], rem[0], variable)
                realRoots.addAll(q.realRoots)
                comps.addAll(q.complexPairs)
                q.rationalRoots.forEach { if (it !in rational) rational.add(it) }
                rational.sort()
            }
            rem.size > 1 -> {
                val hi = rem.reversed().map { Cplx.of(it.toDouble()) }
                val raw = NumericRoots.durandKerner(rem.map { it.toDouble() })
                val cls = NumericRoots.classify(raw, hi)
                realRoots.addAll(cls.real)
                comps.addAll(cls.complexPairs)
            }
        }

        // 3) 数值根里若其实是漂亮的有理数,提升为精确显示
        val exactExtra = mutableListOf<Rational>()
        val keptReal = mutableListOf<Double>()
        for (r in realRoots) {
            val f = NumericRoots.toNiceRational(r)
            if (f != null) exactExtra.add(f) else keptReal.add(r)
        }
        exactExtra.forEach { if (it !in rational) rational.add(it) }
        rational.sort()

        return Result(
            variable = variable,
            rationalRoots = rational,
            multiplicity = mult,
            realRoots = keptReal.distinct().sorted(),
            complexPairs = comps,
            note = "$degree 次多项式"
        )
    }

    private fun polyOf(lowFirst: List<Rational>): Poly {
        var p = Poly.ZERO
        var x = Poly.const(Rational.ONE)
        val v = Poly.variable("x")
        for (i in lowFirst.indices) {
            p += x.scale(lowFirst[i])
            x *= v
        }
        return p
    }

    private fun evalExact(lowFirst: List<Rational>, x: Rational): Rational {
        var acc = Rational.ZERO
        for (i in lowFirst.indices.reversed()) acc = acc * x + lowFirst[i]
        return acc
    }

    /** 综合除法除掉一个已知根,返回降次后的系数(低位在前) */
    private fun deflate(lowFirst: List<Rational>, root: Rational): List<Rational> {
        val hi = lowFirst.reversed()
        val out = mutableListOf(hi[0])
        for (i in 1 until hi.size) out.add(hi[i] + out[i - 1] * root)
        out.removeAt(out.size - 1)
        return out.reversed()
    }

    private fun sqrtDouble(v: Double): Double =
        if (v < 0) Double.NaN else Math.sqrt(v)

    // ---------------- 线性方程组 ----------------

    data class SystemResult(val variables: List<String>, val solution: List<Rational>)

    /**
     * 解线性方程组,如 ["2x+y=5", "x-y=1"]。
     * 高斯消元 + 精确分数运算。
     */
    fun solveLinearSystem(equations: List<String>, variablesIn: List<String>? = null): SystemResult {
        val polys = mutableListOf<Poly>()
        for (eq in equations) {
            val idx = eq.indexOf('=')
            val l = if (idx >= 0) eq.substring(0, idx) else eq
            val r = if (idx >= 0) eq.substring(idx + 1) else "0"
            val diff = BinaryNode("-", Parser.parse(l), Parser.parse(r))
            try {
                polys.add(PolyBuilder.fromNode(diff))
            } catch (e: SolveError) {
                throw SolveError("方程 '$eq' 不是多项式: ${e.message}")
            }
        }
        val used = mutableSetOf<String>()
        polys.forEach { used.addAll(it.variables()) }
        val vars = variablesIn ?: used.sorted()
        val n = vars.size
        val m = polys.size

        // 线性性检查
        for (p in polys) {
            for (v in vars) {
                if (p.degree(v) > 1) throw SolveError("方程含 $v 的 ${p.degree(v)} 次项,不是线性方程组")
            }
            p.crossTerm()?.let { throw SolveError("方程含交叉项 $it,不是线性方程组") }
        }

        // 组装增广矩阵:按变量分组取一次项系数(分组后必须是常数)
        val aug = Array(m) { arrayOfNulls<Rational>(n + 1) }
        for (i in polys.indices) {
            val p = polys[i]
            for (j in vars.indices) {
                val groups = p.termsOf(vars[j])          // [常数部分, 一次项部分, ...]
                val lin = if (groups.size > 1) groups[1] else Poly.ZERO
                if (!lin.isConstant) {
                    throw SolveError("方程里 ${vars[j]} 的系数还含其它变量,不是线性方程组")
                }
                aug[i][j] = if (lin.isZero) Rational.ZERO else lin.constValue()
            }
            aug[i][n] = p.constantPart().unaryMinus()
        }

        // 高斯消元(部分主元)
        var row = 0
        val pivots = mutableListOf<Int>()
        for (col in 0 until n) {
            var pivot = -1
            for (r in row until m) {
                val v = aug[r][col]
                if (v != null && !v.isZero) { pivot = r; break }
            }
            if (pivot < 0) continue
            val tmp = aug[row]; aug[row] = aug[pivot]; aug[pivot] = tmp
            val pv = aug[row][col]!!
            for (c in 0..n) aug[row][c] = aug[row][c]!! / pv
            for (r in 0 until m) {
                if (r != row) {
                    val factor = aug[r][col]!!
                    if (!factor.isZero) {
                        for (c in 0..n) aug[r][c] = aug[r][c]!! - factor * aug[row][c]!!
                    }
                }
            }
            pivots.add(col)
            row++
            if (row == m) break
        }

        // 矛盾行检查
        for (r in row until m) {
            val allZero = (0 until n).all { aug[r][it]!!.isZero }
            if (allZero && !aug[r][n]!!.isZero) throw SolveError("方程组无解(存在矛盾方程)")
        }
        if (pivots.size < n) {
            val free = vars.filterIndexed { i, _ -> i !in pivots }
            throw SolveError("方程组有无穷多解(自由未知量: ${free.joinToString(",")})")
        }

        val solution = MutableList(n) { Rational.ZERO }
        pivots.forEachIndexed { i, col -> solution[col] = aug[i][n]!! }
        return SystemResult(vars, solution)
    }

    // ---------------- SOLVE:牛顿法从初值求根 ----------------

    /**
     * 对应真机的 SOLVE:给一个初值,用**牛顿法**求方程的一个根。
     *
     * 与 [solve] 的分工:[solve] 是"扫描 + 精确/数值"求**全部**根;
     * 这里模拟真机行为 —— **从初值出发收敛到附近的那个根**。
     * 牛顿法不收敛时退回扫描,至少给一个可用答案,并如实说明是退回来的。
     */
    fun solveByNewton(equation: String, varHint: String? = null, guess: Double? = null): String {
        val eqIdx = equation.indexOf('=')
        if (eqIdx < 0) throw SolveError("方程里必须有等号,例如 x^2-2=0")
        val lhs = Parser.parse(equation.substring(0, eqIdx))
        val rhs = Parser.parse(equation.substring(eqIdx + 1))
        val diff = BinaryNode("-", lhs, rhs)
        val names = Parser.collectVariables(diff)
        val variable = varHint ?: names.firstOrNull() ?: "x"

        val f: (Double) -> Double = { x -> diff.eval(mapOf(variable to x)) }

        val x0 = guess ?: 1.0
        var x = x0
        var converged = false
        var iters = 0
        val h = 1e-7
        for (i in 1..200) {
            iters = i
            val fx = f(x)
            if (!fx.isFinite()) break
            if (abs(fx) < 1e-14) {
                converged = true
                break
            }
            val d = (f(x + h) - f(x - h)) / (2 * h)     // 中心差分求导
            if (!d.isFinite() || abs(d) < 1e-14) break
            val next = x - fx / d
            if (!next.isFinite()) break
            if (abs(next - x) < 1e-13 * maxOf(1.0, abs(next))) {
                x = next
                converged = true
                break
            }
            x = next
        }

        val head = "SOLVE  $equation"
        val sub = "自变量 $variable,初值 ${Display.fmt(x0)}"
        if (converged) {
            val scale = Math.pow(10.0, 9.0)
            val r = Math.round(x * scale) / scale
            return Display.box(
                "SOLVE 求根",
                listOf(head, sub, "", "$variable = ${Display.fmt(r)}", "", "牛顿法迭代 $iters 次收敛")
            )
        }

        // 退回扫描
        val alt = try {
            val r = solve(equation, varHint = variable)
            r.realRoots.firstOrNull() ?: r.rationalRoots.firstOrNull()?.toDouble()
        } catch (e: Exception) {
            null
        }
        return if (alt != null) {
            Display.box(
                "SOLVE 求根",
                listOf(head, sub, "", "牛顿法未收敛,改用扫描求得:", "$variable = ${Display.fmt(alt)}")
            )
        } else {
            Display.box(
                "SOLVE 求根",
                listOf(head, sub, "", "未收敛:请换一个更接近根的自变量初值")
            )
        }
    }
}
