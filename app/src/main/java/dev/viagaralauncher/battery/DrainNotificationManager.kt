// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.battery

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.viagaralauncher.MainActivity
import dev.viagaralauncher.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale

/**
 * Manages the real-time persistent notification for battery drain statistics.
 */
class DrainNotificationManager private constructor(
    private val context: Context,
    private val drainTracker: AdvancedDrainTracker = AdvancedDrainTracker.getInstance(context),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    companion object {
        const val CHANNEL_ID = "drain_stats_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_OPEN_BATTERY_STATS = "dev.viagaralauncher.action.OPEN_BATTERY_STATS"
        const val EXTRA_OPEN_BATTERY_STATS = "open_battery_stats"
        private const val PREF_KEY_ENABLED = "pref_battery_drain_notification_enabled"

        @Volatile
        private var instance: DrainNotificationManager? = null

        fun getInstance(context: Context): DrainNotificationManager {
            return instance ?: synchronized(this) {
                instance ?: DrainNotificationManager(context.applicationContext).also { instance = it }
            }
        }

        fun isNotificationEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences("battery_prefs", Context.MODE_PRIVATE)
            return prefs.getBoolean(PREF_KEY_ENABLED, false)
        }

        fun setNotificationEnabled(context: Context, enabled: Boolean) {
            val prefs = context.getSharedPreferences("battery_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean(PREF_KEY_ENABLED, enabled).apply()
            val manager = getInstance(context)
            if (enabled) {
                manager.startNotification()
            } else {
                manager.stopNotification()
            }
        }
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var updateJob: Job? = null
    private var isShowing = false

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.drain_statistics),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.drain_notification_channel_desc)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun startNotification() {
        if (isShowing) return
        isShowing = true

        if (!drainTracker.isRunning()) {
            drainTracker.start()
        }

        if (Build.VERSION.SDK_INT >= 33) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                scope.launch {
                    if (RootBatteryStatsCollector.isRootAvailable()) {
                        RootBatteryStatsCollector.runAsRoot("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
                    }
                }
            }
        }
        
        updateNow()

        updateJob = scope.launch {
            drainTracker.drainState.collectLatest { state ->
                if (isShowing) {
                    try {
                        notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
                    } catch (e: SecurityException) {}
                }
            }
        }
    }

    fun stopNotification() {
        isShowing = false
        updateJob?.cancel()
        updateJob = null
        notificationManager.cancel(NOTIFICATION_ID)
    }

    fun updateNow() {
        if (isShowing) {
            val state = drainTracker.drainState.value
            try {
                notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
            } catch (e: SecurityException) {}
        }
    }

    private fun String.removeEmojis(): String {
        return this.replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF]"), "")
            .replace(Regex("[⚡⏳━]"), "")
            .trim()
            .replace(Regex("\\s{2,}"), " ")
    }

    private fun buildNotification(state: DrainState): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_BATTERY_STATS
                putExtra(EXTRA_OPEN_BATTERY_STATS, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val resetIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, DrainNotificationReceiver::class.java).apply {
                action = DrainNotificationReceiver.ACTION_RESET
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prefs = context.getSharedPreferences("battery_prefs", Context.MODE_PRIVATE)
        val showRates = prefs.getBoolean("notif_show_rates", true)
        val showDeepSleep = prefs.getBoolean("notif_show_deep_sleep", true)
        val showActiveIdle = prefs.getBoolean("notif_show_active_idle", true)
        val showTotalAvg = prefs.getBoolean("notif_show_total_avg", true)
        val showTemp = prefs.getBoolean("notif_show_temperature", true)

        val currentTemp = state.batteryTemperatureC ?: drainTracker.getBatteryTemperatureC()
        val tempText = if (showTemp && currentTemp != null) "${Math.round(currentTemp)}°" else null

        val currentStateText = when {
            state.isCharging -> {
                val chargingText = context.getString(R.string.drain_state_charging)
                if (tempText != null) "$tempText • $chargingText" else chargingText
            }
            state.isDeepSleep -> {
                val sleepText = context.getString(R.string.drain_state_deep_sleep)
                if (tempText != null) "$tempText • $sleepText" else sleepText
            }
            state.isDozing -> {
                val dozeText = context.getString(R.string.drain_state_dozing)
                if (tempText != null) "$tempText • $dozeText" else dozeText
            }
            state.isScreenOn -> {
                tempText ?: ""
            }
            else -> {
                val offText = context.getString(R.string.drain_state_screen_off)
                if (tempText != null) "$tempText • $offText" else offText
            }
        }.removeEmojis()

        val title = if (state.batteryLevel != null) {
            if (currentStateText.isNotBlank()) {
                "${state.batteryLevel}% • $currentStateText"
            } else {
                "${state.batteryLevel}%"
            }
        } else {
            if (currentStateText.isNotBlank()) {
                context.getString(R.string.battery_level_unknown, currentStateText)
            } else {
                context.getString(R.string.battery_level_unknown, "")
            }
        }.removeEmojis()

        val contentText = context.getString(
            R.string.drain_on_off_sleep,
            formatDrainPercentage(state.screenOnDrainRatePercent),
            formatDrainPercentage(state.screenOffDrainRatePercent),
            formatDrainPercentage(state.deepSleepDrainRatePercent)
        ).removeEmojis()

        val screenOnStr = context.getString(
            R.string.drain_screen_on_line,
            formatDrainPercentage(state.screenOnDrainRatePercent),
            formatDuration(state.screenOnTimeMs)
        ).removeEmojis()
        val screenOffStr = context.getString(
            R.string.drain_screen_off_line,
            formatDrainPercentage(state.screenOffDrainRatePercent),
            formatDuration(state.screenOffTimeMs)
        ).removeEmojis()
        val deepSleepStr = context.getString(
            R.string.drain_deep_sleep_line,
            formatDrainPercentage(state.deepSleepDrainRatePercent),
            formatDuration(state.deepSleepTimeMs),
            String.format(Locale.getDefault(), "%.0f%%", state.deepSleepPercentage)
        ).removeEmojis()
        val awakeStr = context.getString(
            R.string.drain_awake_line,
            formatDrainPercentage(state.awakeDrainRatePercent),
            formatDuration(state.awakeTimeMs)
        ).removeEmojis()
        val activeStr = context.getString(
            R.string.drain_active_line,
            formatDrainPercentage(state.activeDrainRatePercent),
            formatDuration(state.activeTimeMs)
        ).removeEmojis()
        val idleStr = context.getString(
            R.string.drain_idle_line,
            formatDrainPercentage(state.idleDrainRatePercent),
            formatDuration(state.idleTimeMs)
        ).removeEmojis()
        val totalStr = context.getString(
            R.string.drain_total_line,
            String.format(Locale.getDefault(), "%.1f mAh", state.totalDrainMah),
            formatDuration(state.totalTimeMs)
        ).removeEmojis()
        val avgStr = context.getString(
            R.string.drain_average_line,
            formatDrainPercentage(state.averageDrainRatePercent)
        ).removeEmojis()

        val bigText = buildString {
            if (showRates) {
                appendLine("$screenOnStr • $screenOffStr")
            }
            if (showDeepSleep) {
                appendLine("$deepSleepStr • $awakeStr")
            }
            if (showActiveIdle) {
                appendLine("$activeStr • $idleStr")
            }
            if (showTotalAvg) {
                append("$totalStr • $avgStr")
            }
        }.trimEnd()

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                android.R.drawable.ic_menu_rotate,
                context.getString(R.string.reset),
                resetIntent
            )
            .build()
    }
}
