// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.agenda

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaSyncTest {

    @Test
    fun nullBytesStringTrimmedCorrectly() {
        val corruptedContent = "\u0000\u0000\u0000   \u0000\u0000"
        val cleaned = corruptedContent.trim { it <= ' ' || it == '\u0000' }
        assertTrue(cleaned.isEmpty())
    }

    @Test
    fun validJsonTrimmedCorrectlyEvenWithNullPadding() {
        val json = "\u0000{\"backupVersion\":\"1.1\",\"activities\":[]}\u0000"
        val cleaned = json.trim { it <= ' ' || it == '\u0000' }
        assertEquals("{\"backupVersion\":\"1.1\",\"activities\":[]}", cleaned)
        assertTrue(cleaned.startsWith("{"))
    }

    @Test
    fun agendaActivityFromJsonHandlesExcludedDatesSafely() {
        val json = JSONObject().apply {
            put("id", "test_id")
            put("title", "Test Title")
            put("date", "2026-10-29")
            put("excludedDates", JSONArray().apply {
                put("2026-10-30")
                put("") // empty string
            })
            put("excludedInstances", JSONArray().apply {
                put("instance_1")
            })
        }

        val activity = AgendaActivity.fromJson(json)
        assertNotNull(activity)
        assertEquals("test_id", activity.id)
        assertEquals(1, activity.excludedDates.size)
        assertEquals("2026-10-30", activity.excludedDates[0])
        assertEquals(1, activity.excludedInstances.size)
        assertEquals("instance_1", activity.excludedInstances[0])
    }
}
