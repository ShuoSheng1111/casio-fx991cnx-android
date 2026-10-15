package com.example.casio.core

/**
 * 表达式语法树 + 递归下降解析器。
 *
 * 与 Python 版严格对齐的几条语义(都有回归测试守着):
 *   - 隐式乘号: 2x、3(x+1)、2sin(pi/6)、x y
 *   - 幂的优先级高于一元负号:-x^2 = -(x^2) = -9(x=3)
 *   - 幂右结合:2^3^2 = 2^(3^2) = 512
 *   - 任何单个字母都是合法变量,未赋值时才当作未知量去解方程
 */
sealed class Node {
    abstract fun eval(env: Map<String, Double>): Double
}

class NumNode(val value: Double, val exact: Rational? = null) : Node() {
    override fun eval(env: Map<String, Double>): Double = value
}

class ConstNode(val name: String) : Node() {
    override fun eval(env: Map<String, Double>): Double =
        Lexer.CONSTANTS[name] ?: throw ParseError("未知常量: $name")
}

class VarNode(val name: String) : Node() {
    override fun eval(env: Map<String, Double>): Double =
        env[name] ?: throw ParseError("未知变量: $name")
}

class UnaryNode(val op: String, val operand: Node) : Node() {
    override fun eval(env: Map<String, Double>): Double {
        val v = operand.eval(env)
        return if (op == "-") -v else v
    }
}

class BinaryNode(val op: String, val left: Node, val right: Node) : Node() {
    override fun eval(env: Map<String, Double>): Double {
        val a = left.eval(env)
        val b = right.eval(env)
        return when (op) {
            "+" -> a + b
            "-" -> a - b
            "*" -> a * b
            "/" -> {
                if (b == 0.0) throw ArithmeticException("除数不能为 0")
                a / b
            }
            "^" -> power(a, b)
            "%" -> a % b
            else -> throw ParseError("未知运算符: $op")
        }
    }

    private fun power(a: Double, b: Double): Double {
        if (a == 0.0 && b < 0) throw ArithmeticException("0 不能取负次幂")
        // 负数的非整数次幂在实数域无意义
        if (a < 0 && b != Math.floor(b)) throw ArithmeticException("负数不能开偶次方(实数域)")
        return Math.pow(a, b)
    }
}

class CallNode(val name: String, val args: List<Node>) : Node() {
    override fun eval(env: Map<String, Double>): Double {
        val v = args.map { it.eval(env) }
        return MathFns.call(name, v)
    }
}

/** 数学函数实现(对应 Python 版 _call_function) */
object MathFns {
    fun call(name: String, a: List<Double>): Double = when (name) {
        "sqrt" -> {
            requireArgs(name, a, 1)
            if (a[0] < 0) throw ArithmeticException("负数不能开平方(实数域)")
            Math.sqrt(a[0])
        }
        "abs" -> { requireArgs(name, a, 1); Math.abs(a[0]) }
        "exp" -> { requireArgs(name, a, 1); Math.exp(a[0]) }
        "ln" -> { requireArgs(name, a, 1); ln(a[0]) }
        "log10" -> { requireArgs(name, a, 1); ln(a[0]) / ln(10.0) }
        "log2" -> { requireArgs(name, a, 1); ln(a[0]) / ln(2.0) }
        "log" -> if (a.size == 1) ln(a[0]) / ln(10.0) else ln(a[0]) / ln(a[1])
        "sin" -> { requireArgs(name, a, 1); Math.sin(a[0]) }
        "cos" -> { requireArgs(name, a, 1); Math.cos(a[0]) }
        "tan" -> { requireArgs(name, a, 1); Math.tan(a[0]) }
        "asin" -> { requireArgs(name, a, 1); domain(name, a[0]); Math.asin(a[0]) }
        "acos" -> { requireArgs(name, a, 1); domain(name, a[0]); Math.acos(a[0]) }
        "atan" -> { requireArgs(name, a, 1); Math.atan(a[0]) }
        "sinh" -> { requireArgs(name, a, 1); Math.sinh(a[0]) }
        "cosh" -> { requireArgs(name, a, 1); Math.cosh(a[0]) }
        "tanh" -> { requireArgs(name, a, 1); Math.tanh(a[0]) }
        "floor" -> { requireArgs(name, a, 1); Math.floor(a[0]) }
        "ceil" -> { requireArgs(name, a, 1); Math.ceil(a[0]) }
        "round" -> if (a.size == 1) Math.round(a[0]).toDouble() else roundTo(a[0], a[1].toInt())
        "sign" -> { requireArgs(name, a, 1); Math.signum(a[0]) }
        "fact" -> {
            requireArgs(name, a, 1)
            val n = a[0]
            if (n < 0 || n != Math.floor(n)) throw ParseError("阶乘只能用于非负整数")
            factorial(n.toLong()).toDouble()
        }
        "comb" -> {
            requireArgs(name, a, 2)
            comb(a[0].toLong(), a[1].toLong()).toDouble()
        }
        "perm" -> {
            requireArgs(name, a, 2)
            perm(a[0].toLong(), a[1].toLong()).toDouble()
        }
        // ---- 批次 7 收尾:真机还有的其余函数 ----
        "cbrt" -> { requireArgs(name, a, 1); Math.cbrt(a[0]) }
        "int" -> { requireArgs(name, a, 1); Math.floor(a[0]) }
        "intg" -> {
            // Intg(-3.5) = -4(向负无穷取整;手册把 Int 与 Intg 都列出)
            requireArgs(name, a, 1)
            Math.floor(a[0])
        }
        "rnd" -> {
            // Rnd(x):按 11 位有效数字四舍五入(手册用它把内部精度截到显示精度)
            requireArgs(name, a, 1)
            rndTo11(a[0])
        }
        "gcd" -> {
            requireArgs(name, a, 2)
            val x = a[0].toLong()
            val y = a[1].toLong()
            if (a[0] < 0 || a[1] < 0) throw ParseError("gcd 的输入必须是非负整数")
            gcdL(x, y).toDouble()
        }
        "lcm" -> {
            requireArgs(name, a, 2)
            val x = a[0].toLong()
            val y = a[1].toLong()
            if (a[0] < 0 || a[1] < 0) throw ParseError("lcm 的输入必须是非负整数")
            if (x == 0L || y == 0L) 0.0 else (x / gcdL(x, y) * y).toDouble()
        }
        "ran" -> { requireArgs(name, a, 0); rng.nextDouble() }
        "ranint" -> {
            // RanInt#(a,b):[a,b] 内的随机整数(a ≤ b)
            requireArgs(name, a, 2)
            val lo = a[0]
            val hi = a[1]
            if (lo > hi) throw ParseError("RanInt# 要求第一个参数不大于第二个")
            if (lo != Math.floor(lo) || hi != Math.floor(hi)) throw ParseError("RanInt# 的参数必须是整数")
            val span = (hi - lo + 1).toLong()
            if (span <= 0) throw ParseError("RanInt# 的范围太大")
            (lo.toLong() + (rng.nextDouble() * span).toLong().coerceIn(0, span - 1)).toDouble()
        }
        // Pol / Rec:手册里返回 X、Y 两个值,这里返回**主值**(与真机把第一分量存入 X 一致)
        //   Pol(x,y) → r = √(x²+y²)
        //   Rec(r,θ) → x = r·cos θ(角度单位由调用方换算,这里收弧度)
        "pol" -> {
            requireArgs(name, a, 2)
            Math.hypot(a[0], a[1])
        }
        "rec" -> {
            requireArgs(name, a, 2)
            a[0] * Math.cos(a[1])
        }
        "poltheta" -> {
            // 极角(弧度),供界面显示第二分量
            requireArgs(name, a, 2)
            Math.atan2(a[1], a[0])
        }
        else -> throw ParseError("未知函数: $name")
    }

    /** Rnd(x):按 11 位有效数字四舍五入 */
    private fun rndTo11(v: Double): Double {
        if (v == 0.0 || !v.isFinite()) return v
        val digits = 11
        val exp = Math.floor(Math.log10(Math.abs(v))).toInt()
        val scale = Math.pow(10.0, (digits - 1 - exp).toDouble())
        return Math.round(v * scale) / scale
    }

    /** 随机数发生器:可复现(自测需要) */
    private var rng = java.util.Random()
    fun reseed(seed: Long) {
        rng = java.util.Random(seed)
    }

    private fun gcdL(x: Long, y: Long): Long {
        var a = if (x < 0) -x else x
        var b = if (y < 0) -y else y
        while (b != 0L) {
            val t = a % b
            a = b
            b = t
        }
        return a
    }

    private fun requireArgs(name: String, a: List<Double>, n: Int) {
        if (a.size != n) throw ParseError("$name 需要 $n 个参数,收到 ${a.size} 个")
    }

    private fun domain(name: String, v: Double) {
        if (v < -1 || v > 1) throw ParseError("$name 的定义域是 [-1,1]")
    }

    private fun ln(v: Double): Double {
        if (v == 0.0) throw ArithmeticException("log(0) 无定义")
        if (v < 0) throw ArithmeticException("负数不能取对数(实数域)")
        return Math.log(v)
    }

    fun roundTo(v: Double, digits: Int): Double {
        val f = Math.pow(10.0, digits.toDouble())
        return Math.round(v * f) / f
    }

    fun factorial(n: Long): java.math.BigInteger {
        var r = java.math.BigInteger.ONE
        for (i in 2..n) r = r.multiply(java.math.BigInteger.valueOf(i))
        return r
    }

    fun comb(n: Long, k: Long): java.math.BigInteger {
        if (k < 0 || n < 0 || k > n) return java.math.BigInteger.ZERO
        var r = java.math.BigInteger.ONE
        for (i in 0 until k) {
            r = r.multiply(java.math.BigInteger.valueOf(n - i))
            r = r.divide(java.math.BigInteger.valueOf(i + 1))
        }
        return r
    }

    fun perm(n: Long, k: Long): java.math.BigInteger {
        var r = java.math.BigInteger.ONE
        for (i in 0 until k) r = r.multiply(java.math.BigInteger.valueOf(n - i))
        return r
    }
}

object Parser {

    fun parse(text: String): Node {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw ParseError("空算式")
        val tokens = Lexer.tokenize(trimmed)
        if (tokens.isEmpty()) throw ParseError("空算式")
        return ParseState(tokens).parseAll()
    }

    /** 找出表达式里第一个出现的变量名(用于「未赋值就解方程」) */
    fun firstVariable(text: String): String {
        val tokens = try {
            Lexer.tokenize(text)
        } catch (e: ParseError) {
            return "x"
        }
        for (t in tokens) {
            if (t is Token.Name && t.text.length == 1 && t.text[0].isLetter()) {
                if (t.text in Lexer.HARD_CONSTANTS) continue
                if (Lexer.FUNC_ALIAS.containsKey(t.text)) continue
                return t.text.lowercase()
            }
        }
        return "x"
    }

    fun collectVariables(node: Node, out: MutableSet<String> = mutableSetOf()): Set<String> {
        when (node) {
            is VarNode -> out.add(node.name)
            is UnaryNode -> collectVariables(node.operand, out)
            is BinaryNode -> {
                collectVariables(node.left, out)
                collectVariables(node.right, out)
            }
            is CallNode -> node.args.forEach { collectVariables(it, out) }
            else -> {}   // 常量与数字节点不含变量
        }
        return out
    }
}

private class ParseState(private val tokens: List<Token>) {
    private var pos = 0

    private fun peek(): Token? = tokens.getOrNull(pos)
    private fun peekAt(k: Int): Token? = tokens.getOrNull(pos + k)
    private fun advance(): Token = tokens[pos++]

    private fun isMulChain(): Boolean {
        val t = peek() ?: return false
        return when (t) {
            is Token.Name -> true
            is Token.Num -> true
            is Token.Op -> t.text == "("
        }
    }

    fun parseAll(): Node {
        val node = parseExpression()
        if (pos != tokens.size) throw ParseError("算式结尾有多余内容: ${tokens[pos]}")
        return node
    }

    private fun parseExpression(): Node {
        var node = parseTerm()
        while (true) {
            val t = peek()
            if (t is Token.Op && (t.text == "+" || t.text == "-")) {
                advance()
                node = BinaryNode(t.text, node, parseTerm())
            } else {
                return node
            }
        }
    }

    /**
     * 省略乘号的乘法(隐式乘号)。
     *
     * 手册的优先顺序:隐式乘号(7) **高于** 乘除(10),
     * 因此 `1/2x` = `1/(2x)`、`6/2π` = `6/(2π)`、`6/2(1+2)` = `6/(2(1+2))`。
     * 这也是真机"自动插入括号"的行为来源。
     */
    private fun parseImplicitMul(): Node {
        var node = parseUnary()
        while (isMulChain()) {
            node = BinaryNode("*", node, parseUnary())
        }
        return node
    }

    private fun parseTerm(): Node {
        var node = parseImplicitMul()
        while (true) {
            val t = peek()
            when {
                t is Token.Op && (t.text == "*" || t.text == "/" || t.text == "%") -> {
                    advance()
                    // 右操作数走隐式乘号层,这样 `6/2x` = `6/(2x)`(手册优先顺序第 7 级)
                    node = BinaryNode(t.text, node, parseImplicitMul())
                }
                else -> return node
            }
        }
    }

    private fun parseUnary(): Node {
        val t = peek()
        if (t is Token.Op && (t.text == "+" || t.text == "-")) {
            advance()
            val operand = parseUnary()
            if (t.text == "-") {
                if (operand is NumNode) return NumNode(-operand.value, operand.exact?.unaryMinus())
                return UnaryNode("-", operand)
            }
            return operand
        }
        return parsePower()
    }

    /** 幂:优先级高于一元负号;指数右结合 */
    private fun parsePower(): Node {
        val base = parsePostfix()
        val t = peek()
        if (t is Token.Op && t.text == "^") {
            advance()
            val exponent = parseUnary()
            return BinaryNode("^", base, exponent)
        }
        return base
    }

    private fun parsePostfix(): Node {
        var node = parseAtom()
        while (true) {
            val t = peek()
            if (t is Token.Op && t.text == "!") {
                advance()
                node = CallNode("fact", listOf(node))
            } else {
                return node
            }
        }
    }

    private fun parseAtom(): Node {
        val t = peek() ?: throw ParseError("算式不完整")
        when (t) {
            is Token.Num -> {
                advance()
                val exact = exactOf(t.value)
                return NumNode(t.value, exact)
            }
            is Token.Name -> {
                val name = t.text
                val nxt = peekAt(1)
                if (name in Lexer.PAREN_FUNCS && nxt is Token.Op && nxt.text == "(") {
                    advance()
                    expectOp("(")
                    val args = mutableListOf(parseExpression())
                    while (peek() is Token.Op && (peek() as Token.Op).text == ",") {
                        advance()
                        args.add(parseExpression())
                    }
                    expectOp(")")
                    return CallNode(Lexer.FUNC_ALIAS[name] ?: name, args)
                }
                if (Lexer.FUNC_ALIAS.containsKey(name) && name !in Lexer.HARD_CONSTANTS) {
                    advance()
                    return CallNode(Lexer.FUNC_ALIAS.getValue(name), listOf(parsePower()))
                }
                if (name in Lexer.HARD_CONSTANTS) {
                    advance()
                    return ConstNode(name)
                }
                // 无参函数:ran / random(真机写作 Ran#,也接受 ran())
                if (name.equals("ran", true) || name.equals("random", true)) {
                    advance()
                    if (peek() is Token.Op && (peek() as Token.Op).text == "(") {
                        advance()
                        expectOp(")")
                    }
                    return CallNode("ran", emptyList())
                }
                if (name.length == 1 && name[0].isLetter()) {
                    advance()
                    return VarNode(name.lowercase())
                }
                throw ParseError("未知名称: $name")
            }
            is Token.Op -> {
                if (t.text == "(") {
                    advance()
                    val inner = parseExpression()
                    expectOp(")")
                    return inner
                }
                throw ParseError("无法识别的记号: ${t.text}")
            }
        }
    }

    private fun expectOp(ch: String) {
        val t = peek()
        if (t !is Token.Op || t.text != ch) throw ParseError("缺少 '$ch'")
        advance()
    }

    private fun exactOf(v: Double): Rational? =
        if (v == Math.floor(v) && Math.abs(v) < 1e15) Rational.of(v.toLong()) else null
}
