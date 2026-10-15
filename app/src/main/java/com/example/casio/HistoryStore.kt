package com.example.casio

import android.content.Context
import com.example.casio.core.Calc

/**
 * 计算历史的持久化(SharedPreferences)。
 *
 * 存储格式由 [Calc.HistoryCodec] 负责(长度前缀,算式里出现任何字符都不会错位)。
 * 这里只做「读 / 写 / 清」三件事,并**吞掉所有异常** ——
 * 存档失败绝不能让计算器本身出问题。
 */
object HistoryStore {

    private const val PREF = "casio_history"
    private const val KEY = "entries"
    private const val KEY_VERSION = "version"
    private const val VERSION = 1

    /** 读回历史;失败返回空表 */
    fun load(context: Context): List<String> = try {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        // 版本不符时忽略旧数据(格式将来若有变动,不至于解析出乱七八糟的东西)
        if (sp.getInt(KEY_VERSION, 0) != VERSION) {
            emptyList()
        } else {
            Calc.HistoryCodec.decode(sp.getString(KEY, "") ?: "")
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** 保存历史;失败静默 */
    fun save(context: Context, entries: List<String>) {
        try {
            val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            sp.edit()
                .putInt(KEY_VERSION, VERSION)
                .putString(KEY, Calc.HistoryCodec.encode(entries))
                .apply()
        } catch (e: Exception) {
            // 存档失败不影响计算
        }
    }

    /** 清空存档 */
    fun clear(context: Context) {
        try {
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
        } catch (e: Exception) {
            // 忽略
        }
    }
}
