// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.root.log

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.victorialauncher.MainActivity
import dev.victorialauncher.R
import dev.victorialauncher.root.LogNotificationReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class CrashManager(private val context: Context) {

    companion object {
        const val CHANNEL_CRASHES_ID = "victoria_crashes_channel"
        const val EXTRA_OPEN_CRASH_ID = "dev.victorialauncher.extra.OPEN_CRASH_ID"
        const val EXTRA_CRASH_PACKAGE = "dev.victorialauncher.extra.CRASH_PACKAGE"
        private const val MAX_CRASH_RECORDS = 50
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val crashesDir = File(context.filesDir, "crashes").apply { if (!exists()) mkdirs() }
    private val crashesFile = File(crashesDir, "crashes_history.json")

    private val _crashes = MutableStateFlow<List<AppCrashRecord>>(emptyList())
    val crashes: StateFlow<List<AppCrashRecord>> = _crashes.asStateFlow()

    // Transient collector state for currently assembling crashes
    private var collectingType: CrashType? = null
    private var collectionStartTime = 0L
    private var collectedLines = mutableListOf<LogLine>()
    private var targetPid: String = ""

    init {
        createNotificationChannel()
        loadCrashes()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_CRASHES_ID,
                context.getString(R.string.log_notif_crashes_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.log_notif_crashes_channel_desc)
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    @Synchronized
    fun processLine(line: LogLine) {
        val now = System.currentTimeMillis()

        // 1. Check if we are currently collecting a crash
        val activeType = collectingType
        if (activeType != null) {
            val withinTimeWindow = (now - collectionStartTime) < 1800L
            val samePid = targetPid.isNotBlank() && line.pid == targetPid
            val isRelevantTag = when (activeType) {
                CrashType.JAVA -> line.tag == "AndroidRuntime" || line.tag == "System.err"
                CrashType.JNI -> line.tag.startsWith("DEBUG")
                CrashType.ANR -> line.tag == "ActivityManager"
            }

            if (withinTimeWindow && (samePid || isRelevantTag)) {
                collectedLines.add(line)
                return
            } else {
                // Time window finished: finalize current crash
                finalizeCurrentCrash()
            }
        }

        // 2. Inspect for start of a new Java Crash
        if (line.tag == "AndroidRuntime" && line.content.contains("FATAL EXCEPTION:")) {
            collectingType = CrashType.JAVA
            collectionStartTime = now
            targetPid = line.pid
            collectedLines.clear()
            collectedLines.add(line)
            return
        }

        // 3. Inspect for start of a new Native / JNI Crash
        if (line.tag.startsWith("DEBUG") && line.content.contains("*** *** ***")) {
            collectingType = CrashType.JNI
            collectionStartTime = now
            targetPid = line.pid
            collectedLines.clear()
            collectedLines.add(line)
            return
        }

        // 4. Inspect for start of an ANR
        if (line.tag == "ActivityManager" && line.content.startsWith("ANR in ")) {
            collectingType = CrashType.ANR
            collectionStartTime = now
            targetPid = line.pid
            collectedLines.clear()
            collectedLines.add(line)
            return
        }
    }

    @Synchronized
    fun flushPending() {
        if (collectingType != null) {
            finalizeCurrentCrash()
        }
    }

    private fun finalizeCurrentCrash() {
        val type = collectingType ?: return
        val lines = collectedLines.toList()
        collectingType = null
        collectedLines.clear()

        if (lines.isEmpty()) return

        scope.launch {
            val packageName = extractPackageName(type, lines)
            val appName = resolveAppName(packageName)
            val pid = lines.firstOrNull()?.pid ?: ""
            val summary = extractSummary(type, lines)
            val stackTrace = lines.joinToString("\n") { it.content }

            val record = AppCrashRecord(
                id = UUID.randomUUID().toString(),
                appName = appName,
                packageName = packageName,
                crashType = type,
                timestamp = lines.firstOrNull()?.timestamp ?: System.currentTimeMillis(),
                summary = summary,
                stackTrace = stackTrace,
                pid = pid,
            )

            addCrashRecord(record)
            showCrashNotification(record)
        }
    }

    private fun extractPackageName(type: CrashType, lines: List<LogLine>): String {
        when (type) {
            CrashType.JAVA -> {
                // Usually line 2: "Process: com.example.app, PID: 1234"
                for (line in lines) {
                    val content = line.content
                    if (content.startsWith("Process: ")) {
                        val comma = content.indexOf(",")
                        return if (comma != -1) {
                            content.substring(9, comma).trim()
                        } else {
                            content.substring(9).trim()
                        }
                    }
                }
            }
            CrashType.JNI -> {
                // Line format: ">>> com.example.app <<<"
                for (line in lines) {
                    val content = line.content
                    val start = content.indexOf(">>> ")
                    val end = content.indexOf(" <<<")
                    if (start != -1 && end != -1 && start < end) {
                        return content.substring(start + 4, end).trim()
                    }
                }
            }
            CrashType.ANR -> {
                // Format: "ANR in com.example.app (reason)"
                val firstContent = lines.firstOrNull()?.content ?: ""
                if (firstContent.startsWith("ANR in ")) {
                    val afterAnr = firstContent.substring(7)
                    val paren = afterAnr.indexOf(" (")
                    return if (paren != -1) afterAnr.substring(0, paren).trim() else afterAnr.trim()
                }
            }
        }

        // Fallback: check log line packageName or search for com. tokens
        for (line in lines) {
            line.packageName?.let { if (it.isNotBlank() && it != "system" && it != "root") return it }
        }

        return "Desconhecido"
    }

    private fun extractSummary(type: CrashType, lines: List<LogLine>): String {
        return when (type) {
            CrashType.JAVA -> {
                lines.find { it.content.contains("Exception") || it.content.contains("Error") }?.content
                    ?: lines.firstOrNull()?.content
                    ?: "Erro de execução Java"
            }
            CrashType.JNI -> {
                lines.find { it.content.contains("Fatal signal") || it.content.contains("SIG") }?.content
                    ?: "Falha de sinal nativo / Sigfault"
            }
            CrashType.ANR -> {
                lines.firstOrNull()?.content ?: "Aplicativo parou de responder (ANR)"
            }
        }
    }

    private fun resolveAppName(packageName: String): String {
        if (packageName == "Desconhecido") return "Sistema Android"
        return runCatching {
            val pm = context.packageManager
            val ai = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(ai).toString()
        }.getOrDefault(packageName)
    }

    private fun showCrashNotification(record: AppCrashRecord) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val notifId = (record.timestamp % 100000).toInt() + 20000

        // 1. Content Intent: Opens launcher to Crash Detail
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_CRASH_ID, record.id)
            putExtra(EXTRA_CRASH_PACKAGE, record.packageName)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notifId + 1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // 2. Action: Copy Stacktrace
        val copyIntent = Intent(context, LogNotificationReceiver::class.java).apply {
            action = LogNotificationReceiver.ACTION_COPY_CRASH_STACKTRACE
            putExtra(LogNotificationReceiver.EXTRA_CRASH_TEXT, record.stackTrace)
        }
        val copyPendingIntent = PendingIntent.getBroadcast(
            context,
            notifId + 2,
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // 3. Action: Share Crash Report
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "Relatório de Erro - ${record.appName} (${record.packageName})\n" +
                        "Tipo: ${record.crashType.title}\n" +
                        "Data: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(record.timestamp))}\n\n" +
                        "Stacktrace:\n${record.stackTrace}"
            )
        }
        val shareChooser = Intent.createChooser(shareIntent, context.getString(R.string.log_notif_action_share)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val sharePendingIntent = PendingIntent.getActivity(
            context,
            notifId + 3,
            shareChooser,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CRASHES_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("${record.crashType.title}: ${record.appName}")
            .setContentText(record.summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${record.summary}\n\n${record.stackTrace.take(400)}..."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_info_details, context.getString(R.string.log_action_view_crash), openPendingIntent)
            .addAction(android.R.drawable.ic_menu_set_as, context.getString(R.string.log_action_copy_trace), copyPendingIntent)
            .addAction(android.R.drawable.ic_menu_share, context.getString(R.string.log_notif_action_share), sharePendingIntent)
            .build()

        nm.notify(notifId, notification)
    }

    private fun addCrashRecord(record: AppCrashRecord) {
        val updated = listOf(record) + _crashes.value.take(MAX_CRASH_RECORDS - 1)
        _crashes.value = updated
        saveCrashesToFile(updated)
    }

    fun clearCrashes() {
        _crashes.value = emptyList()
        scope.launch {
            if (crashesFile.exists()) crashesFile.delete()
        }
    }

    fun deleteCrash(id: String) {
        val updated = _crashes.value.filter { it.id != id }
        _crashes.value = updated
        scope.launch {
            saveCrashesToFile(updated)
        }
    }

    private fun saveCrashesToFile(list: List<AppCrashRecord>) {
        runCatching {
            val jsonArray = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("appName", item.appName)
                    put("packageName", item.packageName)
                    put("crashType", item.crashType.name)
                    put("timestamp", item.timestamp)
                    put("summary", item.summary)
                    put("stackTrace", item.stackTrace)
                    put("pid", item.pid)
                }
                jsonArray.put(obj)
            }
            crashesFile.writeText(jsonArray.toString())
        }
    }

    private fun loadCrashes() {
        scope.launch {
            runCatching {
                if (!crashesFile.exists()) return@launch
                val jsonArray = JSONArray(crashesFile.readText())
                val list = mutableListOf<AppCrashRecord>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        AppCrashRecord(
                            id = obj.getString("id"),
                            appName = obj.getString("appName"),
                            packageName = obj.getString("packageName"),
                            crashType = CrashType.valueOf(obj.getString("crashType")),
                            timestamp = obj.getLong("timestamp"),
                            summary = obj.getString("summary"),
                            stackTrace = obj.getString("stackTrace"),
                            pid = obj.optString("pid", ""),
                        )
                    )
                }
                _crashes.value = list
            }
        }
    }
}
