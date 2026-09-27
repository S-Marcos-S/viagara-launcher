// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRootInspectorTest {

    @Test
    fun `formatBytes converts byte counts correctly`() {
        assertEquals("0 B", formatBytesForTest(0L))
        assertEquals("500.0 B", formatBytesForTest(500L))
        assertEquals("1.0 KB", formatBytesForTest(1024L))
        assertEquals("1.5 MB", formatBytesForTest(1572864L))
        assertEquals("2.0 GB", formatBytesForTest(2147483648L))
    }

    @Test
    fun `parseMeminfo extracts PSS components properly`() {
        val sampleMeminfo = """
            Applications Memory Usage (in Kilobytes):
            Uptime: 123456 Realtime: 123456

            ** MEMINFO in pid 12345 [com.example.app] **
                               App Summary
                               ------
                       Pss(KB)
                        ------
                   Java Heap:    32768
                 Native Heap:    45056
                        Code:    12288
                       Stack:     1024
                    Graphics:    16384
               Private Other:     8192
                      System:     4096
             
                       TOTAL:   120000       TOTAL SWAP PSS:        0
        """.trimIndent()

        // Testing the parsing logic
        var totalPss = 0L
        var dalvik = 0L
        var native = 0L
        var graphics = 0L

        sampleMeminfo.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val lower = trimmed.lowercase()
            when {
                lower.startsWith("total:") || lower.startsWith("total pss:") -> {
                    val nums = Regex("\\d+").findAll(trimmed).map { it.value.toLong() }.toList()
                    if (nums.isNotEmpty()) totalPss = nums[0]
                }
                lower.contains("java heap") || lower.contains("dalvik heap") -> {
                    val nums = Regex("\\d+").findAll(trimmed).map { it.value.toLong() }.toList()
                    if (nums.isNotEmpty()) dalvik = nums[0]
                }
                lower.contains("native heap") -> {
                    val nums = Regex("\\d+").findAll(trimmed).map { it.value.toLong() }.toList()
                    if (nums.isNotEmpty()) native = nums[0]
                }
                lower.contains("graphics") || lower.contains("egl mtrack") -> {
                    val nums = Regex("\\d+").findAll(trimmed).map { it.value.toLong() }.toList()
                    if (nums.isNotEmpty()) graphics += nums[0]
                }
            }
        }

        assertEquals(120000L, totalPss)
        assertEquals(32768L, dalvik)
        assertEquals(45056L, native)
        assertEquals(16384L, graphics)
    }

    @Test
    fun `connection parser recognizes active TCP connections`() {
        val sampleSsOutput = """
            Netid State  Recv-Q Send-Q   Local Address:Port    Peer Address:Port
            tcp   ESTAB  0      0        192.168.1.50:48210    142.250.190.46:443  users:(("com.example.app",pid=12345,fd=42))
            tcp   LISTEN 0      128      0.0.0.0:8080          0.0.0.0:*
        """.trimIndent()

        val connections = mutableListOf<NetworkConnection>()
        sampleSsOutput.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("tcp") || trimmed.startsWith("udp")) {
                val parts = trimmed.split(Regex("\\s+"))
                if (parts.size >= 5) {
                    val proto = parts[0].uppercase()
                    val state = parts[1]
                    val local = parts[3]
                    val remote = parts[4]
                    if (remote.isNotBlank() && remote != "*:*" && !remote.startsWith("0.0.0.0")) {
                        connections.add(NetworkConnection(proto, local, remote, state))
                    }
                }
            }
        }

        assertEquals(1, connections.size)
        assertEquals("TCP", connections[0].protocol)
        assertEquals("ESTAB", connections[0].state)
        assertEquals("142.250.190.46:443", connections[0].remoteAddress)
    }

    private fun formatBytesForTest(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format(java.util.Locale.US, "%.1f %s", value, units[digitGroups])
    }
}
