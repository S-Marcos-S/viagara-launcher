// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.root

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
}
