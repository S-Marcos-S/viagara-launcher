// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.root.log

import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel(val letter: String, val title: String) {
    VERBOSE("V", "Verbose"),
    DEBUG("D", "Debug"),
    INFO("I", "Info"),
    WARN("W", "Warning"),
    ERROR("E", "Error"),
    FATAL("F", "Fatal"),
    SILENT("S", "Silent");

    companion object {
        fun fromLetter(letter: String): LogLevel {
            return entries.firstOrNull { it.letter.equals(letter.trim(), ignoreCase = true) } ?: DEBUG
        }
    }
}

data class LogLine(
    val id: Long,
    val timestamp: Long,
    val uid: String,
    val pid: String,
    val tid: String,
    val packageName: String?,
    val level: LogLevel,
    val tag: String,
    val content: String,
    val originalContent: String,
) {
    fun format(
        showDate: Boolean = true,
        showTime: Boolean = true,
        showUid: Boolean = false,
        showPid: Boolean = true,
        showTid: Boolean = false,
        showPackage: Boolean = true,
        showTag: Boolean = true,
        showContent: Boolean = true,
    ): String = buildString {
        val dateFormat = SimpleDateFormat("MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val dateObj = Date(timestamp)

        if (showDate) append(dateFormat.format(dateObj)).append(" ")
        if (showTime) append(timeFormat.format(dateObj)).append(" ")
        if (showUid && uid.isNotBlank()) append(uid).append(" ")
        if (showPid && pid.isNotBlank()) append(pid).append(" ")
        if (showTid && tid.isNotBlank()) append(tid).append(" ")
        if (showPackage && !packageName.isNullOrBlank()) append(packageName).append(" ")
        append(level.letter).append("/")
        if (showTag) append(tag).append(": ")
        if (showContent) append(content)
    }
}

enum class CrashType(val title: String) {
    JAVA("Java Crash"),
    JNI("Native / JNI Crash"),
    ANR("ANR (Sem Resposta)"),
}

data class AppCrashRecord(
    val id: String,
    val appName: String,
    val packageName: String,
    val crashType: CrashType,
    val timestamp: Long,
    val summary: String,
    val stackTrace: String,
    val pid: String,
)

data class UserLogFilter(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val including: Boolean = true,
    val allowedLevels: Set<LogLevel> = emptySet(),
    val packageName: String? = null,
    val tag: String? = null,
    val pid: String? = null,
    val tid: String? = null,
    val content: String? = null,
    val enabled: Boolean = true,
)

data class SavedLogRecording(
    val file: File,
    val name: String,
    val targetPackage: String?,
    val targetAppName: String?,
    val timestamp: Long,
    val sizeBytes: Long,
    val isZip: Boolean,
)

object DeviceInfoProvider {
    fun getDeviceInfoText(): String = buildString {
        appendLine("==================================================")
        appendLine("VIAGARA LAUNCHER - INFORMAÇÕES DO DISPOSITIVO")
        appendLine("Gerado em: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        appendLine("==================================================")
        appendLine("SDK_INT             : ${runCatching { Build.VERSION.SDK_INT }.getOrDefault(0)}")
        appendLine("ANDROID_RELEASE     : ${runCatching { Build.VERSION.RELEASE }.getOrNull() ?: "N/A"}")
        appendLine("SECURITY_PATCH      : ${runCatching { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "N/A" }.getOrNull() ?: "N/A"}")
        appendLine("MANUFACTURER        : ${runCatching { Build.MANUFACTURER }.getOrNull() ?: "N/A"}")
        appendLine("BRAND               : ${runCatching { Build.BRAND }.getOrNull() ?: "N/A"}")
        appendLine("MODEL               : ${runCatching { Build.MODEL }.getOrNull() ?: "N/A"}")
        appendLine("PRODUCT             : ${runCatching { Build.PRODUCT }.getOrNull() ?: "N/A"}")
        appendLine("DEVICE              : ${runCatching { Build.DEVICE }.getOrNull() ?: "N/A"}")
        appendLine("BOARD               : ${runCatching { Build.BOARD }.getOrNull() ?: "N/A"}")
        appendLine("HARDWARE            : ${runCatching { Build.HARDWARE }.getOrNull() ?: "N/A"}")
        appendLine("SUPPORTED_ABIS      : ${runCatching { Build.SUPPORTED_ABIS?.joinToString(", ") }.getOrNull() ?: "N/A"}")
        appendLine("FINGERPRINT         : ${runCatching { Build.FINGERPRINT }.getOrNull() ?: "N/A"}")
        appendLine("DISPLAY             : ${runCatching { Build.DISPLAY }.getOrNull() ?: "N/A"}")
        appendLine("BOOTLOADER          : ${runCatching { Build.BOOTLOADER }.getOrNull() ?: "N/A"}")
        appendLine("ID                  : ${runCatching { Build.ID }.getOrNull() ?: "N/A"}")
        appendLine("==================================================")
    }
}
