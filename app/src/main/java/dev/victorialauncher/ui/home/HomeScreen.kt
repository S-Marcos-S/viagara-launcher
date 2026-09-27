// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.window.DialogWindowProvider
import dev.victorialauncher.data.DailyQuote
import dev.victorialauncher.data.DailyQuoteManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import dev.victorialauncher.data.EdgeSide
import dev.victorialauncher.ui.theme.dynamicBorderColor
import dev.victorialauncher.ui.theme.dynamicSurfaceColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlignHorizontalLeft
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import dev.victorialauncher.root.AppLogCaptureService
import dev.victorialauncher.root.AppUninstallManager
import dev.victorialauncher.ui.common.ConfirmUninstallDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import dev.victorialauncher.service.HapticUtil
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.ClockStyle
import dev.victorialauncher.data.Folder
import dev.victorialauncher.data.HomePaddings
import dev.victorialauncher.data.folderToken
import dev.victorialauncher.data.PaddingSlot
import dev.victorialauncher.media.NowPlayingWidget
import dev.victorialauncher.media.isListenerEnabled
import dev.victorialauncher.media.openNowPlayingApp
import dev.victorialauncher.ui.common.AppIcon
import dev.victorialauncher.ui.common.EditAppDialog
import dev.victorialauncher.ui.common.FolderIconImage
import dev.victorialauncher.ui.common.recordTouchPosition
import dev.victorialauncher.widget.WidgetSlot
import dev.victorialauncher.widget.WidgetSlotActions
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.annotation.StringRes
import dev.victorialauncher.R
import androidx.compose.ui.res.stringResource

private sealed interface HomeItem {
    data object Widget : HomeItem
    data class Favorite(val app: AppInfo) : HomeItem
    data class FolderItem(val folder: Folder) : HomeItem
}

/** A favorites row is either an app or a folder; both reorder through the same list. */
sealed interface FavoriteEntry {
    val token: String

    data class App(val app: AppInfo) : FavoriteEntry {
        override val token: String get() = app.key
    }

    data class FolderRef(val folder: Folder) : FavoriteEntry {
        override val token: String get() = folderToken(folder.id)
    }
}

private fun buildHomeItems(
    favorites: List<FavoriteEntry>,
    widgetPosition: Int,
    hasWidget: Boolean,
): List<HomeItem> {
    val pos = widgetPosition.coerceIn(0, favorites.size)
    val list = mutableListOf<HomeItem>()
    favorites.forEachIndexed { i, entry ->
        if (hasWidget && i == pos) list += HomeItem.Widget
        list += when (entry) {
            is FavoriteEntry.App -> HomeItem.Favorite(entry.app)
            is FavoriteEntry.FolderRef -> HomeItem.FolderItem(entry.folder)
        }
    }
    if (hasWidget && pos >= favorites.size) list += HomeItem.Widget
    return list
}

@Composable
fun HomeScreen(
    favorites: List<FavoriteEntry>,
    nameOverrides: Map<String, String>,
    iconSizeDp: Int,
    labelSizeSp: Int,
    itemSpacingDp: Int,
    sidePaddingDp: Int,
    onSetSidePadding: (Int) -> Unit,
    alphabetSidePaddingDp: Int = 20,
    onSetAlphabetSidePadding: (Int) -> Unit = {},
    paddings: HomePaddings,
    widgetId: Int = -1,
    widgetIds: List<Int> = emptyList(),
    widgetPosition: Int,
    widgetHeightDp: Int,
    hapticsEnabled: Boolean,
    nowPlayingEnabled: Boolean,
    nowPlayingHeightDp: Int,
    onResizeNowPlaying: (Int) -> Unit,
    widgetActions: WidgetSlotActions,
    onLaunch: (AppInfo) -> Unit,
    onRemoveFavorite: (AppInfo) -> Unit,
    onOpenFolderApp: (AppInfo) -> Unit,
    onRenameFolder: (Folder, String) -> Unit,
    onChangeFolderIcon: (Folder) -> Unit,
    onResetFolderIcon: (Folder) -> Unit,
    onDeleteFolder: (Folder) -> Unit,
    onManageFolder: (Folder) -> Unit,
    onMoveToFolder: (AppInfo) -> Unit,
    onRemoveFromFolder: (Folder, AppInfo) -> Unit,
    appsByKey: Map<String, AppInfo>,
    onReorderHome: (newFavoriteKeys: List<String>, newWidgetPosition: Int) -> Unit,
    onCommitPadding: (PaddingSlot, Int) -> Unit,
    onResetAlignments: () -> Unit = {},
    onFavoritesBoundsChanged: (topPx: Float, bottomPx: Float) -> Unit,
    nowPlayingHasContent: Boolean,
    onDismissPermissionPrompt: () -> Unit = {},
    contentColor: Color,
    showFavoriteLabels: Boolean,
    alignRight: Boolean,
    editMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    onPeekStatusBar: () -> Unit,
    onExpandShade: () -> Unit,
    onManageFavorites: () -> Unit,
    onSetName: (AppInfo, String?) -> Unit,
    onChangeIcon: (AppInfo) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHomeOptions: () -> Unit = {},
    showAppNotifications: Boolean = false,
    folderWindowPopup: Boolean = true,
    notificationsByPackage: Map<String, List<dev.victorialauncher.notification.AppNotificationItem>> = emptyMap(),
    clockStyle: ClockStyle = ClockStyle.CLASSIC,
    doubleTapToLock: Boolean = false,
    onDoubleTapLock: (Offset) -> Unit = {},
    edgeSide: EdgeSide = EdgeSide.RIGHT,
    alwaysShowAz: Boolean = true,
) {
    fun displayName(app: AppInfo) = nameOverrides[app.key] ?: app.label

    // Automatic alignment calculation:
    // When the alphabet is visible on an edge, content insets by at least (alphabetSidePaddingDp + 32) dp,
    // leaving a balanced 8dp breathing gutter next to the alphabet column (24dp wide + alphabetSidePaddingDp).
    // On edges without the alphabet, content aligns symmetrically at sidePaddingDp.
    val hasAlphabetStart = alwaysShowAz && (edgeSide == EdgeSide.LEFT || edgeSide == EdgeSide.BOTH)
    val hasAlphabetEnd = alwaysShowAz && (edgeSide == EdgeSide.RIGHT || edgeSide == EdgeSide.BOTH)
    val alphabetClearance = (alphabetSidePaddingDp + 32).dp
    val contentStart = if (hasAlphabetStart) maxOf(sidePaddingDp.dp, alphabetClearance) else sidePaddingDp.dp
    val contentEnd = if (hasAlphabetEnd) maxOf(sidePaddingDp.dp, alphabetClearance) else sidePaddingDp.dp

    var activeDialogNotification by remember { mutableStateOf<Pair<dev.victorialauncher.notification.AppNotificationItem, AppInfo>?>(null) }
    var floatingFolderDialog by remember { mutableStateOf<Folder?>(null) }
    var appMenuFor by remember { mutableStateOf<AppInfo?>(null) }
    var rootInspectorFor by remember { mutableStateOf<AppInfo?>(null) }
    var menuForKey by remember { mutableStateOf<String?>(null) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var renameDialogFor by remember { mutableStateOf<AppInfo?>(null) }
    var confirmUninstallFor by remember { mutableStateOf<AppInfo?>(null) }
    var folderMenuFor by remember { mutableStateOf<String?>(null) }
    var folderRenameFor by remember { mutableStateOf<Folder?>(null) }
    var nowPlayingMenu by remember { mutableStateOf(false) }
    var nowPlayingMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var expandedFolders by remember { mutableStateOf(setOf<String>()) }
    val touchPosition = remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val view = LocalView.current

    // While a padding handle is being dragged we track it locally so layout follows the
    // finger, and only write the final value to storage on release.
    var liveSlot by remember { mutableStateOf<PaddingSlot?>(null) }
    var liveValue by remember { mutableIntStateOf(0) }
    fun padOf(slot: PaddingSlot): Int = if (liveSlot == slot) liveValue else paddings[slot]

    val colorScheme = MaterialTheme.colorScheme
    val handleResetAlignments = {
        HapticUtil.tick(view, hapticsEnabled)
        onSetSidePadding(20)
        onSetAlphabetSidePadding(20)
        onCommitPadding(PaddingSlot.NOW_PLAYING_TOP, dev.victorialauncher.data.HomePaddings.Default.nowPlayingTop)
        onCommitPadding(PaddingSlot.NOW_PLAYING_BOTTOM, dev.victorialauncher.data.HomePaddings.Default.nowPlayingBottom)
        onCommitPadding(PaddingSlot.WIDGET_TOP, dev.victorialauncher.data.HomePaddings.Default.widgetTop)
        onCommitPadding(PaddingSlot.WIDGET_BOTTOM, dev.victorialauncher.data.HomePaddings.Default.widgetBottom)
        onCommitPadding(PaddingSlot.FAVORITES_TOP, dev.victorialauncher.data.HomePaddings.Default.favoritesTop)
        onCommitPadding(PaddingSlot.FAVORITES_BOTTOM, dev.victorialauncher.data.HomePaddings.Default.favoritesBottom)
        onResetAlignments()
        liveSlot = null
        android.widget.Toast.makeText(
            context,
            context.getString(R.string.toast_aligned_elements),
            android.widget.Toast.LENGTH_SHORT,
        ).show()
    }

    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val effectiveWidgetIds = remember(widgetIds, widgetId) {
        val raw = if (widgetIds.isNotEmpty()) widgetIds.filter { it > 0 }
        else if (widgetId > 0) listOf(widgetId)
        else emptyList()
        raw.filter { id ->
            runCatching { appWidgetManager.getAppWidgetInfo(id) != null }.getOrDefault(false)
        }
    }
    val hasWidget = effectiveWidgetIds.isNotEmpty()
    val showWidgetSlot = hasWidget

    LaunchedEffect(widgetIds, widgetId) {
        val raw = if (widgetIds.isNotEmpty()) widgetIds.filter { it > 0 }
        else if (widgetId > 0) listOf(widgetId)
        else emptyList()
        val hasInvalid = raw.any { id ->
            runCatching { appWidgetManager.getAppWidgetInfo(id) == null }.getOrDefault(true)
        }
        if (hasInvalid) {
            val app = context.applicationContext as dev.victorialauncher.VictoriaApp
            app.prefs.pruneInvalidWidgetIds { id ->
                runCatching { appWidgetManager.getAppWidgetInfo(id) != null }.getOrDefault(false)
            }
        }
    }

    var dragOrder by remember { mutableStateOf<List<HomeItem>?>(null) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val itemHeights = remember { mutableStateMapOf<Int, Int>() }

    val homeItems = remember(favorites) {
        favorites.map { entry ->
            when (entry) {
                is FavoriteEntry.App -> HomeItem.Favorite(entry.app)
                is FavoriteEntry.FolderRef -> HomeItem.FolderItem(entry.folder)
            }
        }
    }
    val displayItems = dragOrder ?: homeItems

    // Folders sit alongside apps in the favorites block, so both bound its padding.
    val firstRowIndex = displayItems.indexOfFirst { it !is HomeItem.Widget }
    val lastRowIndex = displayItems.indexOfLast { it !is HomeItem.Widget }

    fun tokensOf(items: List<HomeItem>) = items.mapNotNull {
        when (it) {
            is HomeItem.Favorite -> it.app.key
            is HomeItem.FolderItem -> folderToken(it.folder.id)
            HomeItem.Widget -> null
        }
    }

    fun commitDragOrder() {
        val order = dragOrder
        if (order != null) {
            val widgetPos = order.indexOfFirst { it is HomeItem.Widget }
                .let { if (it < 0) widgetPosition else it }
            onReorderHome(tokensOf(order), widgetPos)
        }
        dragOrder = null
        draggingIndex = null
        dragOffset = 0f
    }

    /** Swap with the neighbour once the held row has travelled past its midpoint. */
    fun onDragBy(amount: Float) {
        val order = dragOrder ?: return
        val from = draggingIndex ?: return
        dragOffset += amount

        val below = itemHeights[from + 1]
        if (below != null && from + 1 <= order.lastIndex && dragOffset > below / 2f) {
            dragOrder = order.toMutableList().apply { add(from + 1, removeAt(from)) }
            draggingIndex = from + 1
            dragOffset -= below
            return
        }
        val above = itemHeights[from - 1]
        if (above != null && from - 1 >= 0 && dragOffset < -above / 2f) {
            dragOrder = order.toMutableList().apply { add(from - 1, removeAt(from)) }
            draggingIndex = from - 1
            dragOffset += above
        }
    }

    // Measured span of the favorites list, handed up so the A-Z strip can match it.
    var favBoundsTop by remember { mutableFloatStateOf(0f) }
    var favBoundsBottom by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(favBoundsTop, favBoundsBottom) {
        if (favBoundsBottom > favBoundsTop) onFavoritesBoundsChanged(favBoundsTop, favBoundsBottom)
    }

    val peekPullPx = with(density) { 30.dp.toPx() }
    val deepPullPx = with(density) { 200.dp.toPx() }
    var rawPullDown by remember { mutableFloatStateOf(0f) }
    var pullActionFired by remember { mutableStateOf(false) }
    var peekFired by remember { mutableStateOf(false) }

    // Free vertical drag with spring bounce at both ends, outside edit mode.
    val offsetY = remember { Animatable(0f) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    var contentHeight by remember { mutableIntStateOf(0) }
    val minOffset = remember(viewportHeight, contentHeight) {
        minOf(0f, (viewportHeight - contentHeight).toFloat())
    }
    val editScrollState = rememberScrollState()

    val handleDoubleTapLock: (() -> Unit)? = if (doubleTapToLock) {
        {
            val pos = if (touchPosition.value != Offset.Zero) {
                touchPosition.value
            } else {
                Offset(with(density) { 180.dp.toPx() }, viewportHeight / 2f)
            }
            onDoubleTapLock(pos)
        }
    } else null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportHeight = it.height }
            .recordTouchPosition(touchPosition)
            .draggable(
                orientation = Orientation.Vertical,
                enabled = !editMode && liveSlot == null,
                onDragStarted = { rawPullDown = 0f; pullActionFired = false; peekFired = false },
                state = rememberDraggableState { delta ->
                    // Undamped downward travel once already at the top drives the status-bar
                    // gestures; the damped offset would need more than a screen of dragging.
                    if (delta > 0f && offsetY.value >= -2f) {
                        rawPullDown += delta
                        if (!pullActionFired && rawPullDown > deepPullPx) {
                            pullActionFired = true
                            onExpandShade()
                        } else if (!peekFired && rawPullDown > peekPullPx) {
                            peekFired = true
                            onPeekStatusBar()
                        }
                    }
                    scope.launch {
                        val next = offsetY.value + delta
                        val outOfBounds = next > 0f || next < minOffset
                        val applied = if (outOfBounds) offsetY.value + delta * 0.4f else next
                        offsetY.snapTo(applied)
                    }
                },
                onDragStopped = { velocity ->
                    if (!pullActionFired && rawPullDown > peekPullPx && velocity > 2200f) {
                        onExpandShade()
                    }
                    rawPullDown = 0f
                    pullActionFired = false
                    peekFired = false

                    val decay = exponentialDecay<Float>(frictionMultiplier = 1.6f)
                    val target = decay.calculateTargetValue(offsetY.value, velocity)
                        .coerceIn(minOffset, 0f)
                    offsetY.animateTo(
                        targetValue = target,
                        initialVelocity = velocity,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                },
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !editMode,
                onClick = {},
                onDoubleClick = handleDoubleTapLock,
                onLongClick = {
                    HapticUtil.tick(view, hapticsEnabled)
                    onOpenHomeOptions()
                },
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    // Edit mode roughly doubles the stack's height and its handles are
                    // themselves drag targets, so it needs ordinary scrolling.
                    if (editMode) {
                        Modifier.verticalScroll(editScrollState)
                    } else {
                        // Unbounded: the stack is dragged rather than scrolled here, so it
                        // has to be allowed to measure taller than the screen. Without this
                        // anything past the bottom edge is squashed into the leftover space.
                        Modifier
                            .wrapContentHeight(align = Alignment.Top, unbounded = true)
                            .offset { IntOffset(0, offsetY.value.roundToInt()) }
                    }
                )
                .onSizeChanged { contentHeight = it.height },
        ) {
            if (editMode) {
                val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val editTopPadding = maxOf(statusBarTop, 24.dp) + 8.dp

                Spacer(Modifier.height(editTopPadding))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = colorScheme.surfaceContainer.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, dynamicBorderColor().copy(alpha = 0.45f)),
                    tonalElevation = 4.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Linha 1: Título "Editar layout" à esquerda e Botão "Concluído" à direita (sempre cabe em uma só linha)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Tune,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = colorScheme.onPrimaryContainer,
                                    )
                                }
                                Text(
                                    stringResource(R.string.action_edit_layout),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface,
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = colorScheme.primary,
                                modifier = Modifier.clickable { onEditModeChange(false) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.Done,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = colorScheme.onPrimary,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.action_done),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onPrimary,
                                    )
                                }
                            }
                        }

                        // Linha 2: Botão para alinhar elementos
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { handleResetAlignments() },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.AlignHorizontalLeft,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = colorScheme.primary,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.action_align_elements),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurface,
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.home_reorder_hint),
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                SidePaddingHandle(
                    sidePaddingDp = sidePaddingDp,
                    onSetSidePadding = onSetSidePadding,
                    labelRes = R.string.handle_side_padding,
                )
                Spacer(Modifier.height(6.dp))
                SidePaddingHandle(
                    sidePaddingDp = alphabetSidePaddingDp,
                    onSetSidePadding = onSetAlphabetSidePadding,
                    labelRes = R.string.handle_alphabet_side_padding,
                )
                Spacer(Modifier.height(6.dp))
            }

            if (!editMode) {
                // 2.5 cm from the top of the screen
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CLOCK_TOP_PADDING_DP)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                            onDoubleClick = handleDoubleTapLock,
                            onLongClick = {
                                HapticUtil.tick(view, hapticsEnabled)
                                onOpenHomeOptions()
                            },
                        )
                )

                NiagaraClockWidget(
                    clockStyle = clockStyle,
                    contentColor = contentColor,
                    sidePaddingDp = sidePaddingDp,
                    alignRight = alignRight,
                    startPaddingDp = contentStart.value.toInt(),
                    endPaddingDp = contentEnd.value.toInt(),
                )

                // Widget slot placed between clock and favorites - only if a widget is added
                if (hasWidget) {
                    Spacer(Modifier.height(14.dp))
                    WidgetSlot(
                        widgetIds = effectiveWidgetIds,
                        heightDp = widgetHeightDp,
                        hapticsEnabled = hapticsEnabled,
                        onEditLayout = { onEditModeChange(true) },
                        actions = widgetActions,
                        editMode = editMode,
                        contentColor = contentColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = contentStart, end = contentEnd),
                    )
                }

                // Space favorites to begin from the middle of the screen downwards
                val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
                val hasMultipleWidgets = effectiveWidgetIds.size > 1
                val widgetOccupied = if (hasWidget) widgetHeightDp.dp + 14.dp + (if (hasMultipleWidgets) 16.dp else 0.dp) else 0.dp
                val favoritesTopSpacer = (screenHeightDp * 0.50f - CLOCK_TOP_PADDING_DP - 90.dp - widgetOccupied).coerceAtLeast(16.dp)

                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(favoritesTopSpacer)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                            onDoubleClick = handleDoubleTapLock,
                            onLongClick = {
                                HapticUtil.tick(view, hapticsEnabled)
                                onOpenHomeOptions()
                            },
                        )
                )
            } else {
                if (hasWidget) {
                    PaddingHandle(
                        editMode = true,
                        label = R.string.handle_widget_top,
                        value = padOf(PaddingSlot.WIDGET_TOP),
                        onDrag = { d -> liveSlot = PaddingSlot.WIDGET_TOP; liveValue = d },
                        current = { padOf(PaddingSlot.WIDGET_TOP) },
                        onCommit = { onCommitPadding(PaddingSlot.WIDGET_TOP, it); liveSlot = null },
                    )
                    WidgetSlot(
                        widgetIds = effectiveWidgetIds,
                        heightDp = widgetHeightDp,
                        hapticsEnabled = hapticsEnabled,
                        onEditLayout = { onEditModeChange(true) },
                        actions = widgetActions,
                        editMode = true,
                        contentColor = contentColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = contentStart, end = contentEnd),
                    )
                    Spacer(Modifier.height(6.dp))
                    HeightHandle(
                        label = R.string.handle_widget_height,
                        heightDp = widgetHeightDp,
                        range = 80..900,
                        onResize = { widgetActions.onResize(it) },
                    )
                    PaddingHandle(
                        editMode = true,
                        label = R.string.handle_widget_bottom,
                        value = padOf(PaddingSlot.WIDGET_BOTTOM),
                        onDrag = { d -> liveSlot = PaddingSlot.WIDGET_BOTTOM; liveValue = d },
                        current = { padOf(PaddingSlot.WIDGET_BOTTOM) },
                        onCommit = { onCommitPadding(PaddingSlot.WIDGET_BOTTOM, it); liveSlot = null },
                    )
                }
            }

            // Now Playing widget block: rendered below clock / widget and above favorites
            if (nowPlayingHasContent || (editMode && nowPlayingEnabled)) {
                NowPlayingBlock(
                    editMode = editMode,
                    heightDp = nowPlayingHeightDp,
                    iconSizeDp = iconSizeDp,
                    labelSizeSp = labelSizeSp,
                    contentColor = contentColor,
                    sidePaddingDp = sidePaddingDp,
                    alignRight = alignRight,
                    contentStart = contentStart,
                    contentEnd = contentEnd,
                    padTop = padOf(PaddingSlot.NOW_PLAYING_TOP),
                    padBottom = padOf(PaddingSlot.NOW_PLAYING_BOTTOM),
                    touchPosition = touchPosition,
                    menuExpanded = nowPlayingMenu,
                    menuOffset = nowPlayingMenuOffset,
                    onOpenMenu = { offset -> nowPlayingMenuOffset = offset; nowPlayingMenu = true },
                    onDismissMenu = { nowPlayingMenu = false },
                    onEditLayout = { nowPlayingMenu = false; onEditModeChange(true) },
                    onOpenSettings = { nowPlayingMenu = false; onOpenSettings() },
                    onResize = onResizeNowPlaying,
                    onDragPadding = { slot, v -> liveSlot = slot; liveValue = v },
                    currentPadding = { slot -> padOf(slot) },
                    onCommitPadding = { slot, v -> onCommitPadding(slot, v); liveSlot = null },
                    onDismissPermissionPrompt = onDismissPermissionPrompt,
                )
            }

            if (displayItems.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.home_no_favorites_title), color = contentColor, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.home_no_favorites_body),
                        color = contentColor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onManageFavorites) { Text(stringResource(R.string.home_choose_favorites)) }
                }
            }

            displayItems.forEachIndexed { index, item ->
                // Spacing handles live outside the draggable wrapper: inside it they would
                // travel with a dragged row and skew the height the swap threshold uses.
                if (item is HomeItem.Widget) {
                    PaddingHandle(
                        editMode = editMode,
                        label = R.string.handle_widget_top,
                        value = padOf(PaddingSlot.WIDGET_TOP),
                        onDrag = { d -> liveSlot = PaddingSlot.WIDGET_TOP; liveValue = d },
                        current = { padOf(PaddingSlot.WIDGET_TOP) },
                        onCommit = { onCommitPadding(PaddingSlot.WIDGET_TOP, it); liveSlot = null },
                    )
                } else if (index == firstRowIndex) {
                    PaddingHandle(
                        editMode = editMode,
                        label = R.string.handle_favorites_top,
                        value = padOf(PaddingSlot.FAVORITES_TOP),
                        onDrag = { d -> liveSlot = PaddingSlot.FAVORITES_TOP; liveValue = d },
                        current = { padOf(PaddingSlot.FAVORITES_TOP) },
                        onCommit = { onCommitPadding(PaddingSlot.FAVORITES_TOP, it); liveSlot = null },
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            itemHeights[index] = coords.size.height
                            if (item !is HomeItem.Widget) {
                                val top = coords.positionInWindow().y
                                if (index == firstRowIndex) favBoundsTop = top
                                if (index == lastRowIndex) favBoundsBottom = top + coords.size.height
                            }
                        }
                        .zIndex(if (draggingIndex == index) 1f else 0f)
                        .graphicsLayer {
                            if (draggingIndex == index) {
                                translationY = dragOffset
                                scaleX = 1.03f
                                scaleY = 1.03f
                                alpha = 0.9f
                            }
                        }
                        .then(
                            if (editMode) {
                                Modifier.pointerInput(index, displayItems.size) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            dragOrder = displayItems
                                            draggingIndex = index
                                            dragOffset = 0f
                                        },
                                        onDragEnd = { commitDragOrder() },
                                        onDragCancel = { commitDragOrder() },
                                    ) { change, amount ->
                                        change.consume()
                                        onDragBy(amount.y)
                                    }
                                }
                            } else {
                                Modifier
                            }
                        ),
                ) {
                    when (item) {
                        HomeItem.Widget -> if (hasWidget) {
                            WidgetSlot(
                                widgetIds = effectiveWidgetIds,
                                heightDp = widgetHeightDp,
                                hapticsEnabled = hapticsEnabled,
                                onEditLayout = { onEditModeChange(true) },
                                actions = widgetActions,
                                editMode = editMode,
                                contentColor = contentColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = contentStart, end = contentEnd),
                            )
                        }

                        is HomeItem.FolderItem -> FolderRow(
                            folder = item.folder,
                            members = item.folder.apps.mapNotNull { appsByKey[it] },
                            expanded = item.folder.id in expandedFolders,
                            editMode = editMode,
                            iconSizeDp = iconSizeDp,
                            labelSizeSp = labelSizeSp,
                            sidePaddingDp = sidePaddingDp,
                            contentColor = contentColor,
                            showLabels = showFavoriteLabels,
                            alignRight = alignRight,
                            contentStart = contentStart,
                            contentEnd = contentEnd,
                            menuExpanded = folderMenuFor == item.folder.id,
                            menuOffset = menuOffset,
                            touchPosition = touchPosition,
                            displayName = { displayName(it) },
                            onToggleExpanded = {
                                if (folderWindowPopup) {
                                    floatingFolderDialog = item.folder
                                } else {
                                    expandedFolders = if (item.folder.id in expandedFolders) {
                                        expandedFolders - item.folder.id
                                    } else {
                                        expandedFolders + item.folder.id
                                    }
                                }
                            },
                            onOpenMenu = { offset -> menuOffset = offset; folderMenuFor = item.folder.id },
                            onDismissMenu = { folderMenuFor = null },
                            onManage = { folderMenuFor = null; onManageFolder(item.folder) },
                            onEdit = { folderMenuFor = null; folderRenameFor = item.folder },
                            onEditLayout = { folderMenuFor = null; onEditModeChange(true) },
                            onDelete = { folderMenuFor = null; onDeleteFolder(item.folder) },
                            onOpenApp = onOpenFolderApp,
                            onRemoveApp = { member -> onRemoveFromFolder(item.folder, member) },
                        )

                        is HomeItem.Favorite -> {
                            val appNotifications = if (showAppNotifications) {
                                notificationsByPackage[item.app.packageName].orEmpty()
                            } else emptyList()
                            val latestNotification = appNotifications.firstOrNull()

                            FavoriteRow(
                                app = item.app,
                                label = displayName(item.app),
                                editMode = editMode,
                                iconSizeDp = iconSizeDp,
                                labelSizeSp = labelSizeSp,
                                sidePaddingDp = sidePaddingDp,
                                contentColor = contentColor,
                                showLabels = showFavoriteLabels,
                                alignRight = alignRight,
                                contentStart = contentStart,
                                contentEnd = contentEnd,
                                notification = latestNotification,
                                onNotificationClick = { notif ->
                                    activeDialogNotification = notif to item.app
                                },
                                menuExpanded = false,
                                menuOffset = menuOffset,
                                touchPosition = touchPosition,
                                onLaunch = { onLaunch(item.app) },
                                onOpenMenu = { _ -> appMenuFor = item.app },
                                onDismissMenu = { menuForKey = null },
                                onMoveToFolder = { menuForKey = null; onMoveToFolder(item.app) },
                                onEditLayout = { menuForKey = null; onEditModeChange(true) },
                                onAppInfo = { menuForKey = null; onAppInfo(item.app) },
                                onRemove = { menuForKey = null; onRemoveFavorite(item.app) },
                                onEditIconName = { menuForKey = null; renameDialogFor = item.app },
                                onOpenSettings = { menuForKey = null; onOpenSettings() },
                            )
                        }
                    }
                }

                if (item is HomeItem.Widget) {
                    if (editMode) {
                        Spacer(Modifier.height(6.dp))
                        HeightHandle(
                            label = R.string.handle_widget_height,
                            heightDp = widgetHeightDp,
                            range = 80..900,
                            onResize = { widgetActions.onResize(it) },
                        )
                    }
                    PaddingHandle(
                        editMode = editMode,
                        label = R.string.handle_widget_bottom,
                        value = padOf(PaddingSlot.WIDGET_BOTTOM),
                        onDrag = { d -> liveSlot = PaddingSlot.WIDGET_BOTTOM; liveValue = d },
                        current = { padOf(PaddingSlot.WIDGET_BOTTOM) },
                        onCommit = { onCommitPadding(PaddingSlot.WIDGET_BOTTOM, it); liveSlot = null },
                    )
                } else if (index == lastRowIndex) {
                    PaddingHandle(
                        editMode = editMode,
                        label = R.string.handle_favorites_bottom,
                        value = padOf(PaddingSlot.FAVORITES_BOTTOM),
                        onDrag = { d -> liveSlot = PaddingSlot.FAVORITES_BOTTOM; liveValue = d },
                        current = { padOf(PaddingSlot.FAVORITES_BOTTOM) },
                        onCommit = { onCommitPadding(PaddingSlot.FAVORITES_BOTTOM, it); liveSlot = null },
                    )
                } else {
                    Spacer(Modifier.height(itemSpacingDp.dp))
                }
            }

            val showBottomDailyQuote = clockStyle != ClockStyle.DAILY_REFLECTION && clockStyle != ClockStyle.DAILY_REFLECTION_STATS

            if (showBottomDailyQuote) {
                // Spacer separating favorites from the daily quote
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                            onDoubleClick = handleDoubleTapLock,
                            onLongClick = {
                                HapticUtil.tick(view, hapticsEnabled)
                                onOpenHomeOptions()
                            },
                        )
                )

                val todayQuote = remember { DailyQuoteManager.getQuoteForToday() }

                BottomDailyQuoteBlock(
                    quote = todayQuote,
                    contentColor = contentColor,
                    contentStart = contentStart,
                    contentEnd = contentEnd,
                    alignRight = alignRight,
                    hapticsEnabled = hapticsEnabled,
                )

                // Bottom breathing space for smooth drag up and spring bounce
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                            onDoubleClick = handleDoubleTapLock,
                            onLongClick = {
                                HapticUtil.tick(view, hapticsEnabled)
                                onOpenHomeOptions()
                            },
                        )
                )
            } else {
                // Empty space below favorites when quote is displayed in clock widget
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                            onDoubleClick = handleDoubleTapLock,
                            onLongClick = {
                                HapticUtil.tick(view, hapticsEnabled)
                                onOpenHomeOptions()
                            },
                        )
                )
            }

            if (editMode) {
                val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                Spacer(Modifier.height(navBarBottom + 64.dp))
            }
        }
    }

    folderRenameFor?.let { folder ->
        FolderEditDialog(
            currentName = folder.name,
            hasCustomIcon = folder.icon != null,
            onConfirm = { name -> onRenameFolder(folder, name); folderRenameFor = null },
            onChangeIcon = { onChangeFolderIcon(folder); folderRenameFor = null },
            onResetIcon = { onResetFolderIcon(folder); folderRenameFor = null },
            onDismiss = { folderRenameFor = null },
        )
    }

    floatingFolderDialog?.let { folder ->
        FolderFloatingDialog(
            folder = folder,
            members = folder.apps.mapNotNull { appsByKey[it] },
            iconSizeDp = iconSizeDp,
            labelSizeSp = labelSizeSp,
            displayName = { displayName(it) },
            onOpenApp = onOpenFolderApp,
            onManageFolder = { onManageFolder(folder) },
            onDismissRequest = { floatingFolderDialog = null },
            onAppLongClick = { app ->
                floatingFolderDialog = null
                appMenuFor = app
            },
        )
    }

    appMenuFor?.let { app ->
        val isFavorite = favorites.any { it is FavoriteEntry.App && it.app.key == app.key }
        val isRootAvailable = remember { dev.victorialauncher.update.RootInstaller.isRootAvailable() }
        val menuItems = buildList {
            add(
                dev.victorialauncher.ui.common.AppMenuItem(
                    title = stringResource(R.string.action_move_to_folder),
                    icon = Icons.Filled.Folder,
                    onClick = { onMoveToFolder(app) },
                )
            )
            if (isRootAvailable) {
                add(
                    dev.victorialauncher.ui.common.AppMenuItem(
                        title = stringResource(R.string.action_root_inspect_app),
                        icon = Icons.Filled.Terminal,
                        onClick = { rootInspectorFor = app },
                    )
                )
                val isCurrentlyCapturing = AppLogCaptureService.isCapturingApp(app.packageName)
                add(
                    dev.victorialauncher.ui.common.AppMenuItem(
                        title = stringResource(if (isCurrentlyCapturing) R.string.action_stop_save_logs else R.string.action_capture_logs),
                        icon = Icons.Filled.BugReport,
                        onClick = {
                            if (isCurrentlyCapturing) {
                                AppLogCaptureService.saveLog(context)
                            } else {
                                AppLogCaptureService.startCapture(context, app.packageName, displayName(app))
                            }
                        },
                    )
                )
            }
            add(
                dev.victorialauncher.ui.common.AppMenuItem(
                    title = stringResource(R.string.action_edit_layout),
                    icon = Icons.Filled.Edit,
                    onClick = { onEditModeChange(true) },
                )
            )
            add(
                dev.victorialauncher.ui.common.AppMenuItem(
                    title = stringResource(R.string.action_app_info),
                    icon = Icons.Filled.Info,
                    onClick = { onAppInfo(app) },
                )
            )
            add(
                dev.victorialauncher.ui.common.AppMenuItem(
                    title = stringResource(R.string.action_edit_icon_and_name),
                    icon = Icons.Filled.Tune,
                    onClick = { renameDialogFor = app },
                )
            )
            add(
                dev.victorialauncher.ui.common.AppMenuItem(
                    title = stringResource(R.string.action_open_settings),
                    icon = Icons.Filled.Settings,
                    onClick = onOpenSettings,
                )
            )
            add(
                dev.victorialauncher.ui.common.AppMenuItem(
                    title = stringResource(R.string.action_remove),
                    icon = Icons.Filled.Delete,
                    onClick = { onRemoveFavorite(app) },
                    isDestructive = true,
                )
            )
            if (app.packageName != context.packageName) {
                add(
                    dev.victorialauncher.ui.common.AppMenuItem(
                        title = stringResource(R.string.action_uninstall),
                        icon = Icons.Filled.DeleteForever,
                        onClick = {
                            val isRoot = AppUninstallManager.isRootAvailable()
                            val isSystem = AppUninstallManager.isSystemApp(context, app.packageName)
                            if (isRoot) {
                                if (isSystem) {
                                    confirmUninstallFor = app
                                } else {
                                    val appName = displayName(app)
                                    Toast.makeText(context, context.getString(R.string.uninstall_toast_progress, appName), Toast.LENGTH_SHORT).show()
                                    scope.launch {
                                        val result = AppUninstallManager.uninstallViaRoot(app.packageName)
                                        if (result.isSuccess) {
                                            Toast.makeText(context, context.getString(R.string.uninstall_toast_success, appName), Toast.LENGTH_SHORT).show()
                                        } else {
                                            val error = result.exceptionOrNull()?.message ?: ""
                                            Toast.makeText(context, context.getString(R.string.uninstall_toast_failed, appName, error), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            } else {
                                AppUninstallManager.uninstallStandard(context, app.packageName)
                            }
                        },
                        isDestructive = true,
                    )
                )
            }
        }

        dev.victorialauncher.ui.common.AppMenuDialog(
            app = app,
            displayName = displayName(app),
            onDismissRequest = { appMenuFor = null },
            items = menuItems,
        )
    }

    confirmUninstallFor?.let { target ->
        val isSystem = remember(target.packageName) { AppUninstallManager.isSystemApp(context, target.packageName) }
        val isRoot = remember { AppUninstallManager.isRootAvailable() }
        ConfirmUninstallDialog(
            app = target,
            displayName = displayName(target),
            isSystemApp = isSystem,
            onDismissRequest = { confirmUninstallFor = null },
            onConfirm = {
                confirmUninstallFor = null
                val appName = displayName(target)
                if (isRoot) {
                    Toast.makeText(context, context.getString(R.string.uninstall_toast_progress, appName), Toast.LENGTH_SHORT).show()
                    scope.launch {
                        val result = AppUninstallManager.uninstallViaRoot(target.packageName)
                        if (result.isSuccess) {
                            Toast.makeText(context, context.getString(R.string.uninstall_toast_success, appName), Toast.LENGTH_SHORT).show()
                        } else {
                            val error = result.exceptionOrNull()?.message ?: ""
                            Toast.makeText(context, context.getString(R.string.uninstall_toast_failed, appName, error), Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    AppUninstallManager.uninstallStandard(context, target.packageName)
                }
            },
        )
    }

    rootInspectorFor?.let { target ->
        dev.victorialauncher.ui.root.AppRootInspectorDialog(
            app = target,
            displayName = displayName(target),
            onDismissRequest = { rootInspectorFor = null },
        )
    }

    renameDialogFor?.let { app ->
        EditAppDialog(
            currentName = displayName(app),
            onConfirmName = { name -> onSetName(app, name); renameDialogFor = null },
            onChangeIcon = { onChangeIcon(app); renameDialogFor = null },
            onDismiss = { renameDialogFor = null },
        )
    }

    activeDialogNotification?.let { (notif, app) ->
        val appNotifications = notificationsByPackage[app.packageName].orEmpty()
        dev.victorialauncher.ui.notification.NotificationDetailDialog(
            item = notif,
            allNotifications = appNotifications,
            appInfo = app,
            appName = displayName(app),
            onDismissRequest = { activeDialogNotification = null },
            onOpen = { targetNotif ->
                activeDialogNotification = null
                dev.victorialauncher.notification.NotificationBus.launchNotification(
                    context = context,
                    item = targetNotif,
                    appInfo = app,
                    onLaunchFallback = { onLaunch(app) },
                )
            },
            onDismissNotification = { targetNotif ->
                dev.victorialauncher.notification.NotificationBus.dismissNotification(targetNotif.key)
                val remaining = notificationsByPackage[app.packageName].orEmpty().filter { it.key != targetNotif.key }
                if (remaining.isEmpty()) {
                    activeDialogNotification = null
                } else {
                    activeDialogNotification = remaining.first() to app
                }
            },
        )
    }
}

/** One favorite: icon, optional name, press highlight and its context menu. */
@Composable
private fun FavoriteRow(
    app: AppInfo,
    label: String,
    editMode: Boolean,
    iconSizeDp: Int,
    labelSizeSp: Int,
    sidePaddingDp: Int,
    contentColor: Color,
    showLabels: Boolean,
    alignRight: Boolean,
    menuExpanded: Boolean,
    menuOffset: DpOffset,
    touchPosition: MutableState<Offset>,
    onLaunch: () -> Unit,
    onOpenMenu: (DpOffset) -> Unit,
    onDismissMenu: () -> Unit,
    onMoveToFolder: () -> Unit,
    onEditLayout: () -> Unit,
    onAppInfo: () -> Unit,
    onRemove: () -> Unit,
    onEditIconName: () -> Unit,
    onOpenSettings: () -> Unit,
    notification: dev.victorialauncher.notification.AppNotificationItem? = null,
    onNotificationClick: (dev.victorialauncher.notification.AppNotificationItem) -> Unit = {},
    contentStart: Dp = sidePaddingDp.dp,
    contentEnd: Dp = sidePaddingDp.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val density = LocalDensity.current

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = (contentStart - 8.dp).coerceAtLeast(0.dp),
                    end = (contentEnd - 8.dp).coerceAtLeast(0.dp),
                )
                .background(
                    color = if (pressed) contentColor.copy(alpha = 0.15f) else Color.Transparent,
                    shape = RoundedCornerShape(18.dp),
                )
                .then(
                    // In edit mode the row must not claim the long press, or the reorder
                    // drag above it never starts.
                    if (editMode) {
                        Modifier
                    } else {
                        Modifier
                            .recordTouchPosition(touchPosition)
                            .combinedClickable(
                                interactionSource = interaction,
                                indication = null,
                                onClick = onLaunch,
                                onLongClick = {
                                    onOpenMenu(
                                        with(density) {
                                            DpOffset(touchPosition.value.x.toDp(), touchPosition.value.y.toDp())
                                        }
                                    )
                                },
                            )
                    }
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val notificationContent: @Composable () -> Unit = {
                if (notification != null) {
                    val notifText = if (notification.messages.isNotEmpty()) {
                        val lastMsg = notification.messages.last()
                        val countSuffix = if (notification.messages.size > 1) " (${notification.messages.size})" else ""
                        if (notification.title.isNotBlank()) {
                            "${notification.title}: ${lastMsg.text}$countSuffix"
                        } else {
                            "${lastMsg.text}$countSuffix"
                        }
                    } else if (notification.text.isNotBlank()) {
                        "${notification.title}: ${notification.text}"
                    } else {
                        notification.title
                    }
                    Text(
                        text = notifText,
                        color = contentColor.copy(alpha = 0.65f),
                        fontSize = (labelSizeSp - 3).coerceAtLeast(11).sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        textAlign = if (alignRight) TextAlign.End else TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onNotificationClick(notification) },
                            ),
                    )
                }
            }

            if (alignRight) {
                if (showLabels) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            label,
                            color = contentColor,
                            fontSize = labelSizeSp.sp,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        notificationContent()
                    }
                    Spacer(Modifier.width(16.dp))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                AppIcon(app = app, sizeDp = iconSizeDp)
            } else {
                AppIcon(app = app, sizeDp = iconSizeDp)
                if (showLabels) {
                    Spacer(Modifier.width(16.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            label,
                            color = contentColor,
                            fontSize = labelSizeSp.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        notificationContent()
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu, offset = menuOffset) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_move_to_folder)) },
                leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                onClick = onMoveToFolder,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit_layout)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = onEditLayout,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_app_info)) },
                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                onClick = onAppInfo,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_remove)) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = onRemove,
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit_icon_and_name)) },
                leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                onClick = onEditIconName,
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_open_settings)) },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = onOpenSettings,
            )
        }
    }
}

/** A folder row, which expands in place to show its apps. */
@Composable
private fun FolderRow(
    folder: Folder,
    members: List<AppInfo>,
    expanded: Boolean,
    editMode: Boolean,
    iconSizeDp: Int,
    labelSizeSp: Int,
    sidePaddingDp: Int,
    contentColor: Color,
    showLabels: Boolean,
    alignRight: Boolean,
    menuExpanded: Boolean,
    menuOffset: DpOffset,
    touchPosition: MutableState<Offset>,
    displayName: (AppInfo) -> String,
    onToggleExpanded: () -> Unit,
    onOpenMenu: (DpOffset) -> Unit,
    onDismissMenu: () -> Unit,
    onManage: () -> Unit,
    onEdit: () -> Unit,
    onEditLayout: () -> Unit,
    onDelete: () -> Unit,
    onOpenApp: (AppInfo) -> Unit,
    onRemoveApp: (AppInfo) -> Unit,
    contentStart: Dp = sidePaddingDp.dp,
    contentEnd: Dp = sidePaddingDp.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val density = LocalDensity.current

    Column {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = (contentStart - 8.dp).coerceAtLeast(0.dp),
                        end = (contentEnd - 8.dp).coerceAtLeast(0.dp),
                    )
                    .background(
                        color = if (pressed) contentColor.copy(alpha = 0.15f) else Color.Transparent,
                        shape = RoundedCornerShape(18.dp),
                    )
                    .then(
                        if (editMode) {
                            Modifier
                        } else {
                            Modifier
                                .recordTouchPosition(touchPosition)
                                .combinedClickable(
                                    interactionSource = interaction,
                                    indication = null,
                                    onClick = onToggleExpanded,
                                    onLongClick = {
                                        onOpenMenu(
                                            with(density) {
                                                DpOffset(touchPosition.value.x.toDp(), touchPosition.value.y.toDp())
                                            }
                                        )
                                    },
                                )
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!alignRight) FolderIcon(members, iconSizeDp, contentColor, folder.icon)
                if (showLabels) {
                    if (!alignRight) Spacer(Modifier.width(16.dp))
                    Text(
                        folder.name,
                        color = contentColor,
                        fontSize = labelSizeSp.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = if (alignRight) TextAlign.End else TextAlign.Start,
                    )
                    Text(
                        "${members.size}",
                        color = contentColor.copy(alpha = 0.5f),
                        fontSize = (labelSizeSp - 3).coerceAtLeast(9).sp,
                    )
                    if (alignRight) Spacer(Modifier.width(16.dp))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (alignRight) FolderIcon(members, iconSizeDp, contentColor, folder.icon)
            }

            DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu, offset = menuOffset) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_folder_choose_apps)) },
                    leadingIcon = { Icon(Icons.Filled.Checklist, contentDescription = null) },
                    onClick = onManage,
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit_icon_and_name)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = onEdit,
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit_layout)) },
                    leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                    onClick = onEditLayout,
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_folder_delete)) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = onDelete,
                )
            }
        }

        // Members expand in place rather than opening a separate screen.
        AnimatedVisibility(visible = expanded && !editMode) {
            val folderSubStart = if (!alignRight) contentStart + 24.dp else contentStart
            val folderSubEnd = if (alignRight) contentEnd + 24.dp else contentEnd
            Column {
                members.forEach { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = folderSubStart, end = folderSubEnd)
                            .combinedClickable(
                                onClick = { onOpenApp(member) },
                                onLongClick = { onRemoveApp(member) },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app = member, sizeDp = (iconSizeDp * 0.8f).toInt())
                        if (showLabels) {
                            Spacer(Modifier.width(16.dp))
                            Text(displayName(member), color = contentColor, fontSize = labelSizeSp.sp)
                        }
                    }
                }
                if (members.isEmpty()) {
                    Text(
                        stringResource(R.string.home_folder_empty),
                        color = contentColor.copy(alpha = 0.6f),
                        fontSize = (labelSizeSp - 3).coerceAtLeast(10).sp,
                        modifier = Modifier.padding(start = folderSubStart, end = folderSubEnd, top = 4.dp, bottom = 4.dp),
                    )
                }
            }
        }
    }
}

/** Now Playing with its own padding handles and height handle. */
@Composable
private fun NowPlayingBlock(
    editMode: Boolean,
    heightDp: Int,
    iconSizeDp: Int,
    labelSizeSp: Int,
    contentColor: Color,
    sidePaddingDp: Int,
    alignRight: Boolean,
    padTop: Int,
    padBottom: Int,
    touchPosition: MutableState<Offset>,
    menuExpanded: Boolean,
    menuOffset: DpOffset,
    onOpenMenu: (DpOffset) -> Unit,
    onDismissMenu: () -> Unit,
    onEditLayout: () -> Unit,
    onOpenSettings: () -> Unit,
    onResize: (Int) -> Unit,
    onDragPadding: (PaddingSlot, Int) -> Unit,
    currentPadding: (PaddingSlot) -> Int,
    onCommitPadding: (PaddingSlot, Int) -> Unit,
    contentStart: Dp = sidePaddingDp.dp,
    contentEnd: Dp = sidePaddingDp.dp,
    onDismissPermissionPrompt: () -> Unit = {},
) {
    val density = LocalDensity.current
    val context = LocalContext.current

    PaddingHandle(
        editMode = editMode,
        label = R.string.handle_now_playing_top,
        value = padTop,
        onDrag = { onDragPadding(PaddingSlot.NOW_PLAYING_TOP, it) },
        current = { currentPadding(PaddingSlot.NOW_PLAYING_TOP) },
        onCommit = { onCommitPadding(PaddingSlot.NOW_PLAYING_TOP, it) },
    )
    Box {
        NowPlayingWidget(
            heightDp = heightDp,
            iconSizeDp = iconSizeDp,
            labelSizeSp = labelSizeSp,
            contentColor = contentColor,
            alignRight = alignRight,
            editMode = editMode,
            onDismissPermissionPrompt = onDismissPermissionPrompt,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = contentStart, end = contentEnd)
                .recordTouchPosition(touchPosition)
                .combinedClickable(
                    // The transport buttons consume their own taps, so this is only ever the
                    // card itself — which should get you to what is playing. Say so when it
                    // can't, rather than leaving a tap that looks ignored.
                    onClick = {
                        if (isListenerEnabled(context)) {
                            if (!openNowPlayingApp(context)) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.now_playing_open_failed),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    onLongClick = {
                        onOpenMenu(
                            with(density) {
                                DpOffset(touchPosition.value.x.toDp(), touchPosition.value.y.toDp())
                            }
                        )
                    },
                ),
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu, offset = menuOffset) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit_layout)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = onEditLayout,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_open_settings)) },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = onOpenSettings,
            )
        }
    }
    if (editMode) {
        Spacer(Modifier.height(6.dp))
        HeightHandle(
            label = R.string.handle_now_playing_height,
            heightDp = heightDp,
            range = 48..220,
            onResize = onResize,
        )
    }
    PaddingHandle(
        editMode = editMode,
        label = R.string.handle_now_playing_bottom,
        value = padBottom,
        onDrag = { onDragPadding(PaddingSlot.NOW_PLAYING_BOTTOM, it) },
        current = { currentPadding(PaddingSlot.NOW_PLAYING_BOTTOM) },
        onCommit = { onCommitPadding(PaddingSlot.NOW_PLAYING_BOTTOM, it) },
    )
}
@Composable
private fun FolderIcon(
    members: List<AppInfo>,
    sizeDp: Int,
    contentColor: Color,
    iconOverride: String?,
) {
    var drewOverride = false
    if (iconOverride != null) {
        drewOverride = FolderIconImage(iconOverride, sizeDp)
    }
    if (drewOverride) return

    val preview = members.take(4)
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .background(contentColor.copy(alpha = 0.12f), RoundedCornerShape(sizeDp.dp / 4)),
        contentAlignment = Alignment.Center,
    ) {
        if (preview.isEmpty()) {
            Icon(
                Icons.Filled.Folder,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.7f),
                modifier = Modifier.size((sizeDp * 0.55f).dp),
            )
        } else {
            val cell = (sizeDp * 0.36f).toInt()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                preview.chunked(2).forEach { rowApps ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        rowApps.forEach { AppIcon(app = it, sizeDp = cell) }
                    }
                }
            }
        }
    }
}

/** Folders get the same treatment as apps: their own name and their own icon. */
@Composable
private fun FolderEditDialog(
    currentName: String,
    hasCustomIcon: Boolean,
    onConfirm: (String) -> Unit,
    onChangeIcon: () -> Unit,
    onResetIcon: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentName) }
    val view = LocalView.current

    DisposableEffect(view) {
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dialogWindow != null) {
            dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            val params = dialogWindow.attributes
            params.blurBehindRadius = 32
            dialogWindow.attributes = params
        }
        onDispose {}
    }

    val colorScheme = MaterialTheme.colorScheme

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.scrim.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .clip(RoundedCornerShape(28.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                color = dynamicSurfaceColor(),
                border = androidx.compose.foundation.BorderStroke(1.dp, dynamicBorderColor()),
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = null,
                                tint = colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.action_edit_icon_and_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(stringResource(R.string.home_folder_name_label)) },
                        shape = RoundedCornerShape(16.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outline,
                            focusedTextColor = colorScheme.onSurface,
                            unfocusedTextColor = colorScheme.onSurface,
                            focusedLabelColor = colorScheme.primary,
                            unfocusedLabelColor = colorScheme.onSurfaceVariant,
                            cursorColor = colorScheme.primary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onChangeIcon,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colorScheme.primary,
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.50f)),
                        ) {
                            Text(
                                text = stringResource(R.string.action_change_icon),
                                fontWeight = FontWeight.Medium,
                            )
                        }

                        if (hasCustomIcon) {
                            TextButton(
                                onClick = onResetIcon,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.home_folder_use_previews),
                                    color = colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.action_cancel),
                                color = colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        Button(
                            onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primary,
                                contentColor = colorScheme.onPrimary,
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.action_save),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Drag sideways to inset elements equally from the screen edges. */
@Composable
private fun SidePaddingHandle(
    sidePaddingDp: Int,
    onSetSidePadding: (Int) -> Unit,
    @StringRes labelRes: Int = R.string.handle_side_padding,
) {
    val density = LocalDensity.current
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colorScheme.surfaceContainer.copy(alpha = 0.75f))
            .border(BorderStroke(1.dp, dynamicBorderColor().copy(alpha = 0.35f)), RoundedCornerShape(10.dp))
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    val deltaDp = with(density) { delta.toDp().value }
                    onSetSidePadding((sidePaddingDp + deltaDp).roundToInt().coerceIn(0, 96))
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(labelRes, sidePaddingDp),
            color = colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Drag bar for an element's own height, shown below it in edit mode. */
@Composable
private fun HeightHandle(
    @StringRes label: Int,
    heightDp: Int,
    range: IntRange,
    onResize: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val colorScheme = MaterialTheme.colorScheme
    var currentHeight by remember(heightDp) { mutableFloatStateOf(heightDp.toFloat()) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colorScheme.surfaceContainer.copy(alpha = 0.75f))
            .border(BorderStroke(1.dp, dynamicBorderColor().copy(alpha = 0.35f)), RoundedCornerShape(10.dp))
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    val deltaDp = with(density) { delta.toDp().value }
                    currentHeight = (currentHeight + deltaDp).coerceIn(range.first.toFloat(), range.last.toFloat())
                    onResize(currentHeight.roundToInt())
                },
                onDragStopped = {
                    onResize(currentHeight.roundToInt())
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.handle_vertical, stringResource(label), currentHeight.roundToInt()),
            color = colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * A block of vertical space that becomes a drag handle in edit mode, so spacing above and
 * below each element is set by dragging it rather than by a slider.
 */
@Composable
private fun PaddingHandle(
    editMode: Boolean,
    @StringRes label: Int,
    value: Int,
    current: () -> Int,
    onDrag: (Int) -> Unit,
    onCommit: (Int) -> Unit,
) {
    if (!editMode) {
        Spacer(Modifier.height(value.dp))
        return
    }

    val density = LocalDensity.current
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(maxOf(value, 30).dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colorScheme.surfaceContainer.copy(alpha = 0.65f))
            .border(BorderStroke(1.dp, dynamicBorderColor().copy(alpha = 0.35f)), RoundedCornerShape(10.dp))
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    val deltaDp = with(density) { delta.toDp().value }
                    onDrag((current() + deltaDp).roundToInt().coerceIn(0, 400))
                },
                onDragStopped = { onCommit(current()) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.handle_vertical, stringResource(label), value),
            color = colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun BottomDailyQuoteBlock(
    quote: DailyQuote,
    contentColor: Color,
    contentStart: Dp,
    contentEnd: Dp,
    alignRight: Boolean,
    hapticsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = contentStart, end = contentEnd),
    ) {
        val widthFactor = (maxWidth / 340.dp).coerceIn(0.70f, 1.0f)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        HapticUtil.tick(view, hapticsEnabled)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Reflexão do dia", "\"${quote.quote}\"\n— ${quote.author}"))
                        Toast.makeText(context, context.getString(R.string.quote_copied), Toast.LENGTH_SHORT).show()
                    },
                )
                .padding(vertical = 6.dp),
            horizontalAlignment = if (alignRight) Alignment.End else Alignment.Start,
        ) {
            Text(
                text = "“${quote.quote}”",
                color = contentColor.copy(alpha = 0.9f),
                fontSize = (15 * widthFactor).sp,
                fontStyle = FontStyle.Italic,
                lineHeight = (21 * widthFactor).sp,
                textAlign = if (alignRight) TextAlign.End else TextAlign.Start,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "— ${quote.author}",
                color = contentColor.copy(alpha = 0.6f),
                fontSize = (13 * widthFactor).sp,
                fontWeight = FontWeight.Medium,
                textAlign = if (alignRight) TextAlign.End else TextAlign.Start,
            )
        }
    }
}