package com.mide.trifedetect

import android.content.Context

/**
 * 本地持久化: 公式 (全局一个) 与按设备蓝牙 MAC 分开存储的校准值。
 * 使用 SharedPreferences, 无第三方依赖。
 */
class FormulaStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // -------------------------------------------------------------- 公式

    fun getFormula(): String = prefs.getString(KEY_FORMULA, Formula.DEFAULT_EXPRESSION)
        ?: Formula.DEFAULT_EXPRESSION

    fun saveFormula(expression: String) {
        prefs.edit().putString(KEY_FORMULA, expression).apply()
    }

    // ------------------------------------------------------ 校准值 (按 MAC)

    fun saveCalibration(mac: String, rawValue: Double, uvLightLevel: Int) {
        prefs.edit().putString(calKey(mac), "$rawValue,$uvLightLevel").apply()
    }

    fun getCalibration(mac: String): TriFeProtocol.Calibration? {
        val raw = prefs.getString(calKey(mac), null) ?: return null
        val parts = raw.split(",")
        if (parts.size != 2) return null
        val value = parts[0].toDoubleOrNull() ?: return null
        val uv = parts[1].toIntOrNull() ?: return null
        return TriFeProtocol.Calibration(value, uv)
    }

    fun hasCalibration(mac: String): Boolean = prefs.contains(calKey(mac))

    fun clearCalibration(mac: String) {
        prefs.edit().remove(calKey(mac)).apply()
    }

    private fun calKey(mac: String) = "cal_${mac.uppercase()}"

    companion object {
        private const val PREFS_NAME = "trifedetect"
        private const val KEY_FORMULA = "formula"
    }
}
