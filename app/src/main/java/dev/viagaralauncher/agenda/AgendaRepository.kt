// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.agenda

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private val Context.agendaDataStore: DataStore<Preferences> by preferencesDataStore(name = "viagara_agenda_prefs")

class AgendaRepository(private val context: Context) {

    private object Keys {
        val ACTIVITIES_JSON = stringPreferencesKey("agenda_activities")
        val COMPLETED_JSON = stringPreferencesKey("agenda_completed_activities")
        val DELETED_IDS_JSON = stringPreferencesKey("agenda_deleted_ids")
        val HIDE_RECURRING = booleanPreferencesKey("agenda_hide_recurring")
    }

    private val recurrenceService = AgendaRecurrenceService()
    private val notificationService by lazy { AgendaNotificationService(context) }

    val hideRecurring: Flow<Boolean> = context.agendaDataStore.data.map { prefs ->
        prefs[Keys.HIDE_RECURRING] ?: false
    }

    suspend fun setHideRecurring(hide: Boolean) {
        context.agendaDataStore.edit { prefs ->
            prefs[Keys.HIDE_RECURRING] = hide
        }
    }

    val activities: Flow<List<AgendaActivity>> = context.agendaDataStore.data.map { prefs ->
        val jsonStr = prefs[Keys.ACTIVITIES_JSON] ?: "[]"
        parseActivitiesJson(jsonStr)
    }

    val completedActivities: Flow<List<AgendaActivity>> = context.agendaDataStore.data.map { prefs ->
        val jsonStr = prefs[Keys.COMPLETED_JSON] ?: "[]"
        parseActivitiesJson(jsonStr)
    }

    val deletedActivityIds: Flow<Set<String>> = context.agendaDataStore.data.map { prefs ->
        val jsonStr = prefs[Keys.DELETED_IDS_JSON] ?: "[]"
        parseDeletedIds(jsonStr)
    }

    private fun parseDeletedIds(jsonStr: String): Set<String> {
        return try {
            val jsonArray = JSONArray(jsonStr)
            val set = mutableSetOf<String>()
            for (i in 0 until jsonArray.length()) {
                val str = jsonArray.optString(i)
                if (str.isNotBlank()) set.add(str)
            }
            set
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun deletedIdsToJson(ids: Set<String>): String {
        val jsonArray = JSONArray()
        ids.forEach { jsonArray.put(it) }
        return jsonArray.toString()
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

    suspend fun exportAgendaJson(): String {
        val active = activities.first()
        val completed = completedActivities.first()
        val deleted = deletedActivityIds.first()
        val root = JSONObject().apply {
            put("backupVersion", "1.1")
            put("appVersion", "VictoriaLauncher")
            put("createdAt", LocalDate.now().toString())
            put("activities", JSONArray().apply { active.forEach { put(it.toJson()) } })
            put("completedActivities", JSONArray().apply { completed.forEach { put(it.toJson()) } })
            put("deletedActivities", JSONArray().apply { deleted.forEach { put(it) } })
        }
        return root.toString()
    }

    private data class ActivitySignature(
        val title: String,
        val date: String,
        val startTime: String?,
        val endTime: String?,
        val isAllDay: Boolean,
        val activityType: String,
        val recurrenceRule: String
    )

    private fun AgendaActivity.toSignature() = ActivitySignature(
        title = title.trim().lowercase(),
        date = date,
        startTime = startTime?.toString(),
        endTime = endTime?.toString(),
        isAllDay = isAllDay,
        activityType = activityType.name,
        recurrenceRule = recurrenceRule?.trim()?.lowercase() ?: ""
    )

    suspend fun importAgendaJson(jsonStr: String): Int {
        val root = JSONObject(jsonStr)
        val activeArr = root.optJSONArray("activities") ?: JSONArray()
        val compArr = root.optJSONArray("completedActivities") ?: JSONArray()

        val parsedActive = mutableListOf<AgendaActivity>()
        for (i in 0 until activeArr.length()) {
            val obj = activeArr.getJSONObject(i)
            runCatching { parseAgendaActivityFromAny(obj) }.getOrNull()?.let { parsedActive.add(it) }
        }

        val parsedComp = mutableListOf<AgendaActivity>()
        for (i in 0 until compArr.length()) {
            val obj = compArr.getJSONObject(i)
            runCatching { parseAgendaActivityFromAny(obj) }.getOrNull()?.let { parsedComp.add(it) }
        }

        val parsedDeletedIds = mutableSetOf<String>()
        val deletedArr = root.optJSONArray("deletedActivities")
        if (deletedArr != null) {
            for (i in 0 until deletedArr.length()) {
                val optObj = deletedArr.optJSONObject(i)
                if (optObj != null) {
                    val origObj = optObj.optJSONObject("originalActivity")
                    val origId = origObj?.optString("id") ?: optObj.optString("id")
                    if (!origId.isNullOrEmpty()) parsedDeletedIds.add(origId)
                } else {
                    val strId = deletedArr.optString(i)
                    if (!strId.isNullOrEmpty()) parsedDeletedIds.add(strId)
                }
            }
        }

        var importedCount = 0

        context.agendaDataStore.edit { prefs ->
            val curActive = parseActivitiesJson(prefs[Keys.ACTIVITIES_JSON] ?: "[]").toMutableList()
            val curComp = parseActivitiesJson(prefs[Keys.COMPLETED_JSON] ?: "[]").toMutableList()
            val curDeleted = parseDeletedIds(prefs[Keys.DELETED_IDS_JSON] ?: "[]").toMutableSet()

            curDeleted.addAll(parsedDeletedIds)

            // Remove any items that are deleted
            curActive.removeAll { it.id in curDeleted || (it.id.contains("_") && it.id.split("_")[0] in curDeleted) }
            curComp.removeAll { it.id in curDeleted || (it.id.contains("_") && it.id.split("_")[0] in curDeleted) }

            val existingActiveSigs = curActive.associateBy { it.toSignature() }.toMutableMap()
            val existingCompSigs = curComp.associateBy { it.toSignature() }.toMutableMap()

            // 1. Process active items
            parsedActive.forEach { item ->
                if (item.id in curDeleted || (item.id.contains("_") && item.id.split("_")[0] in curDeleted)) {
                    return@forEach
                }
                val sig = item.toSignature()
                val idxById = curActive.indexOfFirst { it.id == item.id }

                if (idxById != -1) {
                    val existing = curActive[idxById]
                    if (item.lastModified >= existing.lastModified) {
                        curActive[idxById] = item
                        existingActiveSigs[sig] = item
                    }
                } else if (existingActiveSigs.containsKey(sig)) {
                    val existing = existingActiveSigs[sig]!!
                    val idxBySig = curActive.indexOfFirst { it.id == existing.id }
                    if (idxBySig != -1 && item.lastModified > existing.lastModified) {
                        curActive[idxBySig] = item.copy(id = existing.id)
                        existingActiveSigs[sig] = curActive[idxBySig]
                    }
                } else if (!existingCompSigs.containsKey(sig)) {
                    curActive.add(item)
                    existingActiveSigs[sig] = item
                    importedCount++
                }
            }

            // 2. Process completed items
            parsedComp.forEach { item ->
                if (item.id in curDeleted || (item.id.contains("_") && item.id.split("_")[0] in curDeleted)) {
                    return@forEach
                }
                val sig = item.toSignature()
                val idxById = curComp.indexOfFirst { it.id == item.id }

                if (idxById != -1) {
                    val existing = curComp[idxById]
                    if (item.lastModified >= existing.lastModified) {
                        curComp[idxById] = item
                        existingCompSigs[sig] = item
                    }
                } else if (existingCompSigs.containsKey(sig)) {
                    val existing = existingCompSigs[sig]!!
                    val idxBySig = curComp.indexOfFirst { it.id == existing.id }
                    if (idxBySig != -1 && item.lastModified > existing.lastModified) {
                        curComp[idxBySig] = item.copy(id = existing.id)
                        existingCompSigs[sig] = curComp[idxBySig]
                    }
                } else if (!existingActiveSigs.containsKey(sig)) {
                    curComp.add(item)
                    existingCompSigs[sig] = item
                    importedCount++
                }
            }

            prefs[Keys.ACTIVITIES_JSON] = activitiesToJson(curActive)
            prefs[Keys.COMPLETED_JSON] = activitiesToJson(curComp)
            prefs[Keys.DELETED_IDS_JSON] = deletedIdsToJson(curDeleted)
        }

        // Reschedule notifications for active items
        parsedActive.forEach { act ->
            if (act.notificationSettings.isEnabled &&
                act.notificationSettings.notificationType != AgendaNotificationType.NONE &&
                !act.isCompleted
            ) {
                notificationService.scheduleNotification(act)
            }
        }

        return if (importedCount > 0) importedCount else (parsedActive.size + parsedComp.size)
    }

    private fun parseAgendaActivityFromAny(obj: JSONObject): AgendaActivity {
        val rawColor = obj.optString("categoryColor", "1")
        val normalizedColor = when {
            rawColor in listOf("1", "2", "3", "4") -> rawColor
            rawColor.startsWith("#") || rawColor.startsWith("0x") -> {
                when (rawColor.uppercase()) {
                    "#2196F3", "#3B82F6", "#42A5F5" -> "2"
                    "#FFC107", "#F59E0B", "#FBBF24" -> "3"
                    "#F44336", "#EF4444", "#E53935" -> "4"
                    else -> "1"
                }
            }
            else -> "1"
        }

        val activity = AgendaActivity.fromJson(obj)
        return activity.copy(categoryColor = normalizedColor)
    }

    suspend fun saveActivity(activity: AgendaActivity) {
        val updatedActivity = if (activity.lastModified <= 0L) {
            activity.copy(lastModified = System.currentTimeMillis())
        } else activity

        context.agendaDataStore.edit { prefs ->
            val current = parseActivitiesJson(prefs[Keys.ACTIVITIES_JSON] ?: "[]").toMutableList()
            val sig = updatedActivity.toSignature()
            val existingIndex = current.indexOfFirst { it.id == updatedActivity.id }
            val existingSigIndex = if (existingIndex == -1) current.indexOfFirst { it.toSignature() == sig } else -1

            if (existingIndex != -1) {
                current[existingIndex] = updatedActivity
            } else if (existingSigIndex != -1) {
                current[existingSigIndex] = updatedActivity.copy(id = current[existingSigIndex].id)
            } else {
                current.add(updatedActivity)
            }
            prefs[Keys.ACTIVITIES_JSON] = activitiesToJson(current)
        }

        // Schedule notification if enabled
        if (updatedActivity.notificationSettings.isEnabled &&
            updatedActivity.notificationSettings.notificationType != AgendaNotificationType.NONE
        ) {
            notificationService.scheduleNotification(updatedActivity)
        } else {
            notificationService.cancelActivityNotifications(updatedActivity)
        }

        triggerSyncIfAvailable()
    }

    suspend fun saveAllActivities(activitiesToSave: List<AgendaActivity>) {
        context.agendaDataStore.edit { prefs ->
            val current = parseActivitiesJson(prefs[Keys.ACTIVITIES_JSON] ?: "[]").toMutableList()
            val sigMap = current.associateBy { it.toSignature() }.toMutableMap()

            activitiesToSave.forEach { activity ->
                val updatedActivity = if (activity.lastModified <= 0L) {
                    activity.copy(lastModified = System.currentTimeMillis())
                } else activity

                val sig = updatedActivity.toSignature()
                val existingIndex = current.indexOfFirst { it.id == updatedActivity.id }
                val existingBySig = if (existingIndex == -1) sigMap[sig] else null

                if (existingIndex != -1) {
                    current[existingIndex] = updatedActivity
                    sigMap[sig] = updatedActivity
                } else if (existingBySig != null) {
                    val idx = current.indexOfFirst { it.id == existingBySig.id }
                    if (idx != -1) {
                        current[idx] = updatedActivity.copy(id = existingBySig.id)
                        sigMap[sig] = current[idx]
                    }
                } else {
                    current.add(updatedActivity)
                    sigMap[sig] = updatedActivity
                }
            }
            prefs[Keys.ACTIVITIES_JSON] = activitiesToJson(current)
        }

        triggerSyncIfAvailable()
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

            val deletedIds = parseDeletedIds(prefs[Keys.DELETED_IDS_JSON] ?: "[]").toMutableSet()
            deletedIds.add(activityId)
            if (activityId.contains("_")) {
                deletedIds.add(activityId.split("_")[0])
            }
            prefs[Keys.DELETED_IDS_JSON] = deletedIdsToJson(deletedIds)
        }

        if (activity != null) {
            notificationService.cancelActivityNotifications(activity)
        } else {
            notificationService.cancelNotification(activityId)
        }

        triggerSyncIfAvailable()
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
                triggerSyncIfAvailable()
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
        val sig = activity.toSignature()
        context.agendaDataStore.edit { prefs ->
            val compList = parseActivitiesJson(prefs[Keys.COMPLETED_JSON] ?: "[]").toMutableList()
            compList.removeAll { it.id == activity.id || it.toSignature() == sig }
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

    private suspend fun triggerSyncIfAvailable() {
        runCatching {
            AgendaSyncService(context).writeSyncFile()
        }
    }
}
