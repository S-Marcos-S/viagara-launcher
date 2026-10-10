// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.agenda

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

class AgendaRecurrenceService {

    fun generateRecurringInstances(
        baseActivity: AgendaActivity,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<AgendaActivity> {
        val recurrenceRule = baseActivity.recurrenceRule ?: return listOf(baseActivity)

        if (recurrenceRule.isEmpty() || recurrenceRule == "NONE") {
            return listOf(baseActivity)
        }

        val instances = mutableListOf<AgendaActivity>()
        val baseDate = try {
            LocalDate.parse(baseActivity.date)
        } catch (_: Exception) {
            LocalDate.now()
        }

        // Include the base activity itself if it falls within the range and is not excluded
        if (!baseDate.isBefore(startDate) && !baseDate.isAfter(endDate)) {
            if (!baseActivity.excludedDates.contains(baseDate.toString())) {
                instances.add(baseActivity)
            }
        }

        when (recurrenceRule) {
            "HOURLY" -> generateHourlyInstances(baseActivity, baseDate, endDate, instances)
            "DAILY" -> generateDailyInstances(baseActivity, baseDate, endDate, instances)
            "WEEKLY" -> generateWeeklyInstances(baseActivity, baseDate, endDate, instances)
            "MONTHLY" -> generateMonthlyInstances(baseActivity, baseDate, endDate, instances)
            "YEARLY" -> generateYearlyInstances(baseActivity, baseDate, endDate, instances)
            else -> parseCustomRecurrenceRule(baseActivity, baseDate, endDate, instances)
        }

        return instances
    }

    private fun generateHourlyInstances(
        baseActivity: AgendaActivity,
        baseDate: LocalDate,
        endDate: LocalDate,
        instances: MutableList<AgendaActivity>
    ) {
        val interval = parseIntervalFromRule(baseActivity.recurrenceRule ?: "HOURLY")
        val hoursPerDay = 24
        val daysPerInterval = if (interval > 0) (interval.toDouble() / hoursPerDay).toInt().coerceAtLeast(1) else 1

        var currentDate = baseDate.plusDays(daysPerInterval.toLong())
        while (!currentDate.isAfter(endDate)) {
            if (!baseActivity.excludedDates.contains(currentDate.toString())) {
                instances.add(createRecurringInstance(baseActivity, currentDate))
            }
            currentDate = currentDate.plusDays(daysPerInterval.toLong())
        }
    }

    private fun generateDailyInstances(
        baseActivity: AgendaActivity,
        baseDate: LocalDate,
        endDate: LocalDate,
        instances: MutableList<AgendaActivity>
    ) {
        var currentDate = baseDate.plusDays(1)
        while (!currentDate.isAfter(endDate)) {
            if (!baseActivity.excludedDates.contains(currentDate.toString())) {
                instances.add(createRecurringInstance(baseActivity, currentDate))
            }
            currentDate = currentDate.plusDays(1)
        }
    }

    private fun generateWeeklyInstances(
        baseActivity: AgendaActivity,
        baseDate: LocalDate,
        endDate: LocalDate,
        instances: MutableList<AgendaActivity>
    ) {
        var currentDate = baseDate.plusWeeks(1)
        while (!currentDate.isAfter(endDate)) {
            if (!baseActivity.excludedDates.contains(currentDate.toString())) {
                instances.add(createRecurringInstance(baseActivity, currentDate))
            }
            currentDate = currentDate.plusWeeks(1)
        }
    }

    private fun generateMonthlyInstances(
        baseActivity: AgendaActivity,
        baseDate: LocalDate,
        endDate: LocalDate,
        instances: MutableList<AgendaActivity>
    ) {
        var currentDate = baseDate.plusMonths(1)
        while (!currentDate.isAfter(endDate)) {
            val targetDay = minOf(baseDate.dayOfMonth, currentDate.lengthOfMonth())
            val adjustedDate = currentDate.withDayOfMonth(targetDay)

            if (!baseActivity.excludedDates.contains(adjustedDate.toString())) {
                instances.add(createRecurringInstance(baseActivity, adjustedDate))
            }
            currentDate = currentDate.plusMonths(1)
        }
    }

    private fun generateYearlyInstances(
        baseActivity: AgendaActivity,
        baseDate: LocalDate,
        endDate: LocalDate,
        instances: MutableList<AgendaActivity>
    ) {
        var currentDate = baseDate.plusYears(1)
        while (!currentDate.isAfter(endDate)) {
            if (!baseActivity.excludedDates.contains(currentDate.toString())) {
                instances.add(createRecurringInstance(baseActivity, currentDate))
            }
            currentDate = currentDate.plusYears(1)
        }
    }

    private fun parseCustomRecurrenceRule(
        baseActivity: AgendaActivity,
        baseDate: LocalDate,
        endDate: LocalDate,
        instances: MutableList<AgendaActivity>
    ) {
        try {
            val rule = baseActivity.recurrenceRule ?: return
            val parts = rule.split(";")
            val freq = parts.find { it.startsWith("FREQ=") }?.substringAfter("=")
            val interval = parts.find { it.startsWith("INTERVAL=") }?.substringAfter("=")?.toIntOrNull() ?: 1
            val until = parts.find { it.startsWith("UNTIL=") }?.substringAfter("=")?.let { LocalDate.parse(it) }
            val count = parts.find { it.startsWith("COUNT=") }?.substringAfter("=")?.toIntOrNull()
            val byDay = parts.find { it.startsWith("BYDAY=") }?.substringAfter("=")

            val actualEndDate = when {
                count != null -> {
                    when (freq) {
                        "HOURLY" -> baseDate.plusDays((count * interval / 24).toLong())
                        "DAILY" -> baseDate.plusDays(count * interval.toLong())
                        "WEEKLY" -> baseDate.plusWeeks(count * interval.toLong())
                        "MONTHLY" -> baseDate.plusMonths(count * interval.toLong())
                        "YEARLY" -> baseDate.plusYears(count * interval.toLong())
                        else -> endDate
                    }
                }
                until != null && until.isBefore(endDate) -> until
                else -> endDate
            }

            when (freq) {
                "HOURLY" -> generateCustomHourlyInstancesWithCount(baseActivity, baseDate, actualEndDate, interval, count, instances)
                "DAILY" -> generateCustomDailyInstancesWithCount(baseActivity, baseDate, actualEndDate, interval, count, instances)
                "WEEKLY" -> generateCustomWeeklyInstancesWithCount(baseActivity, baseDate, actualEndDate, interval, count, byDay, instances)
                "MONTHLY" -> generateCustomMonthlyInstancesWithCount(baseActivity, baseDate, actualEndDate, interval, count, instances)
                "YEARLY" -> generateCustomYearlyInstancesWithCount(baseActivity, baseDate, actualEndDate, interval, count, instances)
            }
        } catch (_: Exception) {
        }
    }

    private fun generateCustomHourlyInstancesWithCount(
        baseActivity: AgendaActivity,
        baseDate: LocalDate,
        endDate: LocalDate,
        interval: Int,
        count: Int?,
        instances: MutableList<AgendaActivity>
    ) {
        val baseTime = baseActivity.startTime ?: LocalTime.of(0, 0)
        var currentDateTime = baseDate.atTime(baseTime)
        var occurrenceCount = 0
        currentDateTime = currentDateTime.plusHours(interval.toLong())

        while (!currentDateTime.toLocalDate().isAfter(endDate) && (count == null || occurrenceCount < count)) {
            val currentDate = currentDateTime.toLocalDate()
            val currentTime = currentDateTime.toLocalTime()
            occurrenceCount++

            val instanceId = "${baseActivity.id}_${currentDate}_${currentTime.format(DateTimeFormatter.ofPattern("HH:mm"))}"
            if (!baseActivity.excludedInstances.contains(instanceId)) {
                instances.add(createRecurringInstanceWithTime(baseActivity, currentDate, currentTime))
            }
            currentDateTime = currentDateTime.plusHours(interval.toLong())
        }
    }

    private fun generateCustomDailyInstancesWithCount(
        baseActivity: AgendaActivity,
        baseDate: LocalDate,
        endDate: LocalDate,
        interval: Int,
        count: Int?,
        instances: MutableList<AgendaActivity>
    ) {
        var currentDate = baseDate.plusDays(interval.toLong())
        var occurrenceCount = 0
        while (!currentDate.isAfter(endDate) && (count == null || occurrenceCount < count)) {
            occurrenceCount++
            if (!baseActivity.excludedDates.contains(currentDate.toString())) {
                instances.add(createRecurringInstance(baseActivity, currentDate))
            }
            currentDate = currentDate.plusDays(interval.toLong())
        }
    }

    private fun generateCustomWeeklyInstancesWithCount(
        baseActivity: AgendaActivity,
        baseDate: LocalDate,
        endDate: LocalDate,
        interval: Int,
        count: Int?,
        byDay: String?,
        instances: MutableList<AgendaActivity>
    ) {
        if (byDay == null || byDay.isEmpty()) {
            var currentDate = baseDate.plusWeeks(interval.toLong())
            var occurrenceCount = 0
            while (!currentDate.isAfter(endDate) && (count == null || occurrenceCount < count)) {
                occurrenceCount++
                instances.add(createRecurringInstance(baseActivity, currentDate))
                currentDate = currentDate.plusWeeks(interval.toLong())
            }
            return
        }

        val targetDays = byDay.split(",").mapNotNull { day ->
            when (day.trim()) {
                "SU" -> 7
                "MO" -> 1
                "TU" -> 2
                "WE" -> 3
                "TH" -> 4
                "FR" -> 5
                "SA" -> 6
                else -> null
            }
        }

        if (targetDays.isEmpty()) return
        var occurrenceCount = 0
        var currentWeekStart = baseDate

        while (!currentWeekStart.isAfter(endDate) && (count == null || occurrenceCount < count)) {
            targetDays.forEach { targetDay ->
                val targetDate = currentWeekStart.with(
                    TemporalAdjusters.nextOrSame(DayOfWeek.of(targetDay))
                )
                if (!targetDate.isAfter(endDate) && (count == null || occurrenceCount < count)) {
                    occurrenceCount++
                    if (!baseActivity.excludedDates.contains(targetDate.toString())) {
                        instances.add(createRecurringInstance(baseActivity, targetDate))
                    }
                }
            }
            currentWeekStart = currentWeekStart.plusWeeks(interval.toLong())
        }
    }

    private fun generateCustomMonthlyInstancesWithCount(
        baseActivity: AgendaActivity,
        baseDate: LocalDate,
        endDate: LocalDate,
        interval: Int,
        count: Int?,
        instances: MutableList<AgendaActivity>
    ) {
        var currentDate = baseDate.plusMonths(interval.toLong())
        var occurrenceCount = 0
        while (!currentDate.isAfter(endDate) && (count == null || occurrenceCount < count)) {
            occurrenceCount++
            val targetDay = minOf(baseDate.dayOfMonth, currentDate.lengthOfMonth())
            val adjustedDate = currentDate.withDayOfMonth(targetDay)
            instances.add(createRecurringInstance(baseActivity, adjustedDate))
            currentDate = currentDate.plusMonths(interval.toLong())
        }
    }

    private fun generateCustomYearlyInstancesWithCount(
        baseActivity: AgendaActivity,
        baseDate: LocalDate,
        endDate: LocalDate,
        interval: Int,
        count: Int?,
        instances: MutableList<AgendaActivity>
    ) {
        var currentDate = baseDate.plusYears(interval.toLong())
        var occurrenceCount = 0
        while (!currentDate.isAfter(endDate) && (count == null || occurrenceCount < count)) {
            occurrenceCount++
            instances.add(createRecurringInstance(baseActivity, currentDate))
            currentDate = currentDate.plusYears(interval.toLong())
        }
    }

    private fun createRecurringInstanceWithTime(baseActivity: AgendaActivity, date: LocalDate, time: LocalTime): AgendaActivity {
        return baseActivity.copy(
            id = "${baseActivity.id}_${date}_${time.format(DateTimeFormatter.ofPattern("HH:mm"))}",
            date = date.toString(),
            startTime = time
        )
    }

    private fun createRecurringInstance(baseActivity: AgendaActivity, date: LocalDate): AgendaActivity {
        return baseActivity.copy(
            id = "${baseActivity.id}_${date}",
            date = date.toString()
        )
    }

    private fun parseIntervalFromRule(rule: String): Int {
        return try {
            val intervalMatch = Regex("INTERVAL=(\\d+)").find(rule)
            intervalMatch?.groupValues?.get(1)?.toInt() ?: 1
        } catch (_: Exception) {
            1
        }
    }

    fun isRecurring(activity: AgendaActivity): Boolean {
        val rule = activity.recurrenceRule ?: return false
        return rule.isNotEmpty() && rule != "NONE"
    }

    fun getNextOccurrence(baseActivity: AgendaActivity, afterDate: LocalDate): LocalDate? {
        val futureRangeEnd = afterDate.plusYears(1)
        val instances = generateRecurringInstances(baseActivity, afterDate.plusDays(1), futureRangeEnd)
        return instances
            .map { LocalDate.parse(it.date) }
            .filter { it.isAfter(afterDate) }
            .minOrNull()
    }
}
