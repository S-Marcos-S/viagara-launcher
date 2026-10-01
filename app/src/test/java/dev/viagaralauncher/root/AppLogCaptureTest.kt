// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLogCaptureTest {

    @Test
    fun `isLogLineMatchingApp matches logs with matching target UID`() {
        val targetUid = 10185
        val pkg = "com.example.myapp"
        val line = "09-27 13:00:00.123 10185 1234 1250 D SomeTag: Hello from my app"

        val matches = AppLogCaptureService.isLogLineMatchingApp(
            line = line,
            uidString = targetUid.toString(),
            pkgNameLower = pkg,
        )

        assertTrue(matches)
    }

    @Test
    fun `isLogLineMatchingApp matches logs containing package name`() {
        val targetUid = 10185
        val pkg = "com.example.myapp"
        val line = "09-27 13:00:00.456 1000 500 500 I ActivityTaskManager: Force finishing activity com.example.myapp/.MainActivity"

        val matches = AppLogCaptureService.isLogLineMatchingApp(
            line = line,
            uidString = targetUid.toString(),
            pkgNameLower = pkg,
        )

        assertTrue(matches)
    }

    @Test
    fun `isLogLineMatchingApp matches logs with known PID seen from previous lines`() {
        val targetUid = 10185
        val pkg = "com.example.myapp"
        val seenPids = mutableSetOf(1234)

        val lineFromSystemWithAppPid = "09-27 13:00:01.000 1234 1234 E AndroidRuntime: FATAL EXCEPTION: main"

        val matches = AppLogCaptureService.isLogLineMatchingApp(
            line = lineFromSystemWithAppPid,
            uidString = targetUid.toString(),
            pkgNameLower = pkg,
            seenPids = seenPids,
        )

        assertTrue(matches)
    }

    @Test
    fun `isLogLineMatchingApp rejects logs from unrelated packages and other UIDs`() {
        val targetUid = 10185
        val pkg = "com.example.myapp"
        val unrelatedLine = "09-27 13:00:02.123 10099 9999 9999 D OtherApp: Completely unrelated log entry"

        val matches = AppLogCaptureService.isLogLineMatchingApp(
            line = unrelatedLine,
            uidString = targetUid.toString(),
            pkgNameLower = pkg,
        )

        assertFalse(matches)
    }

    @Test
    fun `isCrashLine identifies fatal exceptions and crashes correctly`() {
        val pkg = "com.example.myapp"

        val javaCrashLine = "FATAL EXCEPTION: main"
        assertTrue(AppLogCaptureService.isCrashLine(javaCrashLine, pkg))

        val nativeCrashLine = "Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0 in tid 1234"
        assertTrue(AppLogCaptureService.isCrashLine(nativeCrashLine, pkg))

        val amCrashLine = "am_crash: [0,1234,com.example.myapp,8947234,java.lang.NullPointerException]"
        assertTrue(AppLogCaptureService.isCrashLine(amCrashLine, pkg))

        val forceFinishLine = "ActivityTaskManager: Force finishing activity com.example.myapp/.MainActivity"
        assertTrue(AppLogCaptureService.isCrashLine(forceFinishLine, pkg))

        val normalLine = "09-27 13:00:00.123 10185 1234 1250 D SomeTag: Button clicked"
        assertFalse(AppLogCaptureService.isCrashLine(normalLine, pkg))
    }

    @Test
    fun `extractPidFromLine extracts correct PID token`() {
        val uidLine = "09-27 13:00:00.123 10185 1234 1250 D SomeTag: test"
        assertEquals(1234, AppLogCaptureService.extractPidFromLine(uidLine))

        val noUidLine = "09-27 13:00:00.123 1234 1250 D SomeTag: test"
        assertEquals(1234, AppLogCaptureService.extractPidFromLine(noUidLine))
    }

    @Test
    fun `LogFilterEngine correctly applies level, package, search query, and user filters`() {
        val line1 = dev.viagaralauncher.root.log.LogLine(
            id = 1,
            timestamp = System.currentTimeMillis(),
            uid = "10185",
            pid = "1234",
            tid = "1234",
            packageName = "com.example.myapp",
            level = dev.viagaralauncher.root.log.LogLevel.ERROR,
            tag = "NetworkWorker",
            content = "SocketTimeoutException: failed to connect to api",
            originalContent = "raw line 1",
        )
        val line2 = dev.viagaralauncher.root.log.LogLine(
            id = 2,
            timestamp = System.currentTimeMillis(),
            uid = "1000",
            pid = "500",
            tid = "500",
            packageName = "android",
            level = dev.viagaralauncher.root.log.LogLevel.DEBUG,
            tag = "WindowManager",
            content = "Relayout window complete",
            originalContent = "raw line 2",
        )

        val allLines = listOf(line1, line2)

        // 1. Filter by Level
        val errorOnly = dev.viagaralauncher.root.log.LogFilterEngine.filterAndSearch(
            lines = allLines,
            selectedLevel = dev.viagaralauncher.root.log.LogLevel.ERROR,
        )
        assertEquals(1, errorOnly.size)
        assertEquals("NetworkWorker", errorOnly[0].tag)

        // 2. Filter by Package
        val appOnly = dev.viagaralauncher.root.log.LogFilterEngine.filterAndSearch(
            lines = allLines,
            targetPackage = "com.example.myapp",
        )
        assertEquals(1, appOnly.size)
        assertEquals(1L, appOnly[0].id)

        // 3. Search Query
        val searchResults = dev.viagaralauncher.root.log.LogFilterEngine.filterAndSearch(
            lines = allLines,
            query = "SocketTimeout",
        )
        assertEquals(1, searchResults.size)
        assertEquals(1L, searchResults[0].id)

        // 4. Custom Exclude Filter
        val excludeNetworkFilter = dev.viagaralauncher.root.log.UserLogFilter(
            name = "Exclude Network",
            including = false,
            tag = "NetworkWorker",
            enabled = true,
        )
        val afterExclude = dev.viagaralauncher.root.log.LogFilterEngine.filterAndSearch(
            lines = allLines,
            filters = listOf(excludeNetworkFilter),
        )
        assertEquals(1, afterExclude.size)
        assertEquals("WindowManager", afterExclude[0].tag)
    }

    @Test
    fun `DeviceInfoProvider generates non-empty device telemetry text`() {
        val text = dev.viagaralauncher.root.log.DeviceInfoProvider.getDeviceInfoText()
        assertTrue(text.contains("VIAGARA LAUNCHER - INFORMAÇÕES DO DISPOSITIVO"))
        assertTrue(text.contains("SDK_INT"))
        assertTrue(text.contains("MANUFACTURER"))
    }
}
