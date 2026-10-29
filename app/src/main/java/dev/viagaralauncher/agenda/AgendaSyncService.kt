// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.agenda

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

class AgendaSyncService(private val context: Context) {

    private val repository = AgendaRepository(context)

    companion object {
        private const val TAG = "AgendaSyncService"
        private const val SYNC_FILE_NAME = "TBCalendar_Sync_Data.json"
        private const val BIG_CALENDAR_PKG = "com.mss.thebigcalendar"

        fun isBigCalendarInstalled(context: Context): Boolean {
            return try {
                context.packageManager.getPackageInfo(BIG_CALENDAR_PKG, 0)
                true
            } catch (_: Exception) {
                false
            }
        }

        fun getSyncFile(): File {
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val appDir = File(docsDir, "TheBigCalendar")
            if (!appDir.exists()) {
                appDir.mkdirs()
            }
            return File(appDir, SYNC_FILE_NAME)
        }
    }

    suspend fun syncWithLocalFile(): Boolean = withContext(Dispatchers.IO) {
        if (!isBigCalendarInstalled(context)) {
            return@withContext false
        }

        try {
            val file = getSyncFile()
            if (file.exists() && file.length() > 0) {
                val content = file.readText(Charsets.UTF_8)
                val cleanContent = content.trim { it <= ' ' || it == '\u0000' }
                if (cleanContent.isNotEmpty() && (cleanContent.startsWith("{") || cleanContent.startsWith("["))) {
                    try {
                        repository.importAgendaJson(cleanContent)
                    } catch (e: Exception) {
                        Log.w(TAG, "Arquivo de sincronização corrompido, ignorando importação: ${e.message}")
                    }
                } else {
                    Log.w(TAG, "Arquivo de sincronização corrompido ou vazio detectado (${file.length()} bytes)")
                }
            }

            // After merging/importing, export the updated state to the sync file
            writeSyncFile()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro na sincronização com arquivo local: ${e.message}", e)
            false
        }
    }

    suspend fun writeSyncFile(): Boolean = withContext(Dispatchers.IO) {
        if (!isBigCalendarInstalled(context)) {
            return@withContext false
        }

        try {
            val file = getSyncFile()
            val parentDir = file.parentFile
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs()
            }

            val active = repository.activities.first()
            val completed = repository.completedActivities.first()
            val deleted = repository.deletedActivityIds.first()

            val root = JSONObject().apply {
                put("backupVersion", "1.1")
                put("appVersion", "VictoriaLauncher")
                put("createdAt", LocalDate.now().toString())
                put("activities", JSONArray().apply { active.forEach { put(it.toJson()) } })
                put("completedActivities", JSONArray().apply { completed.forEach { put(it.toJson()) } })
                put("deletedActivities", JSONArray().apply { deleted.forEach { put(it) } })
            }

            val jsonString = root.toString()
            val tempFile = File(file.parentFile, "${file.name}.tmp")
            FileOutputStream(tempFile).use { fos ->
                fos.write(jsonString.toByteArray(Charsets.UTF_8))
                fos.flush()
                try {
                    fos.fd.sync()
                } catch (_: Exception) {}
            }
            if (tempFile.exists() && tempFile.length() > 0) {
                if (file.exists()) {
                    file.delete()
                }
                tempFile.renameTo(file)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao gravar arquivo de sincronização: ${e.message}", e)
            false
        }
    }
}
