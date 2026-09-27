// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.root

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.TrafficStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class AppProcessStatus {
    FOREGROUND,
    BACKGROUND,
    STOPPED,
}

data class ProcessDetail(
    val pid: Int,
    val name: String,
    val user: String,
    val cpuPercent: Double,
    val memPercent: Double,
)

data class NetworkConnection(
    val protocol: String,
    val localAddress: String,
    val remoteAddress: String,
    val state: String,
)

data class AppPermissionItem(
    val permission: String,
    val label: String,
    val isGranted: Boolean,
    val isDangerous: Boolean,
)

data class AppInspectionData(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val uid: Int,
    val status: AppProcessStatus,
    val processes: List<ProcessDetail>,
    val totalCpuPercent: Double,
    val ramTotalMb: Double,
    val ramDalvikMb: Double,
    val ramNativeMb: Double,
    val ramGraphicsMb: Double,
    val rxBytes: Long,
    val txBytes: Long,
    val rxSpeedBps: Long = 0L,
    val txSpeedBps: Long = 0L,
    val activeConnections: List<NetworkConnection>,
    val topActivity: String?,
    val activeServices: List<String>,
    val permissions: List<AppPermissionItem>,
    val timestamp: Long = System.currentTimeMillis(),
)

object AppRootInspector {

    private data class NetSnapshot(val timestamp: Long, val rxBytes: Long, val txBytes: Long)
    private val lastNetSnapshots = ConcurrentHashMap<String, NetSnapshot>()

    /**
     * Executes an inspection script via root shell and queries PackageManager to build
     * a complete technical diagnosis of the specified application.
     */
    suspend fun inspectApp(context: Context, packageName: String): Result<AppInspectionData> = withContext(Dispatchers.IO) {
        runCatching {
            val pm = context.packageManager
            val packageInfo: PackageInfo = runCatching {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                }
            }.getOrElse {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }

            val appInfo = packageInfo.applicationInfo
            val appName = if (appInfo != null) {
                runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(packageName)
            } else {
                packageName
            }

            val versionName = packageInfo.versionName ?: "1.0"
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            val uid = appInfo?.uid ?: 0

            val dlManagerUid: Int? = if (packageName == "com.android.vending") {
                runCatching {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo("com.android.providers.downloads", PackageManager.PackageInfoFlags.of(0)).applicationInfo?.uid
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo("com.android.providers.downloads", 0).applicationInfo?.uid
                    }
                }.getOrNull()
            } else null

            val extraPackageName: String? = if (packageName == "com.android.vending") "com.android.providers.downloads" else null

            val topGrep = if (extraPackageName != null) "grep -e \"$packageName\" -e \"$extraPackageName\"" else "grep \"$packageName\""
            val psGrep = if (extraPackageName != null) "grep -e \"$packageName\" -e \"$extraPackageName\"" else "grep \"$packageName\""

            val dlmNetStatsScript = if (dlManagerUid != null) {
                """
                echo "===NET_STATS_DLM==="
                dumpsys netstats detail 2>/dev/null | grep "uid=$dlManagerUid" || dumpsys netstats 2>/dev/null | grep "uid=$dlManagerUid"
                echo "===UID_STAT_DLM==="
                cat /proc/uid_stat/$dlManagerUid/tcp_rcv 2>/dev/null
                echo "---TX---"
                cat /proc/uid_stat/$dlManagerUid/tcp_snd 2>/dev/null
                """.trimIndent()
            } else ""

            val dlmConnFilter = if (dlManagerUid != null) " -e \"$dlManagerUid\"" else ""

            // High-performance unified shell inspection script without toybox-incompatible regexes (\b, \s, \d)
            val shellScript = """
                echo "===PIDS==="
                pidof $packageName 2>/dev/null || pgrep -f $packageName 2>/dev/null
                echo "===TOP==="
                top -b -n 1 -q 2>/dev/null | $topGrep
                echo "===PS==="
                ps -A -o PID,USER,%CPU,%MEM,ARGS 2>/dev/null | $psGrep
                echo "===SMAPS==="
                for p in ${'$'}(pidof $packageName 2>/dev/null || pgrep -f $packageName 2>/dev/null); do
                    echo "---PID:${'$'}p---"
                    cat /proc/${'$'}p/smaps_rollup 2>/dev/null
                    cat /proc/${'$'}p/status 2>/dev/null | grep -E "^(VmRSS|RssAnon|RssFile):"
                done
                echo "===MEMINFO==="
                dumpsys meminfo $packageName 2>/dev/null | grep -E "TOTAL PSS|TOTAL      PSS|TOTAL:|Dalvik Heap|Native Heap|EGL mtrack|GL mtrack|Graphics|TOTAL RSS|Total PSS by process:|[0-9,]+K: *$packageName"
                echo "===NET_STATS==="
                dumpsys netstats detail 2>/dev/null | grep "uid=$uid" || dumpsys netstats 2>/dev/null | grep "uid=$uid"
                echo "===UID_STAT==="
                cat /proc/uid_stat/$uid/tcp_rcv 2>/dev/null
                echo "---TX---"
                cat /proc/uid_stat/$uid/tcp_snd 2>/dev/null
                $dlmNetStatsScript
                echo "===CONNECTIONS==="
                ss -tupn 2>/dev/null | grep -e "$packageName" -e "$uid"$dlmConnFilter || netstat -tlpn 2>/dev/null | grep -e "$packageName" -e "$uid"$dlmConnFilter
                echo "===ACTIVITIES==="
                dumpsys window 2>/dev/null | grep -E "mCurrentFocus|mFocusedApp"
                dumpsys activity activities 2>/dev/null | grep -E "topResumedActivity|mResumedActivity"
                echo "===SERVICES==="
                dumpsys activity services $packageName 2>/dev/null | grep -E "ServiceRecord|app=ProcessRecord"
            """.trimIndent()

            val rawOutput = runSuCommand(shellScript).getOrDefault("")

            // Parse sections
            val sections = parseSections(rawOutput)

            // 1. PIDs & Process details
            val pidsText = sections["PIDS"] ?: ""
            val rawPids = pidsText.split(Regex("\\s+"))
                .mapNotNull { it.trim().toIntOrNull() }
                .distinct()

            val topText = sections["TOP"] ?: ""
            val topProcesses = parseTopOutput(topText, packageName, extraPackageName)

            val psText = sections["PS"] ?: ""
            val psProcesses = parsePsOutput(psText, packageName, extraPackageName)

            val processes = mutableListOf<ProcessDetail>()
            topProcesses.forEach { processes.add(it) }
            psProcesses.forEach { psProc ->
                if (processes.none { it.pid == psProc.pid }) {
                    processes.add(psProc)
                }
            }

            // Fallback for any raw PIDs not captured in top or ps
            rawPids.forEach { pid ->
                if (processes.none { it.pid == pid }) {
                    processes.add(
                        ProcessDetail(
                            pid = pid,
                            name = packageName,
                            user = "u0_a${uid % 100000}",
                            cpuPercent = 0.0,
                            memPercent = 0.0,
                        )
                    )
                }
            }

            val totalCpu = processes.sumOf { it.cpuPercent }

            // 2. Activities & Execution Status (Strict Focus Validation)
            val activitiesText = sections["ACTIVITIES"] ?: ""
            val isForeground = isAppInForeground(activitiesText, packageName)

            val status = when {
                processes.isEmpty() && rawPids.isEmpty() -> AppProcessStatus.STOPPED
                isForeground -> AppProcessStatus.FOREGROUND
                else -> AppProcessStatus.BACKGROUND
            }

            val topActivity = extractTopActivity(activitiesText, packageName)

            // 3. Memory breakdown from smaps_rollup & dumpsys meminfo
            val smapsText = sections["SMAPS"] ?: ""
            val (smapsPss, smapsDalvik, smapsNative, _) = parseSmaps(smapsText)

            val meminfoText = sections["MEMINFO"] ?: ""
            val (meminfoPss, meminfoDalvik, meminfoNative, graphicsKb) = parseMeminfo(meminfoText)

            val totalPssKb = if (smapsPss > 0L) smapsPss else meminfoPss
            val dalvikKb = if (smapsDalvik > 0L) smapsDalvik else meminfoDalvik
            val nativeKb = if (smapsNative > 0L) smapsNative else meminfoNative

            // 4. Network stats
            val netStatsText = sections["NET_STATS"] ?: ""
            val (nsRx, nsTx) = parseNetstatsOutput(netStatsText, uid)

            val tsRx = TrafficStats.getUidRxBytes(uid)
            val tsTx = TrafficStats.getUidTxBytes(uid)

            var rxBytes = when {
                tsRx > 0L -> tsRx
                nsRx > 0L -> nsRx
                else -> 0L
            }
            var txBytes = when {
                tsTx > 0L -> tsTx
                nsTx > 0L -> nsTx
                else -> 0L
            }

            // Legacy /proc/uid_stat fallback
            if (rxBytes == 0L && txBytes == 0L) {
                val uidStatText = sections["UID_STAT"] ?: ""
                val netParts = uidStatStatFallback(uidStatText)
                val legacyRx = netParts.first
                val legacyTx = netParts.second
                if (legacyRx > 0L) rxBytes = legacyRx
                if (legacyTx > 0L) txBytes = legacyTx
            }

            // Download Provider fallback for Play Store (dual-UID tracking)
            var dlmRxBytes = 0L
            var dlmTxBytes = 0L
            if (dlManagerUid != null) {
                val dlmNetStatsText = sections["NET_STATS_DLM"] ?: ""
                val (dlmNsRx, dlmNsTx) = parseNetstatsOutput(dlmNetStatsText, dlManagerUid)
                val tsDlmRx = TrafficStats.getUidRxBytes(dlManagerUid)
                val tsDlmTx = TrafficStats.getUidTxBytes(dlManagerUid)

                dlmRxBytes = when {
                    tsDlmRx > 0L -> tsDlmRx
                    dlmNsRx > 0L -> dlmNsRx
                    else -> 0L
                }
                dlmTxBytes = when {
                    tsDlmTx > 0L -> tsDlmTx
                    dlmNsTx > 0L -> dlmNsTx
                    else -> 0L
                }

                if (dlmRxBytes == 0L && dlmTxBytes == 0L) {
                    val dlmUidStatText = sections["UID_STAT_DLM"] ?: ""
                    val dlmParts = uidStatStatFallback(dlmUidStatText)
                    val legacyRx = dlmParts.first
                    val legacyTx = dlmParts.second
                    if (legacyRx > 0L) dlmRxBytes = legacyRx
                    if (legacyTx > 0L) dlmTxBytes = legacyTx
                }
            }

            // Real-time transfer speed calculation
            val now = System.currentTimeMillis()
            val prevSample = lastNetSnapshots[packageName]
            var rxSpeedBps = 0L
            var txSpeedBps = 0L
            if (prevSample != null) {
                val deltaMs = now - prevSample.timestamp
                if (deltaMs in 500..30000) {
                    val deltaSec = deltaMs / 1000.0
                    val dRx = rxBytes - prevSample.rxBytes
                    val dTx = txBytes - prevSample.txBytes
                    if (dRx > 0) rxSpeedBps = (dRx / deltaSec).toLong()
                    if (dTx > 0) txSpeedBps = (dTx / deltaSec).toLong()
                }
            }
            lastNetSnapshots[packageName] = NetSnapshot(now, rxBytes, txBytes)

            if (dlManagerUid != null) {
                val dlmKey = "$packageName:dlm"
                val prevDlmSample = lastNetSnapshots[dlmKey]
                var dlmRxSpeedBps = 0L
                var dlmTxSpeedBps = 0L
                if (prevDlmSample != null) {
                    val deltaMs = now - prevDlmSample.timestamp
                    if (deltaMs in 500..30000) {
                        val deltaSec = deltaMs / 1000.0
                        val dRx = dlmRxBytes - prevDlmSample.rxBytes
                        val dTx = dlmTxBytes - prevDlmSample.txBytes
                        if (dRx > 0) dlmRxSpeedBps = (dRx / deltaSec).toLong()
                        if (dTx > 0) dlmTxSpeedBps = (dTx / deltaSec).toLong()
                    }
                }
                lastNetSnapshots[dlmKey] = NetSnapshot(now, dlmRxBytes, dlmTxBytes)

                if (dlmRxSpeedBps > 0) {
                    rxSpeedBps += dlmRxSpeedBps
                }
                if (dlmTxSpeedBps > 0) {
                    txSpeedBps += dlmTxSpeedBps
                }
                if (rxBytes == 0L && dlmRxBytes > 0L) {
                    rxBytes = dlmRxBytes
                    txBytes = dlmTxBytes
                }
            }

            // 5. Active connections
            val connText = sections["CONNECTIONS"] ?: ""
            val activeConnections = parseConnections(connText)

            // 6. Active services
            val servicesText = sections["SERVICES"] ?: ""
            val activeServices = extractServices(servicesText, packageName)

            // 7. Permissions
            val permissions = parsePermissions(packageInfo)

            AppInspectionData(
                packageName = packageName,
                appName = appName,
                versionName = versionName,
                versionCode = versionCode,
                uid = uid,
                status = status,
                processes = processes,
                totalCpuPercent = totalCpu,
                ramTotalMb = totalPssKb / 1024.0,
                ramDalvikMb = dalvikKb / 1024.0,
                ramNativeMb = nativeKb / 1024.0,
                ramGraphicsMb = graphicsKb / 1024.0,
                rxBytes = rxBytes,
                txBytes = txBytes,
                rxSpeedBps = rxSpeedBps,
                txSpeedBps = txSpeedBps,
                activeConnections = activeConnections,
                topActivity = topActivity,
                activeServices = activeServices,
                permissions = permissions,
            )
        }
    }

    /**
     * Executes force-stop on the app via root.
     */
    suspend fun forceStopApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val result = runSuCommand("am force-stop $packageName")
        result.isSuccess
    }

    /**
     * Kills a specific process PID via root.
     */
    suspend fun killProcess(pid: Int): Boolean = withContext(Dispatchers.IO) {
        val result = runSuCommand("kill -9 $pid")
        result.isSuccess
    }

    /**
     * Clears internal and external caches for the app via root.
     */
    suspend fun clearAppCache(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val cmd = "rm -rf /data/data/$packageName/cache/* /data/data/$packageName/code_cache/* /sdcard/Android/data/$packageName/cache/* 2>/dev/null"
        val result = runSuCommand(cmd)
        result.isSuccess
    }

    /**
     * Attempts to launch the app activity.
     */
    fun launchApp(context: Context, packageName: String): Boolean {
        return runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (intent != null) {
                context.startActivity(intent)
                true
            } else false
        }.getOrDefault(false)
    }

    /**
     * Runs an arbitrary shell command using the su binary.
     */
    suspend fun runSuCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode == 0 || output.isNotBlank()) {
                output
            } else {
                throw IllegalStateException("Command failed with exit code $exitCode")
            }
        }
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

    private fun uidStatStatFallback(text: String): Pair<Long, Long> {
        val parts = text.split("---TX---")
        val rx = parts.getOrNull(0)?.trim()?.toLongOrNull() ?: 0L
        val tx = parts.getOrNull(1)?.trim()?.toLongOrNull() ?: 0L
        return Pair(rx, tx)
    }

    fun isPackageProcess(cmd: String, packageName: String): Boolean {
        if (!cmd.contains(packageName)) return false
        return cmd.split(Regex("\\s+")).any { token ->
            val clean = token.substringAfterLast("/")
            val stripped = clean.substringAfterLast("=")
                .removeSurrounding("[", "]")
                .removeSurrounding("(", ")")
            stripped == packageName || stripped.startsWith("$packageName:")
        }
    }

    fun parseTopOutput(text: String, packageName: String, extraPackageName: String? = null): List<ProcessDetail> {
        val list = mutableListOf<ProcessDetail>()
        val timeRegex = Regex("^\\d+:\\d+.*")
        text.lineSequence().forEach { line ->
            val tokens = line.trim().split(Regex("\\s+"))
            if (tokens.size >= 5) {
                val pid = tokens[0].toIntOrNull()
                val user = tokens.getOrNull(1) ?: "unknown"
                val timeIdx = tokens.indexOfFirst { timeRegex.matches(it) }

                val (cpu, mem, cmd) = if (timeIdx in 2..(tokens.size - 2)) {
                    val cpuVal = tokens[timeIdx - 2].replace("%", "").toDoubleOrNull()
                    val memVal = tokens[timeIdx - 1].replace("%", "").toDoubleOrNull()
                    val cmdVal = tokens.drop(timeIdx + 1).joinToString(" ")
                    Triple(cpuVal, memVal, cmdVal)
                } else if (tokens.size >= 12) {
                    val cpuVal = tokens[8].replace("%", "").toDoubleOrNull()
                    val memVal = tokens[9].replace("%", "").toDoubleOrNull()
                    val cmdVal = tokens.drop(11).joinToString(" ")
                    Triple(cpuVal, memVal, cmdVal)
                } else {
                    Triple(null, null, "")
                }

                val matchesApp = isPackageProcess(cmd, packageName) ||
                        (extraPackageName != null && isPackageProcess(cmd, extraPackageName))

                if (pid != null && cpu != null && matchesApp) {
                    list.add(
                        ProcessDetail(
                            pid = pid,
                            name = cmd.substringAfterLast("/").substringAfterLast(" "),
                            user = user,
                            cpuPercent = cpu,
                            memPercent = mem ?: 0.0,
                        )
                    )
                }
            }
        }
        return list
    }

    fun parsePsOutput(text: String, packageName: String, extraPackageName: String? = null): List<ProcessDetail> {
        val list = mutableListOf<ProcessDetail>()
        text.lineSequence().forEach { line ->
            val tokens = line.trim().split(Regex("\\s+"))
            if (tokens.size >= 5) {
                val pid = tokens[0].toIntOrNull()
                if (pid != null) {
                    val user = tokens.getOrNull(1) ?: "unknown"
                    val cpu = tokens.getOrNull(2)?.replace("%", "")?.toDoubleOrNull() ?: 0.0
                    val mem = tokens.getOrNull(3)?.replace("%", "")?.toDoubleOrNull() ?: 0.0
                    val cmd = tokens.drop(4).joinToString(" ")
                    val matchesApp = isPackageProcess(cmd, packageName) ||
                            (extraPackageName != null && isPackageProcess(cmd, extraPackageName))
                    if (matchesApp) {
                        list.add(
                            ProcessDetail(
                                pid = pid,
                                name = cmd.substringAfterLast("/").substringAfterLast(" "),
                                user = user,
                                cpuPercent = cpu,
                                memPercent = mem,
                            )
                        )
                    }
                }
            }
        }
        return list
    }

    fun parseSmaps(text: String): LongArray {
        // [totalPssKb, dalvikKb, nativeKb, rssKb]
        var totalPss = 0L
        var dalvik = 0L
        var native = 0L
        var totalRss = 0L

        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val lower = trimmed.lowercase(Locale.ROOT)
            when {
                lower.startsWith("pss:") -> {
                    val num = Regex("\\d+").find(trimmed)?.value?.toLongOrNull() ?: 0L
                    totalPss += num
                }
                lower.startsWith("pss_anon:") || lower.startsWith("rssanon:") -> {
                    val num = Regex("\\d+").find(trimmed)?.value?.toLongOrNull() ?: 0L
                    dalvik += num
                }
                lower.startsWith("pss_file:") || lower.startsWith("rssfile:") -> {
                    val num = Regex("\\d+").find(trimmed)?.value?.toLongOrNull() ?: 0L
                    native += num
                }
                lower.startsWith("rss:") || lower.startsWith("vmrss:") -> {
                    val num = Regex("\\d+").find(trimmed)?.value?.toLongOrNull() ?: 0L
                    totalRss += num
                }
            }
        }
        if (totalPss == 0L && totalRss > 0L) {
            totalPss = totalRss
        }
        return longArrayOf(totalPss, dalvik, native, totalRss)
    }

    fun parseMeminfo(text: String): LongArray {
        // [totalPssKb, dalvikKb, nativeKb, graphicsKb]
        val result = LongArray(4) { 0L }
        var multiProcessSum = 0L

        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val lower = trimmed.lowercase(Locale.ROOT)
            when {
                // Multi-process format: "152,342K: com.android.vending (pid 12345)"
                Regex("^([0-9,]+)\\s*k:\\s+", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) -> {
                    val match = Regex("^([0-9,]+)\\s*k:\\s+", RegexOption.IGNORE_CASE).find(trimmed)
                    val rawNum = match?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull() ?: 0L
                    multiProcessSum += rawNum
                }
                lower.startsWith("total pss:") || lower.startsWith("total:") || (lower.startsWith("total") && lower.contains("pss")) -> {
                    val numbers = Regex("\\d+").findAll(trimmed).map { it.value.toLong() }.toList()
                    if (numbers.isNotEmpty()) {
                        result[0] = numbers[0]
                    }
                }
                lower.contains("dalvik heap") || lower.contains("java heap") -> {
                    val numbers = Regex("\\d+").findAll(trimmed).map { it.value.toLong() }.toList()
                    if (numbers.isNotEmpty()) {
                        result[1] = numbers[0]
                    }
                }
                lower.contains("native heap") -> {
                    val numbers = Regex("\\d+").findAll(trimmed).map { it.value.toLong() }.toList()
                    if (numbers.isNotEmpty()) {
                        result[2] = numbers[0]
                    }
                }
                lower.contains("egl mtrack") || lower.contains("gl mtrack") || lower.contains("graphics") -> {
                    val numbers = Regex("\\d+").findAll(trimmed).map { it.value.toLong() }.toList()
                    if (numbers.isNotEmpty()) {
                        result[3] = (result[3] + numbers[0])
                    }
                }
            }
        }
        if (result[0] == 0L && multiProcessSum > 0L) {
            result[0] = multiProcessSum
        }
        return result
    }

    fun parseNetstatsOutput(text: String, targetUid: Int): Pair<Long, Long> {
        var rxTotal = 0L
        var txTotal = 0L
        val uidRegex = Regex("\\buid=$targetUid\\b")

        text.lineSequence().forEach { line ->
            if (uidRegex.containsMatchIn(line)) {
                // Keep base un-tagged sockets (tag=0x0 or no tag) to prevent double counting
                val isBaseTag = !line.contains("tag=") || line.contains("tag=0x0") || line.contains("tag=0 ")
                if (isBaseTag) {
                    val rxMatch = Regex("(?:rxBytes|rb)=(\\d+)").find(line)
                    val txMatch = Regex("(?:txBytes|tb)=(\\d+)").find(line)
                    if (rxMatch != null) {
                        rxTotal += rxMatch.groupValues[1].toLongOrNull() ?: 0L
                    }
                    if (txMatch != null) {
                        txTotal += txMatch.groupValues[1].toLongOrNull() ?: 0L
                    }
                }
            }
        }
        return Pair(rxTotal, txTotal)
    }

    fun isAppInForeground(activitiesText: String, packageName: String): Boolean {
        var foundFocusLine = false
        for (line in activitiesText.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("mCurrentFocus") || trimmed.startsWith("mFocusedApp")) {
                foundFocusLine = true
                if (trimmed.contains("$packageName/") || trimmed.contains("$packageName}") || trimmed.contains(" $packageName ")) {
                    return true
                }
            }
        }
        if (foundFocusLine) return false

        for (line in activitiesText.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("topResumedActivity=")) {
                return trimmed.contains("$packageName/")
            }
        }
        return false
    }

    private fun extractTopActivity(text: String, packageName: String): String? {
        text.lineSequence().forEach { line ->
            if (line.contains(packageName) && (line.contains("ActivityRecord") || line.contains("mResumedActivity") || line.contains("topResumedActivity"))) {
                val match = Regex("$packageName/([a-zA-Z0-9_.]+)").find(line)
                if (match != null) {
                    val component = match.groupValues[1]
                    return component.removePrefix(".")
                }
            }
        }
        return null
    }

    private fun extractServices(text: String, packageName: String): List<String> {
        val services = mutableListOf<String>()
        text.lineSequence().forEach { line ->
            if (line.contains("ServiceRecord{") && line.contains(packageName)) {
                val match = Regex("$packageName/([a-zA-Z0-9_.]+)").find(line)
                if (match != null) {
                    val simpleName = match.groupValues[1].removePrefix(".")
                    if (!services.contains(simpleName)) {
                        services.add(simpleName)
                    }
                }
            }
        }
        return services
    }

    fun parseConnections(text: String): List<NetworkConnection> {
        val connections = mutableListOf<NetworkConnection>()
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("tcp") || trimmed.startsWith("udp")) {
                val parts = trimmed.split(Regex("\\s+"))
                if (parts.size >= 5) {
                    val proto = parts[0].uppercase(Locale.ROOT)
                    val secondPart = parts.getOrNull(1) ?: ""
                    val isSsFormat = secondPart.toIntOrNull() == null

                    val state: String
                    val local: String
                    val remote: String

                    if (isSsFormat) {
                        state = secondPart
                        local = parts.getOrNull(4) ?: ""
                        remote = parts.getOrNull(5) ?: ""
                    } else {
                        local = parts.getOrNull(3) ?: ""
                        remote = parts.getOrNull(4) ?: ""
                        state = parts.getOrNull(5) ?: "ESTABLISHED"
                    }

                    if (remote.isNotBlank() && remote != "*:*" && !remote.startsWith("0.0.0.0") && !remote.startsWith("[::]")) {
                        connections.add(
                            NetworkConnection(
                                protocol = proto,
                                localAddress = local,
                                remoteAddress = remote,
                                state = state,
                            )
                        )
                    }
                }
            }
        }
        return connections.take(15) // Limit to top 15 connections
    }

    private fun parsePermissions(packageInfo: PackageInfo): List<AppPermissionItem> {
        val requested = packageInfo.requestedPermissions ?: return emptyList()
        val flags = packageInfo.requestedPermissionsFlags ?: IntArray(requested.size)

        return requested.indices.mapNotNull { i ->
            val permName = requested[i]
            val isGranted = if (flags.size > i) {
                (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            } else false

            val (friendlyLabel, isDangerous) = formatPermission(permName)
            if (isDangerous || permName.contains("INTERNET") || permName.contains("NETWORK") || permName.contains("WAKE_LOCK")) {
                AppPermissionItem(
                    permission = permName,
                    label = friendlyLabel,
                    isGranted = isGranted,
                    isDangerous = isDangerous,
                )
            } else null
        }.sortedWith(compareByDescending<AppPermissionItem> { it.isGranted }.thenByDescending { it.isDangerous })
    }

    private fun formatPermission(perm: String): Pair<String, Boolean> {
        return when {
            perm.endsWith(".CAMERA") -> "Câmera" to true
            perm.endsWith(".RECORD_AUDIO") -> "Microfone" to true
            perm.endsWith(".ACCESS_FINE_LOCATION") -> "Localização Precisa" to true
            perm.endsWith(".ACCESS_COARSE_LOCATION") -> "Localização Aproximada" to true
            perm.endsWith(".ACCESS_BACKGROUND_LOCATION") -> "Localização em Segundo Plano" to true
            perm.endsWith(".READ_CONTACTS") -> "Ler Contatos" to true
            perm.endsWith(".WRITE_CONTACTS") -> "Gravar Contatos" to true
            perm.endsWith(".READ_EXTERNAL_STORAGE") -> "Ler Armazenamento" to true
            perm.endsWith(".WRITE_EXTERNAL_STORAGE") -> "Gravar Armazenamento" to true
            perm.endsWith(".MANAGE_EXTERNAL_STORAGE") -> "Acesso Total aos Arquivos" to true
            perm.endsWith(".READ_MEDIA_IMAGES") -> "Ler Fotos" to true
            perm.endsWith(".READ_MEDIA_VIDEO") -> "Ler Vídeos" to true
            perm.endsWith(".READ_MEDIA_AUDIO") -> "Ler Áudios" to true
            perm.endsWith(".POST_NOTIFICATIONS") -> "Enviar Notificações" to true
            perm.endsWith(".READ_PHONE_STATE") -> "Estado do Telefone / IMEI" to true
            perm.endsWith(".CALL_PHONE") -> "Fazer Ligações" to true
            perm.endsWith(".READ_CALL_LOG") -> "Histórico de Chamadas" to true
            perm.endsWith(".SEND_SMS") -> "Enviar SMS" to true
            perm.endsWith(".RECEIVE_SMS") -> "Receber SMS" to true
            perm.endsWith(".BODY_SENSORS") -> "Sensores Corporais" to true
            perm.endsWith(".ACTIVITY_RECOGNITION") -> "Reconhecimento de Atividade Física" to true
            perm.endsWith(".BLUETOOTH_CONNECT") -> "Conectar via Bluetooth" to true
            perm.endsWith(".BLUETOOTH_SCAN") -> "Escanear Bluetooth" to true
            perm.endsWith(".INTERNET") -> "Acesso à Internet" to false
            perm.endsWith(".ACCESS_NETWORK_STATE") -> "Ver Estado da Rede" to false
            perm.endsWith(".WAKE_LOCK") -> "Impedir Hibernação" to false
            else -> perm.substringAfterLast(".") to false
        }
    }

    /**
     * Formats raw byte count into human-readable format (B, KB, MB, GB).
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format(java.util.Locale.US, "%.1f %s", value, units[digitGroups])
    }

    /**
     * Formats bytes per second into human-readable transfer rate (e.g. "3.2 MB/s").
     */
    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return ""
        return "${formatBytes(bytesPerSec)}/s"
    }
}
