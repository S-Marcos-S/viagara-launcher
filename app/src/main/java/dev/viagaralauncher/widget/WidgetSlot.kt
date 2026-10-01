// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.widget

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.viagaralauncher.R
import dev.viagaralauncher.ViagaraApp
import dev.viagaralauncher.service.HapticUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class WidgetSlotActions(
    val onAddWidget: () -> Unit,
    val onRemoveWidget: (Int) -> Unit = {},
    val onWidgetSettings: (Int) -> Unit = {},
    val onAppInfo: (Int) -> Unit = {},
    val onResize: (Int) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

@Composable
fun WidgetSlot(
    widgetIds: List<Int>,
    heightDp: Int,
    hapticsEnabled: Boolean,
    onEditLayout: () -> Unit,
    actions: WidgetSlotActions,
    modifier: Modifier = Modifier,
    editMode: Boolean = false,
    contentColor: Color = Color.White,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val app = context.applicationContext as ViagaraApp
    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val density = LocalDensity.current
    val viewConfig = LocalViewConfiguration.current

    val validWidgetIds = remember(widgetIds) {
        widgetIds.filter { id ->
            id > 0 && runCatching { appWidgetManager.getAppWidgetInfo(id) != null }.getOrDefault(false)
        }
    }
    if (validWidgetIds.isEmpty()) return

    var menuExpanded by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var selectedMenuWidgetId by remember { mutableIntStateOf(-1) }

    var slotSizeDp by remember { mutableStateOf(0 to 0) }
    val reportedSizesDp = remember { mutableStateMapOf<Int, Pair<Int, Int>>() }

    var isResizing by remember { mutableStateOf(false) }
    var liveHeightDp by remember(heightDp) { mutableFloatStateOf(heightDp.toFloat()) }
    val effectiveHeightDp = if (isResizing) liveHeightDp.roundToInt() else heightDp

    // Page index tracking across widget list changes
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var previousIds by remember { mutableStateOf(validWidgetIds) }

    if (validWidgetIds != previousIds) {
        currentPageIndex = if (validWidgetIds.size > previousIds.size) {
            (validWidgetIds.size - 1).coerceAtLeast(0)
        } else {
            currentPageIndex.coerceIn(0, (validWidgetIds.size - 1).coerceAtLeast(0))
        }
        previousIds = validWidgetIds
    }

    // Active widget ID targeted by the contextual dropdown menu
    val activeWidgetId = remember(selectedMenuWidgetId, currentPageIndex, validWidgetIds) {
        if (selectedMenuWidgetId > 0 && selectedMenuWidgetId in validWidgetIds) {
            selectedMenuWidgetId
        } else {
            validWidgetIds.getOrNull(currentPageIndex) ?: validWidgetIds.firstOrNull() ?: -1
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            validWidgetIds.size == 1 -> {
                val singleId = validWidgetIds.first()
                val providerInfo = remember(singleId) { appWidgetManager.getAppWidgetInfo(singleId) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(effectiveHeightDp.dp),
                ) {
                    SingleWidgetView(
                        widgetId = singleId,
                        providerInfo = providerInfo,
                        slotSizeDp = slotSizeDp,
                        onSlotSizeChanged = { slotSizeDp = it },
                        reportedSizesDp = reportedSizesDp,
                        hapticsEnabled = hapticsEnabled,
                        onOpenMenu = { id, offset ->
                            selectedMenuWidgetId = id
                            menuOffset = offset
                            menuExpanded = true
                        },
                        onAddWidget = actions.onAddWidget,
                        app = app,
                        density = density,
                    )

                    if (isResizing) {
                        ResizeOverlay(
                            liveHeightDp = liveHeightDp,
                            onLiveHeightChange = { liveHeightDp = it },
                            onFinishResize = {
                                actions.onResize(liveHeightDp.roundToInt())
                                isResizing = false
                            },
                            hapticsEnabled = hapticsEnabled,
                            view = view,
                            density = density,
                        )
                    }

                    WidgetContextMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        offset = menuOffset,
                        activeWidgetId = singleId,
                        heightDp = heightDp,
                        onStartResize = {
                            liveHeightDp = heightDp.toFloat()
                            isResizing = true
                        },
                        onEditLayout = onEditLayout,
                        actions = actions,
                    )
                }
            }
            else -> {
                key(validWidgetIds) {
                    MultiWidgetContainer(
                        validWidgetIds = validWidgetIds,
                        initialPage = currentPageIndex,
                        effectiveHeightDp = effectiveHeightDp,
                        onPageChanged = { page -> currentPageIndex = page },
                        slotSizeDp = slotSizeDp,
                        onSlotSizeChanged = { slotSizeDp = it },
                        reportedSizesDp = reportedSizesDp,
                        hapticsEnabled = hapticsEnabled,
                        onOpenMenu = { id, offset ->
                            selectedMenuWidgetId = id
                            menuOffset = offset
                            menuExpanded = true
                        },
                        onAddWidget = actions.onAddWidget,
                        app = app,
                        appWidgetManager = appWidgetManager,
                        density = density,
                        viewConfig = viewConfig,
                        contentColor = contentColor,
                        editMode = editMode,
                        isResizing = isResizing,
                        liveHeightDp = liveHeightDp,
                        onLiveHeightChange = { liveHeightDp = it },
                        onFinishResize = {
                            actions.onResize(liveHeightDp.roundToInt())
                            isResizing = false
                        },
                        menuExpanded = menuExpanded,
                        onDismissMenu = { menuExpanded = false },
                        menuOffset = menuOffset,
                        activeWidgetId = activeWidgetId,
                        heightDp = heightDp,
                        onStartResize = {
                            liveHeightDp = heightDp.toFloat()
                            isResizing = true
                        },
                        onEditLayout = onEditLayout,
                        actions = actions,
                        view = view,
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiWidgetContainer(
    validWidgetIds: List<Int>,
    initialPage: Int,
    effectiveHeightDp: Int,
    onPageChanged: (Int) -> Unit,
    slotSizeDp: Pair<Int, Int>,
    onSlotSizeChanged: (Pair<Int, Int>) -> Unit,
    reportedSizesDp: MutableMap<Int, Pair<Int, Int>>,
    hapticsEnabled: Boolean,
    onOpenMenu: (Int, DpOffset) -> Unit,
    onAddWidget: () -> Unit,
    app: ViagaraApp,
    appWidgetManager: AppWidgetManager,
    density: Density,
    viewConfig: ViewConfiguration,
    contentColor: Color,
    editMode: Boolean,
    isResizing: Boolean,
    liveHeightDp: Float,
    onLiveHeightChange: (Float) -> Unit,
    onFinishResize: () -> Unit,
    menuExpanded: Boolean,
    onDismissMenu: () -> Unit,
    menuOffset: DpOffset,
    activeWidgetId: Int,
    heightDp: Int,
    onStartResize: () -> Unit,
    onEditLayout: () -> Unit,
    actions: WidgetSlotActions,
    view: View,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, (validWidgetIds.size - 1).coerceAtLeast(0)),
        pageCount = { validWidgetIds.size },
    )

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }

    var isSwiping by remember { mutableStateOf(false) }
    var showDots by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress, isSwiping, editMode) {
        if (isSwiping || pagerState.isScrollInProgress || editMode) {
            showDots = true
        } else {
            showDots = true
            delay(800)
            showDots = false
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(effectiveHeightDp.dp)
                .pointerInput(validWidgetIds.size, pagerState) {
                    val touchSlop = viewConfig.touchSlop
                    val pageCount = validWidgetIds.size
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                        val startPage = pagerState.currentPage
                        var isHorizontalDrag = false
                        var totalDx = 0f
                        var totalDy = 0f
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPointerInputChange(down)

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            velocityTracker.addPointerInputChange(change)

                            if (!change.pressed) {
                                if (isHorizontalDrag) {
                                    change.consume()
                                    isSwiping = false
                                    val velocityX = velocityTracker.calculateVelocity().x
                                    val currentOffset = (pagerState.currentPage - startPage) + pagerState.currentPageOffsetFraction

                                    val targetPage = when {
                                        velocityX < -600f -> (startPage + 1).coerceAtMost(pageCount - 1)
                                        velocityX > 600f -> (startPage - 1).coerceAtLeast(0)
                                        else -> {
                                            val sign = if (currentOffset >= 0f) 1 else -1
                                            val absOffset = abs(currentOffset)
                                            val wholePages = absOffset.toInt()
                                            val frac = absOffset - wholePages
                                            val extra = if (frac >= 0.25f) 1 else 0
                                            (startPage + sign * (wholePages + extra)).coerceIn(0, pageCount - 1)
                                        }
                                    }
                                    scope.launch {
                                        pagerState.animateScrollToPage(targetPage)
                                    }
                                }
                                break
                            }

                            val dx = change.position.x - change.previousPosition.x
                            val dy = change.position.y - change.previousPosition.y
                            totalDx += dx
                            totalDy += dy

                            if (!isHorizontalDrag) {
                                if (abs(totalDx) > touchSlop && abs(totalDx) > abs(totalDy) * 1.15f) {
                                    isHorizontalDrag = true
                                    isSwiping = true
                                } else if (abs(totalDy) > touchSlop && abs(totalDy) > abs(totalDx) * 1.15f) {
                                    break
                                }
                            }

                            if (isHorizontalDrag) {
                                change.consume()
                                pagerState.dispatchRawDelta(-dx)
                            }
                        }
                    }
                },
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 12.dp,
                key = { index -> validWidgetIds.getOrNull(index) ?: index },
            ) { pageIndex ->
                val currentId = validWidgetIds.getOrNull(pageIndex) ?: return@HorizontalPager
                val providerInfo: AppWidgetProviderInfo? = remember(currentId) {
                    appWidgetManager.getAppWidgetInfo(currentId)
                }

                val pageOffset = abs(
                    (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                ).coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val scale = 1f - 0.05f * pageOffset
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - 0.35f * pageOffset
                        },
                ) {
                    SingleWidgetView(
                        widgetId = currentId,
                        providerInfo = providerInfo,
                        slotSizeDp = slotSizeDp,
                        onSlotSizeChanged = onSlotSizeChanged,
                        reportedSizesDp = reportedSizesDp,
                        hapticsEnabled = hapticsEnabled,
                        onOpenMenu = onOpenMenu,
                        onAddWidget = onAddWidget,
                        app = app,
                        density = density,
                    )
                }
            }

            if (isResizing) {
                ResizeOverlay(
                    liveHeightDp = liveHeightDp,
                    onLiveHeightChange = onLiveHeightChange,
                    onFinishResize = onFinishResize,
                    hapticsEnabled = hapticsEnabled,
                    view = view,
                    density = density,
                )
            }

            WidgetContextMenu(
                expanded = menuExpanded,
                onDismissRequest = onDismissMenu,
                offset = menuOffset,
                activeWidgetId = activeWidgetId,
                heightDp = heightDp,
                onStartResize = onStartResize,
                onEditLayout = onEditLayout,
                actions = actions,
            )
        }

        Spacer(Modifier.height(6.dp))
        WidgetDotsIndicator(
            pageCount = validWidgetIds.size,
            pagerState = pagerState,
            contentColor = contentColor,
            visible = showDots || editMode,
            onSelectPage = { page ->
                scope.launch { pagerState.animateScrollToPage(page) }
            },
        )
    }
}

@Composable
private fun SingleWidgetView(
    widgetId: Int,
    providerInfo: AppWidgetProviderInfo?,
    slotSizeDp: Pair<Int, Int>,
    onSlotSizeChanged: (Pair<Int, Int>) -> Unit,
    reportedSizesDp: MutableMap<Int, Pair<Int, Int>>,
    hapticsEnabled: Boolean,
    onOpenMenu: (Int, DpOffset) -> Unit,
    onAddWidget: () -> Unit,
    app: ViagaraApp,
    density: Density,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (providerInfo != null) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        onSlotSizeChanged(with(density) { size.width.toDp().value.toInt() to size.height.toDp().value.toInt() })
                    },
                factory = { ctx ->
                    val hostView = runCatching {
                        app.widgetHost.createView(ctx, widgetId, providerInfo).apply {
                            setAppWidget(widgetId, providerInfo)
                            setPadding(0, 0, 0, 0)
                        }
                    }.getOrNull()
                    LongPressFrameLayout(ctx).apply {
                        clipChildren = false
                        clipToPadding = false
                        if (hostView != null) {
                            addView(
                                hostView,
                                android.widget.FrameLayout.LayoutParams(
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                ),
                            )
                        }
                        onLongPress = { x, y ->
                            HapticUtil.tick(this, hapticsEnabled)
                            onOpenMenu(widgetId, with(density) { DpOffset(x.toDp(), y.toDp()) })
                        }
                    }
                },
                update = { container ->
                    val hostView = container.getChildAt(0) as? AppWidgetHostView
                    runCatching {
                        hostView?.setPadding(0, 0, 0, 0)
                        hostView?.setAppWidget(widgetId, providerInfo)
                    }
                    val (wDp, hDp) = slotSizeDp
                    if (hostView != null && wDp > 0 && hDp > 0 && reportedSizesDp[widgetId] != slotSizeDp) {
                        reportedSizesDp[widgetId] = slotSizeDp
                        val options = runCatching {
                            AppWidgetManager.getInstance(container.context).getAppWidgetOptions(widgetId)
                        }.getOrNull() ?: Bundle()
                        runCatching {
                            hostView.updateAppWidgetSize(options, wDp, hDp, wDp, hDp)
                        }
                    }
                    container.onLongPress = { x, y ->
                        HapticUtil.tick(container, hapticsEnabled)
                        onOpenMenu(widgetId, with(density) { DpOffset(x.toDp(), y.toDp()) })
                    }
                },
            )
        } else {
            val localView = LocalView.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(16.dp),
                    )
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            HapticUtil.tick(localView, hapticsEnabled)
                            onOpenMenu(widgetId, DpOffset.Zero)
                        },
                        onLongClick = {
                            HapticUtil.tick(localView, hapticsEnabled)
                            onOpenMenu(widgetId, DpOffset.Zero)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.widget_unavailable),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.widget_tap_to_remove),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResizeOverlay(
    liveHeightDp: Float,
    onLiveHeightChange: (Float) -> Unit,
    onFinishResize: () -> Unit,
    hapticsEnabled: Boolean,
    view: View,
    density: Density,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.20f), RoundedCornerShape(16.dp)),
    ) {
        // Top drag handle
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp)
                .size(width = 44.dp, height = 12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.8f))
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        val deltaDp = with(density) { delta.toDp().value }
                        val newH = (liveHeightDp - deltaDp).coerceIn(70f, 800f)
                        if (newH.roundToInt() != liveHeightDp.roundToInt()) {
                            HapticUtil.tick(view, hapticsEnabled)
                        }
                        onLiveHeightChange(newH)
                    },
                    onDragStopped = { onFinishResize() },
                ),
        )

        // Center indicator badge with current height and done button
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${liveHeightDp.roundToInt()} dp",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onFinishResize,
                modifier = Modifier.size(24.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
            ) {
                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_done), modifier = Modifier.size(18.dp))
            }
        }

        // Bottom drag handle
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
                .size(width = 44.dp, height = 12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.8f))
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        val deltaDp = with(density) { delta.toDp().value }
                        val newH = (liveHeightDp + deltaDp).coerceIn(70f, 800f)
                        if (newH.roundToInt() != liveHeightDp.roundToInt()) {
                            HapticUtil.tick(view, hapticsEnabled)
                        }
                        onLiveHeightChange(newH)
                    },
                    onDragStopped = { onFinishResize() },
                ),
        )
    }
}

@Composable
private fun WidgetContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    offset: DpOffset,
    activeWidgetId: Int,
    heightDp: Int,
    onStartResize: () -> Unit,
    onEditLayout: () -> Unit,
    actions: WidgetSlotActions,
) {
    val context = LocalContext.current
    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val info = remember(activeWidgetId) {
        if (activeWidgetId > 0) runCatching { appWidgetManager.getAppWidgetInfo(activeWidgetId) }.getOrNull() else null
    }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest, offset = offset) {
        if (activeWidgetId > 0 && info != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_resize)) },
                leadingIcon = { Icon(Icons.Filled.OpenInFull, contentDescription = null) },
                onClick = {
                    onDismissRequest()
                    onStartResize()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_app_info)) },
                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                onClick = {
                    onDismissRequest()
                    actions.onAppInfo(activeWidgetId)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit_layout)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    onDismissRequest()
                    onEditLayout()
                },
            )
            if (info.configure != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.widget_change_settings)) },
                    leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                    onClick = {
                        onDismissRequest()
                        actions.onWidgetSettings(activeWidgetId)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.widget_add_custom)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = {
                    onDismissRequest()
                    actions.onAddWidget()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_remove)) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = {
                    onDismissRequest()
                    actions.onRemoveWidget(activeWidgetId)
                },
            )
        } else if (activeWidgetId > 0) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_remove)) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = {
                    onDismissRequest()
                    actions.onRemoveWidget(activeWidgetId)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.widget_add_custom)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = {
                    onDismissRequest()
                    actions.onAddWidget()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit_layout)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    onDismissRequest()
                    onEditLayout()
                },
            )
        } else {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.widget_add_custom)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = {
                    onDismissRequest()
                    actions.onAddWidget()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit_layout)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    onDismissRequest()
                    onEditLayout()
                },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_open_settings)) },
            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            onClick = {
                onDismissRequest()
                actions.onOpenSettings()
            },
        )
    }
}

/** Legacy overload maintaining compatibility with single widgetId callers. */
@Composable
fun WidgetSlot(
    widgetId: Int,
    heightDp: Int,
    hapticsEnabled: Boolean,
    onEditLayout: () -> Unit,
    actions: WidgetSlotActions,
    modifier: Modifier = Modifier,
    editMode: Boolean = false,
    contentColor: Color = Color.White,
) {
    val ids = if (widgetId > 0) listOf(widgetId) else emptyList()
    WidgetSlot(
        widgetIds = ids,
        heightDp = heightDp,
        hapticsEnabled = hapticsEnabled,
        onEditLayout = onEditLayout,
        actions = actions,
        modifier = modifier,
        editMode = editMode,
        contentColor = contentColor,
    )
}

@Composable
private fun WidgetDotsIndicator(
    pageCount: Int,
    pagerState: PagerState,
    contentColor: Color,
    visible: Boolean,
    modifier: Modifier = Modifier,
    onSelectPage: (Int) -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(250),
        label = "widgetDotsAlpha",
    )

    if (alpha > 0.01f && pageCount > 1) {
        Surface(
            modifier = modifier
                .graphicsLayer { this.alpha = alpha },
            color = Color.Black.copy(alpha = 0.35f),
            shape = RoundedCornerShape(10.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val currentProgress = pagerState.currentPage + pagerState.currentPageOffsetFraction
                val activeColor = contentColor.copy(alpha = 0.95f)
                val inactiveColor = contentColor.copy(alpha = 0.35f)

                for (i in 0 until pageCount) {
                    val distance = abs(currentProgress - i).coerceIn(0f, 1f)
                    val activeFraction = 1f - distance

                    val dotWidth = androidx.compose.ui.unit.lerp(6.dp, 16.dp, activeFraction)
                    val dotHeight = 6.dp
                    val dotColor = androidx.compose.ui.graphics.lerp(inactiveColor, activeColor, activeFraction)

                    Box(
                        modifier = Modifier
                            .size(width = dotWidth, height = dotHeight)
                            .clip(RoundedCornerShape(3.dp))
                            .background(dotColor)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelectPage(i) },
                            ),
                    )
                }
            }
        }
    }
}