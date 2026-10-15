package com.example.casio.core

/**
 * 科学常数 —— 对应 fx-991CN X 的「科学常数」功能(手册里是 47 项)。
 *
 * ## 数值来源说明(重要)
 *
 * 这里的数值取自 **CODATA 推荐值**(2018 / 2022 调整值),**不是**从手册逐项抄录的 ——
 * 真机手册的常数表未能逐页核对。两者在绝大多数项上应当一致(都以 CODATA 为准),
 * 但**若与手册出现差异,以手册为准**。
 *
 * 每项都带一个 `exact` 标记:凡是**定义值**(如光速 c、普朗克常数 h 自 2019 起为定义值)
 * 标为 true,意思是"这个数不带测量不确定度"。
 */
object Constants {

    /** 一个科学常数 */
    data class SciConst(
        val id: String,          // 内部名(供 :const 查询)
        val symbol: String,      // 符号
        val name: String,        // 中文名
        val value: Double,
        val unit: String,
        val exact: Boolean       // true = 定义值,无测量不确定度
    )

    /** 常数表(按物理类别排列) */
    val all: List<SciConst> = listOf(
        // ---- 普适常数 ----
        SciConst("c", "c", "真空光速", 299792458.0, "m/s", true),
        SciConst("h", "h", "普朗克常数", 6.62607015e-34, "J·s", true),
        SciConst("hbar", "ℏ", "约化普朗克常数", 1.054571817e-34, "J·s", false),
        SciConst("gc", "G", "万有引力常数", 6.67430e-11, "m³/(kg·s²)", false),
        SciConst("gn", "g", "标准重力加速度", 9.80665, "m/s²", true),
        SciConst("atm", "atm", "标准大气压", 101325.0, "Pa", true),
        SciConst("u", "u", "原子质量单位", 1.66053906660e-27, "kg", false),
        SciConst("mu0", "μ₀", "真空磁导率", 1.25663706212e-6, "N/A²", false),
        SciConst("eps0", "ε₀", "真空介电常数", 8.8541878128e-12, "F/m", false),
        SciConst("Z0", "Z₀", "真空特性阻抗", 376.730313668, "Ω", false),

        // ---- 电磁 ----
        SciConst("e", "e", "元电荷", 1.602176634e-19, "C", true),
        SciConst("me", "mₑ", "电子静止质量", 9.1093837015e-31, "kg", false),
        SciConst("mp", "mₚ", "质子静止质量", 1.67262192369e-27, "kg", false),
        SciConst("mn", "mₙ", "中子静止质量", 1.67492749804e-27, "kg", false),
        SciConst("mu", "m_μ", "μ 子静止质量", 1.883531627e-28, "kg", false),
        SciConst("a0", "a₀", "玻尔半径", 5.29177210903e-11, "m", false),
        SciConst("re", "rₑ", "经典电子半径", 2.8179403262e-15, "m", false),
        SciConst("F", "F", "法拉第常数", 96485.33212, "C/mol", false),
        SciConst("e_over_me", "e/mₑ", "电子比荷", 1.75882001076e11, "C/kg", false),
        SciConst("phi0", "Φ₀", "磁通量子", 2.067833848e-15, "Wb", false),
        SciConst("G0", "G₀", "电导量子", 7.748091729e-5, "S", false),
        SciConst("KJ", "K_J", "约瑟夫森常数", 483597.8484e9, "Hz/V", false),
        SciConst("RK", "R_K", "冯·克利青常数", 25812.80745, "Ω", false),

        // ---- 原子与核 ----
        SciConst("NA", "N_A", "阿伏伽德罗常数", 6.02214076e23, "/mol", true),
        SciConst("k", "k", "玻尔兹曼常数", 1.380649e-23, "J/K", true),
        SciConst("R", "R", "摩尔气体常数", 8.314462618, "J/(mol·K)", true),
        SciConst("Vm", "V_m", "理想气体摩尔体积(273.15K,101.325kPa)", 22.41396954e-3, "m³/mol", false),
        SciConst("sigma", "σ", "斯特藩-玻尔兹曼常数", 5.670374419e-8, "W/(m²·K⁴)", true),
        SciConst("alpha", "α", "精细结构常数", 7.2973525693e-3, "", false),
        SciConst("Rinf", "R_∞", "里德伯常数", 10973731.568160, "1/m", false),
        SciConst("muB", "μ_B", "玻尔磁子", 9.2740100783e-24, "J/T", false),
        SciConst("muN", "μ_N", "核磁子", 5.0507837461e-27, "J/T", false),
        SciConst("lambdaC", "λ_C", "康普顿波长", 2.42631023867e-12, "m", false),
        SciConst("gammaP", "γ_p", "质子磁旋比", 2.6752218744e8, "1/(s·T)", false),
        SciConst("Kcd", "K_cd", "发光效能", 683.0, "lm/W", true),

        // ---- 物理化学 ----
        SciConst("t", "t", "摄氏零度(热力学温度)", 273.15, "K", true),
        SciConst("Rgas", "R", "气体常数(同 R)", 8.314462618, "J/(mol·K)", true),

        // ---- 天文 ----
        SciConst("au", "au", "天文单位", 1.495978707e11, "m", true),
        SciConst("pc", "pc", "秒差距", 3.0856775814913673e16, "m", false),
        SciConst("ly", "ly", "光年", 9.4607304725808e15, "m", true),
        SciConst("Mjup", "M_J", "木星质量", 1.89813e27, "kg", false),
        SciConst("Mearth", "M_⊕", "地球质量", 5.9722e24, "kg", false),
        SciConst("Msun", "M_☉", "太阳质量", 1.98847e30, "kg", false),
        SciConst("Rsun", "R_☉", "太阳半径", 6.957e8, "m", true),
        SciConst("Lsun", "L_☉", "太阳光度", 3.828e26, "W", true),

        // ---- 其他常用 ----
        SciConst("Fc", "F", "法拉第常数(同 F)", 96485.33212, "C/mol", false),
        SciConst("hp", "h", "普朗克常数(同 h)", 6.62607015e-34, "J·s", true)
    ).distinctBy { it.id }

    /** 按 id 或符号查找(大小写不敏感)。id 唯一,不会因大小写撞车。 */
    fun find(key: String): SciConst? {
        val k = key.trim().lowercase()
        // 先按 id 精确匹配(优先),再按符号
        return all.firstOrNull { it.id.lowercase() == k }
            ?: all.firstOrNull { it.symbol.lowercase() == k }
    }

    /** 常数个数 */
    val count: Int get() = all.size

    /** 列出全部常数(供 :const 无参调用) */
    fun listAll(): String {
        val rows = all.map { c ->
            val v = Fmt.number(c.value, Settings(), mathExact = false)
            val mark = if (c.exact) " (定义值)" else ""
            "${c.symbol.padEnd(6)} ${c.name}$mark\n        = $v ${c.unit}"
        }
        return Display.box("科学常数(${all.size} 个)", rows)
    }

    /** 查询单个常数 */
    fun query(key: String): String {
        val c = find(key) ?: return "找不到常数「$key」。用 :const 列出全部。"
        val v = Fmt.number(c.value, Settings(), mathExact = false)
        val note = if (c.exact) "定义值(无测量不确定度)" else "CODATA 推荐值(带测量不确定度)"
        return Display.box(
            "科学常数",
            listOf("${c.symbol}   ${c.name}", "", "= $v", "  单位 ${c.unit}", "", note)
        )
    }
}

/**
 * 公制换算 —— 对应 fx-991CN X 的公制换算功能。
 *
 * 真机的换算表是**成对的双向换算**(每对可正向/反向),这里按类别列出,
 * 每对都是精确的换算定义(如 1 in = 2.54 cm 是定义值)。
 *
 * **注意**:手册的换算表条目顺序与编号(01~xx)未能逐项核对,
 * 这里按物理量类别组织,数值本身是标准定义值。
 */
object MetricConv {

    /** 一个换算对:from 与 to 之间的比值 fixed 是定义值 */
    data class Conv(
        val category: String,
        val from: String,
        val to: String,
        val factor: Double,      // to = from × factor
        val exact: Boolean = true
    )

    val all: List<Conv> = listOf(
        // 长度
        Conv("长度", "in", "cm", 2.54),
        Conv("长度", "ft", "m", 0.3048),
        Conv("长度", "yd", "m", 0.9144),
        Conv("长度", "mile", "km", 1.609344),
        Conv("长度", "n mile", "m", 1852.0),
        Conv("长度", "pc", "km", 3.0856775814913673e13, false),
        Conv("长度", "ly", "km", 9.4607304725808e12),
        Conv("长度", "au", "km", 1.495978707e8),
        Conv("长度", "fath", "m", 1.8288),
        Conv("长度", "mil", "mm", 0.0254),

        // 面积
        Conv("面积", "acre", "m²", 4046.8564224),
        Conv("面积", "ha", "m²", 10000.0),

        // 体积
        Conv("体积", "gal(US)", "L", 3.785411784),
        Conv("体积", "gal(UK)", "L", 4.54609),
        Conv("体积", "fl oz(US)", "mL", 29.5735295625),
        Conv("体积", "fl oz(UK)", "mL", 28.4130625),

        // 质量
        Conv("质量", "oz", "g", 28.349523125),
        Conv("质量", "lb", "kg", 0.45359237),
        Conv("质量", "ton(短)", "kg", 907.18474),
        Conv("质量", "ton(长)", "kg", 1016.0469088),
        Conv("质量", "ton(公)", "kg", 1000.0),

        // 速度
        Conv("速度", "km/h", "m/s", 1.0 / 3.6),
        Conv("速度", "knot", "m/s", 1852.0 / 3600.0),
        Conv("速度", "ft/s", "m/s", 0.3048),

        // 压力
        Conv("压力", "atm", "Pa", 101325.0),
        Conv("压力", "mmHg", "Pa", 133.322387415, false),
        Conv("压力", "inHg", "Pa", 3386.38815789, false),
        Conv("压力", "bar", "Pa", 100000.0),
        Conv("压力", "psi", "Pa", 6894.757293168, false),
        Conv("压力", "kgf/cm²", "Pa", 98066.5),

        // 能量 / 功率
        Conv("能量", "cal", "J", 4.184),
        Conv("能量", "kcal", "kJ", 4.184),
        Conv("能量", "kW·h", "J", 3.6e6),
        Conv("能量", "eV", "J", 1.602176634e-19),
        Conv("能量", "erg", "J", 1e-7),
        Conv("能量", "btu", "J", 1055.05585262, false),
        Conv("功率", "hp", "W", 745.6998715823, false),
        Conv("功率", "PS", "W", 735.49875),

        // 温度(特殊:需要偏移,不走比例)
        Conv("温度", "°F", "°C", 0.0),
        Conv("温度", "°C", "K", 0.0)
    )

    /** 按类别分组列出 */
    fun listAll(): String {
        val rows = all.groupBy { it.category }.map { (cat, list) ->
            "$cat:  " + list.joinToString("   ") { "${it.from}→${it.to}" }
        }
        return Display.box("公制换算(${all.size} 项)", rows)
    }

    /**
     * 换算。温度走专门的公式,其余按比例。
     * 查不到时返回 null,由调用方给出提示。
     */
    fun convert(value: Double, from: String, to: String): Pair<Double, String>? {
        val f = from.trim()
        val t = to.trim()
        // 温度:先统一到 K
        val tempUnits = setOf("°C", "°F", "K")
        if (f in tempUnits && t in tempUnits) {
            val kelvin = when (f) {
                "°C" -> value + 273.15
                "°F" -> (value + 459.67) * 5.0 / 9.0
                else -> value
            }
            val out = when (t) {
                "°C" -> kelvin - 273.15
                "°F" -> kelvin * 9.0 / 5.0 - 459.67
                else -> kelvin
            }
            return out to "$value $f = ${Display.fmt(out)} $t  (温度换算,非比例)"
        }
        // 直接匹配
        all.firstOrNull { it.from.equals(f, true) && it.to.equals(t, true) }?.let { c ->
            val out = value * c.factor
            return out to "$value ${c.from} = ${Display.fmt(out)} ${c.to}"
        }
        // 反向
        all.firstOrNull { it.from.equals(t, true) && it.to.equals(f, true) }?.let { c ->
            val out = value / c.factor
            return out to "$value ${c.from} = ${Display.fmt(out)} ${c.to}"
        }
        return null
    }
}
