package com.example.casio.core

/**
 * LCD 显示模型 —— 对应 fx-991CN X 的显示方式。
 *
 * 真机的显示不是一行普通文本:数学输入模式下**分数、根号、积分、Σ 都要竖排**,
 * 分数线/根号横线是画出来的,分子在上、分母在下。所以这里先把要显示的内容
 * 建成树,再交给渲染器(LcdRender 出文本、LcdCanvas 出图形)。
 */
sealed interface Lcd {

    // ---------------- 显示元素 ----------------

    /** 纯文本 */
    data class Text(val s: String) : Lcd

    /** 分数:分子在上、分母在下,中间一条横线 */
    data class Frac(val num: Lcd, val den: Lcd, val mixedWhole: Lcd? = null) : Lcd

    /** 根号:可选次数(如 3 表示立方根),被开方数在横线下 */
    data class Sqrt(val radicand: Lcd, val degree: Lcd? = null) : Lcd

    /** 乘方:底数与指数(指数右上小字) */
    data class Pow(val base: Lcd, val exponent: Lcd) : Lcd

    /** 上下限结构:积分、求和等用(下限在左下、上限在右上) */
    data class Limits(
        val op: String,          // "∫" / "Σ" / "Π"
        val lower: Lcd?,
        val upper: Lcd?,
        val body: Lcd
    ) : Lcd

    /** 小字(真机的上标/下标是缩小的字) */
    data class Small(val inner: Lcd) : Lcd

    /** 横排序列 */
    data class Row(val items: List<Lcd>) : Lcd

    // ---------------- 指示符 ----------------

    /**
     * 屏幕顶部指示符。对应手册中出现的:E(工程符号)、i(复数 a+bi)、
     * ∠(复数 r∠θ)、M(独立存储器)、以及数学输入标志。
     */
    data class Indicators(
        val engineering: Boolean = false,
        val complexRect: Boolean = false,
        val complexPolar: Boolean = false,
        val memory: Boolean = false,
        val mathInput: Boolean = false,
        val shift: Boolean = false,
        val alpha: Boolean = false
    ) {
        fun symbols(): List<String> {
            val out = mutableListOf<String>()
            if (shift) out.add("S")
            if (alpha) out.add("A")
            if (mathInput) out.add("Math")
            if (engineering) out.add("E")
            if (complexRect) out.add("i")
            if (complexPolar) out.add("∠")
            if (memory) out.add("M")
            return out
        }

        companion object {
            /**
             * 由当前设定推导指示符。
             *
             * 注意:`i` / `∠` 只在**确实处于复数模式**时才显示(真机如此),
             * 不能只看设定项 —— 设定里"方程解允许复数"是缺省开,
             * 但那不代表现在正在算复数。
             *
             * @param complexMode 当前是否在复数/复数解语境
             */
            fun of(
                s: Settings,
                memoryUsed: Boolean = false,
                complexMode: Boolean = false
            ): Indicators = Indicators(
                engineering = s.engineering,
                complexRect = complexMode && !s.complexPolar,
                complexPolar = complexMode && s.complexPolar,
                memory = memoryUsed,
                mathInput = s.mathInput
            )
        }
    }
}
