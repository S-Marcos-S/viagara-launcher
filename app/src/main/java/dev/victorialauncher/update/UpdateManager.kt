// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.victorialauncher.BuildConfig
import dev.victorialauncher.MainActivity
import dev.victorialauncher.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

sealed class DownloadStatus {
    object Idle : DownloadStatus()
    data class Downloading(val progressPercent: Int) : DownloadStatus()
    data class Finished(val fileUri: Uri?) : DownloadStatus()
    object InstallingRoot : DownloadStatus()
    data class Failed(val error: String) : DownloadStatus()
}

data class UpdateInfo(
    val hasUpdate: Boolean,
    val tagName: String,
    val releaseName: String,
    val commitSha: String?,
    val versionName: String? = null,
    val changelog: String,
    val apkDownloadUrl: String,
    val apkSize: Long,
    val publishedAtMs: Long,
) {
    val displayVersion: String
        get() {
            val v = versionName?.trim()
            return when {
                !v.isNullOrBlank() -> if (v.startsWith("v", ignoreCase = true)) v else "v$v"
                tagName.isNotBlank() && !tagName.equals("latest", ignoreCase = true) -> {
                    if (tagName.startsWith("v", ignoreCase = true)) tagName else "v$tagName"
                }
                else -> "v${BuildConfig.VERSION_NAME}"
            }
        }
}

object UpdateManager {
    const val ACTION_OPEN_UPDATE_CHANGELOG = "dev.victorialauncher.action.OPEN_UPDATE_CHANGELOG"
    const val EXTRA_OPEN_UPDATE = "dev.victorialauncher.extra.OPEN_UPDATE"
    private const val UPDATE_NOTIFICATION_CHANNEL_ID = "victoria_launcher_updates"
    private const val UPDATE_NOTIFICATION_ID = 4242
    private const val PREFS_NAME = "victoria_update_prefs"
    private const val KEY_LAST_NOTIFIED_UPDATE = "last_notified_update"
    private const val KEY_DOWNLOADED_APK_URI = "downloaded_apk_uri"
    private const val KEY_DOWNLOADED_VERSION_CODE = "downloaded_version_code"
    private const val KEY_DOWNLOADED_VERSION_NAME = "downloaded_version_name"
    private const val KEY_DOWNLOADED_COMMIT_SHA = "downloaded_commit_sha"

    private const val GITHUB_REPO = "S-Marcos-S/viagra-launcher"
    private const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases?per_page=5"
    private const val FALLBACK_RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/tags/latest"

    private var appContext: Context? = null
    private var lastKnownUpdate: UpdateInfo? = null

    private val _updateAvailable = MutableStateFlow<UpdateInfo?>(null)
    val updateAvailable: StateFlow<UpdateInfo?> = _updateAvailable.asStateFlow()

    private val _downloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus: StateFlow<DownloadStatus> = _downloadStatus.asStateFlow()

    private val _showChangelogRequested = MutableStateFlow(false)
    val showChangelogRequested: StateFlow<Boolean> = _showChangelogRequested.asStateFlow()

    private val updateScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var lastCheckTimeMs = 0L
    private const val CHECK_INTERVAL_MS = 20 * 60 * 1000L // 20 minutos de cache para economizar bateria e cota

    private var dismissedUpdateKey: String? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun requestShowUpdateChangelog() {
        if (_updateAvailable.value == null && lastKnownUpdate != null) {
            _updateAvailable.value = lastKnownUpdate
        }
        _showChangelogRequested.value = true
    }

    fun dismissChangelogRequest() {
        _showChangelogRequested.value = false
    }

    fun dismissCurrentUpdate() {
        val info = _updateAvailable.value ?: return
        dismissedUpdateKey = info.commitSha ?: info.tagName
    }

    fun isUpdateDismissed(info: UpdateInfo): Boolean {
        val key = info.commitSha ?: info.tagName
        return key != null && key == dismissedUpdateKey
    }

    fun resetDismissed() {
        dismissedUpdateKey = null
    }

    fun maybeNotifyUpdateAvailable(context: Context, update: UpdateInfo) {
        val appCtx = context.applicationContext
        val updateKey = update.commitSha ?: update.tagName
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastNotified = prefs.getString(KEY_LAST_NOTIFIED_UPDATE, null)
        if (lastNotified != updateKey) {
            showUpdateNotification(appCtx, update)
            prefs.edit().putString(KEY_LAST_NOTIFIED_UPDATE, updateKey).apply()
        }
    }

    fun showUpdateNotification(context: Context, update: UpdateInfo) {
        try {
            val appCtx = context.applicationContext
            val notificationManager = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    UPDATE_NOTIFICATION_CHANNEL_ID,
                    appCtx.getString(R.string.update_notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = appCtx.getString(R.string.update_notification_channel_desc)
                    setShowBadge(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        appCtx,
                        android.Manifest.permission.POST_NOTIFICATIONS,
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
            }

            val intent = Intent(appCtx, MainActivity::class.java).apply {
                action = ACTION_OPEN_UPDATE_CHANGELOG
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_UPDATE, true)
            }

            val pendingIntent = PendingIntent.getActivity(
                appCtx,
                UPDATE_NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val title = appCtx.getString(R.string.update_notification_title, update.displayVersion)
            val text = appCtx.getString(R.string.update_notification_text)

            val notification = NotificationCompat.Builder(appCtx, UPDATE_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(UPDATE_NOTIFICATION_ID, notification)
        } catch (_: Throwable) {
            // Silencia qualquer exceção ao postar notificação
        }
    }

    fun cancelUpdateNotification(context: Context) {
        try {
            val appCtx = context.applicationContext
            val notificationManager = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(UPDATE_NOTIFICATION_ID)
        } catch (_: Throwable) {}
    }

    fun checkForUpdates(coroutineScope: CoroutineScope, force: Boolean = false, context: Context? = null) {
        if (context != null && appContext == null) {
            init(context)
        }
        val now = System.currentTimeMillis()
        if (!force && now - lastCheckTimeMs < CHECK_INTERVAL_MS) {
            return
        }
        lastCheckTimeMs = now

        coroutineScope.launch(Dispatchers.IO) {
            try {
                var responseText: String? = null
                // 1. Tenta obter a lista recente de releases
                try {
                    val url = URL(RELEASES_API_URL)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8000
                        readTimeout = 8000
                        setRequestProperty("Accept", "application/vnd.github.v3+json")
                        setRequestProperty("User-Agent", "VictoriaLauncher/${BuildConfig.VERSION_NAME}")
                    }
                    if (conn.responseCode == 200) {
                        responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    }
                } catch (_: Exception) {}

                // Fallback para tag latest direta se a lista falhar
                if (responseText.isNullOrBlank()) {
                    try {
                        val fallbackUrl = URL(FALLBACK_RELEASES_API_URL)
                        val conn = (fallbackUrl.openConnection() as HttpURLConnection).apply {
                            connectTimeout = 8000
                            readTimeout = 8000
                            setRequestProperty("Accept", "application/vnd.github.v3+json")
                            setRequestProperty("User-Agent", "VictoriaLauncher/${BuildConfig.VERSION_NAME}")
                        }
                        if (conn.responseCode == 200) {
                            responseText = conn.inputStream.bufferedReader().use { it.readText() }
                        }
                    } catch (_: Exception) {}
                }

                if (!responseText.isNullOrBlank()) {
                    val releasesList = mutableListOf<JSONObject>()
                    val trimmed = responseText.trim()
                    if (trimmed.startsWith("[")) {
                        val arr = org.json.JSONArray(trimmed)
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i)
                            if (item != null) releasesList.add(item)
                        }
                    } else if (trimmed.startsWith("{")) {
                        releasesList.add(JSONObject(trimmed))
                    }

                    if (releasesList.isNotEmpty()) {
                        val currentVersion = BuildConfig.VERSION_NAME
                        val currentSha = BuildConfig.GIT_SHA.trim()
                        val currentBuildTime = BuildConfig.BUILD_TIME_MILLIS

                        // 1. Procura primeiro uma release oficial com versão maior que a atual
                        var higherVersionRelease: JSONObject? = null
                        var chosenVersion: String? = null

                        for (rel in releasesList) {
                            val tag = rel.optString("tag_name", "")
                            val name = rel.optString("name", "")
                            val v = extractVersion(tag, name)
                            if (v != null && isVersionGreater(v, currentVersion)) {
                                higherVersionRelease = rel
                                chosenVersion = v
                                break
                            }
                        }

                        // 2. Se nenhuma versão for estritamente maior, escolhe a release que tiver o APK mais recente (ou "latest")
                        val chosenRelease: JSONObject = higherVersionRelease ?: run {
                            val latestRelease = releasesList.find { it.optString("tag_name").equals("latest", ignoreCase = true) }
                            val newestAssetRelease = releasesList.maxByOrNull { rel ->
                                val assets = rel.optJSONArray("assets") ?: return@maxByOrNull 0L
                                var maxMs = 0L
                                for (i in 0 until assets.length()) {
                                    val a = assets.optJSONObject(i) ?: continue
                                    val name = a.optString("name", "")
                                    if (name.contains("release") && name.endsWith(".apk")) {
                                        val u = a.optString("updated_at", "")
                                        if (u.isNotBlank()) {
                                            try {
                                                val ms = Instant.parse(u).toEpochMilli()
                                                if (ms > maxMs) maxMs = ms
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }
                                maxMs
                            }
                            newestAssetRelease ?: latestRelease ?: releasesList.first()
                        }

                        val tagName = chosenRelease.optString("tag_name", "latest")
                        val releaseName = chosenRelease.optString("name", "")
                        val body = chosenRelease.optString("body", "")

                        // Extrai commit SHA da descrição se presente: "Built automatically from commit `...`."
                        val shaRegex = Regex("""commit [`']?([a-f0-9]{7,40})""", RegexOption.IGNORE_CASE)
                        val remoteSha = shaRegex.find(body)?.groupValues?.get(1)?.trim()

                        val assets = chosenRelease.optJSONArray("assets")
                        var apkUrl: String? = null
                        var apkSize: Long = 0L
                        var updatedAtMs: Long = 0L

                        if (assets != null) {
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                val name = asset.optString("name", "")
                                if (name.contains("release") && name.endsWith(".apk")) {
                                    apkUrl = asset.optString("browser_download_url")
                                    apkSize = asset.optLong("size", 0L)
                                    val updatedStr = asset.optString("updated_at", "")
                                    if (updatedStr.isNotBlank()) {
                                        try {
                                            updatedAtMs = Instant.parse(updatedStr).toEpochMilli()
                                        } catch (_: Exception) {}
                                    }
                                    break
                                }
                            }
                        }

                        if (apkUrl.isNullOrBlank()) {
                            apkUrl = "https://github.com/$GITHUB_REPO/releases/download/latest/viagra-launcher-release.apk"
                        }

                        val isNewerVersion = chosenVersion != null && isVersionGreater(chosenVersion, currentVersion)

                        val hasNewerBuild = when {
                            isNewerVersion -> true
                            // Se o commit SHA é conhecido nos dois lados, compara diretamente:
                            !remoteSha.isNullOrBlank() && currentSha.isNotBlank() -> {
                                val isSameSha = remoteSha.startsWith(currentSha, ignoreCase = true) ||
                                        currentSha.startsWith(remoteSha, ignoreCase = true)
                                !isSameSha
                            }
                            // Fallback para comparação por timestamp:
                            updatedAtMs > 0L && currentBuildTime > 0L -> {
                                updatedAtMs > currentBuildTime + 30_000L
                            }
                            else -> false
                        }

                        lastCheckTimeMs = now
                        if (hasNewerBuild) {
                            var changelog = ""
                            if (body.isNotBlank()) {
                                val lines = body.lines().filter {
                                    !it.contains("Direct APK Download") &&
                                            !it.contains("releases/download") &&
                                            !it.startsWith("- [Download") &&
                                            !it.contains("Built automatically from commit")
                                }
                                val candidate = lines.joinToString("\n").trim()
                                if (candidate.isNotBlank() && candidate.lines().size >= 2) {
                                    changelog = candidate
                                }
                            }

                            if (changelog.isBlank() && !remoteSha.isNullOrBlank()) {
                                try {
                                    val commitUrl = URL("https://api.github.com/repos/$GITHUB_REPO/commits/$remoteSha")
                                    val commitConn = (commitUrl.openConnection() as HttpURLConnection).apply {
                                        connectTimeout = 5000
                                        readTimeout = 5000
                                        setRequestProperty("Accept", "application/vnd.github.v3+json")
                                        setRequestProperty("User-Agent", "VictoriaLauncher/${BuildConfig.VERSION_NAME}")
                                    }
                                    if (commitConn.responseCode == 200) {
                                        val commitResp = commitConn.inputStream.bufferedReader().use { it.readText() }
                                        val commitJson = JSONObject(commitResp)
                                        val msg = commitJson.optJSONObject("commit")?.optString("message", "")
                                        if (!msg.isNullOrBlank()) {
                                            changelog = msg.trim()
                                        }
                                    }
                                } catch (_: Exception) {}
                            }

                            if (changelog.isBlank()) {
                                changelog = "Nova versão compilada automaticamente via GitHub Actions."
                            }

                            val displayVer = chosenVersion
                                ?: extractVersion(tagName, releaseName)
                                ?: BuildConfig.VERSION_NAME

                            val updateInfo = UpdateInfo(
                                hasUpdate = true,
                                tagName = tagName,
                                releaseName = releaseName,
                                commitSha = remoteSha,
                                versionName = displayVer,
                                changelog = changelog,
                                apkDownloadUrl = apkUrl,
                                apkSize = apkSize,
                                publishedAtMs = updatedAtMs,
                            )
                            lastKnownUpdate = updateInfo
                            _updateAvailable.value = updateInfo
                            appContext?.let { ctx ->
                                maybeNotifyUpdateAvailable(ctx, updateInfo)
                            }
                        } else {
                            _updateAvailable.value = null
                            appContext?.let { ctx ->
                                cancelUpdateNotification(ctx)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Silencia erros de conexão offline
            }
        }
    }

    private fun extractVersion(tagName: String, releaseName: String): String? {
        val versionRegex = Regex("""v?([0-9]+(?:\.[0-9]+)+)""")
        return versionRegex.find(tagName)?.groupValues?.get(1)
            ?: versionRegex.find(releaseName)?.groupValues?.get(1)
    }

    private fun isVersionGreater(remote: String, current: String): Boolean {
        val cleanRemote = remote.trim().removePrefix("v").removePrefix("V")
        val cleanCurrent = current.trim().removePrefix("v").removePrefix("V")
        val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    fun startDownload(
        context: Context,
        update: UpdateInfo,
        autoInstallWithRoot: Boolean = false,
    ) {
        if (_downloadStatus.value is DownloadStatus.Downloading) {
            return
        }

        val appContext = context.applicationContext
        cancelUpdateNotification(appContext)
        _downloadStatus.value = DownloadStatus.Downloading(0)
        Toast.makeText(
            appContext,
            appContext.getString(R.string.update_toast_downloading),
            Toast.LENGTH_SHORT,
        ).show()

        updateScope.launch {
            try {
                // 1. Segue redirecionamentos HTTP 302/307 do GitHub Releases até o asset final
                var currentUrl = update.apkDownloadUrl
                var conn: HttpURLConnection? = null
                var redirects = 0
                while (redirects < 6) {
                    val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 25000
                        instanceFollowRedirects = false
                        setRequestProperty("User-Agent", "Mozilla/5.0 VictoriaLauncher/${BuildConfig.VERSION_NAME}")
                        setRequestProperty("Accept", "*/*")
                    }
                    val code = connection.responseCode
                    if (code in 300..399) {
                        val location = connection.getHeaderField("Location")
                        connection.disconnect()
                        if (!location.isNullOrBlank()) {
                            currentUrl = location
                            redirects++
                            continue
                        }
                    }
                    conn = connection
                    break
                }

                if (conn == null || conn.responseCode != 200) {
                    throw IllegalStateException("Servidor retornou código ${conn?.responseCode ?: "desconhecido"}")
                }

                val totalLength = conn.contentLengthLong.takeIf { it > 0 } ?: update.apkSize

                // 2. Salva o fluxo na pasta Downloads usando MediaStore (Android 10+) ou FileOutputStream
                val savedUri = conn.inputStream.use { input ->
                    saveStreamToDownloads(appContext, input, totalLength) { percent ->
                        _downloadStatus.value = DownloadStatus.Downloading(percent)
                    }
                }

                conn.disconnect()

                // Salva metadados da versão para limpeza automática pós-atualização
                val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putString(KEY_DOWNLOADED_APK_URI, savedUri?.toString())
                    .putInt(KEY_DOWNLOADED_VERSION_CODE, BuildConfig.VERSION_CODE)
                    .putString(KEY_DOWNLOADED_VERSION_NAME, update.versionName)
                    .putString(KEY_DOWNLOADED_COMMIT_SHA, update.commitSha)
                    .apply()

                _downloadStatus.value = DownloadStatus.Finished(savedUri)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        appContext,
                        appContext.getString(R.string.update_toast_download_completed),
                        Toast.LENGTH_LONG,
                    ).show()

                    if (savedUri != null) {
                        if (autoInstallWithRoot && RootInstaller.isRootAvailable()) {
                            installViaRoot(appContext, savedUri)
                        } else {
                            promptInstall(appContext, savedUri)
                        }
                    }
                }
            } catch (e: Exception) {
                _downloadStatus.value = DownloadStatus.Failed(e.message ?: "Erro")
                withContext(Dispatchers.Main) {
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(update.apkDownloadUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        appContext.startActivity(browserIntent)
                    } catch (_: Exception) {
                        Toast.makeText(
                            appContext,
                            appContext.getString(R.string.update_toast_error, e.message ?: ""),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    }

    private fun saveStreamToDownloads(
        context: Context,
        input: InputStream,
        totalBytes: Long,
        onProgress: (Int) -> Unit,
    ): Uri? {
        val fileName = "victoria-launcher-release.apk"

        // Tenta remover qualquer APK antigo com o mesmo nome na pasta física
        try {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val physicalFile = File(dir, fileName)
            if (physicalFile.exists()) {
                physicalFile.delete()
            }
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val downloadsUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI

            // Limpa entradas anteriores do MediaStore para não criar duplicatas como (1).apk
            try {
                val projection = arrayOf(MediaStore.MediaColumns._ID)
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(fileName)
                resolver.query(downloadsUri, projection, selection, selectionArgs, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        resolver.delete(ContentUris.withAppendedId(downloadsUri, id), null, null)
                    }
                }
            } catch (_: Exception) {}

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = resolver.insert(downloadsUri, contentValues)
                ?: throw IllegalStateException("Não foi possível criar entrada no MediaStore.Downloads")

            try {
                resolver.openOutputStream(uri)?.use { output ->
                    copyStreamWithProgress(input, output, totalBytes, onProgress)
                } ?: throw IllegalStateException("Falha ao abrir fluxo de saída em Downloads")

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                return uri
            } catch (e: Exception) {
                try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                throw e
            }
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val targetFile = File(dir, fileName)
            if (targetFile.exists()) {
                targetFile.delete()
            }
            targetFile.outputStream().use { output ->
                copyStreamWithProgress(input, output, totalBytes, onProgress)
            }
            return Uri.fromFile(targetFile)
        }
    }

    private fun copyStreamWithProgress(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        onProgress: (Int) -> Unit,
    ) {
        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalRead = 0L
        var lastPercent = -1
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            totalRead += bytesRead
            if (totalBytes > 0) {
                val percent = ((totalRead * 100) / totalBytes).toInt().coerceIn(0, 100)
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(percent)
                }
            }
        }
        output.flush()
        onProgress(100)
    }

    fun promptInstall(context: Context, uri: Uri) {
        try {
            val contentUri = if (uri.scheme == "file") {
                val file = uri.path?.let { File(it) }
                if (file != null && file.exists()) {
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                } else uri
            } else {
                uri
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Se o instalador não puder ser chamado via intent, o APK está disponível na pasta Downloads
        }
    }

    fun installUpdate(context: Context, uri: Uri, useRoot: Boolean = false) {
        if (useRoot && RootInstaller.isRootAvailable()) {
            installViaRoot(context, uri)
        } else {
            promptInstall(context, uri)
        }
    }

    fun installViaRoot(context: Context, uri: Uri) {
        val appCtx = context.applicationContext
        _downloadStatus.value = DownloadStatus.InstallingRoot
        updateScope.launch {
            val result = RootInstaller.installApkViaRoot(appCtx, uri)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    Toast.makeText(
                        appCtx,
                        appCtx.getString(R.string.update_toast_root_success),
                        Toast.LENGTH_LONG,
                    ).show()
                    cleanupDownloadedApk(appCtx, force = true)
                    _downloadStatus.value = DownloadStatus.Idle
                    _showChangelogRequested.value = false
                    _updateAvailable.value = null
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Erro desconhecido"
                    Toast.makeText(
                        appCtx,
                        appCtx.getString(R.string.update_toast_root_failed, error),
                        Toast.LENGTH_LONG,
                    ).show()
                    _downloadStatus.value = DownloadStatus.Finished(uri)
                    promptInstall(appCtx, uri)
                }
            }
        }
    }

    /**
     * Cleans up downloaded APK files from Downloads and MediaStore once the app
     * is successfully updated (or when forced, e.g. after silent root install).
     */
    fun cleanupDownloadedApk(context: Context, force: Boolean = false) {
        updateScope.launch(Dispatchers.IO) {
            try {
                val appCtx = context.applicationContext
                val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val savedUriStr = prefs.getString(KEY_DOWNLOADED_APK_URI, null)
                if (savedUriStr.isNullOrBlank()) {
                    return@launch
                }

                val downloadedVerCode = prefs.getInt(KEY_DOWNLOADED_VERSION_CODE, -1)
                val downloadedVerName = prefs.getString(KEY_DOWNLOADED_VERSION_NAME, null)
                val downloadedSha = prefs.getString(KEY_DOWNLOADED_COMMIT_SHA, null)
                val currentVerCode = BuildConfig.VERSION_CODE
                val currentVerName = BuildConfig.VERSION_NAME
                val currentSha = BuildConfig.GIT_SHA.trim()

                val wasUpdated = force ||
                        (downloadedVerCode != -1 && currentVerCode > downloadedVerCode) ||
                        (!downloadedVerName.isNullOrBlank() && (isVersionGreater(currentVerName, downloadedVerName) || currentVerName == downloadedVerName)) ||
                        (!downloadedSha.isNullOrBlank() && currentSha.isNotBlank() && currentSha.startsWith(downloadedSha, ignoreCase = true))

                if (!wasUpdated) {
                    return@launch
                }

                // 1. Tenta apagar apenas a URI específica registrada pelo launcher
                try {
                    val uri = Uri.parse(savedUriStr)
                    if (uri.scheme == "file") {
                        uri.path?.let { path ->
                            val f = File(path)
                            if (f.exists()) f.delete()
                        }
                    } else {
                        appCtx.contentResolver.delete(uri, null, null)
                    }
                } catch (_: Throwable) {}

                // 2. Limpa metadados salvos
                prefs.edit()
                    .remove(KEY_DOWNLOADED_APK_URI)
                    .remove(KEY_DOWNLOADED_VERSION_CODE)
                    .remove(KEY_DOWNLOADED_VERSION_NAME)
                    .remove(KEY_DOWNLOADED_COMMIT_SHA)
                    .apply()
            } catch (_: Throwable) {}
        }
    }
}
