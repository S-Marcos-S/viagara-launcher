// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemTaskTelemetryTest {

    @Test
    fun `SystemPerformanceSnapshot default values are consistent`() {
        val snapshot = SystemPerformanceSnapshot(
            totalCpuPercent = 14.5,
            cpuCores = 8,
            ramTotalMb = 8192.0,
            ramUsedMb = 4096.0,
            ramUsedPercent = 50.0,
        )

        assertEquals(14.5, snapshot.totalCpuPercent, 0.01)
        assertEquals(8, snapshot.cpuCores)
        assertEquals(8192.0, snapshot.ramTotalMb, 0.01)
        assertEquals(4096.0, snapshot.ramUsedMb, 0.01)
        assertEquals(50.0, snapshot.ramUsedPercent, 0.01)
    }

    @Test
    fun `TaskProcessItem models process accurately`() {
        val item = TaskProcessItem(
            pid = 1234,
            packageName = "com.android.chrome",
            processName = "com.android.chrome:sandboxed_process0",
            appName = "Chrome",
            user = "u0_a123",
            cpuPercent = 5.2,
            ramMb = 128.5,
            isForeground = true,
            isSystemProcess = false,
        )

        assertEquals(1234, item.pid)
        assertEquals("com.android.chrome", item.packageName)
        assertTrue(item.isForeground)
        assertFalse(item.isSystemProcess)
        assertEquals(5.2, item.cpuPercent, 0.01)
        assertEquals(128.5, item.ramMb, 0.01)
    }
}
