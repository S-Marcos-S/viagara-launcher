// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.battery

import org.json.JSONArray
import org.json.JSONObject

enum class CycleType {
    CHARGE,
    DISCHARGE,
}

enum class SpeedUnit {
    WATTS,   // W
    VOLTS,   // V
    AMPERES, // A
}

data class CycleTelemetryPoint(
    val timestamp: Long,
    val batteryLevel: Int, // 0 - 100
    val batteryTempC: Float, // °C
    val powerWatts: Float, // W
    val voltageV: Float, // V
    val currentA: Float, // A
    val isScreenOn: Boolean,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("timestamp", timestamp)
        put("batteryLevel", batteryLevel)
        put("batteryTempC", batteryTempC.toDouble())
        put("powerWatts", powerWatts.toDouble())
        put("voltageV", voltageV.toDouble())
        put("currentA", currentA.toDouble())
        put("isScreenOn", isScreenOn)
    }

    companion object {
        fun fromJson(json: JSONObject): CycleTelemetryPoint {
            return CycleTelemetryPoint(
                timestamp = json.optLong("timestamp", 0L),
                batteryLevel = json.optInt("batteryLevel", 0),
                batteryTempC = json.optDouble("batteryTempC", 0.0).toFloat(),
                powerWatts = json.optDouble("powerWatts", 0.0).toFloat(),
                voltageV = json.optDouble("voltageV", 0.0).toFloat(),
                currentA = json.optDouble("currentA", 0.0).toFloat(),
                isScreenOn = json.optBoolean("isScreenOn", false),
            )
        }
    }
}

data class CycleAppUsage(
    val packageName: String,
    val appName: String,
    val drainMah: Double,
    val drainPercent: Float,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("packageName", packageName)
        put("appName", appName)
        put("drainMah", drainMah)
        put("drainPercent", drainPercent.toDouble())
    }

    companion object {
        fun fromJson(json: JSONObject): CycleAppUsage {
            return CycleAppUsage(
                packageName = json.optString("packageName", ""),
                appName = json.optString("appName", ""),
                drainMah = json.optDouble("drainMah", 0.0),
                drainPercent = json.optDouble("drainPercent", 0.0).toFloat(),
            )
        }
    }
}

data class BatteryCycleRecord(
    val id: String,
    val type: CycleType,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val startBatteryLevel: Int,
    val endBatteryLevel: Int,
    val totalMah: Double,
    val totalCyclesDelta: Float,
    // Discharge metrics
    val deepSleepTimeMs: Long = 0L,
    val deepSleepDrainMah: Double = 0.0,
    val lightSleepTimeMs: Long = 0L,
    val lightSleepDrainMah: Double = 0.0,
    val screenOnTimeMs: Long = 0L,
    val screenOnDrainMah: Double = 0.0,
    val appUsages: List<CycleAppUsage> = emptyList(),
    // Telemetry series
    val telemetryPoints: List<CycleTelemetryPoint> = emptyList(),
    val isActive: Boolean = false,
) {
    val durationMs: Long
        get() = (endTimeMs - startTimeMs).coerceAtLeast(0L)

    val batteryDeltaPercent: Int
        get() = if (type == CycleType.CHARGE) {
            (endBatteryLevel - startBatteryLevel).coerceAtLeast(0)
        } else {
            (startBatteryLevel - endBatteryLevel).coerceAtLeast(0)
        }

    val averageDrainOrChargeRateMa: Double
        get() {
            val hours = durationMs / 3600000.0
            return if (hours > 0.01) totalMah / hours else 0.0
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("startTimeMs", startTimeMs)
        put("endTimeMs", endTimeMs)
        put("startBatteryLevel", startBatteryLevel)
        put("endBatteryLevel", endBatteryLevel)
        put("totalMah", totalMah)
        put("totalCyclesDelta", totalCyclesDelta.toDouble())
        put("deepSleepTimeMs", deepSleepTimeMs)
        put("deepSleepDrainMah", deepSleepDrainMah)
        put("lightSleepTimeMs", lightSleepTimeMs)
        put("lightSleepDrainMah", lightSleepDrainMah)
        put("screenOnTimeMs", screenOnTimeMs)
        put("screenOnDrainMah", screenOnDrainMah)
        put("isActive", isActive)

        val appArray = JSONArray()
        appUsages.forEach { appArray.put(it.toJson()) }
        put("appUsages", appArray)

        val pointArray = JSONArray()
        telemetryPoints.forEach { pointArray.put(it.toJson()) }
        put("telemetryPoints", pointArray)
    }

    companion object {
        fun fromJson(json: JSONObject): BatteryCycleRecord {
            val typeStr = json.optString("type", CycleType.DISCHARGE.name)
            val type = runCatching { CycleType.valueOf(typeStr) }.getOrDefault(CycleType.DISCHARGE)

            val apps = mutableListOf<CycleAppUsage>()
            val appArray = json.optJSONArray("appUsages")
            if (appArray != null) {
                for (i in 0 until appArray.length()) {
                    appArray.optJSONObject(i)?.let { apps.add(CycleAppUsage.fromJson(it)) }
                }
            }

            val points = mutableListOf<CycleTelemetryPoint>()
            val pointArray = json.optJSONArray("telemetryPoints")
            if (pointArray != null) {
                for (i in 0 until pointArray.length()) {
                    pointArray.optJSONObject(i)?.let { points.add(CycleTelemetryPoint.fromJson(it)) }
                }
            }

            return BatteryCycleRecord(
                id = json.optString("id", ""),
                type = type,
                startTimeMs = json.optLong("startTimeMs", 0L),
                endTimeMs = json.optLong("endTimeMs", 0L),
                startBatteryLevel = json.optInt("startBatteryLevel", 0),
                endBatteryLevel = json.optInt("endBatteryLevel", 0),
                totalMah = json.optDouble("totalMah", 0.0),
                totalCyclesDelta = json.optDouble("totalCyclesDelta", 0.0).toFloat(),
                deepSleepTimeMs = json.optLong("deepSleepTimeMs", 0L),
                deepSleepDrainMah = json.optDouble("deepSleepDrainMah", 0.0),
                lightSleepTimeMs = json.optLong("lightSleepTimeMs", 0L),
                lightSleepDrainMah = json.optDouble("lightSleepDrainMah", 0.0),
                screenOnTimeMs = json.optLong("screenOnTimeMs", 0L),
                screenOnDrainMah = json.optDouble("screenOnDrainMah", 0.0),
                appUsages = apps,
                telemetryPoints = points,
                isActive = json.optBoolean("isActive", false),
            )
        }
    }
}
