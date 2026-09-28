// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.root.log

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class RecordingState {
    IDLE,
    RECORDING,
    PAUSED,
}

data class ActiveSessionInfo(
    val state: RecordingState = RecordingState.IDLE,
    val targetPackage: String? = null,
    val targetAppName: String? = null,
    val startTime: Long = 0L,
    val linesRecorded: Long = 0L,
    val bytesWritten: Long = 0L,
)

class LogRecordingsManager(private val context: Context) {

    private val _session = MutableStateFlow(ActiveSessionInfo())
    val session: StateFlow<ActiveSessionInfo> = _session.asStateFlow()

    private val _savedRecordings = MutableStateFlow<List<SavedLogRecording>>(emptyList())
    val savedRecordings: StateFlow<List<SavedLogRecording>> = _savedRecordings.asStateFlow()

    private var currentTempFile: File? = null
    private var currentWriter: BufferedWriter? = null

    val recordingsDir: File
        get() {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dir = File(downloads, "VictoriaLogs")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    init {
        refreshSavedRecordings()
    }

    @Synchronized
    fun startSession(targetPackage: String?, targetAppName: String?) {
        stopCurrentSessionInternal(discard = true)

        val tempFile = File(context.cacheDir, "recording_temp_${System.currentTimeMillis()}.tmp")
        currentTempFile = tempFile
        currentWriter = BufferedWriter(FileWriter(tempFile, true))

        _session.value = ActiveSessionInfo(
            state = RecordingState.RECORDING,
            targetPackage = targetPackage,
            targetAppName = targetAppName,
            startTime = System.currentTimeMillis(),
            linesRecorded = 0L,
            bytesWritten = 0L,
        )

        // Write session header
        val header = buildString {
            appendLine("=================================================================")
            appendLine("VIAGRA LAUNCHER - SESSÃO DE GRAVAÇÃO DE LOGS (LOGFOX ENGINE)")
            appendLine("Alvo       : ${targetAppName ?: "Todos os Apps do Sistema"} (${targetPackage ?: "Global"})")
            appendLine("Iniciado em: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine("=================================================================\n")
        }
        writeLine(header)
    }

    @Synchronized
    fun pauseSession() {
        if (_session.value.state == RecordingState.RECORDING) {
            _session.value = _session.value.copy(state = RecordingState.PAUSED)
        }
    }

    @Synchronized
    fun resumeSession() {
        if (_session.value.state == RecordingState.PAUSED) {
            _session.value = _session.value.copy(state = RecordingState.RECORDING)
        }
    }

    @Synchronized
    fun processLine(line: LogLine) {
        val current = _session.value
        if (current.state != RecordingState.RECORDING) return

        val pkg = current.targetPackage
        if (pkg != null) {
            val matches = line.packageName == pkg ||
                    line.content.contains(pkg, ignoreCase = true) ||
                    line.tag.contains(pkg, ignoreCase = true)
            if (!matches) return
        }

        val formatted = line.format()
        writeLine(formatted)
        _session.value = current.copy(
            linesRecorded = current.linesRecorded + 1,
            bytesWritten = current.bytesWritten + formatted.length + 1,
        )
    }

    private fun writeLine(text: String) {
        runCatching {
            currentWriter?.write(text)
            currentWriter?.newLine()
            currentWriter?.flush()
        }
    }

    suspend fun stopAndSave(asZipWithDeviceInfo: Boolean = true): File? = withContext(Dispatchers.IO) {
        val sessionInfo = _session.value
        if (sessionInfo.state == RecordingState.IDLE) return@withContext null

        flushAndCloseWriter()
        val temp = currentTempFile
        if (temp == null || !temp.exists() || temp.length() == 0L) {
            stopCurrentSessionInternal(discard = true)
            return@withContext null
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val cleanName = (sessionInfo.targetPackage ?: "global").replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val dir = recordingsDir

        val savedFile = if (asZipWithDeviceInfo) {
            val zipFile = File(dir, "log_${cleanName}_${timestamp}.zip")
            createZipWithDeviceInfo(zipFile, temp, timestamp)
            zipFile
        } else {
            val txtFile = File(dir, "log_${cleanName}_${timestamp}.txt")
            temp.copyTo(txtFile, overwrite = true)
            txtFile
        }

        temp.delete()
        currentTempFile = null

        _session.value = ActiveSessionInfo(state = RecordingState.IDLE)

        // Index with media scanner
        MediaScannerConnection.scanFile(
            context,
            arrayOf(savedFile.absolutePath),
            arrayOf(if (asZipWithDeviceInfo) "application/zip" else "text/plain"),
            null,
        )

        refreshSavedRecordings()
        savedFile
    }

    @Synchronized
    fun discardSession() {
        stopCurrentSessionInternal(discard = true)
    }

    private fun stopCurrentSessionInternal(discard: Boolean) {
        flushAndCloseWriter()
        if (discard) {
            currentTempFile?.delete()
        }
        currentTempFile = null
        _session.value = ActiveSessionInfo(state = RecordingState.IDLE)
    }

    private fun flushAndCloseWriter() {
        runCatching {
            currentWriter?.flush()
            currentWriter?.close()
        }
        currentWriter = null
    }

    private fun createZipWithDeviceInfo(zipFile: File, logFile: File, timestamp: String) {
        val deviceInfo = DeviceInfoProvider.getDeviceInfoText()
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // 1. Put Device Info
            val deviceEntry = ZipEntry("device_${timestamp}.txt")
            zos.putNextEntry(deviceEntry)
            zos.write(deviceInfo.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 2. Put Recorded Log
            val logEntry = ZipEntry("recorded_${timestamp}.txt")
            zos.putNextEntry(logEntry)
            FileInputStream(logFile).use { fis ->
                fis.copyTo(zos)
            }
            zos.closeEntry()
        }
    }

    fun refreshSavedRecordings() {
        val dir = recordingsDir
        val files = dir.listFiles { f -> f.isFile && (f.name.endsWith(".txt") || f.name.endsWith(".zip")) }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

        val list = files.map { f ->
            val isZip = f.name.endsWith(".zip", ignoreCase = true)
            SavedLogRecording(
                file = f,
                name = f.name,
                targetPackage = null,
                targetAppName = null,
                timestamp = f.lastModified(),
                sizeBytes = f.length(),
                isZip = isZip,
            )
        }
        _savedRecordings.value = list
    }

    fun deleteRecording(file: File) {
        runCatching {
            if (file.exists()) file.delete()
        }
        refreshSavedRecordings()
    }
}
