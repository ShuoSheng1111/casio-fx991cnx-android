package com.example.casio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.casio.core.Edit
import com.example.casio.core.Lexer

private val FIXED_COLOR = Color(0xFF8A8A8A)      // 固定前缀(:int)灰色,提示不可编辑
private val VALUE_COLOR = Color(0xFF111111)
private val CURSOR_COLOR = Color(0xFF2F6F3F)
private val LABEL_COLOR = Color(0xFF4A7A4A)

/** 把一段单元格内容编成带样式的字符串 */
private fun cellAnnotated(cell: Edit.Cell) = buildAnnotatedString {
    cell.spans.forEach { s ->
        val col = when (s.kind) {
            Edit.Span.Kind.FIXED -> FIXED_COLOR
            Edit.Span.Kind.VALUE -> VALUE_COLOR
            Edit.Span.Kind.CURSOR -> CURSOR_COLOR
            Edit.Span.Kind.BLOCK -> LABEL_COLOR
        }
        withStyle(SpanStyle(color = col)) { append(s.text) }
    }
}

/**
 * 表达式栏:命令块内每个字段是「标注在上、内容在下」的一列。
 * 标注与内容都左对齐,所以「上限」「下限」正好落在各自数字的**左上方**。
 * 分隔由布局产生而不是字符,因此键盘删不掉、光标也进不去。
 */
@Composable
fun ExpressionBar(
    parts: List<Edit.Part>,
    cursor: Edit.Cursor?,
    onCellTap: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    version: Int = 0,
    fontSize: androidx.compose.ui.unit.TextUnit = 20.sp
) {
    // version 参与 key:parts 里的对象是可变实例,必须靠版本号才能触发重算
    val lines = remember(version, cursor) { Edit.layout(parts, cursor) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        lines.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                line.cells.forEach { cell ->
                    Column(
                        modifier = Modifier.clickable(enabled = cell.selectable) {
                            onCellTap(cell.partIndex, cell.fieldIndex)
                        },
                        // 关键:左对齐,标注才能落在内容的左上方
                        horizontalAlignment = Alignment.Start
                    ) {
                        if (cell.label.isNotEmpty()) {
                            Text(
                                text = cell.label,
                                color = LABEL_COLOR,
                                fontSize = (fontSize.value * 0.5f).sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = cellAnnotated(cell),
                            fontSize = fontSize,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// ============ 按键表(集中定义,便于按相关性/常用度排序) ============

/**
 * 函数抽屉的分组。每组一行,按"相关性"成组:
 * 三角函数按家族横排 —— sin / arcsin / sinh / asin,cos 与 tan 同理。
 *
 * 注意:凡是已经移到「常用键行」(ACCESS_KEYS)的键,这里**不再重复出现**,
 * 否则同一功能会有两个按键。常用键行现有:ln log √ e π ∞ °。
 */
private val DRAWER_GROUPS: List<List<String>> = listOf(
    // 三角函数:同名家族排一行,按 基本 → 反函数 → 双曲 → 反双曲
    listOf("sin", "arcsin", "sinh", "asin"),
    listOf("cos", "arccos", "cosh", "acos"),
    listOf("tan", "arctan", "tanh", "atan"),
    // 指数(ln/log 已移到常用键行,此处只留 log2 与 exp)
    listOf("log2", "exp"),
    // 取整与绝对值(√ 已移到常用键行)
    listOf("abs", "floor", "ceil", "round"),
    // 组合数学
    listOf("fact", "ncr", "npr")
)

/** 函数在表达式里要补左括号;这些是常量或运算符,直接插入 */
private val NO_PAREN = setOf("√", "π", "e", "∞", "°")

/** 抽屉里每个按键对应的插入文本 */
fun drawerInsertText(name: String): String = when (name) {
    "ncr" -> "comb("
    "npr" -> "perm("
    else -> if (name in NO_PAREN) name else "$name("
}

/**
 * 函数抽屉:列出求解器支持的函数。
 * 已去掉重复键:π 只保留「π」(不再同时有 pi),tau 与 ncr/npr 等别名也各自合并。
 */
@Composable
fun FunctionDrawer(onPick: (String) -> Unit, maxHeight: androidx.compose.ui.unit.Dp = 240.dp) {
    // 用引擎的表做一次校验:抽屉里出现的名字都必须是引擎认识的
    val known = remember {
        Lexer.FUNC_ALIAS.keys + Lexer.CONSTANTS.keys +
            setOf("ncr", "npr", "sqrt", "fact")
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)          // 限高,避免把算式栏与数字键挤出屏幕
            .background(Color(0xFF161616))
            .border(1.dp, Color(0xFF3F8F4F), RoundedCornerShape(6.dp))
            .padding(3.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        DRAWER_GROUPS.forEach { group ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                group.forEach { name ->
                    val ok = name in known
                    Key(
                        name,
                        Modifier.weight(1f),
                        compact = true,
                        // 引擎不认识的名字画红底,便于一眼发现拼写错误
                        accent = !ok,
                        onClick = { onPick(drawerInsertText(name)) }
                    )
                }
                repeat(5 - group.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * 常用键行:放在「求值」那一行的**上面一行**。
 * 原来在抽屉或主键区里重复过的键(pi、e、ln、√ 等)已合并到这里,只留一份。
 */
/** 覆盖层面板的固定高度。用固定值而不是 heightIn(max):
 *  它们是「输出区」(自带 verticalScroll)的子节点,无界约束下 heightIn(max) 无效,
 *  面板会被父容器裁掉。 */
private val PANEL_H = 250.dp

private val ACCESS_KEYS = listOf("ln", "log", "%", "e", "π", "∞", "°")

@Composable
fun AccessRow(onKey: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        ACCESS_KEYS.forEach { name ->
            Key(
                name,
                Modifier.weight(1f),
                compact = true,
                onClick = { onKey(drawerInsertText(name)) }
            )
        }
    }
}

/**
 * HOME 菜单:对应真机按 MENU 键弹出的模式选择。
 * 未实现的模式仍列出(与真机菜单一致),但以灰色标出。
 */
@Composable
fun HomeMenu(
    current: com.example.casio.core.Calc.Mode,
    onPick: (com.example.casio.core.Calc.Mode) -> Unit,
    onClose: () -> Unit,
    /** 计算模式页面里的计算功能:标签 → 动作(插入对应的可编辑块) */
    onCalcTool: (String) -> Unit = {}
) {
    val modes = com.example.casio.core.Calc.Mode.entries
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 固定高度 + 自身滚动。**必须是 height 而不是 heightIn(max)**:
            // 这个页面挂在「输出区」(自带 verticalScroll)里,父容器给的是无界约束,
            // heightIn(max) 在那种约束下不起作用 —— 内容超出就被直接裁掉,滚也滚不动
            // (实测「MatA」系列插入键因此够不到)。
            .height(PANEL_H + 130.dp)
            .verticalScroll(rememberScrollState())
            .background(Color(0xFF141414))
            .border(1.dp, Color(0xFF3F8F4F), RoundedCornerShape(6.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "计算模式页面",
            color = Color(0xFF9FD89F),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        // ---- 计算功能:积分 / 求导 / 极限 都在计算模式页面内部 ----
        Text(
            text = "计算功能",
            color = Color(0xFF7A9A7A),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Key("∫ 定积分", Modifier.weight(1f), compact = true, onClick = { onCalcTool("int") })
            Key("d/dx 求导", Modifier.weight(1f), compact = true, onClick = { onCalcTool("diff") })
            Key("lim 求极限", Modifier.weight(1.2f), compact = true, onClick = { onCalcTool("lim") })
            Key("方程 F", Modifier.weight(1.1f), compact = true, onClick = { onCalcTool("eqn") })
        }
        // 矩阵 / 向量插入键:没有这些键,算式栏里根本打不出 MatA*MatB
        // (字母键盘只有小写、也没有 * 键),功能就等于永远用不到。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Key("MatA", Modifier.weight(1f), compact = true, onClick = { onCalcTool("ins:MatA") })
            Key("MatB", Modifier.weight(1f), compact = true, onClick = { onCalcTool("ins:MatB") })
            Key("MatAns", Modifier.weight(1.2f), compact = true, onClick = { onCalcTool("ins:MatAns") })
            Key("VctA", Modifier.weight(1f), compact = true, onClick = { onCalcTool("ins:VctA") })
            Key("VctB", Modifier.weight(1f), compact = true, onClick = { onCalcTool("ins:VctB") })
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Key("VctAns", Modifier.weight(1.2f), compact = true, onClick = { onCalcTool("ins:VctAns") })
            Key("×", Modifier.weight(0.8f), compact = true, onClick = { onCalcTool("ins:*") })
            Key("Det(", Modifier.weight(1f), compact = true, onClick = { onCalcTool("ins:Det(") })
            Key("Trn(", Modifier.weight(1f), compact = true, onClick = { onCalcTool("ins:Trn(") })
            Key("Inv(", Modifier.weight(1f), compact = true, onClick = { onCalcTool("ins:Inv(") })
            Key("·", Modifier.weight(0.8f), compact = true, onClick = { onCalcTool("ins:·") })
        }
        // ---- 模式选择 ----
        Text(
            text = "计算模式(灰色为尚未实现)",
            color = Color(0xFF7A9A7A),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        modes.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { m ->
                    val isCur = m == current
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 44.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when {
                                    isCur -> Color(0xFF2F6F3F)
                                    m.implemented -> Color(0xFF2D2D2D)
                                    else -> Color(0xFF1F1F1F)
                                }
                            )
                            .clickable { onPick(m) }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = m.label,
                            color = if (m.implemented) Color(0xFFF0F0F0) else Color(0xFF7A7A7A),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
        Key("关闭菜单", Modifier.fillMaxWidth(), compact = true, onClick = onClose)
    }
}

/**
 * 统计编辑器:表格式列表,对应真机的统计编辑器。
 *  · 显示已录数据,每行右侧有删除按钮
 *  · 底部一行是"待录入值",按数字键累积内容,按 [加入] 提交为一行
 *  · 支持插入行位置与全部删除
 */
@Composable
fun StatEditor(
    data: com.example.casio.core.Stats.StatData,
    useFreq: Boolean,
    pending: String,
    insertAt: Int,
    onDeleteRow: (Int) -> Unit,
    onCommit: () -> Unit,
    onInsertMode: () -> Unit,
    onClear: () -> Unit,
    onCalculate: () -> Unit,
    onClose: () -> Unit,
    version: Int
) {
    val rows = remember(version, data.rows.size) { data.rows.toList() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 限高:面板是「输出区」的子节点,而输出区自身带 verticalScroll ——
            // 不限高时超出部分会被父容器**直接裁掉**(不是滚动),用户点不到那些按键
            // (实测矩阵编辑器的「关闭/平方/立方/Abs」因此消失)。
            .height(PANEL_H)
            .background(Color(0xFF141414))
            .border(1.dp, Color(0xFF3F8F4F), RoundedCornerShape(6.dp))
            .padding(4.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "统计编辑器  已录 ${data.filledCount()} 行 / 上限 ${data.maxRows}" +
                (if (useFreq) "  (含频率列)" else ""),
            color = Color(0xFF9FD89F),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        // 双变量时提示下一个「加入」会填进哪一格,避免"点两次才知道填了 x 还是 y"
        if (data.twoVar) {
            val last = data.rows.lastOrNull()
            val nextCell =
                if (last != null && last.x != null && last.y == null) "y(第 ${data.rows.size} 行)"
                else "x(新一行)"
            Text(
                text = "下一个「加入」→ $nextCell",
                color = Color(0xFFFFC46B),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        // 表头
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("  #", color = Color(0xFF7A9A7A), fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(30.dp))
            Text("x", color = Color(0xFF7A9A7A), fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            if (data.twoVar) {
                Text("y", color = Color(0xFF7A9A7A), fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            }
            if (useFreq) {
                Text("Freq", color = Color(0xFF7A9A7A), fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.7f))
            }
            Text(" ", fontSize = 10.sp, modifier = Modifier.width(28.dp))
        }
        // 数据行(最多显示前若干行,避免过长)
        rows.take(40).forEachIndexed { i, r ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${i + 1}",
                    color = Color(0xFF7A9A7A),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(30.dp)
                )
                Text(
                    text = r.x?.let { com.example.casio.core.Fmt.number(it, com.example.casio.core.Settings(), false) } ?: "—",
                    color = Color(0xFFE0FFE0),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if (data.twoVar) {
                    Text(
                        text = r.y?.let { com.example.casio.core.Fmt.number(it, com.example.casio.core.Settings(), false) } ?: "—",
                        color = Color(0xFFE0FFE0),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (useFreq) {
                    Text(
                        text = r.freq?.let { com.example.casio.core.Fmt.number(it, com.example.casio.core.Settings(), false) } ?: "1",
                        color = Color(0xFFE0FFE0),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        modifier = Modifier.weight(0.7f)
                    )
                }
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF5A2A2A))
                        .clickable { onDeleteRow(i) }
                        .padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = Color(0xFFFFD0D0), fontSize = 10.sp)
                }
            }
        }
        if (rows.size > 40) {
            Text(
                text = "… 另有 ${rows.size - 40} 行未显示",
                color = Color(0xFF7A9A7A),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        // 待录入行
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "新增",
                color = Color(0xFFFFC46B),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(38.dp)
            )
            Text(
                text = if (pending.isEmpty()) "(按数字键输入)" else pending,
                color = if (pending.isEmpty()) Color(0xFF808080) else Color(0xFFFFE0A0),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Key("加入", Modifier.weight(1f), accent = true, compact = true, onClick = onCommit)
            Key("插到第 ${insertAt + 1} 行", Modifier.weight(1.4f), compact = true, onClick = onInsertMode)
            Key("全部删除", Modifier.weight(1.1f), compact = true, onClick = onClear)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Key("计算", Modifier.weight(1f), accent = true, compact = true, onClick = onCalculate)
            Key("关闭编辑器", Modifier.weight(1.2f), compact = true, onClick = onClose)
        }
    }
}

/**
 * 矩阵编辑器:选择 MatA~D、设置行列数、逐格录入,并支持 Det/Trn/逆/单位阵/幂/Abs。
 * 与统计编辑器同款思路:数字键累积到"待录入值",按 [下一格] 提交。
 */
@Composable
fun MatrixEditor(
    rows: Int,
    cols: Int,
    cells: List<Double?>,
    current: Int,
    pending: String,
    selected: String,
    result: String,
    onPickVar: (String) -> Unit,
    onSetDim: (Int, Int) -> Unit,
    onCommitCell: () -> Unit,
    onClear: () -> Unit,
    onOp: (String) -> Unit,
    onClose: () -> Unit
) {
    val vars = listOf("MatA", "MatB", "MatC", "MatD")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 限高:面板是「输出区」的子节点,而输出区自身带 verticalScroll ——
            // 不限高时超出部分会被父容器**直接裁掉**(不是滚动),用户点不到那些按键
            // (实测矩阵编辑器的「关闭/平方/立方/Abs」因此消失)。
            .height(PANEL_H)
            .background(Color(0xFF141414))
            .border(1.dp, Color(0xFF3F8F4F), RoundedCornerShape(6.dp))
            .padding(4.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = "矩阵编辑器  $selected = ${rows}×$cols" +
                (if (result.isNotEmpty()) "   $result" else ""),
            color = Color(0xFF9FD89F),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        // 变量选择
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            vars.forEach { v ->
                Key(
                    v.removePrefix("Mat"),
                    Modifier.weight(1f),
                    accent = v == selected,
                    compact = true,
                    onClick = { onPickVar(v) }
                )
            }
        }
        // 维度选择
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Key("1×1", Modifier.weight(1f), compact = true, onClick = { onSetDim(1, 1) })
            Key("2×2", Modifier.weight(1f), compact = true, onClick = { onSetDim(2, 2) })
            Key("3×3", Modifier.weight(1f), compact = true, onClick = { onSetDim(3, 3) })
            Key("4×4", Modifier.weight(1f), compact = true, onClick = { onSetDim(4, 4) })
        }
        // 元素表:每格显示值,当前格高亮
        for (r in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                for (c in 0 until cols) {
                    val idx = r * cols + c
                    val isCur = idx == current
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isCur) Color(0xFF2F6F3F) else Color(0xFF242424))
                            .padding(vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cells.getOrNull(idx)?.let {
                                com.example.casio.core.Fmt.number(it, com.example.casio.core.Settings(), false)
                            } ?: "-",
                            color = if (isCur) Color(0xFFFFFFFF) else Color(0xFFD0D0D0),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        // 待录入格提示
        Text(
            text = if (pending.isEmpty()) "第 ${current + 1} 格:按数字键输入" else "第 ${current + 1} 格: $pending",
            color = Color(0xFFFFC46B),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Key("下一格", Modifier.weight(1f), accent = true, compact = true, onClick = onCommitCell)
            Key("全部清 0", Modifier.weight(1f), compact = true, onClick = onClear)
        }
        // 运算
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Key("Det", Modifier.weight(1f), compact = true, onClick = { onOp("det") })
            Key("Trn", Modifier.weight(1f), compact = true, onClick = { onOp("trn") })
            Key("逆", Modifier.weight(1f), compact = true, onClick = { onOp("inv") })
            Key("单位阵", Modifier.weight(1f), compact = true, onClick = { onOp("identity") })
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Key("平方", Modifier.weight(1f), compact = true, onClick = { onOp("pow2") })
            Key("立方", Modifier.weight(1f), compact = true, onClick = { onOp("pow3") })
            Key("Abs", Modifier.weight(1f), compact = true, onClick = { onOp("abs") })
            // 一键把当前矩阵送进算式栏(如 MatA*MatB、Det(MatA))——
            // 「计算模式页面」里的 MatA 系列插入键在部分机型上滚不到,这里给一条确定可用的路
            Key("去算式栏", Modifier.weight(1.2f), compact = true, onClick = { onOp("toexpr") })
            Key("关闭", Modifier.weight(1f), compact = true, onClick = onClose)
        }
    }
}

/**
 * 向量编辑器:VctA~D、2/3 维、逐分量录入,以及 内积/叉积/模/夹角/单位向量。
 * 与矩阵编辑器同款:数字键累积到"待录入值",按 [下一格] 提交。
 */
@Composable
fun VectorEditor(
    dim: Int,
    cells: List<Double?>,
    current: Int,
    pending: String,
    selected: String,
    result: String,
    onPickVar: (String) -> Unit,
    onSetDim: (Int) -> Unit,
    onCommitCell: () -> Unit,
    onClear: () -> Unit,
    onOp: (String) -> Unit,
    onClose: () -> Unit
) {
    val vars = listOf("VctA", "VctB", "VctC", "VctD")
    val labels = if (dim == 2) listOf("x", "y") else listOf("x", "y", "z")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 限高:面板是「输出区」的子节点,而输出区自身带 verticalScroll ——
            // 不限高时超出部分会被父容器**直接裁掉**(不是滚动),用户点不到那些按键
            // (实测矩阵编辑器的「关闭/平方/立方/Abs」因此消失)。
            .height(PANEL_H)
            .background(Color(0xFF141414))
            .border(1.dp, Color(0xFF3F8F4F), RoundedCornerShape(6.dp))
            .padding(4.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = "向量编辑器  $selected(${dim}维)" + (if (result.isNotEmpty()) "   $result" else ""),
            color = Color(0xFF9FD89F),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            vars.forEach { v ->
                Key(
                    v.removePrefix("Vct"),
                    Modifier.weight(1f),
                    accent = v == selected,
                    compact = true,
                    onClick = { onPickVar(v) }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Key("2 维", Modifier.weight(1f), accent = dim == 2, compact = true, onClick = { onSetDim(2) })
            Key("3 维", Modifier.weight(1f), accent = dim == 3, compact = true, onClick = { onSetDim(3) })
        }
        // 分量表:当前分量高亮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            for (i in 0 until dim) {
                val isCur = i == current
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = labels[i],
                        color = Color(0xFF7A9A7A),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isCur) Color(0xFF2F6F3F) else Color(0xFF242424))
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cells.getOrNull(i)?.let {
                                com.example.casio.core.Fmt.number(it, com.example.casio.core.Settings(), false)
                            } ?: "-",
                            color = if (isCur) Color(0xFFFFFFFF) else Color(0xFFD0D0D0),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        Text(
            text = if (pending.isEmpty()) "第 ${current + 1} 个分量:按数字键输入"
            else "第 ${current + 1} 个分量: $pending",
            color = Color(0xFFFFC46B),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Key("下一格", Modifier.weight(1f), accent = true, compact = true, onClick = onCommitCell)
            Key("全部清 0", Modifier.weight(1f), compact = true, onClick = onClear)
        }
        // 一元运算
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Key("模 |V|", Modifier.weight(1f), compact = true, onClick = { onOp("norm") })
            Key("单位向量", Modifier.weight(1f), compact = true, onClick = { onOp("unit") })
            Key("关闭", Modifier.weight(0.8f), compact = true, onClick = onClose)
        }
        // 二元运算(以当前向量与所选向量配对)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Key("与下一个内积", Modifier.weight(1.4f), compact = true, onClick = { onOp("dot") })
            Key("与下一个叉积", Modifier.weight(1.4f), compact = true, onClick = { onOp("cross") })
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Key("与下一个夹角", Modifier.weight(1.4f), compact = true, onClick = { onOp("angle") })
            Key("与下一个相加", Modifier.weight(1.4f), compact = true, onClick = { onOp("add") })
        }
    }
}

/**
 * 基数(进制)计算面板:对应真机的基数模式。
 *  · 四种进制切换(对应 w / 6 / & / i)
 *  · 显示当前值与当前进制下的表示(二进制按 4 位分组,与真机一致)
 *  · 逻辑运算 and / or / xor / xnor,以及 Not / Neg
 *  · 负数按二位补码
 */
@Composable
fun BaseNPanel(
    base: com.example.casio.core.BaseN.Base,
    valueText: String,
    pending: String,
    pendingOp: String,
    second: String,
    result: String,
    onPickBase: (com.example.casio.core.BaseN.Base) -> Unit,
    onCommit: () -> Unit,
    onClear: () -> Unit,
    onOp: (String) -> Unit,
    onClose: () -> Unit
) {
    val bases = com.example.casio.core.BaseN.Base.entries
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 限高:面板是「输出区」的子节点,而输出区自身带 verticalScroll ——
            // 不限高时超出部分会被父容器**直接裁掉**(不是滚动),用户点不到那些按键
            // (实测矩阵编辑器的「关闭/平方/立方/Abs」因此消失)。
            .height(PANEL_H)
            .background(Color(0xFF141414))
            .border(1.dp, Color(0xFF3F8F4F), RoundedCornerShape(6.dp))
            .padding(4.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = "基数模式   当前 ${base.label}",
            color = Color(0xFF9FD89F),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        // 进制切换
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            bases.forEach { b ->
                Key(
                    b.label,
                    Modifier.weight(1f),
                    accent = b == base,
                    compact = true,
                    onClick = { onPickBase(b) }
                )
            }
        }
        // 当前值(按进制补位显示)
        Text(
            text = valueText,
            color = Color(0xFFE0FFE0),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 3
        )
        // 待录入 / 运算状态
        Text(
            text = when {
                pendingOp.isNotEmpty() && second.isEmpty() -> "已选 ${pendingOp},请输入第二个数"
                pendingOp.isNotEmpty() -> "${pendingOp} 第二个数: $second"
                pending.isEmpty() -> "按数字键输入(十六进制用 A~F 键)"
                else -> "输入: $pending"
            },
            color = Color(0xFFFFC46B),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        if (result.isNotEmpty()) {
            Text(
                text = result,
                color = Color(0xFF9FD89F),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 3
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Key("确定", Modifier.weight(1f), accent = true, compact = true, onClick = onCommit)
            Key("清除", Modifier.weight(1f), compact = true, onClick = onClear)
            Key("关闭", Modifier.weight(1f), compact = true, onClick = onClose)
        }
        // 逻辑运算
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Key("and", Modifier.weight(1f), accent = pendingOp == "and", compact = true, onClick = { onOp("and") })
            Key("or", Modifier.weight(1f), accent = pendingOp == "or", compact = true, onClick = { onOp("or") })
            Key("xor", Modifier.weight(1f), accent = pendingOp == "xor", compact = true, onClick = { onOp("xor") })
            Key("xnor", Modifier.weight(1f), accent = pendingOp == "xnor", compact = true, onClick = { onOp("xnor") })
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Key("Not", Modifier.weight(1f), compact = true, onClick = { onOp("not") })
            Key("Neg", Modifier.weight(1f), compact = true, onClick = { onOp("neg") })
            // 十六进制字母键:基数模式下需要 A~F
            listOf("A", "B", "C").forEach { ch ->
                Key(ch, Modifier.weight(0.7f), compact = true, onClick = { onOp("digit$ch") })
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            listOf("D", "E", "F").forEach { ch ->
                Key(ch, Modifier.weight(1f), compact = true, onClick = { onOp("digit$ch") })
            }
        }
    }
}

/**
 * 比例面板:a : b = c : X(缺第四项)或 a : b = X : d(缺第三项)。
 */
@Composable
fun RatioPanel(
    values: List<String>,      // 四个输入框的内容(a, b, c, d)
    pending: String,           // 当前格的待录入内容
    // 版本号:提交后即使 values/pending 文本没变(空→空),也要强制重组,
    // 否则 activeField 的推进不会反映到界面(实测会串味成 "13")
    version: Int,
    missingIndex: Int,         // 缺哪一项(2 表示 c 位是 X,3 表示 d 位是 X)
    activeField: Int,
    result: String,
    onPickField: (Int) -> Unit,
    onCommit: () -> Unit,
    onToggleMissing: () -> Unit,
    onSolve: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    val names = listOf("a", "b", "c", "d")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .border(1.dp, Color(0xFF3F8F4F), RoundedCornerShape(6.dp))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = "比例计算   a : b = c : d(橙色格为未知项 X)",
            color = Color(0xFF9FD89F),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            names.forEachIndexed { i, n ->
                val isX = i == missingIndex
                // 用 version 参与渲染,确保提交后即使文本没变也重组(见参数注释)
                val shown = if (isX) "X" else if (i == activeField) {
                    if (pending.isEmpty()) values.getOrElse(i) { "" }.ifEmpty { "—" } else pending
                } else values.getOrElse(i) { "" }.ifEmpty { "—" }
                val cellRev = version + i
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isX) "X" else n,
                        color = if (isX) Color(0xFFFFC46B) else Color(0xFF7A9A7A),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Box(
                        // cellRev 参与 modifier:保证 version 被真正读取,从而驱动重组
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when {
                                    isX -> Color(0xFF3A2A14)
                                    i == activeField && cellRev >= 0 -> Color(0xFF2F6F3F)
                                    else -> Color(0xFF242424)
                                }
                            )
                            .clickable { onPickField(i) }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = shown,
                            color = if (isX) Color(0xFFFFC46B) else Color(0xFFE0FFE0),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        Text(
            text = if (pending.isEmpty()) "按数字键输入当前格,再点「下一格」"
            else "当前格输入: $pending",
            color = Color(0xFFFFC46B),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        if (result.isNotEmpty()) {
            Text(
                text = result,
                color = Color(0xFF9FD89F),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 2
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Key("下一格", Modifier.weight(1f), accent = true, compact = true, onClick = onCommit)
            Key(
                if (missingIndex == 3) "改求 c 位" else "改求 d 位",
                Modifier.weight(1.2f),
                compact = true,
                onClick = onToggleMissing
            )
            Key("求解", Modifier.weight(1f), accent = true, compact = true, onClick = onSolve)
            Key("清除", Modifier.weight(0.8f), compact = true, onClick = onClear)
            Key("关闭", Modifier.weight(0.8f), compact = true, onClick = onClose)
        }
    }
}

/**
 * 不等式面板:输入一~四次多项式系数与关系,给出区间解。
 */
@Composable
fun IneqPanel(
    degree: Int,
    coeffs: List<String>,
    pending: String,
    // 版本号:提交后即使文本没变也要重组,否则格的推进不显示(实测会串味)
    version: Int,
    rel: com.example.casio.core.Ineq.Rel,
    activeField: Int,
    result: String,
    onSetDegree: (Int) -> Unit,
    onPickField: (Int) -> Unit,
    onCommit: () -> Unit,
    onToggleSign: () -> Unit,
    onSetRel: (com.example.casio.core.Ineq.Rel) -> Unit,
    onSolve: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 限高:面板是「输出区」的子节点,而输出区自身带 verticalScroll ——
            // 不限高时超出部分会被父容器**直接裁掉**(不是滚动),用户点不到那些按键
            // (实测矩阵编辑器的「关闭/平方/立方/Abs」因此消失)。
            .height(PANEL_H)
            .background(Color(0xFF141414))
            .border(1.dp, Color(0xFF3F8F4F), RoundedCornerShape(6.dp))
            .padding(4.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = "解不等式   ${degree} 次(系数按降幂,高次在前)",
            color = Color(0xFF9FD89F),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        // 次数
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            for (d in 1..4) {
                Key(
                    "$d 次",
                    Modifier.weight(1f),
                    accent = d == degree,
                    compact = true,
                    onClick = { onSetDegree(d) }
                )
            }
        }
        // 系数输入格(降幂)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            for (i in 0..degree) {
                val power = degree - i
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when (power) {
                            0 -> "常数"
                            1 -> "x"
                            else -> "x^$power"
                        },
                        color = Color(0xFF7A9A7A),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (i == activeField) Color(0xFF2F6F3F) else Color(0xFF242424))
                            .clickable { onPickField(i) }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (i == activeField && pending.isNotEmpty()) pending
                            else coeffs.getOrElse(i) { "" }.ifEmpty { "—" },
                            color = Color(0xFFE0FFE0),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        // 关系
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            com.example.casio.core.Ineq.Rel.entries.forEach { r ->
                Key(
                    r.label,
                    Modifier.weight(1f),
                    accent = r == rel,
                    compact = true,
                    onClick = { onSetRel(r) }
                )
            }
        }
        if (result.isNotEmpty()) {
            Text(
                text = result,
                color = Color(0xFF9FD89F),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 4
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Key(
                // version 参与文本:确保它被读取,提交后能正确重组
                "下一格(${activeField + 1}/${degree + 1})" + if (version < 0) "!" else "",
                Modifier.weight(1.4f),
                accent = true,
                compact = true,
                onClick = onCommit
            )
            Key(if (pending.startsWith("-")) "去负号" else "加负号", Modifier.weight(1f), compact = true, onClick = onToggleSign)
            Key("求解", Modifier.weight(1f), accent = true, compact = true, onClick = onSolve)
            Key("清除", Modifier.weight(0.8f), compact = true, onClick = onClear)
            Key("关闭", Modifier.weight(0.8f), compact = true, onClick = onClose)
        }
    }
}

/**
 * 命令抽屉。系统键盘已取消,所有原本要在算式栏里手打 `:xxx` 的命令
 * 都必须有按键入口,这里集中提供。
 * 每项:显示名 → 插入内容("BLOCK:int" 表示插入可编辑块,"TEXT:xxx" 表示插入文本)
 */
@Composable
fun CommandDrawer(onPick: (String) -> Unit) {
    val groups = listOf(
        "计算模式:积分 / 求导" to listOf(
            "∫ 定积分" to "BLOCK::int",
            "d/dx 求导" to "BLOCK::diff"
        ),
        "常数 / 换算 / 求根" to listOf(
            "科学常数表" to "TEXT::const",
            "换算表" to "TEXT::conv 列表",
            "公制换算" to "TEXT::conv 1 in cm",
            "SOLVE 求根" to "TEXT::solve x^2-2=0 x 1"
        ),
        "CALC / TABLE" to listOf(
            "函数值表" to "TEXT::table x^2 1 5 1",
            "双函数表" to "TEXT::table x 0 3 1 x^2",
            "CALC 代入" to "TEXT::calc x^2+y x=3 y=4"
        ),
        "统计" to listOf(
            "1 变量统计" to "TEXT::stat 1 ",
            "2 变量统计" to "TEXT::stat 2 ",
            "正态 P/Q/R" to "TEXT::norm "
        ),
        "方程 / 方程组" to listOf(
            "2 元一次" to "TEXT:x+y=3, x-y=1",
            "3 元一次" to "TEXT:x+y+z=6, x-y+z=2, x+y-z=2"
        ),
        "变量与设定" to listOf(
            "变量赋值" to "TEXT::set ",
            "查看变量" to "TEXT::vars",
            "清空变量" to "TEXT::clear",
            "设定 SETUP" to "TEXT::setup",
            "进制转换" to "TEXT::bin "
        ),
        "其他" to listOf(
            "帮助" to "TEXT::help"
        )
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 固定高度(不是 heightIn / max):
            //  ① 抽屉是「输出区」的子节点,而输出区自身带 verticalScroll,
            //     父滚动会吞掉子树的滑动手势 —— 只有给抽屉一个**确定高度**并让它内部滚动,
            //     才不会被父容器裁掉(之前用 heightIn,max 在无界约束下等于没限制,内容被裁)。
            //  ② 用固定高度还能保证首屏之外的分组可达。
            .height(250.dp)
            .background(Color(0xFF141414))
            .border(1.dp, Color(0xFF3F8F4F), RoundedCornerShape(6.dp))
            .padding(4.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = "命令抽屉 · 计算模式(系统键盘已取消,所有 :命令 都在这里)",
            color = Color(0xFF9FD89F),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        groups.forEach { (title, items) ->
            Text(
                text = title,
                color = Color(0xFF7A9A7A),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            items.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    row.forEach { (label, payload) ->
                        Key(
                            label,
                            Modifier.weight(1f),
                            compact = true,
                            onClick = { onPick(payload) }
                        )
                    }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * 字母键盘:点「切换」后替换下侧的**数字与符号区**。
 * 26 个字母 + 空格 / 退格 / 逗号,共 6 列 5 行。
 * SHIFT 处于按下状态时输出大写。
 */
@Composable
fun AlphaKeypad(
    upper: Boolean,
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onComma: () -> Unit,
    onSwitchBack: () -> Unit,
    onLeft: () -> Unit = {},
    onRight: () -> Unit = {},
    onClear: () -> Unit = {},
    onEval: () -> Unit = {},
    onSolve: () -> Unit = {}
) {
    // 标准 QWERTY 三行:qwertyuiop / asdfghjkl / zxcvbnm。
    // 行首用 Spacer 做阶梯缩进,模仿真实键盘的排布;键做得窄而高(竖长方形)。
    val rows = listOf(
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
        listOf("Z", "X", "C", "V", "B", "N", "M")
    )
    // 每行左右缩进权重(相对该行剩余空间)。
    // 目的是把每行收窄到屏幕的 6 成左右,让单键宽度接近键高 —— 即"竖长方形";
    // 同时行首缩进做成阶梯,模仿真实键盘的错位排布。
    val leadWeight = listOf(0.25f, 1.15f, 2.4f)
    val trailWeight = listOf(1.6f, 2.1f, 3.0f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .verticalScroll(rememberScrollState())
    ) {
        rows.forEachIndexed { ri, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (ri == 0) 0.dp else 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadWeight[ri] > 0f) {
                    Box(Modifier.weight(leadWeight[ri]))
                }
                row.forEachIndexed { ci, label ->
                    if (ci > 0) Box(Modifier.width(2.dp))
                    Key(
                        if (upper) label else label.lowercase(),
                        Modifier.weight(1f),
                        compact = true,
                        tall = true,
                        onClick = { onKey(if (upper) label else label.lowercase()) }
                    )
                }
                if (trailWeight[ri] > 0f) {
                    Box(Modifier.weight(trailWeight[ri]))
                }
            }
        }
        // 控制行:空格 / 逗号 / 方向 / 退格 / 清除 / 返回数字键
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Key("空格", Modifier.weight(1.4f), compact = true, onClick = onSpace)
            Key(",", Modifier.weight(0.8f), compact = true, onClick = onComma)
            Key("←", Modifier.weight(0.8f), compact = true, onClick = onLeft)
            Key("→", Modifier.weight(0.8f), compact = true, onClick = onRight)
            Key("⌫", Modifier.weight(0.8f), compact = true, onClick = onBackspace)
            Key("C", Modifier.weight(0.8f), compact = true, onClick = onClear)
            Key("返回数字键", Modifier.weight(2f), compact = true, onClick = onSwitchBack)
        }
        // 执行行:字母区占满高度后会挤掉外面的执行键排,所以在这里也放一份,
        // 保证「字母模式下一定能求值」。
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Key("= 求值", Modifier.weight(2f), accent = true, compact = true, onClick = onEval)
            Key("解 方程", Modifier.weight(2f), accent = true, compact = true, onClick = onSolve)
            Key("AC 全清", Modifier.weight(1.2f), compact = true, onClick = onClear)
        }
    }
}

/**
 * 方程(F)面板:对应真机的 EQN 模式。
 *  · 联立方程:2 / 3 / 4 个未知数,系数网格 + 常数项
 *  · 多项式方程:2 / 3 / 4 次,降幂系数
 * 数字键累积到"待录入值",按「下一格」提交(与矩阵/向量编辑器同一套交互)。
 */
@Composable
fun EqnPanel(
    kind: String,                 // "sim" 联立 / "poly" 多项式
    count: Int,                   // 未知数个数 或 次数
    coeffs: List<String>,         // 联立:n*(n+1) 个;多项式:n+1 个降幂系数
    pending: String,
    activeField: Int,
    score: Int,                   // 版本号:提交后强制重组
    result: String,
    onSetKind: (String) -> Unit,
    onSetCount: (Int) -> Unit,
    onPickField: (Int) -> Unit,
    onCommit: () -> Unit,
    onToggleSign: () -> Unit,
    onSolve: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    val vars = listOf("x", "y", "z", "t")
    val cellCount = if (kind == "sim") count * (count + 1) else count + 1
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 限高:面板是「输出区」的子节点,而输出区自身带 verticalScroll ——
            // 不限高时超出部分会被父容器**直接裁掉**(不是滚动),用户点不到那些按键
            // (实测矩阵编辑器的「关闭/平方/立方/Abs」因此消失)。
            .height(PANEL_H)
            .background(Color(0xFF141414))
            .border(1.dp, Color(0xFF3F8F4F), RoundedCornerShape(6.dp))
            .padding(4.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = "方程面板  " + if (kind == "sim") "联立方程 $count 元" else "多项式方程 $count 次",
            color = Color(0xFF9FD89F),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Key("联立方程", Modifier.weight(1f), accent = kind == "sim", compact = true, onClick = { onSetKind("sim") })
            Key("多项式", Modifier.weight(1f), accent = kind == "poly", compact = true, onClick = { onSetKind("poly") })
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            for (n in 2..4) {
                Key(
                    if (kind == "sim") "$n 元" else "$n 次",
                    Modifier.weight(1f),
                    accent = n == count,
                    compact = true,
                    onClick = { onSetCount(n) }
                )
            }
        }
        if (kind == "sim") {
            for (r in 0 until count) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (c in 0 until count) {
                        val idx = r * (count + 1) + c
                        val isCur = idx == activeField
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (isCur) Color(0xFF2F6F3F) else Color(0xFF242424))
                                .clickable { onPickField(idx) }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = fieldLabel(coeffs, idx, pending, isCur),
                                color = Color(0xFFE0FFE0),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = vars[c % 4],
                            color = Color(0xFF7A9A7A),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(14.dp)
                        )
                    }
                    Text("=", color = Color(0xFF7A9A7A), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    val ci = r * (count + 1) + count
                    val curC = ci == activeField
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (curC) Color(0xFF2F6F3F) else Color(0xFF242424))
                            .clickable { onPickField(ci) }
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = fieldLabel(coeffs, ci, pending, curC),
                            color = Color(0xFFE0FFE0),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (i in 0..count) {
                    val power = count - i
                    val isCur = i == activeField
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = when (power) {
                                0 -> "常数"
                                1 -> "x"
                                else -> "x^$power"
                            },
                            color = Color(0xFF7A9A7A),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (isCur) Color(0xFF2F6F3F) else Color(0xFF242424))
                                .clickable { onPickField(i) }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = fieldLabel(coeffs, i, pending, isCur),
                                color = Color(0xFFE0FFE0),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            Text(
                text = "按降幂输入系数,末格是常数项",
                color = Color(0xFF7A9A7A),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            text = if (pending.isEmpty()) "第 ${activeField + 1} 格:按数字键输入(共 $cellCount 格)"
            else "第 ${activeField + 1} 格输入: $pending",
            color = Color(0xFFFFC46B),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        if (result.isNotEmpty()) {
            Text(
                text = result,
                color = Color(0xFF9FD89F),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 6
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Key(
                "下一格(${activeField + 1}/$cellCount)" + if (score < 0) "!" else "",
                Modifier.weight(1.5f),
                accent = true,
                compact = true,
                onClick = onCommit
            )
            Key(if (pending.startsWith("-")) "去负号" else "加负号", Modifier.weight(1f), compact = true, onClick = onToggleSign)
            Key("求解", Modifier.weight(1f), accent = true, compact = true, onClick = onSolve)
            Key("清除", Modifier.weight(0.8f), compact = true, onClick = onClear)
            Key("关闭", Modifier.weight(0.8f), compact = true, onClick = onClose)
        }
    }
}

/** 系数格显示:当前格优先显示待录入内容 */
private fun fieldLabel(coeffs: List<String>, idx: Int, pending: String, isCur: Boolean): String {
    if (isCur && pending.isNotEmpty()) return pending
    return coeffs.getOrElse(idx) { "" }.ifEmpty { "—" }
}

/** 输入页的全部按键都做在输入框所在区域里;这里只提供按键排布 */
@Composable
fun EditorKeypad(
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onHome: () -> Unit,
    onEnd: () -> Unit,
    onEval: () -> Unit,
    onSolve: () -> Unit,
    onClearAll: () -> Unit,
    onHelp: () -> Unit,
    onHistUp: () -> Unit = {},
    onHistDown: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Key("⇤", Modifier.weight(1f), compact = true, onClick = onHome)
            Key("←", Modifier.weight(1f), compact = true, onClick = onLeft)
            Key("→", Modifier.weight(1f), compact = true, onClick = onRight)
            Key("⇥", Modifier.weight(1f), compact = true, onClick = onEnd)
            // 计算历史重放:上下翻阅已执行的算式(真机的 d/e 键)
            Key("↑史", Modifier.weight(1f), compact = true, onClick = onHistUp)
            Key("↓史", Modifier.weight(1f), compact = true, onClick = onHistDown)
            Key("⌫", Modifier.weight(1f), compact = true, onClick = onBackspace)
            Key("C", Modifier.weight(1f), compact = true, onClick = onClear)
        }
        val rows = listOf(
            listOf("7", "8", "9", "(", ")", "^"),
            // 第 2 行末位放 √(键上写 √,插入时补左括号,避免"2√"这种不完整式子)
            listOf("4", "5", "6", "+", "-", "√"),
            listOf("1", "2", "3", "/", "=", ","),
            // 最后一位放 z(三元一次方程组要用);阶乘已移到函数抽屉的 fact
            listOf("0", ".", "x", "y", "z", "%")
        )
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { label ->
                    Key(
                        label,
                        Modifier.weight(1f),
                        // √ 需要参数:插入时补上左括号
                        onClick = { onKey(com.example.casio.core.Edit.insertTextFor(com.example.casio.core.IoMode.MATH_MATH, label)) }
                    )
                }
            }
        }
        // 常用函数键行(在求值行的上面一行)
        Box(Modifier.padding(top = 3.dp)) { AccessRow(onKey) }
        // 执行键:固定在这一块里,不会被挤出屏幕
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Key("= 求值", Modifier.weight(1f), accent = true, onClick = onEval)
            Key("解 方程", Modifier.weight(1f), accent = true, onClick = onSolve)
            Key("AC 全清", Modifier.weight(0.8f), compact = true, onClick = onClearAll)
            Key("help", Modifier.weight(0.6f), compact = true, onClick = onHelp)
        }
    }
}
