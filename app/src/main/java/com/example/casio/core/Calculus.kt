package com.example.casio.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 符号求导 + 数值积分。
 * 对应 Python 版的 differentiate / _diff_outer / integrate_numeric。
 */
object Calculus {

    /** 幂的优先级,用于按需加括号 */
    private val PREC = mapOf("+" to 1, "-" to 1, "*" to 2, "/" to 2, "^" to 3)

    // ---------------- 符号求导 ----------------

    fun differentiate(node: Node, v: String): Node = when (node) {
        is NumNode -> NumNode(0.0, Rational.ZERO)
        is ConstNode -> NumNode(0.0, Rational.ZERO)
        is VarNode -> if (node.name == v) NumNode(1.0, Rational.ONE) else NumNode(0.0, Rational.ZERO)
        is UnaryNode -> {
            val d = differentiate(node.operand, v)
            if (node.op == "-") UnaryNode("-", d) else d
        }
        is BinaryNode -> {
            val a = node.left
            val b = node.right
            val da = differentiate(a, v)
            val db = differentiate(b, v)
            when (node.op) {
                "+" -> BinaryNode("+", da, db)
                "-" -> BinaryNode("-", da, db)
                "*" -> BinaryNode(
                    "+",
                    BinaryNode("*", da, b),
                    BinaryNode("*", a, db)
                )
                "/" -> BinaryNode(
                    "/",
                    BinaryNode("-", BinaryNode("*", da, b), BinaryNode("*", a, db)),
                    BinaryNode("^", b, NumNode(2.0, Rational.of(2)))
                )
                "^" -> {
                    if (b is NumNode) {
                        // (f^n)' = n·f^(n-1)·f'
                        val n = b.value
                        BinaryNode(
                            "*",
                            BinaryNode(
                                "*",
                                NumNode(n),
                                BinaryNode("^", a, NumNode(n - 1))
                            ),
                            da
                        )
                    } else {
                        // f^g = exp(g·ln f)
                        BinaryNode(
                            "*",
                            node,
                            BinaryNode(
                                "+",
                                BinaryNode("*", db, CallNode("ln", listOf(a))),
                                BinaryNode("/", BinaryNode("*", b, da), a)
                            )
                        )
                    }
                }
                else -> throw ParseError("暂不支持对 ${node.op} 求导")
            }
        }
        is CallNode -> {
            val a = node.args[0]
            val da = differentiate(a, v)
            val outer = diffOuter(node.name, a)
                ?: throw ParseError("暂不支持对 ${node.name} 求导")
            BinaryNode("*", outer, da)
        }
    }

    private fun diffOuter(name: String, a: Node): Node? {
        val one = { NumNode(1.0, Rational.ONE) }
        val two = { NumNode(2.0, Rational.of(2)) }
        return when (name) {
            "sin" -> CallNode("cos", listOf(a))
            "cos" -> UnaryNode("-", CallNode("sin", listOf(a)))
            "tan" -> BinaryNode("+", one(), BinaryNode("^", CallNode("tan", listOf(a)), two()))
            "ln" -> BinaryNode("/", one(), a)
            "log10" -> BinaryNode("/", one(), BinaryNode("*", a, NumNode(ln(10.0))))
            "log2" -> BinaryNode("/", one(), BinaryNode("*", a, NumNode(ln(2.0))))
            "exp" -> CallNode("exp", listOf(a))
            "sqrt" -> BinaryNode("/", one(), BinaryNode("*", two(), CallNode("sqrt", listOf(a))))
            "asin" -> BinaryNode(
                "/", one(),
                CallNode("sqrt", listOf(BinaryNode("-", one(), BinaryNode("^", a, two()))))
            )
            "acos" -> UnaryNode(
                "-",
                BinaryNode(
                    "/", one(),
                    CallNode("sqrt", listOf(BinaryNode("-", one(), BinaryNode("^", a, two()))))
                )
            )
            "atan" -> BinaryNode("/", one(), BinaryNode("+", one(), BinaryNode("^", a, two())))
            "sinh" -> CallNode("cosh", listOf(a))
            "cosh" -> CallNode("sinh", listOf(a))
            "tanh" -> BinaryNode("-", one(), BinaryNode("^", CallNode("tanh", listOf(a)), two()))
            "abs" -> CallNode("sign", listOf(a))
            else -> null
        }
    }

    // ---------------- 化简 ----------------

    /** 常量折叠 + 基础化简(0*x->0、x+0->x、x/1->x、x^1->x) */
    fun simplify(node: Node, depth: Int = 0): Node {
        if (depth > 60) return node
        return when (node) {
            is NumNode, is ConstNode, is VarNode -> node
            is UnaryNode -> {
                val inner = simplify(node.operand, depth + 1)
                if (isNum(inner)) {
                    val v = inner.eval(emptyMap())
                    NumNode(if (node.op == "-") -v else v)
                } else {
                    UnaryNode(node.op, inner)
                }
            }
            is CallNode -> CallNode(node.name, node.args.map { simplify(it, depth + 1) })
            is BinaryNode -> {
                val op = node.op
                var l = simplify(node.left, depth + 1)
                var r = simplify(node.right, depth + 1)
                val lv = numOrNull(l)
                val rv = numOrNull(r)

                if (lv != null && rv != null) {
                    val v = runCatching {
                        BinaryNode(op, NumNode(lv), NumNode(rv)).eval(emptyMap())
                    }.getOrNull()
                    if (v != null && !v.isNaN()) return NumNode(v)
                }
                when (op) {
                    "*" -> {
                        if (lv == 0.0 || rv == 0.0) return NumNode(0.0, Rational.ZERO)
                        if (lv == 1.0) return r
                        if (rv == 1.0) return l
                        if (isNum(l) && !isNum(r)) {
                            // 保持数字在左
                        } else if (isNum(r) && !isNum(l)) {
                            val t = l; l = r; r = t
                        }
                    }
                    "+" -> {
                        if (lv == 0.0) return r
                        if (rv == 0.0) return l
                    }
                    "-" -> {
                        if (rv == 0.0) return l
                        if (lv == 0.0) return UnaryNode("-", r)
                    }
                    "^" -> {
                        if (rv == 1.0) return l
                        if (rv == 0.0) return NumNode(1.0, Rational.ONE)
                    }
                    "/" -> {
                        if (rv == 1.0) return l
                        if (lv == 0.0) return NumNode(0.0, Rational.ZERO)
                    }
                }
                BinaryNode(op, l, r)
            }
        }
    }

    private fun isNum(n: Node) = n is NumNode

    private fun numOrNull(n: Node): Double? = if (n is NumNode) n.value else null

    // ---------------- 表达式打印 ----------------

    /**
     * 把语法树打印成标准数学写法:按优先级加括号,乘法用 · 连接。
     * outer 为父运算符优先级,right 表示是否为右操作数(处理 - 与 / 的结合性)。
     */
    fun format(node: Node, outer: Int = 0, right: Boolean = false): String {
        when (node) {
            is NumNode -> return Display.fmt(node.value)
            is ConstNode -> return node.name
            is VarNode -> return node.name
            is UnaryNode -> {
                val inner = format(node.operand)
                return if (node.operand is BinaryNode) "-($inner)" else "-$inner"
            }
            is CallNode -> return node.name + "(" +
                node.args.joinToString(", ") { format(it) } + ")"
            is BinaryNode -> {
                val op = node.op
                val p = PREC[op] ?: 2
                val text = when (op) {
                    "^" -> format(node.left, 4) + "^" + format(node.right, 4, right = true)
                    "/" -> format(node.left, p) + "/" + format(node.right, p + 1, right = true)
                    "*" -> {
                        var ls = format(node.left, p)
                        var rs = format(node.right, p, right = true)
                        // 幂与函数/变量相邻时补括号,避免 x^2·cos(x) 被误读
                        if (PREC[opOf(node.left)] == 3 && node.right !is NumNode) ls = "($ls)"
                        if (PREC[opOf(node.right)] == 3 && node.left !is NumNode) rs = "($rs)"
                        if (rs.startsWith("-")) rs = "($rs)"
                        "$ls·$rs"
                    }
                    "-" -> format(node.left, p) + "-" + format(node.right, p + 1, right = true)
                    else -> format(node.left, p) + "+" + format(node.right, p, right = true)
                }
                return if (p < outer) "($text)" else text
            }
        }
    }

    private fun opOf(n: Node): String = if (n is BinaryNode) n.op else ""

    // ---------------- 数值积分 ----------------
    //
    // 早期用复合辛普森法(固定 20000 段)。现改为 **Quad** 的自适应高斯求积:
    // 精度更高,且对尖峰 / 端点奇异能自动细分。
    // nIn 参数保留只为兼容旧调用点。

    fun integrate(f: (Double) -> Double, a: Double, b: Double, nIn: Int = 20000): Double =
        Quad.integrate(f, a, b).value

    /** 带收敛信息的积分(输出里可提示是否达到 tol) */
    fun integrateChecked(f: (Double) -> Double, a: Double, b: Double, tol: Double = 1e-10): Quad.Outcome =
        Quad.integrate(f, a, b, tol)

    /** 数值求导(中心差分 + 外推),对应真机的 d/dx */
    fun derivativeAt(f: (Double) -> Double, x: Double, tol: Double = 1e-10): Double =
        Quad.derivative(f, x, tol)

    private fun safe(f: (Double) -> Double, x: Double): Double {
        val v = runCatching { f(x) }.getOrNull() ?: return 0.0
        return if (v.isNaN() || v.isInfinite()) 0.0 else v
    }

    /** 供界面使用:把表达式树包装成 f(x) */
    fun functionOf(node: Node, v: String): (Double) -> Double = { x ->
        runCatching { node.eval(mapOf(v to x)) }.getOrDefault(Double.NaN)
    }

    /** 求导的完整流程:解析 -> 求导 -> 化简 -> 文本 */
    fun derivativeText(expression: String, v: String): String {
        val node = Parser.parse(expression)
        val d = simplify(differentiate(node, v))
        return format(d)
    }

    /** 只算数值(供 :int 的标注式输出复用) */
    fun integrateValue(lower: String, upper: String, expression: String, env: Map<String, Double>): Double {
        val a = Parser.parse(lower).eval(env)
        val b = Parser.parse(upper).eval(env)
        val node = Parser.parse(expression)
        val v = Parser.firstVariable(expression)
        return integrate(functionOf(node, v), a, b)
    }

    /** 数值积分的文本结果 */
    fun integrateText(lower: String, upper: String, expression: String, env: Map<String, Double>): String {
        val a = Parser.parse(lower).eval(env)
        val b = Parser.parse(upper).eval(env)
        val node = Parser.parse(expression)
        val v = Parser.firstVariable(expression)
        val value = integrate(functionOf(node, v), a, b)
        val exprText = Display.integral(a, b, expression, v)
        return Display.box("数值积分", listOf(exprText, "= ${Display.fmt(value)}"))
    }
}
