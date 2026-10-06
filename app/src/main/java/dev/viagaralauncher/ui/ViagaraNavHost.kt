// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.app.ActivityOptions
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.viagaralauncher.ViagaraApp
import dev.viagaralauncher.ui.search.SearchConfig
import dev.viagaralauncher.ui.search.SearchEngine
import dev.viagaralauncher.ui.search.SearchScreen
import dev.viagaralauncher.ui.settings.SearchSettingsScreen
import dev.viagaralauncher.data.AppFont
import dev.viagaralauncher.data.AppInfo
import dev.viagaralauncher.data.ClockStyle
import dev.viagaralauncher.data.EdgeSide
import dev.viagaralauncher.data.HomePaddings
import dev.viagaralauncher.data.ThemedIconStyle
import dev.viagaralauncher.data.TextColorMode
import dev.viagaralauncher.data.folderIdFromToken
import dev.viagaralauncher.media.isListenerEnabled
import dev.viagaralauncher.service.SystemUi
import dev.viagaralauncher.ui.common.IconPickerScreen
import dev.viagaralauncher.ui.common.clearIconCache
import dev.viagaralauncher.ui.common.encodePackOverride
import dev.viagaralauncher.ui.common.warmIconCache
import dev.viagaralauncher.backup.BackupFrequency
import dev.viagaralauncher.ui.home.FavoriteEntry
import dev.viagaralauncher.ui.home.HomeRoute
import dev.viagaralauncher.ui.home.HomeSettings
import dev.viagaralauncher.ui.settings.BackupSettingsScreen
import dev.viagaralauncher.ui.settings.ClockStylePickerScreen
import dev.viagaralauncher.ui.settings.DynamicButtonSettingsScreen
import dev.viagaralauncher.ui.settings.FolderAppsScreen
import dev.viagaralauncher.ui.settings.HiddenAppsScreen
import dev.viagaralauncher.ui.settings.ManageFavoritesScreen
import dev.viagaralauncher.ui.settings.SettingsScreen
import dev.viagaralauncher.ui.settings.ThemesScreen
import dev.viagaralauncher.ui.theme.rememberContentColor
import dev.viagaralauncher.widget.WidgetPickerActivity
import dev.viagaralauncher.widget.WidgetSlotActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Collects the stored settings once and hosts the navigation graph.
 *
 * Everything the destinations need is read here rather than in each screen, so the DataStore
 * is collected once per key instead of once per consumer.
 */
@Composable
fun ViagaraNavHost(
    app: ViagaraApp,
    homeIntentTick: Int,
    font: AppFont,
    hideStatusBar: Boolean,
    iconPackPackage: String?,
    iconOverrides: Map<String, String>,
    onPeekStatusBar: () -> Unit,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Enumerating every installed app costs a PackageManager round trip per app; doing it in
    // the first composition is what stalled the cold start. Load it off the main thread and
    // let the home screen render against an empty list for the first frame.
    val openBatteryRequested by dev.viagaralauncher.battery.BatteryStatsEvents.openRequested.collectAsState()
    LaunchedEffect(openBatteryRequested) {
        if (openBatteryRequested) {
            dev.viagaralauncher.battery.BatteryStatsEvents.consume()
            if (navController.currentDestination?.route != "battery_stats") {
                navController.navigate("battery_stats") {
                    launchSingleTop = true
                }
            }
        }
    }

    val openNetworkRequested by dev.viagaralauncher.ui.network.NetworkStatsEvents.openRequested.collectAsState()
    LaunchedEffect(openNetworkRequested) {
        if (openNetworkRequested) {
            dev.viagaralauncher.ui.network.NetworkStatsEvents.consume()
            if (navController.currentDestination?.route != "network_monitor") {
                navController.navigate("network_monitor") {
                    launchSingleTop = true
                }
            }
        }
    }

    val openTaskManagerRequested by dev.viagaralauncher.root.TaskManagerEvents.openRequested.collectAsState()
    LaunchedEffect(openTaskManagerRequested) {
        if (openTaskManagerRequested) {
            if (navController.currentDestination?.route != "task_manager") {
                navController.navigate("task_manager") {
                    launchSingleTop = true
                }
            }
        }
    }

    var allApps by remember { mutableStateOf(emptyList<AppInfo>()) }
    suspend fun reloadApps() {
        allApps = withContext(Dispatchers.Default) { app.appRepository.queryAllApps() }
    }
    LaunchedEffect(Unit) { reloadApps() }

    DisposableEffect(Unit) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                scope.launch {
                    // An app that ships a new icon in an update changes none of the cache
                    // key's components, so nothing else would invalidate the stale bitmap.
                    clearIconCache()
                    reloadApps()
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    val hiddenApps by app.prefs.hiddenApps.collectAsState(initial = emptySet())
    val favoriteKeys by app.prefs.favorites.collectAsState(initial = emptyList())
    val nameOverrides by app.prefs.nameOverrides.collectAsState(initial = emptyMap())
    val iconSizeDp by app.prefs.iconSizeDp.collectAsState(initial = 56)
    val labelSizeSp by app.prefs.labelSizeSp.collectAsState(initial = 16)
    val itemSpacingDp by app.prefs.itemSpacingDp.collectAsState(initial = 10)
    val sidePaddingDp by app.prefs.sidePaddingDp.collectAsState(initial = 20)
    val alphabetSidePaddingDp by app.prefs.alphabetSidePaddingDp.collectAsState(initial = 20)
    val nowPlayingHeightDp by app.prefs.nowPlayingHeightDp.collectAsState(initial = 64)
    val homePaddings by app.prefs.homePaddings.collectAsState(initial = HomePaddings.Default)
    val edgeSide by app.prefs.edgeSide.collectAsState(initial = EdgeSide.RIGHT)
    val alwaysShowAz by app.prefs.alwaysShowAz.collectAsState(initial = true)
    val showAlphabet by app.prefs.showAlphabet.collectAsState(initial = true)
    val alignRight by app.prefs.alignRight.collectAsState(initial = false)
    val dimWallpaperAlpha by app.prefs.dimWallpaperAlpha.collectAsState(initial = 0.35f)
    val hapticsEnabled by app.prefs.hapticsEnabled.collectAsState(initial = true)
    val dimHomeAlpha by app.prefs.dimHomeAlpha.collectAsState(initial = 0f)
    val showFavoriteLabels by app.prefs.showFavoriteLabels.collectAsState(initial = true)
    val textColorMode by app.prefs.textColorMode.collectAsState(initial = TextColorMode.AUTO)
    val doubleTapToLock by app.prefs.doubleTapToLock.collectAsState(initial = false)
    val widgetId by app.prefs.widgetId.collectAsState(initial = -1)
    val widgetIds by app.prefs.widgetIds.collectAsState(initial = emptyList())
    val widgetPosition by app.prefs.widgetPosition.collectAsState(initial = 0)
    val widgetHeightDp by app.prefs.widgetHeightDp.collectAsState(initial = 180)
    val nowPlayingEnabled by app.prefs.nowPlayingEnabled.collectAsState(initial = true)
    val showAppNotifications by app.prefs.showAppNotifications.collectAsState(initial = true)
    val folderWindowPopup by app.prefs.folderWindowPopup.collectAsState(initial = true)
    val clockStyle by app.prefs.clockStyle.collectAsState(initial = ClockStyle.CLASSIC)
    val folders by app.prefs.folders.collectAsState(initial = emptyList())
    val contentColor = rememberContentColor(textColorMode)

    val themedIcons by app.prefs.themedIcons.collectAsState(initial = false)
    val themedIconStyle by app.prefs.themedIconStyle.collectAsState(initial = ThemedIconStyle.MATERIAL_YOU)
    val activeThemeId by app.prefs.activeThemeId.collectAsState(initial = "system_dynamic")

    val dynamicButtonEnabled by app.prefs.dynamicButtonEnabled.collectAsState(initial = true)
    val dynamicButtonClickApp by app.prefs.dynamicButtonClickApp.collectAsState(initial = null)
    val dynamicButtonSwipeUpApp by app.prefs.dynamicButtonSwipeUpApp.collectAsState(initial = null)
    val dynamicButtonSwipeDownApp by app.prefs.dynamicButtonSwipeDownApp.collectAsState(initial = null)

    val searchButtonEnabled by app.prefs.searchButtonEnabled.collectAsState(initial = true)
    val searchIncludeApps by app.prefs.searchIncludeApps.collectAsState(initial = true)
    val searchIncludeContacts by app.prefs.searchIncludeContacts.collectAsState(initial = true)
    val searchIncludeSettings by app.prefs.searchIncludeSettings.collectAsState(initial = true)
    val searchIncludeWeb by app.prefs.searchIncludeWeb.collectAsState(initial = true)
    val searchIncludePlayStore by app.prefs.searchIncludePlayStore.collectAsState(initial = true)
    val searchEngineName by app.prefs.searchEngine.collectAsState(initial = "GOOGLE")
    val searchAutoKeyboard by app.prefs.searchAutoKeyboard.collectAsState(initial = true)
    val autoBackupEnabled by app.prefs.autoBackupEnabled.collectAsState(initial = false)
    val autoBackupFrequency by app.prefs.autoBackupFrequency.collectAsState(initial = BackupFrequency.WEEKLY)
    val backupMaxKeep by app.prefs.backupMaxKeep.collectAsState(initial = 5)
    val backupIncludeWallpaper by app.prefs.backupIncludeWallpaper.collectAsState(initial = true)
    val lastAutoBackupTimestamp by app.prefs.lastAutoBackupTimestamp.collectAsState(initial = 0L)
    val backupFolderUri by app.prefs.backupFolderUri.collectAsState(initial = null)
    val backupFolderName by app.prefs.backupFolderName.collectAsState(initial = null)

    val searchConfig = remember(
        searchButtonEnabled,
        searchIncludeApps,
        searchIncludeContacts,
        searchIncludeSettings,
        searchIncludeWeb,
        searchIncludePlayStore,
        searchEngineName,
        searchAutoKeyboard,
    ) {
        SearchConfig(
            buttonEnabled = searchButtonEnabled,
            includeApps = searchIncludeApps,
            includeContacts = searchIncludeContacts,
            includeSettings = searchIncludeSettings,
            includeWeb = searchIncludeWeb,
            includePlayStore = searchIncludePlayStore,
            searchEngine = SearchEngine.fromId(searchEngineName),
            autoKeyboard = searchAutoKeyboard,
        )
    }

    val appsByKey = remember(allApps) { allApps.associateBy { it.key } }
    val foldersById = remember(folders) { folders.associateBy { it.id } }

    // A favorites row is an app or a folder; both come out of the same ordered token list.
    val favoriteEntries = remember(favoriteKeys, appsByKey, foldersById) {
        favoriteKeys.mapNotNull { token ->
            val folderId = folderIdFromToken(token)
            if (folderId != null) {
                foldersById[folderId]?.let { FavoriteEntry.FolderRef(it) }
            } else {
                appsByKey[token]?.let { FavoriteEntry.App(it) }
            }
        }
    }

    // Rasterise icons in the background so opening the A-Z list doesn't have to. Favorites and
    // folder members go first: they are what the home screen needs before anything else.
    val listIconPx = with(LocalDensity.current) { iconSizeDp.dp.roundToPx() }
    val priorityKeys = remember(favoriteKeys, folders) {
        favoriteKeys.toSet() + folders.flatMap { it.apps }
    }
    LaunchedEffect(allApps, iconPackPackage, iconOverrides, listIconPx, priorityKeys, themedIcons) {
        warmIconCache(context, allApps, iconPackPackage, iconOverrides, listIconPx, priorityKeys, themedIcons)
    }

    val settings = HomeSettings(
        iconSizeDp = iconSizeDp,
        labelSizeSp = labelSizeSp,
        itemSpacingDp = itemSpacingDp,
        sidePaddingDp = sidePaddingDp,
        alphabetSidePaddingDp = alphabetSidePaddingDp,
        nowPlayingHeightDp = nowPlayingHeightDp,
        nowPlayingEnabled = nowPlayingEnabled,
        edgeSide = edgeSide,
        alwaysShowAz = alwaysShowAz,
        showAlphabet = showAlphabet,
        alignRight = alignRight,
        dimWallpaperAlpha = dimWallpaperAlpha,
        dimHomeAlpha = dimHomeAlpha,
        hapticsEnabled = hapticsEnabled,
        showFavoriteLabels = showFavoriteLabels,
        doubleTapToLock = doubleTapToLock,
        contentColor = contentColor,
        clockStyle = clockStyle,
        showAppNotifications = showAppNotifications,
        folderWindowPopup = folderWindowPopup,
        dynamicButtonEnabled = dynamicButtonEnabled,
        dynamicButtonClickApp = dynamicButtonClickApp,
        dynamicButtonSwipeUpApp = dynamicButtonSwipeUpApp,
        dynamicButtonSwipeDownApp = dynamicButtonSwipeDownApp,
        searchButtonEnabled = searchButtonEnabled,
    )

    var pendingIconTarget by remember { mutableStateOf<String?>(null) }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val target = pendingIconTarget
        if (uri != null && target != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val folderId = folderIdFromToken(target)
            scope.launch {
                if (folderId != null) {
                    app.prefs.setFolderIcon(folderId, uri.toString())
                } else {
                    app.prefs.setIconOverride(target, uri.toString())
                }
            }
        }
        pendingIconTarget = null
    }

    val widgetPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val id = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
            if (id != -1) {
                scope.launch { app.prefs.addWidgetId(id) }
            }
        }
    }

    val widgetActions = remember(widgetIds, widgetId) {
        WidgetSlotActions(
            onAddWidget = { widgetPickerLauncher.launch(Intent(context, WidgetPickerActivity::class.java)) },
            onRemoveWidget = { targetId ->
                val idToRemove = if (targetId > 0) targetId else (widgetIds.firstOrNull() ?: widgetId)
                scope.launch {
                    if (idToRemove > 0) runCatching { app.widgetHost.deleteAppWidgetId(idToRemove) }
                    app.prefs.removeWidgetId(idToRemove)
                }
            },
            onWidgetSettings = { targetId ->
                val idToConfig = if (targetId > 0) targetId else (widgetIds.firstOrNull() ?: widgetId)
                val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(idToConfig)
                val configure = info?.configure
                if (configure != null) {
                    val activity = context as? Activity
                    var started = false
                    if (activity != null) {
                        try {
                            val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                ActivityOptions.makeBasic().apply {
                                    setPendingIntentBackgroundActivityStartMode(
                                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                                    )
                                }.toBundle()
                            } else {
                                null
                            }
                            app.widgetHost.startAppWidgetConfigureActivityForResult(
                                activity,
                                idToConfig,
                                0,
                                0,
                                options
                            )
                            started = true
                        } catch (e: Exception) {
                            Log.w("ViagaraNavHost", "startAppWidgetConfigureActivityForResult failed for widget $idToConfig", e)
                        }
                    }
                    if (!started) {
                        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                            component = configure
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, idToConfig)
                        }
                        runCatching { context.startActivity(intent) }
                    }
                }
            },
            onAppInfo = { targetId ->
                val idToInfo = if (targetId > 0) targetId else (widgetIds.firstOrNull() ?: widgetId)
                val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(idToInfo)
                info?.let { app.appRepository.openAppInfo(it.provider.packageName) }
            },
            onResize = { newHeight -> scope.launch { app.prefs.setWidgetHeightDp(newHeight) } },
            onOpenSettings = { navController.navigate("settings") },
        )
    }

    // HOME has to unwind the whole stack. The equivalent effect inside the home destination
    // can't do this: that destination isn't composed while Settings is on screen.
    LaunchedEffect(homeIntentTick) {
        if (homeIntentTick > 0 && navController.currentDestination?.route != "home") {
            navController.popBackStack("home", inclusive = false)
        }
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeRoute(
                app = app,
                homeIntentTick = homeIntentTick,
                settings = settings,
                homePaddings = homePaddings,
                favorites = favoriteEntries,
                appsByKey = appsByKey,
                folders = folders,
                favoriteKeys = favoriteKeys,
                hiddenApps = hiddenApps,
                nameOverrides = nameOverrides,
                widgetId = widgetId,
                widgetIds = widgetIds,
                widgetPosition = widgetPosition,
                widgetHeightDp = widgetHeightDp,
                widgetActions = widgetActions,
                onPeekStatusBar = onPeekStatusBar,
                onNavigate = { route -> navController.navigate(route) },
            )
        }

        composable("settings") {
            val iconPacks = remember { app.iconPackRepository.getInstalledIconPacks() }
            val listenerEnabled = remember(homeIntentTick) { isListenerEnabled(context) }
            val isDefaultLauncher = remember(homeIntentTick) {
                dev.viagaralauncher.service.DefaultLauncherUtil.isDefaultLauncher(context)
            }
            SettingsScreen(
                hiddenCount = hiddenApps.size,
                iconPacks = iconPacks,
                iconPackPackage = iconPackPackage,
                previewApp = allApps.firstOrNull(),
                iconSizeDp = iconSizeDp,
                labelSizeSp = labelSizeSp,
                itemSpacingDp = itemSpacingDp,
                font = font,
                hideStatusBar = hideStatusBar,
                dimWallpaperAlpha = dimWallpaperAlpha,
                hapticsEnabled = hapticsEnabled,
                dimHomeAlpha = dimHomeAlpha,
                showFavoriteLabels = showFavoriteLabels,
                textColorMode = textColorMode,
                doubleTapToLock = doubleTapToLock,
                edgeSide = edgeSide,
                alwaysShowAz = alwaysShowAz,
                showAlphabet = showAlphabet,
                alignRight = alignRight,
                nowPlayingEnabled = nowPlayingEnabled,
                nowPlayingListenerEnabled = listenerEnabled,
                showAppNotifications = showAppNotifications,
                onSetShowAppNotifications = { scope.launch { app.prefs.setShowAppNotifications(it) } },
                folderWindowPopup = folderWindowPopup,
                onSetFolderWindowPopup = { scope.launch { app.prefs.setFolderWindowPopup(it) } },
                onSetIconPack = { scope.launch { app.prefs.setIconPackPackage(it) } },
                onSetIconSize = { scope.launch { app.prefs.setIconSizeDp(it) } },
                onSetLabelSize = { scope.launch { app.prefs.setLabelSizeSp(it) } },
                onSetItemSpacing = { scope.launch { app.prefs.setItemSpacingDp(it) } },
                onSetFont = { scope.launch { app.prefs.setFont(it) } },
                onSetHideStatusBar = { scope.launch { app.prefs.setHideStatusBar(it) } },
                onSetDimWallpaper = { scope.launch { app.prefs.setDimWallpaperAlpha(it) } },
                onSetHaptics = { scope.launch { app.prefs.setHapticsEnabled(it) } },
                onSetDimHome = { scope.launch { app.prefs.setDimHomeAlpha(it) } },
                onSetShowFavoriteLabels = { scope.launch { app.prefs.setShowFavoriteLabels(it) } },
                onSetTextColorMode = { scope.launch { app.prefs.setTextColorMode(it) } },
                onSetDoubleTapToLock = { scope.launch { app.prefs.setDoubleTapToLock(it) } },
                onSetEdgeSide = { scope.launch { app.prefs.setEdgeSide(it) } },
                onSetAlwaysShowAz = { scope.launch { app.prefs.setAlwaysShowAz(it) } },
                onSetShowAlphabet = { scope.launch { app.prefs.setShowAlphabet(it) } },
                onSetAlignRight = { scope.launch { app.prefs.setAlignRight(it) } },
                onSetNowPlayingEnabled = { scope.launch { app.prefs.setNowPlayingEnabled(it) } },
                shadeGestureReady = remember(homeIntentTick) { SystemUi.canExpandShade() },
                clockStyle = clockStyle,
                onOpenClockStyle = { navController.navigate("settings/clock") },
                themedIcons = themedIcons,
                themedIconStyle = themedIconStyle,
                onSetThemedIcons = { scope.launch { app.prefs.setThemedIcons(it) } },
                onSetThemedIconStyle = { scope.launch { app.prefs.setThemedIconStyle(it) } },
                onOpenAccessibilitySettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                onOpenHiddenApps = { navController.navigate("settings/hidden") },
                onOpenFavorites = { navController.navigate("favorites") },
                onOpenNotificationSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                onOpenDynamicButtonSettings = { navController.navigate("settings/dynamic_button") },
                onOpenSearchSettings = { navController.navigate("settings/search") },
                isDefaultLauncher = isDefaultLauncher,
                onOpenDefaultLauncherSettings = {
                    dev.viagaralauncher.service.DefaultLauncherUtil.requestSetDefaultLauncher(context)
                },
                onOpenBackupSettings = { navController.navigate("settings/backup") },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "search",
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { fullHeight -> (fullHeight * 0.12f).toInt() },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) + fadeIn(animationSpec = tween(220))
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { fullHeight -> (fullHeight * 0.12f).toInt() },
                    animationSpec = tween(180),
                ) + fadeOut(animationSpec = tween(150))
            },
            popEnterTransition = { fadeIn(animationSpec = tween(180)) },
            popExitTransition = {
                slideOutVertically(
                    targetOffsetY = { fullHeight -> (fullHeight * 0.12f).toInt() },
                    animationSpec = tween(180),
                ) + fadeOut(animationSpec = tween(150))
            },
        ) {
            val view = androidx.compose.ui.platform.LocalView.current
            SearchScreen(
                allApps = allApps,
                nameOverrides = nameOverrides,
                config = searchConfig,
                onLaunchApp = { appInfo, bounds ->
                    app.appRepository.launch(appInfo.componentName, sourceBounds = bounds, sourceView = view)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings/search") {
            SearchSettingsScreen(
                config = searchConfig,
                onSetButtonEnabled = { scope.launch { app.prefs.setSearchButtonEnabled(it) } },
                onSetIncludeApps = { scope.launch { app.prefs.setSearchIncludeApps(it) } },
                onSetIncludeContacts = { scope.launch { app.prefs.setSearchIncludeContacts(it) } },
                onSetIncludeSettings = { scope.launch { app.prefs.setSearchIncludeSettings(it) } },
                onSetIncludeWeb = { scope.launch { app.prefs.setSearchIncludeWeb(it) } },
                onSetIncludePlayStore = { scope.launch { app.prefs.setSearchIncludePlayStore(it) } },
                onSetSearchEngine = { scope.launch { app.prefs.setSearchEngine(it.idName) } },
                onSetAutoKeyboard = { scope.launch { app.prefs.setSearchAutoKeyboard(it) } },
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings/dynamic_button") {
            DynamicButtonSettingsScreen(
                allApps = allApps,
                appsByKey = appsByKey,
                enabled = dynamicButtonEnabled,
                clickAppKey = dynamicButtonClickApp,
                swipeUpAppKey = dynamicButtonSwipeUpApp,
                swipeDownAppKey = dynamicButtonSwipeDownApp,
                hapticsEnabled = hapticsEnabled,
                edgeSide = edgeSide,
                onSetEnabled = { scope.launch { app.prefs.setDynamicButtonEnabled(it) } },
                onSetClickApp = { scope.launch { app.prefs.setDynamicButtonClickApp(it) } },
                onSetSwipeUpApp = { scope.launch { app.prefs.setDynamicButtonSwipeUpApp(it) } },
                onSetSwipeDownApp = { scope.launch { app.prefs.setDynamicButtonSwipeDownApp(it) } },
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings/clock") {
            ClockStylePickerScreen(
                currentStyle = clockStyle,
                hapticsEnabled = hapticsEnabled,
                onSelectStyle = { selected ->
                    scope.launch { app.prefs.setClockStyle(selected) }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable("themes") {
            ThemesScreen(
                currentThemeId = activeThemeId,
                hapticsEnabled = hapticsEnabled,
                onSelectTheme = { selected ->
                    scope.launch { app.prefs.setActiveThemeId(selected) }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings/backup") {
            BackupSettingsScreen(
                prefs = app.prefs,
                backupFolderUri = backupFolderUri,
                backupFolderName = backupFolderName,
                autoBackupEnabled = autoBackupEnabled,
                autoBackupFrequency = autoBackupFrequency,
                backupMaxKeep = backupMaxKeep,
                backupIncludeWallpaper = backupIncludeWallpaper,
                lastAutoBackupTimestamp = lastAutoBackupTimestamp,
                onSetBackupFolder = { uri, name ->
                    scope.launch { app.prefs.setBackupFolder(uri, name) }
                },
                onSetAutoBackupEnabled = { scope.launch { app.prefs.setAutoBackupEnabled(it) } },
                onSetAutoBackupFrequency = { scope.launch { app.prefs.setAutoBackupFrequency(it) } },
                onSetBackupMaxKeep = { scope.launch { app.prefs.setBackupMaxKeep(it) } },
                onSetBackupIncludeWallpaper = { scope.launch { app.prefs.setBackupIncludeWallpaper(it) } },
                onBack = { navController.popBackStack() },
            )
        }

        composable("favorites") {
            ManageFavoritesScreen(
                allApps = allApps,
                favoriteKeys = favoriteKeys,
                nameOverrides = nameOverrides,
                iconSizeDp = iconSizeDp,
                onSetFavorite = { appInfo, add ->
                    scope.launch {
                        if (add) app.prefs.addFavorite(appInfo.key) else app.prefs.removeFavorite(appInfo.key)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable("folder/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            FolderAppsScreen(
                folder = foldersById[id],
                allApps = allApps,
                nameOverrides = nameOverrides,
                iconSizeDp = iconSizeDp,
                onSetInFolder = { appInfo, inFolder ->
                    scope.launch {
                        if (inFolder) {
                            app.prefs.addAppToFolder(id, appInfo.key)
                        } else {
                            app.prefs.removeAppFromFolder(id, appInfo.key)
                        }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings/hidden") {
            HiddenAppsScreen(
                allApps = allApps,
                hiddenApps = hiddenApps,
                onToggleHidden = { appInfo, hidden ->
                    scope.launch { app.prefs.setHidden(appInfo.key, hidden) }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable("iconpicker/{key}") { entry ->
            val key = entry.arguments?.getString("key")?.let { Uri.decode(it) }.orEmpty()
            val folderId = folderIdFromToken(key)
            val targetApp = allApps.find { it.key == key }
            val label = when {
                folderId != null -> foldersById[folderId]?.name.orEmpty()
                else -> nameOverrides[key] ?: targetApp?.label.orEmpty()
            }

            fun applyIcon(value: String?) {
                scope.launch {
                    if (folderId != null) {
                        app.prefs.setFolderIcon(folderId, value)
                    } else {
                        app.prefs.setIconOverride(key, value)
                    }
                }
            }

            IconPickerScreen(
                appLabel = label,
                onPickPackIcon = { packPkg, drawableName ->
                    applyIcon(encodePackOverride(packPkg, drawableName))
                    navController.popBackStack()
                },
                onPickFromGallery = {
                    pendingIconTarget = key
                    pickImageLauncher.launch(arrayOf("image/*"))
                },
                onResetIcon = {
                    applyIcon(null)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "task_manager",
            enterTransition = { fadeIn(tween(250)) + slideInVertically(tween(300)) { it / 8 } },
            exitTransition = { fadeOut(tween(200)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically(tween(250)) { it / 8 } },
        ) {
            dev.viagaralauncher.ui.root.TaskManagerScreen(
                allApps = allApps,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToNetworkStats = { navController.navigate("network_monitor") },
                onNavigateToBatteryStats = { navController.navigate("battery_stats") },
            )
        }

        composable(
            route = "network_monitor",
            enterTransition = { fadeIn(tween(250)) + slideInVertically(tween(300)) { it / 8 } },
            exitTransition = { fadeOut(tween(200)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically(tween(250)) { it / 8 } },
        ) {
            dev.viagaralauncher.ui.network.NetworkStatsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "battery_stats",
            enterTransition = { fadeIn(tween(250)) + slideInVertically(tween(300)) { it / 8 } },
            exitTransition = { fadeOut(tween(200)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically(tween(250)) { it / 8 } },
        ) {
            dev.viagaralauncher.ui.battery.BatteryStatsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}