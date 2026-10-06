// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.wallpaper

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

object WallpaperNotificationManager {

    const val CHANNEL_ID = "wallpapers_channel"
    const val NOTIFICATION_ID = 4004
    const val ACTION_OPEN_WALLPAPERS = "dev.viagaralauncher.action.OPEN_WALLPAPERS"
    const val EXTRA_OPEN_WALLPAPERS = "open_wallpapers"

    private const val PREFS_NAME = "wallpaper_notifications_prefs"
    private const val KEY_KNOWN_IDS = "known_wallpaper_ids"
    private const val KEY_INITIALIZED = "known_wallpapers_initialized"

    fun checkAndNotifyNewWallpapers(context: Context, catalog: WallpaperCatalog) {
        val appCtx = context.applicationContext
        val currentWallpapers = catalog.wallpapers
        if (currentWallpapers.isEmpty()) return

        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isInitialized = prefs.getBoolean(KEY_INITIALIZED, false)
        val currentIds = currentWallpapers.map { it.id }.toSet()

        if (!isInitialized) {
            // First time initialized: store existing catalog IDs so we don't spam on clean install
            prefs.edit()
                .putStringSet(KEY_KNOWN_IDS, currentIds)
                .putBoolean(KEY_INITIALIZED, true)
                .apply()
            return
        }

        val knownIds = prefs.getStringSet(KEY_KNOWN_IDS, emptySet()) ?: emptySet()
        val newWallpapers = currentWallpapers.filter { it.id !in knownIds }

        if (newWallpapers.isNotEmpty()) {
            postNewWallpapersNotification(appCtx, newWallpapers.size)
            val updated = HashSet(knownIds).apply { addAll(currentIds) }
            prefs.edit()
                .putStringSet(KEY_KNOWN_IDS, updated)
                .apply()
        }
    }

    private fun postNewWallpapersNotification(context: Context, count: Int) {
        runCatching {
            val appCtx = context.applicationContext
            val notificationManager = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    appCtx.getString(R.string.wallpaper_notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = appCtx.getString(R.string.wallpaper_notification_channel_desc)
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
                action = ACTION_OPEN_WALLPAPERS
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_WALLPAPERS, true)
            }

            val pendingIntent = PendingIntent.getActivity(
                appCtx,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val title = appCtx.getString(R.string.wallpaper_new_notification_title)
            val text = if (count == 1) {
                appCtx.getString(R.string.wallpaper_new_notification_single)
            } else {
                appCtx.getString(R.string.wallpaper_new_notification_multiple, count)
            }

            val notification = NotificationCompat.Builder(appCtx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    fun cancelNotification(context: Context) {
        runCatching {
            val notificationManager = context.applicationContext
                .getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(NOTIFICATION_ID)
        }
    }
}
