// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.root

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import dev.victorialauncher.MainActivity
import dev.victorialauncher.R
import dev.victorialauncher.root.log.CrashManager
import dev.victorialauncher.root.log.FilterStorage
import dev.victorialauncher.root.log.LogLine
import dev.victorialauncher.root.log.LogLineParser
import dev.victorialauncher.root.log.LogLevel
import dev.victorialauncher.root.log.LogRecordingsManager
import dev.victorialauncher.root.log.RecordingState
import dev.victorialauncher.root.log.TerminalEngine
import dev.victorialauncher.root.log.TerminalType
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-performance, full-featured background logging engine for Victoria Launcher.
 * Features:
 * - Multi-terminal support (Root and direct ADB execution)
 * - Real-time epoch & UID logcat streaming with high-throughput RingBuffer
 * - Real-time Java Crash, Native JNI Sigfault, and ANR Detection (LogFox Engine)
 * - Session recording with instant TXT or ZIP export containing complete device telemetry
 * - Foreground notification controls (Save, Pause, Resume, Discard)
 */
class AppLogCaptureService : Service() {

    companion object {
        const val ACTION_START_CAPTURE = "dev.victorialauncher.action.START_LOG_CAPTURE"
        const val ACTION_SAVE_LOG = "dev.victorialauncher.action.SAVE_LOG"
        const val ACTION_PAUSE_RESUME = "dev.victorialauncher.action.PAUSE_RESUME_CAPTURE"
        const val ACTION_CANCEL_CAPTURE = "dev.victorialauncher.action.CANCEL_LOG_CAPTURE"
        const val ACTION_START_MONITORING = "dev.victorialauncher.action.START_LOG_MONITORING"

        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"

        const val CHANNEL_ID = "victoria_app_log_capture_channel"
        const val NOTIFICATION_ID_RECORDING = 88410
        const val MAX_RING_BUFFER_SIZE = 6000

        private val _capturingPackage = MutableStateFlow<String?>(null)
        val capturingPackage: StateFlow<String?> = _capturingPackage.asStateFlow()

        private val _capturingAppName = MutableStateFlow<String?>(null)
        val capturingAppName: StateFlow<String?> = _capturingAppName.asStateFlow()

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _liveLogs = MutableStateFlow<List<LogLine>>(emptyList())
        val liveLogs: StateFlow<List<LogLine>> = _liveLogs.asStateFlow()

        val isStreamingPaused = MutableStateFlow(false)
        val preferredTerminal = MutableStateFlow(TerminalType.AUTO)

        @Volatile
        var crashManagerInstance: CrashManager? = null
            private set

        @Volatile
        var recordingsManagerInstance: LogRecordingsManager? = null
            private set

        @Volatile
        var filterStorageInstance: FilterStorage? = null
            private set

        @Volatile
        var parserInstance: LogLineParser? = null
            private set

        fun getCrashManager(context: Context): CrashManager {
            return crashManagerInstance ?: synchronized(this) {
                crashManagerInstance ?: CrashManager(context.applicationContext).also {
                    crashManagerInstance = it
                }
            }
        }

        fun getRecordingsManager(context: Context): LogRecordingsManager {
            return recordingsManagerInstance ?: synchronized(this) {
                recordingsManagerInstance ?: LogRecordingsManager(context.applicationContext).also {
                    recordingsManagerInstance = it
                }
            }
        }

        fun getFilterStorage(context: Context): FilterStorage {
            return filterStorageInstance ?: synchronized(this) {
                filterStorageInstance ?: FilterStorage(context.applicationContext).also {
                    filterStorageInstance = it
                }
            }
        }

        fun getParser(context: Context): LogLineParser {
            return parserInstance ?: synchronized(this) {
                parserInstance ?: LogLineParser(context.applicationContext).also {
                    parserInstance = it
                }
            }
        }

        fun clearLiveLogs() {
            _liveLogs.value = emptyList()
        }

        fun isCapturingApp(packageName: String): Boolean {
            return _capturingPackage.value == packageName
        }

        fun isAnyCapturing(): Boolean {
            return _capturingPackage.value != null
        }

        fun startCapture(context: Context, packageName: String?, appName: String?) {
            val intent = Intent(context, AppLogCaptureService::class.java).apply {
                action = ACTION_START_CAPTURE
                if (packageName != null) putExtra(EXTRA_PACKAGE_NAME, packageName)
                if (appName != null) putExtra(EXTRA_APP_NAME, appName)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Throwable) {
                android.util.Log.e("AppLogCaptureService", "startCapture failed", e)
            }
        }

        fun startMonitoring(context: Context) {
            val intent = Intent(context, AppLogCaptureService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            try {
                context.startService(intent)
            } catch (e: Throwable) {
                android.util.Log.e("AppLogCaptureService", "startMonitoring failed", e)
            }
        }

        fun stopMonitoring(context: Context) {
            if (_capturingPackage.value == null && recordingsManagerInstance?.session?.value?.state == RecordingState.IDLE) {
                val intent = Intent(context, AppLogCaptureService::class.java)
                try {
                    context.stopService(intent)
                } catch (_: Throwable) {}
            }
        }

        fun saveLog(context: Context) {
            val intent = Intent(context, AppLogCaptureService::class.java).apply {
                action = ACTION_SAVE_LOG
            }
            context.startService(intent)
        }

        fun togglePauseResume(context: Context) {
            val intent = Intent(context, AppLogCaptureService::class.java).apply {
                action = ACTION_PAUSE_RESUME
            }
            context.startService(intent)
        }

        fun cancelCapture(context: Context) {
            val intent = Intent(context, AppLogCaptureService::class.java).apply {
                action = ACTION_CANCEL_CAPTURE
            }
            context.startService(intent)
        }

        // --- Backwards Compatibility Helpers for Unit Tests & Root Inspector ---

        fun isLogLineMatchingApp(
            line: String,
            uidString: String?,
            pkgNameLower: String,
            seenPids: MutableSet<Int> = mutableSetOf(),
        ): Boolean {
            if (uidString != null && (line.contains(" $uidString ") || line.contains(" uid=$uidString "))) {
                extractPidFromLine(line)?.let { seenPids.add(it) }
                return true
            }

            val lineLower = line.lowercase(Locale.ROOT)
            if (lineLower.contains(pkgNameLower)) {
                extractPidFromLine(line)?.let { seenPids.add(it) }
                return true
            }

            val linePid = extractPidFromLine(line)
            if (linePid != null && seenPids.contains(linePid)) {
                return true
            }

            return false
        }

        fun extractPidFromLine(line: String): Int? {
            val tokens = line.trim().split(Regex("\\s+"))
            if (tokens.size < 3) return null

            if (tokens.size >= 5 &&
                tokens[2].toIntOrNull() != null &&
                tokens[3].toIntOrNull() != null &&
                tokens[4].toIntOrNull() != null
            ) {
                val candidate = tokens[3].toIntOrNull()
                if (candidate != null && candidate > 0) return candidate
            }

            if (tokens.size >= 4 &&
                tokens[2].toIntOrNull() != null &&
                tokens[3].toIntOrNull() != null
            ) {
                val candidate = tokens[2].toIntOrNull()
                if (candidate != null && candidate > 0) return candidate
            }

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

    private var targetPackage: String? = null
    private var targetAppName: String? = null
    private var targetUid: Int = -1

    private var logcatProcess: Process? = null
    private var captureJob: Job? = null
    private var notificationUpdaterJob: Job? = null

    private val crashDetected = AtomicBoolean(false)
    private val isFinalized = AtomicBoolean(false)
    private val seenPids = Collections.synchronizedSet(mutableSetOf<Int>())

    private lateinit var crashManager: CrashManager
    private lateinit var recordingsManager: LogRecordingsManager
    private lateinit var parser: LogLineParser

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        crashManager = getCrashManager(this)
        recordingsManager = getRecordingsManager(this)
        parser = getParser(this)
        getFilterStorage(this)

        createNotificationChannel()
        _isServiceRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START_CAPTURE -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                val name = intent.getStringExtra(EXTRA_APP_NAME) ?: pkg ?: getString(R.string.log_global_system_title)

                targetPackage = pkg
                targetAppName = name
                _capturingPackage.value = pkg
                _capturingAppName.value = name
                crashDetected.set(false)
                isFinalized.set(false)
                seenPids.clear()

                recordingsManager.startSession(pkg, name)
                startOngoingNotificationLoop()
                startStreamingProcess()
            }
            ACTION_START_MONITORING -> {
                if (logcatProcess == null) {
                    startStreamingProcess()
                }
            }
            ACTION_SAVE_LOG -> {
                finishAndSaveLog(isCrash = false)
            }
            ACTION_PAUSE_RESUME -> {
                val current = recordingsManager.session.value.state
                if (current == RecordingState.RECORDING) {
                    recordingsManager.pauseSession()
                } else if (current == RecordingState.PAUSED) {
                    recordingsManager.resumeSession()
                }
                updateOngoingNotification()
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

    private fun startStreamingProcess() {
        if (captureJob?.isActive == true) return

        if (targetPackage != null) {
            targetUid = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageUid(targetPackage!!, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageUid(targetPackage!!, 0)
                }
            } catch (_: Throwable) {
                -1
            }
        }

        captureJob = serviceScope.launch {
            try {
                // Clear old buffer for fresh start
                TerminalEngine.clearLogcatBuffers(applicationContext, preferredTerminal.value)

                val process = TerminalEngine.startLogcatStream(applicationContext, preferredTerminal.value)
                logcatProcess = process

                val reader = process.inputStream.bufferedReader()
                val pkgNameLower = targetPackage?.lowercase(Locale.ROOT)
                val ringBuffer = ArrayList<LogLine>(MAX_RING_BUFFER_SIZE)

                while (isActive) {
                    val rawLine = reader.readLine() ?: break
                    if (isFinalized.get()) break

                    val parsed = parser.parseLine(rawLine)

                    // 1. Process with CrashManager
                    crashManager.processLine(parsed)

                    // 2. Process with RecordingsManager
                    recordingsManager.processLine(parsed)

                    // 3. Add to live UI buffer
                    if (!isStreamingPaused.value) {
                        synchronized(ringBuffer) {
                            if (ringBuffer.size >= MAX_RING_BUFFER_SIZE) {
                                ringBuffer.removeAt(0)
                            }
                            ringBuffer.add(parsed)
                        }
                    }

                    // Throttle state update every ~40 lines or on crash
                    if (parsed.id % 25 == 0L || parsed.level == LogLevel.FATAL || parsed.level == LogLevel.ERROR) {
                        val snapshot = synchronized(ringBuffer) { ringBuffer.toList() }
                        _liveLogs.value = snapshot
                    }

                    // 4. Check for app crash if capturing a single app
                    if (pkgNameLower != null && !crashDetected.get()) {
                        if (isCrashLine(rawLine, pkgNameLower)) {
                            if (crashDetected.compareAndSet(false, true)) {
                                serviceScope.launch {
                                    delay(1000L)
                                    finishAndSaveLog(isCrash = true)
                                }
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
                // Stream closed
            }
        }
    }

    private fun startOngoingNotificationLoop() {
        notificationUpdaterJob?.cancel()
        val notif = buildRecordingNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID_RECORDING,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID_RECORDING, notif)
        }

        notificationUpdaterJob = serviceScope.launch {
            while (isActive && recordingsManager.session.value.state != RecordingState.IDLE) {
                delay(2000L)
                updateOngoingNotification()
            }
        }
    }

    private fun updateOngoingNotification() {
        if (recordingsManager.session.value.state == RecordingState.IDLE) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID_RECORDING, buildRecordingNotification())
    }

    private fun buildRecordingNotification(): android.app.Notification {
        val appName = targetAppName ?: getString(R.string.log_global_system_title)
        val session = recordingsManager.session.value
        val isPaused = session.state == RecordingState.PAUSED

        val saveIntent = Intent(this, LogNotificationReceiver::class.java).apply {
            action = LogNotificationReceiver.ACTION_SAVE_CURRENT_LOG
        }
        val savePending = PendingIntent.getBroadcast(
            this,
            101,
            saveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val pauseResumeIntent = Intent(this, LogNotificationReceiver::class.java).apply {
            action = LogNotificationReceiver.ACTION_PAUSE_RESUME_CAPTURE
        }
        val pauseResumePending = PendingIntent.getBroadcast(
            this,
            102,
            pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val discardIntent = Intent(this, LogNotificationReceiver::class.java).apply {
            action = LogNotificationReceiver.ACTION_DISCARD_CURRENT_LOG
        }
        val discardPending = PendingIntent.getBroadcast(
            this,
            103,
            discardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Open App on notification click
        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            104,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val statusText = if (isPaused) {
            "⏸ Pausado • ${session.linesRecorded} linhas capturadas"
        } else {
            "🔴 Gravando • ${session.linesRecorded} linhas capturadas"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.log_notif_recording_title, appName))
            .setContentText(statusText)
            .setOngoing(true)
            .setContentIntent(openPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_save,
                getString(R.string.log_notif_action_save),
                savePending,
            )
            .addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPaused) "Retomar" else "Pausar",
                pauseResumePending,
            )
            .addAction(
                android.R.drawable.ic_menu_delete,
                getString(R.string.log_notif_action_discard),
                discardPending,
            )
            .build()
    }

    private fun finishAndSaveLog(isCrash: Boolean) {
        if (!isFinalized.compareAndSet(false, true)) return

        serviceScope.launch {
            val name = targetAppName ?: "Sistema"
            val savedFile = recordingsManager.stopAndSave(asZipWithDeviceInfo = true)

            notificationUpdaterJob?.cancel()
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

            if (savedFile != null) {
                withContext(Dispatchers.Main) {
                    showSavedLogNotification(savedFile, name, savedFile.name, isCrash)
                    val toastMsg = if (isCrash) {
                        getString(R.string.log_toast_crash_detected, name)
                    } else {
                        getString(R.string.log_toast_saved, savedFile.name)
                    }
                    Toast.makeText(applicationContext, toastMsg, Toast.LENGTH_LONG).show()
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
            val mimeType = if (fileName.endsWith(".zip")) "application/zip" else "text/plain"

            // Action 1: View File
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, mimeType)
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
                type = mimeType
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
            recordingsManager.discardSession()
            notificationUpdaterJob?.cancel()

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
            notificationUpdaterJob?.cancel()
        } catch (_: Throwable) {}

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
        _isServiceRunning.value = false
        super.onDestroy()
    }
}
