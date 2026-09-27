// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.root

import org.junit.Assert.assertNotNull
import org.junit.Test

class AppUninstallManagerTest {

    @Test
    fun `app uninstall manager singleton is accessible`() {
        assertNotNull(AppUninstallManager)
    }
}
