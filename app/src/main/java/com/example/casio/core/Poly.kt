package com.example.casio.core

import java.math.BigInteger

/**
 * 以 Rational 为系数的多变量多项式。
 * 键 = 变量名 -> 幂次(排序后的列表),值 = 系数。
 * 对应 Python 版的 Polynomial。
 */
class Poly private constructor(val terms: Map<List<Pair<String, Int>>, Rational>) {

    companion object {
        val ZERO = Poly(emptyMap())

        fun const(c: Rational): Poly =
            if (c.isZero) ZERO else Poly(mapOf(emptyList<Pair<String, Int>>() to c))

        fun const(c: Long): Poly = const(Rational.of(c))

        fun variable(name: String): Poly =
            Poly(mapOf(listOf(name to 1) to Rational.ONE))

        private fun key(d: Map<String, Int>): List<Pair<String, Int>> =
            d.entries.filter { it.value != 0 }.map { it.key to it.value }.sortedBy { it.first }

        fun build(raw: Map<List<Pair<String, Int>>, Rational>): Poly {
            val cleaned = raw.filterValues { !it.isZero }
            return if (cleaned.isEmpty()) ZERO else Poly(cleaned)
        }

        private fun fromMap(d: Map<String, Int>, c: Rational): Poly =
            if (c.isZero) ZERO else Poly(mapOf(key(d) to c))
    }

    val isZero: Boolean get() = terms.isEmpty()

    val isConstant: Boolean get() = terms.keys.all { it.isEmpty() }

    fun constValue(): Rational {
        require(isConstant) { "不是常数" }
        return terms.values.fold(Rational.ZERO) { acc, v -> acc + v }
    }

    /** 只取常数项(其它变量项忽略),方程组移项时用 */
    fun constantPart(): Rational {
        var acc = Rational.ZERO
        for ((k, v) in terms) {
            if (k.all { it.second == 0 }) acc += v
        }
        return acc
    }

    fun variables(): Set<String> {
        val s = mutableSetOf<String>()
        terms.keys.forEach { k -> k.forEach { if (it.second != 0) s.add(it.first) } }
        return s
    }

    /** 某个变量的最高次数 */
    fun degree(name: String? = null): Int {
        if (terms.isEmpty()) return 0
        return if (name == null) {
            terms.keys.maxOf { k -> k.sumOf { it.second } }
        } else {
            terms.keys.maxOf { k -> k.firstOrNull { it.first == name }?.second ?: 0 }
        }
    }

    /**
     * 按某个变量整理成系数表 [c0, c1, ...](c0 为常数项)。
     * 其余变量必须已经消掉,否则抛错。
     */
    fun coefficients(name: String): List<Rational> {
        val out = mutableListOf<Rational>()
        for ((k, v) in terms) {
            val deg = k.firstOrNull { it.first == name }?.second ?: 0
            val others = k.filter { it.first != name && it.second != 0 }
            if (others.isNotEmpty()) throw SolveError("表达式含多个变量,无法按 $name 整理")
            while (out.size <= deg) out.add(Rational.ZERO)
            out[deg] = out[deg] + v
        }
        while (out.isNotEmpty() && out.last().isZero) out.removeAt(out.size - 1)
        return out
    }

    /**
     * 按某个变量降幂分组,其余变量保留为多项式。
     * 求解线性方程组时用:按 x 分组后,各组应当是「不含其它变量的常数」。
     */
    fun termsOf(name: String): List<Poly> {
        val out = mutableListOf<Poly>()
        for ((k, v) in terms) {
            val deg = k.firstOrNull { it.first == name }?.second ?: 0
            val rest = k.filter { it.first != name && it.second != 0 }
            while (out.size <= deg) out.add(Poly.ZERO)
            out[deg] = out[deg] + Poly.build(mapOf(rest to v))
        }
        return out
    }

    /** 是否含交叉项(如 x*y),线性方程组判定用 */
    fun crossTerm(): String? {
        for (k in terms.keys) {
            if (k.count { it.second != 0 } > 1) return k.joinToString("*") { it.first }
        }
        return null
    }

    operator fun plus(o: Poly): Poly {
        val m = terms.toMutableMap()
        for ((k, v) in o.terms) {
            val nv = (m[k] ?: Rational.ZERO) + v
            if (nv.isZero) m.remove(k) else m[k] = nv
        }
        return build(m)
    }

    operator fun minus(o: Poly): Poly = this + o.negate()

    operator fun unaryMinus(): Poly = negate()

    fun negate(): Poly = build(terms.mapValues { it.value.unaryMinus() })

    operator fun times(o: Poly): Poly {
        val m = mutableMapOf<List<Pair<String, Int>>, Rational>()
        for ((k1, v1) in terms) {
            for ((k2, v2) in o.terms) {
                val d = mutableMapOf<String, Int>()
                k1.forEach { d[it.first] = (d[it.first] ?: 0) + it.second }
                k2.forEach { d[it.first] = (d[it.first] ?: 0) + it.second }
                val kk = key(d.filterValues { it != 0 })
                m[kk] = (m[kk] ?: Rational.ZERO) + v1 * v2
            }
        }
        return build(m)
    }

    fun scale(c: Rational): Poly =
        if (c.isZero) ZERO else build(terms.mapValues { it.value * c })

    fun pow(n: Int): Poly {
        require(n >= 0) { "多项式不支持负次幂" }
        var result = const(Rational.ONE)
        var base = this
        var e = n
        while (e > 0) {
            if (e and 1 == 1) result *= base
            base *= base
            e = e shr 1
        }
        return result
    }

    /** 用 Horner 法精确求值 */
    fun evalAt(name: String, x: Rational): Rational {
        val cs = coefficients(name)
        if (cs.isEmpty()) return Rational.ZERO
        var acc = Rational.ZERO
        for (i in cs.indices.reversed()) acc = acc * x + cs[i]
        return acc
    }
}

/** 求解过程中的预期错误(与解析错误区分开,便于界面提示) */
class SolveError(message: String) : Exception(message)

/**
 * 把语法树展开成多项式。
 * 含 sin/ln 等非多项式成分、或分母含变量时抛 SolveError。
 */
object PolyBuilder {

    fun fromNode(node: Node): Poly {
        return when (node) {
            is NumNode -> Poly.const(node.exact ?: Rational.fromDouble(node.value))
            is ConstNode -> throw SolveError("含常量 ${node.name},不是多项式")
            is VarNode -> Poly.variable(node.name)
            is UnaryNode -> {
                val inner = fromNode(node.operand)
                if (node.op == "-") inner.negate() else inner
            }
            is BinaryNode -> {
                val l = fromNode(node.left)
                val r = fromNode(node.right)
                when (node.op) {
                    "+" -> l + r
                    "-" -> l - r
                    "*" -> l * r
                    "/" -> {
                        if (!r.isConstant) throw SolveError("分母含变量,不是多项式")
                        val c = r.constValue()
                        if (c.isZero) throw ArithmeticException("除数不能为 0")
                        l.scale(Rational.ONE / c)
                    }
                    "^" -> {
                        if (!r.isConstant) throw SolveError("指数含变量,不是多项式")
                        val e = r.constValue()
                        if (!e.isInteger) {
                            if (l.isConstant) {
                                Poly.const(
                                    Rational.fromDouble(
                                        Math.pow(l.constValue().toDouble(), e.toDouble())
                                    )
                                )
                            } else {
                                throw SolveError("非整数次幂,不是多项式")
                            }
                        } else {
                            l.pow(e.num.toInt())
                        }
                    }
                    else -> throw SolveError("含非多项式运算: ${node.op}")
                }
            }
            is CallNode -> throw SolveError("含函数 ${node.name},不是多项式")
        }
    }
}
