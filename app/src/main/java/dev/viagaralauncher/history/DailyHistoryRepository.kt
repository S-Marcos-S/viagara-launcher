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
import android.os.PowerManager
import dev.viagaralauncher.root.AppRootInspector
import dev.viagaralauncher.root.anomaly.AnomalyHistoryRepository
import dev.viagaralauncher.root.anomaly.AnomalyType
import dev.viagaralauncher.battery.AdvancedDrainTracker
import dev.viagaralauncher.battery.BatteryCycleRepository
import dev.viagaralauncher.battery.CycleType
import kotlin.math.abs
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
                Intent.ACTION_SCREEN_ON -> {
                    sampleBatteryTimelinePoint(isScreenOn = true)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    sampleBatteryTimelinePoint(isScreenOn = false)
                }
            }
        }
    }

    init {
        loadRecordsFromDiskSync()
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
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(batteryReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(batteryReceiver, filter)
            }
        }
    }

    private fun getCurrentBatteryLevel(): Int {
        val intent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level >= 0 && scale > 0) (level * 100) / scale else 50
    }

    private fun sampleBatteryTimelinePoint(isScreenOn: Boolean) {
        val level = getCurrentBatteryLevel()
        val state = if (isScreenOn) BatteryTimelinePoint.STATE_SCREEN_ON else BatteryTimelinePoint.STATE_DEEP_SLEEP
        recordBatteryTimelinePoint(level, state)
    }

    private fun handleBatteryChanged(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (level < 0) return

        val pct = if (scale > 0 && level >= 0) (level * 100) / scale else level
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isScreenOn = pm?.isInteractive ?: false
        val state = if (isScreenOn) BatteryTimelinePoint.STATE_SCREEN_ON else BatteryTimelinePoint.STATE_DEEP_SLEEP
        if (pct in 0..100) {
            recordBatteryTimelinePoint(pct, state)
        }

        val prevLevel = internalPrefs.getInt(KEY_LAST_BATTERY_LEVEL, -1)
        val prevStatus = internalPrefs.getInt(KEY_LAST_BATTERY_STATUS, -1)

        val isDischarging = status == BatteryManager.BATTERY_STATUS_DISCHARGING ||
            status == BatteryManager.BATTERY_STATUS_NOT_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_UNKNOWN

        val wasDischarging = prevStatus == BatteryManager.BATTERY_STATUS_DISCHARGING ||
            prevStatus == BatteryManager.BATTERY_STATUS_NOT_CHARGING ||
            prevStatus == BatteryManager.BATTERY_STATUS_UNKNOWN

        if (prevLevel != -1 && isDischarging && wasDischarging && level < prevLevel) {
            val delta = prevLevel - level
            if (delta in 1..25) {
                recordBatteryDrainToday(delta)
            }
        }

        internalPrefs.edit()
            .putInt(KEY_LAST_BATTERY_LEVEL, level)
            .putInt(KEY_LAST_BATTERY_STATUS, status)
            .apply()
    }

    private fun loadRecordsFromDiskSync() {
        if (!dataFile.exists()) return
        val content = runCatching { dataFile.readText(Charsets.UTF_8) }.getOrNull() ?: return
        val map = parseRecordsJson(content)
        val ignored = getIgnoredLauncherPackages()
        val sanitized = map.mapValues { (_, rec) -> sanitizeRecord(rec, ignored) }
        memoryRecords.putAll(sanitized)
        _recordsFlow.value = HashMap(memoryRecords)
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
        val capacity = runCatching { AdvancedDrainTracker.getInstance(context).drainState.value.batteryCapacityMah }.getOrDefault(4000.0)
        val newDrain = existing.batteryDrainPercent + deltaPercent
        val updated = existing.copy(
            batteryDrainPercent = newDrain,
            batteryDrainMah = ((newDrain / 100.0) * capacity).toLong(),
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
        val level = getCurrentBatteryLevel()
        recordBatteryTimelinePoint(level, BatteryTimelinePoint.STATE_OFF)
        val updated = existing.copy(shutdownCount = existing.shutdownCount + 1)
        memoryRecords[todayKey] = updated
        _recordsFlow.value = HashMap(memoryRecords)
        saveRecordsToDisk()
    }

    fun recordBatteryTimelinePoint(level: Int, state: Int, timestamp: Long = System.currentTimeMillis()) {
        val todayKey = LocalDate.now().format(DATE_FORMATTER)
        val existing = memoryRecords[todayKey] ?: DailyUsageRecord(
            dateKey = todayKey,
            timestamp = timestamp,
        )
        val currentTimeline = existing.batteryTimeline.toMutableList()
        val lastPoint = currentTimeline.lastOrNull()

        val shouldAdd = lastPoint == null ||
            lastPoint.state != state ||
            lastPoint.batteryLevel != level ||
            (timestamp - lastPoint.timestamp) >= 180_000L

        if (shouldAdd) {
            currentTimeline.add(BatteryTimelinePoint(timestamp, level.coerceIn(0, 100), state))
            val trimmed = if (currentTimeline.size > 288) currentTimeline.takeLast(288) else currentTimeline
            val updated = existing.copy(batteryTimeline = trimmed)
            memoryRecords[todayKey] = updated
            _recordsFlow.value = HashMap(memoryRecords)
            saveRecordsToDisk()
        }
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

        // 5. Merge with recorded battery drain, cycles repository, and tracker telemetry
        val cyclesRepo = runCatching { BatteryCycleRepository.getInstance(context) }.getOrNull()
        val todayCycles = cyclesRepo?.cyclesFlow?.value?.filter {
            it.type == CycleType.DISCHARGE &&
                ((it.startTimeMs in startOfDay..endOfDay) || (it.endTimeMs in startOfDay..endOfDay) || (it.isActive && it.startTimeMs <= endOfDay))
        }
        val todayCyclesDrain = todayCycles?.sumOf {
            if (it.batteryDeltaPercent > 0) it.batteryDeltaPercent
            else abs(it.startBatteryLevel - it.endBatteryLevel)
        } ?: 0

        val drainTracker = runCatching { AdvancedDrainTracker.getInstance(context) }.getOrNull()
        val trackerDrainMah = drainTracker?.drainState?.value?.totalDrainMah ?: 0.0
        val capacity = drainTracker?.drainState?.value?.batteryCapacityMah ?: 4000.0
        val trackerDrainPct = if (capacity > 0 && trackerDrainMah > 0) ((trackerDrainMah / capacity) * 100.0).toInt() else 0

        val sotHours = eventStats.screenOnTimeMs / 3600000.0
        val soffHours = eventStats.screenOffTimeMs / 3600000.0
        val estimatedDrain = ((sotHours * 12.0) + (soffHours * 1.0)).toInt().coerceIn(0, 100)

        val existingDrain = existing?.batteryDrainPercent ?: 0
        val calculatedDrain = maxOf(
            existingDrain,
            todayCyclesDrain,
            trackerDrainPct,
            if (eventStats.screenOnTimeMs > 60_000L) estimatedDrain else 0
        )
        val battDrain = if (calculatedDrain > 0) calculatedDrain else existingDrain
        val battMah = existing?.batteryDrainMah ?: ((battDrain / 100.0) * capacity).toLong()

        val ramAvg = if (existing != null && existing.ramSamplesCount > 0) {
            existing.averageRamPercent
        } else {
            readCurrentSystemRamPercent()
        }

        val batteryTimeline = computeBatteryTimelineForDay(
            date = date,
            startOfDay = startOfDay,
            endOfDay = endOfDay,
            existingTimeline = existing?.batteryTimeline.orEmpty(),
            calculatedDrain = battDrain,
        )

        val record = DailyUsageRecord(
            dateKey = dateKey,
            timestamp = startOfDay,
            batteryDrainPercent = battDrain,
            batteryDrainMah = battMah,
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
            batteryTimeline = batteryTimeline,
        )

        memoryRecords[dateKey] = record
        _recordsFlow.value = HashMap(memoryRecords)
        saveRecordsToDisk()
        record
    }

    private fun computeBatteryTimelineForDay(
        date: LocalDate,
        startOfDay: Long,
        endOfDay: Long,
        existingTimeline: List<BatteryTimelinePoint>,
        calculatedDrain: Int,
    ): List<BatteryTimelinePoint> {
        val points = mutableListOf<BatteryTimelinePoint>()

        data class StateInterval(val start: Long, val end: Long, val state: Int)
        val stateIntervals = mutableListOf<StateInterval>()

        val usm = usageStatsManager
        if (usm != null) {
            runCatching {
                val events = usm.queryEvents(startOfDay, endOfDay)
                val event = UsageEvents.Event()
                var lastInteractiveTime = -1L
                var lastShutdownTime = -1L

                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    val time = event.timeStamp
                    when (event.eventType) {
                        UsageEvents.Event.SCREEN_INTERACTIVE, 15 -> {
                            if (lastShutdownTime > 0) {
                                stateIntervals.add(StateInterval(lastShutdownTime, time, BatteryTimelinePoint.STATE_OFF))
                                lastShutdownTime = -1L
                            }
                            lastInteractiveTime = time
                        }
                        UsageEvents.Event.SCREEN_NON_INTERACTIVE, 16 -> {
                            if (lastInteractiveTime > 0) {
                                stateIntervals.add(StateInterval(lastInteractiveTime, time, BatteryTimelinePoint.STATE_SCREEN_ON))
                                lastInteractiveTime = -1L
                            }
                        }
                        UsageEvents.Event.DEVICE_SHUTDOWN, 26 -> {
                            if (lastInteractiveTime > 0) {
                                stateIntervals.add(StateInterval(lastInteractiveTime, time, BatteryTimelinePoint.STATE_SCREEN_ON))
                                lastInteractiveTime = -1L
                            }
                            lastShutdownTime = time
                        }
                        UsageEvents.Event.DEVICE_STARTUP, 27 -> {
                            if (lastShutdownTime > 0) {
                                stateIntervals.add(StateInterval(lastShutdownTime, time, BatteryTimelinePoint.STATE_OFF))
                                lastShutdownTime = -1L
                            }
                        }
                    }
                }

                if (lastInteractiveTime > 0) {
                    stateIntervals.add(StateInterval(lastInteractiveTime, endOfDay, BatteryTimelinePoint.STATE_SCREEN_ON))
                }
                if (lastShutdownTime > 0) {
                    stateIntervals.add(StateInterval(lastShutdownTime, endOfDay, BatteryTimelinePoint.STATE_OFF))
                }
            }
        }

        fun stateAt(time: Long): Int {
            for (interval in stateIntervals) {
                if (time in interval.start..interval.end) {
                    return interval.state
                }
            }
            return BatteryTimelinePoint.STATE_DEEP_SLEEP
        }

        val validExisting = existingTimeline.filter { it.timestamp in startOfDay..endOfDay }
        for (pt in validExisting) {
            val s = stateAt(pt.timestamp)
            val finalState = if (s == BatteryTimelinePoint.STATE_OFF) BatteryTimelinePoint.STATE_OFF else pt.state
            points.add(pt.copy(state = finalState))
        }

        val cyclesRepo = runCatching { BatteryCycleRepository.getInstance(context) }.getOrNull()
        val cycles = cyclesRepo?.cyclesFlow?.value?.filter {
            (it.startTimeMs in startOfDay..endOfDay) || (it.endTimeMs in startOfDay..endOfDay) || (it.isActive && it.startTimeMs <= endOfDay)
        }.orEmpty()

        for (cycle in cycles) {
            for (tp in cycle.telemetryPoints) {
                if (tp.timestamp in startOfDay..endOfDay) {
                    val s = stateAt(tp.timestamp)
                    val state = if (s == BatteryTimelinePoint.STATE_OFF) BatteryTimelinePoint.STATE_OFF
                    else if (tp.isScreenOn) BatteryTimelinePoint.STATE_SCREEN_ON
                    else s
                    points.add(BatteryTimelinePoint(tp.timestamp, tp.batteryLevel, state))
                }
            }
        }

        points.sortBy { it.timestamp }

        val deduped = mutableListOf<BatteryTimelinePoint>()
        for (p in points) {
            val last = deduped.lastOrNull()
            if (last == null || p.timestamp - last.timestamp >= 60_000L || last.state != p.state || abs(last.batteryLevel - p.batteryLevel) >= 2) {
                deduped.add(p)
            }
        }

        if (deduped.size >= 2) {
            val first = deduped.first()
            if (first.timestamp > startOfDay + 300_000L) {
                deduped.add(0, BatteryTimelinePoint(startOfDay, first.batteryLevel, stateAt(startOfDay)))
            }
            val last = deduped.last()
            if (last.timestamp < endOfDay - 300_000L) {
                deduped.add(BatteryTimelinePoint(endOfDay, last.batteryLevel, stateAt(endOfDay)))
            }
            return deduped
        }

        val synthesized = mutableListOf<BatteryTimelinePoint>()
        val startLevel = 100
        val endLevel = (100 - calculatedDrain).coerceIn(5, 100)
        val stepMs = 30 * 60_000L
        var curTime = startOfDay

        while (curTime <= endOfDay) {
            val ratio = ((curTime - startOfDay).toDouble() / (endOfDay - startOfDay).coerceAtLeast(1L)).coerceIn(0.0, 1.0)
            val currentLevel = (startLevel - (ratio * (startLevel - endLevel))).toInt().coerceIn(0, 100)
            val state = stateAt(curTime)
            synthesized.add(BatteryTimelinePoint(curTime, currentLevel, state))
            curTime += stepMs
        }

        return synthesized
    }

    /**
     * Resolves and caches home launchers and system shell packages to prevent them
     * from falsely registering as opened apps in daily usage metrics.
     */
    private fun getIgnoredLauncherPackages(): Set<String> {
        val launcherPkgs = mutableSetOf<String>()
        launcherPkgs.add(context.packageName)
        launcherPkgs.add("com.motorola.launcher3")
        launcherPkgs.add("com.google.android.apps.nexuslauncher")
        launcherPkgs.add("com.android.launcher")
        launcherPkgs.add("com.android.launcher3")
        launcherPkgs.add("com.sec.android.app.launcher")
        launcherPkgs.add("com.miui.home")
        launcherPkgs.add("com.mi.android.globallauncher")
        launcherPkgs.add("com.oppo.launcher")
        launcherPkgs.add("com.huawei.android.launcher")
        launcherPkgs.add("com.android.systemui")
        launcherPkgs.add("com.google.android.googlequicksearchbox")

        runCatching {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolves = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(homeIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(homeIntent, 0)
            }
            for (resolve in resolves) {
                val pkg = resolve.activityInfo?.packageName
                if (!pkg.isNullOrBlank()) {
                    launcherPkgs.add(pkg)
                }
            }
        }
        return launcherPkgs
    }

    private fun sanitizeRecord(record: DailyUsageRecord, ignoredPkgs: Set<String>): DailyUsageRecord {
        val filteredLaunches = record.appLaunches.filter { !ignoredPkgs.contains(it.packageName) }
        return if (filteredLaunches.size != record.appLaunches.size) {
            record.copy(appLaunches = filteredLaunches)
        } else {
            record
        }
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
        val ignoredPkgs = getIgnoredLauncherPackages()

        for (day in 1..lengthOfMonth) {
            val date = LocalDate.of(year, month, day)
            if (date.isAfter(now)) break

            val dateKey = date.format(DATE_FORMATTER)
            val existing = memoryRecords[dateKey]
            if (existing != null && !date.isEqual(now) && existing.appLaunches.isNotEmpty() && existing.batteryTimeline.isNotEmpty()) {
                val sanitized = sanitizeRecord(existing, ignoredPkgs)
                list.add(sanitized)
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
        val ignoredPkgs = getIgnoredLauncherPackages()

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
                            if (!ignoredPkgs.contains(pkg)) {
                                launchCounts[pkg] = (launchCounts[pkg] ?: 0) + 1
                            }
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

        // Build sorted launch stats, excluding launchers and system shell packages
        val allPkgs = (launchCounts.keys + screenTimes.keys).filter {
            it.isNotBlank() && !ignoredPkgs.contains(it)
        }
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
