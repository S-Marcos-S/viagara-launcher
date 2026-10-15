// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.battery

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.viagaralauncher.MainActivity
import dev.viagaralauncher.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Controller for aggressive screen-off Doze management (inspired by EnforceDoze/ForceDoze).
 * Immediately forces Android's deep Doze mode when screen turns off, optionally disabling
 * motion sensing, and notifies the user with battery efficiency stats on screen turn on.
 */
class EnforceDozeController private constructor(
    private val context: Context,
    private val shellRunner: ShellRunner = ShellRunner(context.applicationContext),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    companion object {
        private const val TAG = "EnforceDozeController"
        private const val PREFS_NAME = "battery_prefs"

        const val PREF_ENFORCE_DOZE_ENABLED = "pref_enforce_doze_enabled"
        const val PREF_DISABLE_MOTION = "pref_enforce_doze_disable_motion"
        const val PREF_DELAY_SECONDS = "pref_enforce_doze_delay_seconds"
        const val PREF_NOTIF_ON_WAKE = "pref_enforce_doze_notif_on_wake"

        const val CHANNEL_ID = "channel_enforce_doze"
        const val NOTIFICATION_ID = 2050

        // Minimum screen off duration (e.g. 5 minutes) to trigger a wake notification
        private const val MIN_SCREEN_OFF_FOR_NOTIF_MS = 5 * 60 * 1000L

        @Volatile
        private var instance: EnforceDozeController? = null

        fun getInstance(context: Context): EnforceDozeController {
            return instance ?: synchronized(this) {
                instance ?: EnforceDozeController(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val _isEnabled = MutableStateFlow(prefs.getBoolean(PREF_ENFORCE_DOZE_ENABLED, false))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _disableMotion = MutableStateFlow(prefs.getBoolean(PREF_DISABLE_MOTION, true))
    val disableMotion: StateFlow<Boolean> = _disableMotion.asStateFlow()

    private val _delaySeconds = MutableStateFlow(prefs.getInt(PREF_DELAY_SECONDS, 60))
    val delaySeconds: StateFlow<Int> = _delaySeconds.asStateFlow()

    private val _notifOnWake = MutableStateFlow(prefs.getBoolean(PREF_NOTIF_ON_WAKE, true))
    val notifOnWake: StateFlow<Boolean> = _notifOnWake.asStateFlow()

    private val _isEnforcing = MutableStateFlow(false)
    val isEnforcing: StateFlow<Boolean> = _isEnforcing.asStateFlow()

    private var screenReceiver: BroadcastReceiver? = null
    private var dozeJob: Job? = null

    // Session tracking for the screen-off period
    private var screenOffRealtime: Long = 0L
    private var screenOffBatteryLevel: Int = -1

    init {
        createNotificationChannel()
        if (_isEnabled.value) {
            registerReceiver()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.enforce_doze_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.enforce_doze_channel_desc)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_ENFORCE_DOZE_ENABLED, enabled).apply()
        _isEnabled.value = enabled
        if (enabled) {
            registerReceiver()
        } else {
            unregisterReceiver()
            cancelPendingDoze()
            scope.launch {
                unforceDoze()
            }
        }
    }

    fun setDisableMotion(disable: Boolean) {
        prefs.edit().putBoolean(PREF_DISABLE_MOTION, disable).apply()
        _disableMotion.value = disable
    }

    fun setDelaySeconds(seconds: Int) {
        prefs.edit().putInt(PREF_DELAY_SECONDS, seconds).apply()
        _delaySeconds.value = seconds
    }

    fun setNotifOnWake(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_NOTIF_ON_WAKE, enabled).apply()
        _notifOnWake.value = enabled
    }

    @Synchronized
    private fun registerReceiver() {
        if (screenReceiver != null) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> onScreenOff()
                    Intent.ACTION_SCREEN_ON -> onScreenOn()
                }
            }
        }
        screenReceiver = receiver
        context.registerReceiver(receiver, filter)
    }

    @Synchronized
    private fun unregisterReceiver() {
        screenReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister screenReceiver", e)
            }
            screenReceiver = null
        }
    }

    private fun onScreenOff() {
        if (!_isEnabled.value) return
        screenOffRealtime = SystemClock.elapsedRealtime()
        screenOffBatteryLevel = getBatteryLevel()

        val delaySec = _delaySeconds.value
        cancelPendingDoze()

        dozeJob = scope.launch {
            if (delaySec > 0) {
                delay(delaySec * 1000L)
            }
            if (!isActive || powerManager.isInteractive) return@launch

            applyEnforceDoze()
        }
    }

    private fun onScreenOn() {
        cancelPendingDoze()

        val wasEnforcing = _isEnforcing.value
        val offDurationMs = (SystemClock.elapsedRealtime() - screenOffRealtime).coerceAtLeast(0L)
        val initialLevel = screenOffBatteryLevel
        val currentLevel = getBatteryLevel()

        scope.launch {
            unforceDoze()

            if (wasEnforcing && _notifOnWake.value && offDurationMs >= MIN_SCREEN_OFF_FOR_NOTIF_MS) {
                postWakeReportNotification(offDurationMs, initialLevel, currentLevel)
            }
        }
    }

    private fun cancelPendingDoze() {
        dozeJob?.cancel()
        dozeJob = null
    }

    private suspend fun applyEnforceDoze() {
        try {
            if (_disableMotion.value) {
                shellRunner.exec("dumpsys deviceidle disable motion")
            }
            val outcome = shellRunner.exec("dumpsys deviceidle force-idle deep")
            _isEnforcing.value = (outcome is ShellRunner.Outcome.Success)
            Log.d(TAG, "EnforceDoze triggered: force-idle deep result = $outcome")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying enforce doze", e)
            _isEnforcing.value = false
        }
    }

    private suspend fun unforceDoze() {
        if (_isEnforcing.value) {
            try {
                shellRunner.exec("dumpsys deviceidle unforce")
                if (_disableMotion.value) {
                    shellRunner.exec("dumpsys deviceidle enable motion")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error unforcing doze", e)
            } finally {
                _isEnforcing.value = false
            }
        }
    }

    private fun getBatteryLevel(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
        return bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    }

    private fun postWakeReportNotification(durationMs: Long, startLevel: Int, endLevel: Int) {
        val hours = durationMs / 3600000f
        val drainPercent = if (startLevel in 0..100 && endLevel in 0..100) {
            (startLevel - endLevel).coerceAtLeast(0)
        } else 0

        val ratePerHour = if (hours > 0.05f) drainPercent / hours else 0f
        val formattedTime = formatDuration(durationMs)

        val title = context.getString(R.string.enforce_doze_notif_title)
        val content = context.getString(
            R.string.enforce_doze_notif_content,
            formattedTime,
            drainPercent,
            String.format(Locale.getDefault(), "%.1f", ratePerHour)
        )

        val intent = Intent(context, MainActivity::class.java).apply {
            action = DrainNotificationManager.ACTION_OPEN_BATTERY_STATS
            putExtra(DrainNotificationManager.EXTRA_OPEN_BATTERY_STATS, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                context.getString(
                    R.string.enforce_doze_notif_big_text,
                    formattedTime,
                    drainPercent,
                    String.format(Locale.getDefault(), "%.1f", ratePerHour)
                )
            ))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notif)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing for EnforceDoze wake report", e)
        }
    }

    private fun formatDuration(ms: Long): String {
        val minutes = ms / 60000
        val hours = minutes / 60
        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            else -> "${minutes}m"
        }
    }
}
