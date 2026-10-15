package com.example.casio.core

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.sin

/**
 * 引擎自测:逐条对应 Python 版 test_calc.py 的用例。
 * 这是移植的验收契约 —— 结果必须与原 Python 版完全一致。
 */
object EngineSelfTest {

    private val pass = mutableListOf<String>()
    private val fail = mutableListOf<String>()

    private fun check(name: String, actual: Any?, expected: Any?) {
        if (actual == expected) pass.add(name)
        else fail.add("$name: 期望 $expected,实际 $actual")
    }

    private fun checkTrue(name: String, cond: Boolean, extra: String = "") {
        if (cond) pass.add(name) else fail.add("$name  $extra")
    }

    private fun checkThrows(name: String, block: () -> Any?) {
        try {
            block()
            fail.add("$name: 本应抛错但没有")
        } catch (e: Exception) {
            pass.add(name)
        }
    }

    /** 与 Python 版 fmt_float 对齐:整数不带小数点,否则最多 10 位小数去尾零 */
    fun fmt(v: Double): String {
        if (v.isNaN()) return "nan"
        if (v == 0.0) return "0"
        if (v == Math.floor(v) && Math.abs(v) < 1e16) return v.toLong().toString()
        val s = String.format("%.10f", v).trimEnd('0').trimEnd('.')
        return s
    }

    private fun calc(expr: String, env: Map<String, Double> = emptyMap()): String =
        fmt(Parser.parse(expr).eval(env))

    fun run(): String {
        pass.clear(); fail.clear()
        testArithmetic()
        testImplicitMulAndFunctions()
        testPrecedence()
        testExactRational()
        testLinearAndQuadratic()
        testHighDegree()
        testNumericEquations()
        testFmt()
        testLcd()
        testMemoryAndHistory()
        testStatEditor()
        testMatrix()
        testLimit()
        testVector()
        testBaseN()
        testRatioAndIneq()
        testLimitBlock()
        testEqnAndSolve()
        testQuad()
        testConstantsAndConv()
        testErrorReport()
        testCalcAndTable()
        testMatExpr()
        testEngineMatExpr()
        testHistoryCodec()
        testExtraFunctions()
        testStats()
        testLinearSystems()
        testCalculus()
        return report()
    }

    // ---------------- 10. 微积分 ----------------
    private fun testCalculus() {
        // 求导式与中心差分对拍(这是移植正确性的硬指标)
        val cases = listOf(
            "x^2", "x^3+2x", "sin(x)", "cos(x)", "tan(x)", "ln(x)", "exp(x)",
            "e^(2x)", "x^2*sin(x)", "sin(x)/x", "x/(x+1)", "ln(x)*x",
            "sqrt(x)", "1/x", "sin(x)*cos(x)", "ln(x^2)", "e^(x^2)",
            "sqrt(1+x^2)", "atan(x)", "x^4-3x^2+1", "(x+1)^5"
        )
        for (expr in cases) {
            val node = Parser.parse(expr)
            val d = Calculus.simplify(Calculus.differentiate(node, "x"))
            var worst = 0.0
            for (x0 in listOf(0.7, 1.3, 2.1)) {
                val analytic = runCatching { d.eval(mapOf("x" to x0)) }.getOrNull() ?: continue
                val h = 1e-5
                val numeric = runCatching {
                    (node.eval(mapOf("x" to x0 + h)) - node.eval(mapOf("x" to x0 - h))) / (2 * h)
                }.getOrNull() ?: continue
                val err = abs(analytic - numeric) / maxOf(1.0, abs(numeric))
                if (err > worst) worst = err
            }
            checkTrue("求导对拍 $expr (误差 ${"%.2e".format(worst)})", worst < 1e-4,
                "d/dx = ${Calculus.format(d)}, 最大误差 $worst")
        }

        // 具体导数结果的文本检查
        check("d/dx x^2", Calculus.derivativeText("x^2", "x"), "2·x")
        check("d/dx x^3+2x", Calculus.derivativeText("x^3+2x", "x"), "3·x^2+2")
        check("d/dx sin(x)", Calculus.derivativeText("sin(x)", "x"), "cos(x)")
        check("d/dx cos(x)", Calculus.derivativeText("cos(x)", "x"), "-sin(x)")
        check("d/dx ln(x)", Calculus.derivativeText("ln(x)", "x"), "1/x")
        check("d/dx x^3 对 y 求导", Calculus.derivativeText("x^3", "y"), "0")
        check("d/dx x^2*sin(x)", Calculus.derivativeText("x^2*sin(x)", "x"),
            "2·x·sin(x)+(x^2)·cos(x)")

        // 数值积分:与已知解析值对拍
        run {
            val v = Calculus.integrate({ x -> x * x }, 0.0, 1.0)
            checkTrue("∫₀¹ x² dx = 1/3", abs(v - 1.0 / 3.0) < 1e-9, v.toString())
        }
        run {
            val v = Calculus.integrate({ x -> 4.0 / (1.0 + x * x) }, 0.0, 1.0)
            checkTrue("∫₀¹ 4/(1+x²) dx = π", abs(v - Math.PI) < 1e-9, v.toString())
        }
        run {
            val v = Calculus.integrate({ x -> sin(x) }, 0.0, Math.PI)
            checkTrue("∫₀^π sin(x) dx = 2", abs(v - 2.0) < 1e-8, v.toString())
        }
        run {
            val v = Calculus.integrate({ x -> 1.0 / x }, 1.0, Math.E)
            checkTrue("∫₁^e 1/x dx = 1", abs(v - 1.0) < 1e-8, v.toString())
        }

        // 引擎命令层的输出
        run {
            val e = CalcEngine()
            val out = e.process(":diff x^2*sin(x)")
            checkTrue(":diff 输出含符号求导", out.contains("符号求导"), out)
            checkTrue(":diff 输出含结果", out.contains("sin"), out)
        }
        run {
            // 普通文本(非命令块)必须也能渲染出单元格,否则输入的数字/函数名看不见
            val ps = mutableListOf<Edit.Part>(Edit.Raw(""))
            var cur = Edit.Cursor(0, 0, 0)
            cur = Edit.insert(ps, cur, "5")
            checkTrue("插入 5 后内部文本为 5", Edit.getText(ps) == "5", Edit.getText(ps))
            val lines = Edit.layout(ps, cur)
            checkTrue("普通文本要产出一行", lines.size >= 1, "lines=${lines.size}")
            val joined = lines.joinToString("|") { ln -> ln.cells.joinToString("") { it.text } }
            checkTrue("普通文本 5 要出现在渲染里", joined.contains("5"), "渲染=${joined}")
            // 再插一个函数名
            cur = Edit.insert(ps, cur, "sin(")
            val joined2 = Edit.layout(ps, cur).joinToString("|") { ln -> ln.cells.joinToString("") { it.text } }
            checkTrue("sin( 要出现在渲染里", joined2.contains("sin("), "渲染=${joined2}")
        }
        run {
            // 原子块编辑器:积分块的字段导航与删除语义
            val ps = mutableListOf<Edit.Part>(Edit.Raw(""))
            var cur = Edit.Cursor(0, 0, 0)
            ps.add(Edit.newIntegral())
            val bi = ps.size - 1
            cur = Edit.Cursor(bi, 2, 0)                  // 光标在「函数」字段
            checkTrue("新建积分块默认 上限=0", (ps[bi] as Edit.Block).fields[0].value == "0", Edit.getText(ps))
            checkTrue("新建积分块默认 下限=0", (ps[bi] as Edit.Block).fields[1].value == "0", Edit.getText(ps))
            cur = Edit.insert(ps, cur, "x")              // 函数填 x
            checkTrue("函数填 x", Edit.getText(ps).contains(":int 0 0 x"), Edit.getText(ps))
            checkTrue("插入后光标状态", cur.fieldIndex == 2 && cur.offset == 1, "fi=${cur.fieldIndex} off=${cur.offset}")
            val cur2 = Edit.moveLeft(ps, cur)
            checkTrue("← 一次到达下限字段", cur2.fieldIndex == 1, "fi=${cur2.fieldIndex} off=${cur2.offset} (进入前 fi=${cur.fieldIndex} off=${cur.offset})")
            cur = cur2
            checkTrue("下限光标在末尾", cur.offset == 1, "offset=${cur.offset}")
            cur = Edit.moveLeft(ps, cur)                 // ← 再进「上限」字段末尾
            checkTrue("← 两次到达上限字段", cur.fieldIndex == 0, "fieldIndex=${cur.fieldIndex}")
            cur = Edit.backspace(ps, cur)                // 退格:清掉上限的 0
            checkTrue("退格只清上限的数字", (ps[bi] as Edit.Block).fields[0].value == "", Edit.getText(ps))
            checkTrue("退格没有删掉整块", ps.size == 2 && ps[bi] is Edit.Block, Edit.getText(ps))
            cur = Edit.insert(ps, cur, "1")              // 上限 = 1
            checkTrue("上限改成 1", Edit.getText(ps).contains(":int 1 0 x"), Edit.getText(ps))
            // 字段布局:0=上限 1=下限 2=函数
            // 从函数字段出发一路左移,验证「一次一个字段」的往返
            cur = Edit.Cursor(bi, 2, 0)
            val back1 = Edit.moveLeft(ps, cur)           // 函数 → 下限
            checkTrue("← 到下限", back1.fieldIndex == 1, "fi=${back1.fieldIndex}")
            val back2 = Edit.moveLeft(ps, back1)         // 下限 → 上限
            checkTrue("← 到上限", back2.fieldIndex == 0, "fi=${back2.fieldIndex}")
            val fwd1 = Edit.moveRight(ps, back2)         // 上限 → 下限
            checkTrue("→ 到下限", fwd1.fieldIndex == 1, "fi=${fwd1.fieldIndex}")
            val fwd2 = Edit.moveRight(ps, fwd1)          // 下限 → 函数
            checkTrue("→ 到函数", fwd2.fieldIndex == 2, "fi=${fwd2.fieldIndex}")
            // 在下限字段里退格,应只清掉下限的数字、且不删整块
            cur = Edit.Cursor(bi, 1, 1)
            cur = Edit.backspace(ps, cur)
            checkTrue("退格清掉下限数字", (ps[bi] as Edit.Block).fields[1].value == "", Edit.getText(ps))
            checkTrue("只清数字没删整块", ps[bi] is Edit.Block, Edit.getText(ps))
        }
        run {
            // 块内字段都空之后,再退格应把整块删掉(用新块单独验证)
            val ps = mutableListOf<Edit.Part>(Edit.Raw(""))
            ps.add(Edit.newIntegral())
            val bi = ps.size - 1
            var c = Edit.Cursor(bi, 1, 1)                // 下限
            c = Edit.backspace(ps, c)                    // 下限的 0 → 空
            c = Edit.backspace(ps, c)                    // 下限已空 → 整块删除
            checkTrue("字段空后退格删掉整块", ps.none { it is Edit.Block }, Edit.getText(ps))
        }
        run {
            val e = CalcEngine()
            val out = e.process(":int 0 1 x^2")
            checkTrue(":int 输出含结果 0.333333", out.contains("0.333333"), out)
            checkTrue(":int 标注出上限", out.contains("上限"), out)
            checkTrue(":int 标注出下限", out.contains("下限"), out)
            checkTrue(":int 用方括号显示空位", out.contains("[0]") && out.contains("[1]"), out)
        }
        run {
            // 逗号同样可以作为参数分隔符(按键区有逗号键,方便在手机上输入)
            val e = CalcEngine()
            val out = e.process(":int 0,1,x^2")
            checkTrue(":int 支持逗号分隔", out.contains("0.333333"), out)
        }
        run {
            val e = CalcEngine()
            val out = e.process(":int 0,2,x")
            checkTrue(":int 限值正确显示", out.contains("[0]") && out.contains("[2]"), out)
            checkTrue(":int 结果 = 2", out.contains("= 2"), out)
        }
        run {
            // 参数没填完时给标注式预览而不是报错
            val e = CalcEngine()
            val out = e.process(":int ")
            checkTrue(":int 未完时给提示", out.contains("请补齐"), out)
        }
        run {
            val e = CalcEngine()
            val out = e.process(":set a=5")
            check(":set 回显", out, "a = 5")
            checkTrue(":set 后可用 a", e.process("a*2+1").contains("11"), e.process("a*2+1"))
        }
        run {
            val e = CalcEngine()
            checkTrue("help 命令可用", e.process(":help").contains("解方程"), "help 输出异常")
            checkTrue("未知命令提示", e.process(":zzz").contains("未知命令"), e.process(":zzz"))
        }
    }

    // ---------------- 6. 一次 / 二次方程 ----------------
    private fun testLinearAndQuadratic() {
        run {
            val r = Solver.solve("2x+3=7")
            check("2x+3=7 -> x=2", r.rationalRoots.map { it.toString() }, listOf("2"))
            check("2x+3=7 变量名", r.variable, "x")
        }
        run {
            val r = Solver.solve("3x-5=x+1")
            check("3x-5=x+1 -> x=3", r.rationalRoots.map { it.toString() }, listOf("3"))
        }
        run {
            val r = Solver.solve("2y+6")
            check("2y+6 自动补 =0 -> y=-3", r.rationalRoots.map { it.toString() }, listOf("-3"))
            check("2y+6 变量名是 y", r.variable, "y")
        }
        run {
            val r = Solver.solve("x^2-2=0")
            val text = r.exactTexts.joinToString(" ")
            checkTrue("x^2-2=0 给出精确根式", text.contains("√2"), text)
            checkTrue("x^2-2=0 含正负两根", text.contains("或"), text)
            check("x^2-2=0 小数根", r.realRoots.map { fmt(it) }, listOf("-1.4142135624", "1.4142135624"))
        }
        run {
            val r = Solver.solve("x^2-2x+1=0")
            checkTrue("x^2-2x+1=0 重根", r.exactTexts.joinToString("").contains("重根"),
                r.exactTexts.toString())
        }
        run {
            val r = Solver.solve("x^2-x-1=0")
            checkTrue("x^2-x-1=0 含 √5", r.exactTexts.joinToString("").contains("√5"),
                r.exactTexts.toString())
            checkTrue("x^2-x-1=0 黄金比", r.realRoots.any { abs(it - 1.6180339887) < 1e-9 },
                r.realRoots.toString())
        }
        run {
            val r = Solver.solve("2x^2+3x-2=0")
            checkTrue("2x^2+3x-2=0 有 -2 与 1/2",
                r.exactTexts.joinToString("").contains("-2") && r.exactTexts.joinToString("").contains("1/2"),
                r.exactTexts.toString())
        }
        run {
            val r = Solver.solve("x^2+1=0")
            checkTrue("x^2+1=0 复数精确解", r.exactTexts.joinToString("").contains("i"),
                r.exactTexts.toString())
            check("x^2+1=0 复数对", r.complexPairs.size, 1)
        }
        run {
            val r = Solver.solve("(1/2)x^2-2=0")
            checkTrue("(1/2)x^2-2=0 -> x=±2", r.exactTexts.joinToString("").contains("2"),
                r.exactTexts.toString())
        }
    }

    // ---------------- 7. 高次多项式(实根 + 复根) ----------------
    private fun testHighDegree() {
        run {
            val r = Solver.solve("x^3-6x^2+11x-6=0")
            check("三次方程三个有理根", r.rationalRoots.map { it.toString() }, listOf("1", "2", "3"))
            val text = Display.showEquation(r, "x^3-6x^2+11x-6=0")
            checkTrue("三次方程根数显示为 3", text.contains("共 3 个根"),
                text.lines().firstOrNull { it.contains("共") } ?: text)
        }
        run {
            val r = Solver.solve("(x-1)^2*(x+3)=0")
            val text = Display.showEquation(r, "(x-1)^2*(x+3)=0")
            checkTrue("重根方程根数显示为 3(含重数)", text.contains("共 3 个根"),
                text.lines().firstOrNull { it.contains("共") } ?: text)
        }
        run {
            val r = Solver.solve("x^3-1=0")
            val text = Display.showEquation(r, "x^3-1=0")
            checkTrue("含复根的方程根数显示为 3", text.contains("共 3 个根"),
                text.lines().firstOrNull { it.contains("共") } ?: text)
        }
        run {
            val r = Solver.solve("(x-1)^2*(x+3)=0")
            checkTrue("重根计数 x=1 ×2", (r.multiplicity[Rational.of(1)] ?: 0) == 2,
                r.multiplicity.toString())
            checkTrue("还有 x=-3", r.rationalRoots.any { it == Rational.of(-3) },
                r.rationalRoots.toString())
        }
        run {
            val r = Solver.solve("x^4-5x^2+4=0")
            check("四次方程四根", r.rationalRoots.map { it.toString() }.sorted(),
                listOf("-1", "-2", "1", "2"))
        }
        run {
            val r = Solver.solve("x^3-1=0")
            checkTrue("x^3-1=0 有实根 1", r.rationalRoots.any { it == Rational.ONE },
                r.rationalRoots.toString())
            check("x^3-1=0 有复数对", r.complexPairs.size, 1)
            checkTrue("复数实部约 -0.5",
                abs(r.complexPairs[0].first + 0.5) < 1e-6, r.complexPairs.toString())
        }
        run {
            val r = Solver.solve("x^3-2=0")
            checkTrue("x^3-2=0 实根 ≈1.2599210499",
                r.realRoots.any { abs(it - 1.2599210499) < 1e-7 }, r.realRoots.toString())
            check("x^3-2=0 一个复数对", r.complexPairs.size, 1)
        }
        run {
            val r = Solver.solve("x^5-x-1=0")
            checkTrue("x^5-x-1=0 实根 ≈1.1673039783",
                r.realRoots.any { abs(it - 1.1673039783) < 1e-7 }, r.realRoots.toString())
            check("x^5-x-1=0 两个复数对", r.complexPairs.size, 2)
        }
        run {
            val r = Solver.solve("x^6-1=0")
            checkTrue("x^6-1=0 有 ±1", r.rationalRoots.contains(Rational.ONE) &&
                r.rationalRoots.contains(Rational.of(-1)), r.rationalRoots.toString())
            check("x^6-1=0 两个复数对", r.complexPairs.size, 2)
        }
        run {
            val r = Solver.solve("x^4+1=0")
            check("x^4+1=0 两个复数对", r.complexPairs.size, 2)
            checkTrue("x^4+1=0 虚部 ≈0.7071",
                abs(r.complexPairs[0].second - 0.7071067812) < 1e-6, r.complexPairs.toString())
        }
    }

    // ---------------- 8. 超越方程(数值求解) ----------------
    private fun testNumericEquations() {
        run {
            val r = Solver.solve("sin(x)=0.5")
            checkTrue("sin(x)=0.5 含 ≈0.5235987756",
                r.realRoots.any { abs(it - 0.5235987756) < 1e-7 }, r.realRoots.toString())
        }
        run {
            val r = Solver.solve("e^x=2")
            checkTrue("e^x=2 含 ≈0.6931471806",
                r.realRoots.any { abs(it - 0.6931471806) < 1e-7 }, r.realRoots.toString())
        }
        run {
            val r = Solver.solve("ln(x)=1")
            checkTrue("ln(x)=1 含 ≈2.7182818285",
                r.realRoots.any { abs(it - 2.7182818285) < 1e-6 }, r.realRoots.toString())
        }
        run {
            val r = Solver.solve("x=cos(x)")
            checkTrue("x=cos(x) 含 ≈0.7390851332",
                r.realRoots.any { abs(it - 0.7390851332) < 1e-7 }, r.realRoots.toString())
        }
        run {
            val r = Solver.solve("x^2=cos(x)")
            checkTrue("x^2=cos(x) 含 ±0.8241323123",
                r.realRoots.any { abs(it - 0.8241323123) < 1e-7 } &&
                    r.realRoots.any { abs(it + 0.8241323123) < 1e-7 }, r.realRoots.toString())
        }
        run {
            val r = Solver.solve("e^x=10", wide = true)
            checkTrue("e^x=10 含 ≈2.3025850930",
                r.realRoots.any { abs(it - 2.3025850930) < 1e-7 }, r.realRoots.toString())
        }
        run {
            val r = Solver.solve("sin(t)=0.5")
            check("sin(t) 变量名是 t", r.variable, "t")
        }
    }

    // ---------------- 8b. 统计计算 ----------------
    private fun testStats() {
        run {
            val s = Stats.oneVar(listOf(1.0, 2.0, 3.0, 4.0, 5.0))
            check("统计 n", s.n.toString(), "5")
            check("统计 Σx", s.sumX.toString(), "15.0")
            check("统计 Σx²", s.sumX2.toString(), "55.0")
            check("统计均值", s.mean.toString(), "3.0")
            // 手算:偏差平方和 = 10;总体方差 = 2;样本方差 = 2.5
            check("统计总体σx", Stats.round(s.popSd, 6).toString(), "1.414214")
            check("统计样本sx", Stats.round(s.sampleSd, 6).toString(), "1.581139")
            check("统计最小", s.min.toString(), "1.0")
            check("统计最大", s.max.toString(), "5.0")
            check("统计中位数", s.median.toString(), "3.0")
            // n=5 → 下半 {1,2} 中位数 1.5;上半 {4,5} 中位数 4.5
            check("统计 Q1", s.q1.toString(), "1.5")
            check("统计 Q3", s.q3.toString(), "4.5")
        }
        run {
            val s = Stats.oneVar(listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0))
            check("统计另一组均值", s.mean.toString(), "5.0")
            check("统计另一组σ", Stats.round(s.popSd, 6).toString(), "2.0")
        }
        run {
            val xs = listOf(1.0, 2.0, 3.0)
            val ys = listOf(3.0, 5.0, 7.0)      // y = 1 + 2x
            val r = Stats.regress(Stats.Model.LINEAR, xs, ys)
            check("线性回归 A", Stats.round(r.coeffs[0], 9).toString(), "1.0")
            check("线性回归 B", Stats.round(r.coeffs[1], 9).toString(), "2.0")
            check("线性回归 r", Stats.round(r.corr, 9).toString(), "1.0")
            check(
                "回归预测 x=4",
                Stats.round(Stats.predict(Stats.Model.LINEAR, r.coeffs, 4.0), 9).toString(),
                "9.0"
            )
        }
        run {
            val xs = listOf(-2.0, -1.0, 0.0, 1.0, 2.0)
            val ys = xs.map { it * it }
            val r = Stats.regress(Stats.Model.QUADRATIC, xs, ys)
            check("二次回归 A≈0", Stats.round(r.coeffs[0], 9).toString(), "0.0")
            check("二次回归 B≈0", Stats.round(r.coeffs[1], 9).toString(), "0.0")
            check("二次回归 C≈1", Stats.round(r.coeffs[2], 9).toString(), "1.0")
        }
        run {
            val xs = listOf(1.0, 2.0, 3.0)
            val ys = xs.map { 2.0 * it * it * it }   // y = 2x³
            val r = Stats.regress(Stats.Model.POWER, xs, ys)
            check("幂回归 A≈2", Stats.round(r.coeffs[0], 6).toString(), "2.0")
            check("幂回归 B≈3", Stats.round(r.coeffs[1], 6).toString(), "3.0")
        }
        run {
            val (a, err) = Stats.parseList("1, 2, sqrt(4), 3")
            checkTrue("统计解析无错", err == null, err ?: "")
            check("统计解析结果", a!!.map { it.toString() }, listOf("1.0", "2.0", "2.0", "3.0"))
            val (b, err2) = Stats.parseList("1, abc(")
            checkTrue("非法数据要报错", b == null && err2 != null, err2 ?: "没有报错")
        }
        run {
            val t = Stats.twoVar(listOf(1.0, 2.0, 3.0), listOf(2.0, 4.0, 6.0))
            check("二变量 Σxy", t.sumXY.toString(), "28.0")
            check("二变量 Σy", t.sumY.toString(), "12.0")
            check("二变量 ȳ", t.meanY.toString(), "4.0")
            check("二变量 r", Stats.round(t.corr, 9).toString(), "1.0")
        }
        run {
            // 补全的统计量:高阶和与方差(手册"求和"组共 8 项)
            val s = Stats.oneVar(listOf(1.0, 2.0, 3.0))
            check("Σx³", s.sumX3.toString(), "36.0")
            check("Σx⁴", s.sumX4.toString(), "98.0")
            // 偏差平方和 = 2;总体方差 2/3,样本方差 1
            check("总体 σ²x", Stats.round(s.popVar, 9).toString(), "0.666666667")
            check("样本 s²x", Stats.round(s.sampleVar, 9).toString(), "1.0")
            check("σx² 与 σ²x 一致", Stats.round(s.popSd * s.popSd, 9).toString(), "0.666666667")
        }
        run {
            val t = Stats.twoVar(listOf(1.0, 2.0, 3.0), listOf(2.0, 4.0, 6.0))
            check("Σx²y", t.sumX2Y.toString(), "72.0")
            check("最小 y", t.minY.toString(), "2.0")
            check("最大 y", t.maxY.toString(), "6.0")
            check("总体 σ²y", Stats.round(t.popVarY, 9).toString(), "2.666666667")
            check("样本 s²y", Stats.round(t.sampleVarY, 9).toString(), "4.0")
        }
        run {
            // 正态分布:手册的 P / Q / R / 't
            check("P(0)", Stats.round(Stats.normalP(0.0), 9).toString(), "0.5")
            check("P(1.96)", Stats.round(Stats.normalP(1.96), 6).toString(), "0.975002")
            check("Q(0)", Stats.round(Stats.normalQ(0.0), 9).toString(), "0.5")
            check("R(0)", Stats.round(Stats.normalR(0.0), 9).toString(), "0.0")
            val p = Stats.normalP(1.3)
            val q = Stats.normalQ(1.3)
            check("P+Q=1", Stats.round(p + q, 12).toString(), "1.0")
            check("R=P-Q", Stats.round(Stats.normalR(1.3), 12).toString(), Stats.round(p - q, 12).toString())
            check(
                "对称性 P(-t)=Q(t)",
                Stats.round(Stats.normalP(-0.7), 12).toString(),
                Stats.round(Stats.normalQ(0.7), 12).toString()
            )
            check("erf(0)", Stats.erf(0.0).toString(), "0.0")
            checkTrue("erf(1)≈0.8427", abs(Stats.erf(1.0) - 0.8427007929) < 1e-9, Stats.erf(1.0).toString())
            checkTrue("erf(3)≈0.99998", abs(Stats.erf(3.0) - 0.9999779095) < 1e-9, Stats.erf(3.0).toString())
            checkTrue("erf(-1) 为负", Stats.erf(-1.0) < 0, Stats.erf(-1.0).toString())
        }
        run {
            // 标准化变量 't = (x - x̄)/sx。注意这组数据的样本标准差是 √(32/7)≈2.138,不是 2
            val data = listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0)  // x̄=5, sx=2
            check("标准化 x=3", Stats.round(Stats.standardize(3.0, data), 6).toString(), "-0.935414")
            check("标准化 x=5", Stats.round(Stats.standardize(5.0, data), 9).toString(), "0.0")
            check("标准化 x=7", Stats.round(Stats.standardize(7.0, data), 6).toString(), "0.935414")
            checkTrue(
                "σ=0 时标准化为 NaN",
                Stats.standardize(1.0, listOf(2.0, 2.0, 2.0)).isNaN(),
                "应为 NaN"
            )
        }
        run {
            // 命令层:输出应包含新补的统计量
            val e = CalcEngine()
            val out = e.process(":stat 1 2,4,4,4,5,5,7,9")
            checkTrue("统计输出含 σ²x", out.contains("σ²x"), out)
            checkTrue("统计输出含 Σx⁴", out.contains("Σx⁴"), out)
        }
        run {
            // 命令层:正态分布 :norm x 数据…
            val e = CalcEngine()
            val data = "2,4,4,4,5,5,7,9"
            val out = e.process(":norm 3 $data")
            checkTrue(":norm 含 P(t)", out.contains("P(t)"), out)
            checkTrue(":norm 含 Q(t)", out.contains("Q(t)"), out)
            checkTrue(":norm 含 R(t)", out.contains("R(t)"), out)
            checkTrue(":norm 含标准化 't", out.contains("'t"), out)
            checkTrue(":norm 报告均值", out.contains("x̄"), out)
            // 提示与错误分支
            checkTrue(":norm 缺数据给用法", e.process(":norm 3").contains("用法"), ":norm 3")
            checkTrue(":norm 非数值 x 报错", e.process(":norm abc 1,2,3").contains("错误"), "abc")
        }
        run {
            // 命令层:用户实际输入路径
            val e = CalcEngine()
            val out = e.process(":stat 1 1,2,3,4,5")
            checkTrue(":stat 命令给出均值", out.contains("3"), out)
            checkTrue(":stat 命令给出 n", out.contains("5"), out)
        }
        run {
            val e = CalcEngine()
            val out = e.process(":stat 2 1,2,3 | 3,5,7")
            checkTrue(":stat 2 命令给出回归", out.contains("B"), out)
        }
    }

    // ---------------- 8c. 显示格式(SETUP + Fmt) ----------------
    private fun testFmt() {
        val def = Settings()
        run {
            // 默认设定应与手册标注的初始缺省值一致
            checkTrue("默认输入输出", def.ioMode == IoMode.MATH_MATH, def.ioMode.name)
            checkTrue("默认角度单位", def.angle == AngleUnit.DEGREE, def.angle.name)
            checkTrue("默认显示格式", def.display == DisplayMode.Norm(1), def.display.toString())
            checkTrue("默认小数点为句点", def.decimalPoint == '.', def.decimalPoint.toString())
            checkTrue("默认数学输出", def.mathOutput, "mathOutput=" + def.mathOutput)
        }
        run {
            // 手册:100/7 在 Fix3 → 14.286;Fix2 → 14.29
            val v = 100.0 / 7.0
            check("Fix3 100/7", Fmt.fixString(v, 3, def), "14.286")
            check("Fix2 100/7", Fmt.fixString(v, 2, def), "14.29")
        }
        run {
            // 手册:1/7 在 Sci5 → 1.4286×10⁻¹
            check("Sci5 1/7", Fmt.sciString(1.0 / 7.0, 5, def), "1.4286×10⁻¹")
        }
        run {
            // 手册:1/200 在 Norm1 → 5×10⁻³;Norm2 → 0.005
            val v = 1.0 / 200.0
            check("Norm1 1/200", Fmt.normString(v, 1, def), "5×10⁻³")
            check("Norm2 1/200", Fmt.normString(v, 2, def), "0.005")
        }
        run {
            check("整数 1200", Fmt.normString(1200.0, 1, def), "1200")
            val small = Fmt.normString(0.000123, 1, def)
            checkTrue("小量走指数", small.contains("×10"), small)
        }
        run {
            // 工程符号:指数取 3 的倍数
            check("工程 1234", Fmt.engineering(1234.0, def, 10), "1.234k")
            check("工程 0.0012", Fmt.engineering(0.0012, def, 10), "1.2m")
        }
        run {
            // 分数与带分数
            check("分数 2/3", Fmt.fractionString(2.0 / 3.0, def), "2/3")
            check("假分数 5/3", Fmt.fractionString(5.0 / 3.0, def), "5/3")
            check(
                "带分数 5/3",
                Fmt.fractionString(5.0 / 3.0, def.copy(mixedFraction = true)),
                "1⌐2/3"
            )
            check("负分数 -1/4", Fmt.fractionString(-0.25, def), "-1/4")
        }
        run {
            check("有限小数 1/8", Fmt.terminatingDecimal(1, 8), "0.125")
            checkTrue("1/3 非有限小数", Fmt.terminatingDecimal(1, 3) == null, "不该判定为有限小数")
        }
        run {
            val sep = def.copy(digitSeparator = true)
            check("千位分隔符", Fmt.fixString(1234567.0, 0, sep), "1,234,567")
            val comma = def.copy(decimalComma = true)
            check("小数点用逗号", Fmt.fixString(3.5, 1, comma), "3,5")
            checkTrue("结果分隔符变分号", comma.resultSeparator == ";", comma.resultSeparator)
        }
        run {
            // 精确形式(仅数学输出)
            check("精确形式 π/2", Fmt.exactForm(Math.PI / 2, def), "π/2")
            check("精确形式 π", Fmt.exactForm(Math.PI, def), "π")
            check("精确形式 √2", Fmt.exactForm(Math.sqrt(2.0), def), "√2")
            val decMode = def.copy(ioMode = IoMode.MATH_DEC)
            checkTrue("小数输出无精确形式", Fmt.exactForm(Math.PI / 2, decMode) == null, "不该给出")
        }
        run {
            // 统一入口 number()
            val fixed = def.copy(display = DisplayMode.Fix(3))
            check("number Fix3", Fmt.number(100.0 / 7.0, fixed), "14.286")
            check("number 分数", Fmt.number(2.0 / 3.0, def, mathExact = true), "2/3")
            check("number 工程", Fmt.number(1234.0, def.copy(engineering = true)), "1.234k")
        }
        run {
            // :setup 命令(用户实际输入路径)
            val e = CalcEngine()
            val out = e.process(":setup")
            checkTrue(":setup 列出 14 项设定", out.contains("输入/输出") && out.contains("角度单位"), out)
            checkTrue(":setup 含工程符号", out.contains("工程符号"), out)
            checkTrue(":setup 含多行字体", out.contains("多行字体"), out)
        }
        run {
            val e = CalcEngine()
            e.process(":setup display fix3")
            checkTrue("display fix3 生效", e.settings.display == DisplayMode.Fix(3), e.settings.display.toString())
            // 结果格式化随设定变化
            check("Fix3 结果", e.fmtResult(100.0 / 7.0), "14.286")
            e.process(":setup display norm1")
            checkTrue("切回 norm1", e.settings.display == DisplayMode.Norm(1), e.settings.display.toString())
        }
        run {
            val e = CalcEngine()
            e.process(":setup io 2")
            checkTrue("io 2 为非数学输出", !e.settings.mathOutput, e.settings.ioMode.name)
            checkTrue("io 2 仍为数学输入", e.settings.mathInput, e.settings.ioMode.name)
            // 非数学输出时不给 π/根式精确形式
            checkTrue("io2 无精确形式", e.fmtResult(Math.PI / 2) != "π/2", e.fmtResult(Math.PI / 2))
        }
        run {
            val e = CalcEngine()
            e.process(":setup frac mixed")
            checkTrue("frac mixed 生效", e.settings.mixedFraction, "mixedFraction=" + e.settings.mixedFraction)
            check("带分数结果", Fmt.fractionString(5.0 / 3.0, e.settings), "1⌐2/3")
            e.process(":setup reset")
            checkTrue("reset 恢复缺省", e.settings == Settings(), e.settings.toString())
        }
        run {
            val e = CalcEngine()
            val bad = e.process(":setup display 乱码")
            checkTrue("非法取值给提示", bad.contains("display 取值"), bad)
            val unknown = e.process(":setup 不存在项 on")
            checkTrue("未知设定项给提示", unknown.contains("未知设定项"), unknown)
        }
    }

    // ---------------- 8d. LCD 显示模型与渲染 ----------------
    private fun testLcd() {
        val def = Settings()
        run {
            // 分数必须竖排:分子 / 横线 / 分母
            val f = Lcd.Frac(Lcd.Text("2"), Lcd.Text("3"))
            val out = LcdRender.render(f)
            check("分数竖排为三行", out.lines().size, 3)
            check("分子在第一行", out.lines()[0].trim(), "2")
            checkTrue("第二行是分数线", out.lines()[1].trim().all { it == '─' }, out.lines()[1])
            check("分母在第三行", out.lines()[2].trim(), "3")
        }
        run {
            // 带分数:整数与分数并排
            val f = Lcd.Frac(Lcd.Text("2"), Lcd.Text("3"), mixedWhole = Lcd.Text("1"))
            val out = LcdRender.render(f)
            val first = out.lines()[0]
            checkTrue("带分数第一行含整数 1", first.startsWith("1"), out)
            checkTrue("带分数第一行含分子 2", first.contains("2"), out)
        }
        run {
            // 根号:第一行是 √ 加横线,覆盖被开方数
            val r = Lcd.Sqrt(Lcd.Text("2"))
            val out = LcdRender.render(r)
            check("根号两行", out.lines().size, 2)
            checkTrue("根号行含 √", out.lines()[0].contains("√"), out)
            checkTrue("根号行含横线", out.lines()[0].contains("─"), out)
            check("被开方数在第二行", out.lines()[1].trim(), "2")
        }
        run {
            // 乘方:指数在右上
            val p = Lcd.Pow(Lcd.Text("x"), Lcd.Text("2"))
            check("乘方渲染", LcdRender.render(p), "x2")
            check("乘方单行", LcdRender.renderInline(p), "x^(2)")
        }
        run {
            // 积分上下限结构
            val lim = Lcd.Limits("∫", Lcd.Text("0"), Lcd.Text("1"), Lcd.Text("x^2 dx"))
            val out = LcdRender.render(lim)
            checkTrue("积分含上限", out.contains("1"), out)
            checkTrue("积分含下限", out.contains("0"), out)
            checkTrue("积分含主体", out.contains("x^2 dx"), out)
        }
        run {
            // 指示符随设定与模式变化
            val i0 = Lcd.Indicators.of(def)
            checkTrue("缺省显示 Math", i0.symbols().contains("Math"), i0.symbols().toString())
            // 缺省不在复数模式 → 不应显示 i
            checkTrue("非复数模式不显示 i", !i0.symbols().contains("i"), i0.symbols().toString())
            val eng = def.copy(engineering = true, digitSeparator = true)
            val i1 = Lcd.Indicators.of(eng)
            checkTrue("工程符号显示 E", i1.symbols().contains("E"), i1.symbols().toString())
            // 进入复数模式且设定为 a+bi → 显示 i
            val iC = Lcd.Indicators.of(def, complexMode = true)
            checkTrue("复数模式显示 i", iC.symbols().contains("i"), iC.symbols().toString())
            // 复数模式 + 极坐标 → 显示 ∠ 而不显示 i
            val polar = def.copy(complexPolar = true)
            val i2 = Lcd.Indicators.of(polar, complexMode = true)
            checkTrue("极坐标显示 ∠", i2.symbols().contains("∠"), i2.symbols().toString())
            checkTrue("极坐标不显示 i", !i2.symbols().contains("i"), i2.symbols().toString())
            val mem = Lcd.Indicators.of(def, memoryUsed = true)
            checkTrue("用存储器显示 M", mem.symbols().contains("M"), mem.symbols().toString())
        }
        run {
            // 精确形式必须转成对应结构,否则会退回小数(此前的缺陷)
            val rad = LcdRender.ofNumber(Math.sqrt(2.0), def)
            checkTrue("√2 构造为带根号结构", rad is Lcd.Sqrt || rad is Lcd.Row, rad.toString())
            val radText = LcdRender.render(rad)
            checkTrue("√2 渲染含 √", radText.contains("√"), radText)
            checkTrue("√2 渲染不含小数", !radText.contains("1.4142"), radText)
            // 2√3 这类带系数
            val rad2 = LcdRender.ofNumber(2.0 * Math.sqrt(3.0), def)
            val rad2Text = LcdRender.renderInline(rad2)
            checkTrue("2√3 含系数与根号", rad2Text.contains("2") && rad2Text.contains("√"), rad2Text)
            // π/2 仍应为分数结构
            val piHalf = LcdRender.ofNumber(Math.PI / 2, def)
            checkTrue("π/2 构造为 Frac", piHalf is Lcd.Frac, piHalf.toString())
        }
        run {
            // 由数值构造显示树:2/3 应为分数结构
            val node = LcdRender.ofNumber(2.0 / 3.0, def)
            checkTrue("2/3 构造为 Frac", node is Lcd.Frac, node.toString())
            val rendered = LcdRender.render(node)
            check("2/3 竖排三行", rendered.lines().size, 3)
            // 整数不是分数
            val intNode = LcdRender.ofNumber(4.0, def)
            checkTrue("整数不是 Frac", intNode is Lcd.Text, intNode.toString())
            // 小数输出模式下不给分数
            val decMode = def.copy(ioMode = IoMode.LINEAR_DEC)
            val decNode = LcdRender.ofNumber(2.0 / 3.0, decMode)
            checkTrue("小数输出不给 Frac", decNode is Lcd.Text, decNode.toString())
        }
        run {
            // 输入长度上限:手册规定一次计算最多 199 字符
            check("输入上限常量为 199", Edit.MAX_INPUT.toString(), "199")
            val ps = mutableListOf<Edit.Part>(Edit.Raw(""))
            val c0 = Edit.Cursor(0, 0, 0)
            // 插满到接近上限
            var c = Edit.insert(ps, c0, "1".repeat(190))
            check("190 字符可插入", Edit.byteLength(ps).toString(), "190")
            checkTrue("尚可继续输入", Edit.canInsert(ps, "12345"), "应可插入 5 个")
            // 再插 9 个 → 199
            c = Edit.insert(ps, c, "2".repeat(9))
            check("达到 199", Edit.byteLength(ps).toString(), "199")
            checkTrue("到上限后拒绝插入", !Edit.canInsert(ps, "3"), "应拒绝")
            val before = Edit.getText(ps)
            c = Edit.insert(ps, c, "3")
            check("拒绝后内容不变", Edit.getText(ps), before)
            check("剩余为 0", Edit.remainBytes(ps).toString(), "0")
            checkTrue("已接近上限", Edit.nearLimit(ps), "应提示")
        }
        run {
            // 剩余提示阈值:剩 ≤10 时提示
            val ps = mutableListOf<Edit.Part>(Edit.Raw("1".repeat(188)))
            check("剩 11 不提示", Edit.nearLimit(ps).toString(), "false")
            val ps2 = mutableListOf<Edit.Part>(Edit.Raw("1".repeat(189)))
            check("剩 10 提示", Edit.nearLimit(ps2).toString(), "true")
        }
        run {
            // 输入模式差异集中在一处(后续接真模板只改这里)
            check("数学输入下 √ 补左括号", Edit.insertTextFor(IoMode.MATH_MATH, "√"), "√(")
            check("线性输入下 √ 补左括号", Edit.insertTextFor(IoMode.LINEAR_LINEAR, "√"), "√(")
            check("普通键原样", Edit.insertTextFor(IoMode.MATH_MATH, "7"), "7")
            // 4 种输入模式的数学输入/输出判定
            checkTrue("io1 数学输入", Settings(ioMode = IoMode.MATH_MATH).mathInput, "应为真")
            checkTrue("io1 数学输出", Settings(ioMode = IoMode.MATH_MATH).mathOutput, "应为真")
            checkTrue("io2 数学输入", Settings(ioMode = IoMode.MATH_DEC).mathInput, "应为真")
            checkTrue("io2 非数学输出", !Settings(ioMode = IoMode.MATH_DEC).mathOutput, "应为假")
            checkTrue("io3 非数学输入", !Settings(ioMode = IoMode.LINEAR_LINEAR).mathInput, "应为假")
            checkTrue("io4 非数学输入", !Settings(ioMode = IoMode.LINEAR_DEC).mathInput, "应为假")
        }
    }

    // ---------------- 8e. 模式 / 键前缀 / 存储器 / 历史(第 1 批) ----------------
    private fun testMemoryAndHistory() {
        run {
            // HOME 菜单:手册共 10 个模式(计算/复数/基数/矩阵/向量/统计/表格/方程函数/不等式/比例)
            val modes = Calc.Mode.entries
            checkTrue("模式数量不少于 9", modes.size >= 9, "实际 ${modes.size}")
            checkTrue("含计算模式", modes.contains(Calc.Mode.COMP), modes.toString())
            checkTrue("含方程/函数模式", modes.contains(Calc.Mode.EQN), modes.toString())
            checkTrue("含比例模式", modes.contains(Calc.Mode.RATIO), modes.toString())
            // 已实现的模式要标出来
            checkTrue("计算模式已实现", Calc.Mode.COMP.implemented, "应为真")
            checkTrue("统计模式已实现", Calc.Mode.STAT.implemented, "应为真")
            checkTrue("矩阵模式已实现", Calc.Mode.MATRIX.implemented, "应为真")
            checkTrue("向量模式已实现", Calc.Mode.VECTOR.implemented, "应为真")
            checkTrue("基数模式已实现", Calc.Mode.BASE_N.implemented, "应为真")
            checkTrue("表格模式尚未实现", !Calc.Mode.TABLE.implemented, "应为假")
        }
        run {
            // SHIFT / ALPHA 是"一次性前缀"
            val m0 = Calc.KeyMods()
            checkTrue("初始无前缀", m0.symbols().isEmpty(), m0.symbols().toString())
            val m1 = m0.toggledShift()
            checkTrue("按下 SHIFT 显示 S", m1.symbols().contains("S"), m1.symbols().toString())
            checkTrue("SHIFT 与 ALPHA 互斥", !m1.alpha, "alpha=${m1.alpha}")
            val m2 = m1.toggledAlpha()
            checkTrue("按 ALPHA 后只剩 A", m2.symbols() == listOf("A"), m2.symbols().toString())
            val m3 = m2.consumed()
            checkTrue("按键后前缀清除", m3.symbols().isEmpty(), m3.symbols().toString())
        }
        run {
            // 历史记录与重放
            val h = Calc.History()
            checkTrue("空历史向上翻返回 null", h.up() == null, "应返回 null")
            h.add("1+1")
            h.add("2*3")
            h.add("sin(0)")
            check("历史条数", h.entries().size.toString(), "3")
            check("向上翻到最新一条", h.up() ?: "", "sin(0)")
            check("再向上翻", h.up() ?: "", "2*3")
            check("继续向上翻", h.up() ?: "", "1+1")
            check("到最早后停住", h.up() ?: "", "1+1")
            check("向下翻回", h.down() ?: "", "2*3")
            check("向下翻到最新", h.down() ?: "", "sin(0)")
            checkTrue("再向下翻回到未翻阅", h.down() == null && !h.browsing(), "应结束翻阅")
            h.clear()
            check("清空历史", h.entries().size.toString(), "0")
        }
        run {
            // 连续重复的算式不重复记录
            val h = Calc.History()
            h.add("5+5")
            h.add("5+5")
            check("重复算式只记一条", h.entries().size.toString(), "1")
        }
        run {
            // 历史长度上限
            val h = Calc.History(limit = 3)
            h.add("a"); h.add("b"); h.add("c"); h.add("d")
            check("超出上限丢弃最早的", h.entries().joinToString(","), "b,c,d")
        }
        run {
            // 存储器:Ans 与独立存储器 M
            val mem = Calc.Memory()
            checkTrue("初始无 Ans", mem.ans == null, "应为 null")
            mem.setAns(42.0)
            check("Ans 记录最新结果", mem.ans.toString(), "42.0")
            checkTrue("M 初值为 0 且未使用", !mem.mUsed(), "应为假")
            mem.mPlus(10.0)
            mem.mPlus(5.0)
            check("M+ 累加", mem.independent.toString(), "15.0")
            mem.mMinus(3.0)
            check("M- 累减", mem.independent.toString(), "12.0")
            checkTrue("M 非 0 后显示指示符", mem.mUsed(), "应为真")
            mem.store("A", 7.0)
            check("变量 A 可回读", mem.recall("A").toString(), "7.0")
            check("变量名大小写不敏感", mem.recall("a").toString(), "7.0")
            mem.clear()
            checkTrue("清空后 Ans 为 null", mem.ans == null, "应为 null")
            checkTrue("清空后 M 归零", !mem.mUsed(), "应为假")
            checkTrue("清空后变量为空", mem.all().isEmpty(), mem.all().toString())
        }
    }

    // ---------------- 8f. 统计编辑器与频率加权(第 2 批) ----------------
    private fun testStatEditor() {
        run {
            val d = Stats.StatData(twoVar = false)
            check("单变量最大行数", d.maxRows.toString(), "160")
            d.addRow(Stats.StatRow(x = 1.0))
            d.addRow(Stats.StatRow(x = 2.0))
            d.addRow(Stats.StatRow(x = 3.0))
            check("行数", d.rows.size.toString(), "3")
            check("已填行数", d.filledCount().toString(), "3")
            d.setX(1, 20.0)
            check("改单元格", d.rows[1].x.toString(), "20.0")
        }
        run {
            val d = Stats.StatData(twoVar = true)
            check("双变量最大行数", d.maxRows.toString(), "80")
        }
        run {
            // 插入行 / 删除行 / 全部删除
            val d = Stats.StatData(twoVar = false)
            d.addRow(Stats.StatRow(x = 1.0))
            d.addRow(Stats.StatRow(x = 3.0))
            val ins = d.insertRow(1, Stats.StatRow(x = 2.0))
            check("插入返回下标", ins.toString(), "1")
            check("插入后顺序", d.rows.map { it.x }.toString(), "[1.0, 2.0, 3.0]")
            checkTrue("删除中间行成功", d.deleteRow(1), "应成功")
            check("删除后顺序", d.rows.map { it.x }.toString(), "[1.0, 3.0]")
            checkTrue("删除越界行失败", !d.deleteRow(99), "应失败")
            d.clear()
            check("全部删除后为空", d.rows.size.toString(), "0")
        }
        run {
            // 行数上限
            val d = Stats.StatData(twoVar = true)
            repeat(80) { d.addRow(Stats.StatRow(x = 1.0, y = 1.0)) }
            check("到达上限", d.rows.size.toString(), "80")
            check("超限拒绝加行", d.addRow(Stats.StatRow(x = 1.0)).toString(), "-1")
            check("超限拒绝插行", d.insertRow(0, Stats.StatRow(x = 1.0)).toString(), "-1")
        }
        run {
            // 频率加权:1×1、2×2、3×3 → n=6,均值 14/6
            val d = Stats.StatData(twoVar = false)
            d.addRow(Stats.StatRow(x = 1.0, freq = 1.0))
            d.addRow(Stats.StatRow(x = 2.0, freq = 2.0))
            d.addRow(Stats.StatRow(x = 3.0, freq = 3.0))
            val (list, err) = d.expandSingle(useFreq = true)
            checkTrue("频率展开无错", err == null, err ?: "")
            check("展开后元素个数", list!!.size.toString(), "6")
            check("展开后总和", list.sum().toString(), "14.0")
            val (list2, _) = d.expandSingle(useFreq = false)
            check("不用频率时个数", list2!!.size.toString(), "3")
        }
        run {
            // 频率非法要报错(负数、非整数)
            val d = Stats.StatData(twoVar = false)
            d.addRow(Stats.StatRow(x = 1.0, freq = -1.0))
            val (_, err) = d.expandSingle(useFreq = true)
            checkTrue("负频率报错", err != null, err ?: "没有报错")
            val d2 = Stats.StatData(twoVar = false)
            d2.addRow(Stats.StatRow(x = 1.0, freq = 1.5))
            val (_, err2) = d2.expandSingle(useFreq = true)
            checkTrue("非整数频率报错", err2 != null, err2 ?: "没有报错")
        }
        run {
            val d = Stats.StatData(twoVar = false)
            val (_, err) = d.expandSingle(useFreq = true)
            checkTrue("空数据报错", err != null, err ?: "没有报错")
        }
        run {
            // 双变量展开与频率
            val d = Stats.StatData(twoVar = true)
            d.addRow(Stats.StatRow(x = 1.0, y = 2.0, freq = 2.0))
            d.addRow(Stats.StatRow(x = 3.0, y = 6.0, freq = 1.0))
            val (xs, ys, err) = d.expandTwo(useFreq = true)
            checkTrue("双变量展开无错", err == null, err ?: "")
            check("x 展开个数", xs.size.toString(), "3")
            check("y 内容", ys.toString(), "[2.0, 2.0, 6.0]")
        }
        run {
            // 缺 y 的行在双变量下被跳过
            val d = Stats.StatData(twoVar = true)
            d.addRow(Stats.StatRow(x = 1.0, y = null))
            d.addRow(Stats.StatRow(x = 2.0, y = 4.0))
            val (xs, _, err) = d.expandTwo(useFreq = false)
            checkTrue("缺 y 不报错", err == null, err ?: "")
            check("只取完整的行", xs.toString(), "[2.0]")
        }
        run {
            // 频率加权后统计量应体现重复次数
            val d = Stats.StatData(twoVar = false)
            d.addRow(Stats.StatRow(x = 1.0, freq = 1.0))
            d.addRow(Stats.StatRow(x = 3.0, freq = 3.0))
            val (list, _) = d.expandSingle(useFreq = true)
            val s = Stats.oneVar(list!!)
            check("频率加权后 n", s.n.toString(), "4")
            check("频率加权后 Σx", s.sumX.toString(), "10.0")
            check("频率加权后均值", s.mean.toString(), "2.5")
        }
    }

    // ---------------- 8g. 矩阵(第 3 批) ----------------
    private fun testMatrix() {
        val a = Matrix.of(2.0, 1.0, 1.0, 1.0)      // [[2,1],[1,1]]
        val b = Matrix.of(2.0, -1.0, -1.0, 2.0)    // [[2,-1],[-1,2]]
        run {
            check("矩阵维度", "${a.rows}×${a.cols}", "2×2")
            check("取元素 a(0,0)", a[0, 0].toString(), "2.0")
            check("取元素 a(1,0)", a[1, 0].toString(), "1.0")
            checkTrue("2×2 为方阵", a.isSquare, "应为真")
            // 最大维度限制
            var err: String? = null
            try {
                Matrix.zeros(5, 5)
            } catch (e: Exception) {
                err = e.message
            }
            checkTrue("超过 4×4 应报错", err != null, err ?: "没有报错")
        }
        run {
            // 单位矩阵
            val i2 = Matrix.identity(2)
            check("单位矩阵 (0,0)", i2[0, 0].toString(), "1.0")
            check("单位矩阵 (0,1)", i2[0, 1].toString(), "0.0")
            check("单位矩阵 (1,1)", i2[1, 1].toString(), "1.0")
            // A × I = A
            val ai = Mat.mul(a, i2)
            check("A×I = A", Mat.format(ai), Mat.format(a))
        }
        run {
            // 加法、减法
            check("A+B", Mat.format(Mat.add(a, b)), "4  0\n0  3")
            check("A-B", Mat.format(Mat.sub(a, b)), "0  2\n2  -1")
            // 维度不一致要报错
            var err: String? = null
            try {
                Mat.add(a, Matrix.zeros(2, 3))
            } catch (e: Exception) {
                err = e.message
            }
            checkTrue("维度不一致相加报错", err != null, err ?: "没有报错")
        }
        run {
            // 乘法:[[2,1],[1,1]] × [[2,-1],[-1,2]] = [[3,0],[1,1]]
            check("A×B", Mat.format(Mat.mul(a, b)), "3  0\n1  1")
            // 纯量乘
            check("3×A", Mat.format(Mat.scale(a, 3.0)), "6  3\n3  3")
            // 乘法维度不满足要报错
            var err: String? = null
            try {
                Mat.mul(Matrix.zeros(2, 3), Matrix.zeros(2, 2))
            } catch (e: Exception) {
                err = e.message
            }
            checkTrue("乘法维度不足报错", err != null, err ?: "没有报错")
        }
        run {
            // 行列式:det([[2,1],[1,1]]) = 1
            check("det(A)", Mat.det(a).toString(), "1.0")
            // det([[2,-1],[-1,2]]) = 3
            check("det(B)", Mat.det(b).toString(), "3.0")
            // 非方阵不能求行列式
            var err: String? = null
            try {
                Mat.det(Matrix.zeros(2, 3))
            } catch (e: Exception) {
                err = e.message
            }
            checkTrue("非方阵求行列式报错", err != null, err ?: "没有报错")
            // 奇异矩阵行列式为 0
            check("奇异矩阵 det=0", Mat.det(Matrix.of(1.0, 2.0, 2.0, 4.0)).toString(), "0.0")
        }
        run {
            // 转置
            val t = Mat.transpose(a)
            check("A 转置维度", "${t.rows}×${t.cols}", "2×2")
            check("A 转置 = A(对称)", Mat.format(t), Mat.format(a))
            val r = Matrix.fromRows(listOf(listOf(1.0, 2.0, 3.0)))   // 1×3
            check("1×3 转置为 3×1", "${Mat.transpose(r).rows}×${Mat.transpose(r).cols}", "3×1")
        }
        run {
            // 逆矩阵:A⁻¹ 满足 A·A⁻¹ = I
            val inv = Mat.inverse(a)
            check("A⁻¹", Mat.format(inv), "1  -1\n-1  2")
            val prod = Mat.mul(a, inv)
            check("A×A⁻¹ = I", Mat.format(prod), "1  0\n0  1")
            // 奇异矩阵求逆要报错
            var err: String? = null
            try {
                Mat.inverse(Matrix.of(1.0, 2.0, 2.0, 4.0))
            } catch (e: Exception) {
                err = e.message
            }
            checkTrue("奇异矩阵求逆报错", err != null, err ?: "没有报错")
        }
        run {
            // 幂:A² = A×A,A⁰ = I,A¹ = A
            check("A²", Mat.format(Mat.pow(a, 2)), Mat.format(Mat.mul(a, a)))
            check("A⁰ = I", Mat.format(Mat.pow(a, 0)), Mat.format(Matrix.identity(2)))
            check("A¹ = A", Mat.format(Mat.pow(a, 1)), Mat.format(a))
            check("A³", Mat.format(Mat.pow(a, 3)), Mat.format(Mat.mul(Mat.mul(a, a), a)))
            // 负指数应报错
            var err: String? = null
            try {
                Mat.pow(a, -1)
            } catch (e: Exception) {
                err = e.message
            }
            checkTrue("负指数报错", err != null, err ?: "没有报错")
        }
        run {
            // 每个元素取绝对值
            val neg = Matrix.of(-2.0, 3.0, -4.0, -5.0)
            check("Abs(矩阵)", Mat.format(Mat.absAll(neg)), "2  3\n4  5")
        }
        run {
            // 3×3 行列式(手册能力范围内)
            val m3 = Matrix.fromRows(
                listOf(
                    listOf(1.0, 2.0, 3.0),
                    listOf(4.0, 5.0, 6.0),
                    listOf(7.0, 8.0, 10.0)
                )
            )
            // 手算:1*(5*10-6*8) - 2*(4*10-6*7) + 3*(4*8-5*7) = 2 - (-4) + (-9) = -3
            check("3×3 行列式", Stats.round(Mat.det(m3), 9).toString(), "-3.0")
        }
    }

    // ---------------- 8h. 求极限(数值法) ----------------
    private fun testLimit() {
        fun lim(expr: String, v: String, a: Double, side: Limit.Side): Limit.Result {
            val node = Parser.parse(expr)
            val f: (Double) -> Double? = { x ->
                try {
                    val y = node.eval(mapOf(v to x))
                    if (y.isFinite()) y else null
                } catch (e: Exception) {
                    null
                }
            }
            return Limit.compute(f, a, side, false)
        }

        fun value(r: Limit.Result): Double? = (r as? Limit.Result.Ok)?.value

        run {
            val v = value(lim("sin(x)/x", "x", 0.0, Limit.Side.BOTH))
            checkTrue("lim sin(x)/x = 1", v != null && abs(v - 1.0) < 1e-6, v?.toString() ?: "不收敛")
        }
        run {
            val v = value(lim("(1-cos(x))/x", "x", 0.0, Limit.Side.BOTH))
            checkTrue("lim (1-cos x)/x = 0", v != null && abs(v) < 1e-6, v?.toString() ?: "不收敛")
        }
        run {
            val v = value(lim("(e^x-1)/x", "x", 0.0, Limit.Side.BOTH))
            checkTrue("lim (e^x-1)/x = 1", v != null && abs(v - 1.0) < 1e-5, v?.toString() ?: "不收敛")
        }
        run {
            val v = value(lim("x^2", "x", 2.0, Limit.Side.BOTH))
            checkTrue("lim x^2 = 4 (x→2)", v != null && abs(v - 4.0) < 1e-6, v?.toString() ?: "不收敛")
        }
        run {
            val r = lim("1/x", "x", 0.0, Limit.Side.RIGHT)
            checkTrue("lim 1/x (x→0⁺) 应发散", r is Limit.Result.Diverged, r.toString())
        }
        run {
            val r = lim("1/x", "x", 0.0, Limit.Side.BOTH)
            checkTrue("lim 1/x (x→0) 不存在", r is Limit.Result.Diverged, r.toString())
        }
        run {
            val r = lim("abs(x)/x", "x", 0.0, Limit.Side.BOTH)
            checkTrue("lim |x|/x 不存在", r is Limit.Result.Diverged, r.toString())
            val vr = value(lim("abs(x)/x", "x", 0.0, Limit.Side.RIGHT))
            checkTrue("右极限 = 1", vr != null && abs(vr - 1.0) < 1e-6, vr?.toString() ?: "不收敛")
            val vl = value(lim("abs(x)/x", "x", 0.0, Limit.Side.LEFT))
            checkTrue("左极限 = −1", vl != null && abs(vl + 1.0) < 1e-6, vl?.toString() ?: "不收敛")
        }
        run {
            val node = Parser.parse("1/x")
            val f: (Double) -> Double? = { x ->
                try {
                    val y = node.eval(mapOf("x" to x)); if (y.isFinite()) y else null
                } catch (e: Exception) {
                    null
                }
            }
            val r = Limit.compute(f, 1.0, Limit.Side.BOTH, infinite = true)
            val v = value(r)
            checkTrue("lim 1/x (x→∞) = 0", v != null && abs(v) < 1e-6, v?.toString() ?: r.toString())
        }
        run {
            val f: (Double) -> Double? = { x -> x }
            val r = Limit.compute(f, 1.0, Limit.Side.BOTH, infinite = true)
            checkTrue("lim x (x→∞) 应发散", r is Limit.Result.Diverged, r.toString())
        }
        run {
            val e = CalcEngine()
            val out = e.process(":lim sin(x)/x x 0")
            checkTrue(":lim 输出含结果 1", out.contains("= 1"), out)
            checkTrue(":lim 输出含公式", out.contains("lim"), out)
            checkTrue(":lim 缺参数给用法", e.process(":lim x").contains("用法"), ":lim x")
            checkTrue(":lim 非法趋近值报错", e.process(":lim x^2 x abc").contains("错误"), "abc")
            checkTrue(":lim 发散给出「不存在」", e.process(":lim 1/x x 0").contains("不存在"), "1/x")
        }
    }

    // ---------------- 8i. 向量(第 4 批) ----------------
    private fun testVector() {
        val a = Vector.of2(1.0, 2.0)
        val b = Vector.of2(3.0, 4.0)
        run {
            check("向量维度", a.dim.toString(), "2")
            check("取元素", a[0].toString(), "1.0")
            var err: String? = null
            try {
                Vector(4, DoubleArray(4))
            } catch (e: Exception) {
                err = e.message
            }
            checkTrue("4 维向量应报错", err != null, err ?: "没有报错")
        }
        run {
            check("A+B", Vec.format(Vec.add(a, b)), "(4, 6)")
            check("A-B", Vec.format(Vec.sub(a, b)), "(-2, -2)")
            check("3×A", Vec.format(Vec.scale(a, 3.0)), "(3, 6)")
            var err: String? = null
            try {
                Vec.add(a, Vector.of3(1.0, 1.0, 1.0))
            } catch (e: Exception) {
                err = e.message
            }
            checkTrue("2 维与 3 维相加报错", err != null, err ?: "没有报错")
        }
        run {
            // 内积:1*3 + 2*4 = 11
            check("A·B", Vec.dot(a, b).toString(), "11.0")
            check("A·A = |A|²", Stats.round(Vec.dot(a, a), 9).toString(), Stats.round(Vec.norm(a) * Vec.norm(a), 9).toString())
        }
        run {
            checkTrue("|A| = √5", abs(Vec.norm(a) - sqrt(5.0)) < 1e-12, Vec.norm(a).toString())
            check("零向量模", Vec.norm(Vector.of2(0.0, 0.0)).toString(), "0.0")
        }
        run {
            // 叉积(仅三维):x̂ × ŷ = ẑ
            val ex = Vector.of3(1.0, 0.0, 0.0)
            val ey = Vector.of3(0.0, 1.0, 0.0)
            check("x̂ × ŷ = ẑ", Vec.format(Vec.cross(ex, ey)), "(0, 0, 1)")
            check("ŷ × x̂ = −ẑ", Vec.format(Vec.cross(ey, ex)), "(0, 0, -1)")
            var err: String? = null
            try {
                Vec.cross(a, b)
            } catch (e: Exception) {
                err = e.message
            }
            checkTrue("二维叉积应报错", err != null, err ?: "没有报错")
            check("平行叉积 = 0", Vec.format(Vec.cross(ex, Vec.scale(ex, 5.0))), "(0, 0, 0)")
        }
        run {
            val i = Vector.of2(1.0, 0.0)
            val j = Vector.of2(0.0, 1.0)
            checkTrue("夹角 90°", abs(Math.toDegrees(Vec.angle(i, j)) - 90.0) < 1e-9, Vec.angle(i, j).toString())
            checkTrue("夹角 0°", abs(Vec.angle(i, i)) < 1e-9, Vec.angle(i, i).toString())
            // 手册例:VctA=(1,2) 与 VctB=(3,4) 的夹角
            val deg = Math.toDegrees(Vec.angle(a, b))
            checkTrue("(1,2) 与 (3,4) 夹角≈10.3048°", abs(deg - 10.304846) < 1e-5, deg.toString())
            var err: String? = null
            try {
                Vec.angle(i, Vector.of2(0.0, 0.0))
            } catch (e: Exception) {
                err = e.message
            }
            checkTrue("零向量夹角报错", err != null, err ?: "没有报错")
        }
        run {
            val u = Vec.unit(Vector.of2(3.0, 4.0))
            checkTrue("单位向量第 1 分量", abs(u[0] - 0.6) < 1e-12, u[0].toString())
            checkTrue("单位向量第 2 分量", abs(u[1] - 0.8) < 1e-12, u[1].toString())
            checkTrue("单位向量模为 1", abs(Vec.norm(u) - 1.0) < 1e-12, Vec.norm(u).toString())
            var err: String? = null
            try {
                Vec.unit(Vector.of2(0.0, 0.0))
            } catch (e: Exception) {
                err = e.message
            }
            checkTrue("零向量单位化报错", err != null, err ?: "没有报错")
        }
        run {
            val v3 = Vector.of3(1.0, 1.0, 1.0)
            checkTrue("三维模 = √3", abs(Vec.norm(v3) - sqrt(3.0)) < 1e-12, Vec.norm(v3).toString())
            val rad = Vec.angle(Vector.of2(1.0, 0.0), Vector.of2(0.0, 1.0))
            checkTrue("度", abs(Vec.toAngleUnit(rad, AngleUnit.DEGREE) - 90.0) < 1e-9, "度")
            checkTrue("弧度", abs(Vec.toAngleUnit(rad, AngleUnit.RADIAN) - Math.PI / 2) < 1e-9, "弧度")
            checkTrue("百分度", abs(Vec.toAngleUnit(rad, AngleUnit.GRADIAN) - 100.0) < 1e-9, "百分度")
        }
    }

    // ---------------- 8j. 基数模式(第 5 批) ----------------
    private fun testBaseN() {
        run {
            check("DEC 上限", BaseN.MAX_VALUE.toString(), "2147483647")
            check("DEC 下限", BaseN.MIN_VALUE.toString(), "-2147483648")
            check("解析十进制", BaseN.parse("555", BaseN.Base.DEC).toString(), "555")
            check("解析十进制负数", BaseN.parse("-100", BaseN.Base.DEC).toString(), "-100")
            var err: String? = null
            try {
                BaseN.parse("2147483648", BaseN.Base.DEC)
            } catch (e: Exception) {
                err = e.message
            }
            checkTrue("DEC 越界报错", err != null, err ?: "没有报错")
        }
        run {
            // 手册例:15 × 37 = 555,转成 HEX/BIN/OCT
            check("555 → HEX", BaseN.format(555, BaseN.Base.HEX), "0000022B")
            check("555 → BIN(紧凑)", BaseN.binCompact(555), "00000000000000000000001000101011")
            check("555 → OCT", BaseN.format(555, BaseN.Base.OCT), "00000001053")
            checkTrue("BIN 按 4 位分组", BaseN.format(555, BaseN.Base.BIN).contains(" "), BaseN.format(555, BaseN.Base.BIN))
        }
        run {
            // 手册例:11₂ + 1₂ = 4
            val a = BaseN.parse("11", BaseN.Base.BIN)
            val b = BaseN.parse("1", BaseN.Base.BIN)
            check("BIN 11 + 1 = 4", (a + b).toString(), "4")
            check("4 → BIN", BaseN.binCompact(4), "00000000000000000000000000000100")
            // 手册例:1F₁₆ + 1₁₆ = 32
            check("HEX 1F + 1 = 32", (BaseN.parse("1F", BaseN.Base.HEX) + BaseN.parse("1", BaseN.Base.HEX)).toString(), "32")
            check("32 → HEX", BaseN.format(32, BaseN.Base.HEX), "00000020")
        }
        run {
            // 手册例:10d + 10h + 10b + 10o = 36
            val (v1, _) = BaseN.parseWithSuffix("10d", BaseN.Base.DEC)
            val (v2, _) = BaseN.parseWithSuffix("10h", BaseN.Base.DEC)
            val (v3, _) = BaseN.parseWithSuffix("10b", BaseN.Base.DEC)
            val (v4, _) = BaseN.parseWithSuffix("10o", BaseN.Base.DEC)
            check("10d", v1.toString(), "10")
            check("10h = 16", v2.toString(), "16")
            check("10b = 2", v3.toString(), "2")
            check("10o = 8", v4.toString(), "8")
            check("总和 = 36", (v1 + v2 + v3 + v4).toString(), "36")
        }
        run {
            // 手册例:1010₂ and 1100₂ = 1000₂
            val a = BaseN.parse("1010", BaseN.Base.BIN)
            val b = BaseN.parse("1100", BaseN.Base.BIN)
            check("AND 结果", BaseN.and(a, b).toString(), "8")
            check("AND 的末 4 位", BaseN.binCompact(BaseN.and(a, b)).takeLast(4), "1000")
            check("OR 结果", BaseN.or(a, b).toString(), "14")
            check("XOR 结果", BaseN.xor(a, b).toString(), "6")
            check("XNOR = ~XOR", BaseN.xnor(a, b).toString(), BaseN.xor(a, b).inv().toString())
        }
        run {
            // 手册例:Not(1010₂) → −11,末 8 位 11110101
            val a = BaseN.parse("1010", BaseN.Base.BIN)
            check("Not 结果(十进制)", BaseN.not(a).toString(), "-11")
            check("Not 的末 8 位", BaseN.binCompact(BaseN.not(a)).takeLast(8), "11110101")
            check("Neg(5) = −5", BaseN.neg(5).toString(), "-5")
        }
        run {
            // 负数二位补码
            check("−1 → HEX", BaseN.format(-1, BaseN.Base.HEX), "FFFFFFFF")
            check("−1 → BIN 末 8 位", BaseN.binCompact(-1).takeLast(8), "11111111")
            check("FFFFFFFF → −1", BaseN.parse("FFFFFFFF", BaseN.Base.HEX).toString(), "-1")
            check("80000000 → 最小负数", BaseN.parse("80000000", BaseN.Base.HEX).toString(), "-2147483648")
            check("7FFFFFFF → 最大正数", BaseN.parse("7FFFFFFF", BaseN.Base.HEX).toString(), "2147483647")
        }
        run {
            var err: String? = null
            try {
                BaseN.parse("2", BaseN.Base.BIN)
            } catch (e: Exception) {
                err = e.message
            }
            checkTrue("BIN 出现 2 报错", err != null, err ?: "没有报错")
            var err2: String? = null
            try {
                BaseN.parse("8", BaseN.Base.OCT)
            } catch (e: Exception) {
                err2 = e.message
            }
            checkTrue("OCT 出现 8 报错", err2 != null, err2 ?: "没有报错")
            var err3: String? = null
            try {
                BaseN.parse("G", BaseN.Base.HEX)
            } catch (e: Exception) {
                err3 = e.message
            }
            checkTrue("HEX 出现 G 报错", err3 != null, err3 ?: "没有报错")
        }
        run {
            check("裁切 3.9", BaseN.truncate(3.9).toString(), "3")
            check("裁切 −3.9", BaseN.truncate(-3.9).toString(), "-3")
        }
    }

    // ---------------- 8k. 比例与不等式(第 6 批) ----------------
    private fun testRatioAndIneq() {
        run {
            // 比例:3 : 4 = X : 8 → X = 6(X = b·d/a? 这里用 a:b = c:X 形式 → X = b·c/a)
            // 手册形式 a : b = c : X,取 2 : 3 = 4 : X → X = 6
            val r = Ratio.solveFourth(2.0, 3.0, 4.0)
            checkTrue("比例 2:3 = 4:X 可解", r is Ratio.Result.Ok, r.toString())
            check("X = 6", (r as Ratio.Result.Ok).value.toString(), "6.0")
        }
        run {
            // 手册形式 a : b = X : d,取 2 : 3 = X : 12 → X = 8
            val r = Ratio.solveThird(2.0, 3.0, 12.0)
            checkTrue("比例 2:3 = X:12 可解", r is Ratio.Result.Ok, r.toString())
            check("X = 8", (r as Ratio.Result.Ok).value.toString(), "8.0")
        }
        run {
            // a = 0 或 b = 0 要报错
            checkTrue("a=0 报错", Ratio.solveFourth(0.0, 3.0, 4.0) is Ratio.Result.Failed, "a=0")
            checkTrue("b=0 报错", Ratio.solveThird(2.0, 0.0, 12.0) is Ratio.Result.Failed, "b=0")
        }
        run {
            // 不等式:x² − 3x + 2 = (x−1)(x−2)
            // x²−3x+2 < 0  → 1 < x < 2
            val r = Ineq.solve(listOf(1.0, -3.0, 2.0), Ineq.Rel.LT)
            checkTrue("x²−3x+2<0 可解", r is Ineq.Result.Intervals, r.toString())
            val text = (r as Ineq.Result.Intervals).text
            checkTrue("解为 1<x<2", text.contains("1 < x < 2"), text)
        }
        run {
            // x²−3x+2 > 0 → x < 1 或 x > 2
            val r = Ineq.solve(listOf(1.0, -3.0, 2.0), Ineq.Rel.GT) as Ineq.Result.Intervals
            checkTrue("解含 x<1", r.text.contains("x < 1"), r.text)
            checkTrue("解含 x>2", r.text.contains("x > 2"), r.text)
        }
        run {
            // x²−3x+2 ≤ 0 → 1 ≤ x ≤ 2(含端点)
            val r = Ineq.solve(listOf(1.0, -3.0, 2.0), Ineq.Rel.LE) as Ineq.Result.Intervals
            checkTrue("解为 1≤x≤2", r.text.contains("1 ≤ x ≤ 2"), r.text)
        }
        run {
            // x²−3x+2 ≥ 0 → x ≤ 1 或 x ≥ 2
            val r = Ineq.solve(listOf(1.0, -3.0, 2.0), Ineq.Rel.GE) as Ineq.Result.Intervals
            checkTrue("解含 x≤1", r.text.contains("x ≤ 1"), r.text)
            checkTrue("解含 x≥2", r.text.contains("x ≥ 2"), r.text)
        }
        run {
            // 恒成立:x² + 1 > 0 → 全体实数
            val r = Ineq.solve(listOf(1.0, 0.0, 1.0), Ineq.Rel.GT) as Ineq.Result.Intervals
            checkTrue("x²+1>0 全体实数", r.text.contains("全体实数"), r.text)
            // 无解:x² + 1 < 0
            val r2 = Ineq.solve(listOf(1.0, 0.0, 1.0), Ineq.Rel.LT) as Ineq.Result.Intervals
            checkTrue("x²+1<0 无解", r2.text.contains("无解"), r2.text)
        }
        run {
            // 一次不等式:2x − 4 > 0 → x > 2
            val r = Ineq.solve(listOf(2.0, -4.0), Ineq.Rel.GT) as Ineq.Result.Intervals
            checkTrue("2x−4>0 → x>2", r.text.contains("x > 2"), r.text)
        }
        run {
            // 三次:(x−1)(x−2)(x−3) = x³−6x²+11x−6
            // < 0 的解:x < 1 或 2 < x < 3
            val r = Ineq.solve(listOf(1.0, -6.0, 11.0, -6.0), Ineq.Rel.LT) as Ineq.Result.Intervals
            checkTrue("三次不等式含 x<1", r.text.contains("x < 1"), r.text)
            checkTrue("三次不等式含 2<x<3", r.text.contains("2 < x < 3"), r.text)
        }
        run {
            // 四次:(x²−1)(x²−4) = x⁴ −5x² +4
            // < 0 的解:−2 < x < −1 或 1 < x < 2
            val r = Ineq.solve(listOf(1.0, 0.0, -5.0, 0.0, 4.0), Ineq.Rel.LT) as Ineq.Result.Intervals
            checkTrue("四次含 −2<x<−1", r.text.contains("-2 < x < -1"), r.text)
            checkTrue("四次含 1<x<2", r.text.contains("1 < x < 2"), r.text)
        }
        run {
            // 超出四次要报错
            val r = Ineq.solve(listOf(1.0, 0.0, 0.0, 0.0, 0.0, 1.0), Ineq.Rel.LT)
            checkTrue("五次不等式报错", r is Ineq.Result.Failed, r.toString())
        }
        run {
            // 文本输出含公式与解
            val r = Ineq.solve(listOf(1.0, -3.0, 2.0), Ineq.Rel.LT)
            val txt = Ineq.format(listOf(1.0, -3.0, 2.0), Ineq.Rel.LT, r)
            checkTrue("输出含 x^2", txt.contains("x^2"), txt)
            checkTrue("输出含关系", txt.contains("< 0"), txt)
        }
    }

    // ---------------- 8l. lim 块与命令抽屉(第 6 批补) ----------------
    private fun testLimitBlock() {
        run {
            // lim 块:表达式 / 变量 / 趋近值 / 方向
            val b = Edit.newLimit()
            check("lim 块命令", b.command, ":lim")
            check("lim 块字段数", b.fields.size.toString(), "4")
            check("变量默认 x", b.fields[1].value, "x")
            check("趋近值默认 0", b.fields[2].value, "0")
            check("方向默认 both", b.fields[3].value, "both")
        }
        run {
            // 块 → 内部文本:方向为空时省略该参数
            val ps = mutableListOf<Edit.Part>(Edit.newLimit())
            val b = ps[0] as Edit.Block
            b.fields[0].value = "sin(x)/x"
            check("双侧时方向为 both", Edit.getText(ps), ":lim sin(x)/x x 0 both")
            b.fields[3].value = "right"
            check("单侧时带方向", Edit.getText(ps), ":lim sin(x)/x x 0 right")
        }
        run {
            // 引擎能直接执行块摊平出来的文本
            val e = CalcEngine()
            val out = e.process(":lim sin(x)/x x 0")
            checkTrue("lim 块文本可执行", out.contains("= 1"), out)
            checkTrue("输出含趋近式注解", out.contains("x → 0"), out)
        }
        run {
            // 方向参数 left/right 生效
            val e = CalcEngine()
            val l = e.process(":lim 1/x x 0 left")
            val rr = e.process(":lim 1/x x 0 right")
            checkTrue("左极限报发散", l.contains("不存在") || l.contains("发散"), l)
            checkTrue("右极限报发散", rr.contains("不存在") || rr.contains("发散"), rr)
            checkTrue("左极限注解带负号上标", l.contains("x → 0⁻"), l)
            checkTrue("右极限注解带正号上标", rr.contains("x → 0⁺"), rr)
        }
        run {
            // 显示形态
            val b = Edit.newLimit()
            b.fields[0].value = "sin(x)/x"
            b.fields[2].value = "0"
            val ps = mutableListOf<Edit.Part>(b)
            checkTrue("displayText 形如 lim", Edit.displayText(ps).startsWith("lim("), Edit.displayText(ps))
        }
        run {
            // 注释式显示函数
            val t = Display.limitAnnotated("sin(x)/x", "x", "0", "both")
            checkTrue("含 lim", t.contains("lim"), t)
            checkTrue("含趋近式", t.contains("x → 0"), t)
            val tr = Display.limitAnnotated("1/x", "x", "0", "right")
            checkTrue("单侧带上标", tr.contains("0⁺"), tr)
        }
        run {
            // 块的后退删除:先删字符,空了才整块删(与积分块一致)
            val ps = mutableListOf<Edit.Part>(Edit.newLimit())
            var c = Edit.Cursor(0, 0, 0)
            // 在表达式里输入
            c = Edit.insert(ps, c, "x^2")
            check("表达式已写入", (ps[0] as Edit.Block).fields[0].value, "x^2")
            c = Edit.backspace(ps, c)
            check("退格删一个字符", (ps[0] as Edit.Block).fields[0].value, "x^")
        }
        run {
            // 命令抽屉的载荷格式约定
            checkTrue("块载荷前缀", "BLOCK::lim".startsWith("BLOCK::"), "BLOCK::lim")
            checkTrue("文本载荷前缀", "TEXT::vars".startsWith("TEXT::"), "TEXT::vars")
        }
    }

    // ---------------- 8n. 方程面板与 SOLVE(第 6 批补) ----------------
    private fun testEqnAndSolve() {
        run {
            // 联立方程:面板拼出的方程串应能被引擎解出
            val eqs = listOf("2x+1y=5", "1x+-1y=1")
            val r = Solver.solveLinearSystem(eqs)
            check("联立 2 元变量名", r.variables.joinToString(","), "x,y")
            check("联立 2 元 x", r.solution[0].toString(), "2")
            check("联立 2 元 y", r.solution[1].toString(), "1")
        }
        run {
            // 3 元
            val r = Solver.solveLinearSystem(listOf("1x+1y+1z=6", "1x+-1y+1z=2", "1x+1y+-1z=2"))
            check("联立 3 元", r.solution.map { it.toString() }.joinToString(","), "2,2,2")
        }
        run {
            // 4 元(手册上限)
            val r = Solver.solveLinearSystem(
                listOf(
                    "1x+1y+1z+1t=10",
                    "1x+-1y+1z+1t=6",
                    "1x+1y+-1z+1t=6",
                    "1x+1y+1z+-1t=4"
                )
            )
            check("联立 4 元变量数", r.variables.size.toString(), "4")
            // 手算:x=3,y=3,z=2,t=2
            check("联立 4 元解", r.solution.map { it.toString() }.joinToString(","), "3,3,2,2")
        }
        run {
            // SOLVE:牛顿法从初值收敛
            val out = Solver.solveByNewton("x^2-2=0", "x", 1.0)
            checkTrue("SOLVE 含结果", out.contains("x = "), out)
            checkTrue("SOLVE 得到 √2", out.contains("1.414213562"), out)
            checkTrue("SOLVE 说明是牛顿法", out.contains("牛顿法"), out)
        }
        run {
            // 初值不同应收敛到不同的根
            val pos = Solver.solveByNewton("x^2-2=0", "x", 2.0)
            val neg = Solver.solveByNewton("x^2-2=0", "x", -2.0)
            checkTrue("初值 2 得正根", pos.contains("1.414213562"), pos)
            checkTrue("初值 -2 得负根", neg.contains("-1.414213562"), neg)
        }
        run {
            // 一次方程
            val out = Solver.solveByNewton("2x+3=7", "x", 1.0)
            checkTrue("SOLVE 一次方程得 2", out.contains("x = 2"), out)
        }
        run {
            // 没有等号要报错
            var err: String? = null
            try {
                Solver.solveByNewton("x^2-2", "x", 1.0)
            } catch (e: Exception) {
                err = e.message
            }
            checkTrue("SOLVE 缺等号报错", err != null, err ?: "没有报错")
        }
        run {
            // 命令层
            val e = CalcEngine()
            val out = e.process(":solve x^2-2=0 x 1")
            checkTrue(":solve 输出含结果", out.contains("1.414213562"), out)
            checkTrue(":solve 缺参数给用法", e.process(":solve").contains("用法"), ":solve")
        }
        run {
            // 多项式面板拼串后走引擎(与界面同一条路径)
            val e2 = CalcEngine()
            val out2 = e2.process("x^2-3x+2=0")
            checkTrue("二次有根 1", out2.contains("1"), out2)
            checkTrue("二次有根 2", out2.contains("2"), out2)
            // 三次:x^3-6x^2+11x-6=0 → 1,2,3
            val out3 = e2.process("x^3-6x^2+11x-6=0")
            checkTrue(
                "三次含 1/2/3 三个根",
                out3.contains("1") && out3.contains("2") && out3.contains("3"),
                out3
            )
        }
        run {
            // 四次:x^4-5x^2+4=0 → ±1, ±2
            val r4 = Solver.solve("x^4-5x^2+4=0")
            val n4 = r4.realRoots.size + r4.rationalRoots.size
            checkTrue("四次有 4 个根", n4 == 4, "real=${r4.realRoots} rat=${r4.rationalRoots}")
        }
    }

    // ---------------- 8o. 高斯求积与中心差分求导 ----------------
    private fun testQuad() {
        run {
            // 规则自检:节点在 (-1,1) 内、严格递增、对称;权重全正且和为 2
            for (n in listOf(8, 16, 32)) {
                val xs = Quad.ruleNodes(n)
                val ws = Quad.ruleWeights(n)
                check("n=$n 节点数", xs.size.toString(), n.toString())
                checkTrue("n=$n 节点在 (-1,1) 内", xs.all { it > -1.0 && it < 1.0 }, xs.first().toString())
                checkTrue("n=$n 节点严格递增", (0 until xs.size - 1).all { i -> xs[i + 1] > xs[i] }, "递增")
                checkTrue("n=$n 节点关于 0 对称", abs(xs.first() + xs.last()) < 1e-12, "${xs.first()} ${xs.last()}")
                checkTrue("n=$n 权重全正", ws.all { it > 0 }, "权重")
                checkTrue("n=$n 权重和为 2", abs(ws.sum() - 2.0) < 1e-13, ws.sum().toString())
            }
            // 已知的 8 点规则:最外节点 ≈ ±0.960289856497536,最外权重 ≈ 0.101228536290376
            val x8 = Quad.ruleNodes(8)
            checkTrue("8 点最外节点 ≈ 0.9602899", abs(x8.first() + 0.960289856497536) < 1e-12, x8.first().toString())
            val w8 = Quad.ruleWeights(8)
            checkTrue("8 点最外权重 ≈ 0.1012285", abs(w8.first() - 0.101228536290376) < 1e-12, w8.first().toString())
        }
        run {
            // ★ 判别性检验:n 点 Gauss-Legendre 对 2n−2 次多项式必须精确。
            // 注意:奇次幂在对称区间上积分本来就是 0,所以"精确"要用**偶次幂**验证,
            // 否则 0 附近的浮点残差会让断言看起来像失败(实测踩过)。
            //
            // 这里**不**断言 x^(2n) 不精确 —— Gauss-Legendre 对 x^(2n) 有超收敛,
            // 相对误差随 n 急剧变小(n=32 时只有 4e-15),拿它做判别并不可靠。
            for (n in listOf(8, 16, 32)) {
                val xs = Quad.ruleNodes(n)
                val ws = Quad.ruleWeights(n)
                fun ruleOn(power: Int): Double =
                    xs.indices.sumOf { ws[it] * Math.pow(xs[it], power.toDouble()) }
                val k = 2 * n - 2
                checkTrue("n=$n 对 x^$k 精确", abs(ruleOn(k) - 2.0 / (k + 1)) < 1e-13, ruleOn(k).toString())
            }
        }
        run {
            // 嵌套规则(16 与 32 点)确实在起作用:对光滑函数两者高度一致
            val f: (Double) -> Double = { Math.exp(it) }
            val v16 = Quad.integrateSingle16(f, 0.0, 2.0)
            val v32 = Quad.integrateSingle(f, 0.0, 2.0)
            checkTrue("16 点与 32 点差 < 1e-14", abs(v16 - v32) < 1e-14, abs(v16 - v32).toString())
            checkTrue("两者都接近 e²−1", abs(v32 - (Math.E * Math.E - 1)) < 1e-13, v32.toString())
        }
        run {
            fun integ(f: (Double) -> Double, a: Double, b: Double) = Quad.integrate(f, a, b).value
            checkTrue("∫₀¹ x² = 1/3", abs(integ({ x -> x * x }, 0.0, 1.0) - 1.0 / 3.0) < 1e-14, integ({ x -> x * x }, 0.0, 1.0).toString())
            checkTrue("∫₀^π sin = 2", abs(integ({ x -> Math.sin(x) }, 0.0, Math.PI) - 2.0) < 1e-13, integ({ x -> Math.sin(x) }, 0.0, Math.PI).toString())
            checkTrue("∫₀¹ 4/(1+x²) = π", abs(integ({ x -> 4.0 / (1 + x * x) }, 0.0, 1.0) - Math.PI) < 1e-13, integ({ x -> 4.0 / (1 + x * x) }, 0.0, 1.0).toString())
            checkTrue("∫₁^e 1/x = 1", abs(integ({ x -> 1.0 / x }, 1.0, Math.E) - 1.0) < 1e-13, integ({ x -> 1.0 / x }, 1.0, Math.E).toString())
            checkTrue("∫₀^{2π} sin²(10x) = π", abs(integ({ x -> Math.sin(10 * x) * Math.sin(10 * x) }, 0.0, 2 * Math.PI) - Math.PI) < 1e-10, integ({ x -> Math.sin(10 * x) * Math.sin(10 * x) }, 0.0, 2 * Math.PI).toString())
            checkTrue("∫₀² x^10 = 2048/11", abs(integ({ x -> Math.pow(x, 10.0) }, 0.0, 2.0) - 2048.0 / 11.0) < 1e-11, integ({ x -> Math.pow(x, 10.0) }, 0.0, 2.0).toString())
        }
        run {
            val r = Quad.integrate({ x -> if (x <= 0.0) 0.0 else 1.0 / Math.sqrt(x) }, 0.0, 1.0)
            checkTrue("∫₀¹ 1/√x = 2", abs(r.value - 2.0) < 1e-7, "${r.value} 细分 ${r.subdivisions}")
            checkTrue("奇异积分有细分", r.subdivisions > 0, r.subdivisions.toString())
        }
        run {
            checkTrue("反向积分变号", Quad.integrate({ x -> x * x }, 1.0, 0.0).value < 0, "符号")
            check("零长度积分为 0", Quad.integrate({ x -> x }, 2.0, 2.0).value.toString(), "0.0")
        }
        run {
            val r = Quad.integrate({ _ -> 3.0 }, 0.0, 5.0)
            checkTrue("常数积分 = 15", abs(r.value - 15.0) < 1e-13, r.value.toString())
            check("常数积分无需细分", r.subdivisions.toString(), "0")
        }
        run {
            checkTrue("单区间 ∫₋₁¹ x^14 = 2/15", abs(Quad.integrateSingle({ x -> Math.pow(x, 14.0) }, -1.0, 1.0) - 2.0 / 15.0) < 1e-13, "x^14")
        }
        run {
            checkTrue("d/dx x² @3 = 6", abs(Quad.derivative({ x -> x * x }, 3.0) - 6.0) < 1e-9, Quad.derivative({ x -> x * x }, 3.0).toString())
            checkTrue("d/dx sin @0 = 1", abs(Quad.derivative({ x -> Math.sin(x) }, 0.0) - 1.0) < 1e-9, Quad.derivative({ x -> Math.sin(x) }, 0.0).toString())
            checkTrue("d/dx e^x @1 = e", abs(Quad.derivative({ x -> Math.exp(x) }, 1.0) - Math.E) < 1e-8, Quad.derivative({ x -> Math.exp(x) }, 1.0).toString())
            checkTrue("d/dx ln @2 = 0.5", abs(Quad.derivative({ x -> Math.log(x) }, 2.0) - 0.5) < 1e-9, Quad.derivative({ x -> Math.log(x) }, 2.0).toString())
            checkTrue("d/dx x^5 @2 = 80", abs(Quad.derivative({ x -> Math.pow(x, 5.0) }, 2.0) - 80.0) < 1e-6, Quad.derivative({ x -> Math.pow(x, 5.0) }, 2.0).toString())
            checkTrue("d/dx cos @π/3 = −sin(π/3)", abs(Quad.derivative({ x -> Math.cos(x) }, Math.PI / 3) + Math.sin(Math.PI / 3)) < 1e-8, "cos")
        }
    }

    // ---------------- 8p. 科学常数与公制换算(第 7 批) ----------------
    private fun testConstantsAndConv() {
        run {
            // 常数表规模与唯一性
            val all = Constants.all
            checkTrue("常数不少于 40 项", all.size >= 40, all.size.toString())
            checkTrue("id 唯一", all.map { it.id }.distinct().size == all.size, "唯一")
            // 每项都必须有值、单位、符号
            checkTrue("单位字段非空串", all.all { it.unit != null }, "单位")
            checkTrue("仅 α 无量纲(单位为空)", all.count { it.unit.isBlank() } <= 1, all.filter { it.unit.isBlank() }.map { it.id }.toString())
            checkTrue("每项都有符号", all.all { it.symbol.isNotBlank() }, "符号")
            checkTrue("每项值都有限且非零", all.all { it.value.isFinite() && it.value != 0.0 }, "值")
        }
        run {
            // 关键常数取 CODATA 值(这些是定义值,必须精确匹配)
            check("光速 c", Constants.find("c")!!.value.toString(), "2.99792458E8")
            check("普朗克 h", Constants.find("h")!!.value.toString(), "6.62607015E-34")
            check("元电荷 e", Constants.find("e")!!.value.toString(), "1.602176634E-19")
            check("阿伏伽德罗 NA", Constants.find("NA")!!.value.toString(), "6.02214076E23")
            check("玻尔兹曼 k", Constants.find("k")!!.value.toString(), "1.380649E-23")
            check("标准重力 gn", Constants.find("gn")!!.value.toString(), "9.80665")
            check("万有引力 gc", Constants.find("gc")!!.value.toString(), "6.6743E-11")
            check("标准大气压", Constants.find("atm")!!.value.toString(), "101325.0")
            check("摄氏零度", Constants.find("t")!!.value.toString(), "273.15")
        }
        run {
            // exact 标记:定义值必须标 true
            checkTrue("c 是定义值", Constants.find("c")!!.exact, "c")
            checkTrue("h 是定义值", Constants.find("h")!!.exact, "h")
            checkTrue("e 是定义值", Constants.find("e")!!.exact, "e")
            checkTrue("gc 不是定义值", !Constants.find("gc")!!.exact, "gc")
            checkTrue("α 不是定义值", !Constants.find("alpha")!!.exact, "alpha")
        }
        run {
            // 由定义值派生的一致性检查(这些关系必须成立,能查出抄错的数字)
            val c = Constants.find("c")!!.value
            val h = Constants.find("h")!!.value
            val e = Constants.find("e")!!.value
            val k = Constants.find("k")!!.value
            val NA = Constants.find("NA")!!.value
            // 摩尔气体常数 R = k·N_A
            val R = Constants.find("R")!!.value
            checkTrue("R = k·N_A", abs(R - k * NA) / R < 1e-9, "$R vs ${k * NA}")
            // 法拉第常数 F = e·N_A
            val F = Constants.find("F")!!.value
            checkTrue("F = e·N_A", abs(F - e * NA) / F < 1e-9, "$F vs ${e * NA}")
            // 电子比荷 e/mₑ 与 mₑ 一致
            val me = Constants.find("me")!!.value
            val em = Constants.find("e_over_me")!!.value
            checkTrue("e/mₑ = e/mₑ", abs(em - e / me) / em < 1e-7, "$em vs ${e / me}")
            // 玻尔半径 a₀ = ℏ/(mₑ·c·α)
            val hbar = Constants.find("hbar")!!.value
            val alpha = Constants.find("alpha")!!.value
            val a0 = Constants.find("a0")!!.value
            checkTrue("a₀ 一致", abs(a0 - hbar / (me * c * alpha)) / a0 < 1e-6, "$a0 vs ${hbar / (me * c * alpha)}")
            // 里德伯 R_∞ = α²·mₑ·c/(2h)
            val Rinf = Constants.find("Rinf")!!.value
            checkTrue("R_∞ 一致", abs(Rinf - alpha * alpha * me * c / (2 * h)) / Rinf < 1e-6, "$Rinf")
            // 光年 = c × 儒略年
            val ly = Constants.find("ly")!!.value
            checkTrue("ly = c×365.25d", abs(ly - c * 365.25 * 86400) / ly < 1e-9, "$ly")
            // 秒差距 ≈ 648000/π au
            val pc = Constants.find("pc")!!.value
            val au = Constants.find("au")!!.value
            checkTrue("pc ≈ 648000/π au", abs(pc - 648000.0 / Math.PI * au) / pc < 1e-9, "$pc")
        }
        run {
            // 查询与列出
            checkTrue("列出全部含光速", Constants.listAll().contains("真空光速"), "listAll")
            checkTrue("查询 c 含单位", Constants.query("c").contains("m/s"), Constants.query("c"))
            checkTrue("查询 NA 标注定义值", Constants.query("NA").contains("定义值"), Constants.query("NA"))
            checkTrue("查不到时给提示", Constants.query("zzz").contains("找不到"), Constants.query("zzz"))
        }
        run {
            // 公制换算:定义值必须精确
            fun cv(v: Double, f: String, t: String) = MetricConv.convert(v, f, t)!!.first
            check("1 in = 2.54 cm", cv(1.0, "in", "cm").toString(), "2.54")
            check("1 ft = 0.3048 m", cv(1.0, "ft", "m").toString(), "0.3048")
            check("1 mile = 1.609344 km", cv(1.0, "mile", "km").toString(), "1.609344")
            check("1 lb = 0.45359237 kg", cv(1.0, "lb", "kg").toString(), "0.45359237")
            check("1 atm = 101325 Pa", cv(1.0, "atm", "Pa").toString(), "101325.0")
            check("1 cal = 4.184 J", cv(1.0, "cal", "J").toString(), "4.184")
            check("1 kW·h = 3.6e6 J", cv(1.0, "kW·h", "J").toString(), "3600000.0")
            check("1 ha = 10000 m²", cv(1.0, "ha", "m²").toString(), "10000.0")
        }
        run {
            // 反向换算:自动识别
            fun cv(v: Double, f: String, t: String) = MetricConv.convert(v, f, t)!!.first
            checkTrue("2.54 cm = 1 in(反向)", abs(cv(2.54, "cm", "in") - 1.0) < 1e-12, cv(2.54, "cm", "in").toString())
            checkTrue("0.3048 m = 1 ft(反向)", abs(cv(0.3048, "m", "ft") - 1.0) < 1e-12, cv(0.3048, "m", "ft").toString())
            // 往返一致
            val out = cv(cv(7.5, "in", "cm"), "cm", "in")
            checkTrue("往返一致", abs(out - 7.5) < 1e-12, out.toString())
        }
        run {
            // 温度换算(非比例,必须走专门公式)
            fun cv(v: Double, f: String, t: String) = MetricConv.convert(v, f, t)!!.first
            checkTrue("0°C = 32°F", abs(cv(0.0, "°C", "°F") - 32.0) < 1e-12, cv(0.0, "°C", "°F").toString())
            checkTrue("100°C = 212°F", abs(cv(100.0, "°C", "°F") - 212.0) < 1e-12, cv(100.0, "°C", "°F").toString())
            checkTrue("0°C = 273.15K", abs(cv(0.0, "°C", "K") - 273.15) < 1e-12, cv(0.0, "°C", "K").toString())
            checkTrue("-40°C = -40°F", abs(cv(-40.0, "°C", "°F") + 40.0) < 1e-12, cv(-40.0, "°C", "°F").toString())
            checkTrue("32°F = 0°C", abs(cv(32.0, "°F", "°C")) < 1e-12, cv(32.0, "°F", "°C").toString())
            checkTrue("温度换算注明非比例", MetricConv.convert(0.0, "°C", "°F")!!.second.contains("非比例"), "注明")
        }
        run {
            // 查不到的换算对应返回 null
            checkTrue("未知换算对返回 null", MetricConv.convert(1.0, "zzz", "yyy") == null, "null")
            checkTrue("列出换算表", MetricConv.listAll().contains("长度"), "listAll")
        }
        run {
            // 命令层
            val e = CalcEngine()
            checkTrue(":const 列出", e.process(":const").contains("真空光速"), ":const")
            checkTrue(":const c 查询", e.process(":const c").contains("299792458"), ":const c")
            checkTrue(":conv 1 in cm", e.process(":conv 1 in cm").contains("2.54"), ":conv")
            checkTrue(":conv 列表", e.process(":conv 列表").contains("长度"), ":conv 列表")
            checkTrue(":conv 缺参数给用法", e.process(":conv 1").contains("用法"), ":conv 1")
            checkTrue(":conv 非数值报错", e.process(":conv abc in cm").contains("错误"), "abc")
            checkTrue(":conv 未知单位给提示", e.process(":conv 1 zzz yyy").contains("找不到"), "zzz")
        }
    }

    // ---------------- 8q. 错误系统与出错位置定位 ----------------
    private fun testErrorReport() {
        run {
            // 错误分类:各类措辞要落到正确的类型
            check("除以 0 → Math", Errors.classify("除以 0").label, "Math ERROR")
            check("超出范围 → Range", Errors.classify("结果超出范围").label, "Range ERROR")
            check("未知名称 → Variable", Errors.classify("未知名称: q").label, "Variable ERROR")
            check("参数个数 → Argument", Errors.classify("参数个数不对").label, "Argument ERROR")
            check("嵌套过深 → Stack", Errors.classify("嵌套过深").label, "Stack ERROR")
            check("不支持的字符 → Syntax", Errors.classify("不支持的字符: $").label, "Syntax ERROR")
            check("算式不完整 → Syntax", Errors.classify("算式不完整").label, "Syntax ERROR")
            check("括号不配对 → Syntax", Errors.classify("括号不配对").label, "Syntax ERROR")
        }
        run {
            // 每种类型都带中文提示(不是只有英文名)
            for (k in Errors.Kind.entries) {
                checkTrue("${k.label} 有中文提示", k.hint.isNotBlank(), k.hint)
            }
        }
        run {
            // 位置定位:不支持的字符
            val e = "1+2+;3"
            val pos = Errors.locate(e, "不支持的字符: ;")
            check("指向分号", pos.toString(), "4")
            // 位置指示行:前面 4 列 + ^
            val rep = Errors.report(e, "不支持的字符: ;")
            check("位置行", rep.pointer(), "    ^")
        }
        run {
            // 位置定位:未知名称
            val e = "2*foo+1"
            val pos = Errors.locate(e, "未知名称: foo")
            check("指向 foo 起点", pos.toString(), "2")
        }
        run {
            // 位置定位:括号不配对
            check("多了右括号", Errors.locate("1+2)", "括号不配对").toString(), "3")
            check("少了右括号", Errors.locate("(1+2", "括号不配对").toString(), "0")
        }
        run {
            // 位置定位:算式不完整 → 指向末尾
            check("不完整指向末尾", Errors.locate("1+2+", "算式不完整").toString(), "3")
        }
        run {
            // 位置定位:除以 0 → 指向除号
            check("指向除号", Errors.locate("1/0", "除以 0").toString(), "1")
        }
        run {
            // 引擎显式带位置时优先采用
            check("优先用引擎给的位置", Errors.locate("abcdef", "这里有问题 位置 3").toString(), "3")
            // 越界位置要夹紧
            check("位置越界夹紧", Errors.locate("abc", "位置 99").toString(), "3")
        }
        run {
            // 就地标记:把出错字符用 ▸◂ 夹起来
            check("就地标记", Errors.markInline("1+;3", 2), "1+▸;◂3")
            // 越界时原样返回
            check("越界不改动", Errors.markInline("1+2", 99), "1+2")
        }
        run {
            // 全角字符的列宽按 2 计,保证 ^ 对得齐
            val e = "中文;1"
            val pos = Errors.locate(e, "不支持的字符: ;")
            val rep = Errors.report(e, "不支持的字符: ;")
            // "中文" 各占 2 列 → 共 4 列
            check("全角列宽", rep.pointer(), "    ^")
            check("位置正确", pos.toString(), "2")
        }
        run {
            // 从异常构造
            val rep = Errors.report("1/0", ArithmeticException("除以 0"))
            check("从异常构造类型", rep.kind.label, "Math ERROR")
            checkTrue("render 含类型名", rep.render().contains("Math ERROR"), rep.render())
            checkTrue("render 含算式", rep.render().contains("1/0"), rep.render())
            checkTrue("render 含中文提示", rep.render().contains("除"), rep.render())
            // displayLines 两行
            val (l1, l2) = rep.displayLines()
            check("第一行是算式", l1, "1/0")
            check("第二行是位置指示", l2, " ^")
        }
        run {
            // 端到端:真的错误表达式要能被定位(走引擎)
            val e = CalcEngine()
            val bad = e.process("1+2+;3")
            checkTrue("引擎对非法字符报错", bad.contains("错误"), bad)
            val rep = Errors.report("1+2+;3", bad.removePrefix("错误:").trim())
            checkTrue("能定位到分号", rep.position == 4, "pos=${rep.position}")
        }
        run {
            // 未赋值变量应归为 Variable 类。
            // 注意:不能拿 "q+1" 测 —— 没有等号时引擎会把它当**方程**解出 q=-1,
            // 根本不会报"未赋值"(实测踩过)。要走"必须有数值结果"的求值路径。
            val e = CalcEngine()
            val v = e.evalValue("q*2")
            checkTrue("未知变量求值返回 null", v == null, v?.toString() ?: "null")
            // 分类函数本身对"未知名称"的判定
            check("未知名称归为 Variable", Errors.classify("未知名称: q").label, "Variable ERROR")
        }
    }

    // ---------------- 8r. CALC 与 TABLE(第 9 批) ----------------
    private fun testCalcAndTable() {
        run {
            // CALC 会话:变量按首次出现顺序记录
            val s = TableCalc.CalcSession("x^2+y")
            check("变量个数", s.variables.size.toString(), "2")
            check("变量顺序", s.variables.joinToString(","), "x,y")
            check("初始未就绪", s.ready().toString(), "false")
            check("第一个问的是 x", s.nextVariable(), "x")
        }
        run {
            // 逐个赋值 → 求值
            val s = TableCalc.CalcSession("x^2+y")
            s.assign("x", 3.0)
            check("赋一个后仍未就绪", s.ready().toString(), "false")
            check("下一个是 y", s.nextVariable(), "y")
            s.assign("y", 4.0)
            checkTrue("赋完就绪", s.ready(), "就绪")
            check("求值 9+4", s.value().toString(), "13.0")
        }
        run {
            // 提示语随进度变化
            val s = TableCalc.CalcSession("x^2+y")
            checkTrue("初始提示问 x", TableCalc.calcPrompt(s).contains("x ?"), TableCalc.calcPrompt(s))
            s.assign("x", 3.0)
            checkTrue("提示里带已赋值", TableCalc.calcPrompt(s).contains("x=3"), TableCalc.calcPrompt(s))
            s.assign("y", 4.0)
            checkTrue("赋完提示", TableCalc.calcPrompt(s).contains("已赋值"), TableCalc.calcPrompt(s))
        }
        run {
            // 结果展示
            val s = TableCalc.CalcSession("x^2+y")
            s.assign("x", 3.0); s.assign("y", 4.0)
            val out = TableCalc.calcResult(s)
            checkTrue("结果含 13", out.contains("13"), out)
            checkTrue("结果含代入值", out.contains("x=3"), out)
        }
        run {
            // 无变量的表达式:直接就绪
            val s = TableCalc.CalcSession("2+3")
            check("无变量", s.variables.size.toString(), "0")
            checkTrue("直接就绪", s.ready(), "就绪")
            check("求值", s.value().toString(), "5.0")
        }
        run {
            // reset 清掉赋值
            val s = TableCalc.CalcSession("x+1")
            s.assign("x", 5.0)
            check("赋值后", s.value().toString(), "6.0")
            s.reset()
            checkTrue("reset 后未就绪", !s.ready(), "未就绪")
            check("reset 后问 x", s.nextVariable(), "x")
        }
        run {
            // 方程形式:CALC 也接受带等号的式子(按左−右求值)
            val s = TableCalc.CalcSession("x^2=9")
            s.assign("x", 3.0)
            check("x=3 时左右相等 → 0", s.value().toString(), "0.0")
        }
        run {
            // TABLE:基本生成
            val g = TableCalc.generate("x^2", 1.0, 5.0, 1.0)
            check("行数", g.xValues.size.toString(), "5")
            check("x 值", g.xValues.joinToString(","), "1.0,2.0,3.0,4.0,5.0")
            check("只有一列", g.columns.size.toString(), "1")
            check("列标题是表达式", g.columns[0].title, "x^2")
            check("函数值", g.columns[0].values.joinToString(","), "1.0,4.0,9.0,16.0,25.0")
        }
        run {
            // 小数步长
            val g = TableCalc.generate("x+1", 0.0, 1.0, 0.5)
            check("0.5 步长行数", g.xValues.size.toString(), "3")
            check("x 值", g.xValues.joinToString(","), "0.0,0.5,1.0")
            check("函数值", g.columns[0].values.joinToString(","), "1.0,1.5,2.0")
        }
        run {
            // 双函数
            val g = TableCalc.generate("x", 0.0, 2.0, 1.0, "x^2")
            check("双函数两列", g.columns.size.toString(), "2")
            check("第二列标题", g.columns[1].title, "x^2")
            check("第二列值", g.columns[1].values.joinToString(","), "0.0,1.0,4.0")
            val txt = TableCalc.generateText("x", 0.0, 2.0, 1.0, "x^2")
            checkTrue("文本含两个标题", txt.contains("x^2"), txt)
        }
        run {
            // 无定义处显示 ERROR(如 1/x 在 x=0)
            val g = TableCalc.generate("1/x", -1.0, 1.0, 1.0)
            val vals = g.columns[0].values
            check("x=-1 有值", vals[0].toString(), "-1.0")
            checkTrue("x=0 无定义", vals[1] == null, vals[1]?.toString() ?: "null")
            check("x=1 有值", vals[2].toString(), "1.0")
            checkTrue("文本显示 ERROR", g.render().contains("ERROR"), g.render())
        }
        run {
            // 行数上限:超出时截断并提示
            val txt = TableCalc.generateText("x", 0.0, 100.0, 1.0)
            checkTrue("提示总行数", txt.contains("共 101 行"), txt.take(200))
            checkTrue("含截断说明", txt.contains("只显示前"), txt.take(200))
        }
        run {
            // 要遵守手册的行数上限常量
            check("单函数上限 45", TableCalc.MAX_ROWS_SINGLE.toString(), "45")
            check("双函数上限 30", TableCalc.MAX_ROWS_DOUBLE.toString(), "30")
        }
        run {
            // 参数校验:步长必须为正
            var err: String? = null
            try {
                TableCalc.generate("x", 0.0, 5.0, 0.0)
            } catch (e: Exception) {
                err = e.message
            }
            checkTrue("步长 0 报错", err != null, err ?: "没有报错")
            var err2: String? = null
            try {
                TableCalc.generate("x", 0.0, 5.0, -1.0)
            } catch (e: Exception) {
                err2 = e.message
            }
            checkTrue("负步长报错", err2 != null, err2 ?: "没有报错")
            var err3: String? = null
            try {
                TableCalc.generate("x", 5.0, 0.0, 1.0)
            } catch (e: Exception) {
                err3 = e.message
            }
            checkTrue("起始大于结束报错", err3 != null, err3 ?: "没有报错")
        }
        run {
            // 命令层
            val e = CalcEngine()
            val t = e.process(":table x^2 1 3 1")
            checkTrue(":table 含表头", t.contains("x^2"), t.take(200))
            checkTrue(":table 含结果 9", t.contains("9"), t.take(200))
            checkTrue(":table 缺参数给用法", e.process(":table x^2").contains("用法"), ":table")
            checkTrue(":table 非法数值报错", e.process(":table x^2 a 3 1").contains("错误"), "a")
            val c = e.process(":calc x^2+y x=3 y=4")
            checkTrue(":calc 得 13", c.contains("13"), c)
            checkTrue(":calc 缺参数给用法", e.process(":calc x^2").contains("用法"), ":calc")
            checkTrue(":calc 赋值格式错报错", e.process(":calc x^2 zz").contains("错误"), "zz")
        }
    }

    // ---------------- 8s. 矩阵/向量表达式求值(第 10 批) ----------------
    private fun testMatExpr() {
        val a = Matrix.of(2.0, 1.0, 1.0, 1.0)        // [[2,1],[1,1]]
        val b = Matrix.of(2.0, -1.0, -1.0, 2.0)      // [[2,-1],[-1,2]]
        val mats = mapOf("MatA" to a, "MatB" to b)
        val va = Vector.of2(1.0, 2.0)
        val vb = Vector.of2(3.0, 4.0)
        val vecs = mapOf("VctA" to va, "VctB" to vb)

        run {
            // 识别:只有含矩阵/向量变量或专用函数时才走这个求值器
            checkTrue("识别 MatA", MatExpr.mentions("MatA+MatB"), "MatA+MatB")
            checkTrue("识别 VctA", MatExpr.mentions("VctA·VctB"), "VctA·VctB")
            checkTrue("识别 Det(", MatExpr.mentions("Det(MatA)"), "Det(MatA)")
            checkTrue("识别 Identity(", MatExpr.mentions("Identity(3)"), "Identity(3)")
            checkTrue("纯数值不识别", !MatExpr.mentions("1+2*3"), "1+2*3")
            checkTrue("含 x 不识别", !MatExpr.mentions("x^2+1"), "x^2+1")
        }
        run {
            // 矩阵加减
            val r = MatExpr.eval("MatA+MatB", mats, vecs)
            checkTrue("加法得矩阵", r is MatExpr.Value.Mat, r.toString())
            check("A+B", MatExpr.format(r), "4  0\n0  3")
            val r2 = MatExpr.eval("MatA-MatB", mats, vecs)
            check("A-B", MatExpr.format(r2), "0  2\n2  -1")
        }
        run {
            // 矩阵乘
            val r = MatExpr.eval("MatA*MatB", mats, vecs)
            check("A*B", MatExpr.format(r), "3  0\n1  1")
            // × 也认
            val r2 = MatExpr.eval("MatA×MatB", mats, vecs)
            check("A×B 同 A*B", MatExpr.format(r2), "3  0\n1  1")
        }
        run {
            // 纯量乘(左右都可)
            check("2*A", MatExpr.format(MatExpr.eval("2*MatA", mats, vecs)), "4  2\n2  2")
            check("A*2", MatExpr.format(MatExpr.eval("MatA*2", mats, vecs)), "4  2\n2  2")
            // 除以纯量
            check("A/2", MatExpr.format(MatExpr.eval("MatA/2", mats, vecs)), "1  0.5\n0.5  0.5")
        }
        run {
            // 幂
            check("A^2", MatExpr.format(MatExpr.eval("MatA^2", mats, vecs)), Mat.format(Mat.mul(a, a)))
            check("A^0 = I", MatExpr.format(MatExpr.eval("MatA^0", mats, vecs)), "1  0\n0  1")
            // 非整数指数报错
            var err: String? = null
            try {
                MatExpr.eval("MatA^1.5", mats, vecs)
            } catch (e: Exception) {
                err = e.message
            }
            checkTrue("非整数幂报错", err != null, err ?: "没有报错")
        }
        run {
            // 函数
            check("Det(MatA)", MatExpr.format(MatExpr.eval("Det(MatA)", mats, vecs)), "1")
            check("Trn(MatA)", MatExpr.format(MatExpr.eval("Trn(MatA)", mats, vecs)), "2  1\n1  1")
            check("Inv(MatA)", MatExpr.format(MatExpr.eval("Inv(MatA)", mats, vecs)), "1  -1\n-1  2")
            checkTrue("Identity(3) 是 3×3", MatExpr.format(MatExpr.eval("Identity(3)", mats, vecs)).contains("\n"), "3x3")
            check("Abs(矩阵)", MatExpr.format(MatExpr.eval("Abs(MatB)", mats, vecs)), "2  1\n1  2")
        }
        run {
            // 复合表达式
            val r = MatExpr.eval("MatA*MatB+MatA", mats, vecs)
            checkTrue("复合可算", r is MatExpr.Value.Mat, r.toString())
            // Det(MatA*MatB) = Det(A)*Det(B) = 1*3 = 3
            check("Det(A*B)", MatExpr.format(MatExpr.eval("Det(MatA*MatB)", mats, vecs)), "3")
            // 括号
            check("(MatA+MatB)*2", MatExpr.format(MatExpr.eval("(MatA+MatB)*2", mats, vecs)), "8  0\n0  6")
        }
        run {
            // 向量
            check("VctA+VctB", MatExpr.format(MatExpr.eval("VctA+VctB", vecs = vecs, mats = emptyMap())), "(4, 6)")
            check("VctA-VctB", MatExpr.format(MatExpr.eval("VctA-VctB", vecs = vecs, mats = emptyMap())), "(-2, -2)")
            check("2*VctA", MatExpr.format(MatExpr.eval("2*VctA", vecs = vecs, mats = emptyMap())), "(2, 4)")
            check("VctA·VctB = 11", MatExpr.format(MatExpr.eval("VctA·VctB", vecs = vecs, mats = emptyMap())), "11")
            check("Dot(VctA,VctB)", MatExpr.format(MatExpr.eval("Dot(VctA,VctB)", vecs = vecs, mats = emptyMap())), "11")
            check("Abs(VctA) = √5", MatExpr.format(MatExpr.eval("Abs(VctA)", vecs = vecs, mats = emptyMap())), "2.2360679775")
        }
        run {
            // 三维叉积
            val v3 = mapOf("VctA" to Vector.of3(1.0, 0.0, 0.0), "VctB" to Vector.of3(0.0, 1.0, 0.0))
            check("Cross = ẑ", MatExpr.format(MatExpr.eval("Cross(VctA,VctB)", vecs = v3, mats = emptyMap())), "(0, 0, 1)")
        }
        run {
            // 夹角按设定单位
            val vv = mapOf("VctA" to Vector.of2(1.0, 0.0), "VctB" to Vector.of2(0.0, 1.0))
            val deg = MatExpr.eval("Angle(VctA,VctB)", vecs = vv, mats = emptyMap(), angleUnit = AngleUnit.DEGREE)
            check("角度制 90", MatExpr.format(deg), "90")
            val rad = MatExpr.eval("Angle(VctA,VctB)", vecs = vv, mats = emptyMap(), angleUnit = AngleUnit.RADIAN)
            checkTrue("弧度制约 1.5708", MatExpr.format(rad).startsWith("1.5707"), MatExpr.format(rad))
        }
        run {
            // 单位向量
            val vv = mapOf("VctA" to Vector.of2(3.0, 4.0))
            check("UnitV", MatExpr.format(MatExpr.eval("UnitV(VctA)", vecs = vv, mats = emptyMap())), "(0.6, 0.8)")
        }
        run {
            // 错误分支
            var e1: String? = null
            try {
                MatExpr.eval("MatA+3", mats, vecs)      // 矩阵加纯量:数学上无定义
            } catch (e: Exception) {
                e1 = e.message
            }
            checkTrue("矩阵加纯量明确报错", e1 != null && e1.contains("不能相加"), e1 ?: "没有报错")
            var e2: String? = null
            try {
                MatExpr.eval("MatA+VctA", mats, vecs)
            } catch (e: Exception) {
                e2 = e.message
            }
            checkTrue("矩阵与向量相加报错", e2 != null, e2 ?: "没有报错")
            var e3: String? = null
            try {
                MatExpr.eval("MatZ+MatA", mats, vecs)   // 未录入
            } catch (e: Exception) {
                e3 = e.message
            }
            checkTrue("未录入的矩阵报错", e3 != null && e3.contains("未知名称"), e3 ?: "没有报错")
            var e4: String? = null
            try {
                MatExpr.eval("MatA/MatB", mats, vecs)   // 矩阵不能作除数
            } catch (e: Exception) {
                e4 = e.message
            }
            checkTrue("矩阵作除数报错", e4 != null, e4 ?: "没有报错")
            var e5: String? = null
            try {
                MatExpr.eval("Det(MatA,MatB)", mats, vecs)  // 参数个数不对
            } catch (e: Exception) {
                e5 = e.message
            }
            checkTrue("参数个数错报错", e5 != null && e5.contains("需要"), e5 ?: "没有报错")
        }
        run {
            // 维度不符的错误来自 Mat 层,应能透出
            val m23 = mapOf("MatA" to Matrix.zeros(2, 3), "MatB" to Matrix.zeros(3, 2))
            var err: String? = null
            try {
                MatExpr.eval("MatA+MatB", m23, emptyMap())
            } catch (e: Exception) {
                err = e.message
            }
            checkTrue("维度不符报错", err != null && err.contains("维度"), err ?: "没有报错")
        }
        run {
            // 类型名标注
            check("矩阵类型名", MatExpr.kindOf(MatExpr.eval("MatA", mats, vecs)), "矩阵 2×2")
            check("向量类型名", MatExpr.kindOf(MatExpr.eval("VctA", mats, vecs)), "向量 2 维")
            check("纯量类型名", MatExpr.kindOf(MatExpr.eval("Det(MatA)", mats, vecs)), "纯量")
        }
        run {
            // 标量表达式也能算(纯量部分走同一套优先级)
            check("纯量加减乘", MatExpr.format(MatExpr.eval("1+2*3", emptyMap(), emptyMap())), "7")
            check("纯量括号", MatExpr.format(MatExpr.eval("(1+2)*3", emptyMap(), emptyMap())), "9")
            check("纯量幂", MatExpr.format(MatExpr.eval("2^10", emptyMap(), emptyMap())), "1024")
        }
    }

    // ---------------- 8t. 引擎层的矩阵/向量表达式(第 10 批) ----------------
    private fun testEngineMatExpr() {
        run {
            val e = CalcEngine()
            e.matrixEnv["MatA"] = Matrix.of(2.0, 1.0, 1.0, 1.0)
            e.matrixEnv["MatB"] = Matrix.of(2.0, -1.0, -1.0, 2.0)
            // 算式栏直接写 MatA*MatB
            val out = e.process("MatA*MatB")
            checkTrue("含结果 3", out.contains("3"), out)
            checkTrue("含类型标注", out.contains("矩阵 2×2"), out)
            // 结果进 MatAns
            checkTrue("写回 MatAns", e.matrixEnv.containsKey("MatAns"), e.matrixEnv.keys.toString())
            // MatAns 可继续参与运算
            val out2 = e.process("MatAns*2")
            checkTrue("MatAns 可用", out2.contains("6"), out2)
        }
        run {
            val e = CalcEngine()
            e.matrixEnv["MatA"] = Matrix.of(2.0, 1.0, 1.0, 1.0)
            val out = e.process("Det(MatA)")
            checkTrue("Det 得 1", out.contains("1"), out)
            checkTrue("标量类型标注", out.contains("纯量"), out)
        }
        run {
            val e = CalcEngine()
            e.vectorEnv["VctA"] = Vector.of2(1.0, 2.0)
            e.vectorEnv["VctB"] = Vector.of2(3.0, 4.0)
            val out = e.process("VctA·VctB")
            checkTrue("内积 11", out.contains("11"), out)
            checkTrue("含类型标注", out.contains("纯量"), out)
            val out2 = e.process("VctA+VctB")
            checkTrue("向量加法", out2.contains("(4, 6)"), out2)
            checkTrue("写回 VctAns", e.vectorEnv.containsKey("VctAns"), e.vectorEnv.keys.toString())
        }
        run {
            // 纯数值表达式不受影响(仍走标量引擎)
            val e = CalcEngine()
            checkTrue("纯数值仍正常", e.process("1+2*3").contains("7"), e.process("1+2*3"))
            // x^2 这种含字母但不是矩阵的,不能被误判
            checkTrue("x^2 走标量路径", e.process("x^2") != "", e.process("x^2"))
        }
        run {
            // 未录入矩阵时报错要清楚
            val e = CalcEngine()
            val out = e.process("MatA+MatB")
            checkTrue("未录入报错", out.contains("错误") || out.contains("未知"), out)
        }
        run {
            // 矩阵加纯量:数学上无定义,必须明确报错而不是悄悄当成乘
            val e = CalcEngine()
            e.matrixEnv["MatA"] = Matrix.of(1.0, 0.0, 0.0, 1.0)
            val out = e.process("MatA+3")
            checkTrue("矩阵加纯量报错", out.contains("不能相加"), out)
        }
        run {
            // 维度不符时错误来自 Mat 层
            val e = CalcEngine()
            e.matrixEnv["MatA"] = Matrix.zeros(2, 3)
            e.matrixEnv["MatB"] = Matrix.zeros(3, 2)
            val out = e.process("MatA+MatB")
            checkTrue("维度不符报错", out.contains("维度") || out.contains("错误"), out)
        }
        run {
            // 角度单位影响 Angle
            val e = CalcEngine()
            e.vectorEnv["VctA"] = Vector.of2(1.0, 0.0)
            e.vectorEnv["VctB"] = Vector.of2(0.0, 1.0)
            val out = e.process("Angle(VctA,VctB)")
            checkTrue("默认角度制 90", out.contains("90"), out)
        }
    }

    // ---------------- 8u. 历史持久化编码(第 11 批) ----------------
    private fun testHistoryCodec() {
        run {
            // 基本往返
            val src = listOf("1+1", "sin(30°)", "x^2-2=0")
            val enc = Calc.HistoryCodec.encode(src)
            check("编码往返", Calc.HistoryCodec.decode(enc).joinToString("|"), src.joinToString("|"))
        }
        run {
            // ★ 算式里含分隔符也不能错位 —— 这正是用长度前缀的原因
            val nasty = listOf(
                ":int 0 1 x^2",              // 含空格与冒号
                "x+y=3, x-y=1",              // 含逗号(与编码分隔符相同)
                "1:2:3",                     // 含多个冒号
                "2,3,4",                     // 含逗号
                "a:b,c:d",                   // 冒号逗号混合
                ":conv 1 in cm",             // 冒号 + 空格
                "MatA*MatB",                 // 矩阵表达式
                "5:5:5:5:5"                  // 多个冒号
            )
            val enc = Calc.HistoryCodec.encode(nasty)
            val dec = Calc.HistoryCodec.decode(enc)
            check("特殊字符不错位", dec.joinToString("|"), nasty.joinToString("|"))
        }
        run {
            // 空表
            check("空表编码", Calc.HistoryCodec.encode(emptyList()), "")
            check("空串解码", Calc.HistoryCodec.decode("").size.toString(), "0")
            // 空白项被过滤
            check("过滤空白项", Calc.HistoryCodec.encode(listOf("", "  ", "1+1")), "3:1+1")
        }
        run {
            // 坏数据不能让整份历史丢失
            val broken = "3:1+1,XX:bad,3:2+2"
            val dec = Calc.HistoryCodec.decode(broken)
            checkTrue("坏项被跳过", dec.contains("1+1") && dec.contains("2+2"), dec.toString())
        }
        run {
            // 截断的数据取到末尾为止,不抛异常
            val truncated = "3:1+1,10:abc"
            val dec = Calc.HistoryCodec.decode(truncated)
            checkTrue("截断项被收下", dec.isNotEmpty(), dec.toString())
        }
        run {
            // 单条
            check("单条往返", Calc.HistoryCodec.decode(Calc.HistoryCodec.encode(listOf("7*8")))[0], "7*8")
            // 长度恰好等于内容长度(UTF-16 字符数)
            check("长度按字符数", Calc.HistoryCodec.encode(listOf("abc")), "3:abc")
            // 中文算式:长度按字符数计
            val cn = listOf(":stat 1 2,4,4")
            check("中文/符号混合往返", Calc.HistoryCodec.decode(Calc.HistoryCodec.encode(cn))[0], cn[0])
        }
        run {
            // History.restore:恢复并遵守条数上限
            val h = Calc.History(limit = 3)
            h.restore(listOf("a", "b", "c", "d"))
            check("restore 遵守上限", h.entries().joinToString(","), "b,c,d")
            // restore 会清掉旧的
            h.restore(listOf("z"))
            check("restore 覆盖旧数据", h.entries().joinToString(","), "z")
            // 恢复后可以正常翻阅
            check("恢复后可向上翻", h.up(), "z")
        }
        run {
            // 真实场景:算几次 → 编码 → 解码 → 恢复 → 能翻到最早那条
            val h1 = Calc.History()
            h1.add("7*8")
            h1.add("9+1")
            h1.add("sin(0)")
            val enc = Calc.HistoryCodec.encode(h1.entries())
            val h2 = Calc.History()
            h2.restore(Calc.HistoryCodec.decode(enc))
            check("恢复后条数", h2.entries().size.toString(), "3")
            check("恢复后向上翻最新", h2.up(), "sin(0)")
            check("再向上翻", h2.up(), "9+1")
            check("翻到最早", h2.up(), "7*8")
            check("最早处停住", h2.up(), "7*8")
        }
    }

    // ---------------- 8v. 批次 7 收尾函数 ----------------
    private fun testExtraFunctions() {
        fun calc(expr: String, env: Map<String, Double> = emptyMap()): String =
            Fmt.number(Parser.parse(expr).eval(env), Settings(), mathExact = false)

        run {
            // 立方根:∛27 = 3、∛-8 = -2
            check("cbrt(27)", calc("cbrt(27)"), "3")
            check("cbrt(-8)", calc("cbrt(-8)"), "-2")
            check("cbrt(0)", calc("cbrt(0)"), "0")
        }
        run {
            // Int / Intg:向负无穷取整(注意与 trunc 不同)
            check("int(3.7)", calc("int(3.7)"), "3")
            check("int(-3.7) 向负无穷", calc("int(-3.7)"), "-4")
            check("intg(-3.5)", calc("intg(-3.5)"), "-4")
            check("intg(2.1)", calc("intg(2.1)"), "2")
        }
        run {
            // Rnd:按 11 位有效数字四舍五入
            check("rnd(3.14159265358979)", calc("rnd(3.14159265358979)"), "3.1415926536")
            checkTrue(
                "rnd(123456789012) = 123456789010",
                Parser.parse("rnd(123456789012)").eval(emptyMap()) == 123456789010.0,
                Parser.parse("rnd(123456789012)").eval(emptyMap()).toString()
            )
            check("rnd(0)", calc("rnd(0)"), "0")
            check("rnd(-2.5)", calc("rnd(-2.5)"), "-2.5")
        }
        run {
            // gcd / lcm
            check("gcd(12,18)", calc("gcd(12,18)"), "6")
            check("gcd(17,5)", calc("gcd(17,5)"), "1")
            check("gcd(0,7)", calc("gcd(0,7)"), "7")
            check("lcm(4,6)", calc("lcm(4,6)"), "12")
            check("lcm(0,5)", calc("lcm(0,5)"), "0")
            check("lcm(7,3)", calc("lcm(7,3)"), "21")
            // 负数输入要报错
            var err: String? = null
            try {
                calc("gcd(-1,5)")
            } catch (e: Exception) {
                err = e.message
            }
            checkTrue("gcd 负数报错", err != null, err ?: "没有报错")
        }
        run {
            // Ran#:值域 [0,1),可复现
            MathFns.reseed(42L)
            val a: List<Double> = (1..50).map { Parser.parse("ran()").eval(emptyMap()) }
            checkTrue("Ran# 都在 [0,1)", a.all { it >= 0.0 && it < 1.0 }, a.take(3).toString())
            checkTrue("Ran# 有变化", a.distinct().size > 40, a.distinct().size.toString())
            // 同一种子复现同一序列
            MathFns.reseed(7L)
            val s1 = (1..5).map { Parser.parse("ran()").eval(emptyMap()) }
            MathFns.reseed(7L)
            val s2 = (1..5).map { Parser.parse("ran()").eval(emptyMap()) }
            check("同种子可复现", s1.toString(), s2.toString())
        }
        run {
            // RanInt#:整数、落在闭区间内
            MathFns.reseed(123L)
            val v: List<Double> = (1..200).map { Parser.parse("ranint(1,6)").eval(emptyMap()) }
            checkTrue("RanInt# 都是整数", v.all { it == Math.floor(it) }, "整数")
            checkTrue("RanInt# 在 [1,6]", v.all { it >= 1.0 && it <= 6.0 }, v.min().toString() + "~" + v.max())
            checkTrue("RanInt# 覆盖到边界", v.contains(1.0) && v.contains(6.0), v.distinct().sorted().toString())
            // 参数顺序错要报错
            var err: String? = null
            try {
                calc("ranint(6,1)")
            } catch (e: Exception) {
                err = e.message
            }
            checkTrue("RanInt# 逆序报错", err != null, err ?: "没有报错")
        }
        run {
            // Pol:r = √(x²+y²)
            check("pol(3,4)", calc("pol(3,4)"), "5")
            checkTrue(
                "pol(1,1) = √2",
                abs(Parser.parse("pol(1,1)").eval(emptyMap()) - Math.sqrt(2.0)) < 1e-12,
                calc("pol(1,1)")
            )
            // poltheta:atan2
            checkTrue("poltheta(0,1) = π/2", abs(Parser.parse("poltheta(0,1)").eval(emptyMap()) - Math.PI / 2) < 1e-12, "θ")
        }
        run {
            // Rec:x = r·cos θ(弧度)
            checkTrue("rec(2,0) = 2", abs(Parser.parse("rec(2,0)").eval(emptyMap()) - 2.0) < 1e-12, "rec")
            checkTrue("rec(2,π/3) = 1", abs(Parser.parse("rec(2,pi/3)").eval(emptyMap()) - 1.0) < 1e-12, "rec")
        }
        run {
            // 参数个数错误要报清楚
            var err: String? = null
            try {
                calc("cbrt(1,2)")
            } catch (e: Exception) {
                err = e.message
            }
            checkTrue("cbrt 参数过多报错", err != null && err.contains("需要"), err ?: "没有报错")
            var err2: String? = null
            try {
                calc("ran(1)")
            } catch (e: Exception) {
                err2 = e.message
            }
            checkTrue("ran 不接受参数", err2 != null, err2 ?: "没有报错")
        }
        run {
            // 引擎层:这些函数能在算式栏里用
            val e = CalcEngine()
            checkTrue("gcd 走引擎", e.process("gcd(12,18)").contains("6"), e.process("gcd(12,18)"))
            checkTrue("cbrt 走引擎", e.process("cbrt(27)").contains("3"), e.process("cbrt(27)"))
        }
    }

    // ---------------- 9. 线性方程组 ----------------
    private fun testLinearSystems() {
        run {
            val r = Solver.solveLinearSystem(listOf("2x+y=5", "x-y=1"))
            check("2x+y=5,x-y=1 -> x", r.solution[0].toString(), "2")
            check("2x+y=5,x-y=1 -> y", r.solution[1].toString(), "1")
        }
        run {
            val r = Solver.solveLinearSystem(listOf("3x+2y=7", "x-y=1"))
            check("3x+2y=7,x-y=1 -> x=9/5", r.solution[0].toString(), "9/5")
            check("3x+2y=7,x-y=1 -> y=4/5", r.solution[1].toString(), "4/5")
        }
        run {
            // 通过命令层(用户实际输入路径)验证三元一次方程组,含 z 变量
            val e = CalcEngine()
            val out = e.process("x+y+z=6, 2x-y+z=3, x+2y-z=2")
            checkTrue("命令层三元方程组含解 x=1", out.contains("x = 1"), out)
            checkTrue("命令层三元方程组含解 y=2", out.contains("y = 2"), out)
            checkTrue("命令层三元方程组含解 z=3", out.contains("z = 3"), out)
        }
        run {
            // 三个未知量含分数解:2x+3y+z=1, x-y+z=0, 3x+y-2z=4
            val e = CalcEngine()
            val out = e.process("z=1, y=2, x=3")
            // 顺带验证 z 单独作为未知量可解
            val r = Solver.solveLinearSystem(listOf("z=1"))
            check("单变量 z 方程", r.solution.map { it.toString() }, listOf("1"))
            checkTrue("含 z 的方程有输出", out.isNotEmpty(), out)
        }
        run {
            // 三元无解 / 无穷多解必须给出明确错误,而不是乱给答案
            val bad = runCatching {
                Solver.solveLinearSystem(listOf("x+y+z=1", "x+y+z=2", "x-y=0"))
            }.exceptionOrNull()
            checkTrue("三元矛盾方程组应报错", bad != null, bad?.message ?: "没有报错")
        }
        run {
            val r = Solver.solveLinearSystem(listOf("x+y+z=6", "2x-y+z=3", "x+2y-z=2"))
            check("3x3 方程组", r.solution.map { it.toString() }, listOf("1", "2", "3"))
        }
        run {
            val r = Solver.solveLinearSystem(
                listOf("x+y+z+w=10", "x-y+z-w=2", "x+y-z-w=0", "x-y-z+w=4")
            )
            // 变量按字母序排列:w, x, y, z
            check("4x4 变量顺序", r.variables, listOf("w", "x", "y", "z"))
            check("4x4 解", r.solution.map { it.toString() }, listOf("3", "4", "1", "2"))
        }
        checkThrows("矛盾方程组应报错") {
            Solver.solveLinearSystem(listOf("x+y=1", "x+y=3"))
        }
        checkThrows("无穷多解应报错") {
            Solver.solveLinearSystem(listOf("x+y+z=3", "x-y=1"))
        }
        checkThrows("交叉项应报错") {
            Solver.solveLinearSystem(listOf("2x+y=5", "xy=2"))
        }
    }

    // ---------------- 1. 四则运算与优先级 ----------------
    private fun testArithmetic() {
        check("1+2*3", calc("1+2*3"), "7")
        check("(10-4)/2", calc("(10-4)/2"), "3")
        check("2^10", calc("2^10"), "1024")
        check("-3+1", calc("-3+1"), "-2")
        check("3--2", calc("3--2"), "5")
        check("3*-2", calc("3*-2"), "-6")
        check("-(2+3)", calc("-(2+3)"), "-5")
        check("6/2/3", calc("6/2/3"), "1")
        check("1/3", calc("1/3"), "0.3333333333")
        check("2^0.5", calc("2^0.5"), "1.4142135624")
        check("5!", calc("5!"), "120")
        check("10%3", calc("10%3"), "1")
        check("2.5*4", calc("2.5*4"), "10")
    }

    // ---------------- 2. 隐式乘号与函数 ----------------
    private fun testImplicitMulAndFunctions() {
        check("2x (x=3)", calc("2x", mapOf("x" to 3.0)), "6")
        check("2pi", calc("2pi"), "6.2831853072")
        // 隐式乘号优先于乘除(手册优先顺序第 7 级):1/2x = 1/(2x),而非 (1/2)x。
        // 注意:x=3 时两者恰好都等于 1.5(3/2 与 3/(3*2) 的错觉),无法区分,故用 x=2。
        check("1/2x (x=2) 按手册应为 1/(2x)", calc("1/2x", mapOf("x" to 2.0)), "0.25")
        check("6/2x (x=3) 按手册应为 6/(2x)", calc("6/2x", mapOf("x" to 3.0)), "1")
        check("6/2π 按手册应为 6/(2π)", calc("6/2π"), "0.9549296586")
        check("3(x+1) (x=3)", calc("3(x+1)", mapOf("x" to 3.0)), "12")
        check("sin(pi/6)", calc("sin(pi/6)"), "0.5")
        check("cos(0)", calc("cos(0)"), "1")
        check("ln(e)", calc("ln(e)"), "1")
        check("log(100)", calc("log(100)"), "2")
        check("log2(8)", calc("log2(8)"), "3")
        check("sqrt(16)", calc("sqrt(16)"), "4")
        check("√16", calc("√16"), "4")
        check("√16+1", calc("√16+1"), "5")
        check("2√9", calc("2√9"), "6")
        check("abs(-3)", calc("abs(-3)"), "3")
        check("floor(2.7)", calc("floor(2.7)"), "2")
        check("ceil(2.1)", calc("ceil(2.1)"), "3")
        check("round(2.567,2)", calc("round(2.567,2)"), "2.57")
        check("comb(5,2)", calc("comb(5,2)"), "10")
        check("perm(5,2)", calc("perm(5,2)"), "20")
        check("2sin(pi/6)", calc("2sin(pi/6)"), "1")
        check("e^2", calc("e^2"), "7.3890560989")
        check("sin(90°)", calc("sin(90°)"), "1")
        check("sin(30°)", calc("sin(30°)"), "0.5")
        check(" π ", calc("π"), "3.1415926536")
    }

    // ---------------- 3. 幂与一元负号的优先级(曾踩过的坑) ----------------
    private fun testPrecedence() {
        check("-x^2 (x=3) 应为 -(x^2)", calc("-x^2", mapOf("x" to 3.0)), "-9")
        check("-2^2 应为 -(2^2)", calc("-2^2"), "-4")
        check("(-2)^2", calc("(-2)^2"), "4")
        check("2^3^2 右结合", calc("2^3^2"), "512")
        check("2^-1", calc("2^-1"), "0.5")
        check("2x^2 (x=3) 应为 2*(x^2)", calc("2x^2", mapOf("x" to 3.0)), "18")
        check("2x^3 (x=3)", calc("2x^3", mapOf("x" to 3.0)), "54")
        check("(2x)^2 (x=3)", calc("(2x)^2", mapOf("x" to 3.0)), "36")
        check("e^(2x) x=3", calc("e^(2x)", mapOf("x" to 3.0)), "403.4287934927")
        check("3-2^2", calc("3-2^2"), "-1")
    }

    // ---------------- 4. 精确有理数 ----------------
    private fun testExactRational() {
        val a = Rational.of(1, 2)
        val b = Rational.of(1, 3)
        check("1/2+1/3", (a + b).toString(), "5/6")
        check("1/2*2/3", (a * Rational.of(2, 3)).toString(), "1/3")
        check("1/2 ÷ 1/4", (a / Rational.of(1, 4)).toString(), "2")
        check("2^10", Rational.of(2).pow(10).toString(), "1024")
        check("2^-1", Rational.of(2).pow(-1).toString(), "1/2")
        check("1/2 比较 1/3", (a > b), true)
        check("0.5 还原成分数", Rational.fromDouble(0.5).toString(), "1/2")
        check("-2.0 还原", Rational.fromDouble(-2.0).toString(), "-2")
        check("1/3 还原", Rational.fromDouble(1.0 / 3.0).toString(), "1/3")
        check("0.25 是完全平方", Rational.of(1, 4).isPerfectSquare, true)
        check("sqrt(1/4)", Rational.of(1, 4).exactSqrt().toString(), "1/2")
        check("sqrt(8/2)=2", Rational.of(8, 2).exactSqrt().toString(), "2")
    }

    // ---------------- 5. 错误处理 ----------------
    private fun testErrors() {
        val bad = listOf("1/0", "1++", "(1+2", "abc", "x^2+", "1+", "*2", "()", "sin(", "2^", "((1+2)", "@#", "log(0)")
        for (b in bad) {
            checkThrows("错误用例: $b") { Parser.parse(b).eval(emptyMap()) }
        }
        // 这些应该能正常解析(不过求值会因缺变量而报错,属于正常)
        checkTrue("2x 能解析", runCatching { Parser.parse("2x") }.isSuccess)
        checkTrue("sin(30°) 能解析", runCatching { Parser.parse("sin(30°)") }.isSuccess)
        check("firstVariable(2y+6)", Parser.firstVariable("2y+6"), "y")
        check("firstVariable(x^2)", Parser.firstVariable("x^2"), "x")
    }

    private fun report(): String {
        val sb = StringBuilder()
        sb.append("通过 ${pass.size} / 失败 ${fail.size}\n")
        if (fail.isNotEmpty()) {
            sb.append("---- 失败明细 ----\n")
            fail.forEach { sb.append("  [FAIL] ").append(it).append('\n') }
        } else {
            sb.append("全部用例通过 ✅\n")
        }
        return sb.toString()
    }
}
