package com.example.casio.core

/**
 * 计算器设定(SETUP),逐项对应 fx-991CN X 说明书的「配置计算器设定」。
 * 默认值取手册标注的初始缺省值。
 */
data class Settings(
    /** 输入/输出格式(4 种组合) */
    val ioMode: IoMode = IoMode.MATH_MATH,
    /** 角度单位 */
    val angle: AngleUnit = AngleUnit.DEGREE,
    /** 显示格式:Fix / Sci / Norm */
    val display: DisplayMode = DisplayMode.Norm(1),
    /** 工程符号 开/关 */
    val engineering: Boolean = false,
    /** 分数结果:带分数 / 假分数 */
    val mixedFraction: Boolean = false,
    /** 复数:直角坐标 / 极坐标 */
    val complexPolar: Boolean = false,
    /** 统计编辑器是否显示 Freq 栏 */
    val statFreq: Boolean = false,
    /** 方程/函数模式的解是否使用复数 */
    val eqnComplex: Boolean = true,
    /** 表格模式:f(x) 还是 f(x),g(x) */
    val tableTwoFuncs: Boolean = false,
    /** 小数点显示:句点 / 逗号 */
    val decimalComma: Boolean = false,
    /** 数字分隔符 开/关 */
    val digitSeparator: Boolean = false,
    /** 多行字体:普通(4 行) / 小(6 行) */
    val smallFont: Boolean = false,
    /** 语言:English / 中文 */
    val chinese: Boolean = true,
    /** 对比度 */
    val contrast: Int = 5
) {
    /** 数学输入(可用竖排分数/根号模板) */
    val mathInput: Boolean
        get() = ioMode == IoMode.MATH_MATH || ioMode == IoMode.MATH_DEC

    /** 数学输出(结果可显示分数/根式/π 形式) */
    val mathOutput: Boolean
        get() = ioMode == IoMode.MATH_MATH

    /** 小数点字符 */
    val decimalPoint: Char get() = if (decimalComma) ',' else '.'

    /** 多结果分隔符 */
    val resultSeparator: String get() = if (decimalComma) ";" else ","
}

enum class IoMode(val label: String) {
    MATH_MATH("数学输入/数学输出"),
    MATH_DEC("数学输入/小数输出"),
    LINEAR_LINEAR("线性输入/线性输出"),
    LINEAR_DEC("线性输入/小数输出")
}

enum class AngleUnit(val label: String) {
    DEGREE("度"), RADIAN("弧度"), GRADIAN("百分度")
}

/** 显示格式 */
sealed interface DisplayMode {
    /** 小数位数 0~9 */
    data class Fix(val digits: Int) : DisplayMode

    /** 有效数字位数 0~9 */
    data class Sci(val digits: Int) : DisplayMode

    /**
     * 常规:Norm1 → 10^-2 > |x| 或 |x| ≥ 10^10 时用指数;
     *        Norm2 → 10^-9 > |x| 或 |x| ≥ 10^10 时用指数。
     */
    data class Norm(val variant: Int) : DisplayMode

    companion object {
        val default: DisplayMode = Norm(1)
    }
}
