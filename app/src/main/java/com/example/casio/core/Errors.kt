package com.example.casio.core

/**
 * 错误系统 —— 对应 fx-991CN X 的错误分类与「出错位置定位」。
 *
 * 真机的错误类型(手册「错误信息」一节):
 *  · Math ERROR     计算错误(结果超出范围、除以 0、定义域外)
 *  · Syntax ERROR   语法错误
 *  · Stack ERROR    堆栈错误(嵌套过深)
 *  · Argument ERROR 参数错误
 *  · Range ERROR    范围错误(输入超出允许范围)
 *  · Variable ERROR 变量错误
 *  · Insufficient MEM  内存不足
 *
 * 真机还会用 **^ 指出出错位置**。这里实现同样的事:
 * [locate] 返回出错字符的下标,界面据此画出位置指示。
 */
object Errors {

    /** 错误类型 */
    enum class Kind(val label: String, val hint: String) {
        SYNTAX("Syntax ERROR", "算式写法有问题,请检查括号、运算符与函数名"),
        MATH("Math ERROR", "计算结果超出范围,或出现除以 0、定义域外的情况"),
        ARGUMENT("Argument ERROR", "参数个数或类型不对"),
        RANGE("Range ERROR", "输入的数值超出允许范围"),
        VARIABLE("Variable ERROR", "用到了没有赋值的变量"),
        STACK("Stack ERROR", "嵌套层数过多"),
        MEMORY("Insufficient MEM", "内存不足")
    }

    /** 带类型与位置的错误 */
    data class Report(
        val kind: Kind,
        val message: String,
        /** 出错字符下标(从 0 开始);-1 表示无法定位 */
        val position: Int,
        /** 原始表达式 */
        val expression: String
    ) {
        /** 真机风格的位置指示:下面一行用 ^ 指出出错处 */
        fun pointer(): String {
            if (position < 0 || position > expression.length) return ""
            return " ".repeat(prefixWidth(expression, position)) + "^"
        }

        /** 完整展示 */
        fun render(): String = buildString {
            append("${kind.label}  $message")
            append("\n")
            append(expression)
            val p = pointer()
            if (p.isNotEmpty()) {
                append("\n").append(p)
            }
            append("\n\n")
            append(kind.hint)
        }

        /** 供界面用的两行(式子 + 指示) */
        fun displayLines(): Pair<String, String> = expression to pointer()
    }

    /** 计算显示宽度时把全角/中文算 2 列,避免 ^ 对不齐 */
    private fun prefixWidth(s: String, end: Int): Int {
        var w = 0
        for (i in 0 until minOf(end, s.length)) {
            w += if (s[i].code > 0x2000) 2 else 1
        }
        return w
    }

    /**
     * 把一个异常的文本判定成错误类型。
     * 依据是引擎里已有的错误措辞(不引入新的错误来源)。
     */
    fun classify(message: String): Kind {
        val m = message
        return when {
            m.contains("除以 0") || m.contains("除数为 0") -> Kind.MATH
            m.contains("超出") || m.contains("溢出") || m.contains("太大") -> Kind.RANGE
            m.contains("定义域") || m.contains("不存在") && m.contains("根") -> Kind.MATH
            m.contains("未知名称") || m.contains("没有赋值") -> Kind.VARIABLE
            m.contains("参数") || m.contains("个数") -> Kind.ARGUMENT
            m.contains("嵌套") || m.contains("过深") -> Kind.STACK
            m.contains("内存") -> Kind.MEMORY
            m.contains("不支持的字符") || m.contains("无法识别") || m.contains("不完整") ||
                m.contains("多余") || m.contains("括号") || m.contains("算式") -> Kind.SYNTAX
            else -> Kind.SYNTAX
        }
    }

    /**
     * 定位出错字符。
     *
     * 思路(不改动已验证的 Lexer/Parser 核心):
     *  · 先看错误信息里有没有直接带着位置(引擎部分错误会写 "位置 N")
     *  · 否则按错误类型找最可能的位置:
     *    - 语法类:括号不配对的末尾 / 不支持的字符处 / 尾部残留运算符
     *    - 未知名称:该名字第一次出现处
     *    - 除以 0:除号处
     */
    fun locate(expression: String, message: String): Int {
        // 引擎显式给出的位置优先
        Regex("位置\\s*(\\d+)").find(message)?.let {
            val n = it.groupValues[1].toIntOrNull() ?: return@let
            return n.coerceIn(0, expression.length)
        }

        val kind = classify(message)

        // 不支持的字符:直接指向它
        Regex("不支持的字符:\\s*(.)").find(message)?.let {
            val ch = it.groupValues[1].firstOrNull() ?: return@let
            val i = expression.indexOf(ch)
            if (i >= 0) return i
        }

        // 未知名称:指向该名字
        Regex("未知名称:\\s*([A-Za-z_][A-Za-z0-9_]*)").find(message)?.let {
            val name = it.groupValues[1]
            val i = expression.indexOf(name)
            if (i >= 0) return i
        }

        // 除以 0:指向除号
        if (kind == Kind.MATH) {
            val i = expression.indexOf('/')
            if (i >= 0) return i
        }

        // 括号不平衡:指出多出来/缺失的那一侧
        if (message.contains("括号")) {
            var depth = 0
            var lastOpen = -1
            for ((i, ch) in expression.withIndex()) {
                if (ch == '(') {
                    depth++
                    lastOpen = i
                } else if (ch == ')') {
                    depth--
                    if (depth < 0) return i     // 多了一个右括号
                }
            }
            if (depth > 0 && lastOpen >= 0) return lastOpen   // 少了右括号
        }

        // 算式不完整:指向末尾
        if (message.contains("不完整") || message.contains("多余")) {
            return (expression.length - 1).coerceAtLeast(0)
        }

        // 兜底:指向最后一个非空字符
        val last = expression.indexOfLast { !it.isWhitespace() }
        return if (last >= 0) last else 0
    }

    /** 由异常文本构造报告 */
    fun report(expression: String, message: String): Report =
        Report(classify(message), message, locate(expression, message), expression)

    /** 由异常构造报告 */
    fun report(expression: String, e: Throwable): Report =
        report(expression, e.message ?: "未知错误")

    /**
     * 把出错位置画进式子:用一对中括号标出该字符。
     * 界面宽度有限,竖排 ^ 可能对不齐,所以额外提供这种"就地标记"形式。
     */
    fun markInline(expression: String, position: Int): String {
        if (position < 0 || position >= expression.length) return expression
        return expression.substring(0, position) + "▸" +
            expression[position] + "◂" + expression.substring(position + 1)
    }
}
