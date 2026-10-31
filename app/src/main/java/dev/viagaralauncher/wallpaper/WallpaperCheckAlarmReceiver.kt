// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.wallpaper

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

class WallpaperCheckAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appCtx = context.applicationContext

        // Schedule next check immediately
        scheduleNextCheck(appCtx)

        // Perform wallpaper catalog check on background thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WallpaperRepository.loadCatalog(appCtx)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load catalog during periodic wallpaper check", e)
            }
        }
    }

    companion object {
        private const val TAG = "WallpaperAlarmReceiver"
        const val ACTION_CHECK_WALLPAPERS = "dev.viagaralauncher.action.CHECK_WALLPAPERS"
        private const val REQUEST_CODE = 4092

        /**
         * Calculates the next target window (03:00 UTC or 15:00 UTC) right after
         * the GitHub Actions workflows complete classification, and schedules an alarm.
         */
        fun scheduleNextCheck(context: Context) {
            runCatching {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, WallpaperCheckAlarmReceiver::class.java).apply {
                    action = ACTION_CHECK_WALLPAPERS
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

                val triggerAtMillis = calculateNextTriggerTimeMs()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            }
        }

        private fun calculateNextTriggerTimeMs(): Long {
            val nowUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            val nowMs = nowUtc.timeInMillis

            // Target hours in UTC: 03:00 (after 02:30 workflow) and 15:00 (after 14:30 workflow)
            val window1 = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                set(Calendar.HOUR_OF_DAY, 3)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val window2 = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                set(Calendar.HOUR_OF_DAY, 15)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            return when {
                window1.timeInMillis > nowMs -> window1.timeInMillis
                window2.timeInMillis > nowMs -> window2.timeInMillis
                else -> {
                    // Both have passed for today, schedule for tomorrow 03:00 UTC
                    window1.add(Calendar.DAY_OF_YEAR, 1)
                    window1.timeInMillis
                }
            }
        }
    }
}
