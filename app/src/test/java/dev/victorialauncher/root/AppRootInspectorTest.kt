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
    fun `parseTopOutput normalizes multi-core CPU usage correctly without exceeding 100 percent`() {
        val sampleMultiCoreTop = """
            24192 root 10 -10 17G 207M 123M S 240.0 2.1 35:54.31 com.android.vending
        """.trimIndent()

        // 8-core CPU: 240% in Irix mode should normalize to 30.0% in Solaris/Windows mode
        val procs = AppRootInspector.parseTopOutput(sampleMultiCoreTop, "com.android.vending", coresCount = 8)
        assertEquals(1, procs.size)
        assertEquals(30.0, procs[0].cpuPercent, 0.01)

        // Extreme multi-core spike exceeding 800% is clamped to 100.0%
        val sampleSpikeTop = """
            24192 root 10 -10 17G 207M 123M S 950.0 2.1 35:54.31 com.android.vending
        """.trimIndent()
        val spikedProcs = AppRootInspector.parseTopOutput(sampleSpikeTop, "com.android.vending", coresCount = 8)
        assertEquals(100.0, spikedProcs[0].cpuPercent, 0.01)
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

    @Test
    fun `isPackageProcess correctly matches package and subprocess names`() {
        assertTrue(AppRootInspector.isPackageProcess("com.android.vending", "com.android.vending"))
        assertTrue(AppRootInspector.isPackageProcess("com.android.vending:download_service", "com.android.vending"))
        assertTrue(AppRootInspector.isPackageProcess("/system/bin/app_process --nice-name=com.android.vending", "com.android.vending"))
        assertTrue(AppRootInspector.isPackageProcess("[com.android.vending]", "com.android.vending"))

        assertFalse(AppRootInspector.isPackageProcess("com.android.vending.other", "com.android.vending"))
        assertFalse(AppRootInspector.isPackageProcess("com.google.android.vending", "com.android.vending"))
    }

    @Test
    fun `parseTopOutput captures extraPackageName when provided`() {
        val sampleTop = """
            24192 root 10 -10 17G 207M 123M S 13.5 2.1 35:54.31 com.android.vending
            25000 root 10 -10 2G   80M  40M S  5.0 0.8  5:12.34 com.android.providers.downloads
            12345 root 10 -10 1G   50M  20M S  0.0 0.5  1:00.00 com.other.app
        """.trimIndent()

        val procs = AppRootInspector.parseTopOutput(
            text = sampleTop,
            packageName = "com.android.vending",
            extraPackageName = "com.android.providers.downloads",
        )

        assertEquals(2, procs.size)
        assertEquals(24192, procs[0].pid)
        assertEquals(25000, procs[1].pid)
        assertEquals(5.0, procs[1].cpuPercent, 0.01)
    }

    @Test
    fun `isAppInForeground rejects background apps that are in recents while another app is focused`() {
        val activitiesTextWithLauncherFocusedAndPlayStoreInRecents = """
            mCurrentFocus=Window{abcdef u0 dev.victorialauncher/dev.victorialauncher.MainActivity}
            mFocusedApp=ActivityRecord{123456 u0 dev.victorialauncher/dev.victorialauncher.MainActivity t10}
            topResumedActivity=ActivityRecord{123456 u0 dev.victorialauncher/dev.victorialauncher.MainActivity t10}
            Task{id=42 mResumedActivity=ActivityRecord{999999 u0 com.android.vending/com.google.android.finsky.activities.MainActivity t5}}
        """.trimIndent()

        // Play Store is in recent tasks, but Victoria Launcher has the focus window
        assertFalse(AppRootInspector.isAppInForeground(activitiesTextWithLauncherFocusedAndPlayStoreInRecents, "com.android.vending"))
        assertTrue(AppRootInspector.isAppInForeground(activitiesTextWithLauncherFocusedAndPlayStoreInRecents, "dev.victorialauncher"))
    }

    @Test
    fun `parseProcIo sums rchar and wchar from process IO dumps`() {
        val sampleIo = """
            ---PID:12345---
            ---IO---
            rchar: 10485760
            wchar: 524288
            syscr: 100
            syscw: 50
            ---PID:12346---
            ---IO---
            rchar: 20971520
            wchar: 1048576
        """.trimIndent()

        val (rchar, wchar) = AppRootInspector.parseProcIo(sampleIo)

        assertEquals(31457280L, rchar)
        assertEquals(1572864L, wchar)
    }

    @Test
    fun `parseSocketBytes extracts bytes_received and bytes_acked from ss output`() {
        val sampleSsInfo = """
            tcp ESTAB 0 0 192.168.1.50:48210 142.250.190.46:443 users:(("com.android.vending",pid=12345,fd=42))
                 bbr wscale:7,7 rto:200 bytes_received:5242880 bytes_acked:102400
            tcp ESTAB 0 0 192.168.1.50:48211 142.250.190.46:443 users:(("com.android.vending",pid=12345,fd=43))
                 bbr wscale:7,7 rto:200 bytes_received:1048576 bytes_sent:51200
        """.trimIndent()

        val (rx, tx) = AppRootInspector.parseSocketBytes(sampleSsInfo)

        assertEquals(6291456L, rx)
        assertEquals(153600L, tx)
    }

    @Test
    fun `parseQtaguidStats extracts rx and tx bytes for target uid`() {
        val sampleQtaguid = """
            2 wlan0 0x0 10185 0 15000000 12000 1000000 8000
            3 wlan0 0x123 10185 0 999999 50 999999 50
            4 wlan0 0x0 10099 0 5000000 5000 500000 500
        """.trimIndent()

        val (rx, tx) = AppRootInspector.parseQtaguidStats(sampleQtaguid, 10185)

        assertEquals(15000000L, rx)
        assertEquals(1000000L, tx)
    }

    @Test
    fun `parseNetstatsOutput handles indented bucket lines beneath uid header`() {
        val sampleIndentedNetstats = """
            uid=10185 set=DEFAULT tag=0x0
              st=1727440000 rb=2048 rp=2 tb=1024 tp=2
              st=1727443600 rb=4096 rp=4 tb=2048 tp=4
            uid=10099 set=DEFAULT tag=0x0
              st=1727440000 rb=99999 tb=99999
        """.trimIndent()

        val (rx, tx) = AppRootInspector.parseNetstatsOutput(sampleIndentedNetstats, 10185)

        assertEquals(6144L, rx)
        assertEquals(3072L, tx)
    }

    @Test
    fun `isPackageProcess rejects shell commands and diagnostic tools containing package name`() {
        assertFalse(AppRootInspector.isPackageProcess("grep com.android.vending", "com.android.vending"))
        assertFalse(AppRootInspector.isPackageProcess("su -c dumpsys meminfo com.android.vending", "com.android.vending"))
        assertFalse(AppRootInspector.isPackageProcess("sh -c pidof com.android.vending", "com.android.vending"))
        assertFalse(AppRootInspector.isPackageProcess("toybox ps -A", "com.android.vending"))
        assertFalse(AppRootInspector.isPackageProcess("/system/bin/cat /proc/123/cmdline", "com.android.vending"))
    }

    @Test
    fun `parsePsOutput filters out root processes when inspecting standard app UID`() {
        val samplePs = """
            12345 root 0.0 0.1 grep com.example.app
            12346 u0_a185 2.5 1.2 com.example.app
        """.trimIndent()

        val procs = AppRootInspector.parsePsOutput(
            text = samplePs,
            packageName = "com.example.app",
            targetUid = 10185,
        )

        assertEquals(1, procs.size)
        assertEquals(12346, procs[0].pid)
        assertEquals("u0_a185", procs[0].user)
    }

    @Test
    fun `parseNetstatsOutput filters today and 7-day buckets correctly`() {
        val sampleBuckets = """
            uid=10185 set=DEFAULT tag=0x0
              st=1000 rb=1000 tb=500
              st=5000 rb=2000 tb=1000
              st=9000 rb=3000 tb=1500
        """.trimIndent()

        // 7 days ago starts at 4000, today starts at 8000
        val breakdown = AppRootInspector.parseNetstatsOutput(
            text = sampleBuckets,
            targetUid = 10185,
            todayStartSec = 8000L,
            sevenDaysAgoSec = 4000L,
        )

        assertEquals(6000L, breakdown.rxTotal)
        assertEquals(3000L, breakdown.txTotal)
        assertEquals(3000L, breakdown.rxToday)
        assertEquals(1500L, breakdown.txToday)
        assertEquals(5000L, breakdown.rx7Days)
        assertEquals(2500L, breakdown.tx7Days)
    }

    @Test
    fun `parseBpfUidStats parses mAppUidStatsMap correctly`() {
        val sampleBpf = """
            mAppUidStatsMap:
                uid rxBytes rxPackets txBytes txPackets
                10321 1012 13 4528 56
                10261 3251737943 2576134 79627356 943545
                10470 45848207 31764 1182448 19004
        """.trimIndent()

        val (rxPlay, txPlay) = AppRootInspector.parseBpfUidStats(sampleBpf, 10261)
        assertEquals(3251737943L, rxPlay)
        assertEquals(79627356L, txPlay)

        val (rxLauncher, txLauncher) = AppRootInspector.parseBpfUidStats(sampleBpf, 10470)
        assertEquals(45848207L, rxLauncher)
        assertEquals(1182448L, txLauncher)

        val (rxUnknown, txUnknown) = AppRootInspector.parseBpfUidStats(sampleBpf, 99999)
        assertEquals(0L, rxUnknown)
        assertEquals(0L, txUnknown)
    }

    @Test
    fun `parseBpfUidStats parses mStatsMap fallback correctly`() {
        val sampleStatsMap = """
            mStatsMapA:
                ifaceIndex ifaceName tag_hex uid_int cnt_set rxBytes rxPackets txBytes txPackets
                58 wlan1 0x0 10261 1 2000 20 500 10
                59 rmnet0 0x0 10261 1 3000 30 700 15
        """.trimIndent()

        val (rx, tx) = AppRootInspector.parseBpfUidStats(sampleStatsMap, 10261)
        assertEquals(5000L, rx)
        assertEquals(1200L, tx)
    }
}

