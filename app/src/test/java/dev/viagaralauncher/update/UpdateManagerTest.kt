// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.update

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun testExtractVersion() {
        assertEquals("0.60.99", UpdateManager.extractVersion("v0.60.99", ""))
        assertEquals("0.60.99", UpdateManager.extractVersion("v0.60.99-beta", ""))
        assertEquals("0.60.99", UpdateManager.extractVersion("", "Viagara Launcher v0.60.99 (Beta)"))
        assertEquals("1.0.0", UpdateManager.extractVersion("1.0.0", "Release 1.0.0"))
    }

    @Test
    fun testCompareVersions() {
        assertTrue(UpdateManager.compareVersions("0.60.99", "0.60.98") > 0)
        assertTrue(UpdateManager.compareVersions("0.60.98", "0.60.99") < 0)
        assertEquals(0, UpdateManager.compareVersions("0.60.99", "0.60.99"))
        assertEquals(0, UpdateManager.compareVersions("v0.60.99-beta", "0.60.99"))
        assertEquals(0, UpdateManager.compareVersions("0.60.99 (Beta)", "0.60.99"))
        assertTrue(UpdateManager.compareVersions("0.60.99.1", "0.60.99") > 0)
    }

    @Test
    fun testIsVersionGreater() {
        assertTrue(UpdateManager.isVersionGreater("0.60.99", "0.60.98"))
        assertTrue(UpdateManager.isVersionGreater("0.60.99-beta", "0.60.98"))
        assertTrue(UpdateManager.isVersionGreater("v0.60.99", "0.60.98"))
        assertTrue(UpdateManager.isVersionGreater("0.61.0", "0.60.98"))
        assertTrue(UpdateManager.isVersionGreater("1.0.0", "0.60.98"))

        assertFalse(UpdateManager.isVersionGreater("0.60.98", "0.60.98"))
        assertFalse(UpdateManager.isVersionGreater("0.60.97", "0.60.98"))
        assertFalse(UpdateManager.isVersionGreater("0.60.98-beta", "0.60.98"))
        assertFalse(UpdateManager.isVersionGreater("0.59.99", "0.60.98"))
    }

    @Test
    fun testIsVersionEqual() {
        assertTrue(UpdateManager.isVersionEqual("0.60.99", "0.60.99"))
        assertTrue(UpdateManager.isVersionEqual("v0.60.99", "0.60.99"))
        assertTrue(UpdateManager.isVersionEqual("0.60.99-beta", "0.60.99"))
        assertTrue(UpdateManager.isVersionEqual("0.60.99 (Beta)", "0.60.99"))

        assertFalse(UpdateManager.isVersionEqual("0.60.99", "0.60.98"))
        assertFalse(UpdateManager.isVersionEqual("0.61.0", "0.60.0"))
    }

    private fun createReleaseJson(
        tag: String,
        name: String,
        isPrerelease: Boolean,
        commitSha: String? = null,
    ): JSONObject {
        return JSONObject().apply {
            put("tag_name", tag)
            put("name", name)
            put("prerelease", isPrerelease)
            if (commitSha != null) {
                put("body", "Built automatically from commit `$commitSha`.")
            } else {
                put("body", "Release notes")
            }
        }
    }

    @Test
    fun testFindBestUpdate_nonBetaTester_ignoresBetaReleases() {
        val betaRel = createReleaseJson("v0.60.99-beta", "Viagara Launcher v0.60.99 (Beta)", isPrerelease = true)
        val releases = listOf(betaRel)

        val result = UpdateManager.findBestUpdate(
            releasesList = releases,
            currentVersion = "0.60.98",
            currentSha = "a111111111111111",
            currentIsBeta = false,
            isBetaTester = false,
        )

        assertNull(result)
    }

    @Test
    fun testFindBestUpdate_nonBetaTester_picksStableRelease() {
        val betaRel = createReleaseJson("v0.60.99-beta", "Viagara Launcher v0.60.99 (Beta)", isPrerelease = true)
        val stableRel = createReleaseJson("v0.60.99", "Viagara Launcher v0.60.99", isPrerelease = false)
        val releases = listOf(betaRel, stableRel)

        val result = UpdateManager.findBestUpdate(
            releasesList = releases,
            currentVersion = "0.60.98",
            currentSha = "a111111111111111",
            currentIsBeta = false,
            isBetaTester = false,
        )

        assertNotNull(result)
        assertEquals("0.60.99", result?.second)
        assertEquals("v0.60.99", result?.first?.getString("tag_name"))
    }

    @Test
    fun testFindBestUpdate_betaTester_picksBetaWhenNewer() {
        val betaRel = createReleaseJson("v0.60.99-beta", "Viagara Launcher v0.60.99 (Beta)", isPrerelease = true)
        val releases = listOf(betaRel)

        val result = UpdateManager.findBestUpdate(
            releasesList = releases,
            currentVersion = "0.60.98",
            currentSha = "a111111111111111",
            currentIsBeta = false,
            isBetaTester = true,
        )

        assertNotNull(result)
        assertEquals("0.60.99 (Beta)", result?.second)
        assertEquals("v0.60.99-beta", result?.first?.getString("tag_name"))
    }

    @Test
    fun testFindBestUpdate_betaTester_prefersStableOverBetaWhenVersionIsEqual() {
        // Even if beta release is first in the list, stable of same version must win
        val betaRel = createReleaseJson("v0.60.99-beta", "Viagara Launcher v0.60.99 (Beta)", isPrerelease = true)
        val stableRel = createReleaseJson("v0.60.99", "Viagara Launcher v0.60.99", isPrerelease = false)

        val result1 = UpdateManager.findBestUpdate(
            releasesList = listOf(betaRel, stableRel),
            currentVersion = "0.60.98",
            currentSha = "a111111111111111",
            currentIsBeta = false,
            isBetaTester = true,
        )
        assertNotNull(result1)
        assertEquals("0.60.99", result1?.second)
        assertEquals("v0.60.99", result1?.first?.getString("tag_name"))

        val result2 = UpdateManager.findBestUpdate(
            releasesList = listOf(stableRel, betaRel),
            currentVersion = "0.60.98",
            currentSha = "a111111111111111",
            currentIsBeta = false,
            isBetaTester = true,
        )
        assertNotNull(result2)
        assertEquals("0.60.99", result2?.second)
        assertEquals("v0.60.99", result2?.first?.getString("tag_name"))
    }

    @Test
    fun testFindBestUpdate_picksHighestVersionRegardlessOfOrder() {
        val rel98 = createReleaseJson("v0.60.98", "Viagara Launcher v0.60.98", isPrerelease = false)
        val rel99 = createReleaseJson("v0.60.99", "Viagara Launcher v0.60.99", isPrerelease = false)

        // Older version is first in list
        val result = UpdateManager.findBestUpdate(
            releasesList = listOf(rel98, rel99),
            currentVersion = "0.60.97",
            currentSha = "a000000000000000",
            currentIsBeta = false,
            isBetaTester = false,
        )

        assertNotNull(result)
        assertEquals("0.60.99", result?.second)
        assertEquals("v0.60.99", result?.first?.getString("tag_name"))
    }

    @Test
    fun testFindBestUpdate_betaBuildPromotedToStable() {
        val stableRel = createReleaseJson("v0.60.99", "Viagara Launcher v0.60.99", isPrerelease = false)
        val releases = listOf(stableRel)

        // Current build is beta of 0.60.99, remote is stable 0.60.99
        val result = UpdateManager.findBestUpdate(
            releasesList = releases,
            currentVersion = "0.60.99",
            currentSha = "a111111111111111",
            currentIsBeta = true,
            isBetaTester = true,
        )

        assertNotNull(result)
        assertEquals("0.60.99", result?.second)
        assertEquals("v0.60.99", result?.first?.getString("tag_name"))
    }

    @Test
    fun testFindBestUpdate_betaBuildUpdatesToNewerBetaCommit() {
        val betaRelNew = createReleaseJson("v0.60.99-beta", "Viagara Launcher v0.60.99 (Beta)", isPrerelease = true, commitSha = "b222222222222222")

        val result = UpdateManager.findBestUpdate(
            releasesList = listOf(betaRelNew),
            currentVersion = "0.60.99",
            currentSha = "a111111111111111",
            currentIsBeta = true,
            isBetaTester = true,
        )

        assertNotNull(result)
        assertEquals("0.60.99 (Beta)", result?.second)

        // But if commit sha matches, no update
        val resultSameCommit = UpdateManager.findBestUpdate(
            releasesList = listOf(betaRelNew),
            currentVersion = "0.60.99",
            currentSha = "b222222222222222",
            currentIsBeta = true,
            isBetaTester = true,
        )
        assertNull(resultSameCommit)
    }
}
