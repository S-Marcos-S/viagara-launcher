// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.agenda

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.viagaralauncher.MainActivity
import dev.viagaralauncher.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class AgendaNotificationService(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = context.getSharedPreferences("agenda_notification_tracking", Context.MODE_PRIVATE)
    private val lock = Any()

    companion object {
        private const val TAG = "AgendaNotification"
        const val CHANNEL_ID = "agenda_reminders_channel"
        const val CHANNEL_NAME = "Lembretes e Agendamentos"
        const val CHANNEL_DESCRIPTION = "Notificações de compromissos e tarefas da agenda"

        const val ACTION_VIEW_ACTIVITY = "dev.viagaralauncher.agenda.ACTION_VIEW_ACTIVITY"
        const val ACTION_SNOOZE = "dev.viagaralauncher.agenda.ACTION_SNOOZE"
        const val ACTION_DISMISS = "dev.viagaralauncher.agenda.ACTION_DISMISS"

        const val EXTRA_ACTIVITY_ID = "activity_id"
        const val EXTRA_ACTIVITY_TITLE = "activity_title"
        const val EXTRA_ACTIVITY_DATE = "activity_date"
        const val EXTRA_ACTIVITY_TIME = "activity_time"

        private const val KEY_SENT = "notif_sent_"
        private const val DEDUP_WINDOW_MS = 60000L
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun scheduleNotification(activity: AgendaActivity) {
        if (!activity.notificationSettings.isEnabled ||
            activity.notificationSettings.notificationType == AgendaNotificationType.NONE ||
            activity.isCompleted
        ) {
            cancelActivityNotifications(activity)
            return
        }

        val notificationTime = calculateNotificationTime(activity)
        val triggerTime = getTriggerTime(activity.date, notificationTime)

        if (triggerTime <= System.currentTimeMillis() &&
            activity.recurrenceRule != null &&
            activity.recurrenceRule != "NONE" &&
            activity.recurrenceRule.isNotEmpty() &&
            !activity.id.contains("_")
        ) {
            cancelActivityNotifications(activity)
            val recurrenceService = AgendaRecurrenceService()
            val baseDate = try { LocalDate.parse(activity.date) } catch (_: Exception) { LocalDate.now() }
            val nextTwoYears = LocalDate.now().plusYears(2)
            val instances = recurrenceService.generateRecurringInstances(activity, baseDate, nextTwoYears)

            val nextFutureInstance = instances
                .filter { instance ->
                    val instNotifTime = calculateNotificationTime(instance)
                    getTriggerTime(instance.date, instNotifTime) > System.currentTimeMillis()
                }
                .minByOrNull { instance ->
                    val instNotifTime = calculateNotificationTime(instance)
                    getTriggerTime(instance.date, instNotifTime)
                }

            if (nextFutureInstance != null) {
                scheduleNotification(nextFutureInstance)
                return
            }
        }

        cancelActivityNotifications(activity)

        val activityIdForNotification = if (activity.id.contains("_")) {
            activity.id
        } else {
            "${activity.id}_${activity.date}"
        }

        val intent = Intent(context, AgendaNotificationReceiver::class.java).apply {
            action = ACTION_VIEW_ACTIVITY
            putExtra(EXTRA_ACTIVITY_ID, activityIdForNotification)
            putExtra(EXTRA_ACTIVITY_TITLE, activity.title)
            putExtra(EXTRA_ACTIVITY_DATE, activity.date)
            putExtra(EXTRA_ACTIVITY_TIME, activity.startTime?.toString() ?: "")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            activityIdForNotification.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (triggerTime <= System.currentTimeMillis()) {
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling alarm for ${activity.title}", e)
        }
    }

    fun cancelNotification(activityId: String) {
        val intent = Intent(context, AgendaNotificationReceiver::class.java).apply {
            action = ACTION_VIEW_ACTIVITY
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            activityId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        if (activityId.contains("_")) {
            val baseId = activityId.split("_")[0]
            val baseIntent = Intent(context, AgendaNotificationReceiver::class.java).apply {
                action = ACTION_VIEW_ACTIVITY
            }
            val basePending = PendingIntent.getBroadcast(
                context,
                baseId.hashCode(),
                baseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(basePending)
        }

        notificationManager.cancel(activityId.hashCode())
    }

    fun cancelActivityNotifications(activity: AgendaActivity) {
        cancelNotification(activity.id)
        if (!activity.id.contains("_")) {
            cancelNotification("${activity.id}_${activity.date}")
            if (activity.startTime != null) {
                cancelNotification("${activity.id}_${activity.date}_${activity.startTime}")
            }
        }
    }

    fun showNotification(activity: AgendaActivity) {
        val notifIdStr = if (activity.id.contains("_")) activity.id else activity.id

        synchronized(lock) {
            val key = KEY_SENT + notifIdStr
            val lastSent = prefs.getLong(key, 0L)
            val now = System.currentTimeMillis()
            if (now - lastSent < DEDUP_WINDOW_MS) {
                return
            }
            prefs.edit().putLong(key, now).apply()
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_agenda", true)
            putExtra("selected_activity_id", activity.id)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            activity.id.hashCode(),
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snooze5Intent = createSnoozePendingIntent(activity, 5)
        val snooze30Intent = createSnoozePendingIntent(activity, 30)
        val dismissIntent = createDismissPendingIntent(activity)

        val timeText = if (activity.startTime != null) {
            " às " + String.format(java.util.Locale.getDefault(), "%02d:%02d", activity.startTime.hour, activity.startTime.minute)
        } else ""

        val descText = activity.description?.takeIf { it.isNotBlank() } ?: "Lembrete programado para hoje$timeText"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("🔔 " + activity.title)
            .setContentText(descText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(descText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Concluir",
                dismissIntent
            )

        if (snooze5Intent != null) {
            builder.addAction(android.R.drawable.ic_menu_revert, "Adiar 5m", snooze5Intent)
        }
        if (snooze30Intent != null) {
            builder.addAction(android.R.drawable.ic_menu_revert, "Adiar 30m", snooze30Intent)
        }

        notificationManager.notify(activity.id.hashCode(), builder.build())
    }

    private fun createSnoozePendingIntent(activity: AgendaActivity, minutes: Int): PendingIntent? {
        return try {
            val intent = Intent(context, AgendaNotificationReceiver::class.java).apply {
                action = ACTION_SNOOZE
                putExtra(EXTRA_ACTIVITY_ID, activity.id)
                putExtra("snooze_minutes", minutes)
            }
            PendingIntent.getBroadcast(
                context,
                (activity.id + "_snooze_$minutes").hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun createDismissPendingIntent(activity: AgendaActivity): PendingIntent? {
        return try {
            val intent = Intent(context, AgendaNotificationReceiver::class.java).apply {
                action = ACTION_DISMISS
                putExtra(EXTRA_ACTIVITY_ID, activity.id)
            }
            PendingIntent.getBroadcast(
                context,
                (activity.id + "_dismiss").hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateNotificationTime(activity: AgendaActivity): LocalDateTime {
        if (activity.notificationSettings.notificationTime != null) {
            return LocalDateTime.parse("${activity.date}T${activity.notificationSettings.notificationTime}")
        }

        val activityDateTime = if (activity.startTime != null) {
            LocalDateTime.parse("${activity.date}T${activity.startTime}")
        } else {
            LocalDateTime.parse("${activity.date}T09:00:00")
        }

        val type = activity.notificationSettings.notificationType
        return when (type) {
            AgendaNotificationType.NONE -> activityDateTime
            AgendaNotificationType.BEFORE_ACTIVITY -> activityDateTime
            AgendaNotificationType.CUSTOM -> {
                val customMinutes = activity.notificationSettings.customMinutesBefore ?: 15
                activityDateTime.minusMinutes(customMinutes.toLong())
            }
            else -> {
                val minutes = type.minutesBefore ?: 15
                activityDateTime.minusMinutes(minutes.toLong())
            }
        }
    }

    private fun getTriggerTime(date: String, notificationTime: LocalDateTime): Long {
        return notificationTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}
