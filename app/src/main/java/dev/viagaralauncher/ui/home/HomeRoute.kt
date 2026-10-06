// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.home

import android.content.Context
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.zIndex
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.viagaralauncher.ViagaraApp
import dev.viagaralauncher.data.AppInfo
import dev.viagaralauncher.data.ClockStyle
import dev.viagaralauncher.data.EdgeSide
import dev.viagaralauncher.data.Folder
import dev.viagaralauncher.data.HomePaddings
import dev.viagaralauncher.data.PaddingSlot
import dev.viagaralauncher.data.folderToken
import dev.viagaralauncher.media.NowPlayingBus
import dev.viagaralauncher.media.isListenerEnabled
import dev.viagaralauncher.service.DefaultLauncherUtil
import dev.viagaralauncher.service.SystemUi
import dev.viagaralauncher.ui.applist.AppListModel
import dev.viagaralauncher.ui.applist.AppListScreen
import dev.viagaralauncher.ui.applist.EdgeScrubber
import dev.viagaralauncher.ui.applist.EdgeTouchZone
import dev.viagaralauncher.ui.applist.SCRUBBER_STAR
import dev.viagaralauncher.ui.applist.ScrubBand
import dev.viagaralauncher.ui.applist.ScrubberGeometry
import dev.viagaralauncher.ui.applist.ScrubState
import dev.viagaralauncher.ui.applist.buildAppListModel
import dev.viagaralauncher.ui.common.FolderPickerDialog
import dev.viagaralauncher.widget.WidgetSlotActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Immutable
import android.net.Uri
import dev.viagaralauncher.R

/** Width of the invisible strip at each screen edge that opens the app list. */
private val EDGE_ZONE_WIDTH = 52.dp

/** Android only honours this much gesture exclusion per side, so spend it on the strip. */
private const val GESTURE_EXCLUSION_CAP_DP = 200

/** How long a launch is given to take us off screen before the overlay closes itself. */
private const val LAUNCH_CLOSE_TIMEOUT_MS = 2000L

/** Long enough to read as a settle, short enough not to stand between you and the icons. */
private const val HOME_FADE_MS = 220

/**
 * The home destination: the home screen itself, the app-list overlay layered over it, and the
 * edge zones that move between them.
 *
 * The overlay is kept composed and measured even while hidden — just never placed — because
 * building the whole list from scratch on every open is what made it take a beat to appear.
 */
@Composable
fun HomeRoute(
    app: ViagaraApp,
    homeIntentTick: Int,
    settings: HomeSettings,
    homePaddings: HomePaddings,
    favorites: List<FavoriteEntry>,
    appsByKey: Map<String, AppInfo>,
    folders: List<Folder>,
    favoriteKeys: List<String>,
    hiddenApps: Set<String>,
    nameOverrides: Map<String, String>,
    widgetId: Int = -1,
    widgetIds: List<Int> = emptyList(),
    widgetPosition: Int,
    widgetHeightDp: Int,
    widgetActions: WidgetSlotActions,
    onPeekStatusBar: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // Grouping, sorting and flattening every installed app is too much to do in composition,
    // and it re-runs whenever a name override changes.
    val listModel by produceState(
        initialValue = AppListModel(emptyList(), emptyList()),
        appsByKey,
        hiddenApps,
        nameOverrides,
    ) {
        val apps = appsByKey.values.toList()
        value = withContext(Dispatchers.Default) {
            buildAppListModel(apps, hiddenApps) { nameOverrides[it.key] ?: it.label }
        }
    }

    var appListVisible by remember { mutableStateOf(false) }
    val scrub = remember { ScrubState() }
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var homeEditMode by remember { mutableStateOf(false) }
    var folderPickerFor by remember { mutableStateOf<AppInfo?>(null) }
    var showHomeOptions by remember { mutableStateOf(false) }
    var lockTargetOffset by remember { mutableStateOf<Offset?>(null) }

    val hasPromptedDefaultLauncher by app.prefs.hasPromptedDefaultLauncher.collectAsState(initial = true)
    var showDefaultLauncherDialog by remember { mutableStateOf(false) }

    LaunchedEffect(hasPromptedDefaultLauncher) {
        if (!hasPromptedDefaultLauncher) {
            if (DefaultLauncherUtil.isDefaultLauncher(context)) {
                app.prefs.setHasPromptedDefaultLauncher(true)
            } else {
                delay(500)
                showDefaultLauncherDialog = true
            }
        }
    }

    LaunchedEffect(Unit) {
        dev.viagaralauncher.update.UpdateManager.checkForUpdates(this, context = context)
        dev.viagaralauncher.wallpaper.WallpaperRepository.checkForNewWallpapers(context, this)
    }

    val updateInfo by dev.viagaralauncher.update.UpdateManager.updateAvailable.collectAsState()
    val showChangelogRequested by dev.viagaralauncher.update.UpdateManager.showChangelogRequested.collectAsState()

    val nowPlaying by NowPlayingBus.state.collectAsState()
    val notificationsByPackage by dev.viagaralauncher.notification.NotificationBus.notifications.collectAsState()
    val listenerGranted = remember(homeIntentTick) { isListenerEnabled(context) }
    val nowPlayingPromptDismissed by app.prefs.nowPlayingPromptDismissed.collectAsState(initial = false)
    val musicPlaybackDetected by app.prefs.musicPlaybackDetected.collectAsState(initial = false)

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    DisposableEffect(listenerGranted, nowPlayingPromptDismissed, musicPlaybackDetected, settings.nowPlayingEnabled) {
        if (listenerGranted || nowPlayingPromptDismissed || musicPlaybackDetected || !settings.nowPlayingEnabled) {
            return@DisposableEffect onDispose {}
        }

        fun checkPlayback() {
            if (audioManager?.isMusicActive == true) {
                scope.launch { app.prefs.setMusicPlaybackDetected(true) }
            }
        }

        checkPlayback()

        val lifecycleOwner = context as? androidx.lifecycle.LifecycleOwner
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkPlayback()
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(lifecycleObserver)

        var playbackCallback: AudioManager.AudioPlaybackCallback? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioManager != null) {
            val callback = object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                    val hasActiveMedia = configs?.any { config ->
                        val usage = config.audioAttributes.usage
                        usage == AudioAttributes.USAGE_MEDIA ||
                            usage == AudioAttributes.USAGE_GAME ||
                            usage == AudioAttributes.USAGE_UNKNOWN
                    } == true
                    if (hasActiveMedia || audioManager.isMusicActive) {
                        scope.launch { app.prefs.setMusicPlaybackDetected(true) }
                    }
                }
            }
            playbackCallback = callback
            audioManager.registerAudioPlaybackCallback(callback, Handler(Looper.getMainLooper()))
        }

        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && playbackCallback != null && audioManager != null) {
                runCatching { audioManager.unregisterAudioPlaybackCallback(playbackCallback) }
            }
        }
    }

    // Only show now playing if there is an active session (when permission granted)
    // or when music playback was detected for the first time and user hasn't dismissed the prompt.
    val nowPlayingHasContent = settings.nowPlayingEnabled && if (listenerGranted) {
        nowPlaying != null
    } else {
        musicPlaybackDetected && !nowPlayingPromptDismissed
    }
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val effectiveViewportHeight = if (viewportHeightPx > 0) viewportHeightPx.toFloat() else screenHeightPx
    val band = remember(effectiveViewportHeight, density.density, listModel.letters.size) {
        ScrubberGeometry.computeBand(
            viewportHeightPx = effectiveViewportHeight,
            density = density.density,
            letterCount = listModel.letters.size,
        )
    }

    // Set while a launched app is expected to take over the screen; see closeAfterLaunch.
    var launchClose by remember { mutableStateOf<Job?>(null) }

    // The home screen fades back in when we put the list away ourselves, but not when the
    // system did it for us: laying our own fade over Android's home transition — or running it
    // on resume for a close that happened while we were backgrounded — just reads as noise.
    var snapHome by remember { mutableStateOf(false) }

    fun closeAppList(snap: Boolean = false) {
        launchClose?.cancel()
        launchClose = null
        snapHome = snap
        appListVisible = false
        scrub.cancel()
    }

    // Closing the overlay the moment an app is launched puts the home screen on screen for
    // the few frames before that app's window arrives, which reads as a jolt back to home.
    // Leave the list up and let it go when we are actually backgrounded instead: by then
    // nothing of ours is visible, so the close costs nothing and can snap.
    fun closeAfterLaunch() {
        launchClose?.cancel()
        launchClose = scope.launch {
            // Nothing ever came to the foreground. Rather than leave the list stuck open,
            // put it away the ordinary animated way.
            delay(LAUNCH_CLOSE_TIMEOUT_MS)
            launchClose = null
            closeAppList()
        }
    }

    LaunchedEffect(settings.edgeSide) { scrub.syncRestingSide(settings.edgeSide) }

    BackHandler(enabled = appListVisible) { closeAppList() }

    // Leaving the launcher (screen off, another app) should always drop us back to the home
    // screen rather than reopening onto the overlay.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) closeAppList(snap = true)
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                lockTargetOffset = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(homeIntentTick) {
        if (homeIntentTick > 0) closeAppList(snap = true)
    }

    LaunchedEffect(settings.edgeSide, view, band) {
        view.post {
            val density = view.resources.displayMetrics.density
            val widthPx = (EDGE_ZONE_WIDTH.value * density).toInt()
            val h = view.height
            val w = view.width
            if (h > 0 && w > 0) {
                // Android's back-gesture claims the outer edges, and only honours 200dp of
                // exclusion per side — so spend it on the scrub band rather than spreading it
                // uselessly over the whole screen height.
                val capPx = (GESTURE_EXCLUSION_CAP_DP * density).toInt()
                val top = band.topPx.toInt().coerceIn(0, h)
                val bottom = band.bottomPx.toInt().coerceIn(top, h)
                val clipped = if (bottom - top > capPx) top + capPx else bottom
                val rects = buildList {
                    if (settings.edgeSide != EdgeSide.RIGHT) add(Rect(0, top, widthPx, clipped))
                    if (settings.edgeSide != EdgeSide.LEFT) add(Rect(w - widthPx, top, w, clipped))
                }
                ViewCompat.setSystemGestureExclusionRects(view, rects)
            }
        }
    }
    DisposableEffect(view) {
        // Settings and the other screens must get the system back-gesture back.
        onDispose { ViewCompat.setSystemGestureExclusionRects(view, emptyList()) }
    }

    val handleDoubleTapLock: (Offset) -> Unit = { offset ->
        if (lockTargetOffset == null) {
            lockTargetOffset = offset
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportHeightPx = it.height },
    ) {
        // Hidden entirely while the list is up, so only the wallpaper sits behind it — and
        // kept composed the same way, so coming back is instant.
        // Opening answers a finger on the edge and must not lag behind it, so it snaps. Coming
        // back gets a short decelerating fade: the wallpaper is already there, so this is only
        // the icons settling in, and cutting them in on a single frame is what read as a jolt.
        val showHome by remember(appListVisible, scrub) {
            derivedStateOf { !appListVisible || scrub.letter == SCRUBBER_STAR }
        }
        val homeAlpha by animateFloatAsState(
            targetValue = if (showHome) 1f else 0f,
            animationSpec = if (!showHome || snapHome) {
                snap()
            } else {
                tween(HOME_FADE_MS, easing = LinearOutSlowInEasing)
            },
            label = "homeAlpha",
        )
        val blockHomeTouches by remember(appListVisible, scrub) {
            derivedStateOf { appListVisible && !scrub.active }
        }
        Box(
            modifier = Modifier
                .graphicsLayer { alpha = homeAlpha }
                .then(
                    // Hidden, but still laid out: an AppWidgetHostView that is never placed
                    // loses its layout and comes back with its text collapsed.
                    if (blockHomeTouches) {
                        Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    event.changes.forEach { it.consume() }
                                    if (event.changes.none { it.pressed }) break
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                ),
        ) {
            if (settings.dimHomeAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = settings.dimHomeAlpha)),
                )
            }
            HomeScreen(
                favorites = favorites,
                nameOverrides = nameOverrides,
                iconSizeDp = settings.iconSizeDp,
                labelSizeSp = settings.labelSizeSp,
                itemSpacingDp = settings.itemSpacingDp,
                sidePaddingDp = settings.sidePaddingDp,
                onSetSidePadding = { scope.launch { app.prefs.setSidePaddingDp(it) } },
                alphabetSidePaddingDp = settings.alphabetSidePaddingDp,
                onSetAlphabetSidePadding = { scope.launch { app.prefs.setAlphabetSidePaddingDp(it) } },
                paddings = homePaddings,
                widgetId = widgetId,
                widgetIds = widgetIds,
                widgetPosition = widgetPosition,
                widgetHeightDp = widgetHeightDp,
                hapticsEnabled = settings.hapticsEnabled,
                nowPlayingEnabled = settings.nowPlayingEnabled,
                nowPlayingHeightDp = settings.nowPlayingHeightDp,
                onResizeNowPlaying = { scope.launch { app.prefs.setNowPlayingHeightDp(it) } },
                widgetActions = widgetActions,
                onLaunch = { appInfo, bounds ->
                    app.appRepository.launch(appInfo.componentName, sourceBounds = bounds, sourceView = view)
                },
                onRemoveFavorite = { scope.launch { app.prefs.removeFavorite(it.key) } },
                onOpenFolderApp = { appInfo, bounds ->
                    app.appRepository.launch(appInfo.componentName, sourceBounds = bounds, sourceView = view)
                },
                onRenameFolder = { folder, name ->
                    scope.launch { app.prefs.upsertFolder(folder.copy(name = name)) }
                },
                onDeleteFolder = { folder -> scope.launch { app.prefs.deleteFolder(folder.id) } },
                onChangeFolderIcon = { folder -> onNavigate(iconPickerRoute(folderToken(folder.id))) },
                onResetFolderIcon = { folder -> scope.launch { app.prefs.setFolderIcon(folder.id, null) } },
                onManageFolder = { folder -> onNavigate("folder/" + folder.id) },
                onRemoveFromFolder = { folder, appInfo ->
                    scope.launch { app.prefs.removeAppFromFolder(folder.id, appInfo.key) }
                },
                appsByKey = appsByKey,
                onMoveToFolder = { folderPickerFor = it },
                onReorderHome = { newFavKeys, newWidgetPos ->
                    scope.launch {
                        app.prefs.setFavorites(newFavKeys)
                        app.prefs.setWidgetPosition(newWidgetPos)
                    }
                },
                onCommitPadding = { slot: PaddingSlot, value: Int ->
                    scope.launch { app.prefs.setHomePadding(slot, value) }
                },
                onResetAlignments = {
                    scope.launch { app.prefs.resetHomePaddingsAndSidePadding() }
                },
                onFavoritesBoundsChanged = { _, _ ->
                    // Scrubber band is computed independently to ensure consistent vertical centering
                },
                nowPlayingHasContent = nowPlayingHasContent,
                onDismissPermissionPrompt = {
                    scope.launch { app.prefs.setNowPlayingPromptDismissed(true) }
                },
                contentColor = settings.contentColor,
                showFavoriteLabels = settings.showFavoriteLabels,
                alignRight = settings.alignRight,
                editMode = homeEditMode,
                onEditModeChange = { homeEditMode = it },
                onPeekStatusBar = onPeekStatusBar,
                onExpandShade = {
                    // Say so rather than failing silently — that way a pull that does nothing
                    // tells you whether the gesture or the permission is at fault.
                    if (!SystemUi.expandNotificationShade(context)) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_enable_accessibility_shade),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onManageFavorites = { onNavigate("favorites") },
                onSetName = { appInfo, name -> scope.launch { app.prefs.setNameOverride(appInfo.key, name) } },
                onChangeIcon = { appInfo -> onNavigate(iconPickerRoute(appInfo.key)) },
                onAppInfo = { app.appRepository.openAppInfo(it.packageName, sourceView = view) },
                onOpenSettings = { onNavigate("settings") },
                onOpenHomeOptions = { showHomeOptions = true },
                showAppNotifications = settings.showAppNotifications,
                folderWindowPopup = settings.folderWindowPopup,
                notificationsByPackage = notificationsByPackage,
                clockStyle = settings.clockStyle,
                doubleTapToLock = settings.doubleTapToLock,
                onDoubleTapLock = handleDoubleTapLock,
                edgeSide = settings.edgeSide,
                alwaysShowAz = settings.alwaysShowAz,
            )
        }

        // Kept composed and measured even while hidden, just never placed. Not placing it
        // means it neither draws nor receives touches.
        Box(
            modifier = Modifier
                .graphicsLayer { alpha = if (appListVisible) 1f else 0f }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        if (appListVisible) {
                            placeable.place(0, 0)
                        }
                    }
                },
        ) {
            AppListScreen(
                model = listModel,
                nameOverrides = nameOverrides,
                scrub = scrub,
                dimAlpha = settings.dimWallpaperAlpha,
                iconSizeDp = settings.iconSizeDp,
                labelSizeSp = settings.labelSizeSp,
                band = band,
                viewportHeightPx = viewportHeightPx,
                visible = appListVisible,
                favoriteKeys = remember(favoriteKeys) { favoriteKeys.toSet() },
                onLaunch = { appInfo, bounds ->
                    // A launch that never got off the ground leaves nothing to wait for.
                    if (app.appRepository.launch(appInfo.componentName, sourceBounds = bounds, sourceView = view)) {
                        closeAfterLaunch()
                    } else {
                        closeAppList()
                    }
                },
                onSetFavorite = { appInfo, add ->
                    scope.launch {
                        if (add) app.prefs.addFavorite(appInfo.key) else app.prefs.removeFavorite(appInfo.key)
                    }
                },
                onSetName = { appInfo, name -> scope.launch { app.prefs.setNameOverride(appInfo.key, name) } },
                onChangeIcon = { appInfo ->
                    closeAppList()
                    onNavigate(iconPickerRoute(appInfo.key))
                },
                onAppInfo = { app.appRepository.openAppInfo(it.packageName, sourceView = view) },
                onHideApp = { appInfo -> scope.launch { app.prefs.setHidden(appInfo.key, true) } },
                onMoveToFolder = { appInfo -> closeAppList(); folderPickerFor = appInfo },
                onOpenSettings = { closeAppList(); onNavigate("settings") },
                onDismiss = { closeAppList() },
                contentColor = settings.contentColor,
                showAlphabet = settings.showAlphabet,
                alignRight = settings.alignRight,
                doubleTapToLock = settings.doubleTapToLock,
                showAppNotifications = settings.showAppNotifications,
                notificationsByPackage = notificationsByPackage,
                onDoubleTapLock = handleDoubleTapLock,
                sidePaddingDp = settings.sidePaddingDp,
                alphabetSidePaddingDp = settings.alphabetSidePaddingDp,
                searchButtonEnabled = settings.searchButtonEnabled,
                hapticsEnabled = settings.hapticsEnabled,
                onOpenSearch = {
                    closeAppList()
                    onNavigate("search")
                },
            )
        }

        if (settings.alwaysShowAz && !appListVisible && !showHomeOptions) {
            EdgeScrubber(
                letters = listModel.letters,
                scrubY = remember(scrub) { scrub::currentY },
                pullPx = remember(scrub) { scrub::currentPull },
                band = band,
                side = scrub.side,
                sidePaddingDp = settings.alphabetSidePaddingDp,
                modifier = Modifier.align(
                    if (scrub.side == EdgeSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd
                ),
            )
        }

        folderPickerFor?.let { target ->
            FolderPickerDialog(
                appLabel = nameOverrides[target.key] ?: target.label,
                folders = folders,
                onPickFolder = { folder ->
                    scope.launch { app.prefs.addAppToFolder(folder.id, target.key) }
                    folderPickerFor = null
                },
                onCreateFolder = { name ->
                    scope.launch {
                        val folder = Folder(
                            id = System.currentTimeMillis().toString(36),
                            name = name,
                            apps = listOf(target.key),
                        )
                        app.prefs.upsertFolder(folder)
                        // Drop the app's own row and give the folder one in its place.
                        app.prefs.removeFavorite(target.key)
                        app.prefs.addFavorite(folderToken(folder.id))
                    }
                    folderPickerFor = null
                },
                onDismiss = { folderPickerFor = null },
            )
        }

        // Edge zones sit on top of everything, so one unbroken touch opens the list and then
        // scrubs it as the finger moves.
        if (!homeEditMode && !showHomeOptions) {
            val sides = remember(settings.edgeSide) {
                when (settings.edgeSide) {
                    EdgeSide.LEFT -> listOf(EdgeSide.LEFT)
                    EdgeSide.RIGHT -> listOf(EdgeSide.RIGHT)
                    EdgeSide.BOTH -> listOf(EdgeSide.LEFT, EdgeSide.RIGHT)
                }
            }
            val edgeZoneWidth = (settings.alphabetSidePaddingDp + 32).coerceAtLeast(48).dp
            sides.forEach { side ->
                EdgeTouchZone(
                    side = side,
                    widthDp = edgeZoneWidth,
                    letters = listModel.letters,
                    band = band,
                    hapticsEnabled = settings.hapticsEnabled,
                    state = scrub,
                    onOpen = { appListVisible = true },
                    onDismiss = { closeAppList() },
                    modifier = Modifier.align(
                        if (side == EdgeSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd
                    ),
                )
            }
        }

        if (settings.dynamicButtonEnabled && !appListVisible && !homeEditMode && !showHomeOptions && lockTargetOffset == null) {
            val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val dynamicButtonBottom = maxOf(80.dp, navBarBottom + 68.dp)
            val isLeft = scrub.side == EdgeSide.LEFT
            val dynamicBtnAlignment = if (isLeft) Alignment.BottomStart else Alignment.BottomEnd

            val clickApp = settings.dynamicButtonClickApp?.let { appsByKey[it] }
            val swipeUpApp = settings.dynamicButtonSwipeUpApp?.let { appsByKey[it] }
            val swipeDownApp = settings.dynamicButtonSwipeDownApp?.let { appsByKey[it] }
            val dynamicBtnSidePadding = (settings.alphabetSidePaddingDp + 38).coerceAtLeast(58).dp

            DynamicActionButton(
                clickApp = clickApp,
                swipeUpApp = swipeUpApp,
                swipeDownApp = swipeDownApp,
                side = scrub.side,
                hapticsEnabled = settings.hapticsEnabled,
                contentColor = settings.contentColor,
                onLaunch = { appInfo, bounds ->
                    app.appRepository.launch(appInfo.componentName, sourceBounds = bounds, sourceView = view)
                },
                onOpenSettings = {
                    onNavigate("settings/dynamic_button")
                },
                modifier = Modifier
                    .align(dynamicBtnAlignment)
                    .padding(
                        start = if (isLeft) dynamicBtnSidePadding else 0.dp,
                        end = if (!isLeft) dynamicBtnSidePadding else 0.dp,
                        bottom = dynamicButtonBottom,
                    )
                    .zIndex(2f),
            )
        }

        HomeOptionsBottomSheet(
            visible = showHomeOptions,
            onDismiss = { showHomeOptions = false },
            onOpenSettings = { showHomeOptions = false; onNavigate("settings") },
            onOpenTaskManager = { showHomeOptions = false; onNavigate("task_manager") },
            onManageFavorites = { showHomeOptions = false; onNavigate("favorites") },
            onAddWidget = { showHomeOptions = false; widgetActions.onAddWidget() },
            onOpenClockStyle = { showHomeOptions = false; onNavigate("settings/clock") },
            onOpenThemes = { showHomeOptions = false; onNavigate("themes") },
            onOpenWallpaperPicker = { showHomeOptions = false; onNavigate("wallpapers") },
        )

        val hideStatusBar by app.prefs.hideStatusBar.collectAsState(initial = false)
        val insetsController = remember(view) {
            val window = (context as? android.app.Activity)?.window
            if (window != null) androidx.core.view.WindowCompat.getInsetsController(window, view) else null
        }

        LaunchedEffect(lockTargetOffset) {
            if (lockTargetOffset != null) {
                insetsController?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController?.show(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                val window = (context as? android.app.Activity)?.window
                if (window != null) {
                    dev.viagaralauncher.service.StatusBarFader.setVisible(window, !hideStatusBar)
                }
            }
        }

        lockTargetOffset?.let { target ->
            ScreenOffEffect(
                targetOffset = target,
                onAnimationEnd = {
                    scope.launch {
                        val locked = SystemUi.lockScreen()
                        if (!locked) {
                            lockTargetOffset = null
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_enable_accessibility_lock),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            delay(2000L)
                            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                lockTargetOffset = null
                            }
                        }
                    }
                },
            )
        }

        // Exibe o balão flutuante translúcido ("O que há de novo") apenas quando o usuário
        // solicita explicitamente (ao tocar na notificação do sistema), nunca automaticamente na tela inicial.
        val currentUpdate = updateInfo
        if (showChangelogRequested && currentUpdate != null && currentUpdate.hasUpdate) {
            UpdateChangelogDialog(
                update = currentUpdate,
                onDismissRequest = {
                    dev.viagaralauncher.update.UpdateManager.dismissChangelogRequest()
                },
                onDownload = { useRoot ->
                    dev.viagaralauncher.update.UpdateManager.dismissChangelogRequest()
                    dev.viagaralauncher.update.UpdateManager.startDownload(context, currentUpdate, autoInstallWithRoot = useRoot)
                },
            )
        }

        if (showDefaultLauncherDialog) {
            SetDefaultLauncherDialog(
                onDismissRequest = {
                    showDefaultLauncherDialog = false
                    scope.launch {
                        app.prefs.setHasPromptedDefaultLauncher(true)
                    }
                },
                onConfirm = {
                    showDefaultLauncherDialog = false
                    scope.launch {
                        app.prefs.setHasPromptedDefaultLauncher(true)
                    }
                    DefaultLauncherUtil.requestSetDefaultLauncher(context)
                },
            )
        }
    }
}

private fun iconPickerRoute(key: String) = "iconpicker/" + Uri.encode(key)

/** Display and behaviour settings the home destination reads, grouped so they travel as one. */
@Immutable
data class HomeSettings(
    val iconSizeDp: Int,
    val labelSizeSp: Int,
    val itemSpacingDp: Int,
    val sidePaddingDp: Int,
    val alphabetSidePaddingDp: Int = 20,
    val nowPlayingHeightDp: Int,
    val nowPlayingEnabled: Boolean,
    val edgeSide: EdgeSide,
    val alwaysShowAz: Boolean,
    val showAlphabet: Boolean,
    val alignRight: Boolean,
    val dimWallpaperAlpha: Float,
    val dimHomeAlpha: Float,
    val hapticsEnabled: Boolean,
    val showFavoriteLabels: Boolean,
    val doubleTapToLock: Boolean,
    val contentColor: Color,
    val clockStyle: ClockStyle = ClockStyle.CLASSIC,
    val showAppNotifications: Boolean = false,
    val folderWindowPopup: Boolean = true,
    val dynamicButtonEnabled: Boolean = true,
    val dynamicButtonClickApp: String? = null,
    val dynamicButtonSwipeUpApp: String? = null,
    val dynamicButtonSwipeDownApp: String? = null,
    val searchButtonEnabled: Boolean = true,
)