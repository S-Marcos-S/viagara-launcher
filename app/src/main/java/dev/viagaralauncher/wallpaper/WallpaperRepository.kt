// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class WallpaperCategory(
    val id: String,
    val label: String,
)

data class WallpaperCatalog(
    val categories: List<WallpaperCategory>,
    val wallpapers: List<WallpaperItem>,
)

data class WallpaperItem(
    val id: String,
    val name: String,
    val author: String,
    val thumbnailUrl: String,
    val fullUrl: String,
    val primaryColorHex: String = "#1E1E1E",
    val category: String = "oled",
    val tags: List<String> = emptyList(),
)

object WallpaperRepository {

    private const val REMOTE_CATALOG_URL =
        "https://raw.githubusercontent.com/S-Marcos-S/viagara-launcher/main/wallpapers/catalog.json"

    private val _wallpaperUpdateTick = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val wallpaperUpdateTick: kotlinx.coroutines.flow.StateFlow<Long> = _wallpaperUpdateTick

    fun notifyWallpaperChanged() {
        _wallpaperUpdateTick.value = System.currentTimeMillis()
    }

    val DEFAULT_CATEGORIES = listOf(
        WallpaperCategory("all", "Todos"),
        WallpaperCategory("oled", "OLED"),
        WallpaperCategory("abstract", "Abstrato"),
        WallpaperCategory("space", "Espaço"),
        WallpaperCategory("minimal", "Minimalista"),
        WallpaperCategory("nature", "Natureza"),
    )

    private fun cacheDir(context: Context): File {
        val dir = File(context.cacheDir, "wallpaper_cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun urlHash(url: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(url.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun loadCatalog(context: Context): WallpaperCatalog = withContext(Dispatchers.IO) {
        val catalogFile = File(cacheDir(context), "catalog.json")

        // Try downloading remote catalog
        val remoteJson = runCatching {
            val conn = (URL(REMOTE_CATALOG_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
            }
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        }.getOrNull()

        if (!remoteJson.isNullOrBlank()) {
            runCatching { catalogFile.writeText(remoteJson) }
        }

        val jsonText = when {
            !remoteJson.isNullOrBlank() -> remoteJson
            catalogFile.exists() -> runCatching { catalogFile.readText() }.getOrNull()
            else -> runCatching {
                context.assets.open("wallpapers/catalog.json").bufferedReader().use { it.readText() }
            }.getOrNull()
        } ?: return@withContext WallpaperCatalog(DEFAULT_CATEGORIES, emptyList())

        parseCatalogJson(jsonText)
    }

    private fun parseCatalogJson(jsonText: String): WallpaperCatalog {
        return runCatching {
            val root = JSONObject(jsonText)

            val categories = mutableListOf<WallpaperCategory>()
            val catArray = root.optJSONArray("categories")
            if (catArray != null && catArray.length() > 0) {
                for (i in 0 until catArray.length()) {
                    val obj = catArray.getJSONObject(i)
                    categories.add(
                        WallpaperCategory(
                            id = obj.getString("id"),
                            label = obj.optString("label", obj.getString("id")),
                        )
                    )
                }
            }
            if (categories.isEmpty() || categories.none { it.id.equals("all", ignoreCase = true) }) {
                categories.add(0, WallpaperCategory("all", "Todos"))
            }

            val array = root.optJSONArray("wallpapers") ?: return WallpaperCatalog(categories, emptyList())
            val list = mutableListOf<WallpaperItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val tagsArray = obj.optJSONArray("tags")
                val tags = mutableListOf<String>()
                if (tagsArray != null) {
                    for (t in 0 until tagsArray.length()) {
                        tags.add(tagsArray.getString(t))
                    }
                }
                list.add(
                    WallpaperItem(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        author = obj.optString("author", "Viagara Launcher"),
                        thumbnailUrl = obj.getString("thumbnail_url"),
                        fullUrl = obj.getString("full_url"),
                        primaryColorHex = obj.optString("primary_color", "#1E1E1E"),
                        category = obj.optString("category", if (tags.isNotEmpty()) tags[0] else "oled"),
                        tags = tags,
                    )
                )
            }
            WallpaperCatalog(categories, list)
        }.getOrDefault(WallpaperCatalog(DEFAULT_CATEGORIES, emptyList()))
    }

    suspend fun getCachedOrDownloadBitmap(context: Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
        val hash = urlHash(url)
        val file = File(cacheDir(context), hash)

        if (file.exists() && file.length() > 0) {
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
            if (bmp != null) return@withContext bmp
        }

        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                requestMethod = "GET"
            }
            if (conn.responseCode == 200) {
                val tempFile = File(cacheDir(context), "${hash}.tmp")
                conn.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.exists() && tempFile.length() > 0) {
                    tempFile.renameTo(file)
                    BitmapFactory.decodeFile(file.absolutePath)
                } else null
            } else null
        }.getOrNull()
    }

    suspend fun applyWallpaper(context: Context, fullUrl: String, flags: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = getCachedOrDownloadBitmap(context, fullUrl)
                ?: throw IllegalStateException("Could not download wallpaper bitmap")

            val wm = WallpaperManager.getInstance(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wm.setBitmap(bitmap, null, true, flags)
            } else {
                wm.setBitmap(bitmap)
            }
            notifyWallpaperChanged()
            Unit
        }
    }
}
