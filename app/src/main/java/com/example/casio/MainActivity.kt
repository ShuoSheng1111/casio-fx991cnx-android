package com.example.casio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.example.casio.core.BaseN
import com.example.casio.core.Calc
import com.example.casio.core.CalcEngine
import com.example.casio.core.Display
import com.example.casio.core.Edit
import com.example.casio.core.Errors
import com.example.casio.core.Ineq
import com.example.casio.core.Lcd
import com.example.casio.core.LcdRender
import com.example.casio.core.Mat
import com.example.casio.core.Matrix
import com.example.casio.core.Ratio
import com.example.casio.core.Settings
import com.example.casio.core.Solver
import com.example.casio.core.Stats
import com.example.casio.core.Vec
import com.example.casio.core.Vector
import kotlin.math.abs

/** 深色配色(与 Windows 版一致) */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF2F6F3F),
    onPrimary = Color.White,
    background = Color(0xFF1E1E1E),
    surface = Color(0xFF101010),
    onSurface = Color(0xFF7DFA7D)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 引擎自测(移植期验收手段,结果写进 logcat)
        val report = com.example.casio.core.EngineSelfTest.run()
        android.util.Log.i("CASIO_SELFTEST", "=== 引擎自测开始 ===")
        report.trim().split("\n").forEach { android.util.Log.i("CASIO_SELFTEST", it) }
        android.util.Log.i("CASIO_SELFTEST", "=== 引擎自测结束 ===")

        setContent {
            MaterialTheme(colorScheme = DarkColors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CalculatorScreen()
                }
            }
        }
    }
}

@Composable
fun CalculatorScreen() {
    val engine = remember { CalcEngine() }
    // 输入内容用「原子块列表 + 光标」表示,支持 :int 这类整体块
    var parts by remember { mutableStateOf<List<Edit.Part>>(listOf(Edit.Raw(""))) }
    var cursor by remember { mutableStateOf(Edit.Cursor(0, 0, 0)) }
    var funcsOpen by remember { mutableStateOf(false) }    // 函数抽屉
    var statsOpen by remember { mutableStateOf(false) }    // 统计模式
    var statsMode by remember { mutableStateOf("1") }      // 1 变量 / 2 变量
    // ---- 第 1 批:模式、键前缀、存储器、历史 ----
    var mode by remember { mutableStateOf(Calc.Mode.COMP) }
    var keyMods by remember { mutableStateOf(Calc.KeyMods()) }
    var homeOpen by remember { mutableStateOf(false) }
    val memory = remember { Calc.Memory() }
    val history = remember { Calc.History() }
    var memVersion by remember { mutableStateOf(0) }        // 存储器变化用于刷新指示符

    // 计算历史持久化:启动时恢复一次(重启后仍能翻阅之前算过的式子)
    val historyContext = LocalContext.current
    LaunchedEffect(Unit) {
        val saved = HistoryStore.load(historyContext)
        if (saved.isNotEmpty()) history.restore(saved)
    }
    // ---- 第 2 批:统计编辑器 ----
    var statData by remember { mutableStateOf(Stats.StatData(twoVar = false)) }
    var editorOpen by remember { mutableStateOf(false) }
    var pendingValue by remember { mutableStateOf("") }
    var editorVersion by remember { mutableStateOf(0) }
    // ---- 第 3 批:矩阵编辑器 ----
    var matOpen by remember { mutableStateOf(false) }
    var matSelected by remember { mutableStateOf("MatA") }
    var matRows by remember { mutableStateOf(2) }
    var matCols by remember { mutableStateOf(2) }
    var matCells by remember { mutableStateOf(List<Double?>(4) { null }) }
    var matCurrent by remember { mutableStateOf(0) }
    var matPending by remember { mutableStateOf("") }
    var matResult by remember { mutableStateOf("") }
    val matVars = remember { mutableMapOf<String, Matrix>() }
    var matVersion by remember { mutableStateOf(0) }

    // ---- 第 4 批:向量编辑器 ----
    var vecOpen by remember { mutableStateOf(false) }
    var vecSelected by remember { mutableStateOf("VctA") }
    var vecDim by remember { mutableStateOf(2) }
    var vecCells by remember { mutableStateOf(List<Double?>(3) { null }) }
    var vecCurrent by remember { mutableStateOf(0) }
    var vecPending by remember { mutableStateOf("") }
    var vecResult by remember { mutableStateOf("") }
    val vecVars = remember { mutableMapOf<String, Vector>() }

    /** 把当前分量写回 VctX */
    fun commitVector() {
        val d = vecDim
        val v = (0 until d).map { vecCells.getOrNull(it) ?: 0.0 }
        val vec = if (d == 2) Vector.of2(v[0], v[1]) else Vector.of3(v[0], v[1], v[2])
        vecVars[vecSelected] = vec
        // 同步给引擎,这样算式栏里能直接写 VctA·VctB
        engine.vectorEnv[vecSelected] = vec
    }

    /** 二元运算的配对向量:按 A→B→C→D 循环取下一个 */
    fun nextVectorName(): String {
        val order = listOf("VctA", "VctB", "VctC", "VctD")
        return order[(order.indexOf(vecSelected) + 1) % order.size]
    }

    // ---- 第 5 批:基数模式 ----
    var baseOpen by remember { mutableStateOf(false) }
    var baseSel by remember { mutableStateOf(BaseN.Base.DEC) }
    var baseValue by remember { mutableStateOf(0) }
    var basePending by remember { mutableStateOf("") }
    var baseOp by remember { mutableStateOf("") }
    var baseSecond by remember { mutableStateOf("") }
    var baseResult by remember { mutableStateOf("") }
    // ---- 命令抽屉(系统键盘取消后,所有 :命令 的按键入口) ----
    var cmdOpen by remember { mutableStateOf(false) }
    // ---- 切换键:字母键盘替换下侧的数字与符号区 ----
    var alphaPad by remember { mutableStateOf(false) }

    // ---- 第 6 批:方程(EQN)面板 ----
    var eqnOpen by remember { mutableStateOf(false) }

    /** 把编辑器里已录入的矩阵/向量全部同步给引擎(算式栏要用 MatA 这类名字) */
    fun syncMatVecToEngine() {
        matVars.forEach { (k, v) -> engine.matrixEnv[k] = v }
        vecVars.forEach { (k, v) -> engine.vectorEnv[k] = v }
    }
    var eqnKind by remember { mutableStateOf("sim") }      // sim 联立 / poly 多项式
    var eqnCount by remember { mutableStateOf(2) }         // 未知数个数或次数
    var eqnCoeffs by remember { mutableStateOf(listOf<String>()) }
    var eqnPending by remember { mutableStateOf("") }
    var eqnActive by remember { mutableStateOf(0) }
    var eqnScore by remember { mutableStateOf(0) }
    var eqnResult by remember { mutableStateOf("") }

    /** 面板格子数随类型/元数变化 */
    fun eqnCellCount(): Int = if (eqnKind == "sim") eqnCount * (eqnCount + 1) else eqnCount + 1

    /** 切换类型或元数时重建系数格 */
    fun resetEqn(kind: String, count: Int) {
        eqnKind = kind
        eqnCount = count
        eqnCoeffs = List(if (kind == "sim") count * (count + 1) else count + 1) { "" }
        eqnActive = 0
        eqnPending = ""
        eqnResult = ""
        eqnScore++
    }

    // ---- 第 6 批界面:比例 / 不等式 ----
    var ratioOpen by remember { mutableStateOf(false) }
    var ratioFields by remember { mutableStateOf(listOf("", "", "", "")) }
    var ratioMissing by remember { mutableStateOf(3) }   // 缺哪一项(2 = c 位,3 = d 位)
    var ratioActive by remember { mutableStateOf(0) }
    var ratioResult by remember { mutableStateOf("") }
    var ratioPending by remember { mutableStateOf("") }
    var ratioVersion by remember { mutableStateOf(0) }
    var ineqOpen by remember { mutableStateOf(false) }
    var ineqDegree by remember { mutableStateOf(2) }
    var ineqCoeffs by remember { mutableStateOf(listOf("", "", "", "", "")) }
    var ineqRel by remember { mutableStateOf(Ineq.Rel.LT) }
    var ineqActive by remember { mutableStateOf(0) }
    var ineqResult by remember { mutableStateOf("") }
    var ineqPending by remember { mutableStateOf("") }
    var ineqVersion by remember { mutableStateOf(0) }

    /** 把当前格子写回 MatX */
    fun commitMatrix() {
        val rowsIn = (0 until matRows).map { r -> (0 until matCols).map { c -> matCells.getOrNull(r * matCols + c) ?: 0.0 } }
        val m = Matrix.fromRows(rowsIn)
        matVars[matSelected] = m
        // 同步给引擎,这样算式栏里能直接写 MatA*MatB
        engine.matrixEnv[matSelected] = m
        matVersion++
    }
    var output by remember { mutableStateOf("") }
    // LCD 竖排显示用:最近一次可求值的表达式与其数值(为 null 时退回文本框)
    var lcdExpr by remember { mutableStateOf<String?>(null) }
    var lcdValue by remember { mutableStateOf<Double?>(null) }
    // 编辑版本号:parts 是"装着可变对象的列表",浅拷贝后新旧列表结构相等,
    // Compose 会认为状态没变而跳过重组(表现为输入了数字但算式栏不更新)。
    // 每次编辑递增版本号,强制触发重组。
    var editVersion by remember { mutableStateOf(0) }
    var arrowsOpen by remember { mutableStateOf(false) }   // 方向键抽屉
    val outputScroll = rememberScrollState()

    // ---- 自动滚动所需的尺寸与布局信息 ----
    var outputBlocks by remember { mutableStateOf(0) }      // 已有多少段输出
    var newBlockStart by remember { mutableStateOf(-1) }    // 本次新增输出的起始位置
    var newBlockEnd by remember { mutableStateOf(-1) }      // 本次新增输出的结束位置
    var viewportH by remember { mutableStateOf(0) }         // 显示区可视高度(px)
    var lineH by remember { mutableStateOf(0f) }            // 一行的高度(px)
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var contentHeight by remember { mutableStateOf(0) }     // 内容总高度(px)

    // 有新输出时滚动显示区。放在 LaunchedEffect 里是为了等布局稳定:
    // 在 onTextLayout 里滚动时 maxValue 还是上一帧的旧值,会把滚动量截断。
    // 规则:新输出的顶端对齐显示区顶端;若内容不够长拉不到顶端,则把底端对齐显示区底端。
    LaunchedEffect(newBlockStart, output) {
        if (newBlockStart >= 0 && newBlockEnd > newBlockStart) {
            val lr = layoutResult
            if (lr != null && lr.layoutInput.text.length >= newBlockEnd) {
                val contentTop = lr.getLineTop(0).toFloat()
                val topOf = lr.getLineTop(lr.getLineForOffset(newBlockStart)).toFloat() - contentTop
                val bottomOf = lr.getLineBottom(lr.getLineForOffset(newBlockEnd - 1)).toFloat() - contentTop
                val viewport = viewportH.toFloat()
                val maxScroll = (contentHeight.toFloat() - viewport).coerceAtLeast(0f)
                val wantTop = topOf
                val wantBottom = (bottomOf - viewport).coerceAtLeast(0f)
                val target = if (wantTop <= maxScroll) wantTop else wantBottom.coerceAtMost(maxScroll)
                outputScroll.scrollTo(target.coerceIn(0f, maxScroll).toInt())
            }
        }
    }

    fun exprText(): String = Edit.getText(parts)

    /** 用可变副本执行一次编辑操作,再写回状态 */
    fun edit(op: (MutableList<Edit.Part>, Edit.Cursor) -> Edit.Cursor) {
        val copy = parts.toMutableList()
        cursor = op(copy, cursor)
        parts = copy
        editVersion++
    }

    /** 光标跳到某个字段末尾 */
    fun focusField(pi: Int, fi: Int) {
        val p = parts.getOrNull(pi) ?: return
        val len = when (p) {
            is Edit.Raw -> p.text.length
            is Edit.Block -> p.fields.getOrNull(fi)?.value?.length ?: 0
        }
        cursor = Edit.Cursor(pi, fi, len)
    }

    fun insert(text: String) = edit { ps, c -> Edit.insert(ps, c, text) }
    fun backspace() = edit { ps, c -> Edit.backspace(ps, c) }
    fun cursorLeft() = edit { ps, c -> Edit.moveLeft(ps, c) }
    fun cursorRight() = edit { ps, c -> Edit.moveRight(ps, c) }
    fun cursorHome() { cursor = Edit.Cursor(0, 0, 0) }
    fun cursorEnd() {
        val pi = parts.size - 1
        if (pi < 0) return
        when (val p = parts[pi]) {
            is Edit.Raw -> cursor = Edit.Cursor(pi, 0, p.text.length)
            is Edit.Block -> {
                val fi = p.fields.size - 1
                cursor = Edit.Cursor(pi, fi, p.fields[fi].value.length)
            }
        }
    }

    fun clearAll() {
        parts = listOf(Edit.Raw(""))
        cursor = Edit.Cursor(0, 0, 0)
    }

    fun run(forceEquation: Boolean) {
        var line = exprText().trim()
        if (line.isEmpty()) return
        if (forceEquation && !line.contains("=")) line += "=0"
        val raw = engine.process(line)
        // 错误系统:把引擎的错误文本升级成「类型 + 出错位置指示」,便于定位
        val result = if (raw.startsWith("错误") || raw.startsWith("Syntax") ||
            raw.startsWith("Math") || raw.startsWith("Argument") ||
            raw.startsWith("Range") || raw.startsWith("Variable")
        ) {
            val msg = raw.removePrefix("错误:").removePrefix("错误:").trim()
            val rep = Errors.report(line, msg)
            buildString {
                append("${rep.kind.label}  ${rep.message}\n")
                append(Errors.markInline(line, rep.position))
                append("\n")
                val ptr = rep.pointer()
                if (ptr.isNotEmpty()) append(ptr).append("\n")
                append("\n").append(rep.kind.hint)
            }
        } else {
            raw
        }
        // 记下「本次新增输出」在整段文本里的起止位置,布局完成后据此滚动
        val prefix = if (output.isEmpty()) "" else "$output\n"
        newBlockStart = prefix.length
        val block = ">>> $line\n$result\n"
        newBlockEnd = prefix.length + block.length
        output = prefix + block
        outputBlocks++
        // 存储器:记录 Ans、写入历史(第 1 批)
        engine.evalValue(line)?.let {
            memory.setAns(it)
            memVersion++
        }
        history.add(line)
        // 历史持久化:每次新增都落盘(条目最多 50,开销可忽略)
        HistoryStore.save(historyContext, history.entries())
        // LCD 竖排显示:只对"能算出单个数值"的输入启用(分数/根号等精确形式竖排)
        val value = engine.evalValue(line)
        if (value != null) {
            lcdExpr = line
            lcdValue = value
        } else {
            lcdExpr = null
            lcdValue = null
        }
        clearAll()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(6.dp)
    ) {

        // ================= 顶部:输出显示区 =================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.36f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0A0A0A))
                .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(6.dp))
                .padding(8.dp)
        ) {
            // 顶部:LCD 竖排显示(分数/根号画成真图形) + 指示符
            // 真机是「输入行 + 输出行」两行;这里输出行画成形,输入行用文本预览,
            // 下方仍保留可点击编辑的算式栏(点字段定位光标)。
            val exprNode = lcdExpr
            val valNode = lcdValue
            if (exprNode != null && valNode != null) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(outputScroll)
                ) {
                    IndicatorRow(
                        symbols = Lcd.Indicators.of(
                            engine.settings,
                            memoryUsed = memory.mUsed(),
                            complexMode = mode == Calc.Mode.CMPLX
                        ).symbols() + keyMods.symbols(),
                        color = Color(0xFF7DFA7D)
                    )
                    Box(Modifier.height(4.dp))
                    // 输入行:当前正在排的式子;接近上限时显示剩余字符提示(真机换成 k 形光标)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = Edit.displayText(parts),
                            color = Color(0xFFBFFFC0),
                            fontFamily = FontFamily.Monospace,
                            fontSize = if (engine.settings.smallFont) 12.sp else 14.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (Edit.nearLimit(parts)) {
                            Box(Modifier.width(6.dp))
                            Text(
                                text = "k" + Edit.remainBytes(parts),
                                color = Color(0xFFFFC46B),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                    Box(Modifier.height(6.dp))
                    // 输出行:结果(竖排分数/根号)
                    // 多行字体设定:普通字体最多 4 行、字号大;小字体最多 6 行、字号小
                    LcdView(
                        node = LcdRender.ofNumber(valNode, engine.settings),
                        baseSize = if (engine.settings.smallFont) 20.sp else 30.sp,
                        color = Color(0xFF7DFA7D)
                    )
                    Box(Modifier.height(4.dp))
                    // 同时保留可滚动文本历史,便于回看
                    Text(
                        text = output,
                        color = Color(0xFF5FBF5F),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        onTextLayout = { lr ->
                            lineH = (lr.getLineBottom(0) - lr.getLineTop(0)).toFloat()
                            layoutResult = lr
                            contentHeight = lr.size.height
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                val shown = if (output.isEmpty()) "就绪:按下方按键排式子,再按 [ = 求值 ]" else output
                Text(
                    text = shown,
                    color = Color(0xFF7DFA7D),
                    fontFamily = FontFamily.Monospace,
                    // 框线输出宽 54 列,手机屏宽有限,字号压到 9sp 才不会被裁掉右侧
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    onTextLayout = { lr ->
                        // 行高 = 第一行上下边界之差(TextLayoutResult 没有 lineHeightPx 属性)
                        lineH = (lr.getLineBottom(0) - lr.getLineTop(0)).toFloat()
                        layoutResult = lr
                        // 记录本帧测得的内容高度,真正的滚动放到布局稳定后由 LaunchedEffect 执行:
                        // 在布局回调里滚动时 maxValue 还是上一帧的旧值,会把滚动量截断。
                        contentHeight = lr.size.height
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewportH = it.height }
                        .verticalScroll(outputScroll)
                )
            }

            // ---- 函数抽屉:同样做成覆盖层,不在输入区里占高度,
            //      这样数字键与求值键的位置永远稳定 ----
            if (funcsOpen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Color(0xFF0E0E0E))
                ) {
                    FunctionDrawer(onPick = { insert(it) })
                }
            }

            // ---- 命令抽屉:输出区覆盖层 ----
            if (cmdOpen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                ) {
                    CommandDrawer(onPick = { payload ->
                        when {
                            payload == "BLOCK::int" -> edit { ps, _ ->
                                ps.add(Edit.newIntegral()); Edit.Cursor(ps.size - 1, 0, 0)
                            }
                            payload == "BLOCK::diff" -> edit { ps, _ ->
                                ps.add(Edit.newDiff()); Edit.Cursor(ps.size - 1, 0, 0)
                            }
                            payload == "BLOCK::lim" -> edit { ps, _ ->
                                ps.add(Edit.newLimit()); Edit.Cursor(ps.size - 1, 0, 0)
                            }
                            payload.startsWith("TEXT:") -> {
                                insert(payload.removePrefix("TEXT:"))
                            }
                        }
                        cmdOpen = false
                    })
                }
            }

            // ---- 方程(EQN)面板:输出区覆盖层 ----
            if (eqnOpen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                ) {
                    EqnPanel(
                        kind = eqnKind,
                        count = eqnCount,
                        coeffs = eqnCoeffs,
                        pending = eqnPending,
                        activeField = eqnActive,
                        score = eqnScore,
                        result = eqnResult,
                        onSetKind = { k -> resetEqn(k, eqnCount) },
                        onSetCount = { n -> resetEqn(eqnKind, n) },
                        onPickField = { i -> eqnActive = i },
                        onCommit = {
                            val t = eqnPending.trim()
                            if (t.isNotEmpty()) {
                                val m = eqnCoeffs.toMutableList()
                                while (m.size <= eqnActive) m.add("")
                                m[eqnActive] = t
                                eqnCoeffs = m
                                eqnPending = ""
                                if (eqnActive < eqnCellCount() - 1) eqnActive++
                                eqnScore++
                            }
                        },
                        onToggleSign = {
                            eqnPending = if (eqnPending.startsWith("-")) eqnPending.drop(1) else "-$eqnPending"
                            eqnScore++
                        },
                        onSolve = {
                            // 求解前先把待录入值提交,避免漏按「下一格」
                            val p = eqnPending.trim()
                            var cs = eqnCoeffs
                            if (p.isNotEmpty()) {
                                val m = cs.toMutableList()
                                while (m.size <= eqnActive) m.add("")
                                m[eqnActive] = p
                                cs = m
                                eqnCoeffs = m
                                eqnPending = ""
                            }
                            eqnResult = try {
                                fun num(i: Int): Double {
                                    val t = cs.getOrElse(i) { "" }.trim()
                                    return if (t.isEmpty()) 0.0
                                    else t.toDoubleOrNull() ?: throw IllegalArgumentException("第 ${i + 1} 格不是数值")
                                }
                                if (eqnKind == "sim") {
                                    // 从系数网格拼出方程,交给引擎的线性方程组求解
                                    val vars = listOf("x", "y", "z", "t").take(eqnCount)
                                    val eqs = (0 until eqnCount).map { r ->
                                        val terms = (0 until eqnCount).mapNotNull { c ->
                                            val v = num(r * (eqnCount + 1) + c)
                                            if (v == 0.0) null else "${v.takeIf { it != 1.0 } ?: ""}${vars[c]}"
                                        }
                                        // 系数全为 0 的行没有意义,交给引擎报错
                                        val lhs = if (terms.isEmpty()) "0" else terms.joinToString("+")
                                        "$lhs=${Display.fmt(num(r * (eqnCount + 1) + eqnCount))}"
                                    }
                                    val res = Solver.solveLinearSystem(eqs)
                                    res.variables.mapIndexed { i, v ->
                                        "$v = ${Display.fmt(res.solution[i].toDouble())}"
                                    }.joinToString("   ")
                                } else {
                                    // 多项式:降幂系数拼成 …=0
                                    // 系数格顺序是 [x^n][x^(n-1)]…[常数],
                                    // 所以 x^pw 的系数在 eqnCount - pw 格(之前写成 eqnCount - i 是错的,
                                    // 会把 x 的系数读成 x³ 那一格)
                                    val terms = mutableListOf<String>()
                                    for (pw in eqnCount downTo 0) {
                                        val idx = eqnCount - pw
                                        val c = num(idx)
                                        if (c == 0.0) continue
                                        val mag = if (abs(c) == 1.0 && pw > 0) "" else Display.fmt(abs(c))
                                        val xp = when (pw) {
                                            0 -> ""
                                            1 -> "x"
                                            else -> "x^$pw"
                                        }
                                        terms.add((if (c < 0) "-" else "+") + mag + xp)
                                    }
                                    var expr = if (terms.isEmpty()) "0" else terms.joinToString("")
                                    if (expr.startsWith("+")) expr = expr.drop(1)
                                    val r = Solver.solve("$expr=0")
                                    // 把拼出的式子一并显示:系数读错格时能立刻看出来
                                    "拼出: $expr=0\n\n" + Display.showEquation(r, "$expr=0")
                                }
                            } catch (e: Exception) {
                                "错误: ${e.message}"
                            }
                        },
                        onClear = { resetEqn(eqnKind, eqnCount) },
                        onClose = { eqnOpen = false }
                    )
                }
            }

            // ---- 比例面板 ----
            if (ratioOpen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                ) {
                    RatioPanel(
                        values = ratioFields,
                        pending = ratioPending,
                        version = ratioVersion,
                        missingIndex = ratioMissing,
                        activeField = ratioActive,
                        result = ratioResult,
                        onPickField = { i -> if (i != ratioMissing) ratioActive = i },
                        onCommit = {
                            // 把待录入内容提交到当前格,并自动跳到下一个非未知格
                            val t = ratioPending.trim()
                            if (t.isNotEmpty()) {
                                val m = ratioFields.toMutableList()
                                m[ratioActive] = t
                                ratioFields = m
                                ratioPending = ""
                                ratioVersion++
                                var next = ratioActive + 1
                                while (next < 4 && next == ratioMissing) next++
                                if (next < 4) ratioActive = next
                            }
                        },
                        onToggleMissing = {
                            ratioMissing = if (ratioMissing == 3) 2 else 3
                            ratioActive = if (ratioMissing == 2) 3 else 2
                            ratioResult = ""
                        },
                        onSolve = {
                            // 先自动把待录入值提交到当前格,避免用户漏按「下一格」
                            val p = ratioPending.trim()
                            var fields = ratioFields
                            if (p.isNotEmpty() && ratioActive != ratioMissing) {
                                val m = fields.toMutableList()
                                m[ratioActive] = p
                                fields = m
                                ratioFields = m
                                ratioPending = ""
                            }
                            fun num(i: Int): Double? = fields.getOrNull(i)?.trim()?.toDoubleOrNull()
                            ratioResult = try {
                                if (ratioMissing == 3) {
                                    // a : b = c : X → X = b·c/a
                                    val a = num(0) ?: throw IllegalArgumentException("a 未填")
                                    val b = num(1) ?: throw IllegalArgumentException("b 未填")
                                    val c = num(2) ?: throw IllegalArgumentException("c 未填")
                                    val r = Ratio.solveFourth(a, b, c)
                                    if (r is Ratio.Result.Ok) "X = ${Display.fmt(r.value)}" else (r as Ratio.Result.Failed).reason
                                } else {
                                    // a : b = X : d → X = a·d/b
                                    val a = num(0) ?: throw IllegalArgumentException("a 未填")
                                    val b = num(1) ?: throw IllegalArgumentException("b 未填")
                                    val d = num(3) ?: throw IllegalArgumentException("d 未填")
                                    val r = Ratio.solveThird(a, b, d)
                                    if (r is Ratio.Result.Ok) "X = ${Display.fmt(r.value)}" else (r as Ratio.Result.Failed).reason
                                }
                            } catch (e: Exception) {
                                "错误: ${e.message}"
                            }
                        },
                        onClear = {
                            ratioFields = listOf("", "", "", "")
                            ratioResult = ""
                            ratioActive = 0
                        },
                        onClose = { ratioOpen = false }
                    )
                }
            }

            // ---- 不等式面板 ----
            if (ineqOpen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                ) {
                    IneqPanel(
                        degree = ineqDegree,
                        coeffs = ineqCoeffs.take(ineqDegree + 1),
                        pending = ineqPending,
                        version = ineqVersion,
                        rel = ineqRel,
                        activeField = ineqActive,
                        result = ineqResult,
                        onSetDegree = { d ->
                            ineqDegree = d
                            ineqActive = 0
                            ineqPending = ""
                            ineqResult = ""
                        },
                        onPickField = { i -> ineqActive = i },
                        onCommit = {
                            val t = ineqPending.trim()
                            if (t.isNotEmpty()) {
                                val m = ineqCoeffs.toMutableList()
                                m[ineqActive] = t
                                ineqCoeffs = m
                                ineqPending = ""
                                ineqVersion++
                                if (ineqActive < ineqDegree) ineqActive++
                            }
                        },
                        onToggleSign = {
                            // 只切换待录入内容的正负,不动已提交的系数
                            ineqPending = if (ineqPending.startsWith("-")) ineqPending.drop(1)
                            else "-$ineqPending"
                            ineqVersion++
                        },
                        onSetRel = { r ->
                            ineqRel = r
                            ineqResult = ""
                        },
                        onSolve = {
                            // 先自动提交待录入值,避免漏按「下一格」
                            val p = ineqPending.trim()
                            var coeffList = ineqCoeffs.toMutableList()
                            if (p.isNotEmpty()) {
                                coeffList[ineqActive] = p
                                ineqCoeffs = coeffList
                                ineqPending = ""
                            }
                            ineqResult = try {
                                val cs = (0..ineqDegree).map { i ->
                                    val t = coeffList.getOrElse(i) { "" }.trim()
                                    if (t.isEmpty()) 0.0
                                    else t.toDoubleOrNull() ?: throw IllegalArgumentException("第 ${i + 1} 个系数不是数值")
                                }
                                val r = Ineq.solve(cs, ineqRel)
                                when (r) {
                                    is Ineq.Result.Intervals -> r.text
                                    is Ineq.Result.Failed -> "无法求解: ${r.reason}"
                                }
                            } catch (e: Exception) {
                                "错误: ${e.message}"
                            }
                        },
                        onClear = {
                            ineqCoeffs = listOf("", "", "", "", "")
                            ineqPending = ""
                            ineqResult = ""
                            ineqActive = 0
                        },
                        onClose = { ineqOpen = false }
                    )
                }
            }

            // ---- 基数模式面板:输出区覆盖层 ----
            if (baseOpen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                ) {
                    BaseNPanel(
                        base = baseSel,
                        valueText = BaseN.format(baseValue, baseSel),
                        pending = basePending,
                        pendingOp = baseOp,
                        second = baseSecond,
                        result = baseResult,
                        onPickBase = { b ->
                            // 切换进制:把当前值按新进制重新显示(值本身不变)
                            baseSel = b
                            baseResult = ""
                        },
                        onCommit = {
                            // 把当前 pending 按当前进制解析为值
                            val text = basePending.trim()
                            if (text.isNotEmpty()) {
                                baseResult = try {
                                    val v = BaseN.parse(text, baseSel)
                                    if (baseOp.isEmpty()) {
                                        baseValue = v
                                        ""
                                    } else {
                                        baseSecond = text
                                        ""
                                    }
                                } catch (e: Exception) {
                                    "错误: ${e.message}"
                                }
                                // 二元运算:第二个数就位后立即计算
                                if (baseOp.isNotEmpty() && baseSecond.isNotEmpty()) {
                                    baseResult = try {
                                        val other = BaseN.parse(baseSecond, baseSel)
                                        val r = when (baseOp) {
                                            "and" -> BaseN.and(baseValue, other)
                                            "or" -> BaseN.or(baseValue, other)
                                            "xor" -> BaseN.xor(baseValue, other)
                                            "xnor" -> BaseN.xnor(baseValue, other)
                                            else -> baseValue
                                        }
                                        baseValue = r
                                        "${baseOp} → ${BaseN.format(r, baseSel)}"
                                    } catch (e: Exception) {
                                        "错误: ${e.message}"
                                    }
                                    baseOp = ""
                                    baseSecond = ""
                                }
                                basePending = ""
                            }
                        },
                        onClear = {
                            baseValue = 0
                            basePending = ""
                            baseOp = ""
                            baseSecond = ""
                            baseResult = ""
                        },
                        onOp = { op ->
                            when {
                                op.startsWith("digit") -> {
                                    // 十六进制字母:直接追加到输入
                                    val ch = op.removePrefix("digit")
                                    if (baseSel == BaseN.Base.HEX || baseSel == BaseN.Base.DEC) basePending += ch
                                }
                                op == "not" -> {
                                    baseValue = BaseN.not(baseValue)
                                    baseResult = "Not → ${BaseN.format(baseValue, baseSel)}"
                                }
                                op == "neg" -> {
                                    baseValue = BaseN.neg(baseValue)
                                    baseResult = "Neg → ${BaseN.format(baseValue, baseSel)}"
                                }
                                else -> {
                                    // 记录待运算:先把 pending 提交为第一个数
                                    baseOp = op
                                    baseSecond = ""
                                    val t = basePending.trim()
                                    if (t.isNotEmpty()) {
                                        try {
                                            baseValue = BaseN.parse(t, baseSel)
                                            basePending = ""
                                        } catch (e: Exception) {
                                            baseResult = "错误: ${e.message}"
                                        }
                                    }
                                    baseResult = ""
                                }
                            }
                        },
                        onClose = { baseOpen = false }
                    )
                }
            }

            // ---- 向量编辑器:输出区覆盖层 ----
            if (vecOpen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                ) {
                    VectorEditor(
                        dim = vecDim,
                        cells = vecCells,
                        current = vecCurrent,
                        pending = vecPending,
                        selected = vecSelected,
                        result = vecResult,
                        onPickVar = { v ->
                            vecSelected = v
                            val cur = vecVars[v]
                            if (cur != null) {
                                vecDim = cur.dim
                                vecCells = List(cur.dim) { i -> cur[i] }
                            } else {
                                vecCells = List(vecDim) { null }
                            }
                            vecCurrent = 0
                            vecPending = ""
                            vecResult = ""
                        },
                        onSetDim = { d ->
                            vecDim = d
                            vecCells = List(d) { null }
                            vecCurrent = 0
                            vecPending = ""
                            vecResult = ""
                        },
                        onCommitCell = {
                            val v = vecPending.trim().toDoubleOrNull()
                            if (v != null && vecCurrent < vecDim) {
                                val list = vecCells.toMutableList()
                                list[vecCurrent] = v
                                vecCells = list
                                vecPending = ""
                                if (vecCurrent < vecDim - 1) {
                                    vecCurrent++
                                } else {
                                    commitVector()
                                    vecResult = "$vecSelected 已保存"
                                }
                            }
                        },
                        onClear = {
                            vecCells = List(vecDim) { null }
                            vecCurrent = 0
                            vecPending = ""
                            vecResult = ""
                        },
                        onOp = { op ->
                            vecResult = try {
                                commitVector()
                                val cur = vecVars[vecSelected]
                                    ?: Vector.zeros(vecDim)
                                val otherName = nextVectorName()
                                val other = vecVars[otherName]
                                when (op) {
                                    "norm" -> "|${vecSelected}| = ${Display.fmt(Vec.norm(cur))}"
                                    "unit" -> {
                                        vecVars[vecSelected] = Vec.unit(cur)
                                        val u = vecVars[vecSelected]!!
                                        vecCells = List(u.dim) { i -> u[i] }
                                        "已单位化"
                                    }
                                    "dot" -> if (other == null) "$otherName 还没录入"
                                    else "${vecSelected}·$otherName = ${Display.fmt(Vec.dot(cur, other))}"
                                    "cross" -> {
                                        if (other == null) "$otherName 还没录入"
                                        else {
                                            val c = Vec.cross(cur, other)
                                            vecVars[vecSelected] = c
                                            vecDim = c.dim
                                            vecCells = List(c.dim) { i -> c[i] }
                                            "${vecSelected}×$otherName = ${Vec.format(c)}"
                                        }
                                    }
                                    "angle" -> if (other == null) "$otherName 还没录入"
                                    else {
                                        val rad = Vec.angle(cur, other)
                                        val ang = Vec.toAngleUnit(rad, engine.settings.angle)
                                        "夹角 = ${Display.fmt(ang)}(${engine.settings.angle.label})"
                                    }
                                    "add" -> if (other == null) "$otherName 还没录入"
                                    else {
                                        val s = Vec.add(cur, other)
                                        vecVars[vecSelected] = s
                                        vecDim = s.dim
                                        vecCells = List(s.dim) { i -> s[i] }
                                        "已与 $otherName 相加"
                                    }
                                    else -> ""
                                }
                            } catch (e: Exception) {
                                "错误: ${e.message}"
                            }
                        },
                        onClose = { vecOpen = false }
                    )
                }
            }

            // ---- 矩阵编辑器:输出区覆盖层 ----
            if (matOpen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                ) {
                    MatrixEditor(
                        rows = matRows,
                        cols = matCols,
                        cells = matCells,
                        current = matCurrent,
                        pending = matPending,
                        selected = matSelected,
                        result = matResult,
                        onPickVar = { v ->
                            matSelected = v
                            val m = matVars[v]
                            if (m != null) {
                                matRows = m.rows
                                matCols = m.cols
                                matCells = List(m.rows * m.cols) { i -> m.data[i] }
                            } else {
                                matCells = List(matRows * matCols) { null }
                            }
                            matCurrent = 0
                            matPending = ""
                            matResult = ""
                            matVersion++
                        },
                        onSetDim = { r, c ->
                            matRows = r
                            matCols = c
                            matCells = List(r * c) { null }
                            matCurrent = 0
                            matPending = ""
                            matResult = ""
                            matVersion++
                        },
                        onCommitCell = {
                            val v = matPending.trim().toDoubleOrNull()
                            if (v != null && matCurrent < matRows * matCols) {
                                val list = matCells.toMutableList()
                                list[matCurrent] = v
                                matCells = list
                                matPending = ""
                                // 自动跳到下一格;填满后写回 MatX
                                if (matCurrent < matRows * matCols - 1) {
                                    matCurrent++
                                } else {
                                    commitMatrix()
                                    matResult = "$matSelected 已保存"
                                }
                                matVersion++
                            }
                        },
                        onClear = {
                            matCells = List(matRows * matCols) { null }
                            matCurrent = 0
                            matPending = ""
                            matResult = ""
                            matVersion++
                        },
                        onOp = { op ->
                            matResult = try {
                                commitMatrix()
                                val cur = matVars[matSelected] ?: Matrix.zeros(matRows, matCols)
                                when (op) {
                                    "det" -> "Det = ${Display.fmt(Mat.det(cur))}"
                                    "trn" -> {
                                        matVars[matSelected] = Mat.transpose(cur)
                                        val t = matVars[matSelected]!!
                                        matRows = t.rows; matCols = t.cols
                                        matCells = List(t.rows * t.cols) { i -> t.data[i] }
                                        "已转置为 ${t.rows}×${t.cols}"
                                    }
                                    "inv" -> {
                                        matVars[matSelected] = Mat.inverse(cur)
                                        val t = matVars[matSelected]!!
                                        matRows = t.rows; matCols = t.cols
                                        matCells = List(t.rows * t.cols) { i -> t.data[i] }
                                        "已求逆"
                                    }
                                    "identity" -> {
                                        val n = matRows
                                        val id = Matrix.identity(n)
                                        matVars[matSelected] = id
                                        matCells = List(n * n) { i -> id.data[i] }
                                        "已置为单位阵"
                                    }
                                    "pow2" -> {
                                        val t = Mat.pow(cur, 2)
                                        matVars[matSelected] = t
                                        matCells = List(t.rows * t.cols) { i -> t.data[i] }
                                        "已平方"
                                    }
                                    "pow3" -> {
                                        val t = Mat.pow(cur, 3)
                                        matVars[matSelected] = t
                                        matCells = List(t.rows * t.cols) { i -> t.data[i] }
                                        "已立方"
                                    }
                                    "abs" -> {
                                        val t = Mat.absAll(cur)
                                        matVars[matSelected] = t
                                        matCells = List(t.rows * t.cols) { i -> t.data[i] }
                                        "已取绝对值"
                                    }
                                    "toexpr" -> {
                                        // 把当前矩阵名送进算式栏并关掉面板,接着就能写 MatA*MatB 之类。
                                        // 必须先把**全部**已录入的矩阵/向量同步给引擎 ——
                                        // 这个同步原先只在"切换模式"时做,按本键时还没同步,
                                        // 于是算式栏会报「未知名称: MatA」(实测踩过)。
                                        commitMatrix()
                                        syncMatVecToEngine()
                                        matOpen = false
                                        insert(matSelected)
                                        ""
                                    }
                                    else -> ""
                                }
                            } catch (e: Exception) {
                                "错误: ${e.message}"
                            }
                            matVersion++
                        },
                        onClose = { commitMatrix(); syncMatVecToEngine(); matOpen = false }
                    )
                }
            }

            // ---- 统计编辑器:输出区覆盖层,可上下滑动 ----
            if (statsOpen && editorOpen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                ) {
                    StatEditor(
                        data = statData,
                        useFreq = engine.settings.statFreq,
                        pending = pendingValue,
                        insertAt = statData.rows.size,
                        onDeleteRow = { i ->
                            statData.deleteRow(i)
                            editorVersion++
                        },
                        onCommit = {
                            val v = pendingValue.trim()
                            if (v.isNotEmpty()) {
                                val num = v.toDoubleOrNull()
                                if (num != null) {
                                    if (statData.twoVar) {
                                        // 双变量:第一次加入填 x,第二次填 y(同一行)
                                        val last = statData.rows.lastOrNull()
                                        if (last != null && last.y == null && last.x != null) {
                                            last.y = num
                                        } else {
                                            statData.addRow(Stats.StatRow(x = num))
                                        }
                                    } else {
                                        statData.addRow(Stats.StatRow(x = num))
                                    }
                                    pendingValue = ""
                                    editorVersion++
                                }
                            }
                        },
                        onInsertMode = {
                            // 在末尾插入一行空行,便于随后补数据
                            statData.addRow()
                            editorVersion++
                        },
                        onClear = {
                            statData.clear()
                            pendingValue = ""
                            editorVersion++
                        },
                        onCalculate = {
                            // 按当前编辑器数据出结果
                            editorOpen = false
                            if (statData.twoVar) {
                                val (xs, ys, err) = statData.expandTwo(engine.settings.statFreq)
                                output = if (err != null) "错误: $err"
                                else Stats.twoVarText(xs, ys, Stats.Model.LINEAR)
                            } else {
                                val (list, err) = statData.expandSingle(engine.settings.statFreq)
                                output = if (err != null) "错误: $err" else Stats.oneVarText(list!!)
                            }
                            lcdExpr = null
                            lcdValue = null
                        },
                        onClose = { editorOpen = false },
                        version = editorVersion
                    )
                }
            }


            // ---- HOME 菜单:在输出区**最后**渲染,层级最高。
            //      这样即使矩阵/向量/基数/不等式/比例面板已经打开,也能呼出菜单切换模式,
            //      不会出现"面板盖住模式按钮、用户出不去"的死路。 ----
            if (homeOpen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                ) {
                    HomeMenu(
                        current = mode,
                        onPick = { m ->
                            mode = m
                            homeOpen = false
                            // 切模式时先关掉所有面板,避免叠在一起
                            matOpen = false
                            vecOpen = false
                            baseOpen = false
                            ratioOpen = false
                            ineqOpen = false
                            syncMatVecToEngine()
                            if (m == Calc.Mode.MATRIX) matOpen = true
                            if (m == Calc.Mode.VECTOR) vecOpen = true
                            if (m == Calc.Mode.BASE_N) baseOpen = true
                            if (m == Calc.Mode.INEQ) ineqOpen = true
                            if (m == Calc.Mode.RATIO) ratioOpen = true
                        },
                        onClose = { homeOpen = false },
                        // 计算模式页面里的计算功能:插入对应的可编辑块并关闭页面
                        onCalcTool = { tool ->
                            when {
                                tool == "eqn" -> {
                                    // 方程面板:打开覆盖层(不插块)
                                    homeOpen = false
                                    resetEqn(eqnKind, eqnCount)
                                    eqnOpen = true
                                }
                                tool.startsWith("ins:") -> {
                                    // 矩阵/向量等**片段插入**:页面保持打开 ——
                                    // 用户要连点 `Det(` + `MatA` + `)` 才能拼出式子,
                                    // 点一次就关页面的话第二下就够不到了(实测踩过)。
                                    insert(tool.removePrefix("ins:"))
                                }
                                else -> {
                                    homeOpen = false
                                    edit { ps, _ ->
                                        // 积分块的字段序是 [下限][上限][函数],光标要落在函数格
                                        val (blk, field) = when (tool) {
                                            "int" -> Edit.newIntegral() to 2
                                            "diff" -> Edit.newDiff() to 0
                                            else -> Edit.newLimit() to 0
                                        }
                                        ps.add(blk)
                                        Edit.Cursor(ps.size - 1, field, 0)
                                    }
                                }
                            }
                        }
                    )
                }
            }

            // ---- 方向键抽屉:覆盖在输出区顶部,两行紧凑排布。
            //      展开时不推动按键,按钮做矮一点以免盖住下面的数字键 ----
            if (arrowsOpen) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Color(0xFF1B1B1B))
                        .border(1.dp, Color(0xFF3F8F4F), RoundedCornerShape(6.dp))
                        .padding(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Key("←", Modifier.weight(1f), compact = true, onClick = { cursorLeft() })
                        Key("→", Modifier.weight(1f), compact = true, onClick = { cursorRight() })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Key("⇤ 行首", Modifier.weight(1f), compact = true, onClick = { cursorHome() })
                        Key("⇥ 行末", Modifier.weight(1f), compact = true, onClick = { cursorEnd() })
                    }
                }
            }
        }

        // ================= 中部:输入区 =================
        // 算式栏 + 全部输入按键都放在这一块里(不再另开整屏输入页)。
        // 命令块(:int 等)是整体,块内字段带标注(上限/下限),标注在各自数字左上方;
        // 字段之间的分隔由布局产生而不是字符,所以键盘删不掉、光标也进不去。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.64f)          // 输入区:算式栏 + 全部按键
                .padding(top = 6.dp, bottom = 6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFF4F4F4))
                .border(1.dp, Color(0xFF9AA79A), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 算式栏:弹性高度 + 下限。空间紧张时优先让它收缩,
            // 从而保证下面的按键不被压成细条。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .defaultMinSize(minHeight = 72.dp)
                    .heightIn(max = 132.dp)   // 内容不多时不要占掉过多空白
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFFBFBFB)),
                contentAlignment = Alignment.TopStart
            ) {
                // 必须先读一次 editVersion:parts 是浅拷贝、结构可能相等,
                // 不读版本号的话这个分支不会重组,表现为"输入了数字但算式栏不更新"。
                val hasContent = editVersion >= 0 && !parts.all { it is Edit.Raw && it.text.isEmpty() }
                if (!hasContent) {
                    Text(
                        text = "< 用下方按键排式子 >",
                        color = Color(0xFF8A8A8A),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(6.dp)
                    )
                } else {
                    ExpressionBar(
                        parts = parts,
                        cursor = cursor,
                        version = editVersion,
                        onCellTap = { pi, fi -> focusField(pi, fi) },
                        // 多行字体设定联动:小字体时整体缩小
                        fontSize = if (engine.settings.smallFont) 16.sp else 19.sp
                    )
                }
            }

            // 命令区第一行:ALPHA | 统计 / 积分 / 求导
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // ALPHA 放上面这行(一次性前缀,按键后自动取消)
                Key(
                    "ALPHA",
                    Modifier.weight(1f),
                    accent = keyMods.alpha,
                    compact = true,
                    onClick = { keyMods = keyMods.toggledAlpha() }
                )
                // 统计:短标签避免被裁;开启后下方会出现 1变量/2变量/关闭 一排
                Key(
                    "统计",
                    Modifier.weight(1f),
                    accent = statsOpen,
                    compact = true,
                    onClick = {
                        statsOpen = !statsOpen
                        // 手册:进入统计模式会自动切换到「线性输入/小数输出」
                        engine.statMode = statsOpen
                        if (statsOpen) {
                            statsMode = "1"
                            clearAll()
                            insert(":stat 1 ")
                        } else {
                            clearAll()
                        }
                    }
                )
                Key("积分 ∫", Modifier.weight(1f), compact = true, onClick = {
                    edit { ps, _ ->
                        ps.add(Edit.newIntegral())
                        Edit.Cursor(ps.size - 1, 2, 0)
                    }
                })
                Key("求导 d/dx", Modifier.weight(1f), compact = true, onClick = {
                    edit { ps, _ ->
                        ps.add(Edit.newDiff())
                        Edit.Cursor(ps.size - 1, 0, 0)
                    }
                })
            }

            // 命令区第二行:SHIFT | 切换 | 计算(模式) | 命令 · 函数
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // SHIFT 放下面这行最左
                Key(
                    "SHIFT",
                    Modifier.weight(1f),
                    accent = keyMods.shift,
                    compact = true,
                    onClick = { keyMods = keyMods.toggledShift() }
                )
                // 切换:用 26 字母替换下侧的数字与符号区
                Key(
                    if (alphaPad) "数字" else "切换",
                    Modifier.weight(1f),
                    accent = alphaPad,
                    compact = true,
                    onClick = { alphaPad = !alphaPad }
                )
                // 计算(模式)放 SHIFT 与 命令 中间
                Key(
                    "计算",
                    Modifier.weight(1f),
                    accent = mode != Calc.Mode.COMP || homeOpen,
                    compact = true,
                    onClick = { homeOpen = !homeOpen }
                )
                Key(
                    if (cmdOpen) "▼ 命令" else "▲ 命令",
                    Modifier.weight(1f),
                    accent = cmdOpen,
                    compact = true,
                    onClick = { cmdOpen = !cmdOpen }
                )
                Key(
                    if (funcsOpen) "▼ 函数" else "▲ 函数",
                    Modifier.weight(1f),
                    accent = true,
                    compact = true,
                    onClick = { funcsOpen = !funcsOpen }
                )
            }

            // 统计模式:开启后单独一排,可切换 1/2 变量,并有明确的「关闭统计」
            if (statsOpen) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Key(
                        "1 变量",
                        Modifier.weight(1f),
                        accent = statsMode == "1",
                        compact = true,
                        onClick = {
                            statsMode = "1"
                            clearAll()
                            insert(":stat 1 ")
                        }
                    )
                    Key(
                        "2 变量",
                        Modifier.weight(1f),
                        accent = statsMode == "2",
                        compact = true,
                        onClick = {
                            statsMode = "2"
                            clearAll()
                            insert(":stat 2 ")
                        }
                    )
                    Key(
                        "编辑器",
                        Modifier.weight(1f),
                        accent = editorOpen,
                        compact = true,
                        onClick = { editorOpen = !editorOpen }
                    )
                    // 正态分布不需要打冒号:直接用已录数据 + 待录入值作为 x
                    Key(
                        "正态 P/Q/R",
                        Modifier.weight(1.3f),
                        compact = true,
                        onClick = {
                            val (list, err) = statData.expandSingle(engine.settings.statFreq)
                            if (err != null) {
                                output = "错误: $err(请先在编辑器里录入数据)"
                            } else {
                                val x = pendingValue.trim().toDoubleOrNull()
                                if (x == null) {
                                    output = "错误: 先在编辑器里按数字键输入 x,再点「正态 P/Q/R」"
                                } else {
                                    output = Stats.normalText(x, list!!)
                                    pendingValue = ""
                                }
                            }
                            lcdExpr = null
                            lcdValue = null
                        }
                    )
                    Key(
                        "关闭统计",
                        Modifier.weight(1f),
                        compact = true,
                        onClick = {
                            statsOpen = false
                            editorOpen = false
                            clearAll()
                        }
                    )
                }
            }

            // 下侧区域:默认是数字与运算符;点「切换」后换成 26 字母键盘。
            // 两者都限高 + 内部滚动,宁可滚动也不把按键压成细条。
            if (alphaPad) {
                AlphaKeypad(
                    upper = keyMods.shift,
                    onKey = { ch ->
                        // 字母直接写进算式(变量名、函数名都用得上)
                        if (statsOpen && editorOpen) pendingValue += ch
                        else if (matOpen) matPending += ch
                        else if (vecOpen) vecPending += ch
                        else if (baseOpen) basePending += ch
                        else if (ratioOpen) {
                            val m = ratioFields.toMutableList()
                            m[ratioActive] = m[ratioActive] + ch
                            ratioFields = m
                        } else if (ineqOpen) ineqPending += ch
                        else insert(ch)
                        // SHIFT 是一次性前缀:用过即取消
                        if (keyMods.shift) keyMods = keyMods.consumed()
                    },
                    onBackspace = {
                        if (statsOpen && editorOpen) {
                            if (pendingValue.isNotEmpty()) pendingValue = pendingValue.dropLast(1)
                        } else if (matOpen) {
                            if (matPending.isNotEmpty()) matPending = matPending.dropLast(1)
                        } else if (vecOpen) {
                            if (vecPending.isNotEmpty()) vecPending = vecPending.dropLast(1)
                        } else if (eqnOpen) {
                            if (eqnPending.isNotEmpty()) eqnPending = eqnPending.dropLast(1)
                        } else if (baseOpen) {
                            if (basePending.isNotEmpty()) basePending = basePending.dropLast(1)
                        } else if (ratioOpen) {
                            val m = ratioFields.toMutableList()
                            m[ratioActive] = m[ratioActive].dropLast(1)
                            ratioFields = m
                        } else if (ineqOpen) {
                            if (ineqPending.isNotEmpty()) ineqPending = ineqPending.dropLast(1)
                        } else backspace()
                    },
                    onSpace = { if (!statsOpen && !matOpen && !vecOpen && !baseOpen && !ratioOpen && !ineqOpen) insert(" ") },
                    onComma = {
                        if (statsOpen && editorOpen) pendingValue += ","
                        else if (!matOpen && !vecOpen && !baseOpen && !ratioOpen && !ineqOpen) insert(",")
                    },
                    onSwitchBack = { alphaPad = false },
                    onLeft = { cursorLeft() },
                    onRight = { cursorRight() },
                    onClear = { clearAll() },
                    onEval = { run(false) },
                    onSolve = { run(true) }
                )
            } else {
            EditorKeypad(
                onKey = { key ->
                    // 编辑器打开时数字键进编辑器,不写进表达式
                    if (statsOpen && editorOpen) {
                        if (key == "," || key == "=") Unit else pendingValue += key
                    } else if (matOpen) {
                        if (key == "," || key == "=") Unit else matPending += key
                    } else if (vecOpen) {
                        if (key == "," || key == "=") Unit else vecPending += key
                    } else if (baseOpen) {
                        // 基数模式:数字键累积(十六进制字母用面板上的 A~F 键)
                        if (key == "," || key == "=") Unit else basePending += key
                    } else if (eqnOpen) {
                        // 方程面板:数字键累积到当前格(负号用面板上的「加负号」)
                        if (key == "," || key == "=") Unit else eqnPending += key
                    } else if (ratioOpen) {
                        // 比例:数字键累积到"待录入值",按面板上的「下一格」提交
                        if (key == "=") Unit
                        else if (key == "," || key == ".") ratioPending += "."
                        else ratioPending += key
                    } else if (ineqOpen) {
                        if (key == "=") Unit
                        else if (key == "," || key == ".") ineqPending += "."
                        else ineqPending += key
                    } else {
                        insert(key)
                    }
                },
                onBackspace = { backspace() },
                onClear = { clearAll() },
                onLeft = { cursorLeft() },
                onRight = { cursorRight() },
                onHome = { cursorHome() },
                onEnd = { cursorEnd() },
                onEval = { run(false) },
                onSolve = { run(true) },
                onClearAll = {
                    clearAll()
                    output = ""
                },
                onHelp = {
                    clearAll()
                    val help = engine.process(":help")
                    output = if (output.isEmpty()) help else "$output\n$help"
                },
                // 历史重放:翻阅到的算式直接放回输入栏
                onHistUp = {
                    history.up()?.let { h ->
                        parts = Edit.parse(h).first
                        cursor = Edit.parse(h).second
                        editVersion++
                    }
                },
                onHistDown = {
                    val h = history.down()
                    if (h != null) {
                        parts = Edit.parse(h).first
                        cursor = Edit.parse(h).second
                        editVersion++
                    } else {
                        clearAll()
                    }
                }
            )
            }

        }

    }
}

/** 按键定义:6 列。
 *  第 6 列依次放 清空 / 退格 / 变量 x / 变量 y / 方程组分隔符「,」
 *  功能键行:y / pi / e / ln / sin / help
 *  (x^2 这类快捷键不单独占键位,用 x ^ 2 即可打出来) */
private val ROWS = listOf(
    listOf("7", "8", "9", "(", ")", "C"),
    listOf("4", "5", "6", "^", "√", "⌫"),
    listOf("1", "2", "3", "*", "/", "y"),
    listOf("0", ".", "=", "-", "+", ","),
    listOf("x", "pi", "e", "ln", "sin", "help")
)

@Composable
fun Keypad(
    onInsert: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onClearAll: () -> Unit,
    onHelp: () -> Unit,
    onInsertDiff: () -> Unit,
    onInsertInt: () -> Unit,
    onEval: () -> Unit,
    onSolve: () -> Unit,
    arrowsOpen: Boolean,
    onToggleArrows: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onHome: () -> Unit,
    onEnd: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {


        for (row in ROWS) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (label in row) {
                    Key(
                        label = label,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (label) {
                                "C" -> onClear()
                                "⌫" -> onBackspace()
                                "help" -> onHelp()
                                else -> onInsert(label)
                            }
                        }
                    )
                }
            }
        }
        // 执行键排
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Key("= 求值", Modifier.weight(3f), accent = true, onClick = onEval)
            Key("解 方程", Modifier.weight(3f), accent = true, onClick = onSolve)
        }
        // 功能排:方向键抽屉开关 + 微积分入口 + 全清
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Key(
                if (arrowsOpen) "▼ 方向键" else "▲ 方向键",
                Modifier.weight(1.1f),
                onClick = onToggleArrows
            )
            Key("求导 d/dx", Modifier.weight(1.3f), onClick = onInsertDiff)
            Key("积分 ∫", Modifier.weight(1.1f), onClick = onInsertInt)
            Key("AC 全清", Modifier.weight(1.0f), onClick = onClearAll)
        }
    }
}

@Composable
fun Key(
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    compact: Boolean = false,
    /** 窄而高的竖长方形键(字母键盘用) */
    tall: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .padding(vertical = 1.dp)
            // 最小高度:防止父布局空间不足时把按键压成细条、把文字裁掉
            .defaultMinSize(minHeight = when {
                // 字母键做窄而高的竖长方形:比普通键高,但不过高(过高会把执行键挤出屏幕)
                tall -> 40.dp
                compact -> 32.dp
                else -> 46.dp
            })
            .clip(RoundedCornerShape(6.dp))
            .background(if (accent) Color(0xFF2F6F3F) else Color(0xFF2D2D2D))
            .clickable {
                onClick()
            }
            .padding(vertical = if (tall) 7.dp else 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color(0xFFF0F0F0),
            fontSize = when {
                tall -> 13.sp
                compact -> 13.sp
                accent -> 15.sp
                else -> 16.sp
            },
            fontWeight = if (accent) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}
