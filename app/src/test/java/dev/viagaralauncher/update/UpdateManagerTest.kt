// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun testExtractVersion() {
        assertEquals("0.60.98", UpdateManager.extractVersion("v0.60.98", ""))
        assertEquals("0.60.98", UpdateManager.extractVersion("v0.60.98-beta", ""))
        assertEquals("0.60.98", UpdateManager.extractVersion("", "Viagara Launcher v0.60.98 (Beta)"))
        assertEquals("1.0.0", UpdateManager.extractVersion("1.0.0", "Release 1.0.0"))
    }

    @Test
    fun testIsVersionGreater() {
        assertTrue(UpdateManager.isVersionGreater("0.60.98", "0.60.97"))
        assertTrue(UpdateManager.isVersionGreater("0.60.98-beta", "0.60.97"))
        assertTrue(UpdateManager.isVersionGreater("v0.60.98", "0.60.97"))
        assertTrue(UpdateManager.isVersionGreater("0.61.0", "0.60.97"))
        assertTrue(UpdateManager.isVersionGreater("1.0.0", "0.60.97"))

        assertFalse(UpdateManager.isVersionGreater("0.60.97", "0.60.97"))
        assertFalse(UpdateManager.isVersionGreater("0.60.96", "0.60.97"))
        assertFalse(UpdateManager.isVersionGreater("0.60.97-beta", "0.60.97"))
        assertFalse(UpdateManager.isVersionGreater("0.59.99", "0.60.97"))
    }

    @Test
    fun testIsVersionEqual() {
        assertTrue(UpdateManager.isVersionEqual("0.60.98", "0.60.98"))
        assertTrue(UpdateManager.isVersionEqual("v0.60.98", "0.60.98"))
        assertTrue(UpdateManager.isVersionEqual("0.60.98-beta", "0.60.98"))
        assertTrue(UpdateManager.isVersionEqual("0.60.98 (Beta)", "0.60.98"))

        assertFalse(UpdateManager.isVersionEqual("0.60.98", "0.60.97"))
        assertFalse(UpdateManager.isVersionEqual("0.61.0", "0.60.0"))
    }
}
