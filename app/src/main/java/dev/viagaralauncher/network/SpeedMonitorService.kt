// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.network

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import dev.viagaralauncher.MainActivity
import dev.viagaralauncher.R
import java.util.Calendar
import java.util.Locale

class SpeedMonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "speed_monitor_channel"
        const val NOTIFICATION_ID = 2100

        const val ACTION_START = "dev.viagaralauncher.network.ACTION_START_SPEED_MONITOR"
        const val ACTION_STOP = "dev.viagaralauncher.network.ACTION_STOP_SPEED_MONITOR"
        const val ACTION_VIEW_APPS = "dev.viagaralauncher.network.ACTION_VIEW_APPS"
        const val ACTION_HIDE_APPS = "dev.viagaralauncher.network.ACTION_HIDE_APPS"
        const val ACTION_TOGGLE_APPS = "dev.viagaralauncher.network.ACTION_TOGGLE_APPS"
        const val ACTION_OPEN_NETWORK_STATS = "dev.viagaralauncher.action.OPEN_NETWORK_STATS"

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        fun start(context: Context) {
            val settings = NetSpeedSettings(context)
            settings.isEnabled = true
            val intent = Intent(context, SpeedMonitorService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            val settings = NetSpeedSettings(context)
            settings.isEnabled = false
            val intent = Intent(context, SpeedMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }

    private data class UidSnapshot(val rx: Long, val tx: Long, val timestamp: Long)
    private data class ActiveAppSpeed(val appName: String, val speedBytesSec: Double)

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settings: NetSpeedSettings

    private var lastRxBytes: Long = 0L
    private var lastTxBytes: Long = 0L
    private var lastTime: Long = 0L
    private var lastStatsUpdateTime: Long = 0L

    // Cached daily stats from NetworkStatsManager
    private var dailyMobileTotal: Long = 0L
    private var dailyWifiTotal: Long = 0L

    private var isScreenOn = true
    private var isViewingApps = false

    private var lastNotificationText = ""
    private var lastIconSpeedText = ""
    private var lastIsDownload = false
    private var lastAppsSummary = ""

    private val appNameCache = mutableMapOf<Int, String>()
    private val lastUidSnapshots = mutableMapOf<Int, UidSnapshot>()
    private var lastAppStatsQueryTime = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    // Force daily stats update when screen turns on
                    lastStatsUpdateTime = 0L
                    handler.removeCallbacks(updateRunnable)
                    handler.post(updateRunnable)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    handler.removeCallbacks(updateRunnable)
                }
            }
        }
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isScreenOn) return
            updateStats()
            handler.postDelayed(this, settings.updateInterval.coerceAtLeast(500L))
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = NetSpeedSettings(this)
        createNotificationChannel()

        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTxBytes = TrafficStats.getTotalTxBytes()
        lastTime = System.currentTimeMillis()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
        isServiceRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                settings.isEnabled = false
                isServiceRunning = false
                handler.removeCallbacks(updateRunnable)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NOTIFICATION_ID)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_VIEW_APPS -> {
                isViewingApps = true
                lastAppsSummary = ""
                updateStats(forceUpdate = true)
                return START_STICKY
            }
            ACTION_HIDE_APPS -> {
                isViewingApps = false
                lastAppsSummary = ""
                updateStats(forceUpdate = true)
                return START_STICKY
            }
            ACTION_TOGGLE_APPS -> {
                isViewingApps = !isViewingApps
                lastAppsSummary = ""
                updateStats(forceUpdate = true)
                return START_STICKY
            }
        }

        // Start in foreground
        val initialNotif = createNotification("0 B/s", "0 B/s", "0 B/s", "0 B", "0 B", false, "", emptyList())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, initialNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, initialNotif)
        }

        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTxBytes = TrafficStats.getTotalTxBytes()
        lastStatsUpdateTime = 0L // Force update on start

        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)
        isServiceRunning = true
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isServiceRunning = false
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }

    private fun updateDailyTotalsFromSystem() {
        val networkStatsManager = getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager ?: return
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        dailyMobileTotal = queryDeviceTotal(networkStatsManager, ConnectivityManager.TYPE_MOBILE, startTime, endTime)
        dailyWifiTotal = queryDeviceTotal(networkStatsManager, ConnectivityManager.TYPE_WIFI, startTime, endTime)
    }

    private fun queryDeviceTotal(manager: NetworkStatsManager, type: Int, start: Long, end: Long): Long {
        return try {
            val bucket = manager.querySummaryForDevice(type, null, start, end)
            bucket.rxBytes + bucket.txBytes
        } catch (_: Exception) {
            0L
        }
    }

    private fun updateStats(forceUpdate: Boolean = false) {
        val currentTime = System.currentTimeMillis()

        // Update daily totals from system every 30 seconds to save battery
        if (currentTime - lastStatsUpdateTime >= 30000L || lastStatsUpdateTime == 0L) {
            updateDailyTotalsFromSystem()
            lastStatsUpdateTime = currentTime
        }

        val currentTotalRx = TrafficStats.getTotalRxBytes()
        val currentTotalTx = TrafficStats.getTotalTxBytes()

        val deltaTime = ((currentTime - lastTime) / 1000.0).coerceAtLeast(0.1)

        val deltaRx = if (lastRxBytes > 0 && currentTotalRx >= lastRxBytes) currentTotalRx - lastRxBytes else 0L
        val deltaTx = if (lastTxBytes > 0 && currentTotalTx >= lastTxBytes) currentTotalTx - lastTxBytes else 0L

        val downloadSpeed = deltaRx / deltaTime
        val uploadSpeed = deltaTx / deltaTime

        lastRxBytes = currentTotalRx
        lastTxBytes = currentTotalTx
        lastTime = currentTime

        val isDownload = downloadSpeed >= uploadSpeed
        val displaySpeed = if (isDownload) downloadSpeed else uploadSpeed

        val thresholdInBytes = when (settings.thresholdUnit) {
            "KB/s" -> settings.thresholdValue * 1024
            "MB/s" -> settings.thresholdValue * 1024 * 1024
            else -> settings.thresholdValue
        }

        val showRealTime = settings.isRealTimeEnabled && displaySpeed >= thresholdInBytes

        val finalDownSpeed = if (showRealTime) downloadSpeed else 0.0
        val finalUpSpeed = if (showRealTime) uploadSpeed else 0.0
        val finalIconSpeed = if (showRealTime) displaySpeed else 0.0

        val speedText = formatSpeed(finalIconSpeed)
        val downSpeedText = formatSpeed(finalDownSpeed)
        val upSpeedText = formatSpeed(finalUpSpeed)
        val mobileText = formatBytes(dailyMobileTotal)
        val wifiText = formatBytes(dailyWifiTotal)

        val contentText = buildString {
            if (settings.isRealTimeEnabled) {
                append("↓$downSpeedText ↑$upSpeedText")
            }
            if (settings.isDailyUsageEnabled) {
                if (isNotEmpty()) append(" | ")
                append("M:$mobileText W:$wifiText")
            }
        }

        val activeApps = if (isViewingApps) getActiveAppsSpeed(deltaTime) else emptyList()
        val appsSummary = if (isViewingApps) activeApps.joinToString { "${it.appName}:${it.speedBytesSec}" } else ""

        // Only notify if something meaningful changed or if forceUpdate is requested
        if (forceUpdate ||
            contentText != lastNotificationText ||
            appsSummary != lastAppsSummary ||
            (showRealTime && (speedText != lastIconSpeedText || isDownload != lastIsDownload))
        ) {
            lastNotificationText = contentText
            lastIconSpeedText = speedText
            lastIsDownload = isDownload
            lastAppsSummary = appsSummary

            val notification = createNotification(
                iconSpeedText = speedText,
                downSpeedText = downSpeedText,
                upSpeedText = upSpeedText,
                mobileText = mobileText,
                wifiText = wifiText,
                showRealTime = showRealTime,
                contentText = contentText,
                activeApps = activeApps
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun getActiveAppsSpeed(deltaTime: Double): List<ActiveAppSpeed> {
        val minBytesPerSec = settings.minAppUsageKb * 1024.0 // Threshold 50 KB/s
        val activeApps = mutableListOf<ActiveAppSpeed>()
        val currentTime = System.currentTimeMillis()

        // 1. Process-level quick check via TrafficStats for running apps
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val runningUids = mutableSetOf<Int>()
        activityManager?.runningAppProcesses?.forEach { process ->
            if (process.uid >= 10000) runningUids.add(process.uid)
        }

        var usedTrafficStats = false
        if (runningUids.isNotEmpty()) {
            var validSamples = 0
            for (uid in runningUids) {
                val curRx = TrafficStats.getUidRxBytes(uid)
                val curTx = TrafficStats.getUidTxBytes(uid)
                if (curRx >= 0 && curTx >= 0) {
                    validSamples++
                    val last = lastUidSnapshots[uid]
                    if (last != null) {
                        val dRx = if (curRx >= last.rx) curRx - last.rx else 0L
                        val dTx = if (curTx >= last.tx) curTx - last.tx else 0L
                        val totalSpeed = (dRx + dTx) / deltaTime
                        if (totalSpeed >= minBytesPerSec) {
                            val name = getAppNameForUid(uid)
                            if (name != null) {
                                activeApps.add(ActiveAppSpeed(name, totalSpeed))
                            }
                        }
                    }
                    lastUidSnapshots[uid] = UidSnapshot(curRx, curTx, currentTime)
                }
            }
            if (validSamples > 0) {
                usedTrafficStats = true
            }
        }

        // 2. Fallback to NetworkStatsManager querySummary if TrafficStats is restricted on device
        if (!usedTrafficStats) {
            val nsm = getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            if (nsm != null && lastAppStatsQueryTime > 0) {
                val windowStart = lastAppStatsQueryTime
                val windowEnd = currentTime
                val uidMap = mutableMapOf<Int, Long>()
                collectUidDeltas(nsm, ConnectivityManager.TYPE_WIFI, windowStart, windowEnd, uidMap)
                collectUidDeltas(nsm, ConnectivityManager.TYPE_MOBILE, windowStart, windowEnd, uidMap)

                val winSec = ((windowEnd - windowStart) / 1000.0).coerceAtLeast(0.5)
                for ((uid, totalDelta) in uidMap) {
                    if (uid < 10000) continue
                    val totalSpeed = totalDelta / winSec
                    if (totalSpeed >= minBytesPerSec) {
                        val name = getAppNameForUid(uid)
                        if (name != null) {
                            activeApps.add(ActiveAppSpeed(name, totalSpeed))
                        }
                    }
                }
            }
            lastAppStatsQueryTime = currentTime
        }

        return activeApps.sortedByDescending { it.speedBytesSec }.take(4)
    }

    private fun collectUidDeltas(
        nsm: NetworkStatsManager,
        networkType: Int,
        startTime: Long,
        endTime: Long,
        result: MutableMap<Int, Long>
    ) {
        try {
            val stats = nsm.querySummary(networkType, null, startTime, endTime)
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val uid = bucket.uid
                val current = result[uid] ?: 0L
                result[uid] = current + bucket.rxBytes + bucket.txBytes
            }
            stats.close()
        } catch (_: Exception) {}
    }

    private fun getAppNameForUid(uid: Int): String? {
        if (uid == android.os.Process.myUid()) return null
        return appNameCache.getOrPut(uid) {
            val packages = packageManager.getPackagesForUid(uid)
            val pkg = packages?.firstOrNull() ?: return@getOrPut "UID $uid"
            if (pkg == packageName) return null
            try {
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getApplicationInfo(pkg, 0)
                }
                packageManager.getApplicationLabel(info).toString()
            } catch (_: Exception) {
                pkg
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024))
            bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024))
            bytes >= 1024 -> String.format(Locale.getDefault(), "%d KB", bytes / 1024)
            else -> "$bytes B"
        }
    }

    private fun formatSpeed(bytesPerSecond: Double): String {
        return when {
            bytesPerSecond >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB/s", bytesPerSecond / (1024 * 1024))
            bytesPerSecond >= 1024 -> String.format(Locale.getDefault(), "%d KB/s", (bytesPerSecond / 1024).toInt())
            else -> String.format(Locale.getDefault(), "%d B/s", bytesPerSecond.toInt())
        }
    }

    private fun createNotification(
        iconSpeedText: String,
        downSpeedText: String,
        upSpeedText: String,
        mobileText: String,
        wifiText: String,
        showRealTime: Boolean,
        contentText: String = "",
        activeApps: List<ActiveAppSpeed> = emptyList()
    ): Notification {
        val notifyIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_NETWORK_STATS
            putExtra("open_network_stats", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 1000, notifyIntent, flags)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.net_speed_title))
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && showRealTime) {
            val bitmap = createSpeedBitmap(iconSpeedText)
            builder.setSmallIcon(IconCompat.createWithBitmap(bitmap))
        } else {
            builder.setSmallIcon(R.drawable.ic_net_speed)
        }

        // Expanded view showing speeds, daily usage, and active apps if toggled
        val bigTextStyle = NotificationCompat.BigTextStyle()
        val bigText = buildString {
            if (settings.isRealTimeEnabled) {
                append("${getString(R.string.net_speed_download)}: $downSpeedText  |  ${getString(R.string.net_speed_upload)}: $upSpeedText")
            }
            if (settings.isDailyUsageEnabled) {
                if (isNotEmpty()) append("\n\n")
                append("${getString(R.string.net_speed_mobile_today)}: $mobileText\n")
                append("${getString(R.string.net_speed_wifi_today)}: $wifiText")
            }

            if (isViewingApps) {
                append("\n\n")
                if (activeApps.isNotEmpty()) {
                    appendLine(getString(R.string.net_speed_active_apps_header, settings.minAppUsageKb))
                    for (app in activeApps) {
                        appendLine("• ${app.appName}: ${formatSpeed(app.speedBytesSec)}")
                    }
                } else {
                    append(getString(R.string.net_speed_no_active_apps, settings.minAppUsageKb))
                }
            }
        }.trimEnd()
        builder.setStyle(bigTextStyle.bigText(bigText))

        // Action Buttons: extremely concise, displayed right below the apps
        // 1. Toggle Apps view button ("Ocultar" when open, "Ver apps" when closed)
        val toggleAppsIntent = Intent(this, SpeedMonitorService::class.java).apply {
            action = if (isViewingApps) ACTION_HIDE_APPS else ACTION_VIEW_APPS
        }
        val toggleAppsPending = PendingIntent.getService(this, 1001, toggleAppsIntent, flags)
        val toggleLabel = if (isViewingApps) {
            getString(R.string.net_speed_action_hide_apps)
        } else {
            getString(R.string.net_speed_action_view_apps)
        }
        builder.addAction(0, toggleLabel, toggleAppsPending)

        // 2. Stop service button ("Parar")
        val stopIntent = Intent(this, SpeedMonitorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(this, 1002, stopIntent, flags)
        builder.addAction(0, getString(R.string.net_speed_action_stop), stopPending)

        // 3. Settings button ("Config.")
        val settingsIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_NETWORK_STATS
            putExtra("open_network_stats", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val settingsPending = PendingIntent.getActivity(this, 1003, settingsIntent, flags)
        builder.addAction(0, getString(R.string.net_speed_action_settings), settingsPending)

        return builder.build()
    }

    private fun createSpeedBitmap(speedText: String): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }

        val parts = speedText.split(" ")
        val valueOnly = parts.getOrNull(0) ?: "0"
        val unit = parts.getOrNull(1) ?: "B/s"

        paint.textSize = 50f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(valueOnly, size / 2f, size / 2f + 10f, paint)

        paint.textSize = 25f
        canvas.drawText(unit, size / 2f, size - 10f, paint)

        return bitmap
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.net_speed_notif_channel_name)
            val descriptionText = getString(R.string.net_speed_notif_channel_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
