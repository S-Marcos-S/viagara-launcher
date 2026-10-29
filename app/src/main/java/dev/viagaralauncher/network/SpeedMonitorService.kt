// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.network

import android.app.AppOpsManager
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
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import dev.viagaralauncher.MainActivity
import dev.viagaralauncher.R
import dev.viagaralauncher.update.RootInstaller
import java.util.Calendar
import java.util.Locale

class SpeedMonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "speed_monitor_channel_v2"
        const val OLD_CHANNEL_ID = "speed_monitor_channel"
        const val NOTIFICATION_ID = 2100
        const val GROUP_KEY = "dev.viagaralauncher.group.SPEED_MONITOR"

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

        fun calculateActiveAppsFromDeltas(
            currentTotals: Map<Int, Long>,
            previousTotals: Map<Int, Long>,
            deltaTimeSec: Double,
            minBytesPerSec: Double,
            totalDeviceSpeed: Double = 0.0,
            resolveName: (Int) -> String?
        ): List<ActiveAppSpeed> {
            if (deltaTimeSec <= 0.0) return emptyList()
            val rawActive = mutableListOf<ActiveAppSpeed>()
            for ((uid, current) in currentTotals) {
                if (uid < 10000 && uid != 0 && uid != 1000) continue
                val prev = previousTotals[uid] ?: continue
                if (current > prev) {
                    val delta = current - prev
                    val rawSpeed = delta / deltaTimeSec
                    if (rawSpeed >= minBytesPerSec) {
                        val name = resolveName(uid)
                        if (name != null) {
                            rawActive.add(ActiveAppSpeed(name, rawSpeed))
                        }
                    }
                }
            }
            if (rawActive.isEmpty()) return emptyList()

            val result = if (totalDeviceSpeed > 0.0) {
                val sumRaw = rawActive.sumOf { it.speedBytesSec }
                if (sumRaw > totalDeviceSpeed) {
                    val factor = totalDeviceSpeed / sumRaw
                    rawActive.map { app ->
                        ActiveAppSpeed(app.appName, app.speedBytesSec * factor)
                    }
                } else {
                    rawActive.map { app ->
                        ActiveAppSpeed(app.appName, minOf(app.speedBytesSec, totalDeviceSpeed))
                    }
                }
            } else {
                rawActive
            }

            return result.filter { it.speedBytesSec >= minBytesPerSec }
                .sortedByDescending { it.speedBytesSec }
        }
    }

    data class ActiveAppSpeed(val appName: String, val speedBytesSec: Double)
    private data class RecentAppRecord(val speedBytesSec: Double, val timestamp: Long)

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
    private var isAppsLoading = false
    private var appsViewStartTime = 0L

    private var lastNotificationText = ""
    private var lastIconSpeedText = ""
    private var lastIsDownload = false
    private var lastShowRealTime = false
    private var lastAppsSummary = ""

    private val appNameCache = mutableMapOf<Int, String>()
    private val lastUidCumulativeTotals = mutableMapOf<Int, Long>()
    private val recentActiveApps = mutableMapOf<String, RecentAppRecord>()
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

        val initialNotif = createNotification("0 B/s", "0 B/s", "0 B/s", "0 B", "0 B", false, "", emptyList())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, initialNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, initialNotif)
        }

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
                isAppsLoading = true
                appsViewStartTime = System.currentTimeMillis()
                lastAppsSummary = ""
                recentActiveApps.clear()
                lastUidCumulativeTotals.clear()
                lastAppStatsQueryTime = 0L
                takeAppsBaselineSnapshot()
                updateStats(forceUpdate = true)
                handler.postDelayed({
                    if (isViewingApps) updateStats(forceUpdate = true)
                }, 1000L)
                return START_STICKY
            }
            ACTION_HIDE_APPS -> {
                isViewingApps = false
                isAppsLoading = false
                lastAppsSummary = ""
                recentActiveApps.clear()
                lastUidCumulativeTotals.clear()
                lastAppStatsQueryTime = 0L
                updateStats(forceUpdate = true)
                return START_STICKY
            }
            ACTION_TOGGLE_APPS -> {
                isViewingApps = !isViewingApps
                lastAppsSummary = ""
                if (isViewingApps) {
                    isAppsLoading = true
                    appsViewStartTime = System.currentTimeMillis()
                    recentActiveApps.clear()
                    lastUidCumulativeTotals.clear()
                    lastAppStatsQueryTime = 0L
                    takeAppsBaselineSnapshot()
                    handler.postDelayed({
                        if (isViewingApps) updateStats(forceUpdate = true)
                    }, 1000L)
                } else {
                    isAppsLoading = false
                    recentActiveApps.clear()
                    lastUidCumulativeTotals.clear()
                    lastAppStatsQueryTime = 0L
                }
                updateStats(forceUpdate = true)
                return START_STICKY
            }
        }

        // Start in foreground
        val notif = createNotification(
            iconSpeedText = lastIconSpeedText.ifEmpty { "0 B/s" },
            downSpeedText = "0 B/s",
            upSpeedText = "0 B/s",
            mobileText = formatBytes(dailyMobileTotal),
            wifiText = formatBytes(dailyWifiTotal),
            showRealTime = lastShowRealTime,
            contentText = lastNotificationText,
            activeApps = emptyList()
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notif)
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
        recentActiveApps.clear()
        lastUidCumulativeTotals.clear()
        appNameCache.clear()
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
        val totalSpeed = downloadSpeed + uploadSpeed

        val thresholdInBytes = settings.getThresholdInBytes()

        val showRealTime = settings.isRealTimeEnabled && (thresholdInBytes <= 0.0 || totalSpeed >= thresholdInBytes)

        val finalDownSpeed = if (showRealTime) downloadSpeed else 0.0
        val finalUpSpeed = if (showRealTime) uploadSpeed else 0.0
        val finalIconSpeed = if (showRealTime) displaySpeed else 0.0

        val speedText = formatSpeed(finalIconSpeed)
        val downSpeedText = formatSpeed(finalDownSpeed)
        val upSpeedText = formatSpeed(finalUpSpeed)
        val mobileText = formatBytes(dailyMobileTotal)
        val wifiText = formatBytes(dailyWifiTotal)

        val contentText = buildString {
            if (settings.isRealTimeEnabled && showRealTime) {
                append("↓$downSpeedText ↑$upSpeedText")
            }
            if (settings.isDailyUsageEnabled) {
                if (isNotEmpty()) append(" | ")
                append("M:$mobileText W:$wifiText")
            }
            if (isEmpty()) {
                append("↓0 B/s ↑0 B/s")
            }
        }

        val activeApps = if (isViewingApps) getActiveAppsSpeed(deltaTime, totalSpeed) else emptyList()
        val appsSummary = if (isViewingApps) activeApps.joinToString { "${it.appName}:${it.speedBytesSec}" } else ""

        // Only notify if something meaningful changed or if forceUpdate is requested
        if (forceUpdate ||
            contentText != lastNotificationText ||
            appsSummary != lastAppsSummary ||
            showRealTime != lastShowRealTime ||
            (showRealTime && (speedText != lastIconSpeedText || isDownload != lastIsDownload))
        ) {
            lastNotificationText = contentText
            lastIconSpeedText = speedText
            lastIsDownload = isDownload
            lastAppsSummary = appsSummary
            lastShowRealTime = showRealTime

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

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun tryAutoGrantUsagePermission() {
        if (RootInstaller.isRootAvailable()) {
            Thread {
                try {
                    val cmd = "pm grant $packageName android.permission.PACKAGE_USAGE_STATS 2>/dev/null; cmd appops set $packageName GET_USAGE_STATS allow 2>/dev/null; appops set $packageName GET_USAGE_STATS allow 2>/dev/null"
                    Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)).waitFor()
                } catch (_: Exception) {}
            }.start()
        }
    }

    private fun takeAppsBaselineSnapshot() {
        if (!hasUsageStatsPermission()) {
            tryAutoGrantUsagePermission()
            return
        }
        val nsm = getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager ?: return
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        val currentTime = System.currentTimeMillis()

        val totals = mutableMapOf<Int, Long>()
        collectUidCumulative(nsm, ConnectivityManager.TYPE_WIFI, startOfDay, currentTime, totals)
        collectUidCumulative(nsm, ConnectivityManager.TYPE_MOBILE, startOfDay, currentTime, totals)
        collectUidCumulative(nsm, ConnectivityManager.TYPE_ETHERNET, startOfDay, currentTime, totals)

        lastUidCumulativeTotals.clear()
        lastUidCumulativeTotals.putAll(totals)
        lastAppStatsQueryTime = currentTime
    }

    private fun getActiveAppsSpeed(deltaTime: Double, totalDeviceSpeed: Double): List<ActiveAppSpeed> {
        if (!hasUsageStatsPermission()) {
            tryAutoGrantUsagePermission()
            return emptyList()
        }

        val currentTime = System.currentTimeMillis()
        val minBytesPerSec = (settings.minAppUsageKb.coerceAtLeast(1L) * 1024.0)

        // If total device speed is virtually zero or below threshold, clear cached apps immediately
        if (totalDeviceSpeed < minBytesPerSec && recentActiveApps.isNotEmpty()) {
            recentActiveApps.clear()
        }

        val nsm = getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager ?: return emptyList()

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis

        val currentTotals = mutableMapOf<Int, Long>()
        collectUidCumulative(nsm, ConnectivityManager.TYPE_WIFI, startOfDay, currentTime, currentTotals)
        collectUidCumulative(nsm, ConnectivityManager.TYPE_MOBILE, startOfDay, currentTime, currentTotals)
        collectUidCumulative(nsm, ConnectivityManager.TYPE_ETHERNET, startOfDay, currentTime, currentTotals)

        if (lastUidCumulativeTotals.isNotEmpty() && lastAppStatsQueryTime > 0) {
            val elapsedSec = ((currentTime - lastAppStatsQueryTime) / 1000.0)
            if (elapsedSec >= 0.8) {
                val detected = calculateActiveAppsFromDeltas(
                    currentTotals = currentTotals,
                    previousTotals = lastUidCumulativeTotals,
                    deltaTimeSec = elapsedSec,
                    minBytesPerSec = minBytesPerSec,
                    totalDeviceSpeed = totalDeviceSpeed,
                    resolveName = { uid -> getAppNameForUid(uid) }
                )
                if (detected.isNotEmpty()) {
                    isAppsLoading = false
                    recentActiveApps.clear()
                    for (app in detected) {
                        recentActiveApps[app.appName] = RecentAppRecord(app.speedBytesSec, currentTime)
                    }
                } else if (currentTime - appsViewStartTime >= 1800L) {
                    isAppsLoading = false
                }
                lastUidCumulativeTotals.clear()
                lastUidCumulativeTotals.putAll(currentTotals)
                lastAppStatsQueryTime = currentTime
            }
        } else {
            lastUidCumulativeTotals.clear()
            lastUidCumulativeTotals.putAll(currentTotals)
            lastAppStatsQueryTime = currentTime
        }

        // Prune records older than 2500ms
        recentActiveApps.entries.removeAll { (currentTime - it.value.timestamp) > 2500L }

        val activeList = recentActiveApps.map { (name, record) ->
            val speed = if (totalDeviceSpeed > 0.0) minOf(record.speedBytesSec, totalDeviceSpeed) else record.speedBytesSec
            ActiveAppSpeed(name, speed)
        }.filter { it.speedBytesSec >= minBytesPerSec }
         .sortedByDescending { it.speedBytesSec }
         .take(4)

        if (activeList.isNotEmpty()) {
            isAppsLoading = false
        } else if (currentTime - appsViewStartTime >= 1800L) {
            isAppsLoading = false
        }

        return activeList
    }

    private fun collectUidCumulative(
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
        } catch (_: Throwable) {}
    }

    private fun getAppNameForUid(uid: Int): String? {
        if (uid == android.os.Process.myUid()) return null
        val cached = appNameCache[uid]
        if (cached != null) return cached

        val resolvedName = when (uid) {
            0 -> "Sistema (Root)"
            1000 -> "Sistema Android"
            else -> {
                val packages = packageManager.getPackagesForUid(uid)
                val pkg = packages?.firstOrNull() ?: packageManager.getNameForUid(uid) ?: "UID $uid"
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
        appNameCache[uid] = resolvedName
        return resolvedName
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
        val hasUsagePermission = hasUsageStatsPermission()
        val notifyIntent = if (isViewingApps && !hasUsagePermission) {
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_OPEN_NETWORK_STATS
                putExtra("open_network_stats", true)
                setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 1000, notifyIntent, pendingIntentFlags)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.net_speed_title))
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setGroup(GROUP_KEY)
            .setGroupSummary(false)
            .setSortKey("0_speed_monitor")
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
                if (!hasUsagePermission) {
                    append(getString(R.string.net_speed_permission_required))
                } else if (activeApps.isNotEmpty()) {
                    appendLine(getString(R.string.net_speed_active_apps_header, settings.minAppUsageKb))
                    for (app in activeApps) {
                        appendLine("• ${app.appName}: ${formatSpeed(app.speedBytesSec)}")
                    }
                } else if (isAppsLoading) {
                    append(getString(R.string.net_speed_calculating_apps))
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
        val toggleAppsPending = PendingIntent.getService(this, 1001, toggleAppsIntent, pendingIntentFlags)
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
        val stopPending = PendingIntent.getService(this, 1002, stopIntent, pendingIntentFlags)
        builder.addAction(0, getString(R.string.net_speed_action_stop), stopPending)

        // 3. Settings or Permission button
        val action3Pending = if (isViewingApps && !hasUsagePermission) {
            val usageIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            PendingIntent.getActivity(this, 1004, usageIntent, pendingIntentFlags)
        } else {
            val settingsIntent = Intent(this, MainActivity::class.java).apply {
                action = ACTION_OPEN_NETWORK_STATS
                putExtra("open_network_stats", true)
                setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            PendingIntent.getActivity(this, 1003, settingsIntent, pendingIntentFlags)
        }
        val action3Label = if (isViewingApps && !hasUsagePermission) {
            getString(R.string.net_speed_action_grant_permission)
        } else {
            getString(R.string.net_speed_action_settings)
        }
        builder.addAction(0, action3Label, action3Pending)

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
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Delete legacy channel so system immediately upgrades to default importance without sound
            runCatching {
                notificationManager.deleteNotificationChannel(OLD_CHANNEL_ID)
            }

            val name = getString(R.string.net_speed_notif_channel_name)
            val descriptionText = getString(R.string.net_speed_notif_channel_desc)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
