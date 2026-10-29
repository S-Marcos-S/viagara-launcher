// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.network

import android.content.Context
import android.content.SharedPreferences

class NetSpeedSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("net_speed_indicator_prefs", Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean("speed_monitor_enabled", false)
        set(value) = prefs.edit().putBoolean("speed_monitor_enabled", value).apply()

    var isRealTimeEnabled: Boolean
        get() = prefs.getBoolean("real_time_enabled", true)
        set(value) = prefs.edit().putBoolean("real_time_enabled", value).apply()

    var isDailyUsageEnabled: Boolean
        get() = prefs.getBoolean("daily_usage_enabled", true)
        set(value) = prefs.edit().putBoolean("daily_usage_enabled", value).apply()

    var thresholdValue: Float
        get() = prefs.getFloat("threshold_value", 0f)
        set(value) = prefs.edit().putFloat("threshold_value", value).apply()

    var thresholdUnit: String
        get() = prefs.getString("threshold_unit", "KB/s") ?: "KB/s"
        set(value) = prefs.edit().putString("threshold_unit", value).apply()

    var updateInterval: Long
        get() = prefs.getLong("update_interval", 1000L)
        set(value) = prefs.edit().putLong("update_interval", value).apply()

    var updateIntervalSeconds: Int
        get() = (updateInterval / 1000L).toInt().coerceIn(1, 10)
        set(value) {
            updateInterval = value.toLong().coerceIn(1L, 10L) * 1000L
        }

    var minAppUsageKb: Long
        get() = prefs.getLong("min_app_usage_kb", 10L)
        set(value) = prefs.edit().putLong("min_app_usage_kb", value).apply()

    fun getThresholdInBytes(): Double = calculateThresholdInBytes(thresholdValue, thresholdUnit)

    companion object {
        fun calculateThresholdInBytes(value: Float, unit: String): Double {
            val normalizedUnit = unit.uppercase().replace("/S", "").trim()
            val dVal = value.toDouble()
            if (dVal.isNaN() || dVal.isInfinite() || dVal <= 0.0) return 0.0
            return when (normalizedUnit) {
                "KB", "K" -> dVal * 1024.0
                "MB", "M" -> dVal * 1024.0 * 1024.0
                "GB", "G" -> dVal * 1024.0 * 1024.0 * 1024.0
                else -> dVal
            }
        }
    }
}
