// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DailyWallpaperTest {

    @Test
    fun `isDifferentDay returns true for uninitialized timestamp`() {
        val now = System.currentTimeMillis()
        assertTrue(DailyWallpaperManager.isDifferentDay(0L, now))
        assertTrue(DailyWallpaperManager.isDifferentDay(-1L, now))
    }

    @Test
    fun `isDifferentDay returns false for same timestamp`() {
        val now = System.currentTimeMillis()
        assertFalse(DailyWallpaperManager.isDifferentDay(now, now))
    }

    @Test
    fun `isDifferentDay returns false when now is earlier than last (backward clock drift)`() {
        val calLater = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.OCTOBER)
            set(Calendar.DAY_OF_MONTH, 17)
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val calEarlier = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.OCTOBER)
            set(Calendar.DAY_OF_MONTH, 17)
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertFalse(DailyWallpaperManager.isDifferentDay(calLater.timeInMillis, calEarlier.timeInMillis))
    }

    @Test
    fun `isDifferentDay returns false for timestamps on same calendar day`() {
        val calMorning = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.OCTOBER)
            set(Calendar.DAY_OF_MONTH, 17)
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val calEvening = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.OCTOBER)
            set(Calendar.DAY_OF_MONTH, 17)
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertFalse(DailyWallpaperManager.isDifferentDay(calMorning.timeInMillis, calEvening.timeInMillis))
    }

    @Test
    fun `isDifferentDay returns true across midnight transition`() {
        val calNight = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.OCTOBER)
            set(Calendar.DAY_OF_MONTH, 17)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }
        val calNextDay = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.OCTOBER)
            set(Calendar.DAY_OF_MONTH, 18)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
        }
        assertTrue(DailyWallpaperManager.isDifferentDay(calNight.timeInMillis, calNextDay.timeInMillis))
    }

    @Test
    fun `isDifferentDay returns true across month transition`() {
        val calEndOfMonth = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.OCTOBER)
            set(Calendar.DAY_OF_MONTH, 31)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
        }
        val calNextMonth = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.NOVEMBER)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 5)
        }
        assertTrue(DailyWallpaperManager.isDifferentDay(calEndOfMonth.timeInMillis, calNextMonth.timeInMillis))
    }

    @Test
    fun `isDifferentDay returns true across leap year February transition`() {
        val calFeb28 = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2024)
            set(Calendar.MONTH, Calendar.FEBRUARY)
            set(Calendar.DAY_OF_MONTH, 28)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
        }
        val calFeb29 = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2024)
            set(Calendar.MONTH, Calendar.FEBRUARY)
            set(Calendar.DAY_OF_MONTH, 29)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 10)
        }
        assertTrue(DailyWallpaperManager.isDifferentDay(calFeb28.timeInMillis, calFeb29.timeInMillis))
    }

    @Test
    fun `isDifferentDay returns true across year boundary`() {
        val calLastDay = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2025)
            set(Calendar.MONTH, Calendar.DECEMBER)
            set(Calendar.DAY_OF_MONTH, 31)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
        }
        val calNewYear = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 1)
        }
        assertTrue(DailyWallpaperManager.isDifferentDay(calLastDay.timeInMillis, calNewYear.timeInMillis))
    }

    @Test
    fun `cyclical catalog navigation wraps around correctly`() {
        val items = listOf("wp1", "wp2", "wp3")

        fun nextItem(currentId: String, isDislike: Boolean = false): String {
            val idx = items.indexOf(currentId)
            val nextIdx = when {
                idx >= 0 -> (idx + 1) % items.size
                isDislike && items.size > 1 -> 1
                else -> 0
            }
            return items[nextIdx]
        }

        assertEquals("wp1", nextItem("unknown_id", isDislike = false))
        assertEquals("wp2", nextItem("unknown_id", isDislike = true))
        assertEquals("wp2", nextItem("wp1"))
        assertEquals("wp3", nextItem("wp2"))
        assertEquals("wp1", nextItem("wp3"))
    }
}
