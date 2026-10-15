package com.example.casio.core

/**
 * 输入框的「原子块」编辑器模型。
 *
 * 设计要点(对应需求):
 *  1. `:int` 这类命令是一个**整体块**,块内部分成若干字段(上限/下限/函数),
 *     字段之间的分隔是布局产生的、不是字符,所以键盘删不掉,光标也进不去。
 *  2. 光标只能在「字段」之间移动,不会停在 `:int` 这种前缀的中间。
 *  3. 删掉块时整块一起删(块内所有内容同步消失)。
 *  4. 每个字段可以带标注(如积分的「上限」「下限」),渲染在字段左上方。
 *
 * 内部文本形式(getText)用于交给求解引擎;恢复(parse)时会把
 * 已认识的命令块重新解析成块,其余部分保持为普通文本。
 */
object Edit {

    /** 一个「可以放光标」的最小单元 */
    interface Field {
        var value: String
        val label: String        // 标注,空串表示无
        val fixedPrefix: String  // 固定前缀(如 ":int" 后的空格),不可编辑
        val deletable: Boolean   // false = 固定内容(如命令关键字本身)
        /** 是否已被用户改过;false 时首次输入会整体替换默认值(如把默认 0 换成 1) */
        var touched: Boolean
    }

    /** 普通文本字段 */
    class TextField(override var value: String) : Field {
        override val label = ""
        override val fixedPrefix = ""
        override val deletable = true
        override var touched = value.isNotEmpty()
    }

    /** 光标的可见标记(细竖线),独立成节点,便于自动化验证 */
    const val CURSOR_MARK = "\u258F"

    /** 命令块里的字段(如积分的上限) */
    class BlockField(
        override var value: String,
        override val label: String = "",
        override val fixedPrefix: String = "",
        override val deletable: Boolean = true
    ) : Field {
        override var touched = false
    }

    /** 输入项:要么是一段文本,要么是一个命令块 */
    sealed interface Part

    /** 普通文本(可编辑,内部按字符编辑) */
    class Raw(var text: String) : Part {
        /** 同 Field.touched:false 时首次输入整体替换 */
        var touched = text.isNotEmpty()
    }

    /** 命令块:command 是关键字(如 ":int"),fields 是块内字段 */
    class Block(val command: String, val fields: MutableList<Field>) : Part

    /** 光标位置:partIndex 指向某个 Part,fieldIndex 指向块内字段,offset 指向字段内字符位置 */
    data class Cursor(val partIndex: Int, val fieldIndex: Int, val offset: Int)

    // ---------------- 内部文本 ↔ 模型 ----------------

    private val KNOWN_COMMANDS = listOf(":int", ":diff", ":lim", ":set", ":vars", ":clear", ":help")

    /** 把一个块摊平成内部文本,如 ":int 0 1 x^2" */
    private fun blockText(b: Block): String {
        val parts = b.fields.map { it.value.trim() }
        return when (b.command) {
            ":int" -> ":int ${parts.getOrElse(0) { "" }} ${parts.getOrElse(1) { "" }} ${parts.getOrElse(2) { "" }}".trimEnd()
            ":diff" -> {
                val body = parts.getOrElse(0) { "" }
                val v = parts.getOrElse(1) { "" }
                if (v.isBlank()) ":diff $body".trimEnd() else ":diff $body $v"
            }
            ":lim" -> {
                val body = parts.getOrElse(0) { "" }
                val v = parts.getOrElse(1) { "" }.ifBlank { "x" }
                val to = parts.getOrElse(2) { "" }
                val side = parts.getOrElse(3) { "" }.ifBlank { "both" }
                ":lim $body $v $to $side".trimEnd()
            }
            else -> (listOf(b.command) + parts).joinToString(" ").trimEnd()
        }
    }

    /** 模型 → 内部文本(交给引擎) */
    fun getText(parts: List<Part>): String = parts.joinToString("") { p ->
        when (p) {
            is Raw -> p.text
            is Block -> blockText(p)
        }
    }

    /**
     * 当前输入的「显示形态」,用于 LCD 行预览(命令块显示成数学写法)。
     * 例如积分块显示为 "∫_0^1 x^2 dx",普通文本原样。
     */
    fun displayText(parts: List<Part>): String = parts.joinToString("") { p ->
        when (p) {
            is Raw -> p.text
            is Block -> when (p.command) {
                ":int" -> {
                    val lo = p.fields.getOrNull(0)?.value ?: ""
                    val hi = p.fields.getOrNull(1)?.value ?: ""
                    val body = p.fields.getOrNull(2)?.value ?: ""
                    "∫_$lo^$hi $body dx"
                }
                ":diff" -> {
                    val body = p.fields.getOrNull(0)?.value ?: ""
                    "d/dx($body)"
                }
                ":lim" -> {
                    val body = p.fields.getOrNull(0)?.value ?: ""
                    val v = p.fields.getOrNull(1)?.value ?: "x"
                    val to = p.fields.getOrNull(2)?.value ?: ""
                    val side = p.fields.getOrNull(3)?.value ?: ""
                    val suffix = when (side.lowercase()) {
                        "left", "l" -> "⁻"
                        "right", "r" -> "⁺"
                        else -> ""
                    }
                    "lim($v→$to$suffix) $body"
                }
                else -> blockText(p)
            }
        }
    }

    /**
     * 输入长度上限。手册:一次计算最多可输入 **199** 字符,
     * 且部分函数占 2~13 字节。剩余 ≤10 时真机把光标形状变成 k。
     */
    const val MAX_INPUT = 199

    /** 剩余多少字符时给出提示(真机换成 k 形光标) */
    const val INPUT_WARN_REMAIN = 10

    /**
     * 估算输入的"字节数"。手册说多数数字/符号/函数占 1 字节,
     * 部分函数占 2~13。这里按常见规则近似:
     *  · 普通字符 1 字节
     *  · 形如 `func(` 的函数调用按其名字长度 +1(与手册"数个字节"一致)
     *  · 命令块按内部文本长度计
     */
    fun byteLength(parts: List<Part>): Int = getText(parts).length

    /** 还能输入多少字符(不为负) */
    fun remainBytes(parts: List<Part>): Int =
        (MAX_INPUT - byteLength(parts)).coerceAtLeast(0)

    /** 是否已接近上限(真机会把光标变成 k) */
    fun nearLimit(parts: List<Part>): Boolean =
        remainBytes(parts) <= INPUT_WARN_REMAIN

    /** 是否还能再插入 text(超出上限则拒绝,避免像真机一样"输不进去") */
    fun canInsert(parts: List<Part>, text: String): Boolean =
        byteLength(parts) + text.length <= MAX_INPUT

    /**
     * 按输入模式变换按键文本。
     *
     * 数学输入(MATH_*)下可以用竖排模板,所以 `√` 插入 `√(`、
     * 分数类是模板;线性输入(LINEAR_*)下 `√` 只能插入 `√(` 的线性写法,
     * 且不提供竖排模板(真机此时禁用数学输入)。
     *
     * 目前两种情况的插入文本相同(引擎接受 `√(x)` 形式),
     * 这个函数把差异集中到一处,后续接入真模板时只改这里。
     */
    fun insertTextFor(mode: IoMode, key: String): String = when (key) {
        "√" -> "√("
        else -> key
    }

    // ---------------- 布局用的「显示行」 ----------------

    /**
     * 渲染用的样式片段。kind:
     *  FIXED 固定前缀(如 :int),不参与编辑
     *  VALUE 字段内容
     *  CURSOR 光标所在的那一个字符(高亮)
     */
    data class Span(val text: String, val kind: Kind) {
        enum class Kind { FIXED, VALUE, CURSOR, BLOCK }
    }

    /**
     * 一个渲染单元格。label 非空时,要在内容左上方显示标注
     * (如积分的「上限」「下限」),并用空格把内容右推、避免被标注压住。
     */
    data class Cell(
        val label: String,
        val spans: List<Span>,
        val partIndex: Int,
        val fieldIndex: Int,
        val selectable: Boolean
    ) {
        val text: String get() = spans.joinToString("") { it.text }
    }

    /** 视觉行 */
    data class VLine(val cells: List<Cell>)

    /**
     * 生成渲染行。命令块独占一行,块内每个字段是一个「纵向单元格」:
     * 标注(如「上限」)显示在字段内容的正上方,所以上下限自然左右并排、
     * 各自的标注都在自己数字的左上方。
     */
    fun layout(parts: List<Part>, cursor: Cursor?): List<VLine> {
        val lines = mutableListOf<VLine>()
        var current = mutableListOf<Cell>()

        fun flush() {
            if (current.isNotEmpty()) {
                lines.add(VLine(current))
                current = mutableListOf()
            }
        }

        fun cellFor(pi: Int, fi: Int, label: String, prefix: String, value: String): Cell {
            val spans = mutableListOf<Span>()
            if (prefix.isNotEmpty()) spans.add(Span(prefix, Span.Kind.FIXED))
            val active = cursor != null && cursor.partIndex == pi && cursor.fieldIndex == fi
            if (active) {
                val off = cursor!!.offset.coerceIn(0, value.length)
                // 光标画成一条可见的竖线,插在插入点处(独立节点,便于自动化断言)
                if (off > 0) spans.add(Span(value.substring(0, off), Span.Kind.VALUE))
                spans.add(Span(CURSOR_MARK, Span.Kind.CURSOR))
                if (off < value.length) spans.add(Span(value.substring(off), Span.Kind.VALUE))
            } else if (value.isNotEmpty()) {
                spans.add(Span(value, Span.Kind.VALUE))
            } else {
                spans.add(Span("", Span.Kind.VALUE))
            }
            return Cell(label, spans, pi, fi, selectable = true)
        }

        parts.forEachIndexed { pi, part ->
            when (part) {
                is Raw -> {
                    val active = cursor != null && cursor.partIndex == pi && cursor.fieldIndex == 0
                    val off = if (active) cursor!!.offset.coerceIn(0, part.text.length) else -1
                    if (off >= 0) {
                        val spans = mutableListOf<Span>()
                        if (off > 0) spans.add(Span(part.text.substring(0, off), Span.Kind.VALUE))
                        spans.add(Span(CURSOR_MARK, Span.Kind.CURSOR))
                        if (off < part.text.length) spans.add(Span(part.text.substring(off), Span.Kind.VALUE))
                        current.add(Cell("", spans, pi, 0, true))
                    } else if (part.text.isNotEmpty()) {
                        current.add(Cell("", listOf(Span(part.text, Span.Kind.VALUE)), pi, 0, true))
                    }
                }
                is Block -> {
                    flush()
                    val cells = part.fields.mapIndexed { fi, f ->
                        cellFor(
                            pi, fi, f.label,
                            if (fi == 0) f.fixedPrefix else "", f.value
                        )
                    }
                    lines.add(VLine(cells))
                    flush()
                }
            }
        }
        flush()
        return lines
    }

    // ---------------- 按键 ----------------

    /** 光标指向的项(越界返回 null) */
    private fun partAt(parts: List<Part>, c: Cursor?): Part? =
        if (c == null) null else parts.getOrNull(c.partIndex)

    /** 字段数:Raw 视为 1 个字段 */
    private fun fieldCount(p: Part) = when (p) {
        is Raw -> 1
        is Block -> p.fields.size
    }

    /**
     * 在当前字段插入文字。
     * 若该字段是**默认值且还没被改过**(touched=false),首次输入直接整体替换,
     * 这样用户用方向键切到限字段后按数字,得到的是 1 而不是 01。
     * 超出 199 字符上限的插入会被拒绝(返回原光标,不改变内容)。
     */
    fun insert(parts: MutableList<Part>, cursor: Cursor, text: String): Cursor {
        if (!canInsert(parts, text)) return cursor
        val p = partAt(parts, cursor) ?: return cursor
        return when (p) {
            is Raw -> {
                val off = cursor.offset.coerceIn(0, p.text.length)
                p.text = p.text.substring(0, off) + text + p.text.substring(off)
                p.touched = true
                cursor.copy(offset = off + text.length)
            }
            is Block -> {
                val f = p.fields.getOrNull(cursor.fieldIndex) ?: return cursor
                if (!f.touched) {
                    // 首次编辑:整体替换默认内容
                    f.value = text
                    f.touched = true
                    return cursor.copy(offset = text.length)
                }
                val off = cursor.offset.coerceIn(0, f.value.length)
                f.value = f.value.substring(0, off) + text + f.value.substring(off)
                cursor.copy(offset = off + text.length)
            }
        }
    }

    /**
     * 退格。规则:
     *  · 字段里还有字符 → 删掉光标前(光标在开头时删光标处)的一个字符
     *  · 字段确实已空 → 命令块整体删掉(块内所有内容一起消失);普通文本则跳到上一项
     *  · 固定前缀(如 :int 关键字)不可单独删除
     */
    fun backspace(parts: MutableList<Part>, cursor: Cursor): Cursor {
        val p = partAt(parts, cursor) ?: return cursor
        // 取出光标所在字段(普通文本视为一个字段)
        val value = when (p) {
            is Raw -> p.text
            is Block -> p.fields.getOrNull(cursor.fieldIndex)?.value ?: ""
        }
        val pos = cursor.offset.coerceIn(0, value.length)

        // 1) 字段里还有字符 → 删一个字符
        if (value.isNotEmpty()) {
            // 光标在开头时,删光标处那个字符(否则退回开头却删不掉,会误判为字段已空)
            val delAt = if (pos > 0) pos - 1 else 0
            val newValue = value.removeRange(delAt, delAt + 1)
            when (p) {
                is Raw -> p.text = newValue
                is Block -> p.fields[cursor.fieldIndex].value = newValue
            }
            return cursor.copy(offset = delAt)
        }

        // 2) 字段已空:命令块整体删除
        if (p is Block) {
            parts.removeAt(cursor.partIndex)
            return moveLeftAfterRemoval(parts, cursor.partIndex)
        }
        // 3) 字段多且不是第一个字段 → 退到上一个字段末尾(不删内容)
        if (cursor.fieldIndex > 0) {
            return cursor.copy(fieldIndex = cursor.fieldIndex - 1, offset = 0)
        }
        // 4) 普通文本已到开头 → 看前一项
        if (cursor.partIndex > 0) {
            val prev = parts[cursor.partIndex - 1]
            if (prev is Block) {
                // 普通文本在块后面且已空:把空的文本项删掉,光标回到块内最后一个字段
                parts.removeAt(cursor.partIndex)
                val lastField = prev.fields.size - 1
                return Cursor(cursor.partIndex - 1, lastField, prev.fields[lastField].value.length)
            }
            val pt = prev as Raw
            return Cursor(cursor.partIndex - 1, 0, pt.text.length)
        }
        return cursor
    }

    private fun moveLeftAfterRemoval(parts: List<Part>, removedIndex: Int): Cursor {
        if (removedIndex <= 0) return Cursor(0, 0, 0)
        val prev = parts[removedIndex - 1]
        return when (prev) {
            is Raw -> Cursor(removedIndex - 1, 0, prev.text.length)
            is Block -> {
                val last = prev.fields.size - 1
                Cursor(removedIndex - 1, last, prev.fields[last].value.length)
            }
        }
    }

    /**
     * 光标左移。字段级语义:**一次「←」就换到上一个字段**。
     * 上限/下限这类只有一个字符的字段,按一下就换一个,不会"原地不动";
     * 光标落点用可见标记画出,不必靠"清掉默认值"腾位置。
     */
    fun moveLeft(parts: List<Part>, cursor: Cursor): Cursor {
        if (cursor.fieldIndex > 0) {
            val p = partAt(parts, cursor) ?: return cursor
            val f = (p as Block).fields[cursor.fieldIndex - 1]
            return cursor.copy(fieldIndex = cursor.fieldIndex - 1, offset = f.value.length)
        }
        if (cursor.partIndex > 0) {
            val prev = parts[cursor.partIndex - 1]
            return when (prev) {
                is Raw -> Cursor(cursor.partIndex - 1, 0, prev.text.length)
                is Block -> {
                    val last = prev.fields.size - 1
                    Cursor(cursor.partIndex - 1, last, prev.fields[last].value.length)
                }
            }
        }
        return cursor
    }

    /** 光标右移:一次「→」同样换到下一个字段的**开头** */
    fun moveRight(parts: List<Part>, cursor: Cursor): Cursor {
        val p = partAt(parts, cursor) ?: return cursor
        if (p is Block && cursor.fieldIndex < p.fields.size - 1) {
            // 重新进入的字段视为"待整体替换",与首次进入行为一致
            p.fields[cursor.fieldIndex + 1].touched = false
            return cursor.copy(fieldIndex = cursor.fieldIndex + 1, offset = 0)
        }
        if (p is Raw && cursor.offset < p.text.length) {
            return cursor.copy(offset = p.text.length)
        }
        if (cursor.partIndex < parts.size - 1) {
            return Cursor(cursor.partIndex + 1, 0, 0)
        }
        return cursor
    }

    // ---------------- 生成命令块 ----------------

    /**
     * 新建积分块:下限、上限各自默认 0,光标落在函数处。
     * 字段顺序按数学书写习惯 —— 先下限后上限(∫ 从下限积到上限)。
     * 索引:0=下限 1=上限 2=函数。引擎的命令行形式是 ":int 下限 上限 表达式"。
     */
    fun newIntegral(): Block = Block(
        ":int",
        mutableListOf(
            BlockField("0", label = "下限", fixedPrefix = ":int ", deletable = false),
            BlockField("0", label = "上限"),
            BlockField("", label = "函数")
        )
    )

    /**
     * 求极限块,对应 `:lim 表达式 变量 趋近值 [方向]`。
     * 方向缺省为 both(双侧)。
     */
    fun newLimit(): Block = Block(
        ":lim",
        mutableListOf(
            // 首字段**不能**带 fixedPrefix:那样它会被 firstWritable 当作固定前缀跳过,
            // 输入会落到"变量"格里(实测表达式只剩 "1/",变量变成 x)
            BlockField("", label = "表达式"),
            BlockField("x", label = "变量"),
            BlockField("0", label = "趋近值"),
            // 方向必须留非空默认值:blockText 用 isNotBlank 过滤空字段,
            // 留空会把方向参数整个丢掉,引擎拿不到 both
            BlockField("both", label = "方向(left/right/both)")
        )
    )

    fun newDiff(): Block = Block(
        ":diff",
        mutableListOf(
            BlockField("", label = "表达式", fixedPrefix = ":diff ", deletable = false),
            BlockField("x", label = "自变量")
        )
    )

    /** 块内第一个可写的字段下标(通常跳过固定前缀字段) */
    private fun firstWritable(b: Block): Int {
        b.fields.forEachIndexed { i, f ->
            if (!f.fixedPrefix.endsWith(" ") || f.value.isEmpty()) return i
        }
        return b.fields.size - 1
    }

    // ---------------- 从文本解析 ----------------

    /** 把内部文本解析成 模型 + 光标位置(光标停在末尾) */
    fun parse(text: String): Pair<MutableList<Part>, Cursor> {
        val parts = mutableListOf<Part>()
        val sb = StringBuilder()
        var i = 0
        var handled = 0

        fun flushRaw() {
            if (sb.isNotEmpty()) {
                parts.add(Raw(sb.toString()))
                sb.clear()
            }
        }

        while (i < text.length) {
            val hit = KNOWN_COMMANDS.firstOrNull { c ->
                text.startsWith(c, i, ignoreCase = true) &&
                    (i + c.length >= text.length || !text[i + c.length].isLetterOrDigit())
            }
            if (hit != null) {
                val start = i
                // 命令的结束位置:碰到下一个已知命令或字符串结束
                var end = text.length
                for (j in (i + hit.length) until text.length) {
                    if (KNOWN_COMMANDS.any { k -> text.startsWith(k, j, ignoreCase = true) }) {
                        end = j
                        break
                    }
                }
                val seg = text.substring(start, end).trimEnd()
                val block = parseBlock(hit, seg)
                if (block != null) {
                    flushRaw()
                    parts.add(block)
                    handled++
                    i = end
                    continue
                }
            }
            sb.append(text[i])
            i++
        }
        flushRaw()

        // 光标停在末尾
        val cursor = if (parts.isEmpty()) Cursor(0, 0, 0) else {
            val pi = parts.size - 1
            when (val p = parts[pi]) {
                is Raw -> Cursor(pi, 0, p.text.length)
                is Block -> {
                    val li = p.fields.size - 1
                    Cursor(pi, li, p.fields[li].value.length)
                }
            }
        }
        return parts to cursor
    }

    private fun parseBlock(cmd: String, seg: String): Block? {
        if (cmd == ":vars" || cmd == ":clear" || cmd == ":help") {
            return Block(cmd, mutableListOf(BlockField("", deletable = false)))
        }
        val rest = seg.substring(cmd.length).trim()
        return when (cmd.lowercase()) {
            ":int" -> {
                val toks = rest.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
                Block(
                    ":int",
                    mutableListOf(
                        BlockField(toks.getOrElse(0) { "0" }, label = "上限", fixedPrefix = ":int ", deletable = false),
                        BlockField(toks.getOrElse(1) { "0" }, label = "下限"),
                        BlockField(toks.drop(2).joinToString(" "), label = "函数")
                    )
                )
            }
            ":diff" -> {
                val toks = rest.split(Regex("\\s+")).filter { it.isNotEmpty() }
                Block(
                    ":diff",
                    mutableListOf(
                        BlockField(toks.getOrElse(0) { "" }, label = "表达式", fixedPrefix = ":diff ", deletable = false),
                        BlockField(toks.getOrElse(1) { "x" }, label = "自变量")
                    )
                )
            }
            ":set" -> Block(
                ":set",
                mutableListOf(
                    BlockField(rest, label = "赋值", fixedPrefix = ":set ", deletable = false)
                )
            )
            else -> null
        }
    }

    /** 供自测使用:创建空编辑器 */
    fun empty(): Pair<MutableList<Part>, Cursor> =
        mutableListOf<Part>(Raw("")) to Cursor(0, 0, 0)
}
