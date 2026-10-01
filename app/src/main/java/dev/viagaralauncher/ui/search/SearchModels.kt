// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.search

import androidx.compose.ui.graphics.vector.ImageVector
import dev.viagaralauncher.data.AppInfo

enum class SearchCategory {
    ALL,
    APPS,
    CONTACTS,
    SETTINGS,
    WEB,
}

enum class SearchEngine(val idName: String, val displayName: String, val searchUrl: String) {
    GOOGLE("GOOGLE", "Google", "https://www.google.com/search?q="),
    DUCKDUCKGO("DUCKDUCKGO", "DuckDuckGo", "https://duckduckgo.com/?q="),
    BING("BING", "Bing", "https://www.bing.com/search?q="),
    ECOSIA("ECOSIA", "Ecosia", "https://www.ecosia.org/search?q=");

    companion object {
        fun fromId(id: String?): SearchEngine {
            return entries.firstOrNull { it.idName.equals(id, ignoreCase = true) } ?: GOOGLE
        }
    }
}

data class SearchConfig(
    val buttonEnabled: Boolean = true,
    val includeApps: Boolean = true,
    val includeContacts: Boolean = true,
    val includeSettings: Boolean = true,
    val includeWeb: Boolean = true,
    val includePlayStore: Boolean = true,
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val autoKeyboard: Boolean = true,
)

sealed interface SearchResult {
    data class AppItem(
        val app: AppInfo,
        val label: String,
    ) : SearchResult

    data class ContactItem(
        val id: String,
        val lookupKey: String?,
        val name: String,
        val phoneNumber: String?,
        val photoUri: String?,
    ) : SearchResult

    data class SettingItem(
        val id: String,
        val title: String,
        val subtitle: String,
        val intentAction: String,
        val icon: ImageVector,
    ) : SearchResult

    data class CalculationItem(
        val expression: String,
        val result: String,
    ) : SearchResult

    data class WebItem(
        val query: String,
        val engine: SearchEngine,
    ) : SearchResult

    data class PlayStoreItem(
        val query: String,
    ) : SearchResult
}
