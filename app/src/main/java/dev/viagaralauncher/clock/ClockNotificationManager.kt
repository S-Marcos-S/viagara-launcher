// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.clock

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.viagaralauncher.MainActivity
import dev.viagaralauncher.R

object ClockNotificationManager {

    const val CHANNEL_ID = "clocks_channel"
    const val NOTIFICATION_ID = 4005
    const val ACTION_OPEN_CLOCKS = "dev.viagaralauncher.action.OPEN_CLOCKS"
    const val EXTRA_OPEN_CLOCKS = "open_clocks"

    private const val PREFS_NAME = "clock_notifications_prefs"
    private const val KEY_KNOWN_IDS = "known_clock_ids"
    private const val KEY_INITIALIZED = "known_clocks_initialized"

    fun checkAndNotifyNewClocks(context: Context, catalog: ClockCatalog) {
        val appCtx = context.applicationContext
        val currentClocks = catalog.clocks
        if (currentClocks.isEmpty()) return

        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isInitialized = prefs.getBoolean(KEY_INITIALIZED, false)
        val currentIds = currentClocks.map { it.id }.toSet()

        if (!isInitialized) {
            // First time initialized: store current IDs so clean installs don't get spammed
            prefs.edit()
                .putStringSet(KEY_KNOWN_IDS, currentIds)
                .putBoolean(KEY_INITIALIZED, true)
                .apply()
            return
        }

        val knownIds = prefs.getStringSet(KEY_KNOWN_IDS, emptySet()) ?: emptySet()
        val newClocks = currentClocks.filter { it.id !in knownIds }

        if (newClocks.isNotEmpty()) {
            postNewClocksNotification(appCtx, newClocks.size)
            val updated = HashSet(knownIds).apply { addAll(currentIds) }
            prefs.edit()
                .putStringSet(KEY_KNOWN_IDS, updated)
                .apply()
        }
    }

    fun markAllAsSeen(context: Context, catalog: ClockCatalog) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentIds = catalog.clocks.map { it.id }.toSet()
        prefs.edit()
            .putStringSet(KEY_KNOWN_IDS, currentIds)
            .putBoolean(KEY_INITIALIZED, true)
            .apply()
    }

    private fun postNewClocksNotification(context: Context, count: Int) {
        runCatching {
            val appCtx = context.applicationContext
            val notificationManager = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    appCtx.getString(R.string.clock_notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = appCtx.getString(R.string.clock_notification_channel_desc)
                }
                notificationManager.createNotificationChannel(channel)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    appCtx,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasPermission) return
            }

            val intent = Intent(appCtx, MainActivity::class.java).apply {
                action = ACTION_OPEN_CLOCKS
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_CLOCKS, true)
            }

            val pendingIntent = PendingIntent.getActivity(
                appCtx,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val title = appCtx.getString(R.string.clock_new_notification_title)
            val text = if (count == 1) {
                appCtx.getString(R.string.clock_new_notification_single)
            } else {
                appCtx.getString(R.string.clock_new_notification_multiple, count)
            }

            val notification = NotificationCompat.Builder(appCtx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setGroup("dev.viagaralauncher.group.CLOCKS")
                .setGroupSummary(false)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }
}
