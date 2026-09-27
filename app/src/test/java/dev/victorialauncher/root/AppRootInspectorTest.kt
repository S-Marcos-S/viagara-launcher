// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRootInspectorTest {

    @Test
    fun `formatBytes converts byte counts correctly`() {
        assertEquals("0 B", AppRootInspector.formatBytes(0L))
        assertEquals("500.0 B", AppRootInspector.formatBytes(500L))
        assertEquals("1.0 KB", AppRootInspector.formatBytes(1024L))
        assertEquals("1.5 MB", AppRootInspector.formatBytes(1572864L))
        assertEquals("2.0 GB", AppRootInspector.formatBytes(2147483648L))
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

        val (totalPss, dalvik, native, graphics) = AppRootInspector.parseMeminfo(sampleMeminfo)

        assertEquals(120000L, totalPss)
        assertEquals(32768L, dalvik)
        assertEquals(45056L, native)
        assertEquals(16384L, graphics)
    }

    @Test
    fun `parseMeminfo parses multi-process dumpsys output properly`() {
        val sampleMultiProcessMeminfo = """
            Total PSS by process:
                152,342K: com.android.vending (pid 12345)
                 43,210K: com.android.vending:download_service (pid 12346)
        """.trimIndent()

        val (totalPss, _, _, _) = AppRootInspector.parseMeminfo(sampleMultiProcessMeminfo)

        assertEquals(195552L, totalPss)
    }

    @Test
    fun `parseSmaps extracts PSS Anon and File correctly from kernel rollup`() {
        val sampleSmaps = """
            ---PID:12345---
            Rss:              200384 kB
            Pss:               75899 kB
            Pss_Anon:          59971 kB
            Pss_File:           6461 kB
            ---PID:12346---
            Rss:               50000 kB
            Pss:               25000 kB
            Pss_Anon:          15000 kB
            Pss_File:           2000 kB
        """.trimIndent()

        val (totalPss, dalvik, native, totalRss) = AppRootInspector.parseSmaps(sampleSmaps)

        assertEquals(100899L, totalPss)
        assertEquals(74971L, dalvik)
        assertEquals(8461L, native)
        assertEquals(250384L, totalRss)
    }

    @Test
    fun `parseNetstatsOutput extracts bytes from eBPF netstats correctly`() {
        val sampleNetstats = """
            ident=[type=WIFI, subType=COMBINED] uid=10185 set=DEFAULT tag=0x0 rxBytes=15829381 rxPackets=12984 txBytes=1492042 txPackets=9812
            ident=[type=WIFI, subType=COMBINED] uid=10185 set=BACKGROUND tag=0x0 rxBytes=50000 rxPackets=20 txBytes=10000 txPackets=10
            ident=[type=WIFI, subType=COMBINED] uid=10185 set=DEFAULT tag=0x123 rxBytes=99999 txBytes=99999
            ident=[type=WIFI, subType=COMBINED] uid=10099 set=DEFAULT tag=0x0 rxBytes=888888 txBytes=888888
        """.trimIndent()

        val (rx, tx) = AppRootInspector.parseNetstatsOutput(sampleNetstats, 10185)

        assertEquals(15879381L, rx)
        assertEquals(1502042L, tx)
    }

    @Test
    fun `parseNetstatsOutput supports rb and tb format`() {
        val sampleNetstats = """
            uid=10185 set=DEFAULT tag=0x0 rb=1024 rp=1 tb=512 tp=1
        """.trimIndent()

        val (rx, tx) = AppRootInspector.parseNetstatsOutput(sampleNetstats, 10185)

        assertEquals(1024L, rx)
        assertEquals(512L, tx)
    }

    @Test
    fun `isAppInForeground accurately verifies current focused package`() {
        val activitiesTextWithLauncherFocused = """
            mCurrentFocus=Window{12345 u0 dev.victorialauncher/dev.victorialauncher.MainActivity}
            topResumedActivity=ActivityRecord{67890 u0 dev.victorialauncher/dev.victorialauncher.MainActivity t1}
            mResumedActivity: ActivityRecord{67890 u0 dev.victorialauncher/dev.victorialauncher.MainActivity t1}
        """.trimIndent()

        assertFalse(AppRootInspector.isAppInForeground(activitiesTextWithLauncherFocused, "com.android.vending"))
        assertTrue(AppRootInspector.isAppInForeground(activitiesTextWithLauncherFocused, "dev.victorialauncher"))

        val activitiesTextWithPlayStoreFocused = """
            mCurrentFocus=Window{11111 u0 com.android.vending/com.google.android.finsky.activities.MainActivity}
            topResumedActivity=ActivityRecord{22222 u0 com.android.vending/com.google.android.finsky.activities.MainActivity t5}
        """.trimIndent()

        assertTrue(AppRootInspector.isAppInForeground(activitiesTextWithPlayStoreFocused, "com.android.vending"))
    }

    @Test
    fun `parseTopOutput extracts real-time CPU and memory percent`() {
        val sampleTop = """
            24192 root 10 -10 17G 207M 123M S 13.5 2.1 35:54.31 com.android.vending
            24890 root 10 -10 5G  100M  50M S  4.2 1.0 10:20.10 com.android.vending:download_service
            12345 root 10 -10 1G   50M  20M S  0.0 0.5  1:00.00 com.other.app
        """.trimIndent()

        val procs = AppRootInspector.parseTopOutput(sampleTop, "com.android.vending")

        assertEquals(2, procs.size)
        assertEquals(24192, procs[0].pid)
        assertEquals(13.5, procs[0].cpuPercent, 0.01)
        assertEquals(2.1, procs[0].memPercent, 0.01)
        assertEquals(24890, procs[1].pid)
        assertEquals(4.2, procs[1].cpuPercent, 0.01)
    }

    @Test
    fun `formatSpeed formats rates correctly`() {
        assertEquals("", AppRootInspector.formatSpeed(0L))
        assertEquals("1.0 KB/s", AppRootInspector.formatSpeed(1024L))
        assertEquals("2.5 MB/s", AppRootInspector.formatSpeed(2621440L))
    }

    @Test
    fun `connection parser recognizes active TCP connections`() {
        val sampleSsOutput = """
            Netid State  Recv-Q Send-Q   Local Address:Port    Peer Address:Port
            tcp   ESTAB  0      0        192.168.1.50:48210    142.250.190.46:443  users:(("com.example.app",pid=12345,fd=42))
            tcp   LISTEN 0      128      0.0.0.0:8080          0.0.0.0:*
        """.trimIndent()

        val connections = AppRootInspector.parseConnections(sampleSsOutput)

        assertEquals(1, connections.size)
        assertEquals("TCP", connections[0].protocol)
        assertEquals("ESTAB", connections[0].state)
        assertEquals("142.250.190.46:443", connections[0].remoteAddress)
    }

    @Test
    fun `connection parser recognizes netstat active TCP connections`() {
        val sampleNetstatOutput = """
            Proto Recv-Q Send-Q Local Address          Foreign Address        State
            tcp        0      0 192.168.1.50:48210     142.250.190.46:443     ESTABLISHED
            tcp        0      0 0.0.0.0:8080           0.0.0.0:*              LISTEN
        """.trimIndent()

        val connections = AppRootInspector.parseConnections(sampleNetstatOutput)

        assertEquals(1, connections.size)
        assertEquals("TCP", connections[0].protocol)
        assertEquals("ESTABLISHED", connections[0].state)
        assertEquals("142.250.190.46:443", connections[0].remoteAddress)
    }
}
