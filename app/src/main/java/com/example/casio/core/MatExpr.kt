package com.example.casio.core

/**
 * 矩阵 / 向量的**表达式求值器**。
 *
 * ## 为什么单独做一个求值器
 *
 * 主引擎的 `Node.eval` 返回 `Double`,整套 Parser/Node 都是标量类型。
 * 为了支持 `MatA×MatB` 而把它改成泛型值,会动到已被 700+ 断言覆盖的核心 ——
 * 风险远大于收益。
 *
 * 所以这里做一个**独立的、只处理矩阵/向量表达式的求值器**:
 *  · 表达式中出现 `Mat[A-D]` / `MatAns` / `Vct[A-D]` / `VctAns` 时才启用
 *  · 其余(纯数值)表达式仍走原来的引擎,一行未改
 *  · 矩阵/向量与纯量可混合(如 `2×MatA`、`MatA+3` 里 3 视作纯量乘单位阵?不 —— 见下)
 *
 * ## 支持的写法
 *
 * | 写法 | 含义 |
 * |---|---|
 * | `MatA+MatB` / `MatA-MatB` | 加减(维度须一致) |
 * | `MatA×MatB` / `MatA*MatB` | 矩阵乘 |
 * | `2×MatA` / `MatA×2` | 纯量乘 |
 * | `MatA^2` | 幂(方阵) |
 * | `Det(MatA)` | 行列式 |
 * | `Trn(MatA)` | 转置 |
 * | `Inv(MatA)` | 逆 |
 * | `Identity(3)` | 单位阵 |
 * | `Abs(MatA)` | 各元素取绝对值 |
 * | `VctA+VctB` / `VctA-VctB` | 向量加减 |
 * | `VctA·VctB` / `Dot(VctA,VctB)` | 内积 |
 * | `Cross(VctA,VctB)` | 叉积(三维) |
 * | `Abs(VctA)` | 模 |
 * | `Angle(VctA,VctB)` | 夹角(按设定角度单位) |
 * | `UnitV(VctA)` | 单位向量 |
 *
 * **注意**:`MatA+3` 这类"矩阵加纯量"**不支持**(数学上无定义),会明确报错,
 * 而不是悄悄按纯量乘处理。
 */
object MatExpr {

    /** 求值结果:标量 / 矩阵 / 向量 */
    sealed interface Value {
        data class Scalar(val v: Double) : Value
        data class Mat(val m: Matrix) : Value
        data class Vec(val v: Vector) : Value
    }

    /** 表达式中是否含矩阵/向量变量 */
    fun mentions(text: String): Boolean {
        return Regex("\\b(Mat[A-D]|MatAns|Vct[A-D]|VctAns)\\b").containsMatchIn(text) ||
            Regex("\\b(Det|Trn|Inv|Identity|Cross|Dot|Angle|UnitV)\\s*\\(", RegexOption.IGNORE_CASE)
                .containsMatchIn(text)
    }

    /**
     * 求值。
     * @param text 表达式
     * @param mats 已存的矩阵(键为 MatA 这类名字)
     * @param vecs 已存的向量
     * @param angleUnit 角度单位(供 Angle 用)
     */
    fun eval(
        text: String,
        mats: Map<String, Matrix>,
        vecs: Map<String, Vector>,
        angleUnit: AngleUnit = AngleUnit.DEGREE
    ): Value {
        val p = EMul(tokenize(text), mats, vecs, angleUnit)
        val v = p.parseExpression()
        p.expectEnd()
        return v
    }

    // ---------------- 词法 ----------------

    private sealed interface Tok {
        data class Num(val v: Double) : Tok
        data class Name(val s: String) : Tok
        data class Op(val s: String) : Tok
    }

    private fun tokenize(text: String): List<Tok> {
        val out = mutableListOf<Tok>()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                ch.isWhitespace() -> i++
                ch.isDigit() || ch == '.' -> {
                    val start = i
                    while (i < text.length && (text[i].isDigit() || text[i] == '.')) i++
                    // 科学计数法
                    if (i < text.length && (text[i] == 'e' || text[i] == 'E')) {
                        val save = i
                        i++
                        if (i < text.length && (text[i] == '+' || text[i] == '-')) i++
                        if (i < text.length && text[i].isDigit()) {
                            while (i < text.length && text[i].isDigit()) i++
                        } else {
                            i = save
                        }
                    }
                    val s = text.substring(start, i)
                    out.add(Tok.Num(s.toDoubleOrNull() ?: throw ParseError("无法识别的数字:$s")))
                }
                ch.isLetter() || ch == '_' -> {
                    val start = i
                    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                    out.add(Tok.Name(text.substring(start, i)))
                }
                ch == '×' || ch == '·' -> {
                    out.add(Tok.Op(if (ch == '·') "·" else "*")); i++
                }
                ch == '−' -> {
                    out.add(Tok.Op("-")); i++
                }
                ch == '^' || ch == '+' || ch == '-' || ch == '*' || ch == '/' ||
                    ch == '(' || ch == ')' || ch == ',' -> {
                    out.add(Tok.Op(ch.toString())); i++
                }
                else -> throw ParseError("不支持的字符: $ch")
            }
        }
        return out
    }

    // ---------------- 语法 ----------------

    private class EMul(
        private val toks: List<Tok>,
        private val mats: Map<String, Matrix>,
        private val vecs: Map<String, Vector>,
        private val angleUnit: AngleUnit
    ) {
        private var pos = 0
        private fun peek(): Tok? = toks.getOrNull(pos)
        private fun next(): Tok = toks.getOrNull(pos++) ?: throw ParseError("算式不完整")
        private fun eatOp(s: String): Boolean {
            val t = peek()
            if (t is Tok.Op && t.s == s) {
                pos++
                return true
            }
            return false
        }

        fun expectEnd() {
            if (pos != toks.size) throw ParseError("算式结尾有多余内容")
        }

        fun parseExpression(): Value {
            var left = parseTerm()
            while (true) {
                when {
                    eatOp("+") -> left = addValues(left, parseTerm())
                    eatOp("-") -> left = subValues(left, parseTerm())
                    else -> return left
                }
            }
        }

        private fun parseTerm(): Value {
            var left = parseUnary()
            while (true) {
                when {
                    eatOp("*") -> left = mulValues(left, parseUnary())
                    eatOp("·") -> left = dotValues(left, parseUnary())
                    eatOp("/") -> {
                        val r = parseUnary()
                        val rs = (r as? Value.Scalar)?.v
                            ?: throw ParseError("矩阵/向量不能作除数")
                        left = scaleValue(left, 1.0 / rs)
                    }
                    else -> return left
                }
            }
        }

        private fun parseUnary(): Value {
            if (eatOp("-")) return scaleValue(parseUnary(), -1.0)
            if (eatOp("+")) return parseUnary()
            return parsePower()
        }

        private fun parsePower(): Value {
            val base = parseAtom()
            if (eatOp("^")) {
                val e = parseUnary()
                val n = (e as? Value.Scalar)?.v
                    ?: throw ParseError("幂指数必须是纯量")
                if (n != Math.floor(n)) throw ParseError("矩阵/向量的幂指数必须是整数")
                return when (base) {
                    is Value.Mat -> Value.Mat(Mat.pow(base.m, n.toInt()))
                    is Value.Vec -> throw ParseError("向量不支持幂运算")
                    is Value.Scalar -> Value.Scalar(Math.pow(base.v, n))
                }
            }
            return base
        }

        private fun parseAtom(): Value {
            val t = peek() ?: throw ParseError("算式不完整")
            if (t is Tok.Op && t.s == "(") {
                pos++
                val v = parseExpression()
                if (!eatOp(")")) throw ParseError("括号不配对")
                return v
            }
            if (t is Tok.Num) {
                pos++
                return Value.Scalar(t.v)
            }
            if (t is Tok.Name) {
                pos++
                // 函数调用
                if (peek() is Tok.Op && (peek() as Tok.Op).s == "(") {
                    pos++
                    val args = mutableListOf<Value>()
                    if (!(peek() is Tok.Op && (peek() as Tok.Op).s == ")")) {
                        args.add(parseExpression())
                        while (eatOp(",")) args.add(parseExpression())
                    }
                    if (!eatOp(")")) throw ParseError("括号不配对")
                    return callFunction(t.s, args)
                }
                // 变量
                return lookup(t.s)
            }
            throw ParseError("算式不完整")
        }

        private fun lookup(name: String): Value {
            mats[name]?.let { return Value.Mat(it) }
            vecs[name]?.let { return Value.Vec(it) }
            // 大小写不敏感再试一次
            mats.entries.firstOrNull { it.key.equals(name, true) }?.let { return Value.Mat(it.value) }
            vecs.entries.firstOrNull { it.key.equals(name, true) }?.let { return Value.Vec(it.value) }
            throw ParseError("未知名称: $name(该矩阵/向量还没录入数据)")
        }

        private fun callFunction(name: String, args: List<Value>): Value {
            fun need(n: Int) {
                if (args.size != n) throw ParseError("$name 需要 $n 个参数,实际给了 ${args.size} 个")
            }
            return when (name.lowercase()) {
                "det" -> {
                    need(1)
                    val m = (args[0] as? Value.Mat)?.m ?: throw ParseError("Det 的参数必须是矩阵")
                    Value.Scalar(Mat.det(m))
                }
                "trn" -> {
                    need(1)
                    val m = (args[0] as? Value.Mat)?.m ?: throw ParseError("Trn 的参数必须是矩阵")
                    Value.Mat(Mat.transpose(m))
                }
                "inv" -> {
                    need(1)
                    val m = (args[0] as? Value.Mat)?.m ?: throw ParseError("Inv 的参数必须是矩阵")
                    Value.Mat(Mat.inverse(m))
                }
                "identity" -> {
                    need(1)
                    val n = (args[0] as? Value.Scalar)?.v ?: throw ParseError("Identity 的参数必须是纯量")
                    Value.Mat(Matrix.identity(n.toInt()))
                }
                "abs" -> {
                    need(1)
                    when (val a = args[0]) {
                        is Value.Mat -> Value.Mat(Mat.absAll(a.m))
                        is Value.Vec -> Value.Scalar(Vec.norm(a.v))
                        is Value.Scalar -> Value.Scalar(Math.abs(a.v))
                    }
                }
                "dot" -> {
                    need(2)
                    val a = (args[0] as? Value.Vec)?.v ?: throw ParseError("Dot 的参数必须是向量")
                    val b = (args[1] as? Value.Vec)?.v ?: throw ParseError("Dot 的参数必须是向量")
                    Value.Scalar(Vec.dot(a, b))
                }
                "cross" -> {
                    need(2)
                    val a = (args[0] as? Value.Vec)?.v ?: throw ParseError("Cross 的参数必须是向量")
                    val b = (args[1] as? Value.Vec)?.v ?: throw ParseError("Cross 的参数必须是向量")
                    Value.Vec(Vec.cross(a, b))
                }
                "angle" -> {
                    need(2)
                    val a = (args[0] as? Value.Vec)?.v ?: throw ParseError("Angle 的参数必须是向量")
                    val b = (args[1] as? Value.Vec)?.v ?: throw ParseError("Angle 的参数必须是向量")
                    Value.Scalar(Vec.toAngleUnit(Vec.angle(a, b), angleUnit))
                }
                "unitv" -> {
                    need(1)
                    val a = (args[0] as? Value.Vec)?.v ?: throw ParseError("UnitV 的参数必须是向量")
                    Value.Vec(Vec.unit(a))
                }
                else -> throw ParseError("不认识的矩阵/向量函数: $name")
            }
        }

        // ---- 值之间的运算 ----

        private fun addValues(a: Value, b: Value): Value = when {
            a is Value.Mat && b is Value.Mat -> Value.Mat(Mat.add(a.m, b.m))
            a is Value.Vec && b is Value.Vec -> Value.Vec(Vec.add(a.v, b.v))
            a is Value.Scalar && b is Value.Scalar -> Value.Scalar(a.v + b.v)
            else -> throw ParseError("不能相加:${kindOf(a)} 与 ${kindOf(b)}")
        }

        private fun subValues(a: Value, b: Value): Value = when {
            a is Value.Mat && b is Value.Mat -> Value.Mat(Mat.sub(a.m, b.m))
            a is Value.Vec && b is Value.Vec -> Value.Vec(Vec.sub(a.v, b.v))
            a is Value.Scalar && b is Value.Scalar -> Value.Scalar(a.v - b.v)
            else -> throw ParseError("不能相减:${kindOf(a)} 与 ${kindOf(b)}")
        }

        private fun mulValues(a: Value, b: Value): Value = when {
            a is Value.Mat && b is Value.Mat -> Value.Mat(Mat.mul(a.m, b.m))
            a is Value.Mat && b is Value.Scalar -> Value.Mat(Mat.scale(a.m, b.v))
            a is Value.Scalar && b is Value.Mat -> Value.Mat(Mat.scale(b.m, a.v))
            a is Value.Vec && b is Value.Scalar -> Value.Vec(Vec.scale(a.v, b.v))
            a is Value.Scalar && b is Value.Vec -> Value.Vec(Vec.scale(b.v, a.v))
            a is Value.Scalar && b is Value.Scalar -> Value.Scalar(a.v * b.v)
            else -> throw ParseError("不能相乘:${kindOf(a)} 与 ${kindOf(b)}")
        }

        private fun dotValues(a: Value, b: Value): Value = when {
            a is Value.Vec && b is Value.Vec -> Value.Scalar(Vec.dot(a.v, b.v))
            a is Value.Scalar && b is Value.Scalar -> Value.Scalar(a.v * b.v)
            else -> throw ParseError("「·」只能用于两个向量之间")
        }

        private fun scaleValue(v: Value, k: Double): Value = when (v) {
            is Value.Mat -> Value.Mat(Mat.scale(v.m, k))
            is Value.Vec -> Value.Vec(Vec.scale(v.v, k))
            is Value.Scalar -> Value.Scalar(v.v * k)
        }

        private fun kindOf(v: Value): String = when (v) {
            is Value.Mat -> "矩阵"
            is Value.Vec -> "向量"
            is Value.Scalar -> "纯量"
        }
    }

    /** 结果 → 显示文本 */
    fun format(v: Value, exactMath: Boolean = false): String = when (v) {
        is Value.Scalar -> Display.fmt(v.v)
        is Value.Mat -> Mat.format(v.m)
        is Value.Vec -> Vec.format(v.v)
    }

    /** 结果的类型名(供输出里标注) */
    fun kindOf(v: Value): String = when (v) {
        is Value.Scalar -> "纯量"
        is Value.Mat -> "矩阵 ${v.m.rows}×${v.m.cols}"
        is Value.Vec -> "向量 ${v.v.dim} 维"
    }
}
