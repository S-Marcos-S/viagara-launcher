// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.root

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
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

data class NetBreakdown(
    val rxTotal: Long = 0L,
    val txTotal: Long = 0L,
    val rxToday: Long = 0L,
    val txToday: Long = 0L,
    val rx7Days: Long = 0L,
    val tx7Days: Long = 0L,
)

data class AppBatteryInspectionData(
    val totalMah: Double = 0.0,
    val foregroundMah: Double = 0.0,
    val backgroundMah: Double = 0.0,
    val cpuMah: Double = 0.0,
    val wakelockMah: Double = 0.0,
    val mobileRadioMah: Double = 0.0,
    val wifiMah: Double = 0.0,
    val percentTotalDrain: Double = 0.0,
    val wakelockTimeSec: Long = 0L,
    val foregroundTimeSec: Long = 0L,
    val backgroundTimeSec: Long = 0L,
    val wakeupAlarmsCount: Int = 0,
    val powerImpact: PowerImpactLevel = PowerImpactLevel.LOW,
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
    val rxBytesToday: Long = 0L,
    val txBytesToday: Long = 0L,
    val rxBytes7Days: Long = 0L,
    val txBytes7Days: Long = 0L,
    val rxSpeedBps: Long = 0L,
    val txSpeedBps: Long = 0L,
    val activeConnections: List<NetworkConnection>,
    val topActivity: String?,
    val activeServices: List<String>,
    val permissions: List<AppPermissionItem>,
    val batteryData: AppBatteryInspectionData? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

object AppRootInspector {

    /**
     * Checks whether superuser / root privileges are available on the device.
     */
    fun isRootAvailable(): Boolean = dev.victorialauncher.update.RootInstaller.isRootAvailable()

    private data class ExtendedNetSnapshot(
        val timestamp: Long,
        val realtimeRx: Long,
        val realtimeTx: Long,
    )
    private val lastExtendedSnapshots = ConcurrentHashMap<String, ExtendedNetSnapshot>()

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

            val extraPidsScript = if (extraPackageName != null && dlManagerUid != null) {
                """
                pidof $extraPackageName 2>/dev/null
                for p in ${'$'}(pgrep -u $dlManagerUid 2>/dev/null); do
                    cmd=${'$'}(cat /proc/${'$'}p/cmdline 2>/dev/null | tr '\0' ' ')
                    case "${'$'}cmd" in
                        $extraPackageName|$extraPackageName\ *|$extraPackageName:*|" $extraPackageName"*)
                            echo "${'$'}p"
                            ;;
                    esac
                done
                """.trimIndent()
            } else ""

            val extraSmapsScript = if (extraPackageName != null && dlManagerUid != null) {
                """
                for p in ${'$'}(pidof $extraPackageName 2>/dev/null; for p2 in ${'$'}(pgrep -u $dlManagerUid 2>/dev/null); do cmd=${'$'}(cat /proc/${'$'}p2/cmdline 2>/dev/null | tr '\0' ' '); case "${'$'}cmd" in $extraPackageName|$extraPackageName\ *|$extraPackageName:*|" $extraPackageName"*) echo "${'$'}p2";; esac; done | sort -u); do
                    echo "---PID:${'$'}p---"
                    echo "---IO---"
                    cat /proc/${'$'}p/io 2>/dev/null
                done
                """.trimIndent()
            } else ""

            val dlmNetStatsScript = if (dlManagerUid != null) {
                """
                echo "===NET_BPF_DLM==="
                dumpsys netstats 2>/dev/null | grep -E '^ *$dlManagerUid '
                echo "===NET_STATS_DLM==="
                dumpsys netstats detail 2>/dev/null | grep -A 2 "uid=$dlManagerUid" || dumpsys netstats 2>/dev/null | grep -A 2 "uid=$dlManagerUid"
                cat /proc/net/xt_qtaguid/stats 2>/dev/null | grep " $dlManagerUid "
                echo "===UID_STAT_DLM==="
                cat /proc/uid_stat/$dlManagerUid/tcp_rcv 2>/dev/null
                echo "---TX---"
                cat /proc/uid_stat/$dlManagerUid/tcp_snd 2>/dev/null
                """.trimIndent()
            } else ""

            val dlmConnFilter = if (dlManagerUid != null) " -e \"$dlManagerUid\"" else ""

            // High-performance unified shell inspection script without toybox-incompatible regexes (\b, \s, \d)
            val shellScript = """
                appops set dev.victorialauncher GET_USAGE_STATS allow 2>/dev/null
                pm grant dev.victorialauncher android.permission.PACKAGE_USAGE_STATS 2>/dev/null
                echo "===PIDS==="
                pidof $packageName 2>/dev/null
                for p in ${'$'}(pgrep -u $uid 2>/dev/null); do
                    cmd=${'$'}(cat /proc/${'$'}p/cmdline 2>/dev/null | tr '\0' ' ')
                    case "${'$'}cmd" in
                        $packageName|$packageName\ *|$packageName:*|" $packageName"*)
                            echo "${'$'}p"
                            ;;
                    esac
                done
                $extraPidsScript
                echo "===TOP==="
                top -b -n 1 -q 2>/dev/null | $topGrep
                echo "===PS==="
                ps -A -o PID,USER,%CPU,%MEM,ARGS 2>/dev/null | $psGrep
                echo "===SMAPS==="
                for p in ${'$'}(pidof $packageName 2>/dev/null; for p2 in ${'$'}(pgrep -u $uid 2>/dev/null); do cmd=${'$'}(cat /proc/${'$'}p2/cmdline 2>/dev/null | tr '\0' ' '); case "${'$'}cmd" in $packageName|$packageName\ *|$packageName:*|" $packageName"*) echo "${'$'}p2";; esac; done | sort -u); do
                    echo "---PID:${'$'}p---"
                    cat /proc/${'$'}p/smaps_rollup 2>/dev/null
                    cat /proc/${'$'}p/status 2>/dev/null | grep -E "^(VmRSS|RssAnon|RssFile):"
                    echo "---IO---"
                    cat /proc/${'$'}p/io 2>/dev/null
                done
                $extraSmapsScript
                echo "===MEMINFO==="
                dumpsys meminfo $packageName 2>/dev/null | grep -E "TOTAL PSS|TOTAL      PSS|TOTAL:|Dalvik Heap|Native Heap|EGL mtrack|GL mtrack|Graphics|TOTAL RSS|Total PSS by process:|[0-9,]+K: *$packageName"
                echo "===NET_BPF==="
                dumpsys netstats 2>/dev/null | grep -E '^ *$uid '
                echo "===NET_STATS==="
                dumpsys netstats detail 2>/dev/null | grep -A 2 "uid=$uid" || dumpsys netstats 2>/dev/null | grep -A 2 "uid=$uid"
                cat /proc/net/xt_qtaguid/stats 2>/dev/null | grep " $uid "
                echo "===UID_STAT==="
                cat /proc/uid_stat/$uid/tcp_rcv 2>/dev/null
                echo "---TX---"
                cat /proc/uid_stat/$uid/tcp_snd 2>/dev/null
                $dlmNetStatsScript
                echo "===CONNECTIONS==="
                ss -t -u -p -n -e -i 2>/dev/null | grep -e "$packageName" -e "$uid"$dlmConnFilter -A 1 || ss -tupn 2>/dev/null | grep -e "$packageName" -e "$uid"$dlmConnFilter || netstat -tlpn 2>/dev/null | grep -e "$packageName" -e "$uid"$dlmConnFilter
                echo "===ACTIVITIES==="
                dumpsys window 2>/dev/null | grep -E "mCurrentFocus|mFocusedApp"
                dumpsys activity activities 2>/dev/null | grep -E "topResumedActivity|mResumedActivity"
                echo "===SERVICES==="
                dumpsys activity services $packageName 2>/dev/null | grep -E "ServiceRecord|app=ProcessRecord"
                echo "===BATTERYSTATS==="
                dumpsys batterystats --charged $packageName 2>/dev/null || dumpsys batterystats $packageName 2>/dev/null
            """.trimIndent()

            val rawOutput = runSuCommand(shellScript).getOrDefault("")

            // Parse sections
            val sections = parseSections(rawOutput)

            // 1. PIDs & Process details
            val pidsText = sections["PIDS"] ?: ""
            val rawPids = pidsText.split(Regex("\\s+"))
                .mapNotNull { it.trim().toIntOrNull() }
                .distinct()

            val coresCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

            val topText = sections["TOP"] ?: ""
            val topProcesses = parseTopOutput(topText, packageName, extraPackageName, targetUid = uid, coresCount = coresCount)

            val psText = sections["PS"] ?: ""
            val psProcesses = parsePsOutput(psText, packageName, extraPackageName, targetUid = uid, coresCount = coresCount)

            val processes = mutableListOf<ProcessDetail>()
            topProcesses.forEach { processes.add(it) }
            psProcesses.forEach { psProc ->
                if (processes.none { it.pid == psProc.pid }) {
                    processes.add(psProc)
                }
            }

            // Fallback for any verified raw PIDs not captured in top or ps
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

            val totalCpu = processes.sumOf { it.cpuPercent }.coerceIn(0.0, 100.0)

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

            // 4. Native Android Network Stats (Today & Last 7 Days) + Real-Time Telemetry
            val now = System.currentTimeMillis()
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val todayStartMs = calendar.timeInMillis
            val todayStartSec = todayStartMs / 1000
            val sevenDaysAgoMs = now - (7L * 24 * 60 * 60 * 1000)
            val sevenDaysAgoSec = sevenDaysAgoMs / 1000

            val bpfText = sections["NET_BPF"] ?: ""
            val (bpfRx, bpfTx) = parseBpfUidStats(bpfText, uid)

            val netStatsText = sections["NET_STATS"] ?: ""
            val nsBreakdown = parseNetstatsOutput(netStatsText, uid, todayStartSec, sevenDaysAgoSec)
            val (qtagRx, qtagTx) = parseQtaguidStats(netStatsText, uid)
            val tsRx = runCatching { TrafficStats.getUidRxBytes(uid) }.getOrDefault(-1L)
            val tsTx = runCatching { TrafficStats.getUidTxBytes(uid) }.getOrDefault(-1L)

            val uidStatText = sections["UID_STAT"] ?: ""
            val (uidStatRx, uidStatTx) = uidStatStatFallback(uidStatText)

            val appRealtimeRx = when {
                bpfRx > 0L -> bpfRx
                tsRx >= 0L -> tsRx
                qtagRx > 0L -> qtagRx
                uidStatRx > 0L -> uidStatRx
                else -> 0L
            }
            val appRealtimeTx = when {
                bpfTx > 0L -> bpfTx
                tsTx >= 0L -> tsTx
                qtagTx > 0L -> qtagTx
                uidStatTx > 0L -> uidStatTx
                else -> 0L
            }

            val appNetRx = maxOf(if (tsRx > 0L) tsRx else 0L, bpfRx, nsBreakdown.rxTotal, qtagRx, uidStatRx)
            val appNetTx = maxOf(if (tsTx > 0L) tsTx else 0L, bpfTx, nsBreakdown.txTotal, qtagTx, uidStatTx)

            // Native Android NetworkStatsManager query for exact system accounting
            val (nsmTodayRx, nsmTodayTx) = getNativeAppUsage(context, uid, todayStartMs, now)
            val (nsm7DaysRx, nsm7DaysTx) = getNativeAppUsage(context, uid, sevenDaysAgoMs, now)

            var appRxToday = maxOf(nsBreakdown.rxToday, nsmTodayRx)
            var appTxToday = maxOf(nsBreakdown.txToday, nsmTodayTx)
            var appRx7Days = maxOf(nsBreakdown.rx7Days, nsm7DaysRx)
            var appTx7Days = maxOf(nsBreakdown.tx7Days, nsm7DaysTx)

            // Download Provider fallback for Play Store (dual-UID tracking)
            var dlmNetRx = 0L
            var dlmNetTx = 0L
            var dlmRxToday = 0L
            var dlmTxToday = 0L
            var dlmRx7Days = 0L
            var dlmTx7Days = 0L

            var dlmRealtimeRx = 0L
            var dlmRealtimeTx = 0L

            if (dlManagerUid != null) {
                val dlmBpfText = sections["NET_BPF_DLM"] ?: ""
                val (dlmBpfRx, dlmBpfTx) = parseBpfUidStats(dlmBpfText, dlManagerUid)

                val dlmNetStatsText = sections["NET_STATS_DLM"] ?: ""
                val dlmNsBreakdown = parseNetstatsOutput(dlmNetStatsText, dlManagerUid, todayStartSec, sevenDaysAgoSec)
                val (dlmQtagRx, dlmQtagTx) = parseQtaguidStats(dlmNetStatsText, dlManagerUid)
                val tsDlmRx = runCatching { TrafficStats.getUidRxBytes(dlManagerUid) }.getOrDefault(-1L)
                val tsDlmTx = runCatching { TrafficStats.getUidTxBytes(dlManagerUid) }.getOrDefault(-1L)

                val dlmUidStatText = sections["UID_STAT_DLM"] ?: ""
                val (dlmUidRx, dlmUidTx) = uidStatStatFallback(dlmUidStatText)

                dlmRealtimeRx = when {
                    dlmBpfRx > 0L -> dlmBpfRx
                    tsDlmRx >= 0L -> tsDlmRx
                    dlmQtagRx > 0L -> dlmQtagRx
                    dlmUidRx > 0L -> dlmUidRx
                    else -> 0L
                }
                dlmRealtimeTx = when {
                    dlmBpfTx > 0L -> dlmBpfTx
                    tsDlmTx >= 0L -> tsDlmTx
                    dlmQtagTx > 0L -> dlmQtagTx
                    dlmUidTx > 0L -> dlmUidTx
                    else -> 0L
                }

                dlmNetRx = maxOf(if (tsDlmRx > 0L) tsDlmRx else 0L, dlmBpfRx, dlmNsBreakdown.rxTotal, dlmQtagRx, dlmUidRx)
                dlmNetTx = maxOf(if (tsDlmTx > 0L) tsDlmTx else 0L, dlmBpfTx, dlmNsBreakdown.txTotal, dlmQtagTx, dlmUidTx)

                val (nsmDlmTodayRx, nsmDlmTodayTx) = getNativeAppUsage(context, dlManagerUid, todayStartMs, now)
                val (nsmDlm7DaysRx, nsmDlm7DaysTx) = getNativeAppUsage(context, dlManagerUid, sevenDaysAgoMs, now)

                dlmRxToday = maxOf(dlmNsBreakdown.rxToday, nsmDlmTodayRx)
                dlmTxToday = maxOf(dlmNsBreakdown.txToday, nsmDlmTodayTx)
                dlmRx7Days = maxOf(dlmNsBreakdown.rx7Days, nsmDlm7DaysRx)
                dlmTx7Days = maxOf(dlmNsBreakdown.tx7Days, nsmDlm7DaysTx)
            }

            // Real-time network accounting strictly from kernel network counters (BPF / TrafficStats / qtaguid / uid_stat)
            val currentRealtimeRx = appRealtimeRx + dlmRealtimeRx
            val currentRealtimeTx = appRealtimeTx + dlmRealtimeTx

            // Socket Telemetry (TCP internal stats)
            val connText = sections["CONNECTIONS"] ?: ""
            val (sockRx, sockTx) = parseSocketBytes(connText)

            val combinedNetRx = appNetRx + dlmNetRx
            val combinedNetTx = appNetTx + dlmNetTx
            val combinedRxToday = appRxToday + dlmRxToday
            val combinedTxToday = appTxToday + dlmTxToday
            val combinedRx7Days = appRx7Days + dlmRx7Days
            val combinedTx7Days = appTx7Days + dlmTx7Days

            // Real-time transfer speed calculation strictly from monotonic network counters
            val prevSample = lastExtendedSnapshots[packageName]
            var rxSpeedBps = 0L
            var txSpeedBps = 0L

            if (prevSample != null) {
                val deltaMs = now - prevSample.timestamp
                if (deltaMs in 400..30000) {
                    val dt = deltaMs / 1000.0

                    val dRx = currentRealtimeRx - prevSample.realtimeRx
                    val dTx = currentRealtimeTx - prevSample.realtimeTx

                    if (prevSample.realtimeRx > 0L && dRx > 0) {
                        rxSpeedBps = (dRx / dt).toLong()
                    }
                    if (prevSample.realtimeTx > 0L && dTx > 0) {
                        txSpeedBps = (dTx / dt).toLong()
                    }
                }
            }

            lastExtendedSnapshots[packageName] = ExtendedNetSnapshot(
                timestamp = now,
                realtimeRx = currentRealtimeRx,
                realtimeTx = currentRealtimeTx,
            )

            // Total Bytes to display (strictly network traffic, never disk I/O)
            val rxBytes = when {
                combinedNetRx > 0L -> combinedNetRx
                currentRealtimeRx > 0L -> currentRealtimeRx
                sockRx > 0L -> sockRx
                else -> 0L
            }

            val txBytes = when {
                combinedNetTx > 0L -> combinedNetTx
                currentRealtimeTx > 0L -> currentRealtimeTx
                sockTx > 0L -> sockTx
                else -> 0L
            }

            // 5. Active connections
            val activeConnections = parseConnections(connText)

            // 6. Active services
            val servicesText = sections["SERVICES"] ?: ""
            val activeServices = extractServices(servicesText, packageName)

            // 7. Permissions
            val permissions = parsePermissions(packageInfo)

            // 8. Battery & Power Telemetry
            val batteryText = sections["BATTERYSTATS"] ?: ""
            val batteryData = parseBatteryStats(batteryText, uid)

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
                rxBytesToday = combinedRxToday,
                txBytesToday = combinedTxToday,
                rxBytes7Days = combinedRx7Days,
                txBytes7Days = combinedTx7Days,
                rxSpeedBps = rxSpeedBps,
                txSpeedBps = txSpeedBps,
                activeConnections = activeConnections,
                topActivity = topActivity,
                activeServices = activeServices,
                permissions = permissions,
                batteryData = batteryData,
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

    fun parseBatteryStats(rawText: String, uid: Int, deviceBatteryCapacityMah: Double = 4500.0): AppBatteryInspectionData {
        if (rawText.isBlank()) return AppBatteryInspectionData()

        var totalMah = 0.0
        var cpuMah = 0.0
        var wakelockMah = 0.0
        var radioMah = 0.0
        var wifiMah = 0.0

        var fgTimeSec = 0L
        var bgTimeSec = 0L
        var wlTimeSec = 0L
        var wakeups = 0

        rawText.lineSequence().forEach { line ->
            val trimmed = line.trim()

            // 1. Drain extraction: "Computed drain: 14.2" or "drain: 14.2"
            if (trimmed.contains("Computed drain:") || trimmed.contains("drain:")) {
                val match = Regex("(?:Computed drain|drain):\\s*([0-9]+(?:\\.[0-9]+)?)").find(trimmed)
                if (match != null) {
                    val d = match.groupValues[1].toDoubleOrNull() ?: 0.0
                    if (d > totalMah) totalMah = d
                }
            }

            // 2. Component breakdowns: ( cpu=12.4 wake=1.2 radio=0.5 wifi=0.1 )
            if (trimmed.contains("cpu=") || trimmed.contains("wake=")) {
                Regex("cpu=([0-9]+(?:\\.[0-9]+)?)").find(trimmed)?.let {
                    cpuMah = it.groupValues[1].toDoubleOrNull() ?: cpuMah
                }
                Regex("wake=([0-9]+(?:\\.[0-9]+)?)").find(trimmed)?.let {
                    wakelockMah = it.groupValues[1].toDoubleOrNull() ?: wakelockMah
                }
                Regex("radio=([0-9]+(?:\\.[0-9]+)?)").find(trimmed)?.let {
                    radioMah = it.groupValues[1].toDoubleOrNull() ?: radioMah
                }
                Regex("wifi=([0-9]+(?:\\.[0-9]+)?)").find(trimmed)?.let {
                    wifiMah = it.groupValues[1].toDoubleOrNull() ?: wifiMah
                }
            }

            // 3. Foreground activity time
            if (trimmed.startsWith("Foreground activities:") || trimmed.startsWith("Foreground:") || trimmed.startsWith("Foreground for:")) {
                val timeStr = trimmed.substringAfter(":").substringBefore("(").trim()
                val sec = parseDurationSec(timeStr)
                if (sec > fgTimeSec) fgTimeSec = sec
            }

            // 4. Background time
            if (trimmed.startsWith("Background:") || trimmed.startsWith("Background for:") || trimmed.startsWith("Background cpu time:")) {
                val timeStr = trimmed.substringAfter(":").trim()
                val sec = parseDurationSec(timeStr)
                if (sec > bgTimeSec) bgTimeSec = sec
            }

            // 5. Wakelocks
            if (trimmed.startsWith("Partial wakelocks:") || trimmed.startsWith("Wakelocks:") || trimmed.contains("wakelock")) {
                val timeMatch = Regex("([0-9]+h\\s*)?([0-9]+m\\s*)?([0-9]+s\\s*)?([0-9]+ms)?").find(trimmed.substringAfter(":"))
                if (timeMatch != null && timeMatch.value.isNotBlank()) {
                    val sec = parseDurationSec(timeMatch.value)
                    if (sec > wlTimeSec) wlTimeSec = sec
                }
            }

            // 6. Wakeups / Alarms
            if (trimmed.startsWith("Wakeups:") || trimmed.startsWith("Alarms:")) {
                val count = Regex("\\d+").find(trimmed.substringAfter(":"))?.value?.toIntOrNull() ?: 0
                if (count > wakeups) wakeups = count
            }
        }

        // Fallback: If totalMah is 0 but components are known, sum them
        val sumComponents = cpuMah + wakelockMah + radioMah + wifiMah
        if (totalMah <= 0.0 && sumComponents > 0.0) {
            totalMah = sumComponents
        }

        // Foreground vs Background proportional division
        val totalActiveTime = (fgTimeSec + bgTimeSec).coerceAtLeast(1L)
        val fgRatio = fgTimeSec.toDouble() / totalActiveTime.toDouble()

        val fgMah = when {
            fgTimeSec > 0 && bgTimeSec > 0 -> totalMah * fgRatio
            fgTimeSec > 0 -> totalMah
            bgTimeSec > 0 -> 0.0
            else -> totalMah * 0.5
        }

        val bgMah = (totalMah - fgMah).coerceAtLeast(0.0)

        val percentDrain = if (deviceBatteryCapacityMah > 0) {
            (totalMah / deviceBatteryCapacityMah) * 100.0
        } else 0.0

        val impact = when {
            totalMah > 50.0 || wlTimeSec > 600 || wakeups > 100 -> PowerImpactLevel.VERY_HIGH
            totalMah > 20.0 || wlTimeSec > 180 || wakeups > 30 -> PowerImpactLevel.HIGH
            totalMah > 5.0 || wlTimeSec > 30 || wakeups > 10 -> PowerImpactLevel.MEDIUM
            totalMah > 0.5 || wlTimeSec > 0 -> PowerImpactLevel.LOW
            else -> PowerImpactLevel.MINIMAL
        }

        return AppBatteryInspectionData(
            totalMah = totalMah,
            foregroundMah = fgMah,
            backgroundMah = bgMah,
            cpuMah = cpuMah,
            wakelockMah = wakelockMah,
            mobileRadioMah = radioMah,
            wifiMah = wifiMah,
            percentTotalDrain = percentDrain,
            wakelockTimeSec = wlTimeSec,
            foregroundTimeSec = fgTimeSec,
            backgroundTimeSec = bgTimeSec,
            wakeupAlarmsCount = wakeups,
            powerImpact = impact,
        )
    }

    private fun parseDurationSec(str: String): Long {
        if (str.isBlank()) return 0L
        var total = 0L
        Regex("(\\d+)\\s*d").find(str)?.groupValues?.get(1)?.toLongOrNull()?.let { total += it * 86400L }
        Regex("(\\d+)\\s*h").find(str)?.groupValues?.get(1)?.toLongOrNull()?.let { total += it * 3600L }
        Regex("(\\d+)\\s*m(?!s)").find(str)?.groupValues?.get(1)?.toLongOrNull()?.let { total += it * 60L }
        Regex("(\\d+)\\s*s").find(str)?.groupValues?.get(1)?.toLongOrNull()?.let { total += it }
        Regex("(\\d+)\\s*ms").find(str)?.groupValues?.get(1)?.toLongOrNull()?.let { if (it >= 500) total += 1L }
        return total
    }

    fun isPackageProcess(cmd: String, packageName: String): Boolean {
        val trimmed = cmd.trim()
        if (!trimmed.contains(packageName)) return false
        val tokens = trimmed.split(Regex("\\s+"))
        val firstToken = tokens.firstOrNull()?.substringAfterLast("/") ?: return false

        // Discard shell interpreters, system binaries, grep, and diagnostic command lines
        val ignoredCommands = setOf("grep", "su", "sh", "bash", "toybox", "top", "ps", "pidof", "pgrep", "dumpsys", "cat", "echo", "tr", "kill")
        if (ignoredCommands.contains(firstToken.lowercase(Locale.ROOT))) {
            return false
        }

        val procName = firstToken.substringAfterLast("=").removeSurrounding("[", "]").removeSurrounding("(", ")")
        if (procName == packageName || procName.startsWith("$packageName:")) {
            return true
        }

        for (token in tokens) {
            if (token.startsWith("--nice-name=")) {
                val name = token.removePrefix("--nice-name=")
                if (name == packageName || name.startsWith("$packageName:")) {
                    return true
                }
            }
        }
        return false
    }

    fun parseTopOutput(
        text: String,
        packageName: String,
        extraPackageName: String? = null,
        targetUid: Int? = null,
        coresCount: Int = 1,
    ): List<ProcessDetail> {
        val list = mutableListOf<ProcessDetail>()
        val timeRegex = Regex("^\\d+:\\d+.*")
        val effectiveCores = coresCount.coerceAtLeast(1)
        text.lineSequence().forEach { line ->
            val tokens = line.trim().split(Regex("\\s+"))
            if (tokens.size >= 5) {
                val pid = tokens[0].toIntOrNull()
                val user = tokens.getOrNull(1) ?: "unknown"

                if (targetUid != null && targetUid >= 10000 && (user == "root" || user == "0")) {
                    return@forEach
                }

                val timeIdx = tokens.indexOfFirst { timeRegex.matches(it) }

                val (cpu, mem, cmd) = if (timeIdx in 2..(tokens.size - 2)) {
                    val rawCpuVal = tokens[timeIdx - 2].replace("%", "").toDoubleOrNull()
                    val cpuVal = rawCpuVal?.let { (it / effectiveCores).coerceIn(0.0, 100.0) }
                    val memVal = tokens[timeIdx - 1].replace("%", "").toDoubleOrNull()
                    val cmdVal = tokens.drop(timeIdx + 1).joinToString(" ")
                    Triple(cpuVal, memVal, cmdVal)
                } else if (tokens.size >= 12) {
                    val rawCpuVal = tokens[8].replace("%", "").toDoubleOrNull()
                    val cpuVal = rawCpuVal?.let { (it / effectiveCores).coerceIn(0.0, 100.0) }
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

    fun parsePsOutput(
        text: String,
        packageName: String,
        extraPackageName: String? = null,
        targetUid: Int? = null,
        coresCount: Int = 1,
    ): List<ProcessDetail> {
        val list = mutableListOf<ProcessDetail>()
        val effectiveCores = coresCount.coerceAtLeast(1)
        text.lineSequence().forEach { line ->
            val tokens = line.trim().split(Regex("\\s+"))
            if (tokens.size >= 5) {
                val pid = tokens[0].toIntOrNull()
                if (pid != null) {
                    val user = tokens.getOrNull(1) ?: "unknown"

                    if (targetUid != null && targetUid >= 10000 && (user == "root" || user == "0")) {
                        return@forEach
                    }

                    val rawCpu = tokens.getOrNull(2)?.replace("%", "")?.toDoubleOrNull() ?: 0.0
                    val cpu = (rawCpu / effectiveCores).coerceIn(0.0, 100.0)
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

    fun parseProcIo(text: String): Pair<Long, Long> {
        var totalRchar = 0L
        var totalWchar = 0L
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("rchar:")) {
                totalRchar += trimmed.substringAfter("rchar:").trim().toLongOrNull() ?: 0L
            } else if (trimmed.startsWith("wchar:")) {
                totalWchar += trimmed.substringAfter("wchar:").trim().toLongOrNull() ?: 0L
            }
        }
        return Pair(totalRchar, totalWchar)
    }

    fun parseSocketBytes(text: String): Pair<Long, Long> {
        var totalRx = 0L
        var totalTx = 0L
        text.lineSequence().forEach { line ->
            val rxMatch = Regex("bytes_received:(\\d+)").find(line)
            if (rxMatch != null) {
                totalRx += rxMatch.groupValues[1].toLongOrNull() ?: 0L
            }
            val txMatch = Regex("(?:bytes_acked|bytes_sent):(\\d+)").find(line)
            if (txMatch != null) {
                totalTx += txMatch.groupValues[1].toLongOrNull() ?: 0L
            }
        }
        return Pair(totalRx, totalTx)
    }

    fun parseBpfUidStats(text: String, targetUid: Int): Pair<Long, Long> {
        var rxTotal = 0L
        var txTotal = 0L
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val tokens = trimmed.split(Regex("\\s+"))
            if (tokens.size >= 5) {
                val uid = tokens[0].toIntOrNull()
                if (uid == targetUid) {
                    val rx = tokens[1].toLongOrNull() ?: 0L
                    val tx = tokens[3].toLongOrNull() ?: 0L
                    if (rx > 0L || tx > 0L) {
                        return Pair(rx, tx)
                    }
                }
            }
            if (tokens.size >= 8) {
                val tag = tokens[2]
                val uid = tokens[3].toIntOrNull()
                if (uid == targetUid && (tag == "0x0" || tag == "0")) {
                    rxTotal += tokens[5].toLongOrNull() ?: 0L
                    txTotal += tokens[7].toLongOrNull() ?: 0L
                }
            }
        }
        return Pair(rxTotal, txTotal)
    }

    fun parseQtaguidStats(text: String, targetUid: Int): Pair<Long, Long> {
        var totalRx = 0L
        var totalTx = 0L
        text.lineSequence().forEach { line ->
            val tokens = line.trim().split(Regex("\\s+"))
            if (tokens.size >= 8) {
                val tag = tokens[2]
                val uid = tokens[3].toIntOrNull()
                if (uid == targetUid && (tag == "0x0" || tag == "0")) {
                    totalRx += tokens[5].toLongOrNull() ?: 0L
                    totalTx += tokens[7].toLongOrNull() ?: 0L
                }
            }
        }
        return Pair(totalRx, totalTx)
    }

    fun parseNetstatsOutput(
        text: String,
        targetUid: Int,
        todayStartSec: Long = 0L,
        sevenDaysAgoSec: Long = 0L,
    ): NetBreakdown {
        var rxTotal = 0L
        var txTotal = 0L
        var rxToday = 0L
        var txToday = 0L
        var rx7Days = 0L
        var tx7Days = 0L
        var insideTargetUid = false
        val uidRegex = Regex("\\buid=$targetUid\\b")

        text.lineSequence().forEach { line ->
            if (line.contains("uid=")) {
                insideTargetUid = uidRegex.containsMatchIn(line)
            }
            if (insideTargetUid) {
                val isBaseTag = !line.contains("tag=") || line.contains("tag=0x0") || line.contains("tag=0 ")
                if (isBaseTag) {
                    val rxMatch = Regex("(?:rxBytes|rb)=(\\d+)").find(line)
                    val txMatch = Regex("(?:txBytes|tb)=(\\d+)").find(line)
                    val stMatch = Regex("st=(\\d+)").find(line)

                    val rx = rxMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    val tx = txMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    val st = stMatch?.groupValues?.get(1)?.toLongOrNull()

                    rxTotal += rx
                    txTotal += tx

                    if (st != null) {
                        if (todayStartSec > 0L && st >= todayStartSec) {
                            rxToday += rx
                            txToday += tx
                        }
                        if (sevenDaysAgoSec > 0L && st >= sevenDaysAgoSec) {
                            rx7Days += rx
                            tx7Days += tx
                        }
                    }
                }
            }
        }

        if (rxTotal == 0L && txTotal == 0L) {
            val (bpfRx, bpfTx) = parseBpfUidStats(text, targetUid)
            rxTotal = bpfRx
            txTotal = bpfTx
        }

        return NetBreakdown(
            rxTotal = rxTotal,
            txTotal = txTotal,
            rxToday = if (rxToday > 0L) rxToday else rxTotal,
            txToday = if (txToday > 0L) txToday else txTotal,
            rx7Days = if (rx7Days > 0L) rx7Days else rxTotal,
            tx7Days = if (tx7Days > 0L) tx7Days else txTotal,
        )
    }

    /**
     * Uses Android's native NetworkStatsManager system service to retrieve exact byte usage
     * (Wi-Fi + Mobile cellular) for any application UID within a time interval.
     */
    fun getNativeAppUsage(
        context: Context,
        uid: Int,
        startTimeMs: Long,
        endTimeMs: Long = System.currentTimeMillis(),
    ): Pair<Long, Long> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return Pair(0L, 0L)
        return runCatching {
            val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
                ?: return Pair(0L, 0L)

            var rxTotal = 0L
            var txTotal = 0L

            // 1. Wi-Fi
            runCatching {
                val statsWifi = nsm.queryDetailsForUid(ConnectivityManager.TYPE_WIFI, null, startTimeMs, endTimeMs, uid)
                val bucket = NetworkStats.Bucket()
                while (statsWifi.hasNextBucket()) {
                    statsWifi.getNextBucket(bucket)
                    rxTotal += bucket.rxBytes
                    txTotal += bucket.txBytes
                }
                statsWifi.close()
            }

            // 2. Mobile Cellular
            runCatching {
                val statsMobile = nsm.queryDetailsForUid(ConnectivityManager.TYPE_MOBILE, null, startTimeMs, endTimeMs, uid)
                val bucket = NetworkStats.Bucket()
                while (statsMobile.hasNextBucket()) {
                    statsMobile.getNextBucket(bucket)
                    rxTotal += bucket.rxBytes
                    txTotal += bucket.txBytes
                }
                statsMobile.close()
            }

            Pair(rxTotal, txTotal)
        }.getOrDefault(Pair(0L, 0L))
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
