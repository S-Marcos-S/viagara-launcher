// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedMonitorAppDeltasTest {

    @Test
    fun calculateActiveAppsFromDeltas_correctlyComputesSpeedAndSorts() {
        val previousTotals = mapOf(
            10100 to 1_000_000L,
            10200 to 5_000_000L,
            10300 to 2_000_000L
        )
        val currentTotals = mapOf(
            10100 to 1_200_000L, // delta = 200,000 bytes
            10200 to 6_000_000L, // delta = 1,000,000 bytes
            10300 to 2_010_000L  // delta = 10,000 bytes
        )
        val deltaTimeSec = 2.0
        val minBytesPerSec = 10_000.0 // 10 KB/s

        val names = mapOf(
            10100 to "Browser",
            10200 to "YouTube",
            10300 to "Messenger"
        )

        val result = SpeedMonitorService.calculateActiveAppsFromDeltas(
            currentTotals = currentTotals,
            previousTotals = previousTotals,
            deltaTimeSec = deltaTimeSec,
            minBytesPerSec = minBytesPerSec,
            resolveName = { names[it] }
        )

        // 10200 speed = 1,000,000 / 2 = 500,000 B/s
        // 10100 speed = 200,000 / 2 = 100,000 B/s
        // 10300 speed = 10,000 / 2 = 5,000 B/s (below 10,000 threshold -> excluded)
        assertEquals(2, result.size)
        assertEquals("YouTube", result[0].appName)
        assertEquals(500_000.0, result[0].speedBytesSec, 0.001)

        assertEquals("Browser", result[1].appName)
        assertEquals(100_000.0, result[1].speedBytesSec, 0.001)
    }

    @Test
    fun calculateActiveAppsFromDeltas_excludesSelfOrNullNames() {
        val previous = mapOf(10100 to 100L)
        val current = mapOf(10100 to 100_100L)

        val result = SpeedMonitorService.calculateActiveAppsFromDeltas(
            currentTotals = current,
            previousTotals = previous,
            deltaTimeSec = 1.0,
            minBytesPerSec = 10.0,
            resolveName = { null } // Self app returns null
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun calculateActiveAppsFromDeltas_handlesZeroOrNegativeDeltaTime() {
        val previous = mapOf(10100 to 100L)
        val current = mapOf(10100 to 500L)

        val resultZero = SpeedMonitorService.calculateActiveAppsFromDeltas(
            currentTotals = current,
            previousTotals = previous,
            deltaTimeSec = 0.0,
            minBytesPerSec = 10.0,
            resolveName = { "App" }
        )
        assertTrue(resultZero.isEmpty())

        val resultNeg = SpeedMonitorService.calculateActiveAppsFromDeltas(
            currentTotals = current,
            previousTotals = previous,
            deltaTimeSec = -1.0,
            minBytesPerSec = 10.0,
            resolveName = { "App" }
        )
        assertTrue(resultNeg.isEmpty())
    }

    @Test
    fun calculateActiveAppsFromDeltas_includesRootAndSystemUids() {
        val previous = mapOf(
            0 to 100_000L,
            1000 to 200_000L
        )
        val current = mapOf(
            0 to 300_000L,   // delta 200,000 in 1s = 200,000 B/s
            1000 to 250_000L  // delta 50,000 in 1s = 50,000 B/s
        )

        val result = SpeedMonitorService.calculateActiveAppsFromDeltas(
            currentTotals = current,
            previousTotals = previous,
            deltaTimeSec = 1.0,
            minBytesPerSec = 10_000.0,
            resolveName = { uid ->
                when (uid) {
                    0 -> "Sistema (Root)"
                    1000 -> "Sistema Android"
                    else -> null
                }
            }
        )

        assertEquals(2, result.size)
        assertEquals("Sistema (Root)", result[0].appName)
        assertEquals(200_000.0, result[0].speedBytesSec, 0.001)
        assertEquals("Sistema Android", result[1].appName)
        assertEquals(50_000.0, result[1].speedBytesSec, 0.001)
    }

    @Test
    fun calculateActiveAppsFromDeltas_capsSingleAppToTotalDeviceSpeed() {
        val previous = mapOf(10100 to 0L)
        val current = mapOf(10100 to 3_000_000L) // Delta = 3 MB/s in 1s

        val totalDeviceSpeed = 1_000_000.0 // Total device speed is 1 MB/s

        val result = SpeedMonitorService.calculateActiveAppsFromDeltas(
            currentTotals = current,
            previousTotals = previous,
            deltaTimeSec = 1.0,
            minBytesPerSec = 10_000.0,
            totalDeviceSpeed = totalDeviceSpeed,
            resolveName = { "YouTube" }
        )

        assertEquals(1, result.size)
        assertEquals("YouTube", result[0].appName)
        // Must be capped to totalDeviceSpeed (1 MB/s), NOT 3 MB/s!
        assertEquals(1_000_000.0, result[0].speedBytesSec, 0.001)
    }

    @Test
    fun calculateActiveAppsFromDeltas_proportionallyNormalizesMultipleAppsWhenExceedingTotalSpeed() {
        val previous = mapOf(
            10100 to 0L,
            10200 to 0L
        )
        val current = mapOf(
            10100 to 2_000_000L, // Raw 2 MB/s
            10200 to 1_000_000L  // Raw 1 MB/s
        ) // Raw sum = 3 MB/s

        val totalDeviceSpeed = 1_200_000.0 // Actual total speed = 1.2 MB/s

        val result = SpeedMonitorService.calculateActiveAppsFromDeltas(
            currentTotals = current,
            previousTotals = previous,
            deltaTimeSec = 1.0,
            minBytesPerSec = 10_000.0,
            totalDeviceSpeed = totalDeviceSpeed,
            resolveName = { if (it == 10100) "App1" else "App2" }
        )

        assertEquals(2, result.size)
        assertEquals("App1", result[0].appName)
        assertEquals(800_000.0, result[0].speedBytesSec, 0.001) // 2/3 of 1.2 MB/s = 800 KB/s

        assertEquals("App2", result[1].appName)
        assertEquals(400_000.0, result[1].speedBytesSec, 0.001) // 1/3 of 1.2 MB/s = 400 KB/s
    }

    @Test
    fun calculateActiveAppsFromDeltas_keepsOriginalSpeedWhenBelowTotalSpeed() {
        val previous = mapOf(10100 to 0L)
        val current = mapOf(10100 to 500_000L) // 500 KB/s

        val totalDeviceSpeed = 1_000_000.0 // Total device speed is 1 MB/s

        val result = SpeedMonitorService.calculateActiveAppsFromDeltas(
            currentTotals = current,
            previousTotals = previous,
            deltaTimeSec = 1.0,
            minBytesPerSec = 10_000.0,
            totalDeviceSpeed = totalDeviceSpeed,
            resolveName = { "App" }
        )

        assertEquals(1, result.size)
        assertEquals("App", result[0].appName)
        assertEquals(500_000.0, result[0].speedBytesSec, 0.001)
    }
}
