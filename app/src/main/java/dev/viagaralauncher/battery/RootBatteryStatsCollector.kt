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
        val timeInState: Map<Long, Long>, // freq -> time in jiffies
        val cores: List<Int> = emptyList()
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
        val hasRoot = isRootAvailable()

        // 1. If root is available, use consolidated root execution for complete SELinux bypass
        if (hasRoot) {
            val rootCmd = "sh -c '" +
                "DIRS=\"\"; " +
                "for d in /sys/devices/system/cpu/cpufreq/policy* /sys/devices/system/cpu/cpu[0-9]*/cpufreq; do " +
                "  [ -d \"\$d\" ] && DIRS=\"\$DIRS \$d\"; " +
                "done; " +
                "SEEN=\"\"; " +
                "for p in \$DIRS; do " +
                "  aff=\$(cat \"\$p/affected_cpus\" 2>/dev/null); " +
                "  [ -z \"\$aff\" ] && aff=\$(echo \"\$p\" | grep -o \"[0-9]*\" | tail -n 1); " +
                "  case \"\$SEEN\" in *\" \$aff \"*) continue ;; esac; " +
                "  SEEN=\"\$SEEN \$aff \"; " +
                "  echo \"===POLICY===\"; " +
                "  echo \"affected:\$aff\"; " +
                "  gov=\$(cat \"\$p/scaling_governor\" 2>/dev/null); " +
                "  [ -z \"\$gov\" ] && gov=\$(cat \"\$p/scaling_driver\" 2>/dev/null); " +
                "  echo \"governor:\${gov:-unknown}\"; " +
                "  cur=\$(cat \"\$p/scaling_cur_freq\" 2>/dev/null); " +
                "  [ -z \"\$cur\" ] && cur=\$(cat \"\$p/cpuinfo_cur_freq\" 2>/dev/null); " +
                "  echo \"cur:\${cur:-0}\"; " +
                "  min=\$(cat \"\$p/scaling_min_freq\" 2>/dev/null); " +
                "  [ -z \"\$min\" ] && min=\$(cat \"\$p/cpuinfo_min_freq\" 2>/dev/null); " +
                "  echo \"min:\${min:-0}\"; " +
                "  max=\$(cat \"\$p/scaling_max_freq\" 2>/dev/null); " +
                "  [ -z \"\$max\" ] && max=\$(cat \"\$p/cpuinfo_max_freq\" 2>/dev/null); " +
                "  echo \"max:\${max:-0}\"; " +
                "  echo \"---TIS---\"; " +
                "  cat \"\$p/stats/time_in_state\" 2>/dev/null; " +
                "  echo \"===END===\"; " +
                "done'"

            val out = exec(rootCmd, 3500L)
            if (!out.isNullOrBlank()) {
                val parsed = parseRootCpuOutput(out)
                if (parsed.isNotEmpty()) {
                    return@withContext parsed
                }
            }
        }

        // 2. Fallback to direct sysfs file reading (if accessible without root)
        try {
            val policyDir = File("/sys/devices/system/cpu/cpufreq")
            val policyDirs = if (policyDir.exists() && policyDir.canRead()) {
                policyDir.listFiles { f -> f.name.startsWith("policy") }
                    ?.sortedBy { it.name.removePrefix("policy").toIntOrNull() ?: 0 }
            } else null

            if (!policyDirs.isNullOrEmpty()) {
                policyDirs.forEach { p ->
                    val policyNum = p.name.removePrefix("policy").toIntOrNull() ?: 0
                    val affectedStr = readFileDirect(p, "affected_cpus") ?: "$policyNum"
                    val cores = affectedStr.split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }.sorted()
                    val gov = readFileDirect(p, "scaling_governor") ?: readFileDirect(p, "scaling_driver") ?: "unknown"
                    val cur = (readFileDirect(p, "scaling_cur_freq") ?: readFileDirect(p, "cpuinfo_cur_freq"))?.toLongOrNull() ?: 0L
                    val min = (readFileDirect(p, "scaling_min_freq") ?: readFileDirect(p, "cpuinfo_min_freq"))?.toLongOrNull() ?: 0L
                    val max = (readFileDirect(p, "scaling_max_freq") ?: readFileDirect(p, "cpuinfo_max_freq"))?.toLongOrNull() ?: 0L
                    val tis = readTimeInStateDirect(File(p, "stats/time_in_state"))

                    result.add(
                        CpuInfo(
                            cluster = policyNum,
                            currentFreq = cur,
                            minFreq = min,
                            maxFreq = max,
                            governor = gov,
                            timeInState = tis,
                            cores = cores
                        )
                    )
                }
            } else {
                val cpuDir = File("/sys/devices/system/cpu")
                val cpuDirs = cpuDir.listFiles { f -> f.name.matches(Regex("cpu\\d+")) }
                    ?.sortedBy { it.name.removePrefix("cpu").toIntOrNull() ?: 0 }
                    ?: return@withContext result

                val clusters = mutableMapOf<Int, MutableList<Int>>()
                cpuDirs.forEach { cpu ->
                    val cpuNum = cpu.name.removePrefix("cpu").toIntOrNull() ?: return@forEach
                    val policyPath = File("${cpu.absolutePath}/cpufreq/affected_cpus")
                    val cluster = if (policyPath.exists() && policyPath.canRead()) {
                        policyPath.readText().trim().split(Regex("\\s+")).firstOrNull()?.toIntOrNull() ?: cpuNum
                    } else cpuNum
                    clusters.getOrPut(cluster) { mutableListOf() }.add(cpuNum)
                }

                clusters.forEach { (clusterNum, coreList) ->
                    val cpuPath = File("/sys/devices/system/cpu/cpu$clusterNum/cpufreq")
                    if (!cpuPath.exists()) return@forEach

                    val gov = readFileDirect(cpuPath, "scaling_governor") ?: readFileDirect(cpuPath, "scaling_driver") ?: "unknown"
                    val cur = (readFileDirect(cpuPath, "scaling_cur_freq") ?: readFileDirect(cpuPath, "cpuinfo_cur_freq"))?.toLongOrNull() ?: 0L
                    val min = (readFileDirect(cpuPath, "scaling_min_freq") ?: readFileDirect(cpuPath, "cpuinfo_min_freq"))?.toLongOrNull() ?: 0L
                    val max = (readFileDirect(cpuPath, "scaling_max_freq") ?: readFileDirect(cpuPath, "cpuinfo_max_freq"))?.toLongOrNull() ?: 0L
                    val tis = readTimeInStateDirect(File(cpuPath, "stats/time_in_state"))

                    result.add(
                        CpuInfo(
                            cluster = clusterNum,
                            currentFreq = cur,
                            minFreq = min,
                            maxFreq = max,
                            governor = gov,
                            timeInState = tis,
                            cores = coreList.sorted()
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        result.sortedBy { it.cluster }
    }

    private fun readFileDirect(dir: File, name: String): String? = try {
        val f = File(dir, name)
        if (f.exists() && f.canRead()) f.readText().trim().takeIf { it.isNotBlank() } else null
    } catch (_: Exception) { null }

    private fun readTimeInStateDirect(file: File): Map<Long, Long> {
        val map = mutableMapOf<Long, Long>()
        try {
            if (file.exists() && file.canRead()) {
                file.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val parts = line.trim().split(Regex("\\s+"))
                        if (parts.size >= 2) {
                            val freq = parts[0].toLongOrNull() ?: return@forEach
                            val time = parts[1].toLongOrNull() ?: 0L
                            map[freq] = time
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return map
    }

    private fun parseRootCpuOutput(output: String): List<CpuInfo> {
        val result = mutableListOf<CpuInfo>()
        val blocks = output.split("===POLICY===")
        var clusterIndex = 0

        for (block in blocks) {
            val trimmed = block.trim()
            if (trimmed.isEmpty()) continue

            var affected = ""
            var governor = "unknown"
            var cur = 0L
            var min = 0L
            var max = 0L
            val timeInState = mutableMapOf<Long, Long>()
            var inTis = false

            for (line in trimmed.lines()) {
                val l = line.trim()
                when {
                    l == "---TIS---" -> inTis = true
                    l == "===END===" -> inTis = false
                    inTis -> {
                        val parts = l.split(Regex("\\s+"))
                        if (parts.size >= 2) {
                            val freq = parts[0].toLongOrNull()
                            val time = parts[1].toLongOrNull()
                            if (freq != null && time != null) {
                                timeInState[freq] = time
                            }
                        }
                    }
                    l.startsWith("affected:") -> affected = l.removePrefix("affected:").trim()
                    l.startsWith("governor:") -> governor = l.removePrefix("governor:").trim()
                    l.startsWith("cur:") -> cur = l.removePrefix("cur:").trim().toLongOrNull() ?: 0L
                    l.startsWith("min:") -> min = l.removePrefix("min:").trim().toLongOrNull() ?: 0L
                    l.startsWith("max:") -> max = l.removePrefix("max:").trim().toLongOrNull() ?: 0L
                }
            }

            val cores = affected.split(Regex("\\s+"))
                .mapNotNull { it.toIntOrNull() }
                .sorted()

            val clusterNum = cores.firstOrNull() ?: clusterIndex

            result.add(
                CpuInfo(
                    cluster = clusterNum,
                    currentFreq = cur,
                    minFreq = min,
                    maxFreq = max,
                    governor = governor,
                    timeInState = timeInState,
                    cores = cores
                )
            )
            clusterIndex++
        }

        return result.sortedBy { it.cluster }
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
                } catch (ignored: InterruptedException) {}
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
                } catch (ignored: InterruptedException) {}
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

