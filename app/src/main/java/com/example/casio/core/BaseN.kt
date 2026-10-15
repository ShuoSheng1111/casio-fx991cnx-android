package com.example.casio.core

/**
 * 基数(进制)计算 —— 对应 fx-991CN X 的基数模式。
 *
 * 手册能力:
 *  · 四种进制:DEC(10)/ HEX(16)/ BIN(2)/ OCT(8),按 w / 6 / & / i 切换
 *  · 输入范围 **32 位**:DEC −2147483648 ~ 2147483647;
 *    HEX 00000000 ~ 7FFFFFFF(正)/ 80000000 ~ FFFFFFFF(负,二位补码)
 *  · 小数部分会被裁切;不支持幂指数输入
 *  · 数值后可跟进制后缀 d / h / b / o 指定该数的进制
 *  · 逻辑运算 and / or / xor / xnor,以及函数 Not / Neg
 *  · 负数以**二位补码**表示
 */
object BaseN {

    /** 进制 */
    enum class Base(val radix: Int, val label: String, val suffix: Char) {
        DEC(10, "DEC", 'd'),
        HEX(16, "HEX", 'h'),
        BIN(2, "BIN", 'b'),
        OCT(8, "OCT", 'o')
    }

    /** 32 位有符号范围 */
    const val MIN_VALUE = Int.MIN_VALUE      // -2147483648
    const val MAX_VALUE = Int.MAX_VALUE      // 2147483647

    /**
     * 把某个进制下的字符串解析为 32 位整数。
     * 解析规则对应真机:
     *  · 负数用前导负号(DEC)→ 直接取负
     *  · HEX/BIN/OCT 下,若最高位为 1(即数值超过该进制的"正数上限"),按**二位补码**解释为负数
     */
    fun parse(textIn: String, base: Base): Int {
        val text = textIn.trim()
        require(text.isNotEmpty()) { "空的数值" }

        // 十进制允许负号
        if (base == Base.DEC) {
            val negative = text.startsWith("-")
            val body = if (negative) text.substring(1) else text
            require(body.isNotEmpty()) { "空的数值" }
            val v = body.toLongOrNull() ?: throw IllegalArgumentException("不是合法的十进制数:$text")
            val signed = if (negative) -v else v
            require(signed in MIN_VALUE.toLong()..MAX_VALUE.toLong()) {
                "超出 32 位范围($MIN_VALUE ~ $MAX_VALUE):$text"
            }
            return signed.toInt()
        }

        // 其他进制:按位取值,最高位决定符号(二位补码)
        val digits = text.lowercase()
        val radix = base.radix
        var acc = 0L
        val bits = 32
        for (ch in digits) {
            val d = digitValue(ch)
            require(d >= 0 && d < radix) { "「$ch」不是合法的 ${base.label} 数字" }
            acc = acc * radix + d
            require(acc <= 0xFFFFFFFFL) { "超出 32 位范围:$text" }
        }
        // 若最高位(第 31 位)为 1,按补码解释为负数
        val raw = acc.toInt()
        // 判断是否"用满了 32 位":位数达到该进制的 32 位宽度且最高位为 1
        val widthDigits = when (base) {
            Base.HEX -> 8
            Base.BIN -> 32
            Base.OCT -> 11
            Base.DEC -> 0
        }
        return if (digits.length >= widthDigits && raw < 0) raw else raw
    }

    private fun digitValue(ch: Char): Int = when (ch) {
        in '0'..'9' -> ch - '0'
        in 'a'..'f' -> ch - 'a' + 10
        else -> -1
    }

    /** 格式化:按真机的补位规则输出 */
    fun format(value: Int, base: Base): String = when (base) {
        Base.DEC -> value.toString()
        Base.HEX -> String.format("%08X", value.toLong() and 0xFFFFFFFFL)
        Base.BIN -> {
            // 真机按 4 位一组显示二进制
            val s = String.format("%32s", Integer.toBinaryString(value)).replace(' ', '0')
            s.chunked(4).joinToString(" ")
        }
        Base.OCT -> String.format("%011o", value.toLong() and 0xFFFFFFFFL)
    }

    /** 紧凑二进制(无分组),供自测比较 */
    fun binCompact(value: Int): String =
        String.format("%32s", Integer.toBinaryString(value)).replace(' ', '0')

    // ---------------- 逻辑运算(逐位,结果为 32 位) ----------------

    fun and(a: Int, b: Int): Int = a and b
    fun or(a: Int, b: Int): Int = a or b
    fun xor(a: Int, b: Int): Int = a xor b
    fun xnor(a: Int, b: Int): Int = (a xor b).inv()

    /** Not(≡ 位补码) */
    fun not(a: Int): Int = a.inv()

    /** Neg(≡ 取负) */
    fun neg(a: Int): Int = -a

    /** 十进制分数部分裁切(手册:基数模式下小数被裁切) */
    fun truncate(v: Double): Int = v.toInt()

    /** 解析带进制后缀的数值,如 10h / 1010b / 10o / 10d */
    fun parseWithSuffix(text: String, defaultBase: Base): Pair<Int, Base> {
        val t = text.trim()
        if (t.isEmpty()) throw IllegalArgumentException("空的数值")
        val last = t.last().lowercaseChar()
        for (b in Base.entries) {
            if (last == b.suffix) {
                return parse(t.dropLast(1), b) to b
            }
        }
        return parse(t, defaultBase) to defaultBase
    }
}
