// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.clock

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object ClockRepository {

    private const val GITHUB_RAW_BASE =
        "https://raw.githubusercontent.com/S-Marcos-S/viagara-launcher/main/clocks"
    private const val REMOTE_CATALOG_URL =
        "$GITHUB_RAW_BASE/catalog.json"

    private fun clocksDir(context: Context): File {
        val dir = File(context.filesDir, "clocks")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun stylesDir(context: Context): File {
        val dir = File(clocksDir(context), "styles")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun fontsDir(context: Context): File {
        val dir = File(clocksDir(context), "fonts")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun md5(str: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(str.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun loadCatalog(context: Context): ClockCatalog = withContext(Dispatchers.IO) {
        val catalogFile = File(clocksDir(context), "catalog.json")

        // Try downloading remote catalog with cache-busting
        val remoteJson = runCatching {
            val urlWithBuster = "$REMOTE_CATALOG_URL?t=${System.currentTimeMillis()}"
            val conn = (URL(urlWithBuster).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Pragma", "no-cache")
                setRequestProperty("User-Agent", "ViagaraLauncher")
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
                context.assets.open("clocks/catalog.json").bufferedReader().use { it.readText() }
            }.getOrNull()
        } ?: return@withContext ClockCatalog(1, emptyList())

        val catalog = parseCatalogJson(jsonText)
        ClockNotificationManager.checkAndNotifyNewClocks(context, catalog)
        catalog
    }

    private var lastClockCheckTimeMs = 0L
    private const val CLOCK_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6 hours throttle (zero battery impact)

    fun checkForNewClocks(context: Context, coroutineScope: kotlinx.coroutines.CoroutineScope, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastClockCheckTimeMs < CLOCK_CHECK_INTERVAL_MS) {
            return
        }
        lastClockCheckTimeMs = now
        coroutineScope.launch(Dispatchers.IO) {
            loadCatalog(context)
        }
    }

    private fun parseCatalogJson(jsonText: String): ClockCatalog {
        return runCatching {
            val root = JSONObject(jsonText)
            val version = root.optInt("version", 1)
            val array = root.optJSONArray("clocks") ?: return ClockCatalog(version, emptyList())
            val list = mutableListOf<RemoteClockItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val tagsList = mutableListOf<String>()
                val tagsArr = obj.optJSONArray("tags")
                if (tagsArr != null) {
                    for (t in 0 until tagsArr.length()) {
                        tagsList.add(tagsArr.getString(t))
                    }
                }
                list.add(
                    RemoteClockItem(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        author = obj.optString("author", "Comunidade"),
                        description = obj.optString("description", ""),
                        styleJsonUrl = obj.optString("styleJsonUrl", ""),
                        previewType = obj.optString("previewType", "dynamic"),
                        previewImageUrl = obj.optString("previewImageUrl").takeIf { !it.isNullOrBlank() },
                        layoutType = obj.optString("layoutType", "column"),
                        tags = tagsList,
                    )
                )
            }
            ClockCatalog(version, list)
        }.getOrDefault(ClockCatalog(1, emptyList()))
    }

    suspend fun getStyleDefinition(context: Context, clockId: String): RemoteClockStyleDefinition? = withContext(Dispatchers.IO) {
        val styleFile = File(stylesDir(context), "$clockId.json")

        // 1. Check local cached file
        if (styleFile.exists()) {
            val text = runCatching { styleFile.readText() }.getOrNull()
            if (!text.isNullOrBlank()) {
                val parsed = parseStyleJson(text)
                if (parsed != null) return@withContext parsed
            }
        }

        // 2. Try fetching from assets
        val assetJson = runCatching {
            context.assets.open("clocks/styles/$clockId.json").bufferedReader().use { it.readText() }
        }.getOrNull()
        if (!assetJson.isNullOrBlank()) {
            val parsed = parseStyleJson(assetJson)
            if (parsed != null) {
                runCatching { styleFile.writeText(assetJson) }
                return@withContext parsed
            }
        }

        // 3. Try fetching from remote GitHub
        val remoteUrl = "$GITHUB_RAW_BASE/styles/$clockId.json?t=${System.currentTimeMillis()}"
        val remoteJson = runCatching {
            val conn = (URL(remoteUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "ViagaraLauncher")
            }
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        }.getOrNull()

        if (!remoteJson.isNullOrBlank()) {
            runCatching { styleFile.writeText(remoteJson) }
            parseStyleJson(remoteJson)
        } else {
            null
        }
    }

    private fun parseStyleJson(jsonText: String): RemoteClockStyleDefinition? {
        return runCatching {
            val root = JSONObject(jsonText)
            val timeObj = root.optJSONObject("time") ?: JSONObject()
            val dateObj = root.optJSONObject("date")
            val secObj = root.optJSONObject("secondary")
            val analogObj = root.optJSONObject("analog")

            RemoteClockStyleDefinition(
                id = root.getString("id"),
                name = root.getString("name"),
                author = root.optString("author", "Comunidade"),
                layout = root.optString("layout", "column"),
                alignment = root.optString("alignment", "start"),
                containerPaddingDp = root.optInt("containerPaddingDp", 0),
                timeElement = parseTextElement(timeObj),
                dateElement = dateObj?.let { parseTextElement(it) },
                secondaryElement = secObj?.let { parseTextElement(it) },
                analogConfig = analogObj?.let { parseAnalogConfig(it) },
            )
        }.getOrNull()
    }

    private fun parseTextElement(obj: JSONObject): ClockTextElement {
        return ClockTextElement(
            format = obj.optString("format", "HH:mm"),
            fontSizeSp = obj.optInt("fontSizeSp", 48),
            fontWeight = obj.optString("fontWeight", "bold"),
            fontUrl = obj.optString("fontUrl").takeIf { !it.isNullOrBlank() },
            fontFileName = obj.optString("fontFileName").takeIf { !it.isNullOrBlank() },
            colorHex = obj.optString("colorHex").takeIf { !it.isNullOrBlank() },
            secondaryColorHex = obj.optString("secondaryColorHex").takeIf { !it.isNullOrBlank() },
            animation = obj.optString("animation", "none"),
            animationDurationMs = obj.optInt("animationDurationMs", 2000),
            animationTrigger = obj.optString("animationTrigger", "continuous"),
            letterSpacingSp = obj.optDouble("letterSpacingSp", 0.0).toFloat(),
            glowRadiusDp = obj.optDouble("glowRadiusDp", 0.0).toFloat(),
            glowColorHex = obj.optString("glowColorHex").takeIf { !it.isNullOrBlank() },
        )
    }

    private fun parseAnalogConfig(obj: JSONObject): AnalogClockConfig {
        return AnalogClockConfig(
            dialShape = obj.optString("dialShape", "circle"),
            showTicks = obj.optBoolean("showTicks", true),
            smoothSecondHand = obj.optBoolean("smoothSecondHand", true),
            dialColorHex = obj.optString("dialColorHex").takeIf { !it.isNullOrBlank() },
            hourHandColorHex = obj.optString("hourHandColorHex").takeIf { !it.isNullOrBlank() },
            minuteHandColorHex = obj.optString("minuteHandColorHex").takeIf { !it.isNullOrBlank() },
            secondHandColorHex = obj.optString("secondHandColorHex").takeIf { !it.isNullOrBlank() },
        )
    }

    suspend fun loadCustomFont(context: Context, element: ClockTextElement): FontFamily? = withContext(Dispatchers.IO) {
        val fileName = element.fontFileName
            ?: element.fontUrl?.substringAfterLast("/")?.substringBefore("?")
            ?: return@withContext null

        val localFontFile = File(fontsDir(context), fileName)
        if (localFontFile.exists() && localFontFile.length() > 0) {
            return@withContext runCatching { FontFamily(Font(localFontFile)) }.getOrNull()
        }

        // If fontUrl provided, attempt download
        val url = element.fontUrl ?: "$GITHUB_RAW_BASE/fonts/$fileName"
        val downloaded = downloadFont(url, localFontFile)
        if (downloaded && localFontFile.exists()) {
            runCatching { FontFamily(Font(localFontFile)) }.getOrNull()
        } else {
            null
        }
    }

    private fun downloadFont(urlStr: String, destFile: File): Boolean {
        return runCatching {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "ViagaraLauncher")
            }
            if (conn.responseCode == 200) {
                conn.inputStream.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }
}
