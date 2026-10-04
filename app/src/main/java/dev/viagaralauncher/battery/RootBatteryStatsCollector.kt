// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.battery

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Collects root and kernel-level battery statistics.
 * Reads sysfs power supply nodes, kernel wakelocks, thermal zones, and CPU frequencies.
 */
object RootBatteryStatsCollector {

    private const val TAG = "RootBatteryStatsCollector"
    private const val ROOT_PROBE_TIMEOUT_MS = 4_000L
    private const val CMD_TIMEOUT_MS = 15_000L
    private const val NEGATIVE_CACHE_MS = 60_000L

    private val rootProbeLock = Mutex()

    @Volatile
    private var cachedRoot: Boolean? = null

    @Volatile
    private var cachedRootAt = 0L

    data class KernelBatteryInfo(
        val technology: String?,
        val cycleCount: Int?,
        val chargeFullDesign: Long?, // μAh or mAh
        val chargeFull: Long?, // μAh or mAh
        val chargeNow: Long?, // μAh
        val currentNow: Long?, // μA
        val voltageNow: Int?, // μV
        val tempNow: Int?, // 0.1°C
        val health: String?,
        val status: String?,
        val capacityLevel: String?,
        val timeToEmptyNow: Long?, // secs
        val timeToFullNow: Long?, // secs
        val batteryAge: Double? // percentage of design capacity
    )

    data class KernelWakelockInfo(
        val name: String,
        val count: Int,
        val expireCount: Int,
        val wakeCount: Int,
        val activeCount: Int,
        val totalTime: Long, // nanosecs
        val sleepTime: Long, // nanosecs
        val maxTime: Long, // nanosecs
        val lastChange: Long // nanosecs
    )

    data class CpuInfo(
        val cluster: Int,
        val currentFreq: Long, // kHz
        val minFreq: Long,
        val maxFreq: Long,
        val governor: String,
        val timeInState: Map<Long, Long> // freq -> time in jiffies
    )

    data class ThermalZone(
        val name: String,
        val type: String,
        val tempMilliC: Int,
        val tripPoints: List<TripPoint> = emptyList()
    )

    data class TripPoint(
        val type: String,
        val tempMilliC: Int
    )

    suspend fun isRootAvailable(): Boolean {
        cachedRoot?.let { cached ->
            if (cached || SystemClock.elapsedRealtime() - cachedRootAt < NEGATIVE_CACHE_MS) return cached
        }
        return rootProbeLock.withLock {
            cachedRoot?.let { cached ->
                if (cached || SystemClock.elapsedRealtime() - cachedRootAt < NEGATIVE_CACHE_MS) {
                    return@withLock cached
                }
            }
            val available = withContext(Dispatchers.IO) {
                exec("id", ROOT_PROBE_TIMEOUT_MS)?.contains("uid=0") == true
            }
            cachedRoot = available
            cachedRootAt = SystemClock.elapsedRealtime()
            available
        }
    }

    fun invalidateRootCache() {
        cachedRoot = null
        cachedRootAt = 0L
    }

    suspend fun resetBatteryStats(): Boolean = withContext(Dispatchers.IO) {
        val result = exec("dumpsys batterystats --reset", CMD_TIMEOUT_MS) ?: return@withContext false
        result.contains("Battery stats reset") || result.isBlank()
    }

    suspend fun getKernelBatteryInfo(): KernelBatteryInfo? = withContext(Dispatchers.IO) {
        try {
            val batteryPath = "/sys/class/power_supply/battery"
            val hasRoot = isRootAvailable()

            fun readFile(name: String): String? {
                try {
                    val f = File("$batteryPath/$name")
                    if (f.exists() && f.canRead()) {
                        val text = f.readText().trim()
                        if (text.isNotBlank()) return text
                    }
                } catch (_: Exception) {}

                if (hasRoot) {
                    val rootText = exec("cat $batteryPath/$name", 2000L)?.trim()
                    if (!rootText.isNullOrBlank() && !rootText.contains("No such file")) {
                        return rootText
                    }
                }
                return null
            }

            val chargeFullDesign = readFile("charge_full_design")?.toLongOrNull()
            val chargeFull = readFile("charge_full")?.toLongOrNull()

            KernelBatteryInfo(
                technology = readFile("technology"),
                cycleCount = readFile("cycle_count")?.toIntOrNull(),
                chargeFullDesign = chargeFullDesign,
                chargeFull = chargeFull,
                chargeNow = readFile("charge_now")?.toLongOrNull(),
                currentNow = readFile("current_now")?.toLongOrNull(),
                voltageNow = readFile("voltage_now")?.toIntOrNull(),
                tempNow = readFile("temp")?.toIntOrNull(),
                health = readFile("health"),
                status = readFile("status"),
                capacityLevel = readFile("capacity_level"),
                timeToEmptyNow = readFile("time_to_empty_now")?.toLongOrNull(),
                timeToFullNow = readFile("time_to_full_now")?.toLongOrNull(),
                batteryAge = if (chargeFullDesign != null && chargeFull != null && chargeFullDesign > 0) {
                    (chargeFull.toDouble() / chargeFullDesign) * 100
                } else null
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getKernelWakelocks(): List<KernelWakelockInfo> = withContext(Dispatchers.IO) {
        val result = mutableListOf<KernelWakelockInfo>()
        try {
            val wakelockPath = when {
                File("/sys/kernel/wakelock_stats").exists() -> "/sys/kernel/wakelock_stats"
                File("/proc/wakelocks").exists() -> "/proc/wakelocks"
                else -> null
            }

            var linesList: List<String>? = null
            if (wakelockPath != null) {
                try {
                    val f = File(wakelockPath)
                    if (f.canRead()) {
                        linesList = f.readLines()
                    }
                } catch (_: Exception) {}
            }

            if (linesList == null && isRootAvailable()) {
                val out = exec("cat /sys/kernel/wakelock_stats 2>/dev/null || cat /proc/wakelocks 2>/dev/null", 3000L)
                if (!out.isNullOrBlank()) {
                    linesList = out.lines()
                }
            }

            linesList?.drop(1)?.forEach { line ->
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 6) {
                    result.add(
                        KernelWakelockInfo(
                            name = parts[0].trim('"'),
                            count = parts.getOrNull(1)?.toIntOrNull() ?: 0,
                            expireCount = parts.getOrNull(2)?.toIntOrNull() ?: 0,
                            wakeCount = parts.getOrNull(3)?.toIntOrNull() ?: 0,
                            activeCount = parts.getOrNull(4)?.toIntOrNull() ?: 0,
                            totalTime = parts.getOrNull(5)?.toLongOrNull() ?: 0L,
                            sleepTime = parts.getOrNull(6)?.toLongOrNull() ?: 0L,
                            maxTime = parts.getOrNull(7)?.toLongOrNull() ?: 0L,
                            lastChange = parts.getOrNull(8)?.toLongOrNull() ?: 0L
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        result.sortedByDescending { it.totalTime }
    }

    suspend fun getCpuInfo(): List<CpuInfo> = withContext(Dispatchers.IO) {
        val result = mutableListOf<CpuInfo>()
        try {
            val cpuDir = File("/sys/devices/system/cpu")
            val cpuDirs = cpuDir.listFiles { f -> f.name.matches(Regex("cpu\\d+")) }
                ?.sortedBy { it.name.removePrefix("cpu").toIntOrNull() ?: 0 }
                ?: return@withContext result

            val clusters = mutableMapOf<Int, MutableList<Int>>()
            cpuDirs.forEach { cpu ->
                val cpuNum = cpu.name.removePrefix("cpu").toIntOrNull() ?: return@forEach
                val policyPath = File("${cpu.absolutePath}/cpufreq/affected_cpus")
                val cluster = if (policyPath.exists() && policyPath.canRead()) {
                    policyPath.readText().trim().split(" ").firstOrNull()?.toIntOrNull() ?: cpuNum
                } else cpuNum
                clusters.getOrPut(cluster) { mutableListOf() }.add(cpuNum)
            }

            clusters.forEach { (clusterNum, _) ->
                val cpuPath = "/sys/devices/system/cpu/cpu$clusterNum/cpufreq"
                if (!File(cpuPath).exists()) return@forEach

                fun read(name: String): String? = try {
                    File("$cpuPath/$name").readText().trim()
                } catch (_: Exception) { null }

                val timeInState = mutableMapOf<Long, Long>()
                try {
                    val stateFile = File("$cpuPath/stats/time_in_state")
                    if (stateFile.exists() && stateFile.canRead()) {
                        stateFile.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                val parts = line.split(" ")
                                if (parts.size >= 2) {
                                    val freq = parts[0].toLongOrNull() ?: return@forEach
                                    val time = parts[1].toLongOrNull() ?: 0L
                                    timeInState[freq] = time
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}

                result.add(
                    CpuInfo(
                        cluster = clusterNum,
                        currentFreq = read("scaling_cur_freq")?.toLongOrNull() ?: 0L,
                        minFreq = read("scaling_min_freq")?.toLongOrNull() ?: 0L,
                        maxFreq = read("scaling_max_freq")?.toLongOrNull() ?: 0L,
                        governor = read("scaling_governor") ?: "unknown",
                        timeInState = timeInState
                    )
                )
            }
        } catch (_: Exception) {}
        result
    }

    suspend fun getThermalZones(): List<ThermalZone> = withContext(Dispatchers.IO) {
        val result = mutableListOf<ThermalZone>()
        try {
            val thermalDir = File("/sys/class/thermal")
            val zones = thermalDir.listFiles { f -> f.name.startsWith("thermal_zone") }
                ?: return@withContext result

            zones.forEach { zone ->
                fun read(name: String): String? = try {
                    File("${zone.absolutePath}/$name").readText().trim()
                } catch (_: Exception) { null }

                val tripPoints = mutableListOf<TripPoint>()
                var i = 0
                while (i < 5) {
                    val tripType = read("trip_point_${i}_type") ?: break
                    val tripTemp = read("trip_point_${i}_temp")?.toIntOrNull() ?: break
                    tripPoints.add(TripPoint(tripType, tripTemp))
                    i++
                }

                result.add(
                    ThermalZone(
                        name = zone.name,
                        type = read("type") ?: "unknown",
                        tempMilliC = read("temp")?.toIntOrNull() ?: 0,
                        tripPoints = tripPoints
                    )
                )
            }
        } catch (_: Exception) {}
        result.sortedBy { it.name }
    }

    val isRootCached: Boolean? get() = cachedRoot

    suspend fun runAsRoot(command: String): String? = withContext(Dispatchers.IO) {
        exec(command, CMD_TIMEOUT_MS)
    }

    /**
     * Executes a command as root and returns true if it completed within [timeoutMs] and exited with code 0.
     */
    suspend fun runAsRootSuccessful(command: String, timeoutMs: Long = 3_000L): Boolean = withContext(Dispatchers.IO) {
        var process: Process? = null
        var watchdog: Thread? = null
        val timedOut = AtomicBoolean(false)
        try {
            val p = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            process = p
            runCatching { p.outputStream.close() }

            watchdog = Thread {
                try {
                    if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                        timedOut.set(true)
                        Log.w(TAG, "su timed out after $timeoutMs ms: $command")
                        p.destroyForcibly()
                    }
                } catch (_: InterruptedException) {}
            }.apply {
                isDaemon = true
                start()
            }

            p.inputStream.bufferedReader().use { it.readText() }
            val exitCode = p.waitFor()
            !timedOut.get() && exitCode == 0
        } catch (e: Exception) {
            Log.d(TAG, "su failed for '$command': ${e.message}")
            false
        } finally {
            watchdog?.interrupt()
            runCatching { process?.destroy() }
        }
    }

    /**
     * Turns off / locks the screen using root command `input keyevent 26` (KEYCODE_POWER),
     * which activates the system's smooth display sleep transition without abrupt termination.
     */
    suspend fun lockScreen(): Boolean = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) return@withContext false
        runAsRootSuccessful("input keyevent 26")
    }

    private fun exec(command: String, timeoutMs: Long): String? {
        var process: Process? = null
        var watchdog: Thread? = null
        val timedOut = AtomicBoolean(false)
        return try {
            val p = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            process = p
            runCatching { p.outputStream.close() }

            watchdog = Thread {
                try {
                    if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                        timedOut.set(true)
                        Log.w(TAG, "su timed out after $timeoutMs ms: $command")
                        p.destroyForcibly()
                    }
                } catch (_: InterruptedException) {}
            }.apply {
                isDaemon = true
                start()
            }

            val out = p.inputStream.bufferedReader().use(BufferedReader::readText)
            if (timedOut.get()) null else out
        } catch (e: Exception) {
            Log.d(TAG, "su failed for '$command': ${e.message}")
            null
        } finally {
            watchdog?.interrupt()
            runCatching { process?.destroy() }
        }
    }
}

