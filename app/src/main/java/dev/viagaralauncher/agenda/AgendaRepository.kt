// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.agenda

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.time.LocalDate

private val Context.agendaDataStore: DataStore<Preferences> by preferencesDataStore(name = "viagara_agenda_prefs")

class AgendaRepository(private val context: Context) {

    private object Keys {
        val ACTIVITIES_JSON = stringPreferencesKey("agenda_activities")
        val COMPLETED_JSON = stringPreferencesKey("agenda_completed_activities")
    }

    private val recurrenceService = AgendaRecurrenceService()
    private val notificationService by lazy { AgendaNotificationService(context) }

    val activities: Flow<List<AgendaActivity>> = context.agendaDataStore.data.map { prefs ->
        val jsonStr = prefs[Keys.ACTIVITIES_JSON] ?: "[]"
        parseActivitiesJson(jsonStr)
    }

    val completedActivities: Flow<List<AgendaActivity>> = context.agendaDataStore.data.map { prefs ->
        val jsonStr = prefs[Keys.COMPLETED_JSON] ?: "[]"
        parseActivitiesJson(jsonStr)
    }

    private fun parseActivitiesJson(jsonStr: String): List<AgendaActivity> {
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<AgendaActivity>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(AgendaActivity.fromJson(obj))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun activitiesToJson(list: List<AgendaActivity>): String {
        val jsonArray = JSONArray()
        list.forEach { jsonArray.put(it.toJson()) }
        return jsonArray.toString()
    }

    suspend fun saveActivity(activity: AgendaActivity) {
        context.agendaDataStore.edit { prefs ->
            val current = parseActivitiesJson(prefs[Keys.ACTIVITIES_JSON] ?: "[]").toMutableList()
            val existingIndex = current.indexOfFirst { it.id == activity.id }
            if (existingIndex != -1) {
                current[existingIndex] = activity
            } else {
                current.add(activity)
            }
            prefs[Keys.ACTIVITIES_JSON] = activitiesToJson(current)
        }

        // Schedule notification if enabled
        if (activity.notificationSettings.isEnabled &&
            activity.notificationSettings.notificationType != AgendaNotificationType.NONE
        ) {
            notificationService.scheduleNotification(activity)
        } else {
            notificationService.cancelActivityNotifications(activity)
        }
    }

    suspend fun saveAllActivities(activitiesToSave: List<AgendaActivity>) {
        context.agendaDataStore.edit { prefs ->
            val current = parseActivitiesJson(prefs[Keys.ACTIVITIES_JSON] ?: "[]").toMutableList()
            activitiesToSave.forEach { activity ->
                val existingIndex = current.indexOfFirst { it.id == activity.id }
                if (existingIndex != -1) {
                    current[existingIndex] = activity
                } else {
                    current.add(activity)
                }
            }
            prefs[Keys.ACTIVITIES_JSON] = activitiesToJson(current)
        }
    }

    suspend fun deleteActivity(activityId: String) {
        val currentList = activities.first()
        val activity = currentList.find { it.id == activityId }

        context.agendaDataStore.edit { prefs ->
            val activeList = parseActivitiesJson(prefs[Keys.ACTIVITIES_JSON] ?: "[]").toMutableList()
            activeList.removeAll { it.id == activityId || (activityId.contains("_") && it.id == activityId.split("_")[0]) }
            prefs[Keys.ACTIVITIES_JSON] = activitiesToJson(activeList)

            val compList = parseActivitiesJson(prefs[Keys.COMPLETED_JSON] ?: "[]").toMutableList()
            compList.removeAll { it.id == activityId }
            prefs[Keys.COMPLETED_JSON] = activitiesToJson(compList)
        }

        if (activity != null) {
            notificationService.cancelActivityNotifications(activity)
        } else {
            notificationService.cancelNotification(activityId)
        }
    }

    suspend fun markAsCompleted(activityId: String) {
        val allActivities = activities.first()
        val isRecurringInstance = activityId.contains("_") && activityId.split("_").size >= 2

        if (isRecurringInstance) {
            val parts = activityId.split("_")
            val baseId = parts[0]
            val instanceDate = parts[1]
            val baseActivity = allActivities.find { it.id == baseId }

            if (baseActivity != null && recurrenceService.isRecurring(baseActivity)) {
                val instanceToComplete = baseActivity.copy(
                    id = activityId,
                    date = instanceDate,
                    isCompleted = true,
                    showInCalendar = false
                )

                // Add to completed
                addCompletedActivity(instanceToComplete)

                // Exclude this instance from base activity
                val updatedExcludedDates = baseActivity.excludedDates + instanceDate
                val updatedBase = baseActivity.copy(excludedDates = updatedExcludedDates)
                saveActivity(updatedBase)

                notificationService.cancelNotification(activityId)
                return
            }
        }

        val activity = allActivities.find { it.id == activityId }
        if (activity != null) {
            if (recurrenceService.isRecurring(activity)) {
                val instanceToComplete = activity.copy(
                    id = "${activity.id}_${activity.date}",
                    isCompleted = true,
                    showInCalendar = false
                )
                addCompletedActivity(instanceToComplete)
                val updatedBase = activity.copy(excludedDates = activity.excludedDates + activity.date)
                saveActivity(updatedBase)
                notificationService.cancelNotification(instanceToComplete.id)
            } else {
                val completed = activity.copy(isCompleted = true, showInCalendar = false)
                addCompletedActivity(completed)
                deleteActiveOnly(activity.id)
                notificationService.cancelActivityNotifications(activity)
            }
        }
    }

    suspend fun markAsIncomplete(activity: AgendaActivity) {
        // Remove from completed
        removeCompletedActivity(activity.id)

        if (activity.id.contains("_")) {
            val parts = activity.id.split("_")
            val baseId = parts[0]
            val instanceDate = parts[1]
            val allActivities = activities.first()
            val baseActivity = allActivities.find { it.id == baseId }
            if (baseActivity != null) {
                val updatedExcludedDates = baseActivity.excludedDates - instanceDate
                saveActivity(baseActivity.copy(excludedDates = updatedExcludedDates))
                return
            }
        }

        // Restore active item
        saveActivity(activity.copy(isCompleted = false, showInCalendar = true))
    }

    private suspend fun addCompletedActivity(activity: AgendaActivity) {
        context.agendaDataStore.edit { prefs ->
            val compList = parseActivitiesJson(prefs[Keys.COMPLETED_JSON] ?: "[]").toMutableList()
            compList.removeAll { it.id == activity.id }
            compList.add(activity)
            prefs[Keys.COMPLETED_JSON] = activitiesToJson(compList)
        }
    }

    private suspend fun removeCompletedActivity(activityId: String) {
        context.agendaDataStore.edit { prefs ->
            val compList = parseActivitiesJson(prefs[Keys.COMPLETED_JSON] ?: "[]").toMutableList()
            compList.removeAll { it.id == activityId }
            prefs[Keys.COMPLETED_JSON] = activitiesToJson(compList)
        }
    }

    private suspend fun deleteActiveOnly(activityId: String) {
        context.agendaDataStore.edit { prefs ->
            val activeList = parseActivitiesJson(prefs[Keys.ACTIVITIES_JSON] ?: "[]").toMutableList()
            activeList.removeAll { it.id == activityId }
            prefs[Keys.ACTIVITIES_JSON] = activitiesToJson(activeList)
        }
    }

    suspend fun performRollover() {
        val today = LocalDate.now()
        val allActivities = activities.first()
        val modifiedList = mutableListOf<AgendaActivity>()

        allActivities.forEach { act ->
            if (act.rollover && !act.isCompleted && act.activityType == AgendaActivityType.TASK) {
                try {
                    val actDate = LocalDate.parse(act.date)
                    if (actDate.isBefore(today)) {
                        val rolledOver = act.copy(
                            date = today.toString(),
                            lastModified = System.currentTimeMillis()
                        )
                        modifiedList.add(rolledOver)
                    }
                } catch (_: Exception) {
                }
            }
        }

        if (modifiedList.isNotEmpty()) {
            saveAllActivities(modifiedList)
            modifiedList.forEach { notificationService.scheduleNotification(it) }
        }
    }
}
