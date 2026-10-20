package com.example.casio.core

/**
 * 记号(tokens)与词法分析。
 * 规则逐条对齐 Python 版:
 *  - 最长的字母数字串先整体取出,再按「最长匹配」拆成函数名/常量/变量
 *    (这样 log2 不会被切成 log + 2,2 也不会被当成词的一部分)
 *  - √ 是前缀函数,° 是常量(角度 -> 弧度)
 */
sealed class Token {
    data class Num(val value: Double) : Token()
    data class Name(val text: String) : Token()
    data class Op(val text: String) : Token()
    override fun toString(): String = when (this) {
        is Num -> "Num($value)"
        is Name -> "Name($text)"
        is Op -> "Op($text)"
    }
}

class ParseError(message: String) : Exception(message)

object Lexer {

    /** 函数别名表(与 Python 版一致) */
    val FUNC_ALIAS: Map<String, String> = mapOf(
        "sin" to "sin", "cos" to "cos", "tan" to "tan",
        "asin" to "asin", "acos" to "acos", "atan" to "atan",
        "arcsin" to "asin", "arccos" to "acos", "arctan" to "atan",
        "sinh" to "sinh", "cosh" to "cosh", "tanh" to "tanh",
        "ln" to "ln", "log" to "log10", "log10" to "log10", "lg" to "log10",
        "log2" to "log2", "exp" to "exp", "sqrt" to "sqrt", "√" to "sqrt",
        "abs" to "abs", "fabs" to "abs",
        "floor" to "floor", "ceil" to "ceil", "round" to "round",
        "sign" to "sign", "sgn" to "sign",
        "fact" to "fact", "factorial" to "fact",
        "comb" to "comb", "ncr" to "comb", "perm" to "perm", "npr" to "perm",
        // 批次 7 收尾:白名单必须同步登记,否则解析器会把它们当成变量名(实测踩过:
        // 只往 MathFns 里加函数,解析阶段就报「未知名称: cbrt」)
        "cbrt" to "cbrt", "∛" to "cbrt",
        "int" to "int", "intg" to "intg",
        "rnd" to "rnd",
        "gcd" to "gcd", "lcm" to "lcm",
        // 注意:ran 是**无参**函数,不走 PAREN_FUNCS 那条需要参数的路径,
        // 由 Parser 单独处理(ran 与 ran() 都接受)
        "ranint" to "ranint",
        "pol" to "pol", "rec" to "rec", "poltheta" to "poltheta"
    )

    /** 需要括号的函数(至少 1 个参数) */
    val PAREN_FUNCS: Set<String> = FUNC_ALIAS.values.toSet()

    /** 常量:π e tau inf °(角度) */
    val CONSTANTS: Map<String, Double> = mapOf(
        "pi" to Math.PI, "π" to Math.PI,
        "e" to Math.E, "tau" to (2 * Math.PI),
        "inf" to Double.POSITIVE_INFINITY, "∞" to Double.POSITIVE_INFINITY,
        "°" to (Math.PI / 180.0)
    )

    val HARD_CONSTANTS: Set<String> = setOf("pi", "π", "e", "tau", "inf", "∞", "°")

    /** 可作未知量的单字母 */
    val VARIABLE_LETTERS: Set<Char> = "xyzntabiwh".toSet()

    private val NUMBER_RE = Regex("""(\d+\.\d*|\.\d+|\d+)(?:[eE]([+-]?\d+))?""")

    fun lookupAlias(word: String): String? =
        FUNC_ALIAS[word] ?: FUNC_ALIAS[word.lowercase()]

    fun lookupConstant(word: String): String? = when {
        CONSTANTS.containsKey(word) -> word
        CONSTANTS.containsKey(word.lowercase()) -> word.lowercase()
        else -> null
    }

    /** 把一段连续字母数字切成 变量/常量/函数 名(最长匹配) */
    fun splitLetters(word: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < word.length) {
            var best: Pair<String, Int>? = null
            var k = word.length - i
            while (k >= 1) {
                val piece = word.substring(i, i + k)
                val c = lookupConstant(piece)
                if (c != null) {
                    best = c to k
                    break
                }
                val a = lookupAlias(piece)
                if (a != null) {
                    best = a to k
                    break
                }
                if (k == 1 && piece[0].isLetter() && piece.lowercase()[0] in VARIABLE_LETTERS) {
                    best = piece.lowercase() to 1
                    break
                }
                k--
            }
            if (best == null) {
                out.add(word.substring(i))
                break
            }
            out.add(best.first)
            i += best.second
        }
        return out
    }

    fun tokenize(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        val n = text.length
        while (i < n) {
            val start = i
            val ch = text[i]
            when {
                ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n' -> i++

                ch.isDigit() || (ch == '.' && i + 1 < n && text[i + 1].isDigit()) -> {
                    val m = NUMBER_RE.find(text, i)
                        ?: throw ParseError("无法识别的数字: ${text.substring(i, minOf(i + 8, n))}")
                    tokens.add(Token.Num(m.value.toDouble()))
                    i = m.range.last + 1
                }

                ch == '√' -> {
                    tokens.add(Token.Name("sqrt"))
                    i++
                }

                ch == '°' -> {
                    tokens.add(Token.Name("°"))
                    i++
                }

                ch.isLetter() || ch == '_' -> {
                    var j = i
                    while (j < n && (text[j].isLetter() || text[j].isDigit())) j++
                    val word = text.substring(i, j)
                    when {
                        word.length == 1 -> {
                            tokens.add(
                                if (CONSTANTS.containsKey(word)) Token.Name(word)
                                else Token.Name(word.lowercase())
                            )
                        }
                        FUNC_ALIAS.containsKey(word) -> tokens.add(Token.Name(FUNC_ALIAS.getValue(word)))
                        CONSTANTS.containsKey(word) -> tokens.add(Token.Name(word))
                        else -> splitLetters(word).forEach { tokens.add(Token.Name(it)) }
                    }
                    i = j
                }

                // 排版用运算符:归一成 ASCII。键盘上放「×」比「*」好看,
                // 但解析器只认 ASCII,所以必须在词法这一层转换(否则按下就报"不支持的字符")。
                ch == '×' || ch == '⋅' || ch == '·' -> {
                    tokens.add(Token.Op("*"))
                    i++
                }

                ch == '÷' -> {
                    tokens.add(Token.Op("/"))
                    i++
                }

                ch == '−' || ch == '–' -> {
                    tokens.add(Token.Op("-"))
                    i++
                }

                ch == '+' || ch == '-' || ch == '*' || ch == '/' || ch == '^' ||
                    ch == '%' || ch == '!' || ch == '(' || ch == ')' ||
                    ch == ',' || ch == '=' -> {
                    tokens.add(Token.Op(ch.toString()))
                    i++
                }

                else -> throw ParseError("不支持的字符: $ch")
            }

            if (i <= start) throw ParseError("词法分析无法推进(字符 '${text[start]}')")
        }
        return tokens
    }
}
