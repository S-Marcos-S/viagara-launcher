// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.data

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

enum class EdgeSide { LEFT, RIGHT, BOTH }
enum class AppFont { SYSTEM, SANS_SERIF, SERIF, MONOSPACE }

enum class ClockStyle {
    CLASSIC,
    STACKED,
    MINIMAL,
    ANALOG,
    DIGITAL_CARD,
    DAY_FOCUS,
    TECH_HUD,
    TECH_HUD_PRO,
    SYSTEM_MONITOR,
    MINIMAL_SPECS,
    RETRO_TERMINAL,
    DAILY_REFLECTION,
    DAILY_REFLECTION_STATS,
    NOTHING_DOTS,
    CENTERED_PILL,
    DUAL_TONE_STACK,
    CALLIGRAPHY_LARGE,
    CALLIGRAPHY_STACKED,
    CALLIGRAPHY_MINIMAL,
    HANDWRITTEN_CANVAS,
    HANDWRITTEN_CANVAS_STATS,
    CANVAS_SHIMMER,
}

/** AUTO picks light or dark text from the wallpaper's own colours. */
enum class TextColorMode { AUTO, LIGHT, DARK }

enum class ThemedIconStyle { MATERIAL_YOU, MINIMALIST, OLED_DARK }

private val Context.dataStore by preferencesDataStore(name = "viagara_prefs")

class Prefs(private val context: Context) {

    private object Keys {
        val HIDDEN_APPS = stringSetPreferencesKey("hidden_apps")
        val FAVORITES = stringPreferencesKey("favorites_order")
        val FOLDERS = stringPreferencesKey("folders_json")
        val NAME_OVERRIDES = stringPreferencesKey("name_overrides_json")
        val ICON_OVERRIDES = stringPreferencesKey("icon_overrides_json")
        val ICON_SIZE_DP = intPreferencesKey("icon_size_dp")
        val LABEL_SIZE_SP = intPreferencesKey("label_size_sp")
        val ITEM_SPACING_DP = intPreferencesKey("item_spacing_dp")
        val SIDE_PADDING_DP = intPreferencesKey("side_padding_dp")
        val ALPHABET_SIDE_PADDING_DP = intPreferencesKey("alphabet_side_padding_dp")
        val NOW_PLAYING_HEIGHT_DP = intPreferencesKey("now_playing_height_dp")
        val NOW_PLAYING_PAD_TOP = intPreferencesKey("now_playing_pad_top")
        val NOW_PLAYING_PAD_BOTTOM = intPreferencesKey("now_playing_pad_bottom")
        val WIDGET_PAD_TOP = intPreferencesKey("widget_pad_top")
        val WIDGET_PAD_BOTTOM = intPreferencesKey("widget_pad_bottom")
        val FAVORITES_PAD_TOP = intPreferencesKey("favorites_pad_top")
        val FAVORITES_PAD_BOTTOM = intPreferencesKey("favorites_pad_bottom")
        val FONT = stringPreferencesKey("font")
        val HIDE_STATUS_BAR = booleanPreferencesKey("hide_status_bar")
        val DIM_WALLPAPER_ALPHA = floatPreferencesKey("dim_wallpaper_alpha")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val DIM_HOME_ALPHA = floatPreferencesKey("dim_home_alpha")
        val SHOW_FAVORITE_LABELS = booleanPreferencesKey("show_favorite_labels")
        val TEXT_COLOR_MODE = stringPreferencesKey("text_color_mode")
        val DOUBLE_TAP_TO_LOCK = booleanPreferencesKey("double_tap_to_lock")
        val EDGE_SIDE = stringPreferencesKey("edge_side")
        val ALWAYS_SHOW_AZ = booleanPreferencesKey("always_show_az")
        val SHOW_ALPHABET = booleanPreferencesKey("show_alphabet")
        val ALIGN_RIGHT = booleanPreferencesKey("align_right")
        val ICON_PACK_PACKAGE = stringPreferencesKey("icon_pack_package")
        val THEMED_ICONS = booleanPreferencesKey("themed_icons")
        val THEMED_ICON_STYLE = stringPreferencesKey("themed_icon_style")
        val NOW_PLAYING_ENABLED = booleanPreferencesKey("now_playing_enabled")
        val AUDIO_VISUALIZER_ENABLED = booleanPreferencesKey("audio_visualizer_enabled")
        val AUDIO_VISUALIZER_SPEED = floatPreferencesKey("audio_visualizer_speed")
        val NOW_PLAYING_PROMPT_DISMISSED = booleanPreferencesKey("now_playing_prompt_dismissed")
        val MUSIC_PLAYBACK_DETECTED = booleanPreferencesKey("music_playback_detected")
        val SHOW_APP_NOTIFICATIONS = booleanPreferencesKey("show_app_notifications")
        val FOLDER_WINDOW_POPUP = booleanPreferencesKey("folder_window_popup")
        val ACTIVE_THEME = stringPreferencesKey("active_theme_id")
        val CLOCK_STYLE = stringPreferencesKey("clock_style")
        val CLOCK_WIDTH_SCALE = floatPreferencesKey("clock_width_scale")
        val CLOCK_HEIGHT_SCALE = floatPreferencesKey("clock_height_scale")
        val CLOCK_TOP_PADDING_DP = intPreferencesKey("clock_top_padding_dp")
        val WIDGET_ID = intPreferencesKey("widget_id")
        val WIDGET_IDS = stringPreferencesKey("widget_ids_csv")
        val WIDGET_POSITION = intPreferencesKey("widget_position")
        val WIDGET_HEIGHT_DP = intPreferencesKey("widget_height_dp")
        val DYNAMIC_BUTTON_ENABLED = booleanPreferencesKey("dynamic_button_enabled")
        val DYNAMIC_BUTTON_CLICK_APP = stringPreferencesKey("dynamic_button_click_app")
        val DYNAMIC_BUTTON_SWIPE_UP_APP = stringPreferencesKey("dynamic_button_swipe_up_app")
        val DYNAMIC_BUTTON_SWIPE_DOWN_APP = stringPreferencesKey("dynamic_button_swipe_down_app")
        val SEARCH_BUTTON_ENABLED = booleanPreferencesKey("search_button_enabled")
        val SEARCH_INCLUDE_APPS = booleanPreferencesKey("search_include_apps")
        val SEARCH_INCLUDE_CONTACTS = booleanPreferencesKey("search_include_contacts")
        val SEARCH_INCLUDE_SETTINGS = booleanPreferencesKey("search_include_settings")
        val SEARCH_INCLUDE_WEB = booleanPreferencesKey("search_include_web")
        val SEARCH_INCLUDE_PLAY_STORE = booleanPreferencesKey("search_include_play_store")
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val SEARCH_AUTO_KEYBOARD = booleanPreferencesKey("search_auto_keyboard")
        val HAS_PROMPTED_DEFAULT_LAUNCHER = booleanPreferencesKey("has_prompted_default_launcher")
        val BACKUP_AUTO_ENABLED = booleanPreferencesKey("backup_auto_enabled")
        val BACKUP_AUTO_FREQUENCY = stringPreferencesKey("backup_auto_frequency")
        val BACKUP_MAX_KEEP = intPreferencesKey("backup_max_keep")
        val BACKUP_INCLUDE_WALLPAPER = booleanPreferencesKey("backup_include_wallpaper")
        val BACKUP_LAST_AUTO_TIMESTAMP = longPreferencesKey("backup_last_auto_timestamp")
        val BACKUP_FOLDER_URI = stringPreferencesKey("backup_folder_uri")
        val BACKUP_FOLDER_NAME = stringPreferencesKey("backup_folder_name")
    }

    private val data get() = context.dataStore.data

    /** Favorites are stored as one newline-joined string; these are the only two readers. */
    private fun readFavorites(pref: Preferences): List<String> =
        pref[Keys.FAVORITES]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

    private fun MutablePreferences.writeFavorites(list: List<String>) {
        this[Keys.FAVORITES] = list.joinToString("\n")
    }

    val hiddenApps: Flow<Set<String>> =
        data.map { it[Keys.HIDDEN_APPS] ?: emptySet() }.distinctUntilChanged()

    val favorites: Flow<List<String>> =
        data.map { pref -> readFavorites(pref) }.distinctUntilChanged()

    val folders: Flow<List<Folder>> =
        data.map { pref -> foldersFromJson(pref[Keys.FOLDERS]) }.distinctUntilChanged()

    val nameOverrides: Flow<Map<String, String>> =
        data.map { pref -> jsonToMap(pref[Keys.NAME_OVERRIDES]) }.distinctUntilChanged()

    val iconOverrides: Flow<Map<String, String>> =
        data.map { pref -> jsonToMap(pref[Keys.ICON_OVERRIDES]) }.distinctUntilChanged()

    val iconSizeDp: Flow<Int> = data.map { it[Keys.ICON_SIZE_DP] ?: 56 }.distinctUntilChanged()

    val labelSizeSp: Flow<Int> = data.map { it[Keys.LABEL_SIZE_SP] ?: 16 }.distinctUntilChanged()

    /** Vertical gap between favorite rows. */
    val itemSpacingDp: Flow<Int> = data.map { it[Keys.ITEM_SPACING_DP] ?: 10 }.distinctUntilChanged()

    /** Left/right inset applied to every element on the home screen, so they stay in line. */
    val sidePaddingDp: Flow<Int> = data.map { it[Keys.SIDE_PADDING_DP] ?: 20 }.distinctUntilChanged()

    /** Left/right inset applied specifically to the alphabet scrubber. */
    val alphabetSidePaddingDp: Flow<Int> = data.map { it[Keys.ALPHABET_SIDE_PADDING_DP] ?: 20 }.distinctUntilChanged()

    val nowPlayingHeightDp: Flow<Int> = data.map { it[Keys.NOW_PLAYING_HEIGHT_DP] ?: 64 }.distinctUntilChanged()

    /** Draggable top/bottom padding for each home block, set in edit mode. */
    val homePaddings: Flow<HomePaddings> = data.map {
        HomePaddings(
            nowPlayingTop = it[Keys.NOW_PLAYING_PAD_TOP] ?: 8,
            nowPlayingBottom = it[Keys.NOW_PLAYING_PAD_BOTTOM] ?: 8,
            widgetTop = it[Keys.WIDGET_PAD_TOP] ?: 8,
            widgetBottom = it[Keys.WIDGET_PAD_BOTTOM] ?: 8,
            favoritesTop = it[Keys.FAVORITES_PAD_TOP] ?: 8,
            favoritesBottom = it[Keys.FAVORITES_PAD_BOTTOM] ?: 24,
        )
    }.distinctUntilChanged()

    val font: Flow<AppFont> = data.map {
        runCatching { AppFont.valueOf(it[Keys.FONT] ?: AppFont.SYSTEM.name) }.getOrDefault(AppFont.SYSTEM)
    }.distinctUntilChanged()

    val hideStatusBar: Flow<Boolean> = data.map { it[Keys.HIDE_STATUS_BAR] ?: false }.distinctUntilChanged()

    val dimWallpaperAlpha: Flow<Float> = data.map { it[Keys.DIM_WALLPAPER_ALPHA] ?: 0.35f }.distinctUntilChanged()

    val hapticsEnabled: Flow<Boolean> = data.map { it[Keys.HAPTICS_ENABLED] ?: true }.distinctUntilChanged()

    val dimHomeAlpha: Flow<Float> = data.map { it[Keys.DIM_HOME_ALPHA] ?: 0f }.distinctUntilChanged()

    val showFavoriteLabels: Flow<Boolean> =
        data.map { it[Keys.SHOW_FAVORITE_LABELS] ?: true }.distinctUntilChanged()

    val textColorMode: Flow<TextColorMode> = data.map {
        runCatching { TextColorMode.valueOf(it[Keys.TEXT_COLOR_MODE] ?: TextColorMode.AUTO.name) }
            .getOrDefault(TextColorMode.AUTO)
    }.distinctUntilChanged()

    val doubleTapToLock: Flow<Boolean> =
        data.map { it[Keys.DOUBLE_TAP_TO_LOCK] ?: false }.distinctUntilChanged()

    val edgeSide: Flow<EdgeSide> = data.map {
        runCatching { EdgeSide.valueOf(it[Keys.EDGE_SIDE] ?: EdgeSide.RIGHT.name) }.getOrDefault(EdgeSide.RIGHT)
    }.distinctUntilChanged()

    /** Keep the A-Z strip on screen even when the app list is closed. */
    val alwaysShowAz: Flow<Boolean> = data.map { it[Keys.ALWAYS_SHOW_AZ] ?: true }.distinctUntilChanged()

    /** The A-Z strip inside the app list; the edge gesture still works without it. */
    val showAlphabet: Flow<Boolean> = data.map { it[Keys.SHOW_ALPHABET] ?: true }.distinctUntilChanged()

    /** Lay icons and labels out from the right edge instead of the left. */
    val alignRight: Flow<Boolean> = data.map { it[Keys.ALIGN_RIGHT] ?: false }.distinctUntilChanged()

    val iconPackPackage: Flow<String?> = data.map { it[Keys.ICON_PACK_PACKAGE] }.distinctUntilChanged()
    val themedIcons: Flow<Boolean> = data.map { it[Keys.THEMED_ICONS] ?: false }.distinctUntilChanged()
    val themedIconStyle: Flow<ThemedIconStyle> = data.map {
        runCatching { ThemedIconStyle.valueOf(it[Keys.THEMED_ICON_STYLE] ?: ThemedIconStyle.MATERIAL_YOU.name) }
            .getOrDefault(ThemedIconStyle.MATERIAL_YOU)
    }.distinctUntilChanged()

    val nowPlayingEnabled: Flow<Boolean> = data.map { it[Keys.NOW_PLAYING_ENABLED] ?: true }.distinctUntilChanged()
    val audioVisualizerEnabled: Flow<Boolean> = data.map { it[Keys.AUDIO_VISUALIZER_ENABLED] ?: false }.distinctUntilChanged()
    val audioVisualizerSpeed: Flow<Float> = data.map { it[Keys.AUDIO_VISUALIZER_SPEED] ?: 0.50f }.distinctUntilChanged()
    val nowPlayingPromptDismissed: Flow<Boolean> = data.map { it[Keys.NOW_PLAYING_PROMPT_DISMISSED] ?: false }.distinctUntilChanged()
    val musicPlaybackDetected: Flow<Boolean> = data.map { it[Keys.MUSIC_PLAYBACK_DETECTED] ?: false }.distinctUntilChanged()
    val showAppNotifications: Flow<Boolean> = data.map { it[Keys.SHOW_APP_NOTIFICATIONS] ?: true }.distinctUntilChanged()
    val folderWindowPopup: Flow<Boolean> = data.map { it[Keys.FOLDER_WINDOW_POPUP] ?: true }.distinctUntilChanged()
    val activeThemeId: Flow<String> = data.map { it[Keys.ACTIVE_THEME] ?: "system_dynamic" }.distinctUntilChanged()
    val clockStyle: Flow<ClockStyle> = data.map {
        runCatching { ClockStyle.valueOf(it[Keys.CLOCK_STYLE] ?: ClockStyle.CLASSIC.name) }
            .getOrDefault(ClockStyle.CLASSIC)
    }.distinctUntilChanged()
    val clockWidthScale: Flow<Float> = data.map { it[Keys.CLOCK_WIDTH_SCALE] ?: 1.0f }.distinctUntilChanged()
    val clockHeightScale: Flow<Float> = data.map { it[Keys.CLOCK_HEIGHT_SCALE] ?: 1.0f }.distinctUntilChanged()
    val clockTopPaddingDp: Flow<Int> = data.map { it[Keys.CLOCK_TOP_PADDING_DP] ?: 158 }.distinctUntilChanged()

    val widgetId: Flow<Int> = data.map { it[Keys.WIDGET_ID] ?: -1 }.distinctUntilChanged()
    val widgetIds: Flow<List<Int>> = data.map { pref ->
        val csv = pref[Keys.WIDGET_IDS]
        if (csv != null) {
            csv.split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it > 0 }
        } else {
            val legacy = pref[Keys.WIDGET_ID] ?: -1
            if (legacy > 0) listOf(legacy) else emptyList()
        }
    }.distinctUntilChanged()
    /** Index into the merged (favorites + widget) home list where the widget sits. 0 = top. */
    val widgetPosition: Flow<Int> = data.map { it[Keys.WIDGET_POSITION] ?: 0 }.distinctUntilChanged()
    val widgetHeightDp: Flow<Int> = data.map { it[Keys.WIDGET_HEIGHT_DP] ?: 180 }.distinctUntilChanged()

    val dynamicButtonEnabled: Flow<Boolean> = data.map { it[Keys.DYNAMIC_BUTTON_ENABLED] ?: true }.distinctUntilChanged()
    val dynamicButtonClickApp: Flow<String?> = data.map { it[Keys.DYNAMIC_BUTTON_CLICK_APP] }.distinctUntilChanged()
    val dynamicButtonSwipeUpApp: Flow<String?> = data.map { it[Keys.DYNAMIC_BUTTON_SWIPE_UP_APP] }.distinctUntilChanged()
    val dynamicButtonSwipeDownApp: Flow<String?> = data.map { it[Keys.DYNAMIC_BUTTON_SWIPE_DOWN_APP] }.distinctUntilChanged()
    val searchButtonEnabled: Flow<Boolean> = data.map { it[Keys.SEARCH_BUTTON_ENABLED] ?: true }.distinctUntilChanged()
    val searchIncludeApps: Flow<Boolean> = data.map { it[Keys.SEARCH_INCLUDE_APPS] ?: true }.distinctUntilChanged()
    val searchIncludeContacts: Flow<Boolean> = data.map { it[Keys.SEARCH_INCLUDE_CONTACTS] ?: true }.distinctUntilChanged()
    val searchIncludeSettings: Flow<Boolean> = data.map { it[Keys.SEARCH_INCLUDE_SETTINGS] ?: true }.distinctUntilChanged()
    val searchIncludeWeb: Flow<Boolean> = data.map { it[Keys.SEARCH_INCLUDE_WEB] ?: true }.distinctUntilChanged()
    val searchIncludePlayStore: Flow<Boolean> = data.map { it[Keys.SEARCH_INCLUDE_PLAY_STORE] ?: true }.distinctUntilChanged()
    val searchEngine: Flow<String> = data.map { it[Keys.SEARCH_ENGINE] ?: "GOOGLE" }.distinctUntilChanged()
    val searchAutoKeyboard: Flow<Boolean> = data.map { it[Keys.SEARCH_AUTO_KEYBOARD] ?: true }.distinctUntilChanged()
    val hasPromptedDefaultLauncher: Flow<Boolean> = data.map { it[Keys.HAS_PROMPTED_DEFAULT_LAUNCHER] ?: false }.distinctUntilChanged()
    val autoBackupEnabled: Flow<Boolean> = data.map { it[Keys.BACKUP_AUTO_ENABLED] ?: false }.distinctUntilChanged()
    val autoBackupFrequency: Flow<dev.viagaralauncher.backup.BackupFrequency> = data.map {
        dev.viagaralauncher.backup.BackupFrequency.fromName(it[Keys.BACKUP_AUTO_FREQUENCY])
    }.distinctUntilChanged()
    val backupMaxKeep: Flow<Int> = data.map { it[Keys.BACKUP_MAX_KEEP] ?: 5 }.distinctUntilChanged()
    val backupIncludeWallpaper: Flow<Boolean> = data.map { it[Keys.BACKUP_INCLUDE_WALLPAPER] ?: true }.distinctUntilChanged()
    val lastAutoBackupTimestamp: Flow<Long> = data.map { it[Keys.BACKUP_LAST_AUTO_TIMESTAMP] ?: 0L }.distinctUntilChanged()
    val backupFolderUri: Flow<String?> = data.map { it[Keys.BACKUP_FOLDER_URI] }.distinctUntilChanged()
    val backupFolderName: Flow<String?> = data.map { it[Keys.BACKUP_FOLDER_NAME] }.distinctUntilChanged()

    suspend fun setHidden(componentKey: String, hidden: Boolean) {
        context.dataStore.edit { pref ->
            val current = pref[Keys.HIDDEN_APPS] ?: emptySet()
            pref[Keys.HIDDEN_APPS] = if (hidden) current + componentKey else current - componentKey
        }
    }

    suspend fun setFavorites(list: List<String>) {
        context.dataStore.edit { it.writeFavorites(list) }
    }

    suspend fun addFavorite(componentKey: String) {
        context.dataStore.edit { pref ->
            val current = readFavorites(pref)
            if (componentKey !in current) pref.writeFavorites(current + componentKey)
        }
    }

    suspend fun removeFavorite(componentKey: String) {
        context.dataStore.edit { pref ->
            pref.writeFavorites(readFavorites(pref) - componentKey)
        }
    }

    /** Creates the folder if [id] is new, otherwise replaces it. */
    suspend fun upsertFolder(folder: Folder) {
        context.dataStore.edit { pref ->
            val existing = foldersFromJson(pref[Keys.FOLDERS])
            val updated = if (existing.any { it.id == folder.id }) {
                existing.map { if (it.id == folder.id) folder else it }
            } else {
                existing + folder
            }
            pref[Keys.FOLDERS] = foldersToJson(updated)
        }
    }

    /** Removes the folder and its row; the apps themselves are untouched. */
    suspend fun deleteFolder(id: String) {
        context.dataStore.edit { pref ->
            pref[Keys.FOLDERS] = foldersToJson(foldersFromJson(pref[Keys.FOLDERS]).filterNot { it.id == id })
            pref.writeFavorites(readFavorites(pref).filterNot { it == folderToken(id) })
        }
    }

    suspend fun setFolderIcon(id: String, icon: String?) {
        context.dataStore.edit { pref ->
            val updated = foldersFromJson(pref[Keys.FOLDERS])
                .map { if (it.id == id) it.copy(icon = icon) else it }
            pref[Keys.FOLDERS] = foldersToJson(updated)
        }
    }

    suspend fun setFolderApps(id: String, apps: List<String>) {
        context.dataStore.edit { pref ->
            val updated = foldersFromJson(pref[Keys.FOLDERS])
                .map { if (it.id == id) it.copy(apps = apps) else it }
            pref[Keys.FOLDERS] = foldersToJson(updated)
        }
    }

    /**
     * Puts [componentKey] in the folder. An app that was a top-level favorite moves into it
     * rather than being duplicated in both places.
     */
    suspend fun addAppToFolder(folderId: String, componentKey: String) {
        context.dataStore.edit { pref ->
            val updated = foldersFromJson(pref[Keys.FOLDERS]).map { folder ->
                if (folder.id == folderId && componentKey !in folder.apps) {
                    folder.copy(apps = folder.apps + componentKey)
                } else {
                    folder
                }
            }
            pref[Keys.FOLDERS] = foldersToJson(updated)
            pref.writeFavorites(readFavorites(pref).filterNot { it == componentKey })
        }
    }

    suspend fun removeAppFromFolder(folderId: String, componentKey: String) {
        context.dataStore.edit { pref ->
            val updated = foldersFromJson(pref[Keys.FOLDERS]).map { folder ->
                if (folder.id == folderId) folder.copy(apps = folder.apps - componentKey) else folder
            }
            pref[Keys.FOLDERS] = foldersToJson(updated)
        }
    }

    suspend fun setNameOverride(componentKey: String, name: String?) {
        context.dataStore.edit { pref ->
            val map = jsonToMap(pref[Keys.NAME_OVERRIDES]).toMutableMap()
            if (name.isNullOrBlank()) map.remove(componentKey) else map[componentKey] = name
            pref[Keys.NAME_OVERRIDES] = mapToJson(map)
        }
    }

    suspend fun setIconOverride(componentKey: String, uri: String?) {
        context.dataStore.edit { pref ->
            val map = jsonToMap(pref[Keys.ICON_OVERRIDES]).toMutableMap()
            if (uri.isNullOrBlank()) map.remove(componentKey) else map[componentKey] = uri
            pref[Keys.ICON_OVERRIDES] = mapToJson(map)
        }
    }

    suspend fun setIconSizeDp(v: Int) {
        context.dataStore.edit { it[Keys.ICON_SIZE_DP] = v }
    }

    suspend fun setLabelSizeSp(v: Int) {
        context.dataStore.edit { it[Keys.LABEL_SIZE_SP] = v }
    }

    suspend fun setSidePaddingDp(v: Int) {
        context.dataStore.edit { it[Keys.SIDE_PADDING_DP] = v }
    }

    suspend fun setAlphabetSidePaddingDp(v: Int) {
        context.dataStore.edit { it[Keys.ALPHABET_SIDE_PADDING_DP] = v }
    }

    suspend fun setItemSpacingDp(v: Int) {
        context.dataStore.edit { it[Keys.ITEM_SPACING_DP] = v }
    }

    suspend fun setNowPlayingHeightDp(v: Int) {
        context.dataStore.edit { it[Keys.NOW_PLAYING_HEIGHT_DP] = v }
    }

    suspend fun setHomePadding(slot: PaddingSlot, v: Int) {
        val key = when (slot) {
            PaddingSlot.NOW_PLAYING_TOP -> Keys.NOW_PLAYING_PAD_TOP
            PaddingSlot.NOW_PLAYING_BOTTOM -> Keys.NOW_PLAYING_PAD_BOTTOM
            PaddingSlot.WIDGET_TOP -> Keys.WIDGET_PAD_TOP
            PaddingSlot.WIDGET_BOTTOM -> Keys.WIDGET_PAD_BOTTOM
            PaddingSlot.FAVORITES_TOP -> Keys.FAVORITES_PAD_TOP
            PaddingSlot.FAVORITES_BOTTOM -> Keys.FAVORITES_PAD_BOTTOM
        }
        context.dataStore.edit { it[key] = v }
    }

    suspend fun resetHomePaddingsAndSidePadding() {
        context.dataStore.edit {
            it[Keys.SIDE_PADDING_DP] = 20
            it[Keys.ALPHABET_SIDE_PADDING_DP] = 20
            it[Keys.NOW_PLAYING_PAD_TOP] = HomePaddings.Default.nowPlayingTop
            it[Keys.NOW_PLAYING_PAD_BOTTOM] = HomePaddings.Default.nowPlayingBottom
            it[Keys.WIDGET_PAD_TOP] = HomePaddings.Default.widgetTop
            it[Keys.WIDGET_PAD_BOTTOM] = HomePaddings.Default.widgetBottom
            it[Keys.FAVORITES_PAD_TOP] = HomePaddings.Default.favoritesTop
            it[Keys.FAVORITES_PAD_BOTTOM] = HomePaddings.Default.favoritesBottom
        }
    }

    suspend fun setFont(f: AppFont) {
        context.dataStore.edit { it[Keys.FONT] = f.name }
    }

    suspend fun setHideStatusBar(v: Boolean) {
        context.dataStore.edit { it[Keys.HIDE_STATUS_BAR] = v }
    }

    suspend fun setDimWallpaperAlpha(v: Float) {
        context.dataStore.edit { it[Keys.DIM_WALLPAPER_ALPHA] = v }
    }

    suspend fun setDimHomeAlpha(v: Float) {
        context.dataStore.edit { it[Keys.DIM_HOME_ALPHA] = v }
    }

    suspend fun setShowFavoriteLabels(v: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_FAVORITE_LABELS] = v }
    }

    suspend fun setTextColorMode(v: TextColorMode) {
        context.dataStore.edit { it[Keys.TEXT_COLOR_MODE] = v.name }
    }

    suspend fun setDoubleTapToLock(v: Boolean) {
        context.dataStore.edit { it[Keys.DOUBLE_TAP_TO_LOCK] = v }
    }

    suspend fun setHapticsEnabled(v: Boolean) {
        context.dataStore.edit { it[Keys.HAPTICS_ENABLED] = v }
    }

    suspend fun setShowAlphabet(v: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_ALPHABET] = v }
    }

    suspend fun setAlignRight(v: Boolean) {
        context.dataStore.edit { it[Keys.ALIGN_RIGHT] = v }
    }

    suspend fun setAlwaysShowAz(v: Boolean) {
        context.dataStore.edit { it[Keys.ALWAYS_SHOW_AZ] = v }
    }

    suspend fun setEdgeSide(v: EdgeSide) {
        context.dataStore.edit { it[Keys.EDGE_SIDE] = v.name }
    }

    suspend fun setIconPackPackage(pkg: String?) {
        context.dataStore.edit { pref ->
            if (pkg.isNullOrBlank()) pref.remove(Keys.ICON_PACK_PACKAGE) else pref[Keys.ICON_PACK_PACKAGE] = pkg
        }
    }

    suspend fun setThemedIcons(v: Boolean) {
        context.dataStore.edit { it[Keys.THEMED_ICONS] = v }
    }

    suspend fun setThemedIconStyle(v: ThemedIconStyle) {
        context.dataStore.edit { it[Keys.THEMED_ICON_STYLE] = v.name }
    }

    suspend fun setActiveThemeId(id: String) {
        context.dataStore.edit { it[Keys.ACTIVE_THEME] = id }
    }

    suspend fun setWidgetId(id: Int) {
        context.dataStore.edit { pref ->
            if (id > 0) {
                pref[Keys.WIDGET_ID] = id
                pref[Keys.WIDGET_IDS] = id.toString()
            } else {
                pref.remove(Keys.WIDGET_ID)
                pref.remove(Keys.WIDGET_IDS)
            }
        }
    }

    suspend fun setWidgetIds(ids: List<Int>) {
        context.dataStore.edit { pref ->
            val valid = ids.filter { it > 0 }
            if (valid.isEmpty()) {
                pref.remove(Keys.WIDGET_IDS)
                pref.remove(Keys.WIDGET_ID)
            } else {
                pref[Keys.WIDGET_IDS] = valid.joinToString(",")
                pref[Keys.WIDGET_ID] = valid.first()
            }
        }
    }

    suspend fun addWidgetId(id: Int) {
        if (id <= 0) return
        context.dataStore.edit { pref ->
            val current = pref[Keys.WIDGET_IDS]?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.filter { it > 0 }
                ?: (pref[Keys.WIDGET_ID]?.takeIf { it > 0 }?.let { listOf(it) } ?: emptyList())
            val updated = current + id
            pref[Keys.WIDGET_IDS] = updated.joinToString(",")
            pref[Keys.WIDGET_ID] = updated.first()
        }
    }

    suspend fun removeWidgetId(id: Int) {
        if (id <= 0) return
        context.dataStore.edit { pref ->
            val current = pref[Keys.WIDGET_IDS]?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.filter { it > 0 }
                ?: (pref[Keys.WIDGET_ID]?.takeIf { it > 0 }?.let { listOf(it) } ?: emptyList())
            val indexToRemove = current.indexOf(id)
            val updated = if (indexToRemove >= 0) {
                current.toMutableList().apply { removeAt(indexToRemove) }
            } else {
                current.filter { it != id }
            }
            if (updated.isEmpty()) {
                pref.remove(Keys.WIDGET_IDS)
                pref.remove(Keys.WIDGET_ID)
            } else {
                pref[Keys.WIDGET_IDS] = updated.joinToString(",")
                pref[Keys.WIDGET_ID] = updated.first()
            }
        }
    }

    suspend fun pruneInvalidWidgetIds(isValid: (Int) -> Boolean) {
        context.dataStore.edit { pref ->
            val current = pref[Keys.WIDGET_IDS]?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.filter { it > 0 }
                ?: (pref[Keys.WIDGET_ID]?.takeIf { it > 0 }?.let { listOf(it) } ?: emptyList())
            if (current.isEmpty()) {
                if (pref.contains(Keys.WIDGET_ID) || pref.contains(Keys.WIDGET_IDS)) {
                    pref.remove(Keys.WIDGET_IDS)
                    pref.remove(Keys.WIDGET_ID)
                }
                return@edit
            }
            val valid = current.filter { isValid(it) }
            if (valid != current) {
                if (valid.isEmpty()) {
                    pref.remove(Keys.WIDGET_IDS)
                    pref.remove(Keys.WIDGET_ID)
                } else {
                    pref[Keys.WIDGET_IDS] = valid.joinToString(",")
                    pref[Keys.WIDGET_ID] = valid.first()
                }
            }
        }
    }

    suspend fun setNowPlayingEnabled(v: Boolean) {
        context.dataStore.edit {
            it[Keys.NOW_PLAYING_ENABLED] = v
            if (v) {
                it[Keys.NOW_PLAYING_PROMPT_DISMISSED] = false
            }
        }
    }

    suspend fun setAudioVisualizerEnabled(v: Boolean) {
        context.dataStore.edit {
            it[Keys.AUDIO_VISUALIZER_ENABLED] = v
        }
    }

    suspend fun setAudioVisualizerSpeed(v: Float) {
        context.dataStore.edit {
            it[Keys.AUDIO_VISUALIZER_SPEED] = v.coerceIn(0.20f, 1.00f)
        }
    }

    suspend fun setNowPlayingPromptDismissed(v: Boolean) {
        context.dataStore.edit { it[Keys.NOW_PLAYING_PROMPT_DISMISSED] = v }
    }

    suspend fun setMusicPlaybackDetected(v: Boolean) {
        context.dataStore.edit { it[Keys.MUSIC_PLAYBACK_DETECTED] = v }
    }

    suspend fun setShowAppNotifications(v: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_APP_NOTIFICATIONS] = v }
    }

    suspend fun setFolderWindowPopup(v: Boolean) {
        context.dataStore.edit { it[Keys.FOLDER_WINDOW_POPUP] = v }
    }

    suspend fun setClockStyle(v: ClockStyle) {
        context.dataStore.edit { it[Keys.CLOCK_STYLE] = v.name }
    }

    suspend fun setClockWidthScale(v: Float) {
        context.dataStore.edit { it[Keys.CLOCK_WIDTH_SCALE] = v.coerceIn(0.5f, 2.2f) }
    }

    suspend fun setClockHeightScale(v: Float) {
        context.dataStore.edit { it[Keys.CLOCK_HEIGHT_SCALE] = v.coerceIn(0.5f, 2.2f) }
    }

    suspend fun setClockTopPaddingDp(v: Int) {
        context.dataStore.edit { it[Keys.CLOCK_TOP_PADDING_DP] = v.coerceIn(30, 240) }
    }

    suspend fun resetClockSize() {
        context.dataStore.edit {
            it[Keys.CLOCK_WIDTH_SCALE] = 1.0f
            it[Keys.CLOCK_HEIGHT_SCALE] = 1.0f
            it[Keys.CLOCK_TOP_PADDING_DP] = 158
        }
    }

    suspend fun setWidgetPosition(v: Int) {
        context.dataStore.edit { it[Keys.WIDGET_POSITION] = v }
    }

    suspend fun setWidgetHeightDp(v: Int) {
        context.dataStore.edit { it[Keys.WIDGET_HEIGHT_DP] = v }
    }

    suspend fun setDynamicButtonEnabled(v: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_BUTTON_ENABLED] = v }
    }

    suspend fun setDynamicButtonClickApp(key: String?) {
        context.dataStore.edit { pref ->
            if (key.isNullOrBlank()) pref.remove(Keys.DYNAMIC_BUTTON_CLICK_APP) else pref[Keys.DYNAMIC_BUTTON_CLICK_APP] = key
        }
    }

    suspend fun setDynamicButtonSwipeUpApp(key: String?) {
        context.dataStore.edit { pref ->
            if (key.isNullOrBlank()) pref.remove(Keys.DYNAMIC_BUTTON_SWIPE_UP_APP) else pref[Keys.DYNAMIC_BUTTON_SWIPE_UP_APP] = key
        }
    }

    suspend fun setDynamicButtonSwipeDownApp(key: String?) {
        context.dataStore.edit { pref ->
            if (key.isNullOrBlank()) pref.remove(Keys.DYNAMIC_BUTTON_SWIPE_DOWN_APP) else pref[Keys.DYNAMIC_BUTTON_SWIPE_DOWN_APP] = key
        }
    }

    suspend fun setSearchButtonEnabled(v: Boolean) {
        context.dataStore.edit { it[Keys.SEARCH_BUTTON_ENABLED] = v }
    }

    suspend fun setSearchIncludeApps(v: Boolean) {
        context.dataStore.edit { it[Keys.SEARCH_INCLUDE_APPS] = v }
    }

    suspend fun setSearchIncludeContacts(v: Boolean) {
        context.dataStore.edit { it[Keys.SEARCH_INCLUDE_CONTACTS] = v }
    }

    suspend fun setSearchIncludeSettings(v: Boolean) {
        context.dataStore.edit { it[Keys.SEARCH_INCLUDE_SETTINGS] = v }
    }

    suspend fun setSearchIncludeWeb(v: Boolean) {
        context.dataStore.edit { it[Keys.SEARCH_INCLUDE_WEB] = v }
    }

    suspend fun setSearchIncludePlayStore(v: Boolean) {
        context.dataStore.edit { it[Keys.SEARCH_INCLUDE_PLAY_STORE] = v }
    }

    suspend fun setSearchEngine(v: String) {
        context.dataStore.edit { it[Keys.SEARCH_ENGINE] = v }
    }

    suspend fun setSearchAutoKeyboard(v: Boolean) {
        context.dataStore.edit { it[Keys.SEARCH_AUTO_KEYBOARD] = v }
    }

    suspend fun setHasPromptedDefaultLauncher(v: Boolean) {
        context.dataStore.edit { it[Keys.HAS_PROMPTED_DEFAULT_LAUNCHER] = v }
    }

    suspend fun setAutoBackupEnabled(v: Boolean) {
        context.dataStore.edit { it[Keys.BACKUP_AUTO_ENABLED] = v }
    }

    suspend fun setAutoBackupFrequency(v: dev.viagaralauncher.backup.BackupFrequency) {
        context.dataStore.edit { it[Keys.BACKUP_AUTO_FREQUENCY] = v.name }
    }

    suspend fun setBackupMaxKeep(v: Int) {
        context.dataStore.edit { it[Keys.BACKUP_MAX_KEEP] = v }
    }

    suspend fun setBackupIncludeWallpaper(v: Boolean) {
        context.dataStore.edit { it[Keys.BACKUP_INCLUDE_WALLPAPER] = v }
    }

    suspend fun setLastAutoBackupTimestamp(v: Long) {
        context.dataStore.edit { it[Keys.BACKUP_LAST_AUTO_TIMESTAMP] = v }
    }

    suspend fun setBackupFolder(uri: String?, name: String?) {
        context.dataStore.edit { pref ->
            if (uri != null) {
                pref[Keys.BACKUP_FOLDER_URI] = uri
            } else {
                pref.remove(Keys.BACKUP_FOLDER_URI)
            }
            if (name != null) {
                pref[Keys.BACKUP_FOLDER_NAME] = name
            } else {
                pref.remove(Keys.BACKUP_FOLDER_NAME)
            }
        }
    }

    private val EXCLUDED_BACKUP_KEYS = setOf(
        Keys.WIDGET_ID.name,
        Keys.WIDGET_IDS.name,
        Keys.BACKUP_LAST_AUTO_TIMESTAMP.name,
        Keys.BACKUP_FOLDER_URI.name,
        Keys.BACKUP_FOLDER_NAME.name,
    )

    suspend fun exportAllPreferencesJson(): String {
        val prefsMap = context.dataStore.data.first().asMap()
        val root = JSONObject()
        val arr = JSONArray()

        for ((key, value) in prefsMap) {
            if (key.name in EXCLUDED_BACKUP_KEYS) continue
            val item = JSONObject()
            item.put("key", key.name)
            when (value) {
                is String -> {
                    item.put("type", "STRING")
                    item.put("value", value)
                }
                is Int -> {
                    item.put("type", "INT")
                    item.put("value", value)
                }
                is Boolean -> {
                    item.put("type", "BOOLEAN")
                    item.put("value", value)
                }
                is Float -> {
                    item.put("type", "FLOAT")
                    item.put("value", value.toDouble())
                }
                is Long -> {
                    item.put("type", "LONG")
                    item.put("value", value)
                }
                is Double -> {
                    item.put("type", "DOUBLE")
                    item.put("value", value)
                }
                is Set<*> -> {
                    item.put("type", "STRING_SET")
                    val setArr = JSONArray()
                    for (s in value) {
                        if (s is String) setArr.put(s)
                    }
                    item.put("value", setArr)
                }
            }
            arr.put(item)
        }
        root.put("preferences", arr)
        return root.toString()
    }

    suspend fun importPreferencesJson(jsonStr: String): Int {
        val root = JSONObject(jsonStr)
        val arr = root.optJSONArray("preferences") ?: return 0
        var count = 0
        val appWidgetManager = runCatching { AppWidgetManager.getInstance(context) }.getOrNull()

        context.dataStore.edit { prefs ->
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val keyName = item.getString("key")
                val type = item.getString("type")

                // Skip device-specific backup folder settings
                if (keyName == Keys.BACKUP_FOLDER_URI.name ||
                    keyName == Keys.BACKUP_FOLDER_NAME.name ||
                    keyName == Keys.BACKUP_LAST_AUTO_TIMESTAMP.name
                ) {
                    continue
                }

                // Never import raw widget IDs from backup if they don't exist on this device
                if (keyName == Keys.WIDGET_ID.name) {
                    val candidateId = item.getInt("value")
                    val isValid = candidateId > 0 && runCatching {
                        appWidgetManager?.getAppWidgetInfo(candidateId) != null
                    }.getOrDefault(false)
                    if (isValid) {
                        prefs[Keys.WIDGET_ID] = candidateId
                        count++
                    } else {
                        prefs.remove(Keys.WIDGET_ID)
                    }
                    continue
                }

                if (keyName == Keys.WIDGET_IDS.name) {
                    val candidateCsv = item.getString("value")
                    val validIds = candidateCsv.split(",")
                        .mapNotNull { it.trim().toIntOrNull() }
                        .filter { id ->
                            id > 0 && runCatching {
                                appWidgetManager?.getAppWidgetInfo(id) != null
                            }.getOrDefault(false)
                        }
                    if (validIds.isNotEmpty()) {
                        prefs[Keys.WIDGET_IDS] = validIds.joinToString(",")
                        prefs[Keys.WIDGET_ID] = validIds.first()
                        count++
                    } else {
                        prefs.remove(Keys.WIDGET_IDS)
                        prefs.remove(Keys.WIDGET_ID)
                    }
                    continue
                }

                when (type) {
                    "STRING" -> {
                        prefs[stringPreferencesKey(keyName)] = item.getString("value")
                        count++
                    }
                    "INT" -> {
                        prefs[intPreferencesKey(keyName)] = item.getInt("value")
                        count++
                    }
                    "BOOLEAN" -> {
                        prefs[booleanPreferencesKey(keyName)] = item.getBoolean("value")
                        count++
                    }
                    "FLOAT" -> {
                        prefs[floatPreferencesKey(keyName)] = item.getDouble("value").toFloat()
                        count++
                    }
                    "LONG" -> {
                        prefs[longPreferencesKey(keyName)] = item.getLong("value")
                        count++
                    }
                    "DOUBLE" -> {
                        prefs[doublePreferencesKey(keyName)] = item.getDouble("value")
                        count++
                    }
                    "STRING_SET" -> {
                        val setArr = item.getJSONArray("value")
                        val set = mutableSetOf<String>()
                        for (j in 0 until setArr.length()) {
                            set.add(setArr.getString(j))
                        }
                        prefs[stringSetPreferencesKey(keyName)] = set
                        count++
                    }
                }
            }

            // Post-import sanitation: ensure no orphaned or dead widget IDs remain
            val remainingIds = prefs[Keys.WIDGET_IDS]?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.filter { id ->
                    id > 0 && runCatching {
                        appWidgetManager?.getAppWidgetInfo(id) != null
                    }.getOrDefault(false)
                } ?: emptyList()

            if (remainingIds.isEmpty()) {
                prefs.remove(Keys.WIDGET_IDS)
                prefs.remove(Keys.WIDGET_ID)
            } else {
                prefs[Keys.WIDGET_IDS] = remainingIds.joinToString(",")
                prefs[Keys.WIDGET_ID] = remainingIds.first()
            }
        }
        return count
    }

    private fun jsonToMap(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, String>()
            obj.keys().forEach { k -> map[k] = obj.getString(k) }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun mapToJson(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }
}