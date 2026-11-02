// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.history

import org.json.JSONArray
import org.json.JSONObject

/**
 * Hourly data point for internet usage (0h to 23h).
 */
data class HourlyNetUsagePoint(
    val hour: Int,
    val label: String,
    val wifiBytes: Long = 0L,
    val mobileBytes: Long = 0L,
) {
    val totalBytes: Long get() = wifiBytes + mobileBytes

    fun toJson(): JSONObject = JSONObject().apply {
        put("hour", hour)
        put("label", label)
        put("wifiBytes", wifiBytes)
        put("mobileBytes", mobileBytes)
    }

    companion object {
        fun fromJson(json: JSONObject): HourlyNetUsagePoint {
            return HourlyNetUsagePoint(
                hour = json.optInt("hour", 0),
                label = json.optString("label", ""),
                wifiBytes = json.optLong("wifiBytes", 0L),
                mobileBytes = json.optLong("mobileBytes", 0L),
            )
        }
    }
}

/**
 * Application launch and screen time statistics for a specific day.
 */
data class AppDailyLaunchStat(
    val packageName: String,
    val appName: String,
    val launchCount: Int = 0,
    val screenTimeMs: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("packageName", packageName)
        put("appName", appName)
        put("launchCount", launchCount)
        put("screenTimeMs", screenTimeMs)
    }

    companion object {
        fun fromJson(json: JSONObject): AppDailyLaunchStat {
            return AppDailyLaunchStat(
                packageName = json.optString("packageName", ""),
                appName = json.optString("appName", ""),
                launchCount = json.optInt("launchCount", 0),
                screenTimeMs = json.optLong("screenTimeMs", 0L),
            )
        }
    }
}

/**
 * Top battery consumer app record for a day.
 */
data class DailyBatteryConsumer(
    val packageName: String,
    val appName: String,
    val drainPercent: Double = 0.0,
    val drainMah: Long? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("packageName", packageName)
        put("appName", appName)
        put("drainPercent", drainPercent)
        if (drainMah != null) put("drainMah", drainMah)
    }

    companion object {
        fun fromJson(json: JSONObject): DailyBatteryConsumer {
            val mah = if (json.has("drainMah")) json.optLong("drainMah") else null
            return DailyBatteryConsumer(
                packageName = json.optString("packageName", ""),
                appName = json.optString("appName", ""),
                drainPercent = json.optDouble("drainPercent", 0.0),
                drainMah = mah,
            )
        }
    }
}

/**
 * Timeline point of battery level and device state throughout a day.
 */
data class BatteryTimelinePoint(
    val timestamp: Long, // epoch ms
    val batteryLevel: Int, // 0 - 100
    val state: Int, // 0 = DEEP_SLEEP, 1 = SCREEN_ON, 2 = OFF
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("timestamp", timestamp)
        put("batteryLevel", batteryLevel)
        put("state", state)
    }

    companion object {
        const val STATE_DEEP_SLEEP = 0
        const val STATE_SCREEN_ON = 1
        const val STATE_OFF = 2

        fun fromJson(json: JSONObject): BatteryTimelinePoint {
            return BatteryTimelinePoint(
                timestamp = json.optLong("timestamp", 0L),
                batteryLevel = json.optInt("batteryLevel", 0),
                state = json.optInt("state", STATE_DEEP_SLEEP),
            )
        }
    }
}

/**
 * Consolidated daily usage record containing all metrics for a given date.
 * dateKey format: "yyyy-MM-dd" (e.g., "2026-11-01")
 */
data class DailyUsageRecord(
    val dateKey: String,
    val timestamp: Long,
    val batteryDrainPercent: Int = 0,
    val batteryDrainMah: Long? = null,
    val screenOnTimeMs: Long = 0L,
    val screenOffTimeMs: Long = 0L,
    val rebootCount: Int = 0,
    val shutdownCount: Int = 0,
    val averageRamPercent: Double = 0.0,
    val ramSamplesCount: Int = 0,
    val totalWifiBytes: Long = 0L,
    val totalMobileBytes: Long = 0L,
    val hourlyNetUsage: List<HourlyNetUsagePoint> = emptyList(),
    val appLaunches: List<AppDailyLaunchStat> = emptyList(),
    val topBatteryConsumers: List<DailyBatteryConsumer> = emptyList(),
    val backgroundAnomaliesCount: Int = 0,
    val deepSleepDisruptionsCount: Int = 0,
    val batteryTimeline: List<BatteryTimelinePoint> = emptyList(),
) {
    val totalInternetBytes: Long get() = totalWifiBytes + totalMobileBytes
    val totalRebootsAndShutdowns: Int get() = rebootCount + shutdownCount

    fun toJson(): JSONObject = JSONObject().apply {
        put("dateKey", dateKey)
        put("timestamp", timestamp)
        put("batteryDrainPercent", batteryDrainPercent)
        if (batteryDrainMah != null) put("batteryDrainMah", batteryDrainMah)
        put("screenOnTimeMs", screenOnTimeMs)
        put("screenOffTimeMs", screenOffTimeMs)
        put("rebootCount", rebootCount)
        put("shutdownCount", shutdownCount)
        put("averageRamPercent", averageRamPercent)
        put("ramSamplesCount", ramSamplesCount)
        put("totalWifiBytes", totalWifiBytes)
        put("totalMobileBytes", totalMobileBytes)
        put("backgroundAnomaliesCount", backgroundAnomaliesCount)
        put("deepSleepDisruptionsCount", deepSleepDisruptionsCount)

        val netArray = JSONArray()
        hourlyNetUsage.forEach { netArray.put(it.toJson()) }
        put("hourlyNetUsage", netArray)

        val launchArray = JSONArray()
        appLaunches.forEach { launchArray.put(it.toJson()) }
        put("appLaunches", launchArray)

        val battArray = JSONArray()
        topBatteryConsumers.forEach { battArray.put(it.toJson()) }
        put("topBatteryConsumers", battArray)

        val timelineArray = JSONArray()
        batteryTimeline.forEach { timelineArray.put(it.toJson()) }
        put("batteryTimeline", timelineArray)
    }

    companion object {
        fun fromJson(json: JSONObject): DailyUsageRecord {
            val dateKey = json.optString("dateKey", "")
            val timestamp = json.optLong("timestamp", 0L)
            val battDrain = json.optInt("batteryDrainPercent", 0)
            val battMah = if (json.has("batteryDrainMah")) json.optLong("batteryDrainMah") else null
            val sot = json.optLong("screenOnTimeMs", 0L)
            val soft = json.optLong("screenOffTimeMs", 0L)
            val reboots = json.optInt("rebootCount", 0)
            val shutdowns = json.optInt("shutdownCount", 0)
            val ramPct = json.optDouble("averageRamPercent", 0.0)
            val ramSamples = json.optInt("ramSamplesCount", 0)
            val wifi = json.optLong("totalWifiBytes", 0L)
            val mobile = json.optLong("totalMobileBytes", 0L)
            val anomCount = json.optInt("backgroundAnomaliesCount", 0)
            val deepSleepCount = json.optInt("deepSleepDisruptionsCount", 0)

            val netList = mutableListOf<HourlyNetUsagePoint>()
            val netArray = json.optJSONArray("hourlyNetUsage")
            if (netArray != null) {
                for (i in 0 until netArray.length()) {
                    val obj = netArray.optJSONObject(i)
                    if (obj != null) netList.add(HourlyNetUsagePoint.fromJson(obj))
                }
            }

            val launchList = mutableListOf<AppDailyLaunchStat>()
            val launchArray = json.optJSONArray("appLaunches")
            if (launchArray != null) {
                for (i in 0 until launchArray.length()) {
                    val obj = launchArray.optJSONObject(i)
                    if (obj != null) launchList.add(AppDailyLaunchStat.fromJson(obj))
                }
            }

            val battList = mutableListOf<DailyBatteryConsumer>()
            val battArray = json.optJSONArray("topBatteryConsumers")
            if (battArray != null) {
                for (i in 0 until battArray.length()) {
                    val obj = battArray.optJSONObject(i)
                    if (obj != null) battList.add(DailyBatteryConsumer.fromJson(obj))
                }
            }

            val timelineList = mutableListOf<BatteryTimelinePoint>()
            val timelineArray = json.optJSONArray("batteryTimeline")
            if (timelineArray != null) {
                for (i in 0 until timelineArray.length()) {
                    val obj = timelineArray.optJSONObject(i)
                    if (obj != null) timelineList.add(BatteryTimelinePoint.fromJson(obj))
                }
            }

            return DailyUsageRecord(
                dateKey = dateKey,
                timestamp = timestamp,
                batteryDrainPercent = battDrain,
                batteryDrainMah = battMah,
                screenOnTimeMs = sot,
                screenOffTimeMs = soft,
                rebootCount = reboots,
                shutdownCount = shutdowns,
                averageRamPercent = ramPct,
                ramSamplesCount = ramSamples,
                totalWifiBytes = wifi,
                totalMobileBytes = mobile,
                hourlyNetUsage = netList,
                appLaunches = launchList,
                topBatteryConsumers = battList,
                backgroundAnomaliesCount = anomCount,
                deepSleepDisruptionsCount = deepSleepCount,
                batteryTimeline = timelineList,
            )
        }
    }
}
