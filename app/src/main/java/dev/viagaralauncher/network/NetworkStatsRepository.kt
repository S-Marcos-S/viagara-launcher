// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.network

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import androidx.core.graphics.drawable.toBitmap
import dev.viagaralauncher.root.AppRootInspector
import dev.viagaralauncher.update.RootInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class NetworkStatsRepository(private val context: Context) {

    private val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
    private val packageManager = context.packageManager
    private val appDetailsCache = mutableMapOf<String, AppDetails>()

    private data class AppDetails(
        val appName: String,
        val icon: Drawable?,
        val dominantColor: Int,
    )

    suspend fun hasUsageStatsPermission(): Boolean = withContext(Dispatchers.IO) {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return@withContext false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }

        if (mode == AppOpsManager.MODE_ALLOWED) {
            return@withContext true
        }

        // Auto-grant via root if device is rooted
        if (RootInstaller.isRootAvailable()) {
            val cmd = "pm grant ${context.packageName} android.permission.PACKAGE_USAGE_STATS 2>/dev/null; appops set ${context.packageName} GET_USAGE_STATS allow 2>/dev/null"
            AppRootInspector.runSuCommand(cmd)

            val newMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
            return@withContext newMode == AppOpsManager.MODE_ALLOWED
        }

        false
    }

    private fun getAppDetails(packageName: String, uid: Int): AppDetails {
        return appDetailsCache.getOrPut(packageName) {
            try {
                val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getApplicationInfo(packageName, 0)
                }
                val label = packageManager.getApplicationLabel(appInfo).toString()
                val icon = packageManager.getApplicationIcon(appInfo)
                val color = extractDominantColor(icon)
                AppDetails(label, icon, color)
            } catch (_: Throwable) {
                val label = when (uid) {
                    0 -> "Sistema (Root)"
                    1000 -> "Sistema Android"
                    else -> packageName
                }
                AppDetails(label, null, Color.GRAY)
            }
        }
    }

    private fun extractDominantColor(drawable: Drawable?): Int {
        if (drawable == null) return Color.GRAY
        return try {
            val bitmap = drawable.toBitmap(width = 40, height = 40, config = Bitmap.Config.ARGB_8888)
            var redSum = 0L
            var greenSum = 0L
            var blueSum = 0L
            var count = 0

            for (x in 0 until bitmap.width) {
                for (y in 0 until bitmap.height) {
                    val pixel = bitmap.getPixel(x, y)
                    val alpha = Color.alpha(pixel)
                    if (alpha > 150) {
                        val r = Color.red(pixel)
                        val g = Color.green(pixel)
                        val b = Color.blue(pixel)

                        val isNearWhite = r > 240 && g > 240 && b > 240
                        val isNearBlack = r < 15 && g < 15 && b < 15
                        if (!isNearWhite && !isNearBlack) {
                            redSum += r
                            greenSum += g
                            blueSum += b
                            count++
                        }
                    }
                }
            }

            if (count > 0) {
                Color.rgb((redSum / count).toInt(), (greenSum / count).toInt(), (blueSum / count).toInt())
            } else {
                val centerPixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                if (Color.alpha(centerPixel) > 0) centerPixel else Color.GRAY
            }
        } catch (_: Throwable) {
            Color.GRAY
        }
    }

    private fun getAppSegmentsForInterval(startTime: Long, endTime: Long, totalUsage: Long): List<AppUsageSegment> {
        if (totalUsage <= 0) return emptyList()
        val usageMap = mutableMapOf<Int, Long>()

        collectUidUsageForInterval(ConnectivityManager.TYPE_MOBILE, startTime, endTime, usageMap)
        collectUidUsageForInterval(ConnectivityManager.TYPE_WIFI, startTime, endTime, usageMap)

        return usageMap.mapNotNull { (uid, bytes) ->
            if (bytes <= 1 * 1024) return@mapNotNull null // Ignore below 1KB noise
            val packageNames = packageManager.getPackagesForUid(uid) ?: return@mapNotNull null
            val packageName = packageNames.firstOrNull() ?: return@mapNotNull null

            val details = getAppDetails(packageName, uid)
            AppUsageSegment(
                packageName = packageName,
                appName = details.appName,
                icon = details.icon,
                bytes = bytes,
                color = details.dominantColor,
            )
        }.sortedByDescending { it.bytes }
    }

    private fun collectUidUsageForInterval(
        networkType: Int,
        startTime: Long,
        endTime: Long,
        map: MutableMap<Int, Long>,
    ) {
        val manager = networkStatsManager ?: return
        try {
            val stats = manager.querySummary(networkType, null, startTime, endTime)
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val uid = bucket.uid
                val current = map[uid] ?: 0L
                map[uid] = current + bucket.rxBytes + bucket.txBytes
            }
            stats.close()
        } catch (_: Throwable) {
            // Ignore if permission denied or query fails
        }
    }

    suspend fun getStatsForPeriod(startTime: Long, endTime: Long): TimePeriodStats = withContext(Dispatchers.IO) {
        val mobileTotal = getTotalUsage(ConnectivityManager.TYPE_MOBILE, startTime, endTime)
        val wifiTotal = getTotalUsage(ConnectivityManager.TYPE_WIFI, startTime, endTime)

        val usageMap = mutableMapOf<Int, Pair<Long, Long>>() // uid -> (mobile, wifi)

        collectUidUsage(ConnectivityManager.TYPE_MOBILE, startTime, endTime, usageMap, isMobile = true)
        collectUidUsage(ConnectivityManager.TYPE_WIFI, startTime, endTime, usageMap, isMobile = false)

        val topApps = usageMap.mapNotNull { (uid, usage) ->
            if (usage.first + usage.second <= 0) return@mapNotNull null

            val packageNames = packageManager.getPackagesForUid(uid) ?: return@mapNotNull null
            val packageName = packageNames.firstOrNull() ?: return@mapNotNull null

            try {
                val details = getAppDetails(packageName, uid)
                AppUsageInfo(
                    packageName = packageName,
                    appName = details.appName,
                    icon = details.icon,
                    mobileData = usage.first,
                    wifiData = usage.second,
                )
            } catch (_: Throwable) {
                when (uid) {
                    0 -> AppUsageInfo("root", "Sistema (Root)", null, usage.first, usage.second)
                    1000 -> AppUsageInfo("system", "Sistema Android", null, usage.first, usage.second)
                    else -> AppUsageInfo(
                        packageName = packageName,
                        appName = packageName,
                        icon = null,
                        mobileData = usage.first,
                        wifiData = usage.second,
                    )
                }
            }
        }.sortedByDescending { it.totalData }.take(30)

        val dataPoints = generateDataPoints(startTime, endTime)

        TimePeriodStats(
            dataPoints = dataPoints,
            totalMobile = mobileTotal,
            totalWifi = wifiTotal,
            topApps = topApps,
        )
    }

    private fun collectUidUsage(
        networkType: Int,
        startTime: Long,
        endTime: Long,
        map: MutableMap<Int, Pair<Long, Long>>,
        isMobile: Boolean,
    ) {
        val manager = networkStatsManager ?: return
        try {
            val stats = manager.querySummary(networkType, null, startTime, endTime)
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val uid = bucket.uid
                val current = map[uid] ?: (0L to 0L)
                val rxTx = bucket.rxBytes + bucket.txBytes

                map[uid] = if (isMobile) {
                    (current.first + rxTx) to current.second
                } else {
                    current.first to (current.second + rxTx)
                }
            }
            stats.close()
        } catch (_: Throwable) {
            // Ignore
        }
    }

    private fun getTotalUsage(networkType: Int, startTime: Long, endTime: Long): Long {
        val manager = networkStatsManager ?: return 0L
        return try {
            val bucket = manager.querySummaryForDevice(networkType, null, startTime, endTime)
            bucket.rxBytes + bucket.txBytes
        } catch (_: Throwable) {
            0L
        }
    }

    private fun generateDataPoints(startTime: Long, endTime: Long): List<DataPoint> {
        val points = mutableListOf<DataPoint>()
        val duration = endTime - startTime

        // If duration is roughly 24 hours or less, use hourly breakdown
        if (duration <= 25 * 60 * 60 * 1000L) {
            val hourFormat = SimpleDateFormat("HH'h'", Locale.getDefault())
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = startTime

            for (i in 0 until 24) {
                val start = calendar.timeInMillis
                val label = hourFormat.format(calendar.time)
                calendar.add(Calendar.HOUR_OF_DAY, 1)
                val end = calendar.timeInMillis

                if (start > endTime) break

                val mobile = getTotalUsage(ConnectivityManager.TYPE_MOBILE, start, minOf(end, endTime))
                val wifi = getTotalUsage(ConnectivityManager.TYPE_WIFI, start, minOf(end, endTime))
                val segments = getAppSegmentsForInterval(start, minOf(end, endTime), mobile + wifi)
                points.add(DataPoint(start, label, mobile, wifi, segments))
            }
        } else {
            // For longer periods, use daily breakdown
            val dayFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = startTime
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            while (calendar.timeInMillis < endTime) {
                val start = calendar.timeInMillis
                val label = dayFormat.format(calendar.time)
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                val end = calendar.timeInMillis

                val mobile = getTotalUsage(ConnectivityManager.TYPE_MOBILE, maxOf(start, startTime), minOf(end, endTime))
                val wifi = getTotalUsage(ConnectivityManager.TYPE_WIFI, maxOf(start, startTime), minOf(end, endTime))
                val segments = getAppSegmentsForInterval(maxOf(start, startTime), minOf(end, endTime), mobile + wifi)
                points.add(DataPoint(start, label, mobile, wifi, segments))
            }
        }
        return points
    }
}
