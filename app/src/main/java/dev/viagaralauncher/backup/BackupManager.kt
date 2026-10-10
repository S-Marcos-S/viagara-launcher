// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.backup

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import dev.viagaralauncher.BuildConfig
import dev.viagaralauncher.agenda.AgendaRepository
import dev.viagaralauncher.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {
    const val BACKUP_EXTENSION = ".viagarabackup"
    const val LEGACY_BACKUP_EXTENSION = ".victoriabackup"
    private const val META_ENTRY = "meta.json"
    private const val PREFS_ENTRY = "preferences.json"
    private const val WALLPAPER_ENTRY = "wallpaper.png"
    private const val AGENDA_ENTRY = "agenda.json"

    fun getBackupsDir(context: Context): File {
        val dir = File(context.filesDir, "backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Persists read and write permissions for a selected tree directory URI.
     */
    fun takePersistablePermission(context: Context, treeUri: Uri): Boolean {
        return runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
            true
        }.getOrDefault(false)
    }

    /**
     * Checks whether we still hold valid persisted permissions for the directory URI.
     */
    fun hasFolderPermission(context: Context, treeUri: Uri): Boolean {
        return runCatching {
            context.contentResolver.persistedUriPermissions.any {
                it.uri == treeUri && it.isWritePermission && it.isReadPermission
            }
        }.getOrDefault(false)
    }

    /**
     * Retrieves a human-readable display name for the selected folder.
     */
    fun getFolderDisplayName(context: Context, treeUri: Uri): String {
        return runCatching {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (idx != -1) cursor.getString(idx) else null
                } else null
            }
        }.getOrNull() ?: treeUri.lastPathSegment?.substringAfterLast(':') ?: "Pasta Selecionada"
    }

    /**
     * Extracts current wallpaper bitmap if accessible.
     */
    fun extractWallpaperBitmap(context: Context): Bitmap? {
        val wallpaperManager = WallpaperManager.getInstance(context)
        return runCatching {
            val drawable = wallpaperManager.drawable ?: wallpaperManager.peekDrawable() ?: return null
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap
            } else {
                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1080
                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1920
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
        }.getOrNull()
    }

    /**
     * Creates a full backup containing all preferences, agenda items, and optionally the wallpaper into the user-selected folder.
     */
    suspend fun createBackup(
        context: Context,
        prefs: Prefs,
        folderUriStr: String?,
        type: BackupType = BackupType.MANUAL,
        includeWallpaper: Boolean = true,
        maxKeep: Int = 5,
    ): Result<BackupItem> = withContext(Dispatchers.IO) {
        runCatching {
            if (folderUriStr.isNullOrBlank()) {
                throw IllegalStateException("Destination folder not selected")
            }
            val treeUri = Uri.parse(folderUriStr)
            val now = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val prettyFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val dateStr = dateFormat.format(Date(now))
            val prettyDateStr = prettyFormat.format(Date(now))

            val prefix = if (type == BackupType.AUTOMATIC) "viagara_autobackup_" else "viagara_backup_"
            val fileName = "$prefix$dateStr$BACKUP_EXTENSION"

            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            val docUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                "application/octet-stream",
                fileName,
            ) ?: throw IllegalStateException("Could not create backup file in selected folder")

            // 1. Export DataStore preferences to JSON
            val preferencesJson = prefs.exportAllPreferencesJson()

            // 2. Export Agenda activities to JSON
            val agendaRepo = AgendaRepository(context)
            val agendaJson = agendaRepo.exportAgendaJson()

            // 3. Extract wallpaper if requested
            val wallpaperBitmap = if (includeWallpaper) extractWallpaperBitmap(context) else null
            val hasWallpaper = wallpaperBitmap != null

            // 4. Build metadata
            val meta = BackupMeta(
                versionCode = BuildConfig.VERSION_CODE,
                versionName = BuildConfig.VERSION_NAME,
                timestamp = now,
                formattedDate = prettyDateStr,
                hasWallpaper = hasWallpaper,
                type = type,
                preferenceCount = 0,
            )
            val metaJson = JSONObject().apply {
                put("versionCode", meta.versionCode)
                put("versionName", meta.versionName)
                put("timestamp", meta.timestamp)
                put("formattedDate", meta.formattedDate)
                put("hasWallpaper", meta.hasWallpaper)
                put("type", meta.type.name)
            }.toString()

            // 5. Write zip archive to OutputStream
            context.contentResolver.openOutputStream(docUri)?.use { os ->
                ZipOutputStream(os).use { zos ->
                    // Entry 1: meta.json
                    zos.putNextEntry(ZipEntry(META_ENTRY))
                    zos.write(metaJson.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // Entry 2: preferences.json
                    zos.putNextEntry(ZipEntry(PREFS_ENTRY))
                    zos.write(preferencesJson.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // Entry 3: agenda.json
                    zos.putNextEntry(ZipEntry(AGENDA_ENTRY))
                    zos.write(agendaJson.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // Entry 4: wallpaper.png
                    if (wallpaperBitmap != null) {
                        zos.putNextEntry(ZipEntry(WALLPAPER_ENTRY))
                        val baos = ByteArrayOutputStream()
                        wallpaperBitmap.compress(Bitmap.CompressFormat.PNG, 90, baos)
                        zos.write(baos.toByteArray())
                        zos.closeEntry()
                    }
                }
            } ?: throw IllegalStateException("Could not open output stream for created backup")

            // 6. Prune backups in the tree folder
            pruneOldBackupsInTree(context, treeUri, maxKeep)

            // 7. Query size
            val size = getDocumentSize(context, docUri)

            BackupItem(
                uri = docUri,
                displayName = fileName,
                meta = meta,
                sizeBytes = size,
                formattedSize = formatFileSize(size),
                lastModified = now,
            )
        }
    }

    /**
     * Restores preferences, agenda and wallpaper from a backup file.
     */
    suspend fun restoreBackup(
        context: Context,
        prefs: Prefs,
        backupFile: File,
    ): Result<BackupMeta> = withContext(Dispatchers.IO) {
        runCatching {
            FileInputStream(backupFile).use { fis ->
                restoreFromInputStream(context, prefs, fis)
            }
        }
    }

    /**
     * Restores preferences, agenda and wallpaper from a content URI.
     */
    suspend fun restoreBackupFromUri(
        context: Context,
        prefs: Prefs,
        uri: Uri,
    ): Result<BackupMeta> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                restoreFromInputStream(context, prefs, stream)
            } ?: throw IllegalStateException("Could not open input stream from URI")
        }
    }

    private suspend fun restoreFromInputStream(
        context: Context,
        prefs: Prefs,
        inputStream: InputStream,
    ): BackupMeta {
        val bytes = inputStream.readBytes()
        if (bytes.size < 4) {
            throw IllegalArgumentException("Arquivo de backup vazio ou inválido")
        }

        val isZip = bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()

        if (!isZip) {
            // Check if plain JSON (e.g. BigCalendar backup)
            val jsonText = bytes.toString(Charsets.UTF_8).trim()
            if (jsonText.startsWith("{")) {
                val rootJson = JSONObject(jsonText)
                if (rootJson.has("activities") || rootJson.has("completedActivities")) {
                    val agendaRepo = AgendaRepository(context)
                    val importedCount = agendaRepo.importAgendaJson(jsonText)
                    return BackupMeta(
                        versionCode = 1,
                        versionName = "BigCalendar",
                        timestamp = System.currentTimeMillis(),
                        formattedDate = rootJson.optString("createdAt", ""),
                        hasWallpaper = false,
                        type = BackupType.MANUAL,
                        preferenceCount = 0,
                        agendaCount = importedCount,
                    )
                }
            }
            throw IllegalArgumentException("Formato de backup não reconhecido")
        }

        var meta: BackupMeta? = null
        var preferencesJson: String? = null
        var agendaJson: String? = null
        var wallpaperBytes: ByteArray? = null

        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                when (entry.name) {
                    META_ENTRY -> {
                        val text = zis.bufferedReader(Charsets.UTF_8).readText()
                        val json = JSONObject(text)
                        meta = BackupMeta(
                            versionCode = json.optInt("versionCode", 1),
                            versionName = json.optString("versionName", ""),
                            timestamp = json.optLong("timestamp", 0L),
                            formattedDate = json.optString("formattedDate", ""),
                            hasWallpaper = json.optBoolean("hasWallpaper", false),
                            type = BackupType.fromName(json.optString("type")),
                            preferenceCount = 0,
                        )
                    }
                    PREFS_ENTRY -> {
                        preferencesJson = zis.bufferedReader(Charsets.UTF_8).readText()
                    }
                    AGENDA_ENTRY -> {
                        agendaJson = zis.bufferedReader(Charsets.UTF_8).readText()
                    }
                    WALLPAPER_ENTRY -> {
                        val baos = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var len: Int
                        while (zis.read(buffer).also { len = it } != -1) {
                            baos.write(buffer, 0, len)
                        }
                        wallpaperBytes = baos.toByteArray()
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        // Apply preferences
        var prefCount = 0
        if (preferencesJson != null) {
            prefCount = prefs.importPreferencesJson(preferencesJson)
        } else {
            throw IllegalArgumentException("No preferences.json found in backup archive")
        }

        // Apply agenda if present
        var importedAgendaCount = 0
        if (agendaJson != null) {
            val agendaRepo = AgendaRepository(context)
            importedAgendaCount = agendaRepo.importAgendaJson(agendaJson)
        }

        if (meta != null) {
            meta = meta.copy(preferenceCount = prefCount, agendaCount = importedAgendaCount)
        }

        // Apply wallpaper if present
        if (wallpaperBytes != null) {
            runCatching {
                val bitmap = BitmapFactory.decodeByteArray(wallpaperBytes, 0, wallpaperBytes.size)
                if (bitmap != null) {
                    val wallpaperManager = WallpaperManager.getInstance(context)
                    wallpaperManager.setBitmap(bitmap)
                }
            }
        }

        return meta ?: BackupMeta(
            versionCode = BuildConfig.VERSION_CODE,
            versionName = BuildConfig.VERSION_NAME,
            timestamp = System.currentTimeMillis(),
            formattedDate = "",
            hasWallpaper = wallpaperBytes != null,
            type = BackupType.MANUAL,
            preferenceCount = prefCount,
            agendaCount = importedAgendaCount,
        )
    }

    /**
     * Lists backup files. If folderUriStr is provided and valid, lists from that SAF tree folder.
     */
    fun listBackups(context: Context, folderUriStr: String?): List<BackupItem> {
        if (folderUriStr.isNullOrBlank()) return emptyList()

        val treeUri = runCatching { Uri.parse(folderUriStr) }.getOrNull() ?: return emptyList()
        val list = listBackupsInTree(context, treeUri)

        return list.sortedByDescending { it.meta?.timestamp ?: it.lastModified }
    }

    /**
     * Lists backups in a SAF tree directory.
     */
    private fun listBackupsInTree(context: Context, treeUri: Uri): List<BackupItem> {
        val list = mutableListOf<BackupItem>()
        runCatching {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: continue
                    if (name.endsWith(BACKUP_EXTENSION) || name.endsWith(LEGACY_BACKUP_EXTENSION) || name.endsWith(".zip") || name.endsWith(".json")) {
                        val childDocId = cursor.getString(idCol)
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                        val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                        val mod = if (modCol != -1) cursor.getLong(modCol) else 0L
                        val meta = readMetaFromUri(context, docUri)
                        list.add(
                            BackupItem(
                                uri = docUri,
                                displayName = name,
                                meta = meta,
                                sizeBytes = size,
                                formattedSize = formatFileSize(size),
                                lastModified = meta?.timestamp ?: mod,
                            )
                        )
                    }
                }
            }
        }
        return list
    }

    /**
     * Reads metadata without fully decompressing the backup from URI.
     */
    fun readMetaFromUri(context: Context, uri: Uri): BackupMeta? {
        return runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes()
            } ?: return null

            if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()) {
                ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == META_ENTRY) {
                            val text = zis.bufferedReader(Charsets.UTF_8).readText()
                            val json = JSONObject(text)
                            return BackupMeta(
                                versionCode = json.optInt("versionCode", 1),
                                versionName = json.optString("versionName", ""),
                                timestamp = json.optLong("timestamp", 0L),
                                formattedDate = json.optString("formattedDate", ""),
                                hasWallpaper = json.optBoolean("hasWallpaper", false),
                                type = BackupType.fromName(json.optString("type")),
                                preferenceCount = json.optInt("itemCount", 0),
                            )
                        }
                        entry = zis.nextEntry
                    }
                }
            } else {
                val jsonText = bytes.toString(Charsets.UTF_8).trim()
                if (jsonText.startsWith("{")) {
                    val rootJson = JSONObject(jsonText)
                    if (rootJson.has("activities") || rootJson.has("completedActivities")) {
                        val activeCount = rootJson.optJSONArray("activities")?.length() ?: 0
                        val compCount = rootJson.optJSONArray("completedActivities")?.length() ?: 0
                        return BackupMeta(
                            versionCode = 1,
                            versionName = "BigCalendar",
                            timestamp = System.currentTimeMillis(),
                            formattedDate = rootJson.optString("createdAt", ""),
                            hasWallpaper = false,
                            type = BackupType.MANUAL,
                            preferenceCount = 0,
                            agendaCount = activeCount + compCount,
                        )
                    }
                }
            }
            null
        }.getOrNull()
    }

    /**
     * Deletes a backup item.
     */
    fun deleteBackup(context: Context, item: BackupItem): Boolean {
        return runCatching {
            DocumentsContract.deleteDocument(context.contentResolver, item.uri)
        }.getOrElse {
            if (item.file != null) item.file.delete() else false
        }
    }

    /**
     * Shares a backup item via Android system share sheet.
     */
    fun shareBackup(context: Context, item: BackupItem) {
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, item.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, item.displayName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /**
     * Enforces the maximum retention limit of backups in a tree folder.
     */
    private fun pruneOldBackupsInTree(context: Context, treeUri: Uri, maxKeep: Int) {
        if (maxKeep <= 0) return
        runCatching {
            val backups = listBackupsInTree(context, treeUri)
            if (backups.size > maxKeep) {
                val toDelete = backups.drop(maxKeep)
                toDelete.forEach { item ->
                    runCatching {
                        DocumentsContract.deleteDocument(context.contentResolver, item.uri)
                    }
                }
            }
        }
    }

    private fun getDocumentSize(context: Context, docUri: Uri): Long {
        return runCatching {
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    if (idx != -1) cursor.getLong(idx) else 0L
                } else 0L
            } ?: 0L
        }.getOrDefault(0L)
    }

    /**
     * Checks if an automatic backup is due and runs it if enabled and destination folder is set.
     */
    suspend fun checkAndRunAutoBackup(context: Context, prefs: Prefs) = withContext(Dispatchers.IO) {
        runCatching {
            val enabled = prefs.autoBackupEnabled.first()
            if (!enabled) return@runCatching

            val folderUriStr = prefs.backupFolderUri.first()
            if (folderUriStr.isNullOrBlank()) return@runCatching

            val treeUri = Uri.parse(folderUriStr)
            if (!hasFolderPermission(context, treeUri)) return@runCatching

            val freq = prefs.autoBackupFrequency.first()
            val lastTime = prefs.lastAutoBackupTimestamp.first()
            val intervalMillis = freq.days * 24L * 60L * 60L * 1000L
            val now = System.currentTimeMillis()

            if (now - lastTime >= intervalMillis) {
                val includeWallpaper = prefs.backupIncludeWallpaper.first()
                val maxKeep = prefs.backupMaxKeep.first()
                val result = createBackup(
                    context = context,
                    prefs = prefs,
                    folderUriStr = folderUriStr,
                    type = BackupType.AUTOMATIC,
                    includeWallpaper = includeWallpaper,
                    maxKeep = maxKeep,
                )
                if (result.isSuccess) {
                    prefs.setLastAutoBackupTimestamp(now)
                }
            }
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
            else -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
        }
    }
}
