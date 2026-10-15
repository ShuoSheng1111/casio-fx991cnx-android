package com.example.casio.core

/**
 * 计算模式与按键辅助状态 —— 对应 fx-991CN X 的 HOME 菜单与 SHIFT/ALPHA。
 *
 * 手册:
 *  · HOME 菜单有 9 个模式:计算 / 复数 / 基数 / 矩阵 / 向量 / 统计 / 表格 / 方程函数 / 不等式 / 比例
 *  · SHIFT 与 ALPHA 是键前缀:按下后下一次按键取"第二/第三功能",之后自动取消
 *  · 计算历史记录与重放:多次按 d/e(上下方向键)可前后翻阅已执行的算式
 *  · Ans 存储器保存最新结果;独立存储器 M 可用 M+/M- 累加;变量 A~F/x/y 可存储
 */
object Calc {

    /** HOME 菜单的计算模式 */
    enum class Mode(
        val label: String,
        val icon: String,
        /** 该模式是否已实现(未实现的先占位,便于后续按批次补齐) */
        val implemented: Boolean
    ) {
        COMP("计算", "计算", true),
        CMPLX("复数", "复数", false),
        BASE_N("基数", "基数", true),
        MATRIX("矩阵", "矩阵", true),
        VECTOR("向量", "向量", true),
        STAT("统计", "统计", true),
        TABLE("表格", "表格", false),
        EQN("方程/函数", "方程", true),
        INEQ("不等式", "不等式", true),
        RATIO("比例", "比例", true)
    }

    /** 键前缀状态:SHIFT / ALPHA 按下后只对**下一次**按键生效 */
    data class KeyMods(
        val shift: Boolean = false,
        val alpha: Boolean = false
    ) {
        fun toggledShift() = copy(shift = !shift, alpha = false)
        fun toggledAlpha() = copy(alpha = !alpha, shift = false)

        /** 一次按键之后前缀应被清除(真机行为) */
        fun consumed() = KeyMods()

        /** 屏幕指示符 */
        fun symbols(): List<String> = buildList {
            if (shift) add("S")
            if (alpha) add("A")
        }
    }

    /**
     * 计算历史。手册:可前后翻阅已执行的算式并"重放"。
     * 用游标表示当前翻阅位置:null 表示在最末尾(未翻阅)。
     */
    class History(private val limit: Int = 50) {
        private val items = mutableListOf<String>()
        private var cursor: Int? = null

        fun add(line: String) {
            if (line.isBlank()) return
            // 连续重复不重复记录(贴近真机:同一算式连按只留一条)
            if (items.lastOrNull() != line) {
                items.add(line)
                while (items.size > limit) items.removeAt(0)
            }
            cursor = null
        }

        fun entries(): List<String> = items.toList()

        /** 向上翻阅(更早的一条);没有更早的则停在最早一条 */
        fun up(): String? {
            if (items.isEmpty()) return null
            val cur = cursor
            cursor = if (cur == null) items.size - 1 else (cur - 1).coerceAtLeast(0)
            return items[cursor!!]
        }

        /** 向下翻阅(更近的一条);越过最新则回到"未翻阅" */
        fun down(): String? {
            if (items.isEmpty()) return null
            val cur = cursor ?: return null
            return if (cur >= items.size - 1) {
                cursor = null
                null
            } else {
                cursor = cur + 1
                items[cursor!!]
            }
        }

        fun clear() {
            items.clear()
            cursor = null
        }

        /** 用已存条目整体替换(用于从磁盘恢复) */
        fun restore(lines: List<String>) {
            items.clear()
            items.addAll(lines.filter { it.isNotBlank() }.takeLast(limit))
            cursor = null
        }

        /** 当前是否正在翻阅历史 */
        fun browsing(): Boolean = cursor != null
    }

    /**
     * 计算历史的**持久化编码**。
     *
     * 算式里可能含任何字符(冒号、逗号、空格、`=`),所以不能简单用分隔符拼接 ——
     * 这里用「长度前缀」格式:
     *
     *     长度:内容,长度:内容,…
     *
     * 长度按**字符数**计,解析时严格按长度切,因此内容里出现任何字符都不会错位。
     * 纯函数,不依赖 Android,便于自测。
     */
    object HistoryCodec {

        fun encode(lines: List<String>): String =
            lines.filter { it.isNotBlank() }.joinToString(",") { "${it.length}:$it" }

        /** 解析;遇到坏数据就跳过该项,尽量不让整份历史丢失 */
        fun decode(text: String): List<String> {
            if (text.isEmpty()) return emptyList()
            val out = mutableListOf<String>()
            var i = 0
            while (i < text.length) {
                val colon = text.indexOf(':', i)
                if (colon < 0) break
                val len = text.substring(i, colon).toIntOrNull()
                if (len == null || len < 0) {
                    // 坏项:跳到下一个逗号后继续,保住后面的数据
                    val next = text.indexOf(',', i)
                    if (next < 0) break
                    i = next + 1
                    continue
                }
                val start = colon + 1
                val end = start + len
                if (end > text.length) {
                    // 截断项:取到末尾后停止
                    out.add(text.substring(start))
                    break
                }
                out.add(text.substring(start, end))
                i = end
                if (i < text.length && text[i] == ',') i++
            }
            return out
        }
    }

    /**
     * 存储器:Ans(最新结果)、独立存储器 M、变量表。
     * 变量表与引擎的 env 分开维护,便于"清空存储器"只清自己这部分。
     */
    class Memory {
        var ans: Double? = null
            private set
        var independent: Double = 0.0
            private set

        private val vars = linkedMapOf<String, Double>()

        /** 记录一次结果到 Ans */
        fun setAns(v: Double) {
            ans = v
        }

        fun mPlus(v: Double) {
            independent += v
        }

        fun mMinus(v: Double) {
            independent -= v
        }

        /** 独立存储器是否非 0(真机非 0 时显示 M 指示符) */
        fun mUsed(): Boolean = independent != 0.0

        fun store(name: String, v: Double) {
            vars[name.lowercase()] = v
        }

        fun recall(name: String): Double? = vars[name.lowercase()]

        fun all(): Map<String, Double> = vars.toMap()

        /** 清空所有存储器(手册:重置 → 存储器) */
        fun clear() {
            ans = null
            independent = 0.0
            vars.clear()
        }
    }

    /**
     * 把一段文本里的 Ans 与变量名替换成数值,便于直接参与计算。
     * 仅用于显示辅助与简单求值;复杂表达式仍交给 Parser(它会查 env)。
     */
    fun substitute(text: String, mem: Memory): String {
        var out = text
        mem.ans?.let { out = out.replace("Ans", Fmt.number(it, Settings(), mathExact = false)) }
        // 独立存储器 M 与变量名
        for ((k, v) in mem.all()) {
            out = out.replace(Regex("\\b${Regex.escape(k)}\\b"), Fmt.number(v, Settings(), mathExact = false))
        }
        return out
    }
}
