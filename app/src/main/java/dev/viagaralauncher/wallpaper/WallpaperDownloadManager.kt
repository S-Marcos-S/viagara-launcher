// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.wallpaper

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.viagaralauncher.BuildConfig
import dev.viagaralauncher.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object WallpaperDownloadManager {

    private const val CHANNEL_ID = "wallpaper_downloads_channel"
    private const val NOTIFICATION_ID = 4005

    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val queueChannel = Channel<WallpaperDownloadTask>(Channel.UNLIMITED)
    private val isWorkerRunning = AtomicBoolean(false)

    // Set of wallpaper IDs currently active (queued or downloading)
    private val activeItemIds = ConcurrentHashMap.newKeySet<String>()

    // Total downloads registered in the current active batch
    private val batchTotal = AtomicInteger(0)
    // How many items completed in current batch
    private val batchCompleted = AtomicInteger(0)
    // How many items currently remaining in queue or actively downloading
    private val activeCount = AtomicInteger(0)

    private data class WallpaperDownloadTask(
        val context: Context,
        val item: WallpaperItem,
    )

    fun downloadWallpaper(context: Context, item: WallpaperItem) {
        val appCtx = context.applicationContext

        if (activeItemIds.contains(item.id)) {
            mainHandler.post {
                Toast.makeText(
                    appCtx,
                    appCtx.getString(R.string.wallpaper_download_already_queued),
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        activeItemIds.add(item.id)
        val currentActive = activeCount.incrementAndGet()
        batchTotal.incrementAndGet()

        mainHandler.post {
            Toast.makeText(
                appCtx,
                appCtx.getString(R.string.wallpaper_download_starting, item.name),
                Toast.LENGTH_SHORT
            ).show()
        }

        // Show/update initial notification immediately
        ensureChannelCreated(appCtx)
        postInitialProgressNotification(appCtx, item, currentActive)

        queueChannel.trySend(WallpaperDownloadTask(appCtx, item))

        startWorkerIfNeeded(appCtx)
    }

    private fun startWorkerIfNeeded(context: Context) {
        if (isWorkerRunning.compareAndSet(false, true)) {
            downloadScope.launch {
                var lastSavedUri: Uri? = null
                var lastSavedMimeType = "image/png"
                var lastSavedName: String? = null

                try {
                    while (true) {
                        val task = queueChannel.tryReceive().getOrNull()
                        if (task == null) {
                            break
                        }

                        val item = task.item
                        val currentBatchIndex = batchCompleted.get() + 1
                        val totalInBatch = batchTotal.get()
                        val currentActive = activeCount.get()

                        var resultUri: Uri? = null
                        var error: Exception? = null

                        try {
                            resultUri = performDownload(
                                context = task.context,
                                item = item,
                                batchIndex = currentBatchIndex,
                                batchTotal = totalInBatch,
                                activeCount = currentActive,
                            )
                        } catch (e: Exception) {
                            error = e
                        }

                        activeItemIds.remove(item.id)
                        val remainingActive = activeCount.decrementAndGet()
                        batchCompleted.incrementAndGet()

                        if (resultUri != null) {
                            lastSavedUri = resultUri
                            lastSavedMimeType = getMimeType(item)
                            lastSavedName = item.name
                        } else if (error != null) {
                            mainHandler.post {
                                Toast.makeText(
                                    task.context,
                                    task.context.getString(R.string.wallpaper_download_failed, item.name),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        if (remainingActive > 0) {
                            continue
                        } else {
                            break
                        }
                    }
                } finally {
                    isWorkerRunning.set(false)

                    val completedCount = batchCompleted.get()

                    // Reset batch counters
                    batchCompleted.set(0)
                    batchTotal.set(0)
                    activeCount.set(0)
                    activeItemIds.clear()

                    if (completedCount > 0 && lastSavedName != null) {
                        postCompletedNotification(
                            context = context,
                            completedCount = completedCount,
                            lastName = lastSavedName,
                            uri = lastSavedUri,
                            mimeType = lastSavedMimeType
                        )
                    } else {
                        cancelNotification(context)
                    }
                }
            }
        }
    }

    private suspend fun performDownload(
        context: Context,
        item: WallpaperItem,
        batchIndex: Int,
        batchTotal: Int,
        activeCount: Int,
    ): Uri? = withContext(Dispatchers.IO) {
        val cachedFile = WallpaperRepository.getCachedFile(context, item.fullUrl)
        val isVideo = item.isLive || item.type == "video" || item.fullUrl.endsWith(".mp4", ignoreCase = true)
        val ext = getExtension(item.fullUrl, isVideo)
        val mimeType = getMimeType(item)
        val cleanName = item.name.replace(Regex("[^a-zA-Z0-9 _-]"), "").trim().ifBlank { "wallpaper" }
        val fileName = "${cleanName}_${item.id}.$ext"

        var lastNotificationTime = 0L

        val onProgress: (Int) -> Unit = { percent ->
            val now = System.currentTimeMillis()
            if (percent == 100 || now - lastNotificationTime >= 350L) {
                lastNotificationTime = now
                postProgressNotification(
                    context = context,
                    item = item,
                    batchIndex = batchIndex,
                    batchTotal = batchTotal,
                    activeCount = activeCount,
                    progressPercent = percent
                )
            }
        }

        if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
            val totalBytes = cachedFile.length()
            FileInputStream(cachedFile).use { input ->
                saveStreamToStorage(
                    context = context,
                    input = input,
                    totalBytes = totalBytes,
                    fileName = fileName,
                    mimeType = mimeType,
                    isVideo = isVideo,
                    onProgress = onProgress
                )
            }
        } else {
            // Download from HTTP fullUrl
            var currentUrl = item.fullUrl
            var conn: HttpURLConnection? = null
            var redirects = 0
            while (redirects < 6) {
                val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "Mozilla/5.0 ViagaraLauncher/${BuildConfig.VERSION_NAME}")
                    setRequestProperty("Accept", "*/*")
                }
                val code = connection.responseCode
                if (code in 300..399) {
                    val loc = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (!loc.isNullOrBlank()) {
                        currentUrl = loc
                        redirects++
                        continue
                    }
                }
                conn = connection
                break
            }

            if (conn == null || conn.responseCode != 200) {
                throw IllegalStateException("Server returned HTTP ${conn?.responseCode}")
            }

            val totalBytes = conn.contentLengthLong.takeIf { it > 0 } ?: 0L

            val cacheDir = File(context.cacheDir, "wallpaper_cache").apply { if (!exists()) mkdirs() }
            val hash = urlHash(item.fullUrl)
            val cacheTarget = File(cacheDir, hash)
            val cacheTemp = File(cacheDir, "$hash.tmp")

            val uri = conn.inputStream.use { input ->
                saveStreamToStorageAndCache(
                    context = context,
                    input = input,
                    totalBytes = totalBytes,
                    fileName = fileName,
                    mimeType = mimeType,
                    isVideo = isVideo,
                    cacheTempFile = cacheTemp,
                    onProgress = onProgress
                )
            }

            conn.disconnect()

            if (cacheTemp.exists() && cacheTemp.length() > 0) {
                cacheTemp.renameTo(cacheTarget)
            }

            uri
        }
    }

    private fun saveStreamToStorage(
        context: Context,
        input: InputStream,
        totalBytes: Long,
        fileName: String,
        mimeType: String,
        isVideo: Boolean,
        onProgress: (Int) -> Unit,
    ): Uri? {
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }

            val relativeDir = if (isVideo) {
                "${Environment.DIRECTORY_MOVIES}/Wallpapers"
            } else {
                "${Environment.DIRECTORY_PICTURES}/Wallpapers"
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Failed to insert MediaStore entry")

            try {
                resolver.openOutputStream(uri)?.use { output ->
                    copyStreamWithProgress(input, output, null, totalBytes, onProgress)
                } ?: throw IllegalStateException("Failed to open output stream")

                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return uri
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
        } else {
            val baseDir = Environment.getExternalStoragePublicDirectory(
                if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            )
            val dir = File(baseDir, "Wallpapers")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { output ->
                copyStreamWithProgress(input, output, null, totalBytes, onProgress)
            }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null)
            return Uri.fromFile(file)
        }
    }

    private fun saveStreamToStorageAndCache(
        context: Context,
        input: InputStream,
        totalBytes: Long,
        fileName: String,
        mimeType: String,
        isVideo: Boolean,
        cacheTempFile: File,
        onProgress: (Int) -> Unit,
    ): Uri? {
        val resolver = context.contentResolver
        val cacheOutput = FileOutputStream(cacheTempFile)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val relativeDir = if (isVideo) {
                "${Environment.DIRECTORY_MOVIES}/Wallpapers"
            } else {
                "${Environment.DIRECTORY_PICTURES}/Wallpapers"
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Failed to insert MediaStore entry")

            try {
                cacheOutput.use { cOut ->
                    resolver.openOutputStream(uri)?.use { output ->
                        copyStreamWithProgress(input, output, cOut, totalBytes, onProgress)
                    } ?: throw IllegalStateException("Failed to open output stream")
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return uri
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                runCatching { cacheTempFile.delete() }
                throw e
            }
        } else {
            val baseDir = Environment.getExternalStoragePublicDirectory(
                if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            )
            val dir = File(baseDir, "Wallpapers")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            cacheOutput.use { cOut ->
                FileOutputStream(file).use { output ->
                    copyStreamWithProgress(input, output, cOut, totalBytes, onProgress)
                }
            }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null)
            return Uri.fromFile(file)
        }
    }

    private fun copyStreamWithProgress(
        input: InputStream,
        output1: OutputStream,
        output2: OutputStream?,
        totalBytes: Long,
        onProgress: (Int) -> Unit,
    ) {
        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalRead = 0L
        var lastPercent = -1

        while (input.read(buffer).also { bytesRead = it } != -1) {
            output1.write(buffer, 0, bytesRead)
            output2?.write(buffer, 0, bytesRead)
            totalRead += bytesRead

            if (totalBytes > 0) {
                val percent = ((totalRead * 100) / totalBytes).toInt().coerceIn(0, 100)
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(percent)
                }
            }
        }
        output1.flush()
        output2?.flush()
        onProgress(100)
    }

    private fun ensureChannelCreated(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.wallpaper_download_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.wallpaper_download_channel_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun postInitialProgressNotification(context: Context, item: WallpaperItem, activeCount: Int) {
        postProgressNotification(
            context = context,
            item = item,
            batchIndex = 1,
            batchTotal = batchTotal.get(),
            activeCount = activeCount,
            progressPercent = 0
        )
    }

    private fun postProgressNotification(
        context: Context,
        item: WallpaperItem,
        batchIndex: Int,
        batchTotal: Int,
        activeCount: Int,
        progressPercent: Int,
    ) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasPermission) return
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val title = if (activeCount <= 1) {
                context.getString(R.string.wallpaper_downloading_single)
            } else {
                context.getString(R.string.wallpaper_downloading_multiple, activeCount)
            }

            val text = if (batchTotal > 1) {
                "${item.name} ($batchIndex/$batchTotal) • $progressPercent%"
            } else {
                "${item.name} • $progressPercent%"
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(text)
                .setProgress(100, progressPercent, progressPercent <= 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun postCompletedNotification(
        context: Context,
        completedCount: Int,
        lastName: String,
        uri: Uri?,
        mimeType: String,
    ) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasPermission) return
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val title = context.getString(R.string.wallpaper_download_completed_title)
            val text = if (completedCount <= 1) {
                context.getString(R.string.wallpaper_download_completed_single, lastName)
            } else {
                context.getString(R.string.wallpaper_download_completed_multiple, completedCount)
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(text)
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            if (uri != null) {
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    NOTIFICATION_ID,
                    viewIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.setContentIntent(pendingIntent)
            }

            notificationManager.notify(NOTIFICATION_ID, builder.build())
        }
    }

    fun cancelNotification(context: Context) {
        runCatching {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(NOTIFICATION_ID)
        }
    }

    private fun getExtension(url: String, isVideo: Boolean): String {
        val cleanUrl = url.substringBefore("?").substringBefore("#")
        val ext = cleanUrl.substringAfterLast(".", "").lowercase()
        return when (ext) {
            "png", "jpg", "jpeg", "webp", "gif" -> ext
            "mp4", "webm", "mkv" -> ext
            else -> if (isVideo) "mp4" else "png"
        }
    }

    private fun getMimeType(item: WallpaperItem): String {
        val isVideo = item.isLive || item.type == "video" || item.fullUrl.endsWith(".mp4", ignoreCase = true)
        val ext = getExtension(item.fullUrl, isVideo)
        return when (ext) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            else -> if (isVideo) "video/mp4" else "image/png"
        }
    }

    private fun urlHash(url: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(url.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
