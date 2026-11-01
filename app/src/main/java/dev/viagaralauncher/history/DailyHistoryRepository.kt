// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.history

import android.app.ActivityManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import dev.viagaralauncher.root.AppRootInspector
import dev.viagaralauncher.root.anomaly.AnomalyHistoryRepository
import dev.viagaralauncher.root.anomaly.AnomalyType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository responsible for gathering, computing, and persisting daily usage telemetry.
 */
class DailyHistoryRepository private constructor(private val context: Context) {

    companion object {
        private const val DIR_NAME = "daily_history"
        private const val FILE_NAME = "records.json"
        private const val PREFS_NAME = "daily_history_internal_prefs"
        private const val KEY_LAST_BATTERY_LEVEL = "key_last_battery_level"
        private const val KEY_LAST_BATTERY_STATUS = "key_last_battery_status"

        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

        @Volatile
        private var instance: DailyHistoryRepository? = null

        fun getInstance(context: Context): DailyHistoryRepository {
            return instance ?: synchronized(this) {
                instance ?: DailyHistoryRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val packageManager: PackageManager = context.packageManager
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
    private val internalPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val dataFile: File by lazy {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        File(dir, FILE_NAME)
    }

    private val memoryRecords = ConcurrentHashMap<String, DailyUsageRecord>()

    private val _recordsFlow = MutableStateFlow<Map<String, DailyUsageRecord>>(emptyMap())
    val recordsFlow: StateFlow<Map<String, DailyUsageRecord>> = _recordsFlow.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    handleBatteryChanged(intent)
                }
                Intent.ACTION_SHUTDOWN -> {
                    recordShutdownToday()
                }
            }
        }
    }

    init {
        loadRecordsFromDisk()
        registerBatteryMonitor()
        // Compute today's telemetry on initialization
        scope.launch {
            refreshDay(LocalDate.now())
        }
    }

    private fun registerBatteryMonitor() {
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_SHUTDOWN)
            }
            context.registerReceiver(batteryReceiver, filter)
        }
    }

    private fun handleBatteryChanged(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (level < 0) return

        val prevLevel = internalPrefs.getInt(KEY_LAST_BATTERY_LEVEL, -1)
        val prevStatus = internalPrefs.getInt(KEY_LAST_BATTERY_STATUS, -1)

        val isDischarging = status == BatteryManager.BATTERY_STATUS_DISCHARGING ||
            status == BatteryManager.BATTERY_STATUS_NOT_CHARGING

        if (prevLevel != -1 && isDischarging && prevStatus == status && level < prevLevel) {
            val delta = prevLevel - level
            if (delta in 1..20) {
                recordBatteryDrainToday(delta)
            }
        }

        internalPrefs.edit()
            .putInt(KEY_LAST_BATTERY_LEVEL, level)
            .putInt(KEY_LAST_BATTERY_STATUS, status)
            .apply()
    }

    private fun loadRecordsFromDisk() {
        scope.launch {
            if (!dataFile.exists()) return@launch
            val content = runCatching { dataFile.readText(Charsets.UTF_8) }.getOrNull() ?: return@launch
            val map = parseRecordsJson(content)
            memoryRecords.putAll(map)
            _recordsFlow.value = HashMap(memoryRecords)
        }
    }

    private fun saveRecordsToDisk() {
        scope.launch {
            runCatching {
                val json = serializeRecordsJson(memoryRecords.values.toList())
                dataFile.writeText(json, Charsets.UTF_8)
            }
        }
    }

    fun recordBatteryDrainToday(deltaPercent: Int) {
        val todayKey = LocalDate.now().format(DATE_FORMATTER)
        val existing = memoryRecords[todayKey] ?: DailyUsageRecord(
            dateKey = todayKey,
            timestamp = System.currentTimeMillis(),
        )
        val updated = existing.copy(
            batteryDrainPercent = existing.batteryDrainPercent + deltaPercent,
        )
        memoryRecords[todayKey] = updated
        _recordsFlow.value = HashMap(memoryRecords)
        saveRecordsToDisk()
    }

    fun recordRamSample(ramPercent: Double) {
        if (ramPercent <= 0.0) return
        val todayKey = LocalDate.now().format(DATE_FORMATTER)
        val existing = memoryRecords[todayKey] ?: DailyUsageRecord(
            dateKey = todayKey,
            timestamp = System.currentTimeMillis(),
        )
        val newCount = existing.ramSamplesCount + 1
        val newAvg = ((existing.averageRamPercent * existing.ramSamplesCount) + ramPercent) / newCount
        val updated = existing.copy(
            averageRamPercent = (newAvg * 10.0).toInt() / 10.0,
            ramSamplesCount = newCount,
        )
        memoryRecords[todayKey] = updated
        _recordsFlow.value = HashMap(memoryRecords)
    }

    fun recordRebootToday() {
        val todayKey = LocalDate.now().format(DATE_FORMATTER)
        val existing = memoryRecords[todayKey] ?: DailyUsageRecord(
            dateKey = todayKey,
            timestamp = System.currentTimeMillis(),
        )
        val updated = existing.copy(rebootCount = existing.rebootCount + 1)
        memoryRecords[todayKey] = updated
        _recordsFlow.value = HashMap(memoryRecords)
        saveRecordsToDisk()
    }

    fun recordShutdownToday() {
        val todayKey = LocalDate.now().format(DATE_FORMATTER)
        val existing = memoryRecords[todayKey] ?: DailyUsageRecord(
            dateKey = todayKey,
            timestamp = System.currentTimeMillis(),
        )
        val updated = existing.copy(shutdownCount = existing.shutdownCount + 1)
        memoryRecords[todayKey] = updated
        _recordsFlow.value = HashMap(memoryRecords)
        saveRecordsToDisk()
    }

    /**
     * Recomputes and enriches telemetry for the given date.
     */
    suspend fun refreshDay(date: LocalDate): DailyUsageRecord = withContext(Dispatchers.IO) {
        val dateKey = date.format(DATE_FORMATTER)
        val existing = memoryRecords[dateKey]

        val zoneId = ZoneId.systemDefault()
        val startOfDay = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val isToday = date == LocalDate.now()
        val endOfDay = if (isToday) {
            System.currentTimeMillis()
        } else {
            date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L
        }

        // 1. Gather internet usage (Wi-Fi, Mobile, and 24-hour distribution)
        val (totalWifi, totalMobile, hourlyNet) = computeNetworkForDay(date, startOfDay, endOfDay)

        // 2. Gather App Launches, Screen On Time, Reboots, Shutdowns from UsageEvents
        val eventStats = computeUsageEventsForDay(startOfDay, endOfDay)

        // 3. Compute Top Battery Consumers for the day
        val batteryConsumers = computeBatteryConsumers(date, eventStats.appScreenTimes, eventStats.appLaunches)

        // 4. Background anomalies from AnomalyHistoryRepository for this day
        val anomalyRepo = AnomalyHistoryRepository.getInstance(context)
        val anomaliesForDay = anomalyRepo.events.value.filter { it.timestamp in startOfDay..endOfDay }
        val anomalyCount = anomaliesForDay.size
        val deepSleepCount = anomaliesForDay.count { it.type == AnomalyType.DEEP_SLEEP }

        // 5. Merge with existing recorded battery drain and RAM samples
        val battDrain = if (existing != null && existing.batteryDrainPercent > 0) {
            existing.batteryDrainPercent
        } else {
            // Fallback estimation based on battery consumer proportions or SOT
            val estimatedDrain = (eventStats.screenOnTimeMs / (1000L * 60L * 4L)).toInt().coerceIn(10, 100)
            if (isToday) 0 else estimatedDrain
        }

        val ramAvg = if (existing != null && existing.ramSamplesCount > 0) {
            existing.averageRamPercent
        } else {
            readCurrentSystemRamPercent()
        }

        val record = DailyUsageRecord(
            dateKey = dateKey,
            timestamp = startOfDay,
            batteryDrainPercent = battDrain,
            batteryDrainMah = existing?.batteryDrainMah,
            screenOnTimeMs = eventStats.screenOnTimeMs,
            screenOffTimeMs = eventStats.screenOffTimeMs,
            rebootCount = maxOf(existing?.rebootCount ?: 0, eventStats.rebootCount),
            shutdownCount = maxOf(existing?.shutdownCount ?: 0, eventStats.shutdownCount),
            averageRamPercent = ramAvg,
            ramSamplesCount = maxOf(existing?.ramSamplesCount ?: 0, 1),
            totalWifiBytes = totalWifi,
            totalMobileBytes = totalMobile,
            hourlyNetUsage = hourlyNet,
            appLaunches = eventStats.appLaunches,
            topBatteryConsumers = batteryConsumers,
            backgroundAnomaliesCount = maxOf(existing?.backgroundAnomaliesCount ?: 0, anomalyCount),
            deepSleepDisruptionsCount = maxOf(existing?.deepSleepDisruptionsCount ?: 0, deepSleepCount),
        )

        memoryRecords[dateKey] = record
        _recordsFlow.value = HashMap(memoryRecords)
        saveRecordsToDisk()
        record
    }

    /**
     * Precomputes or loads all days for the specified month.
     */
    suspend fun loadMonthRecords(year: Int, month: Int): List<DailyUsageRecord> = withContext(Dispatchers.IO) {
        _isRefreshing.value = true
        val list = mutableListOf<DailyUsageRecord>()
        val now = LocalDate.now()
        val firstDay = LocalDate.of(year, month, 1)
        val lengthOfMonth = firstDay.lengthOfMonth()

        for (day in 1..lengthOfMonth) {
            val date = LocalDate.of(year, month, day)
            if (date.isAfter(now)) break

            val dateKey = date.format(DATE_FORMATTER)
            val existing = memoryRecords[dateKey]
            if (existing != null && !date.isEqual(now) && existing.appLaunches.isNotEmpty()) {
                list.add(existing)
            } else {
                val computed = refreshDay(date)
                list.add(computed)
            }
        }
        _isRefreshing.value = false
        list.sortedByDescending { it.timestamp }
    }

    private data class UsageEventComputation(
        val screenOnTimeMs: Long,
        val screenOffTimeMs: Long,
        val rebootCount: Int,
        val shutdownCount: Int,
        val appLaunches: List<AppDailyLaunchStat>,
        val appScreenTimes: Map<String, Long>,
    )

    private fun computeUsageEventsForDay(startTimeMs: Long, endTimeMs: Long): UsageEventComputation {
        val usm = usageStatsManager ?: return UsageEventComputation(0L, 0L, 0, 0, emptyList(), emptyMap())

        val launchCounts = mutableMapOf<String, Int>()
        val screenTimes = mutableMapOf<String, Long>()
        var screenOnTime = 0L
        var screenOffTime = 0L
        var reboots = 0
        var shutdowns = 0

        var lastInteractiveTimestamp = -1L
        var lastResumedPkg: String? = null
        var lastResumedTime = -1L

        runCatching {
            val events = usm.queryEvents(startTimeMs, endTimeMs)
            val event = UsageEvents.Event()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val eventType = event.eventType
                val pkg = event.packageName
                val time = event.timeStamp

                when (eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED, 1 /* MOVE_TO_FOREGROUND */ -> {
                        if (!pkg.isNullOrBlank()) {
                            launchCounts[pkg] = (launchCounts[pkg] ?: 0) + 1
                            if (lastResumedPkg != null && lastResumedTime > 0) {
                                val duration = (time - lastResumedTime).coerceAtLeast(0L)
                                screenTimes[lastResumedPkg] = (screenTimes[lastResumedPkg] ?: 0L) + duration
                            }
                            lastResumedPkg = pkg
                            lastResumedTime = time
                        }
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED, 2 /* MOVE_TO_BACKGROUND */ -> {
                        if (lastResumedPkg != null && lastResumedTime > 0) {
                            val duration = (time - lastResumedTime).coerceAtLeast(0L)
                            screenTimes[lastResumedPkg] = (screenTimes[lastResumedPkg] ?: 0L) + duration
                            lastResumedPkg = null
                            lastResumedTime = -1L
                        }
                    }
                    UsageEvents.Event.SCREEN_INTERACTIVE, 15 -> {
                        lastInteractiveTimestamp = time
                    }
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE, 16 -> {
                        if (lastInteractiveTimestamp > 0) {
                            val duration = (time - lastInteractiveTimestamp).coerceAtLeast(0L)
                            screenOnTime += duration
                            lastInteractiveTimestamp = -1L
                        }
                    }
                    UsageEvents.Event.DEVICE_STARTUP, 27 -> {
                        reboots++
                    }
                    UsageEvents.Event.DEVICE_SHUTDOWN, 26 -> {
                        shutdowns++
                    }
                }
            }

            if (lastInteractiveTimestamp > 0) {
                screenOnTime += (endTimeMs - lastInteractiveTimestamp).coerceAtLeast(0L)
            }
        }

        // Cross-check with queryUsageStats for any missing foreground time
        runCatching {
            val statsList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTimeMs, endTimeMs)
            for (st in statsList) {
                val pkg = st.packageName ?: continue
                val fgTime = st.totalTimeInForeground
                if (fgTime > 0) {
                    val current = screenTimes[pkg] ?: 0L
                    if (fgTime > current) {
                        screenTimes[pkg] = fgTime
                    }
                }
            }
            if (screenOnTime == 0L && screenTimes.isNotEmpty()) {
                screenOnTime = screenTimes.values.sum()
            }
        }

        val totalWindowMs = (endTimeMs - startTimeMs).coerceAtLeast(0L)
        screenOffTime = (totalWindowMs - screenOnTime).coerceAtLeast(0L)

        // Build sorted launch stats
        val allPkgs = (launchCounts.keys + screenTimes.keys).filter { it.isNotBlank() }
        val appLaunches = allPkgs.mapNotNull { pkg ->
            val count = launchCounts[pkg] ?: 0
            val time = screenTimes[pkg] ?: 0L
            if (count == 0 && time < 1000L) return@mapNotNull null

            val appName = runCatching {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            }.getOrDefault(pkg)

            AppDailyLaunchStat(
                packageName = pkg,
                appName = appName,
                launchCount = count,
                screenTimeMs = time,
            )
        }.sortedByDescending { it.launchCount * 1000 + (it.screenTimeMs / 1000) }

        return UsageEventComputation(
            screenOnTimeMs = screenOnTime,
            screenOffTimeMs = screenOffTime,
            rebootCount = reboots,
            shutdownCount = shutdowns,
            appLaunches = appLaunches,
            appScreenTimes = screenTimes,
        )
    }

    private fun computeNetworkForDay(
        date: LocalDate,
        startOfDay: Long,
        endOfDay: Long,
    ): Triple<Long, Long, List<HourlyNetUsagePoint>> {
        val nsm = networkStatsManager ?: return Triple(0L, 0L, defaultHourlyPoints())

        val totalWifi = getNetworkUsageForBucket(ConnectivityManager.TYPE_WIFI, startOfDay, endOfDay)
        val totalMobile = getNetworkUsageForBucket(ConnectivityManager.TYPE_MOBILE, startOfDay, endOfDay)

        val zoneId = ZoneId.systemDefault()
        val hourlyList = mutableListOf<HourlyNetUsagePoint>()

        for (hour in 0..23) {
            val label = String.format(Locale.US, "%02dh", hour)
            val hStart = date.atTime(hour, 0).atZone(zoneId).toInstant().toEpochMilli()
            val hEnd = date.atTime(hour, 59, 59, 999_000_000).atZone(zoneId).toInstant().toEpochMilli()

            if (hStart > endOfDay) {
                hourlyList.add(HourlyNetUsagePoint(hour, label, 0L, 0L))
            } else {
                val validEnd = minOf(hEnd, endOfDay)
                val wifi = getNetworkUsageForBucket(ConnectivityManager.TYPE_WIFI, hStart, validEnd)
                val mobile = getNetworkUsageForBucket(ConnectivityManager.TYPE_MOBILE, hStart, validEnd)
                hourlyList.add(HourlyNetUsagePoint(hour, label, wifi, mobile))
            }
        }

        return Triple(totalWifi, totalMobile, hourlyList)
    }

    private fun getNetworkUsageForBucket(networkType: Int, start: Long, end: Long): Long {
        val nsm = networkStatsManager ?: return 0L
        if (start >= end) return 0L
        return runCatching {
            val bucket = nsm.querySummaryForDevice(networkType, null, start, end)
            bucket.rxBytes + bucket.txBytes
        }.getOrDefault(0L)
    }

    private fun defaultHourlyPoints(): List<HourlyNetUsagePoint> {
        return (0..23).map { hour ->
            HourlyNetUsagePoint(hour, String.format(Locale.US, "%02dh", hour))
        }
    }

    private fun computeBatteryConsumers(
        date: LocalDate,
        appScreenTimes: Map<String, Long>,
        appLaunches: List<AppDailyLaunchStat>,
    ): List<DailyBatteryConsumer> {
        val totalScreenTime = appScreenTimes.values.sum().coerceAtLeast(1L)
        val list = mutableListOf<DailyBatteryConsumer>()

        for (launch in appLaunches.take(15)) {
            val time = launch.screenTimeMs
            if (time <= 0L) continue
            val ratio = (time.toDouble() / totalScreenTime.toDouble()).coerceIn(0.01, 1.0)
            val drainPct = (ratio * 100.0 * 10.0).toInt() / 10.0
            list.add(
                DailyBatteryConsumer(
                    packageName = launch.packageName,
                    appName = launch.appName,
                    drainPercent = drainPct,
                )
            )
        }
        return list.sortedByDescending { it.drainPercent }.take(10)
    }

    private fun readCurrentSystemRamPercent(): Double {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 50.0
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val total = memInfo.totalMem.toDouble()
        val avail = memInfo.availMem.toDouble()
        if (total <= 0.0) return 50.0
        val usedPct = ((total - avail) / total) * 100.0
        return (usedPct * 10.0).toInt() / 10.0
    }

    private fun serializeRecordsJson(records: List<DailyUsageRecord>): String {
        val array = JSONArray()
        records.forEach { array.put(it.toJson()) }
        return array.toString()
    }

    private fun parseRecordsJson(jsonStr: String): Map<String, DailyUsageRecord> {
        val map = mutableMapOf<String, DailyUsageRecord>()
        runCatching {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val record = DailyUsageRecord.fromJson(obj)
                if (record.dateKey.isNotBlank()) {
                    map[record.dateKey] = record
                }
            }
        }
        return map
    }

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        serializeRecordsJson(memoryRecords.values.toList())
    }

    suspend fun importJson(jsonStr: String) = withContext(Dispatchers.IO) {
        val map = parseRecordsJson(jsonStr)
        if (map.isNotEmpty()) {
            memoryRecords.putAll(map)
            _recordsFlow.value = HashMap(memoryRecords)
            saveRecordsToDisk()
        }
    }
}
