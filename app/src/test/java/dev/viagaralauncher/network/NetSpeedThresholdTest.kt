// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.network

import org.junit.Assert.assertEquals
import org.junit.Test

class NetSpeedThresholdTest {

    @Test
    fun zeroOrNegativeValueReturnsZero() {
        assertEquals(0.0, NetSpeedSettings.calculateThresholdInBytes(0f, "KB/s"), 0.001)
        assertEquals(0.0, NetSpeedSettings.calculateThresholdInBytes(-10f, "MB/s"), 0.001)
        assertEquals(0.0, NetSpeedSettings.calculateThresholdInBytes(Float.NaN, "KB/s"), 0.001)
    }

    @Test
    fun bytesUnitCalculatesCorrectly() {
        assertEquals(500.0, NetSpeedSettings.calculateThresholdInBytes(500f, "B"), 0.001)
        assertEquals(500.0, NetSpeedSettings.calculateThresholdInBytes(500f, "B/s"), 0.001)
        assertEquals(500.0, NetSpeedSettings.calculateThresholdInBytes(500f, "b/s"), 0.001)
    }

    @Test
    fun kilobytesUnitCalculatesCorrectly() {
        assertEquals(1024.0, NetSpeedSettings.calculateThresholdInBytes(1f, "KB"), 0.001)
        assertEquals(1024.0, NetSpeedSettings.calculateThresholdInBytes(1f, "KB/s"), 0.001)
        assertEquals(51200.0, NetSpeedSettings.calculateThresholdInBytes(50f, "kb/s"), 0.001)
    }

    @Test
    fun megabytesUnitCalculatesCorrectly() {
        assertEquals(1048576.0, NetSpeedSettings.calculateThresholdInBytes(1f, "MB"), 0.001)
        assertEquals(1048576.0, NetSpeedSettings.calculateThresholdInBytes(1f, "MB/s"), 0.001)
        assertEquals(2621440.0, NetSpeedSettings.calculateThresholdInBytes(2.5f, "mb/s"), 0.001)
    }

    @Test
    fun gigabytesUnitCalculatesCorrectly() {
        assertEquals(1073741824.0, NetSpeedSettings.calculateThresholdInBytes(1f, "GB/s"), 0.001)
    }
}
