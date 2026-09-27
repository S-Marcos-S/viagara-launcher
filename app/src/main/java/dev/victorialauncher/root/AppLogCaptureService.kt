// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.root

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import dev.victorialauncher.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Background Foreground Service that captures logcat outputs specifically for a single app
 * using root privilege, automatically saving the log upon normal termination or crash.
 */
class AppLogCaptureService : Service() {

    companion object {
        const val ACTION_START_CAPTURE = "dev.victorialauncher.action.START_LOG_CAPTURE"
        const val ACTION_SAVE_LOG = "dev.victorialauncher.action.SAVE_LOG"
        const val ACTION_CANCEL_CAPTURE = "dev.victorialauncher.action.CANCEL_LOG_CAPTURE"

        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"

        const val CHANNEL_ID = "victoria_app_log_capture_channel"
        const val NOTIFICATION_ID_RECORDING = 88410

        private val _capturingPackage = MutableStateFlow<String?>(null)
        val capturingPackage: StateFlow<String?> = _capturingPackage.asStateFlow()

        private val _capturingAppName = MutableStateFlow<String?>(null)
        val capturingAppName: StateFlow<String?> = _capturingAppName.asStateFlow()

        fun isCapturingApp(packageName: String): Boolean {
            return _capturingPackage.value == packageName
        }

        fun isAnyCapturing(): Boolean {
            return _capturingPackage.value != null
        }

        fun startCapture(context: Context, packageName: String, appName: String) {
            val intent = Intent(context, AppLogCaptureService::class.java).apply {
                action = ACTION_START_CAPTURE
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_APP_NAME, appName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun saveLog(context: Context) {
            val intent = Intent(context, AppLogCaptureService::class.java).apply {
                action = ACTION_SAVE_LOG
            }
            context.startService(intent)
        }

        fun cancelCapture(context: Context) {
            val intent = Intent(context, AppLogCaptureService::class.java).apply {
                action = ACTION_CANCEL_CAPTURE
            }
            context.startService(intent)
        }

        fun isLogLineMatchingApp(
            line: String,
            uidString: String?,
            pkgNameLower: String,
            seenPids: MutableSet<Int> = mutableSetOf(),
        ): Boolean {
            // 1. Direct UID match in threadtime uid format: "MM-DD HH:MM:SS.mmm UID PID TID ..."
            if (uidString != null && (line.contains(" $uidString ") || line.contains(" uid=$uidString "))) {
                extractPidFromLine(line)?.let { seenPids.add(it) }
                return true
            }

            // 2. Package name match
            val lineLower = line.lowercase(Locale.ROOT)
            if (lineLower.contains(pkgNameLower)) {
                extractPidFromLine(line)?.let { seenPids.add(it) }
                return true
            }

            // 3. Known PID match (from processes belonging to the app)
            val linePid = extractPidFromLine(line)
            if (linePid != null && seenPids.contains(linePid)) {
                return true
            }

            return false
        }

        fun extractPidFromLine(line: String): Int? {
            val tokens = line.trim().split(Regex("\\s+"))
            // In "MM-DD HH:MM:SS.mmm UID PID TID ..." PID is typically token index 3
            if (tokens.size >= 4) {
                val candidate = tokens[3].toIntOrNull()
                if (candidate != null && candidate > 0) return candidate
            }
            // In "MM-DD HH:MM:SS.mmm PID TID ..." PID is token index 2
            if (tokens.size >= 3) {
                val candidate = tokens[2].toIntOrNull()
                if (candidate != null && candidate > 0) return candidate
            }
            return null
        }

        fun isCrashLine(line: String, pkgNameLower: String): Boolean {
            val lower = line.lowercase(Locale.ROOT)
            val hasFatalTag = lower.contains("fatal exception") ||
                    lower.contains("fatal signal") ||
                    lower.contains("androidruntime: fatal") ||
                    lower.contains("sigsegv") ||
                    lower.contains("sigabrt")

            if (hasFatalTag) return true

            if (lower.contains("am_crash") && lower.contains(pkgNameLower)) return true
            if (lower.contains("force finishing activity") && lower.contains(pkgNameLower)) return true
            if (lower.contains("process $pkgNameLower has died") || lower.contains("has died: crash")) return true

            return false
        }
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var targetPackage: String = ""
    private var targetAppName: String = ""
    private var targetUid: Int = -1

    private var logcatProcess: Process? = null
    private var tempLogFile: File? = null
    private var fileWriter: BufferedWriter? = null
    private var captureJob: Job? = null

    private val crashDetected = AtomicBoolean(false)
    private val isFinalized = AtomicBoolean(false)

    private val seenPids = Collections.synchronizedSet(mutableSetOf<Int>())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START_CAPTURE -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return START_NOT_STICKY
                val name = intent.getStringExtra(EXTRA_APP_NAME) ?: pkg

                // Stop any previous recording cleanly if active
                if (_capturingPackage.value != null && _capturingPackage.value != pkg) {
                    stopCaptureProcess()
                }

                targetPackage = pkg
                targetAppName = name
                _capturingPackage.value = pkg
                _capturingAppName.value = name
                crashDetected.set(false)
                isFinalized.set(false)
                seenPids.clear()

                startRecordingSession()
            }
            ACTION_SAVE_LOG -> {
                finishAndSaveLog(isCrash = false)
            }
            ACTION_CANCEL_CAPTURE -> {
                discardCapture()
            }
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.log_notif_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.log_notif_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    private fun startRecordingSession() {
        targetUid = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageUid(targetPackage, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageUid(targetPackage, 0)
            }
        } catch (_: Throwable) {
            -1
        }

        // 1. Build and show Ongoing Foreground Notification
        val notification = buildRecordingNotification(targetAppName)
        startForeground(NOTIFICATION_ID_RECORDING, notification)

        Toast.makeText(
            applicationContext,
            getString(R.string.log_toast_started, targetAppName),
            Toast.LENGTH_SHORT,
        ).show()

        // 2. Prepare temporary file
        val tempFile = File(cacheDir, "logcat_${targetPackage}_${System.currentTimeMillis()}.tmp")
        tempLogFile = tempFile
        fileWriter = BufferedWriter(FileWriter(tempFile, true))

        // 3. Start streaming logcat with UID formatting
        captureJob = serviceScope.launch {
            try {
                // Clear previous buffers so the captured log is fresh
                runCatching {
                    ProcessBuilder("su", "-c", "logcat -c").start().waitFor()
                }

                val process = ProcessBuilder("su", "-c", "logcat -v threadtime -v uid").start()
                logcatProcess = process

                val reader = process.inputStream.bufferedReader()
                val uidString = if (targetUid > 0) targetUid.toString() else null
                val pkgNameLower = targetPackage.lowercase(Locale.ROOT)

                // Write capture header
                writeLogLine("=================================================================")
                writeLogLine("VIAGRA LAUNCHER - ROOT LOGCAT CAPTURE")
                writeLogLine("Application : $targetAppName ($targetPackage)")
                writeLogLine("UID         : ${targetUid.takeIf { it > 0 } ?: "Unknown"}")
                writeLogLine("Started At  : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                writeLogLine("=================================================================\n")

                while (isActive) {
                    val line = reader.readLine() ?: break
                    if (isFinalized.get()) break

                    val matchesApp = isLogLineMatchingApp(line, uidString, pkgNameLower, seenPids)
                    if (matchesApp) {
                        writeLogLine(line)

                        // Inspect for fatal app crash/errors
                        if (!crashDetected.get() && isCrashLine(line, pkgNameLower)) {
                            if (crashDetected.compareAndSet(false, true)) {
                                writeLogLine("\n[!][CRASH DETECTED - APPLICATION ENCOUNTERED A FATAL ERROR][!]\n")
                                // Wait briefly to capture trailing stack trace details
                                serviceScope.launch {
                                    delay(900)
                                    finishAndSaveLog(isCrash = true)
                                }
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
                // Process terminated or cancelled
            }
        }
    }

    private fun writeLogLine(line: String) {
        try {
            fileWriter?.write(line)
            fileWriter?.newLine()
            fileWriter?.flush()
        } catch (_: Throwable) {}
    }

    private fun buildRecordingNotification(appName: String): android.app.Notification {
        val saveIntent = Intent(this, LogNotificationReceiver::class.java).apply {
            action = LogNotificationReceiver.ACTION_SAVE_CURRENT_LOG
        }
        val savePendingIntent = PendingIntent.getBroadcast(
            this,
            101,
            saveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val discardIntent = Intent(this, LogNotificationReceiver::class.java).apply {
            action = LogNotificationReceiver.ACTION_DISCARD_CURRENT_LOG
        }
        val discardPendingIntent = PendingIntent.getBroadcast(
            this,
            102,
            discardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.log_notif_recording_title, appName))
            .setContentText(getString(R.string.log_notif_recording_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_save,
                getString(R.string.log_notif_action_save),
                savePendingIntent,
            )
            .addAction(
                android.R.drawable.ic_menu_delete,
                getString(R.string.log_notif_action_discard),
                discardPendingIntent,
            )
            .build()
    }

    private fun finishAndSaveLog(isCrash: Boolean) {
        if (!isFinalized.compareAndSet(false, true)) return

        serviceScope.launch {
            stopCaptureProcess()

            val temp = tempLogFile
            val pkg = targetPackage
            val name = targetAppName

            _capturingPackage.value = null
            _capturingAppName.value = null

            // Dismiss ongoing notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID_RECORDING)

            if (temp != null && temp.exists() && temp.length() > 0) {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val crashPrefix = if (isCrash) "CRASH_" else ""
                val cleanPkg = pkg.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val fileName = "logcat_${cleanPkg}_${crashPrefix}${timestamp}.txt"
                val destFile = File(downloadsDir, fileName)

                try {
                    temp.copyTo(destFile, overwrite = true)
                    temp.delete()

                    // Ensure file is immediately indexed by the system media scanner
                    MediaScannerConnection.scanFile(
                        applicationContext,
                        arrayOf(destFile.absolutePath),
                        arrayOf("text/plain"),
                        null,
                    )

                    withContext(Dispatchers.Main) {
                        showSavedLogNotification(destFile, name, fileName, isCrash)
                        val toastMsg = if (isCrash) {
                            getString(R.string.log_toast_crash_detected, name)
                        } else {
                            getString(R.string.log_toast_saved, fileName)
                        }
                        Toast.makeText(applicationContext, toastMsg, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "Erro ao salvar log: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }

            stopSelf()
        }
    }

    private fun showSavedLogNotification(destFile: File, appName: String, fileName: String, isCrash: Boolean) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val savedNotificationId = (System.currentTimeMillis() % 100000).toInt() + 1000

        val fileUri = try {
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", destFile)
        } catch (_: Throwable) {
            null
        }

        val title = if (isCrash) {
            getString(R.string.log_notif_crash_title, appName)
        } else {
            getString(R.string.log_notif_saved_title)
        }
        val text = getString(R.string.log_notif_saved_text, fileName)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (fileUri != null) {
            // Action 1: View File
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "text/plain")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val viewPendingIntent = PendingIntent.getActivity(
                this,
                savedNotificationId + 1,
                viewIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.setContentIntent(viewPendingIntent)
            builder.addAction(
                android.R.drawable.ic_menu_view,
                getString(R.string.log_notif_action_view),
                viewPendingIntent,
            )

            // Action 2: Share File
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooserIntent = Intent.createChooser(shareIntent, getString(R.string.log_notif_action_share)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val sharePendingIntent = PendingIntent.getActivity(
                this,
                savedNotificationId + 2,
                chooserIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(
                android.R.drawable.ic_menu_share,
                getString(R.string.log_notif_action_share),
                sharePendingIntent,
            )
        }

        // Action 3: Delete File
        val deleteIntent = Intent(this, LogNotificationReceiver::class.java).apply {
            action = LogNotificationReceiver.ACTION_DELETE_SAVED_LOG
            putExtra(LogNotificationReceiver.EXTRA_FILE_PATH, destFile.absolutePath)
            putExtra(LogNotificationReceiver.EXTRA_NOTIFICATION_ID, savedNotificationId)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            this,
            savedNotificationId + 3,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        builder.addAction(
            android.R.drawable.ic_menu_delete,
            getString(R.string.log_notif_action_delete),
            deletePendingIntent,
        )

        nm.notify(savedNotificationId, builder.build())
    }

    private fun discardCapture() {
        isFinalized.set(true)
        serviceScope.launch {
            stopCaptureProcess()

            val temp = tempLogFile
            try {
                if (temp != null && temp.exists()) {
                    temp.delete()
                }
            } catch (_: Throwable) {}

            _capturingPackage.value = null
            _capturingAppName.value = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID_RECORDING)

            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, getString(R.string.log_toast_discarded), Toast.LENGTH_SHORT).show()
            }

            stopSelf()
        }
    }

    private fun stopCaptureProcess() {
        try {
            captureJob?.cancel()
        } catch (_: Throwable) {}

        try {
            fileWriter?.flush()
            fileWriter?.close()
        } catch (_: Throwable) {}
        fileWriter = null

        try {
            logcatProcess?.destroy()
        } catch (_: Throwable) {}
        logcatProcess = null
    }

    override fun onDestroy() {
        stopCaptureProcess()
        serviceJob.cancel()
        _capturingPackage.value = null
        _capturingAppName.value = null
        super.onDestroy()
    }
}
