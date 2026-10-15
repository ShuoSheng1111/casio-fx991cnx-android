package com.example.casio.core

import kotlin.math.abs

/**
 * 把 [Lcd] 渲染成字符画。真机用点阵 LCD,这里用等宽字符近似:
 * 分数是「分子 / 分数线 / 分母」三行,根号用横线覆盖被开方数,乘方用小字上标。
 *
 * 这个渲染器是**可自测的纯逻辑**;界面层再用 Canvas 画得更接近真机
 * (见 LcdCanvas),两者的布局规则保持一致。
 */
object LcdRender {

    /** 渲染结果:每行一个字符串,已去掉行尾空白 */
    fun render(node: Lcd): String {
        val block = layout(node)
        return block.lines.joinToString("\n") { it.trimEnd() }.trimEnd('\n')
    }

    /** 渲染成单行(放不下竖排时使用,如线性输入模式) */
    fun renderInline(node: Lcd): String = when (node) {
        is Lcd.Text -> node.s
        is Lcd.Row -> node.items.joinToString("") { renderInline(it) }
        is Lcd.Frac -> {
            val n = renderInline(node.num)
            val d = renderInline(node.den)
            val w = node.mixedWhole?.let { renderInline(it) + "⌐" } ?: ""
            "$w$n/$d"
        }
        is Lcd.Sqrt -> {
            val deg = node.degree?.let { renderInline(it) } ?: ""
            "$deg√(${renderInline(node.radicand)})"
        }
        is Lcd.Pow -> "${renderInline(node.base)}^(${renderInline(node.exponent)})"
        is Lcd.Limits -> {
            val lo = node.lower?.let { renderInline(it) } ?: ""
            val hi = node.upper?.let { renderInline(it) } ?: ""
            "${node.op}($lo,$hi)${renderInline(node.body)}"
        }
        is Lcd.Small -> renderInline(node.inner)
    }

    // ---------------- 布局 ----------------

    private class Block(val lines: List<String>, val baseline: Int) {
        val width: Int get() = lines.maxOfOrNull { it.length } ?: 0
        val height: Int get() = lines.size
    }

    private fun pad(s: String, w: Int): String =
        if (s.length >= w) s else s + " ".repeat(w - s.length)

    /** 把内容在宽度 w 的区域内居中(自身宽度 own) */
    private fun center(s: String, own: Int, w: Int): String {
        val left = ((w - own) / 2).coerceAtLeast(0)
        return " ".repeat(left) + s
    }

    /** 把若干行包成 Lcd.Row,用于带分数的并排布局 */
    private fun fracNodeOf(lines: List<String>): Lcd =
        Lcd.Row(lines.map { Lcd.Text(it) })

    private fun layout(node: Lcd): Block {
        return when (node) {
            is Lcd.Text -> Block(listOf(node.s), 0)

            is Lcd.Small -> {
                // 小字:渲染层用更小字号画;字符画模式下内容不变
                val b = layout(node.inner)
                Block(b.lines, b.baseline)
            }

            is Lcd.Row -> {
                val blocks = node.items.map { layout(it) }
                if (blocks.isEmpty()) {
                    Block(listOf(""), 0)
                } else {
                    val maxAbove = blocks.maxOf { it.baseline }
                    val maxBelow = blocks.maxOf { it.height - 1 - it.baseline }
                    val height = maxAbove + maxBelow + 1
                    val out = MutableList(height) { StringBuilder() }
                    blocks.forEach { b ->
                        val top = maxAbove - b.baseline
                        for (r in 0 until height) {
                            val src = r - top
                            val seg = if (src in 0 until b.height) b.lines[src] else ""
                            out[r].append(pad(seg, b.width))
                        }
                    }
                    Block(out.map { it.toString() }, maxAbove)
                }
            }

            is Lcd.Frac -> {
                val n = layout(node.num)
                val d = layout(node.den)
                val w = maxOf(n.width, d.width)
                val lines = mutableListOf<String>()
                n.lines.forEach { lines.add(pad(center(it, n.width, w), w)) }
                lines.add("─".repeat(w))
                d.lines.forEach { lines.add(pad(center(it, d.width, w), w)) }
                if (node.mixedWhole != null) {
                    // 带分数:整数部分与分数并排(整数按显示宽度右对齐到分数中线)
                    layout(
                        Lcd.Row(
                            listOf(
                                Lcd.Text(node.mixedWhole.let { renderInline(it) }),
                                fracNodeOf(lines)
                            )
                        )
                    )
                } else {
                    Block(lines, n.height)
                }
            }

            is Lcd.Sqrt -> {
                val r = layout(node.radicand)
                val deg = node.degree?.let { renderInline(it) } ?: ""
                val lines = mutableListOf(deg + "√" + "─".repeat(r.width))
                r.lines.forEach { lines.add(" " + it) }
                Block(lines, 1)
            }

            is Lcd.Pow -> {
                val b = layout(node.base)
                val e = layout(node.exponent)
                val lines = mutableListOf(pad(b.lines.first(), b.width) + e.lines.first())
                b.lines.drop(1).forEach { lines.add(pad(it, b.width)) }
                if (e.height > 1) {
                    e.lines.drop(1).forEach { lines.add(pad("", b.width) + it) }
                }
                Block(lines, b.baseline)
            }

            is Lcd.Limits -> {
                val body = layout(node.body)
                val hi = node.upper?.let { renderInline(it) } ?: ""
                val lo = node.lower?.let { renderInline(it) } ?: ""
                val bodyFirst = body.lines.firstOrNull() ?: ""
                val lines = mutableListOf(if (hi.isEmpty()) bodyFirst else hi + bodyFirst)
                body.lines.drop(1).forEach { lines.add(it) }
                if (lo.isNotEmpty()) lines.add(lo)
                Block(lines, 0)
            }
        }
    }

    // ---------------- 由数值构造显示树 ----------------

    /**
     * 把数值构造成显示树:优先精确形式(分数、带分数、π 分数),
     * 否则退回 [Fmt] 的文本。仅「数学输出」下才给竖排的分数。
     */
    fun ofNumber(v: Double, s: Settings, allowExact: Boolean = true): Lcd {
        if (s.mathOutput && allowExact) {
            // 分数(含带分数)
            Fmt.bestFraction(v)?.let { (num, den) ->
                if (den != 1L && den <= 100000L) {
                    val sign = if (num < 0) "-" else ""
                    val an = abs(num)
                    return if (s.mixedFraction && an > den) {
                        Lcd.Frac(
                            Lcd.Text((an % den).toString()),
                            Lcd.Text(den.toString()),
                            mixedWhole = Lcd.Text(sign + (an / den))
                        )
                    } else {
                        Lcd.Frac(Lcd.Text(sign + an), Lcd.Text(den.toString()))
                    }
                }
            }
            // π 的分数倍(如 π/2)
            Fmt.exactForm(v, s)?.let { txt ->
                if (txt.contains('π') && txt.contains('/')) {
                    val a = txt.substringBefore('/')
                    val b = txt.substringAfter('/')
                    val an = a.removeSuffix("π")
                    return Lcd.Frac(
                        Lcd.Text(if (an.isEmpty() || an == "-") an + "π" else an + "π"),
                        Lcd.Text(b)
                    )
                }
                // 根式:a√b —— 必须显式转成 Sqrt 结构,否则会退回小数
                if (txt.contains('√')) {
                    return radicalNode(txt)
                }
            }
        }
        return Lcd.Text(Fmt.number(v, s, mathExact = allowExact))
    }

    /**
     * 把 "√2" / "2√3" / "-√5" / "-2√3" 这样的精确形式文本转成显示树。
     * 提取根号前的系数,系数非 1 时用 Row 把系数与根号并排。
     */
    private fun radicalNode(txt: String): Lcd {
        val neg = txt.startsWith("-")
        val body = if (neg) txt.substring(1) else txt
        val idx = body.indexOf('√')
        val coef = body.substring(0, idx)
        val radicand = body.substring(idx + 1)
        val sqrtNode = if (radicand.isEmpty()) {
            Lcd.Text("√")
        } else {
            Lcd.Sqrt(Lcd.Text(radicand))
        }
        return when {
            coef.isEmpty() && !neg -> sqrtNode
            coef.isEmpty() && neg -> Lcd.Row(listOf(Lcd.Text("-"), sqrtNode))
            else -> {
                val prefix = if (neg) "-$coef" else coef
                Lcd.Row(listOf(Lcd.Text(prefix), sqrtNode))
            }
        }
    }
}
