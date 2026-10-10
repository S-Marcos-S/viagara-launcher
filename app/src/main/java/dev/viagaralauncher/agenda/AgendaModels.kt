// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.agenda

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class AgendaActivityType {
    TASK,
    EVENT,
    NOTE,
    BIRTHDAY
}

enum class AgendaVisibilityLevel {
    LOW,
    MEDIUM,
    HIGH
}

enum class AgendaNotificationType(val displayName: String, val minutesBefore: Int?) {
    NONE("Sem notificação", null),
    BEFORE_ACTIVITY("Antes da atividade", 0),
    FIVE_MINUTES_BEFORE("5 minutos antes", 5),
    TEN_MINUTES_BEFORE("10 minutos antes", 10),
    FIFTEEN_MINUTES_BEFORE("15 minutos antes", 15),
    THIRTY_MINUTES_BEFORE("30 minutos antes", 30),
    ONE_HOUR_BEFORE("1 hora antes", 60),
    TWO_HOURS_BEFORE("2 horas antes", 120),
    ONE_DAY_BEFORE("1 dia antes", 1440),
    CUSTOM("Personalizado", null)
}

fun AgendaNotificationType.calculateNotificationTime(activityTime: LocalTime, customMinutes: Int? = null): LocalTime {
    return when (this) {
        AgendaNotificationType.NONE -> activityTime
        AgendaNotificationType.BEFORE_ACTIVITY -> activityTime
        AgendaNotificationType.CUSTOM -> {
            val minutes = customMinutes ?: 15
            activityTime.minusMinutes(minutes.toLong())
        }
        else -> {
            val minutes = this.minutesBefore ?: 15
            activityTime.minusMinutes(minutes.toLong())
        }
    }
}

fun AgendaNotificationType.getDescription(): String {
    return when (this) {
        AgendaNotificationType.NONE -> "Sem notificação"
        AgendaNotificationType.BEFORE_ACTIVITY -> "No momento da atividade"
        AgendaNotificationType.CUSTOM -> "Personalizado"
        else -> this.displayName
    }
}

@Immutable
data class AgendaNotificationSettings(
    val isEnabled: Boolean = false,
    val notificationTime: LocalTime? = null,
    val notificationType: AgendaNotificationType = AgendaNotificationType.BEFORE_ACTIVITY,
    val customMinutesBefore: Int? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("isEnabled", isEnabled)
        put("notificationTime", notificationTime?.format(DateTimeFormatter.ISO_LOCAL_TIME) ?: "")
        put("notificationType", notificationType.name)
        if (customMinutesBefore != null) {
            put("customMinutesBefore", customMinutesBefore)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): AgendaNotificationSettings {
            val isEnabled = json.optBoolean("isEnabled", false)
            val timeStr = json.optString("notificationTime", "")
            val notificationTime = if (timeStr.isNotBlank()) {
                try { LocalTime.parse(timeStr) } catch (_: Exception) { null }
            } else null
            val typeStr = json.optString("notificationType", AgendaNotificationType.BEFORE_ACTIVITY.name)
            val notificationType = try {
                AgendaNotificationType.valueOf(typeStr)
            } catch (_: Exception) {
                AgendaNotificationType.BEFORE_ACTIVITY
            }
            val customMinutes = if (json.has("customMinutesBefore")) json.optInt("customMinutesBefore") else null

            return AgendaNotificationSettings(
                isEnabled = isEnabled,
                notificationTime = notificationTime,
                notificationType = notificationType,
                customMinutesBefore = customMinutes
            )
        }
    }
}

@Immutable
data class AgendaActivity(
    val id: String,
    val title: String,
    val description: String? = null,
    val date: String, // "yyyy-MM-dd"
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val isAllDay: Boolean = true,
    val location: String? = null,
    val categoryColor: String = "1", // "1", "2", "3", "4"
    val activityType: AgendaActivityType = AgendaActivityType.TASK,
    val recurrenceRule: String? = null,
    val notificationSettings: AgendaNotificationSettings = AgendaNotificationSettings(),
    val isCompleted: Boolean = false,
    val visibility: AgendaVisibilityLevel = AgendaVisibilityLevel.LOW,
    val showInCalendar: Boolean = true,
    val excludedDates: List<String> = emptyList(),
    val excludedInstances: List<String> = emptyList(),
    val rollover: Boolean = false,
    val lastModified: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("description", description ?: "")
        put("date", date)
        put("startTime", startTime?.format(DateTimeFormatter.ISO_LOCAL_TIME) ?: "")
        put("endTime", endTime?.format(DateTimeFormatter.ISO_LOCAL_TIME) ?: "")
        put("isAllDay", isAllDay)
        put("location", location ?: "")
        put("categoryColor", categoryColor)
        put("activityType", activityType.name)
        put("recurrenceRule", recurrenceRule ?: "")
        put("notificationSettings", notificationSettings.toJson())
        put("isCompleted", isCompleted)
        put("visibility", visibility.name)
        put("showInCalendar", showInCalendar)
        put("excludedDates", JSONArray(excludedDates))
        put("excludedInstances", JSONArray(excludedInstances))
        put("rollover", rollover)
        put("lastModified", lastModified)
    }

    companion object {
        fun fromJson(json: JSONObject): AgendaActivity {
            val id = json.optString("id", "")
            val title = json.optString("title", "")
            val desc = json.optString("description", "").takeIf { it.isNotBlank() }
            val date = json.optString("date", LocalDate.now().toString())
            val startStr = json.optString("startTime", "")
            val startTime = if (startStr.isNotBlank()) {
                try { LocalTime.parse(startStr) } catch (_: Exception) { null }
            } else null
            val endStr = json.optString("endTime", "")
            val endTime = if (endStr.isNotBlank()) {
                try { LocalTime.parse(endStr) } catch (_: Exception) { null }
            } else null
            val isAllDay = json.optBoolean("isAllDay", startTime == null)
            val loc = json.optString("location", "").takeIf { it.isNotBlank() }
            val color = json.optString("categoryColor", "1")
            val actTypeStr = json.optString("activityType", AgendaActivityType.TASK.name)
            val activityType = try {
                AgendaActivityType.valueOf(actTypeStr)
            } catch (_: Exception) {
                AgendaActivityType.TASK
            }
            val recRule = json.optString("recurrenceRule", "").takeIf { it.isNotBlank() }
            val notifObj = json.optJSONObject("notificationSettings")
            val notificationSettings = if (notifObj != null) {
                AgendaNotificationSettings.fromJson(notifObj)
            } else {
                AgendaNotificationSettings()
            }
            val isCompleted = json.optBoolean("isCompleted", false)
            val visStr = json.optString("visibility", AgendaVisibilityLevel.LOW.name)
            val visibility = try {
                AgendaVisibilityLevel.valueOf(visStr)
            } catch (_: Exception) {
                AgendaVisibilityLevel.LOW
            }
            val showInCal = json.optBoolean("showInCalendar", true)

            val excludedDates = mutableListOf<String>()
            val exDatesArr = json.optJSONArray("excludedDates")
            if (exDatesArr != null) {
                for (i in 0 until exDatesArr.length()) {
                    excludedDates.add(exDatesArr.getString(i))
                }
            }

            val excludedInstances = mutableListOf<String>()
            val exInstArr = json.optJSONArray("excludedInstances")
            if (exInstArr != null) {
                for (i in 0 until exInstArr.length()) {
                    excludedInstances.add(exInstArr.getString(i))
                }
            }

            val rollover = json.optBoolean("rollover", false)
            val lastModified = json.optLong("lastModified", 0L)

            return AgendaActivity(
                id = id,
                title = title,
                description = desc,
                date = date,
                startTime = startTime,
                endTime = endTime,
                isAllDay = isAllDay,
                location = loc,
                categoryColor = color,
                activityType = activityType,
                recurrenceRule = recRule,
                notificationSettings = notificationSettings,
                isCompleted = isCompleted,
                visibility = visibility,
                showInCalendar = showInCal,
                excludedDates = excludedDates,
                excludedInstances = excludedInstances,
                rollover = rollover,
                lastModified = lastModified
            )
        }
    }
}
