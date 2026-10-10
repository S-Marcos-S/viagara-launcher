// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.agenda

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class AgendaNotificationReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AgendaReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                when (intent.action) {
                    AgendaNotificationService.ACTION_VIEW_ACTIVITY -> {
                        handleViewActivity(context, intent)
                    }
                    AgendaNotificationService.ACTION_SNOOZE -> {
                        handleSnooze(context, intent)
                    }
                    AgendaNotificationService.ACTION_DISMISS -> {
                        handleDismiss(context, intent)
                    }
                    Intent.ACTION_BOOT_COMPLETED -> {
                        handleBootCompleted(context)
                    }
                    Intent.ACTION_DATE_CHANGED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED -> {
                        val repository = AgendaRepository(context)
                        repository.performRollover()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling broadcast: ${intent.action}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleViewActivity(context: Context, intent: Intent) {
        val activityId = intent.getStringExtra(AgendaNotificationService.EXTRA_ACTIVITY_ID) ?: return
        val activityDate = intent.getStringExtra(AgendaNotificationService.EXTRA_ACTIVITY_DATE)

        val repository = AgendaRepository(context)
        val activities = repository.activities.first()

        val baseId = if (activityId.contains("_")) activityId.split("_")[0] else activityId
        val baseActivity = activities.find { it.id == baseId }

        if (baseActivity == null || baseActivity.isCompleted) {
            val notificationService = AgendaNotificationService(context)
            notificationService.cancelNotification(activityId)
            return
        }

        if (!baseActivity.notificationSettings.isEnabled ||
            baseActivity.notificationSettings.notificationType == AgendaNotificationType.NONE
        ) {
            val notificationService = AgendaNotificationService(context)
            notificationService.cancelNotification(activityId)
            return
        }

        val recurrenceService = AgendaRecurrenceService()
        val isRecurring = recurrenceService.isRecurring(baseActivity)
        val alarmDate = if (activityId.contains("_")) {
            activityId.split("_").getOrNull(1) ?: activityDate ?: ""
        } else {
            activityDate ?: baseActivity.date
        }

        if (!isRecurring) {
            if (alarmDate.isNotBlank() && alarmDate != baseActivity.date) {
                val notificationService = AgendaNotificationService(context)
                notificationService.cancelNotification(activityId)
                return
            }
        } else {
            if (alarmDate.isNotBlank() && baseActivity.excludedDates.contains(alarmDate)) {
                val notificationService = AgendaNotificationService(context)
                notificationService.cancelNotification(activityId)
                return
            }
        }

        val realActivity = if (isRecurring) {
            baseActivity.copy(
                id = activityId,
                date = alarmDate
            )
        } else {
            baseActivity
        }

        withContext(Dispatchers.Main) {
            val notificationService = AgendaNotificationService(context)
            notificationService.showNotification(realActivity)
        }

        if (isRecurring) {
            val currentLocalDate = try {
                LocalDate.parse(alarmDate)
            } catch (_: Exception) {
                LocalDate.now()
            }
            val nextOccurrence = recurrenceService.getNextOccurrence(baseActivity, currentLocalDate)
            if (nextOccurrence != null) {
                val nextActivity = baseActivity.copy(
                    id = "${baseActivity.id}_$nextOccurrence",
                    date = nextOccurrence.toString()
                )
                val notificationService = AgendaNotificationService(context)
                notificationService.scheduleNotification(nextActivity)
            }
        }
    }

    private suspend fun handleSnooze(context: Context, intent: Intent) {
        val activityId = intent.getStringExtra(AgendaNotificationService.EXTRA_ACTIVITY_ID) ?: return
        val snoozeMinutes = intent.getIntExtra("snooze_minutes", 5)

        val notificationService = AgendaNotificationService(context)
        notificationService.cancelNotification(activityId)

        val repository = AgendaRepository(context)
        val activities = repository.activities.first()
        val baseId = if (activityId.contains("_")) activityId.split("_")[0] else activityId
        val activity = activities.find { it.id == baseId } ?: return

        val snoozedTime = java.time.LocalTime.now().plusMinutes(snoozeMinutes.toLong())
        val snoozedActivity = activity.copy(
            startTime = snoozedTime,
            isAllDay = false,
            notificationSettings = activity.notificationSettings.copy(
                isEnabled = true,
                notificationType = AgendaNotificationType.BEFORE_ACTIVITY
            )
        )
        notificationService.scheduleNotification(snoozedActivity)
    }

    private suspend fun handleDismiss(context: Context, intent: Intent) {
        val activityId = intent.getStringExtra(AgendaNotificationService.EXTRA_ACTIVITY_ID) ?: return
        val notificationService = AgendaNotificationService(context)
        notificationService.cancelNotification(activityId)

        val repository = AgendaRepository(context)
        repository.markAsCompleted(activityId)
    }

    private suspend fun handleBootCompleted(context: Context) {
        val repository = AgendaRepository(context)
        val notificationService = AgendaNotificationService(context)
        val activities = repository.activities.first()

        activities.forEach { act ->
            if (act.notificationSettings.isEnabled &&
                act.notificationSettings.notificationType != AgendaNotificationType.NONE &&
                !act.isCompleted
            ) {
                notificationService.scheduleNotification(act)
            }
        }

        repository.performRollover()
    }
}
