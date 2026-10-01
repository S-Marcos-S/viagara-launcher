// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.search

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import dev.viagaralauncher.R
import dev.viagaralauncher.data.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.Normalizer
import java.util.Locale

object SearchRepository {

    fun removeAccents(str: String): String {
        return Normalizer.normalize(str, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase()
            .trim()
    }

    /**
     * Search installed applications with accent insensitivity and smart prefix/contains scoring.
     */
    fun searchApps(
        query: String,
        apps: List<AppInfo>,
        nameOverrides: Map<String, String>,
    ): List<SearchResult.AppItem> {
        val normQuery = removeAccents(query)
        if (normQuery.isBlank()) return emptyList()

        return apps.mapNotNull { app ->
            val label = nameOverrides[app.key] ?: app.label
            val normLabel = removeAccents(label)
            val normPkg = app.packageName.lowercase()

            val score = when {
                normLabel == normQuery -> 1000
                normLabel.startsWith(normQuery) -> 500
                normLabel.contains(" $normQuery") -> 300
                normLabel.contains(normQuery) -> 100
                normPkg.contains(normQuery) -> 20
                else -> 0
            }

            if (score > 0) Pair(SearchResult.AppItem(app, label), score) else null
        }
            .sortedWith(compareByDescending<Pair<SearchResult.AppItem, Int>> { it.second }.thenBy { it.first.label })
            .map { it.first }
    }

    /**
     * Search device contacts safely and strictly offline using Android's native ContactsProvider.
     * Checks READ_CONTACTS permission beforehand and uses indexed query filtering.
     */
    suspend fun searchContacts(
        context: Context,
        query: String,
    ): List<SearchResult.ContactItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext emptyList()
        }

        val results = mutableListOf<SearchResult.ContactItem>()
        val seenKeys = mutableSetOf<String>()

        try {
            val filterUri = Uri.withAppendedPath(
                ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
                Uri.encode(trimmed),
            )

            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
            )

            context.contentResolver.query(
                filterUri,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val lookupCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
                val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numberCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

                while (cursor.moveToNext() && results.size < 25) {
                    val id = if (idCol >= 0) cursor.getString(idCol) else ""
                    val lookupKey = if (lookupCol >= 0) cursor.getString(lookupCol) else null
                    val name = if (nameCol >= 0) cursor.getString(nameCol) else ""
                    val number = if (numberCol >= 0) cursor.getString(numberCol) else null
                    val photoUri = if (photoCol >= 0) cursor.getString(photoCol) else null

                    val dedupeKey = "$name|$number"
                    if (name.isNotBlank() && dedupeKey !in seenKeys) {
                        seenKeys.add(dedupeKey)
                        results.add(
                            SearchResult.ContactItem(
                                id = id,
                                lookupKey = lookupKey,
                                name = name,
                                phoneNumber = number,
                                photoUri = photoUri,
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Log silently or ignore to prevent any launcher crash
        }

        results
    }

    private data class RawSetting(
        val id: String,
        val titlePt: String,
        val titleEn: String,
        val keywords: List<String>,
        val action: String,
        val icon: ImageVector,
    )

    private val SYSTEM_SETTINGS = listOf(
        RawSetting(
            id = "wifi",
            titlePt = "Wi-Fi e Conexões",
            titleEn = "Wi-Fi & Connections",
            keywords = listOf("wifi", "wi-fi", "internet", "rede", "conexao", "network", "wlan", "roteador"),
            action = Settings.ACTION_WIFI_SETTINGS,
            icon = Icons.Filled.Wifi,
        ),
        RawSetting(
            id = "bluetooth",
            titlePt = "Bluetooth e Aparelhos",
            titleEn = "Bluetooth & Devices",
            keywords = listOf("bluetooth", "dispositivos", "fone", "devices", "pair", "conectar"),
            action = Settings.ACTION_BLUETOOTH_SETTINGS,
            icon = Icons.Filled.Bluetooth,
        ),
        RawSetting(
            id = "battery",
            titlePt = "Bateria e Energia",
            titleEn = "Battery & Power",
            keywords = listOf("bateria", "energia", "carga", "battery", "power", "economizador", "saver"),
            action = Settings.ACTION_BATTERY_SAVER_SETTINGS,
            icon = Icons.Filled.BatteryChargingFull,
        ),
        RawSetting(
            id = "display",
            titlePt = "Tela e Brilho",
            titleEn = "Display & Brightness",
            keywords = listOf("tela", "brilho", "display", "screen", "modo escuro", "dark mode", "fonte", "cor"),
            action = Settings.ACTION_DISPLAY_SETTINGS,
            icon = Icons.Filled.BrightnessMedium,
        ),
        RawSetting(
            id = "sound",
            titlePt = "Som e Vibração",
            titleEn = "Sound & Vibration",
            keywords = listOf("som", "audio", "volume", "toque", "sound", "vibration", "silencioso", "ringtone"),
            action = Settings.ACTION_SOUND_SETTINGS,
            icon = Icons.Filled.VolumeUp,
        ),
        RawSetting(
            id = "storage",
            titlePt = "Armazenamento",
            titleEn = "Storage",
            keywords = listOf("armazenamento", "memoria", "espaco", "storage", "disco", "limpar"),
            action = Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
            icon = Icons.Filled.Storage,
        ),
        RawSetting(
            id = "apps",
            titlePt = "Aplicativos Instalados",
            titleEn = "Installed Apps",
            keywords = listOf("aplicativos", "apps", "gerenciador", "permissoes", "applications"),
            action = Settings.ACTION_APPLICATION_SETTINGS,
            icon = Icons.Filled.Apps,
        ),
        RawSetting(
            id = "notifications",
            titlePt = "Notificações do Sistema",
            titleEn = "System Notifications",
            keywords = listOf("notificacoes", "alertas", "avisos", "notifications", "bloqueio"),
            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS,
            icon = Icons.Filled.Notifications,
        ),
        RawSetting(
            id = "security",
            titlePt = "Segurança e Privacidade",
            titleEn = "Security & Privacy",
            keywords = listOf("seguranca", "privacidade", "bloqueio", "security", "privacy", "biometria", "digital"),
            action = Settings.ACTION_SECURITY_SETTINGS,
            icon = Icons.Filled.Security,
        ),
        RawSetting(
            id = "location",
            titlePt = "Localização (GPS)",
            titleEn = "Location (GPS)",
            keywords = listOf("localizacao", "gps", "mapa", "location", "posicionamento"),
            action = Settings.ACTION_LOCATION_SOURCE_SETTINGS,
            icon = Icons.Filled.LocationOn,
        ),
        RawSetting(
            id = "accessibility",
            titlePt = "Acessibilidade",
            titleEn = "Accessibility",
            keywords = listOf("acessibilidade", "leitor", "gestos", "accessibility", "visual"),
            action = Settings.ACTION_ACCESSIBILITY_SETTINGS,
            icon = Icons.Filled.Accessibility,
        ),
        RawSetting(
            id = "date_time",
            titlePt = "Data e Hora",
            titleEn = "Date & Time",
            keywords = listOf("data", "hora", "relogio", "fuso", "date", "time", "clock"),
            action = Settings.ACTION_DATE_SETTINGS,
            icon = Icons.Filled.AccessTime,
        ),
        RawSetting(
            id = "airplane",
            titlePt = "Modo Avião",
            titleEn = "Airplane Mode",
            keywords = listOf("aviao", "modo aviao", "airplane", "flight mode"),
            action = Settings.ACTION_AIRPLANE_MODE_SETTINGS,
            icon = Icons.Filled.AirplanemodeActive,
        ),
        RawSetting(
            id = "hotspot",
            titlePt = "Roteador Wi-Fi (Ponto de Acesso)",
            titleEn = "Hotspot & Tethering",
            keywords = listOf("roteador", "ponto de acesso", "hotspot", "tethering", "ancoragem"),
            action = Settings.ACTION_WIRELESS_SETTINGS,
            icon = Icons.Filled.WifiTethering,
        ),
        RawSetting(
            id = "language",
            titlePt = "Idioma e Teclado",
            titleEn = "Language & Keyboard",
            keywords = listOf("idioma", "linguagem", "teclado", "language", "keyboard", "input"),
            action = Settings.ACTION_LOCALE_SETTINGS,
            icon = Icons.Filled.Language,
        ),
        RawSetting(
            id = "developer",
            titlePt = "Opções do Desenvolvedor",
            titleEn = "Developer Options",
            keywords = listOf("desenvolvedor", "developer", "depuracao", "usb", "debug"),
            action = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
            icon = Icons.Filled.DeveloperMode,
        ),
        RawSetting(
            id = "about",
            titlePt = "Sobre o Dispositivo",
            titleEn = "About Device",
            keywords = listOf("sobre", "dispositivo", "sistema", "versao", "about", "phone", "device"),
            action = Settings.ACTION_DEVICE_INFO_SETTINGS,
            icon = Icons.Filled.Info,
        ),
        RawSetting(
            id = "wallpaper",
            titlePt = "Papel de Parede",
            titleEn = "Wallpaper",
            keywords = listOf("papel de parede", "fundo", "wallpaper", "plano de fundo"),
            action = Intent.ACTION_SET_WALLPAPER,
            icon = Icons.Filled.Wallpaper,
        ),
        RawSetting(
            id = "vpn",
            titlePt = "VPN",
            titleEn = "VPN",
            keywords = listOf("vpn", "rede privada", "proxy"),
            action = Settings.ACTION_VPN_SETTINGS,
            icon = Icons.Filled.VpnKey,
        ),
        RawSetting(
            id = "accounts",
            titlePt = "Contas e Sincronização",
            titleEn = "Accounts & Sync",
            keywords = listOf("contas", "google", "sincronizacao", "accounts", "sync", "usuario"),
            action = Settings.ACTION_SYNC_SETTINGS,
            icon = Icons.Filled.AccountCircle,
        ),
    )

    /**
     * Search Android device settings shortcuts.
     */
    fun searchSettings(context: Context, query: String): List<SearchResult.SettingItem> {
        val normQuery = removeAccents(query)
        if (normQuery.isBlank()) return emptyList()

        val isPortuguese = Locale.getDefault().language.equals("pt", ignoreCase = true)
        val subtitle = if (isPortuguese) "Configuração do celular" else "Device setting"

        return SYSTEM_SETTINGS.mapNotNull { raw ->
            val title = if (isPortuguese) raw.titlePt else raw.titleEn
            val normTitle = removeAccents(title)

            var score = 0
            if (normTitle == normQuery) {
                score = 300
            } else if (normTitle.startsWith(normQuery)) {
                score = 200
            } else if (normTitle.contains(normQuery)) {
                score = 100
            } else {
                for (kw in raw.keywords) {
                    val normKw = removeAccents(kw)
                    if (normKw.startsWith(normQuery)) {
                        score = 80
                        break
                    } else if (normKw.contains(normQuery)) {
                        score = 50
                        break
                    }
                }
            }

            if (score > 0) {
                Pair(
                    SearchResult.SettingItem(
                        id = raw.id,
                        title = title,
                        subtitle = subtitle,
                        intentAction = raw.action,
                        icon = raw.icon,
                    ),
                    score,
                )
            } else {
                null
            }
        }.sortedByDescending { it.second }.map { it.first }
    }

    /**
     * Safe arithmetic calculator evaluator for mathematical queries like "15 * 8", "120 + 45", "100 / 4".
     */
    fun evaluateCalculation(query: String): SearchResult.CalculationItem? {
        val clean = query.trim().replace(",", ".")
        if (!clean.matches("^[0-9\\s\\+\\-\\*/xX%\\.\\(\\)]+$".toRegex())) return null
        // Must contain at least one arithmetic operator and at least one digit
        if (!clean.any { it in "+-*/xX%" }) return null
        if (!clean.any { it.isDigit() }) return null

        return try {
            val sanitized = clean.replace("x", "*").replace("X", "*")
            val result = SimpleMathParser.eval(sanitized)
            if (result.isNaN() || result.isInfinite()) return null

            val df = DecimalFormat("#,##0.######", DecimalFormatSymbols(Locale.getDefault()))
            val formatted = df.format(result)
            SearchResult.CalculationItem(expression = query.trim(), result = formatted)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Builds the web search action with the selected search engine.
     */
    fun getWebAction(query: String, engine: SearchEngine): SearchResult.WebItem {
        return SearchResult.WebItem(query = query.trim(), engine = engine)
    }

    /**
     * Builds the Google Play Store search action.
     */
    fun getPlayStoreAction(query: String): SearchResult.PlayStoreItem {
        return SearchResult.PlayStoreItem(query = query.trim())
    }

    /**
     * Recursive descent arithmetic evaluator without external dependencies.
     */
    private object SimpleMathParser {
        fun eval(str: String): Double {
            return Parser(str).parse()
        }

        private class Parser(private val str: String) {
            private var pos = -1
            private var ch = -1

            private fun nextChar() {
                ch = if (++pos < str.length) str[pos].code else -1
            }

            private fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parse(): Double {
                nextChar()
                val result = parseExpression()
                return if (pos < str.length) Double.NaN else result
            }

            private fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    when {
                        eat('+'.code) -> x += parseTerm()
                        eat('-'.code) -> x -= parseTerm()
                        else -> return x
                    }
                }
            }

            private fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    when {
                        eat('*'.code) -> x *= parseFactor()
                        eat('/'.code) -> {
                            val divisor = parseFactor()
                            if (divisor == 0.0) return Double.NaN
                            x /= divisor
                        }
                        eat('%'.code) -> {
                            val divisor = parseFactor()
                            if (divisor == 0.0) return Double.NaN
                            x %= divisor
                        }
                        else -> return x
                    }
                }
            }

            private fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor()
                if (eat('-'.code)) return -parseFactor()

                var x: Double
                val startPos = pos
                if (eat('('.code)) {
                    x = parseExpression()
                    eat(')'.code)
                } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                    while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                    x = str.substring(startPos, pos).toDouble()
                } else {
                    return Double.NaN
                }

                return x
            }
        }
    }
}
