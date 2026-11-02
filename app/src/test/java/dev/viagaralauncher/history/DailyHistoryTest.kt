// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.history

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyHistoryTest {

    @Test
    fun testHourlyNetUsagePointJson() {
        val point = HourlyNetUsagePoint(
            hour = 14,
            label = "14h",
            wifiBytes = 1048576L,
            mobileBytes = 524288L,
        )
        val json = point.toJson()
        val restored = HourlyNetUsagePoint.fromJson(json)

        assertEquals(14, restored.hour)
        assertEquals("14h", restored.label)
        assertEquals(1048576L, restored.wifiBytes)
        assertEquals(524288L, restored.mobileBytes)
        assertEquals(1572864L, restored.totalBytes)
    }

    @Test
    fun testAppDailyLaunchStatJson() {
        val stat = AppDailyLaunchStat(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            launchCount = 42,
            screenTimeMs = 3600000L,
        )
        val json = stat.toJson()
        val restored = AppDailyLaunchStat.fromJson(json)

        assertEquals("com.whatsapp", restored.packageName)
        assertEquals("WhatsApp", restored.appName)
        assertEquals(42, restored.launchCount)
        assertEquals(3600000L, restored.screenTimeMs)
    }

    @Test
    fun testDailyBatteryConsumerJson() {
        val consumer = DailyBatteryConsumer(
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            drainPercent = 23.5,
            drainMah = 650L,
        )
        val json = consumer.toJson()
        val restored = DailyBatteryConsumer.fromJson(json)

        assertEquals("com.google.android.youtube", restored.packageName)
        assertEquals("YouTube", restored.appName)
        assertEquals(23.5, restored.drainPercent, 0.01)
        assertEquals(650L, restored.drainMah)
    }

    @Test
    fun testBatteryTimelinePointJson() {
        val point = BatteryTimelinePoint(
            timestamp = 1793503600000L,
            batteryLevel = 92,
            state = BatteryTimelinePoint.STATE_SCREEN_ON,
        )
        val json = point.toJson()
        val restored = BatteryTimelinePoint.fromJson(json)

        assertEquals(1793503600000L, restored.timestamp)
        assertEquals(92, restored.batteryLevel)
        assertEquals(BatteryTimelinePoint.STATE_SCREEN_ON, restored.state)
    }

    @Test
    fun testDailyUsageRecordFullSerialization() {
        val record = DailyUsageRecord(
            dateKey = "2026-11-01",
            timestamp = 1793500000000L,
            batteryDrainPercent = 85,
            batteryDrainMah = 3200L,
            screenOnTimeMs = 18000000L, // 5 hours
            screenOffTimeMs = 68400000L, // 19 hours
            rebootCount = 2,
            shutdownCount = 1,
            averageRamPercent = 58.4,
            ramSamplesCount = 120,
            totalWifiBytes = 2147483648L, // 2 GB
            totalMobileBytes = 524288000L, // 500 MB
            hourlyNetUsage = listOf(
                HourlyNetUsagePoint(hour = 0, label = "00h", wifiBytes = 1000L, mobileBytes = 500L),
                HourlyNetUsagePoint(hour = 12, label = "12h", wifiBytes = 50000L, mobileBytes = 20000L),
            ),
            appLaunches = listOf(
                AppDailyLaunchStat("com.whatsapp", "WhatsApp", 45, 7200000L),
                AppDailyLaunchStat("com.android.chrome", "Chrome", 18, 3600000L),
            ),
            topBatteryConsumers = listOf(
                DailyBatteryConsumer("com.whatsapp", "WhatsApp", 32.0, 1024L),
            ),
            backgroundAnomaliesCount = 3,
            deepSleepDisruptionsCount = 2,
            batteryTimeline = listOf(
                BatteryTimelinePoint(1793500000000L, 100, BatteryTimelinePoint.STATE_DEEP_SLEEP),
                BatteryTimelinePoint(1793503600000L, 95, BatteryTimelinePoint.STATE_SCREEN_ON),
                BatteryTimelinePoint(1793507200000L, 90, BatteryTimelinePoint.STATE_OFF),
            ),
        )

        assertEquals(2671771648L, record.totalInternetBytes)
        assertEquals(3, record.totalRebootsAndShutdowns)

        val json = record.toJson()
        val restored = DailyUsageRecord.fromJson(json)

        assertEquals("2026-11-01", restored.dateKey)
        assertEquals(1793500000000L, restored.timestamp)
        assertEquals(85, restored.batteryDrainPercent)
        assertEquals(3200L, restored.batteryDrainMah)
        assertEquals(18000000L, restored.screenOnTimeMs)
        assertEquals(68400000L, restored.screenOffTimeMs)
        assertEquals(2, restored.rebootCount)
        assertEquals(1, restored.shutdownCount)
        assertEquals(3, restored.totalRebootsAndShutdowns)
        assertEquals(58.4, restored.averageRamPercent, 0.01)
        assertEquals(120, restored.ramSamplesCount)
        assertEquals(2147483648L, restored.totalWifiBytes)
        assertEquals(524288000L, restored.totalMobileBytes)
        assertEquals(2671771648L, restored.totalInternetBytes)
        assertEquals(3, restored.backgroundAnomaliesCount)
        assertEquals(2, restored.deepSleepDisruptionsCount)

        assertEquals(2, restored.hourlyNetUsage.size)
        assertEquals(2, restored.appLaunches.size)
        assertEquals("com.whatsapp", restored.appLaunches[0].packageName)
        assertEquals(45, restored.appLaunches[0].launchCount)
        assertEquals(1, restored.topBatteryConsumers.size)
        assertEquals("WhatsApp", restored.topBatteryConsumers[0].appName)

        assertEquals(3, restored.batteryTimeline.size)
        assertEquals(100, restored.batteryTimeline[0].batteryLevel)
        assertEquals(BatteryTimelinePoint.STATE_DEEP_SLEEP, restored.batteryTimeline[0].state)
        assertEquals(95, restored.batteryTimeline[1].batteryLevel)
        assertEquals(BatteryTimelinePoint.STATE_SCREEN_ON, restored.batteryTimeline[1].state)
        assertEquals(90, restored.batteryTimeline[2].batteryLevel)
        assertEquals(BatteryTimelinePoint.STATE_OFF, restored.batteryTimeline[2].state)
    }
}
