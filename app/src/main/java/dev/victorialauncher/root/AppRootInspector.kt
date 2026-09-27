// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.root

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

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
    val activeConnections: List<NetworkConnection>,
    val topActivity: String?,
    val activeServices: List<String>,
    val permissions: List<AppPermissionItem>,
    val timestamp: Long = System.currentTimeMillis(),
)

object AppRootInspector {

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

            // Unified shell inspection script
            val shellScript = """
                echo "===PIDS==="
                pidof $packageName 2>/dev/null || pgrep -f $packageName 2>/dev/null
                echo "===PS==="
                ps -A -o PID,USER,%CPU,%MEM,CMD 2>/dev/null | grep $packageName
                echo "===MEMINFO==="
                dumpsys meminfo $packageName 2>/dev/null | grep -E "TOTAL PSS|TOTAL      PSS|TOTAL:|Dalvik Heap|Native Heap|EGL mtrack|GL mtrack|Graphics"
                echo "===NET_STATS==="
                cat /proc/uid_stat/$uid/tcp_rcv 2>/dev/null
                echo "---TX---"
                cat /proc/uid_stat/$uid/tcp_snd 2>/dev/null
                echo "===CONNECTIONS==="
                ss -tupn 2>/dev/null | grep $packageName || ss -tupn 2>/dev/null | grep $uid || netstat -tlpn 2>/dev/null | grep $packageName
                echo "===ACTIVITIES==="
                dumpsys activity activities 2>/dev/null | grep -E "mResumedActivity|topResumedActivity|ActivityRecord.*$packageName"
                echo "===SERVICES==="
                dumpsys activity services $packageName 2>/dev/null | grep -E "ServiceRecord\{|app=ProcessRecord"
            """.trimIndent()

            val rawOutput = runSuCommand(shellScript).getOrDefault("")

            // Parse sections
            val sections = parseSections(rawOutput)

            // 1. PIDs & Process details
            val pidsText = sections["PIDS"] ?: ""
            val rawPids = pidsText.split(Regex("\\s+"))
                .mapNotNull { it.trim().toIntOrNull() }
                .distinct()

            val psText = sections["PS"] ?: ""
            val processes = mutableListOf<ProcessDetail>()
            var totalCpu = 0.0

            psText.lineSequence().forEach { line ->
                val tokens = line.trim().split(Regex("\\s+"))
                if (tokens.size >= 5) {
                    val pid = tokens[0].toIntOrNull()
                    if (pid != null) {
                        val user = tokens.getOrNull(1) ?: "unknown"
                        val cpu = tokens.getOrNull(2)?.replace("%", "")?.toDoubleOrNull() ?: 0.0
                        val mem = tokens.getOrNull(3)?.replace("%", "")?.toDoubleOrNull() ?: 0.0
                        val cmd = tokens.drop(4).joinToString(" ")
                        if (cmd.contains(packageName)) {
                            totalCpu += cpu
                            processes.add(
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

            // Fallback if ps did not capture all pids
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

            // 2. Activities & Execution Status
            val activitiesText = sections["ACTIVITIES"] ?: ""
            val isForeground = activitiesText.contains("mResumedActivity") ||
                    activitiesText.contains("topResumedActivity") ||
                    (activitiesText.contains(packageName) && activitiesText.contains("Resumed"))

            val status = when {
                processes.isEmpty() -> AppProcessStatus.STOPPED
                isForeground -> AppProcessStatus.FOREGROUND
                else -> AppProcessStatus.BACKGROUND
            }

            val topActivity = extractTopActivity(activitiesText, packageName)

            // 3. Memory breakdown from dumpsys meminfo
            val meminfoText = sections["MEMINFO"] ?: ""
            val (totalPssKb, dalvikKb, nativeKb, graphicsKb) = parseMeminfo(meminfoText)

            // 4. Network stats
            val netStatsText = sections["NET_STATS"] ?: ""
            val netParts = netStatsText.split("---TX---")
            val rxBytes = netParts.getOrNull(0)?.trim()?.toLongOrNull() ?: 0L
            val txBytes = netParts.getOrNull(1)?.trim()?.toLongOrNull() ?: 0L

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

    private fun parseMeminfo(text: String): LongArray {
        // [totalPssKb, dalvikKb, nativeKb, graphicsKb]
        val result = LongArray(4) { 0L }
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val lower = trimmed.lowercase(Locale.ROOT)
            when {
                lower.startsWith("total pss:") || lower.startsWith("total:") || (lower.startsWith("total") && lower.contains("pss")) -> {
                    val numbers = Regex("\\d+").findAll(trimmed).map { it.value.toLong() }.toList()
                    if (numbers.isNotEmpty()) {
                        result[0] = numbers[0]
                    }
                }
                lower.contains("dalvik heap") -> {
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
        return result
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

    private fun parseConnections(text: String): List<NetworkConnection> {
        val connections = mutableListOf<NetworkConnection>()
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("tcp") || trimmed.startsWith("udp")) {
                val parts = trimmed.split(Regex("\\s+"))
                if (parts.size >= 5) {
                    val proto = parts[0].uppercase(Locale.ROOT)
                    val state = parts.getOrNull(1) ?: "UNKNOWN"
                    val local = parts.getOrNull(3) ?: ""
                    val remote = parts.getOrNull(4) ?: ""
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
}
