// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.root.log

import android.content.Context
import androidx.collection.LruCache
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class LogLineParser(private val context: Context) {

    private val lineIdCounter = AtomicLong(1L)
    private val uidsCache = LruCache<String, String>(500)

    // Regex for: logcat -v uid -v epoch: "1689234857.123  u0_a123   1234   1234 D MyTag  : Message here"
    private val epochUidRegex = Regex("^(\\d{10}\\.\\d{3})\\s+(\\S+)\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEFS])\\s+([^:]+?):\\s*(.*)$")

    // Regex for: logcat -v threadtime -v uid: "09-28 14:30:15.123  u0_a123   1234   1234 D MyTag  : Message here"
    private val threadtimeUidRegex = Regex("^(\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(\\S+)\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEFS])\\s+([^:]+?):\\s*(.*)$")

    // Regex for standard threadtime: "09-28 14:30:15.123   1234   1234 D MyTag  : Message here"
    private val threadtimeRegex = Regex("^(\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEFS])\\s+([^:]+?):\\s*(.*)$")

    private val uidPattern = Regex("^u(\\d+).*a(\\d+)$")

    private val wellKnownUids = mapOf(
        "0" to "root",
        "root" to "root",
        "1000" to "system",
        "system" to "system",
        "1001" to "telephony (radio)",
        "radio" to "telephony (radio)",
        "1002" to "bluetooth",
        "bluetooth" to "bluetooth",
        "1013" to "media",
        "media" to "media",
        "1023" to "sdcard_rw",
        "2000" to "shell",
        "shell" to "shell",
        "log" to "logd",
    )

    fun parseLine(rawLine: String): LogLine {
        val trimmed = rawLine.trim()
        val id = lineIdCounter.getAndIncrement()

        // 1. Try epoch + uid format
        epochUidRegex.find(trimmed)?.let { match ->
            val groups = match.groupValues
            val timestamp = parseEpochTimestamp(groups[1])
            val uid = groups[2].trim()
            val pid = groups[3].trim()
            val tid = groups[4].trim()
            val level = LogLevel.fromLetter(groups[5])
            val tag = groups[6].trim()
            val content = groups[7]
            val packageName = resolvePackageName(uid)

            return LogLine(
                id = id,
                timestamp = timestamp,
                uid = uid,
                pid = pid,
                tid = tid,
                packageName = packageName,
                level = level,
                tag = tag,
                content = content,
                originalContent = rawLine,
            )
        }

        // 2. Try threadtime + uid format
        threadtimeUidRegex.find(trimmed)?.let { match ->
            val groups = match.groupValues
            val timestamp = parseThreadtimeDate(groups[1])
            val uid = groups[2].trim()
            val pid = groups[3].trim()
            val tid = groups[4].trim()
            val level = LogLevel.fromLetter(groups[5])
            val tag = groups[6].trim()
            val content = groups[7]
            val packageName = resolvePackageName(uid)

            return LogLine(
                id = id,
                timestamp = timestamp,
                uid = uid,
                pid = pid,
                tid = tid,
                packageName = packageName,
                level = level,
                tag = tag,
                content = content,
                originalContent = rawLine,
            )
        }

        // 3. Try standard threadtime
        threadtimeRegex.find(trimmed)?.let { match ->
            val groups = match.groupValues
            val timestamp = parseThreadtimeDate(groups[1])
            val pid = groups[2].trim()
            val tid = groups[3].trim()
            val level = LogLevel.fromLetter(groups[4])
            val tag = groups[5].trim()
            val content = groups[6]

            return LogLine(
                id = id,
                timestamp = timestamp,
                uid = "",
                pid = pid,
                tid = tid,
                packageName = null,
                level = level,
                tag = tag,
                content = content,
                originalContent = rawLine,
            )
        }

        // 4. Fallback line (continuation line, kernel header, etc.)
        val level = when {
            trimmed.contains(" E ") || trimmed.startsWith("E/") -> LogLevel.ERROR
            trimmed.contains(" W ") || trimmed.startsWith("W/") -> LogLevel.WARN
            trimmed.contains(" I ") || trimmed.startsWith("I/") -> LogLevel.INFO
            trimmed.contains(" D ") || trimmed.startsWith("D/") -> LogLevel.DEBUG
            trimmed.contains(" F ") || trimmed.startsWith("F/") -> LogLevel.FATAL
            else -> LogLevel.VERBOSE
        }

        return LogLine(
            id = id,
            timestamp = System.currentTimeMillis(),
            uid = "",
            pid = "",
            tid = "",
            packageName = null,
            level = level,
            tag = "System",
            content = trimmed,
            originalContent = rawLine,
        )
    }

    fun resolvePackageName(uid: String): String? {
        if (uid.isBlank()) return null
        uidsCache[uid]?.let { return it }

        wellKnownUids[uid]?.let {
            uidsCache.put(uid, it)
            return it
        }

        val integerUid = resolveIntegerUid(uid) ?: return null
        return runCatching {
            context.packageManager.getPackagesForUid(integerUid)?.firstOrNull()?.also { pkg ->
                uidsCache.put(uid, pkg)
            }
        }.getOrNull()
    }

    private fun resolveIntegerUid(uid: String): Int? {
        uid.toIntOrNull()?.let { return it }

        return uidPattern.find(uid)?.let { match ->
            val userId = match.groupValues[1].toIntOrNull() ?: 0
            val appId = match.groupValues[2].toIntOrNull() ?: return null
            100_000 * userId + 10_000 + appId
        }
    }

    private fun parseEpochTimestamp(epochStr: String): Long {
        val dot = epochStr.indexOf('.')
        if (dot == -1) return (epochStr.toLongOrNull() ?: 0L) * 1000L
        val seconds = epochStr.substring(0, dot).toLongOrNull() ?: 0L
        val millis = epochStr.substring(dot + 1).padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        return seconds * 1000L + millis
    }

    private fun parseThreadtimeDate(timeStr: String): Long {
        // "MM-DD HH:MM:SS.mmm"
        return runCatching {
            val parts = timeStr.trim().split(" ")
            if (parts.size < 2) return System.currentTimeMillis()
            val dateParts = parts[0].split("-")
            val month = dateParts[0].toInt() - 1
            val day = dateParts[1].toInt()

            val timeParts = parts[1].split(":")
            val hour = timeParts[0].toInt()
            val minute = timeParts[1].toInt()
            val secParts = timeParts[2].split(".")
            val second = secParts[0].toInt()
            val millis = secParts.getOrNull(1)?.toInt() ?: 0

            val cal = Calendar.getInstance()
            cal.set(Calendar.MONTH, month)
            cal.set(Calendar.DAY_OF_MONTH, day)
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, second)
            cal.set(Calendar.MILLISECOND, millis)
            cal.timeInMillis
        }.getOrDefault(System.currentTimeMillis())
    }
}
