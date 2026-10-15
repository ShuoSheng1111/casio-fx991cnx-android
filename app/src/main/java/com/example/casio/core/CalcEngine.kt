package com.example.casio.core

/**
 * 界面调度层:把用户输入分派到求值 / 解方程 / 方程组 / 命令。
 * 对应 Python 版的 process_line / process_command。
 */
/**
 * 界面里正在编辑的积分式(上下限与主体分开保存,便于上下键切换插槽)。
 * 放在类外,这样 "CalcEngine.Integral" 可以直接当类型使用。
 */
data class Integral(val lower: String, val upper: String, val body: String, val dVar: String)

class CalcEngine {

    /** 变量表(:set x=3 后,输入 2x+1 直接算) */
    val env = mutableMapOf<String, Double>()

    /**
     * 已录入的矩阵与向量(界面在矩阵/向量编辑器里写这里)。
     * 有了它们,算式栏里就能直接写 `MatA*MatB`、`VctA·VctB` 这类式子。
     */
    val matrixEnv = mutableMapOf<String, Matrix>()
    val vectorEnv = mutableMapOf<String, Vector>()

    /**
     * 计算器设定(SETUP)。默认为 fx-991CN X 的初始缺省值。
     * 结果输出走 [fmtResult],因此改动设定会直接影响显示。
     */
    var settings: Settings = Settings()

    /** 当前是否处于统计模式(界面按钮会写这个标志) */
    var statMode: Boolean = false
        set(value) {
            field = value
            // 手册:无论何时进入统计模式,计算器都会自动切换到「线性输入/小数输出」
            if (value) {
                settings = settings.copy(ioMode = IoMode.LINEAR_DEC)
            }
        }

    /** 结果格式化:按当前 SETUP 设定输出(支持分数/π 等精确形式) */
    fun fmtResult(v: Double, allowExact: Boolean = true): String {
        if (allowExact && settings.mathOutput) {
            Fmt.exactForm(v, settings)?.let { return it }
        }
        return Fmt.number(v, settings, mathExact = allowExact)
    }

    /**
     * 只求数值(供 LCD 竖排显示用)。
     * 若输入不是"能直接算出单个数值"的形式(方程组、命令、报错),
     * 返回 null,调用方退回文本显示。
     */
    fun evalValue(expr: String): Double? {
        val t = expr.trim()
        if (t.isEmpty() || t.startsWith(":")) return null
        if (t.contains('=')) return null
        if (t.contains(',')) return null
        return try {
            Parser.parse(t).eval(env)
        } catch (e: Exception) {
            null
        }
    }

    /** 处理一行输入,返回要显示的文本 */
    /**
     * 矩阵 / 向量表达式的求值入口。
     * 把结果写回 MatAns / VctAns,并按类型给出带标注的输出。
     */
    private fun matrixExpression(text: String): String {
        return try {
            val v = MatExpr.eval(text, matrixEnv, vectorEnv, settings.angle)
            // 结果存储器:手册里矩阵结果进 MatAns、向量结果进 VctAns
            when (v) {
                is MatExpr.Value.Mat -> matrixEnv["MatAns"] = v.m
                is MatExpr.Value.Vec -> vectorEnv["VctAns"] = v.v
                is MatExpr.Value.Scalar -> Unit
            }
            Display.box(
                "矩阵 / 向量运算",
                listOf(
                    text,
                    "",
                    MatExpr.format(v, settings.mathOutput),
                    "",
                    MatExpr.kindOf(v)
                )
            )
        } catch (e: ParseError) {
            "错误: ${e.message}"
        } catch (e: IllegalArgumentException) {
            "错误: ${e.message}"
        } catch (e: Exception) {
            "错误: ${e.message}"
        }
    }

    fun process(textIn: String): String {
        val text = textIn.trim()
        if (text.isEmpty()) return ""
        val lower = text.lowercase()

        if (lower.startsWith(":")) return command(text)

        // 矩阵 / 向量表达式:含 MatA..MatD / VctA..VctD 或专用函数时走专用求值器。
        // 纯数值表达式不受影响(仍走原来的标量引擎,一行未改)。
        if (MatExpr.mentions(text)) return matrixExpression(text)

        // 方程组:每一段都含 '=' 且用逗号分隔
        if (looksLikeSystem(text)) {
            val eqs = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            return try {
                val r = Solver.solveLinearSystem(eqs)
                Display.showSystem(r, eqs)
            } catch (e: SolveError) {
                "错误: ${e.message}"
            } catch (e: ParseError) {
                "错误: ${e.message}"
            }
        }

        if (text.contains("=")) {
            return solveAndFormat(text)
        }

        // 不含 '=':先试求值;若含未赋值变量,再当作 "=0" 解方程
        return try {
            val v = Parser.parse(text).eval(env)
            Display.showCalculation(text, v)
        } catch (e: ParseError) {
            val names = runCatching { Parser.collectVariables(Parser.parse(text)) }.getOrDefault(emptySet())
            val unknown = names - env.keys
            if (unknown.isNotEmpty()) solveAndFormat("$text=0") else "错误: ${e.message}"
        } catch (e: ArithmeticException) {
            "错误: ${e.message}"
        }
    }

    /**
     * SETUP 命令。对应 fx-991CN X 的「配置计算器设定」(14 项)。
     *   :setup                      显示全部当前设定
     *   :setup io 1|2|3|4            输入/输出格式
     *   :setup angle d|r|g           角度单位
     *   :setup display fix<n>|sci<n>|norm1|norm2
     *   :setup eng on|off            工程符号
     *   :setup frac mixed|improper   分数结果
     *   :setup complex rect|polar    复数显示
     *   :setup freq on|off           统计 Freq 栏
     *   :setup eqncomplex on|off     方程解是否用复数
     *   :setup table f|fg            表格函数个数
     *   :setup dot period|comma      小数点显示
     *   :setup sep on|off            数字分隔符
     *   :setup font normal|small     多行字体
     *   :setup reset                 恢复初始缺省
     */
    private fun setupCommand(args: List<String>, raw: String): String {
        if (args.isEmpty()) return Display.box("设定(SETUP)", setupLines())

        val key = args[0].lowercase()
        val v = args.getOrNull(1)?.lowercase() ?: ""

        // reset 不需要取值,先处理
        if (key == "reset") {
            settings = Settings()
            return Display.box("已恢复初始缺省", setupLines())
        }

        fun needValue(): String? = if (v.isEmpty()) "用法: :setup $key <值>" else null
        needValue()?.let { return it }

        val s = settings
        return try {
            settings = when (key) {
                "io" -> when (v) {
                    "1", "math" -> s.copy(ioMode = IoMode.MATH_MATH)
                    "2", "mathdec" -> s.copy(ioMode = IoMode.MATH_DEC)
                    "3", "linear" -> s.copy(ioMode = IoMode.LINEAR_LINEAR)
                    "4", "lineardec" -> s.copy(ioMode = IoMode.LINEAR_DEC)
                    else -> return "io 取值: 1(数学/数学) 2(数学/小数) 3(线性/线性) 4(线性/小数)"
                }
                "angle" -> when (v) {
                    "d", "deg" -> s.copy(angle = AngleUnit.DEGREE)
                    "r", "rad" -> s.copy(angle = AngleUnit.RADIAN)
                    "g", "gra" -> s.copy(angle = AngleUnit.GRADIAN)
                    else -> return "angle 取值: d(度) r(弧度) g(百分度)"
                }
                "display" -> when {
                    v.startsWith("fix") -> {
                        val n = v.removePrefix("fix").toIntOrNull() ?: 3
                        s.copy(display = DisplayMode.Fix(n.coerceIn(0, 9)))
                    }
                    v.startsWith("sci") -> {
                        val n = v.removePrefix("sci").toIntOrNull() ?: 5
                        s.copy(display = DisplayMode.Sci(n.coerceIn(0, 9)))
                    }
                    v == "norm1" -> s.copy(display = DisplayMode.Norm(1))
                    v == "norm2" -> s.copy(display = DisplayMode.Norm(2))
                    else -> return "display 取值: fix0~fix9 / sci0~sci9 / norm1 / norm2"
                }
                "eng" -> s.copy(engineering = v == "on")
                "frac" -> when (v) {
                    "mixed" -> s.copy(mixedFraction = true)
                    "improper" -> s.copy(mixedFraction = false)
                    else -> return "frac 取值: mixed(带分数) improper(假分数)"
                }
                "complex" -> when (v) {
                    "polar" -> s.copy(complexPolar = true)
                    "rect", "abi" -> s.copy(complexPolar = false)
                    else -> return "complex 取值: rect(a+bi) polar(r∠θ)"
                }
                "freq" -> s.copy(statFreq = v == "on")
                "eqncomplex" -> s.copy(eqnComplex = v == "on")
                "table" -> when (v) {
                    "f" -> s.copy(tableTwoFuncs = false)
                    "fg" -> s.copy(tableTwoFuncs = true)
                    else -> return "table 取值: f / fg"
                }
                "dot" -> when (v) {
                    "period" -> s.copy(decimalComma = false)
                    "comma" -> s.copy(decimalComma = true)
                    else -> return "dot 取值: period(句点) comma(逗号)"
                }
                "sep" -> s.copy(digitSeparator = v == "on")
                "font" -> when (v) {
                    "normal" -> s.copy(smallFont = false)
                    "small" -> s.copy(smallFont = true)
                    else -> return "font 取值: normal(4行) small(6行)"
                }
                "reset" -> Settings()
                else -> return "未知设定项: $key(直接输入 :setup 可看全部)"
            }
            Display.box("已更新设定", setupLines())
        } catch (e: Exception) {
            "错误: ${e.message}"
        }
    }

    /** 当前设定的文本清单 */
    private fun setupLines(): List<String> {
        val s = settings
        val disp = when (val d = s.display) {
            is DisplayMode.Fix -> "位数(Fix ${d.digits})"
            is DisplayMode.Sci -> "科学(Sci ${d.digits})"
            is DisplayMode.Norm -> "常规(Norm ${d.variant})"
        }
        return listOf(
            "输入/输出   ${s.ioMode.label}",
            "角度单位    ${s.angle.label}",
            "显示格式    $disp",
            "工程符号    ${if (s.engineering) "开" else "关"}",
            "分数结果    ${if (s.mixedFraction) "带分数" else "假分数"}",
            "复数        ${if (s.complexPolar) "r∠θ" else "a+bi"}",
            "统计 Freq   ${if (s.statFreq) "开" else "关"}",
            "方程用复数  ${if (s.eqnComplex) "开" else "关"}",
            "表格        ${if (s.tableTwoFuncs) "f(x),g(x)" else "f(x)"}",
            "小数点      ${if (s.decimalComma) "逗号" else "句点"}",
            "数字分隔符  ${if (s.digitSeparator) "开" else "关"}",
            "多行字体    ${if (s.smallFont) "小(6行)" else "普通(4行)"}",
            "语言        ${if (s.chinese) "中文" else "English"}",
            "对比度      ${s.contrast}"
        )
    }

    private fun solveAndFormat(equation: String): String = try {
        val varHint = Parser.firstVariable(equation)
        val r = Solver.solve(equation, varHint = varHint)
        Display.showEquation(r, equation)
    } catch (e: SolveError) {
        "错误: ${e.message}"
    } catch (e: ParseError) {
        "错误: ${e.message}"
    } catch (e: ArithmeticException) {
        "错误: ${e.message}"
    }

    private fun looksLikeSystem(text: String): Boolean {
        if (!text.contains(",")) return false
        val parts = text.split(",").map { it.trim() }
        return parts.size >= 2 && parts.all { it.contains("=") }
    }

    private fun command(text: String): String {
        val parts = text.substring(1).trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return HELP
        val cmd = parts[0].lowercase()
        val args = parts.drop(1)
        return when (cmd) {
            "help", "h", "?" -> HELP
            "setup" -> setupCommand(args, text)
            "set" -> {
                val a = args.firstOrNull() ?: return "用法: :set x=3"
                val eq = a.indexOf('=')
                if (eq <= 0) return "用法: :set x=3"
                val name = a.substring(0, eq).trim()
                val value = try {
                    Parser.parse(a.substring(eq + 1)).eval(env)
                } catch (e: Exception) {
                    return "错误: ${e.message}"
                }
                env[name] = value
                "$name = ${Display.fmt(value)}"
            }
            "vars" -> if (env.isEmpty()) "(没有已赋值的变量)" else
                env.entries.joinToString("\n") { "${it.key} = ${Display.fmt(it.value)}" }
            "diff" -> {
                if (args.isEmpty()) return "用法: :diff 表达式 [变量]"
                val expr = args[0]
                val v = args.getOrNull(1) ?: Parser.firstVariable(expr)
                try {
                    val text = Calculus.derivativeText(expr, v)
                    Display.box("符号求导", listOf("d/d$v ($expr)", "= $text"))
                } catch (e: Exception) {
                    "错误: ${e.message}"
                }
            }
            "int" -> {
                // 允许只有命令、限或主体尚未填完,此时只做标注式预览
                val rest = text.substringAfter("int", "").trimStart()
                val m = INT_RE.find(":int $rest")
                val lo = m?.groupValues?.get(1) ?: ""
                val hi = m?.groupValues?.get(2) ?: ""
                val body = m?.groupValues?.get(3)?.trim() ?: ""
                val annotated = Display.integralAnnotated(":int", lo, hi, body)
                if (lo.isBlank() || hi.isBlank() || body.isBlank()) {
                    return Display.box("数值积分", annotated.split("\n") + listOf("", "请补齐下限、上限与表达式"))
                }
                try {
                    val value = Calculus.integrateValue(lo, hi, body, env)
                    Display.box(
                        "数值积分",
                        annotated.split("\n") + listOf("", "= ${Display.fmt(value)}")
                    )
                } catch (e: Exception) {
                    "错误: ${e.message}"
                }
            }
            "const" -> {
                // :const            列出全部科学常数
                // :const c          查询单个常数
                if (args.isEmpty()) Constants.listAll() else Constants.query(args[0])
            }
            "conv" -> {
                // :conv 列表                列出全部换算对
                // :conv 数值 从单位 到单位    例: :conv 1 in cm
                if (args.size == 1 && (args[0].equals("列表", true) || args[0].equals("list", true))) {
                    return MetricConv.listAll()
                }
                if (args.size < 3) {
                    return "用法: :conv 数值 从单位 到单位\n例: :conv 1 in cm   /   :conv 100 °F °C\n" +
                        "可用单位见 :conv 列表"
                }
                val v = args[0].toDoubleOrNull()
                    ?: return "错误: 第一个参数必须是数值,当前为「${args[0]}」"
                val r = MetricConv.convert(v, args[1], args[2])
                    ?: return "找不到换算对「${args[1]} → ${args[2]}」。用 :conv 列表 看全部。"
                Display.box("公制换算", listOf(r.second))
            }
            "table" -> {
                // :table f(x) 起始 结束 步长 [第二个函数]
                // 例: :table x^2 1 5 1
                if (args.size < 4) {
                    return "用法: :table 表达式 起始 结束 步长 [第二函数]\n例: :table x^2 1 5 1"
                }
                val expr = args[0].removePrefix("f(x)=").removePrefix("y=")
                val a = args[1].toDoubleOrNull() ?: return "错误: 起始值不是数值「${args[1]}」"
                val b = args[2].toDoubleOrNull() ?: return "错误: 结束值不是数值「${args[2]}」"
                val h = args[3].toDoubleOrNull() ?: return "错误: 步长不是数值「${args[3]}」"
                val second = args.getOrNull(4)
                try {
                    TableCalc.generateText(expr, a, b, h, second)
                } catch (e: Exception) {
                    "错误: ${e.message}"
                }
            }
            "calc" -> {
                // :calc 表达式 x=值 [y=值 …]   一次性代入求值(CALC 的命令形式)
                // 例: :calc x^2+y x=3 y=4
                if (args.size < 2) {
                    return "用法: :calc 表达式 变量=值 …\n例: :calc x^2+y x=3 y=4"
                }
                val expr = args[0]
                try {
                    val s = TableCalc.CalcSession(expr)
                    for (i in 1 until args.size) {
                        val kv = args[i]
                        val eq = kv.indexOf('=')
                        if (eq <= 0) return "错误: 赋值要写成 变量=值,当前为「$kv」"
                        val name = kv.substring(0, eq).trim()
                        val v = kv.substring(eq + 1).toDoubleOrNull()
                            ?: return "错误: 值不是数值「$kv」"
                        s.assign(name, v)
                    }
                    TableCalc.calcResult(s)
                } catch (e: Exception) {
                    "错误: ${e.message}"
                }
            }
            "solve" -> {
                // :solve 方程 [变量] [初值]   牛顿法求根(对应真机的 SOLVE 功能)
                // 例: :solve x^2-2=0 x 1
                if (args.isEmpty()) {
                    return "用法: :solve 方程 [变量] [初值]\n例: :solve x^2-2=0 x 1"
                }
                val eq = args[0]
                val variable = args.getOrNull(1)?.takeIf { it.isNotBlank() }
                val guess = args.getOrNull(2)?.toDoubleOrNull()
                Solver.solveByNewton(eq, variable, guess)
            }
            "lim" -> {
                // :lim <表达式> <变量> <趋近值> [left|right|both]
                // 例: :lim sin(x)/x x 0        → 1
                //      :lim 1/x x 0 right       → +∞(发散)
                //      :lim 1/x x inf           → 0
                if (args.size < 3) {
                    return "用法: :lim 表达式 变量 趋近值 [left|right|both]\n" +
                        "例: :lim sin(x)/x x 0   /   :lim 1/x x inf"
                }
                val expr = args[0]
                val variable = args[1]
                val towardText = args[2]
                val sideText = args.getOrNull(3)?.lowercase() ?: "both"
                val side = when (sideText) {
                    "left", "l", "-" -> Limit.Side.LEFT
                    "right", "r", "+" -> Limit.Side.RIGHT
                    else -> Limit.Side.BOTH
                }
                // 趋近值:数字或 ±inf
                val lower = towardText.lowercase()
                val infinite = lower == "inf" || lower == "+inf" || lower == "-inf" ||
                    lower == "∞" || lower == "+∞" || lower == "-∞"
                val negativeInf = lower == "-inf" || lower == "-∞"
                val toward = if (infinite) {
                    if (negativeInf) -1.0 else 1.0
                } else {
                    towardText.toDoubleOrNull()
                        ?: return "错误: 趋近值必须是数值或 inf,当前为「$towardText」"
                }
                val node = try {
                    Parser.parse(expr)
                } catch (e: Exception) {
                    return "错误: ${e.message}"
                }
                val envBase = env.toMap()
                val f: (Double) -> Double? = { x ->
                    try {
                        val e2 = envBase.toMutableMap()
                        e2[variable] = x
                        val y = node.eval(e2)
                        if (y.isFinite()) y else null
                    } catch (e: Exception) {
                        null
                    }
                }
                val r = Limit.compute(f, toward, side, infinite)
                val annotated = Display.limitAnnotated(expr, variable, towardText, sideText)
                annotated + "\n\n" + Limit.format(expr, variable, towardText, side, r)
            }
            "norm" -> {
                // :norm <x> <数据…>   单变量数据的正态分布 P/Q/R/'t
                // 例::norm 3 0,1,1,2,2,2,3,3,3,3,4,4,4,5
                if (args.size < 2) {
                    return "用法: :norm x 数据…   例: :norm 3 1,2,2,3,3,3"
                }
                val x = args[0].toDoubleOrNull()
                    ?: return "错误: 第一个参数必须是数值(要计算 P/Q/R 的 x 值)"
                val (list, err) = Stats.parseList(text.substringAfter("norm", "").trim().removePrefix(args[0]).trim())
                if (list == null) "错误: $err" else Stats.normalText(x, list)
            }
            "stat" -> {
                // :stat 1 数据…            → 1 变量统计
                // :stat 2 x数据 | y数据 [模型] → 2 变量统计 + 回归
                val mode = args.getOrNull(0) ?: "1"
                val rest = text.substringAfter("stat", "").trim()
                // 去掉模式位,剩下的是数据部分
                val body = rest.removePrefix(mode).trim()
                try {
                    if (mode == "1") {
                        val (list, err) = Stats.parseList(body)
                        if (list == null) "错误: ${err}" else Stats.oneVarText(list)
                    } else {
                        val sep = body.indexOf('|')
                        if (sep < 0) {
                            "用法: :stat 2 x数据 | y数据 [模型]\n模型可选 linear/quad/log/exp/power/inverse"
                        } else {
                            val left = body.substring(0, sep)
                            val rightRaw = body.substring(sep + 1).trim()
                            // 末尾可能带模型名
                            val modelKeys = mapOf(
                                "linear" to Stats.Model.LINEAR, "quad" to Stats.Model.QUADRATIC,
                                "log" to Stats.Model.LOG, "exp" to Stats.Model.EXP,
                                "power" to Stats.Model.POWER, "inverse" to Stats.Model.INVERSE
                            )
                            var model = Stats.Model.LINEAR
                            var right = rightRaw
                            for ((k, v) in modelKeys) {
                                if (rightRaw.endsWith(k)) {
                                    model = v
                                    right = rightRaw.dropLast(k.length).trim()
                                    break
                                }
                            }
                            val (xs, ex) = Stats.parseList(left)
                            val (ys, ey) = Stats.parseList(right)
                            when {
                                xs == null -> "错误(x): $ex"
                                ys == null -> "错误(y): $ey"
                                xs.size != ys.size -> "错误: x 有 ${xs.size} 个、y 有 ${ys.size} 个,个数不一致"
                                else -> Stats.twoVarText(xs, ys, model)
                            }
                        }
                    }
                } catch (e: Exception) {
                    "错误: ${e.message}"
                }
            }
            "clear" -> {
                env.clear()
                "已清空变量表"
            }
            else -> "未知命令: $cmd"
        }
    }

    companion object {
        private val INT_RE = Regex("^:int[\\s,]+([^\\s,]*)[\\s,]+([^\\s,]*)[\\s,]+(.*)$", RegexOption.IGNORE_CASE)

        /**
         * 尝试把输入解析成积分式。规范形式 ":int 下限 上限 主体",
         * 限可以是空串(用空白占位),这样界面能先给出两个空插槽再填。
         */
        fun parseInt(textIn: String): Integral? {
            val t = textIn.trimEnd()
            if (!t.lowercase().startsWith(":int")) return null
            val rest = t.substring(4)
            if (rest.isNotEmpty() && !rest[0].isWhitespace()) return null
            if (rest.isBlank()) return Integral("", "", "", "x")
            val m = INT_RE.find(t) ?: return null
            val lo = m.groupValues[1]
            val hi = m.groupValues[2]
            val body = m.groupValues[3].trim()
            return Integral(lo, hi, body, Parser.firstVariable(body))
        }

        /** 反解回规范形式,交给求解引擎 */
        fun toCanonical(i: Integral) = ":int ${i.lower} ${i.upper} ${i.body}"

        /** 积分在界面上显示成撬棍形状:∫ 右侧上标是上限、下标是下限 */
        fun renderIntegral(i: Integral, activeSlot: Int): String {
            fun bound(s: String, sub: Boolean): String {
                val trimmed = s.trim()
                if (trimmed.isEmpty()) return "◻"
                val mapped = if (sub) Display.toSubscript(trimmed) else Display.toSuperscript(trimmed)
                return mapped ?: "($trimmed)"      // 画不出上下标的(带小数)退化成括号写法
            }
            val lo = bound(i.lower, true)
            val hi = bound(i.upper, false)
            val mark = if (i.body.isBlank()) "▌" else ""
            return "\u222b$lo$hi$mark ${i.body} d${i.dVar}"
        }

        /** 拆分带样式的积分渲染:供界面把「激活的限」涂成绿色 */
        data class IntegralParts(
            val prefix: String,     // ∫
            val lower: String,      // 下限(下标)
            val upper: String,      // 上限(上标)
            val rest: String,       // 后续(主体 + dx)
            val marker: String      // 空主体时的插入标记 ▌
        )

        fun renderIntegralParts(i: Integral): IntegralParts {
            fun bound(s: String, sub: Boolean): String {
                val trimmed = s.trim()
                if (trimmed.isEmpty()) return "◻"
                val mapped = if (sub) Display.toSubscript(trimmed) else Display.toSuperscript(trimmed)
                return mapped ?: "($trimmed)"
            }
            val mark = if (i.body.isBlank()) "▌" else ""
            return IntegralParts(
                prefix = "\u222b",
                lower = bound(i.lower, true),
                upper = bound(i.upper, false),
                rest = " ${i.body} d${i.dVar}",
                marker = mark
            )
        }

        val HELP = """
【这是什么】
  一个会解方程的卡西欧式计算器。算式、方程、方程组都可以直接排。
  按键上的 = 只用来输入等号;要算结果请按绿色的 [ = 求值 ] 或 [ 解 方程 ]。

【四则与函数】
  1+2*3     四则运算,遵循先乘除后加减
  (10-4)/2  括号
  2^10      乘方;  5!  阶乘;  10%3  取余
  2x        隐式乘号:2x 就是 2 乘 x
  3(x+1)    数字后面紧跟括号也是乘法
  2sin(pi/6) 函数与数字之间同样自动相乘
  x^2-2=0   方程写法;只有一个 = 号

  可用的函数:
    sin cos tan        三角函数(弧度制)
    asin acos atan     反三角
    ln log log2        自然对数 / 常用对数 / 以 2 为底
    exp sqrt abs       指数 / 平方根 / 绝对值
    floor ceil round   向下取整 / 向上取整 / 四舍五入
    fact comb perm     阶乘 / 组合数 / 排列数
  可用的常量:
    pi(π)  e  tau  inf(∞)  °(角度,例 sin(30°))

【解方程】
  2x+3=7             一元一次
  x^2-2=0            一元二次,会给精确根式如 (1/2 + √5/2)
  x^3-6x^2+11x-6=0   三次及以上,给全部实根与复根
  x^5-x-1=0          高次方程(数值求根)
  sin(x)=0.5  e^x=2  超越方程,用牛顿法在 ±100 内找根
  2x+y=5, x-y=1      线性方程组:用逗号分隔多个方程
  未知量可以是任何单个字母,程序会自动识别你用的是 x 还是 y

【命令(用「▲ 命令」抽屉按键插入,系统键盘已取消)】
  :diff 表达式 [变量]  符号求导,返回导函数
                       例 :diff x^2*sin(x) 得 2·x·sin(x)+(x^2)·cos(x)
  :int 下限 上限 表达式  定积分(复合辛普森法数值积分)
                       例 :int 0 1 x^2 表示 ∫ 从 0 到 1 的 x²,结果 1/3
                       输出里会用小字标出哪个位置是上限、哪个是下限
  :lim 表达式 变量 趋近值 [方向]   求极限(数值法)
                       例 :lim sin(x)/x x 0 得 1;方向可写 left/right/both
                       趋近值可写数值或 inf;不收敛时会明确报"不存在"
  :set 变量=值          给变量赋值,之后可直接参与运算
                       例 :set a=5 然后输入 a*2+1 得 11
  :vars                查看当前已赋值的所有变量
  :clear               清空变量表
  :help                显示这份帮助

【小提示】
  · 二次方程给精确根式,三次以上给全部实根与复根
  · 计算历史会显示在上方黑色区域,内容变多时自动把新结果滚到顶部
  · 想修改式子中间的内容:点算式栏调出键盘,或用方向键抽屉移动光标
""".trim()
    }
}
