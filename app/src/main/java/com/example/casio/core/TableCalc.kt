package com.example.casio.core

/**
 * CALC 与 TABLE —— 对应 fx-991CN X 的两个功能。
 *
 * ## CALC
 * 把一个含变量的表达式存起来,然后**反复代入不同的变量值求值**。
 * 真机流程:输入式子 → 按 CALC → 依次提示 `x?`、`y?` → 给值后得结果。
 * 这里保留同样的"逐个问变量"节奏。
 *
 * ## TABLE
 * 由 `f(x)` 与起止值、步长生成**函数值表**;
 * 手册支持同时列两个函数(设定项「表格双函数」)。
 */
object TableCalc {

    // ---------------- CALC ----------------

    /**
     * 一个待代入的表达式。
     * 变量按**首次出现顺序**记录,代入时逐个询问(与真机一致)。
     */
    class CalcSession(val expression: String) {
        val variables: List<String>
        private val answers = linkedMapOf<String, Double>()
        private val node: Node

        init {
            val eq = expression.indexOf('=')
            node = if (eq >= 0) {
                BinaryNode("-", Parser.parse(expression.substring(0, eq)), Parser.parse(expression.substring(eq + 1)))
            } else {
                Parser.parse(expression)
            }
            variables = Parser.collectVariables(node).toList()
        }

        /** 下一个还没赋值的变量;都赋完返回 null */
        fun nextVariable(): String? = variables.firstOrNull { it !in answers }

        /** 给变量赋值 */
        fun assign(name: String, value: Double) {
            answers[name] = value
        }

        /** 全部变量都赋过值了吗 */
        fun ready(): Boolean = nextVariable() == null

        /** 当前已赋的值(供显示) */
        fun assignments(): Map<String, Double> = answers.toMap()

        /** 代入求值;未赋完或出错返回 null */
        fun value(): Double? {
            if (!ready()) return null
            return try {
                val v = node.eval(answers)
                if (v.isFinite()) v else null
            } catch (e: Exception) {
                null
            }
        }

        /** 重置为只保留表达式 */
        fun reset() {
            answers.clear()
        }
    }

    /** CALC 的提示语:下一个变量问什么 */
    fun calcPrompt(session: CalcSession): String {
        val v = session.nextVariable()
            ?: return "所有变量已赋值,按「求值」得结果"
        val done = session.assignments().entries.joinToString(", ") { "${it.key}=${Display.fmt(it.value)}" }
        return if (done.isEmpty()) "$v ?" else "$v ?   (已赋:$done)"
    }

    /** CALC 的结果展示 */
    fun calcResult(session: CalcSession): String {
        val v = session.value()
        val sub = session.assignments().entries.joinToString(", ") { "${it.key}=${Display.fmt(it.value)}" }
        return if (v == null) {
            Display.box("CALC", listOf(session.expression, sub, "", "还没有赋值完,或代入后无有效结果"))
        } else {
            Display.box("CALC", listOf(session.expression, sub, "", "= ${Display.fmt(v)}"))
        }
    }

    // ---------------- TABLE ----------------

    /** 表格的一列 */
    data class Column(val title: String, val values: List<Double?>)

    /** 一张表 */
    data class Grid(val xValues: List<Double>, val columns: List<Column>) {
        fun render(): String {
            val rows = mutableListOf<String>()
            // 表头
            val head = buildString {
                append("x".padEnd(12))
                columns.forEach { append(it.title.padEnd(14)) }
            }
            rows.add(head)
            rows.add("-".repeat(head.length))
            xValues.forEachIndexed { i, x ->
                val line = buildString {
                    append(Display.fmt(x).padEnd(12))
                    columns.forEach { c ->
                        val v = c.values.getOrNull(i)
                        append((if (v == null) "ERROR" else Display.fmt(v)).padEnd(14))
                    }
                }
                rows.add(line.trimEnd())
            }
            return rows.joinToString("\n")
        }
    }

    /**
     * 生成函数值表。
     *
     * @param expr 表达式(用 x 作自变量)
     * @param start 起始值
     * @param end 结束值
     * @param step 步长(必须为正)
     * @param expr2 第二个表达式(可空;设定项「表格双函数」为开时才用)
     */
    fun generate(
        expr: String,
        start: Double,
        end: Double,
        step: Double,
        expr2: String? = null,
        maxRows: Int = 45
    ): Grid {
        require(step > 0) { "步长必须大于 0,当前为 ${Display.fmt(step)}" }
        require(step.isFinite()) { "步长必须是有限数" }
        require(start.isFinite() && end.isFinite()) { "起止值必须是有限数" }
        val steps = Math.floor((end - start) / step + 1e-9).toInt() + 1
        require(steps > 0) { "起始值不能大于结束值" }
        require(steps <= maxRows * 20) { "行数太多($steps 行),请减小范围或增大步长" }

        val n = minOf(steps, maxRows)

        fun evalAt(e: String): (Double) -> Double? {
            val node = Parser.parse(e)
            return { x ->
                try {
                    val v = node.eval(mapOf("x" to x))
                    if (v.isFinite()) v else null
                } catch (ex: Exception) {
                    null
                }
            }
        }
        val f1 = evalAt(expr)
        val f2 = expr2?.takeIf { it.isNotBlank() }?.let { evalAt(it) }

        val xs = (0 until n).map { start + it * step }
        val cols = mutableListOf(Column(expr, xs.map { f1(it) }))
        if (f2 != null) cols.add(Column(expr2!!, xs.map { f2(it) }))
        return Grid(xs, cols)
    }

    /** 生成并把截断提示一并放进文本 */
    fun generateText(
        expr: String,
        start: Double,
        end: Double,
        step: Double,
        expr2: String? = null,
        maxRows: Int = 45
    ): String {
        val g = generate(expr, start, end, step, expr2, maxRows)
        val steps = Math.floor((end - start) / step + 1e-9).toInt() + 1
        val tip = if (steps > maxRows) "\n(共 $steps 行,只显示前 $maxRows 行)" else ""
        return Display.box("函数值表", listOf(g.render() + tip))
    }

    /** 手册规定:表格最多 45 行(双函数时 30 行) */
    const val MAX_ROWS_SINGLE = 45
    const val MAX_ROWS_DOUBLE = 30
}
