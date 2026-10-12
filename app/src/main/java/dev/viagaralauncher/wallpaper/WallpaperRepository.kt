// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.wallpaper

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
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
    val authorUrl: String? = null,
    val thumbnailUrl: String,
    val fullUrl: String,
    val primaryColorHex: String = "#1E1E1E",
    val category: String = "images",
    val type: String = "image",
    val isLive: Boolean = false,
    val tags: List<String> = emptyList(),
    val searchTerms: List<String> = emptyList(),
)

object WallpaperRepository {

    private const val REMOTE_CATALOG_URL =
        "https://raw.githubusercontent.com/S-Marcos-S/viagara-wallpapers/main/wallpapers/catalog.json"

    private val _wallpaperUpdateTick = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val wallpaperUpdateTick: kotlinx.coroutines.flow.StateFlow<Long> = _wallpaperUpdateTick

    private val _currentPrimaryColor = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)
    val currentPrimaryColor: kotlinx.coroutines.flow.StateFlow<Int?> = _currentPrimaryColor

    fun notifyWallpaperChanged() {
        _wallpaperUpdateTick.value = System.currentTimeMillis()
    }

    val DEFAULT_CATEGORIES = listOf(
        WallpaperCategory("all", "Todos"),
        WallpaperCategory("oled", "OLED"),
        WallpaperCategory("anime", "Anime"),
        WallpaperCategory("games", "Jogos"),
        WallpaperCategory("minimal", "Minimalista"),
        WallpaperCategory("abstract", "Abstrato"),
        WallpaperCategory("nature", "Natureza"),
        WallpaperCategory("space", "Espaço"),
        WallpaperCategory("cyberpunk", "Cyberpunk"),
        WallpaperCategory("city", "Cidades"),
        WallpaperCategory("cars", "Carros"),
        WallpaperCategory("animals", "Animais"),
        WallpaperCategory("fantasy", "Fantasia"),
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
            val urlWithBuster = "$REMOTE_CATALOG_URL?t=${System.currentTimeMillis()}"
            val conn = (URL(urlWithBuster).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Pragma", "no-cache")
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

        val catalog = parseCatalogJson(jsonText)
        WallpaperNotificationManager.checkAndNotifyNewWallpapers(context, catalog)
        catalog
    }

    private var lastWallpaperCheckTimeMs = 0L
    private const val WALLPAPER_CHECK_INTERVAL_MS = 2 * 60 * 60 * 1000L // 2 hours

    fun checkForNewWallpapers(context: Context, coroutineScope: kotlinx.coroutines.CoroutineScope, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastWallpaperCheckTimeMs < WALLPAPER_CHECK_INTERVAL_MS) {
            return
        }
        lastWallpaperCheckTimeMs = now
        coroutineScope.launch(Dispatchers.IO) {
            loadCatalog(context)
        }
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

                val searchTermsArray = obj.optJSONArray("search_terms")
                val searchTerms = mutableListOf<String>()
                if (searchTermsArray != null) {
                    for (t in 0 until searchTermsArray.length()) {
                        searchTerms.add(searchTermsArray.getString(t))
                    }
                }

                val itemType = obj.optString("type", if (obj.optBoolean("is_live", false)) "video" else "image")
                val isLive = obj.optBoolean("is_live", itemType == "video")

                list.add(
                    WallpaperItem(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        author = obj.optString("author", "Wallpaper HD/4K (Amanda)"),
                        authorUrl = obj.optString("author_url", "https://t.me/amandinhawallpaper").takeIf { it.isNotBlank() },
                        thumbnailUrl = obj.getString("thumbnail_url"),
                        fullUrl = obj.getString("full_url"),
                        primaryColorHex = obj.optString("primary_color", "#1E1E1E"),
                        category = obj.optString("category", if (isLive) "live" else "images"),
                        type = itemType,
                        isLive = isLive,
                        tags = tags,
                        searchTerms = searchTerms,
                    )
                )
            }
            WallpaperCatalog(categories, list)
        }.getOrDefault(WallpaperCatalog(DEFAULT_CATEGORIES, emptyList()))
    }

    suspend fun getCachedOrDownloadFile(context: Context, url: String): File? = withContext(Dispatchers.IO) {
        val hash = urlHash(url)
        val file = File(cacheDir(context), hash)

        if (file.exists() && file.length() > 0) {
            return@withContext file
        }

        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 25000
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
                    file
                } else null
            } else null
        }.getOrNull()
    }

    suspend fun getCachedOrDownloadBitmap(context: Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = getCachedOrDownloadFile(context, url) ?: return@withContext null
        runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    suspend fun applyWallpaper(
        context: Context,
        item: WallpaperItem,
        flags: Int,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val bitmap = getCachedOrDownloadBitmap(context, item.fullUrl)
            ?: return@withContext Result.failure(IllegalStateException("Could not download wallpaper bitmap"))
        applyWallpaper(context, item, bitmap, flags)
    }

    @SuppressLint("MissingPermission")
    suspend fun applyWallpaper(
        context: Context,
        item: WallpaperItem,
        bitmap: Bitmap,
        flags: Int,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val wm = WallpaperManager.getInstance(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wm.setBitmap(bitmap, null, true, flags)
            } else {
                wm.setBitmap(bitmap)
            }

            // Immediately extract primary color from the applied wallpaper
            val extractedColor: Int? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                runCatching {
                    android.app.WallpaperColors.fromBitmap(bitmap)?.primaryColor?.toArgb()
                }.getOrNull()
            } else null ?: runCatching {
                android.graphics.Color.parseColor(item.primaryColorHex)
            }.getOrNull()

            if (extractedColor != null) {
                _currentPrimaryColor.value = extractedColor
            }

            notifyWallpaperChanged()
            Unit
        }
    }

    @SuppressLint("MissingPermission")
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                runCatching {
                    android.app.WallpaperColors.fromBitmap(bitmap)?.primaryColor?.toArgb()?.let {
                        _currentPrimaryColor.value = it
                    }
                }
            }

            notifyWallpaperChanged()
            Unit
        }
    }

    suspend fun updateLocalCatalogItem(
        context: Context,
        itemId: String,
        newTitle: String,
        newTags: List<String>,
    ) = withContext(Dispatchers.IO) {
        val file = File(cacheDir(context), "wallpaper_catalog.json")
        if (!file.exists()) return@withContext

        runCatching {
            val jsonText = file.readText()
            val root = JSONObject(jsonText)
            val wallpapers = root.optJSONArray("wallpapers") ?: return@withContext

            for (i in 0 until wallpapers.length()) {
                val itemObj = wallpapers.getJSONObject(i)
                if (itemObj.optString("id") == itemId) {
                    if (newTitle.isNotBlank()) {
                        itemObj.put("name", newTitle.trim())
                    }
                    val existingTags = mutableListOf<String>()
                    val tagsArr = itemObj.optJSONArray("tags")
                    if (tagsArr != null) {
                        for (t in 0 until tagsArr.length()) {
                            existingTags.add(tagsArr.getString(t))
                        }
                    }
                    val existingSearch = mutableListOf<String>()
                    val searchArr = itemObj.optJSONArray("search_terms")
                    if (searchArr != null) {
                        for (s in 0 until searchArr.length()) {
                            existingSearch.add(searchArr.getString(s))
                        }
                    }

                    for (tag in newTags) {
                        val cleaned = tag.trim()
                        if (cleaned.isNotBlank()) {
                            if (existingTags.none { it.equals(cleaned, ignoreCase = true) }) {
                                existingTags.add(cleaned)
                            }
                            if (existingSearch.none { it.equals(cleaned, ignoreCase = true) }) {
                                existingSearch.add(cleaned)
                            }
                        }
                    }

                    itemObj.put("tags", JSONArray(existingTags))
                    itemObj.put("search_terms", JSONArray(existingSearch))
                    itemObj.put("classified_by", "manual")
                    itemObj.put("custom_classified", true)
                    break
                }
            }

            file.writeText(root.toString(2))
            notifyWallpaperChanged()
        }
    }

    suspend fun submitClassification(
        context: Context,
        item: WallpaperItem,
        newTitle: String,
        newTags: List<String>,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Atualizar o item localmente no cache do app
            updateLocalCatalogItem(context, item.id, newTitle, newTags)

            // 2. Montar Issue estruturada para moderação no GitHub
            val title = "[Sugestão] ${item.id}: $newTitle"
            val body = buildString {
                appendLine("### 🏷️ Sugestão de Classificação Manual de Wallpaper")
                appendLine()
                appendLine("- **ID:** `${item.id}`")
                appendLine("- **Novo Título:** `$newTitle`")
                appendLine("- **Tags Propostas:** ${newTags.joinToString(", ") { "`$it`" }}")
                appendLine()
                appendLine("![Preview](${item.thumbnailUrl})")
                appendLine()
                appendLine("---")
                appendLine("```json")
                appendLine(JSONObject().apply {
                    put("id", item.id)
                    put("name", newTitle)
                    put("tags", JSONArray(newTags))
                    put("classified_by", "manual")
                    put("custom_classified", true)
                }.toString(2))
                appendLine("```")
                appendLine()
                appendLine("> *Para aprovar esta sugestão e atualizar automaticamente o catálogo oficial do repositório, responda com o comentário `/approve` ou adicione a label `approved`.*")
            }

            // 3. Abrir página de nova Issue pré-preenchida no GitHub para confirmação do usuário
            withContext(Dispatchers.Main) {
                val encodedTitle = URLEncoder.encode(title, "UTF-8")
                val encodedBody = URLEncoder.encode(body, "UTF-8")
                val issueUrl = "https://github.com/S-Marcos-S/viagara-wallpapers/issues/new?title=$encodedTitle&body=$encodedBody&labels=wallpaper-suggestion"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(issueUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
            true
        }
    }
}
