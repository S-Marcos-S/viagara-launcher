// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.root

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import dev.victorialauncher.data.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class SystemPerformanceSnapshot(
    val totalCpuPercent: Double = 0.0,
    val cpuCores: Int = 1,
    val cpuFrequenciesMhz: List<Long> = emptyList(),
    val maxCpuFrequencyMhz: Long = 0L,
    val uptimeMillis: Long = 0L,
    val totalProcesses: Int = 0,
    val totalThreads: Int = 0,
    val ramTotalMb: Double = 0.0,
    val ramUsedMb: Double = 0.0,
    val ramAvailableMb: Double = 0.0,
    val ramUsedPercent: Double = 0.0,
    val swapTotalMb: Double = 0.0,
    val swapUsedMb: Double = 0.0,
    val rxSpeedBps: Long = 0L,
    val txSpeedBps: Long = 0L,
    val totalRxBytes: Long = 0L,
    val totalTxBytes: Long = 0L,
    val networkType: String = "Offline",
    val gpuUsagePercent: Double? = null,
    val gpuClockMhz: Long? = null,
    val gpuModel: String? = null,
    val storageTotalGb: Double = 0.0,
    val storageUsedGb: Double = 0.0,
    val storageAvailableGb: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
)

data class TaskProcessItem(
    val pid: Int,
    val packageName: String,
    val processName: String,
    val appName: String,
    val user: String,
    val cpuPercent: Double,
    val ramMb: Double,
    val isForeground: Boolean,
    val isSystemProcess: Boolean = false,
    val appInfo: AppInfo? = null,
)

data class RunningTasksSnapshot(
    val foregroundApps: List<TaskProcessItem> = emptyList(),
    val backgroundApps: List<TaskProcessItem> = emptyList(),
    val systemProcesses: List<TaskProcessItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
)

object SystemTaskInspector {

    private data class CpuSample(val total: Long, val idle: Long, val timestamp: Long)
    private val lastCpuSample = AtomicReference<CpuSample?>(null)

    private data class NetSample(val rx: Long, val tx: Long, val timestamp: Long)
    private val lastNetSample = AtomicReference<NetSample?>(null)

    /**
     * Samples overall device performance metrics (CPU, RAM, GPU, Network, Storage).
     */
    suspend fun getPerformanceSnapshot(context: Context): SystemPerformanceSnapshot = withContext(Dispatchers.IO) {
        val now = SystemClock.elapsedRealtime()

        // 1. CPU Utilization
        val cpuUsage = sampleGlobalCpuUsage(now)
        val coresCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val coreFreqs = readCpuCoreFrequencies(coresCount)
        val maxFreq = coreFreqs.maxOrNull() ?: 0L

        // 2. RAM and Swap / zRAM
        val (ramTotal, ramAvail, ramUsed, ramPercent, swapTotal, swapUsed) = readRamAndSwap(context)

        // 3. Network Transfer & Speeds
        val totalRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
        val totalTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)
        var rxSpeed = 0L
        var txSpeed = 0L

        val prevNet = lastNetSample.getAndSet(NetSample(totalRx, totalTx, now))
        if (prevNet != null) {
            val dt = (now - prevNet.timestamp) / 1000.0
            if (dt in 0.4..30.0) {
                val dRx = totalRx - prevNet.rx
                val dTx = totalTx - prevNet.tx
                if (dRx > 0) rxSpeed = (dRx / dt).toLong()
                if (dTx > 0) txSpeed = (dTx / dt).toLong()
            }
        }

        val netType = detectNetworkType(context)

        // 4. GPU Telemetry
        val (gpuPercent, gpuClock, gpuModel) = sampleGpuInfo()

        // 5. Storage
        val (storageTotal, storageUsed, storageAvail) = sampleStorage()

        // 6. Process and Thread counts from /proc/loadavg
        val (procCount, threadCount) = readLoadAvgProcCounts()

        SystemPerformanceSnapshot(
            totalCpuPercent = cpuUsage,
            cpuCores = coresCount,
            cpuFrequenciesMhz = coreFreqs,
            maxCpuFrequencyMhz = maxFreq,
            uptimeMillis = SystemClock.elapsedRealtime(),
            totalProcesses = procCount,
            totalThreads = threadCount,
            ramTotalMb = ramTotal,
            ramUsedMb = ramUsed,
            ramAvailableMb = ramAvail,
            ramUsedPercent = ramPercent,
            swapTotalMb = swapTotal,
            swapUsedMb = swapUsed,
            rxSpeedBps = rxSpeed,
            txSpeedBps = txSpeed,
            totalRxBytes = totalRx,
            totalTxBytes = totalTx,
            networkType = netType,
            gpuUsagePercent = gpuPercent,
            gpuClockMhz = gpuClock,
            gpuModel = gpuModel,
            storageTotalGb = storageTotal,
            storageUsedGb = storageUsed,
            storageAvailableGb = storageAvail,
            timestamp = System.currentTimeMillis(),
        )
    }

    /**
     * Collects and categorizes all running processes into Foreground Apps,
     * Background Apps, and System Processes with live CPU% and RAM usage.
     */
    suspend fun getRunningTasks(
        context: Context,
        installedApps: List<AppInfo>,
    ): RunningTasksSnapshot = withContext(Dispatchers.IO) {
        val appsByPackage = installedApps.associateBy { it.packageName }
        val pm = context.packageManager

        // Shell script to query top/ps and current focused/resumed activity via root
        val shellScript = """
            echo "===ACTIVITIES==="
            dumpsys window 2>/dev/null | grep -E "mCurrentFocus|mFocusedApp"
            dumpsys activity activities 2>/dev/null | grep -E "topResumedActivity|mResumedActivity"
            echo "===PS==="
            ps -A -o PID,USER,%CPU,%MEM,ARGS 2>/dev/null || ps -o PID,USER,%CPU,%MEM,ARGS 2>/dev/null
        """.trimIndent()

        val output = AppRootInspector.runSuCommand(shellScript).getOrDefault("")
        val sections = parseSections(output)

        val activitiesText = sections["ACTIVITIES"] ?: ""
        val psText = sections["PS"] ?: ""

        val foregroundPackages = extractForegroundPackages(activitiesText)

        // Parse processes
        val rawList = mutableListOf<RawProc>()
        psText.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("PID")) return@forEach
            val tokens = trimmed.split(Regex("\\s+"))
            if (tokens.size >= 5) {
                val pid = tokens[0].toIntOrNull() ?: return@forEach
                val user = tokens[1]
                val cpu = tokens[2].toDoubleOrNull() ?: 0.0
                val mem = tokens[3].toDoubleOrNull() ?: 0.0
                val args = tokens.subList(4, tokens.size).joinToString(" ")
                rawList.add(RawProc(pid, user, cpu, mem, args))
            }
        }

        // Get total system RAM to convert %MEM into MB
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024.0 * 1024.0)

        val foregroundList = mutableListOf<TaskProcessItem>()
        val backgroundList = mutableListOf<TaskProcessItem>()
        val systemList = mutableListOf<TaskProcessItem>()

        // Cache for resolving package app names
        val appNameCache = mutableMapOf<String, String>()

        for (proc in rawList) {
            val cmd = proc.args.trim()
            val baseCmd = cmd.substringBefore(" ").substringAfterLast("/")
            val cleanCmd = baseCmd.removeSurrounding("[", "]").removeSurrounding("(", ")")

            val matchedApp = appsByPackage.values.firstOrNull { app ->
                AppRootInspector.isPackageProcess(cmd, app.packageName) ||
                    cleanCmd == app.packageName ||
                    cleanCmd.startsWith("${app.packageName}:")
            }

            if (matchedApp != null) {
                val pkg = matchedApp.packageName
                val isFg = foregroundPackages.contains(pkg)
                val ramMb = (proc.memPercent / 100.0) * totalRamMb
                val item = TaskProcessItem(
                    pid = proc.pid,
                    packageName = pkg,
                    processName = cleanCmd,
                    appName = matchedApp.label,
                    user = proc.user,
                    cpuPercent = proc.cpuPercent,
                    ramMb = ramMb,
                    isForeground = isFg,
                    isSystemProcess = false,
                    appInfo = matchedApp,
                )
                if (isFg) {
                    foregroundList.add(item)
                } else {
                    backgroundList.add(item)
                }
            } else {
                // Check if user is an Android app user (e.g. u0_a...) or package name format
                val isAppUser = proc.user.startsWith("u0_a") || proc.user.startsWith("u0_i")
                var detectedPkg: String? = null
                if (cleanCmd.contains(".") && !cleanCmd.startsWith("/")) {
                    val potentialPkg = cleanCmd.substringBefore(":")
                    if (potentialPkg.length > 3 && potentialPkg.contains(".")) {
                        detectedPkg = potentialPkg
                    }
                }

                if (detectedPkg != null || isAppUser) {
                    val pkgName = detectedPkg ?: cleanCmd.substringBefore(":")
                    val isFg = foregroundPackages.contains(pkgName)
                    val label = appNameCache.getOrPut(pkgName) {
                        runCatching {
                            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                pm.getApplicationInfo(pkgName, PackageManager.ApplicationInfoFlags.of(0))
                            } else {
                                @Suppress("DEPRECATION")
                                pm.getApplicationInfo(pkgName, 0)
                            }
                            pm.getApplicationLabel(info).toString()
                        }.getOrDefault(pkgName)
                    }

                    val ramMb = (proc.memPercent / 100.0) * totalRamMb
                    val item = TaskProcessItem(
                        pid = proc.pid,
                        packageName = pkgName,
                        processName = cleanCmd,
                        appName = label,
                        user = proc.user,
                        cpuPercent = proc.cpuPercent,
                        ramMb = ramMb,
                        isForeground = isFg,
                        isSystemProcess = false,
                        appInfo = null,
                    )

                    if (isFg) {
                        foregroundList.add(item)
                    } else {
                        backgroundList.add(item)
                    }
                } else {
                    // System daemon / kernel thread
                    val ramMb = (proc.memPercent / 100.0) * totalRamMb
                    systemList.add(
                        TaskProcessItem(
                            pid = proc.pid,
                            packageName = cleanCmd,
                            processName = cleanCmd,
                            appName = cleanCmd,
                            user = proc.user,
                            cpuPercent = proc.cpuPercent,
                            ramMb = ramMb,
                            isForeground = false,
                            isSystemProcess = true,
                            appInfo = null,
                        )
                    )
                }
            }
        }

        // Sort each category by CPU descending, then by RAM descending
        val sortedFg = foregroundList.sortedWith(compareByDescending<TaskProcessItem> { it.cpuPercent }.thenByDescending { it.ramMb })
        val sortedBg = backgroundList.sortedWith(compareByDescending<TaskProcessItem> { it.cpuPercent }.thenByDescending { it.ramMb })
        val sortedSys = systemList.sortedWith(compareByDescending<TaskProcessItem> { it.cpuPercent }.thenByDescending { it.ramMb })

        RunningTasksSnapshot(
            foregroundApps = sortedFg,
            backgroundApps = sortedBg,
            systemProcesses = sortedSys,
            timestamp = System.currentTimeMillis(),
        )
    }

    private data class RawProc(
        val pid: Int,
        val user: String,
        val cpuPercent: Double,
        val memPercent: Double,
        val args: String,
    )

    private suspend fun sampleGlobalCpuUsage(now: Long): Double {
        return try {
            var firstLine = try {
                val statFile = File("/proc/stat")
                if (statFile.canRead()) {
                    statFile.bufferedReader().use { it.readLine() }
                } else null
            } catch (_: Throwable) {
                null
            }

            // Fallback via root execution if SELinux denies direct access
            if (firstLine.isNullOrBlank()) {
                firstLine = AppRootInspector.runSuCommand("head -n 1 /proc/stat 2>/dev/null").getOrNull()?.trim()
            }

            if (!firstLine.isNullOrBlank() && firstLine.startsWith("cpu ")) {
                val parts = firstLine.removePrefix("cpu ").trim().split(Regex("\\s+"))
                if (parts.size >= 4) {
                    val user = parts[0].toLongOrNull() ?: 0L
                    val nice = parts[1].toLongOrNull() ?: 0L
                    val system = parts[2].toLongOrNull() ?: 0L
                    val idle = parts[3].toLongOrNull() ?: 0L
                    val iowait = parts.getOrNull(4)?.toLongOrNull() ?: 0L
                    val irq = parts.getOrNull(5)?.toLongOrNull() ?: 0L
                    val softirq = parts.getOrNull(6)?.toLongOrNull() ?: 0L
                    val steal = parts.getOrNull(7)?.toLongOrNull() ?: 0L

                    val total = user + nice + system + idle + iowait + irq + softirq + steal
                    val idleTotal = idle + iowait

                    val prev = lastCpuSample.getAndSet(CpuSample(total, idleTotal, now))
                    if (prev != null) {
                        val dTotal = total - prev.total
                        val dIdle = idleTotal - prev.idle
                        if (dTotal > 0) {
                            val usage = ((dTotal - dIdle).toDouble() / dTotal.toDouble()) * 100.0
                            return usage.coerceIn(0.0, 100.0)
                        }
                    } else {
                        // First sample ever: briefly delay to obtain an immediate baseline calculation
                        delay(120L)
                        return sampleGlobalCpuUsage(SystemClock.elapsedRealtime())
                    }
                }
            }
            0.0
        } catch (_: Throwable) {
            0.0
        }
    }

    private suspend fun readCpuCoreFrequencies(cores: Int): List<Long> {
        val targetSize = maxOf(cores, 8)
        val freqs = MutableList(targetSize) { 0L }
        var needRootFallback = false

        for (i in 0 until targetSize) {
            val freqFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
            val freqKHz = if (freqFile.canRead()) {
                runCatching { freqFile.readText().trim().toLongOrNull() }.getOrNull()
            } else null

            if (freqKHz != null && freqKHz > 0) {
                freqs[i] = freqKHz / 1000 // Convert to MHz
            } else {
                needRootFallback = true
            }
        }

        if (needRootFallback) {
            val cmd = (0 until targetSize).joinToString("; ") { i ->
                "cat /sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq 2>/dev/null || echo 0"
            }
            val raw = AppRootInspector.runSuCommand(cmd).getOrNull() ?: ""
            val lines = raw.lines().map { it.trim().toLongOrNull() ?: 0L }
            for (i in 0 until minOf(targetSize, lines.size)) {
                val khz = lines[i]
                if (khz > 0) {
                    freqs[i] = khz / 1000
                }
            }
        }

        return freqs
    }

    private fun readRamAndSwap(context: Context): RamSwapData {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val ramTotalMb = memInfo.totalMem / (1024.0 * 1024.0)
        val ramAvailMb = memInfo.availMem / (1024.0 * 1024.0)
        val ramUsedMb = (ramTotalMb - ramAvailMb).coerceAtLeast(0.0)
        val ramPercent = if (ramTotalMb > 0) (ramUsedMb / ramTotalMb) * 100.0 else 0.0

        var swapTotalMb = 0.0
        var swapUsedMb = 0.0

        runCatching {
            val memInfoFile = File("/proc/meminfo")
            if (memInfoFile.canRead()) {
                memInfoFile.forEachLine { line ->
                    if (line.startsWith("SwapTotal:")) {
                        val kb = line.substringAfter(":").trim().substringBefore(" ").trim().toDoubleOrNull() ?: 0.0
                        swapTotalMb = kb / 1024.0
                    } else if (line.startsWith("SwapFree:")) {
                        val freeKb = line.substringAfter(":").trim().substringBefore(" ").trim().toDoubleOrNull() ?: 0.0
                        val freeMb = freeKb / 1024.0
                        swapUsedMb = (swapTotalMb - freeMb).coerceAtLeast(0.0)
                    }
                }
            }
        }

        return RamSwapData(ramTotalMb, ramAvailMb, ramUsedMb, ramPercent, swapTotalMb, swapUsedMb)
    }

    private data class RamSwapData(
        val totalMb: Double,
        val availMb: Double,
        val usedMb: Double,
        val usedPercent: Double,
        val swapTotalMb: Double,
        val swapUsedMb: Double,
    )
    

    @SuppressLint("MissingPermission")
    private fun detectNetworkType(context: Context): String {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNet = cm?.activeNetwork ?: return "Sem conexão"
            val caps = cm.getNetworkCapabilities(activeNet) ?: return "Sem conexão"

            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Dados Móveis"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                else -> "Conectado"
            }
        }.getOrDefault("Offline")
    }

    private fun sampleGpuInfo(): Triple<Double?, Long?, String?> {
        // Try Qualcomm Adreno sysfs
        val adrenoBusy = File("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage")
        val adrenoClock = File("/sys/class/kgsl/kgsl-3d0/gpuclk")

        if (adrenoBusy.canRead()) {
            val busy = runCatching { adrenoBusy.readText().trim().removeSuffix("%").toDoubleOrNull() }.getOrNull()
            val clockHz = runCatching { adrenoClock.readText().trim().toLongOrNull() }.getOrNull()
            val clockMhz = if (clockHz != null && clockHz > 0) clockHz / 1_000_000L else null
            return Triple(busy, clockMhz, "Qualcomm Adreno")
        }

        // Try ARM Mali sysfs
        val maliUtil = File("/sys/class/misc/mali0/device/utilization")
        val maliClock = File("/sys/class/misc/mali0/device/clock")
        if (maliUtil.canRead()) {
            val util = runCatching { maliUtil.readText().trim().toDoubleOrNull() }.getOrNull()
            val clockHz = runCatching { maliClock.readText().trim().toLongOrNull() }.getOrNull()
            val clockMhz = if (clockHz != null && clockHz > 0) clockHz / 1_000_000L else null
            return Triple(util, clockMhz, "ARM Mali")
        }

        return Triple(null, null, null)
    }

    private fun sampleStorage(): Triple<Double, Double, Double> {
        return runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            val totalBytes = stat.totalBytes
            val availBytes = stat.availableBytes
            val usedBytes = totalBytes - availBytes

            val totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0)
            val usedGb = usedBytes / (1024.0 * 1024.0 * 1024.0)
            val availGb = availBytes / (1024.0 * 1024.0 * 1024.0)

            Triple(totalGb, usedGb, availGb)
        }.getOrDefault(Triple(0.0, 0.0, 0.0))
    }

    private fun readLoadAvgProcCounts(): Pair<Int, Int> {
        return runCatching {
            val loadAvgFile = File("/proc/loadavg")
            if (loadAvgFile.canRead()) {
                val parts = loadAvgFile.readText().trim().split(Regex("\\s+"))
                if (parts.size >= 4) {
                    val procParts = parts[3].split("/")
                    val activeThreads = procParts.getOrNull(0)?.toIntOrNull() ?: 0
                    val totalEntities = procParts.getOrNull(1)?.toIntOrNull() ?: 0
                    return Pair(totalEntities, activeThreads)
                }
            }
            Pair(0, 0)
        }.getOrDefault(Pair(0, 0))
    }

    private fun extractForegroundPackages(text: String): Set<String> {
        val result = mutableSetOf<String>()
        val regex = Regex("([a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)+)/([a-zA-Z0-9_.]+)")
        text.lineSequence().forEach { line ->
            regex.findAll(line).forEach { match ->
                val pkg = match.groupValues[1]
                if (pkg.contains(".") && !pkg.startsWith("android.")) {
                    result.add(pkg)
                }
            }
        }
        return result
    }

    private fun parseSections(raw: String): Map<String, String> {
        val map = mutableMapOf<String, StringBuilder>()
        var currentSection = ""
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("===") && trimmed.endsWith("===")) {
                currentSection = trimmed.removeSurrounding("===").trim()
                map[currentSection] = StringBuilder()
            } else if (currentSection.isNotEmpty()) {
                map[currentSection]?.appendLine(line)
            }
        }
        return map.mapValues { it.value.toString().trim() }
    }
}
