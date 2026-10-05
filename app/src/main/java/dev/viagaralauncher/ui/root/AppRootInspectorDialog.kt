// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.root

import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.viagaralauncher.R
import dev.viagaralauncher.data.AppInfo
import dev.viagaralauncher.root.AppBatteryInspectionData
import dev.viagaralauncher.root.AppInspectionData
import dev.viagaralauncher.root.AppProcessStatus
import dev.viagaralauncher.root.AppRootInspector
import dev.viagaralauncher.root.PowerImpactLevel
import dev.viagaralauncher.ui.common.AppIcon
import dev.viagaralauncher.ui.theme.dynamicBorderColor
import dev.viagaralauncher.ui.theme.dynamicSurfaceColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private enum class InspectorTab {
    MEMORY,
    NETWORK,
    BATTERY,
    PERMISSIONS,
    ACTIONS
}

private enum class DataPeriod {
    TODAY,
    LAST_7_DAYS,
    TOTAL,
}

/**
 * High-precision, professional root inspection dialog for individual apps.
 * Displays live process telemetry, memory allocations (Dalvik, Native, Graphics),
 * network sockets, active background services, and quick root administrative actions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppRootInspectorDialog(
    app: AppInfo,
    displayName: String,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var inspectionData by remember { mutableStateOf<AppInspectionData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(InspectorTab.MEMORY) }
    var selectedPeriod by remember { mutableStateOf(DataPeriod.TODAY) }
    var showLogViewer by remember { mutableStateOf(false) }

    var isAutoRefreshEnabled by remember { mutableStateOf(true) }

    // Floating window state & bounds
    var isMinimized by remember { mutableStateOf(false) }
    var windowOffsetX by remember { mutableFloatStateOf(0f) }
    var windowOffsetY by remember { mutableFloatStateOf(0f) }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val maxBubbleX = (screenWidthPx - with(density) { 72.dp.toPx() }).coerceAtLeast(0f)
    val maxBubbleY = (screenHeightPx - with(density) { 80.dp.toPx() }).coerceAtLeast(0f)

    val defaultBubbleX = with(density) { 20.dp.toPx() }
    val defaultBubbleY = with(density) { 110.dp.toPx() }

    var bubbleOffsetX by remember { mutableFloatStateOf(defaultBubbleX) }
    var bubbleOffsetY by remember { mutableFloatStateOf(defaultBubbleY) }

    fun refreshData(showIndicator: Boolean = false) {
        scope.launch {
            if (showIndicator) isRefreshing = true
            val result = AppRootInspector.inspectApp(context, app.packageName)
            inspectionData = result.getOrNull()
            isLoading = false
            isRefreshing = false
        }
    }

    LaunchedEffect(app.packageName, isAutoRefreshEnabled) {
        if (!isAutoRefreshEnabled) {
            refreshData(showIndicator = false)
            return@LaunchedEffect
        }
        while (isActive) {
            val result = AppRootInspector.inspectApp(context, app.packageName)
            inspectionData = result.getOrNull()
            isLoading = false
            delay(2000L)
        }
    }

    DisposableEffect(view) {
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window
        if (dialogWindow != null) {
            dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            dialogWindow.setDimAmount(0f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val params = dialogWindow.attributes
                params.blurBehindRadius = 36
                dialogWindow.attributes = params
            }
        }
        onDispose {}
    }

    if (!isMinimized) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.94f)
                        .fillMaxHeight(0.89f)
                        .offset { IntOffset(windowOffsetX.roundToInt(), (windowOffsetY - with(density) { 8.dp.toPx() }).roundToInt()) }
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, dynamicBorderColor(), RoundedCornerShape(24.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}, // Prevents click-through dismiss
                        ),
                    color = dynamicSurfaceColor().copy(alpha = 0.96f),
                    tonalElevation = 8.dp,
                    shadowElevation = 16.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        // Drag handle pill at the top (draggable!)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 5.dp)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        windowOffsetX += dragAmount.x
                                        windowOffsetY += dragAmount.y
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(38.dp)
                                    .height(3.5.dp)
                                    .clip(CircleShape)
                                    .background(colorScheme.onSurface.copy(alpha = 0.22f)),
                            )
                        }

                        // Header: Icon + Name + Status Badge + Top Action Buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        windowOffsetX += dragAmount.x
                                        windowOffsetY += dragAmount.y
                                    }
                                },
                            verticalAlignment = Alignment.Top,
                        ) {
                            AppIcon(
                                app = app,
                                sizeDp = 38,
                                modifier = Modifier
                                    .padding(top = 1.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                            )

                            Spacer(Modifier.width(10.dp))

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Top,
                            ) {
                            Text(
                                text = displayName,
                                color = colorScheme.onSurface,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = app.packageName,
                                color = colorScheme.onSurface.copy(alpha = 0.55f),
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(3.dp))

                            // Status Badge + AO VIVO badge
                            val currentStatus = inspectionData?.status ?: AppProcessStatus.STOPPED
                            val badgeColor = when (currentStatus) {
                                AppProcessStatus.FOREGROUND -> Color(0xFF10B981) // Emerald Green
                                AppProcessStatus.BACKGROUND -> Color(0xFF3B82F6) // Electric Blue
                                AppProcessStatus.STOPPED -> colorScheme.onSurface.copy(alpha = 0.45f)
                            }
                            val badgeText = when (currentStatus) {
                                AppProcessStatus.FOREGROUND -> stringResource(R.string.root_inspector_status_foreground)
                                AppProcessStatus.BACKGROUND -> stringResource(R.string.root_inspector_status_background)
                                AppProcessStatus.STOPPED -> stringResource(R.string.root_inspector_status_stopped)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(badgeColor.copy(alpha = 0.12f))
                                        .padding(horizontal = 5.dp, vertical = 1.5.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(badgeColor),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = badgeText,
                                        color = badgeColor,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.3.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }

                                if (isAutoRefreshEnabled && currentStatus != AppProcessStatus.STOPPED) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(Color(0xFF10B981).copy(alpha = 0.12f))
                                            .padding(horizontal = 5.dp, vertical = 1.5.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF10B981)),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "AO VIVO",
                                            color = Color(0xFF10B981),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.3.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                }
                            }
                        }

                        // Top action buttons: compact and positioned higher up
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            IconButton(
                                onClick = { refreshData(showIndicator = true) },
                                enabled = !isRefreshing,
                                modifier = Modifier.size(28.dp),
                            ) {
                                if (isRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        strokeWidth = 2.dp,
                                        color = colorScheme.primary,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = stringResource(R.string.action_reset),
                                        tint = colorScheme.onSurface.copy(alpha = 0.75f),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }

                            IconButton(
                                onClick = { showLogViewer = true },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.BugReport,
                                    contentDescription = stringResource(R.string.action_inspect_logs),
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }

                            IconButton(
                                onClick = { isMinimized = true },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Remove,
                                    contentDescription = "Minimizar",
                                    tint = colorScheme.onSurface.copy(alpha = 0.75f),
                                    modifier = Modifier.size(16.dp),
                                )
                            }

                            IconButton(
                                onClick = onDismissRequest,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.action_close),
                                    tint = colorScheme.onSurface.copy(alpha = 0.75f),
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = colorScheme.primary,
                                    strokeWidth = 3.dp,
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.root_inspector_loading),
                                    color = colorScheme.onSurface.copy(alpha = 0.65f),
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    } else {
                        val data = inspectionData

                        if (data == null) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Falha ao coletar dados com superusuário.",
                                    color = colorScheme.error,
                                    fontSize = 13.sp,
                                )
                            }
                        } else {
                            // Hardware Metrics: CPU & RAM (compact, name left, value right)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                MetricCard(
                                    modifier = Modifier.weight(1f),
                                    title = stringResource(R.string.root_inspector_cpu),
                                    value = "${String.format(Locale.US, "%.1f", data.totalCpuPercent)}%",
                                    icon = Icons.Filled.Speed,
                                    accentColor = if (data.totalCpuPercent > 0.0) Color(0xFFF59E0B) else colorScheme.onSurface.copy(alpha = 0.5f),
                                )
                                MetricCard(
                                    modifier = Modifier.weight(1f),
                                    title = stringResource(R.string.root_inspector_ram),
                                    value = "${String.format(Locale.US, "%.1f", data.ramTotalMb)} MB",
                                    icon = Icons.Filled.Memory,
                                    accentColor = if (data.ramTotalMb > 0) Color(0xFF6366F1) else colorScheme.onSurface.copy(alpha = 0.5f),
                                )
                            }

                            Spacer(Modifier.height(5.dp))

                            // Dedicated Real-time Speed Cards: Download & Upload (compact, name left, value right)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                RealtimeSpeedCard(
                                    modifier = Modifier.weight(1f),
                                    title = stringResource(R.string.root_inspector_speed_download),
                                    speedBps = data.rxSpeedBps,
                                    isDownload = true,
                                )
                                RealtimeSpeedCard(
                                    modifier = Modifier.weight(1f),
                                    title = stringResource(R.string.root_inspector_speed_upload),
                                    speedBps = data.txSpeedBps,
                                    isDownload = false,
                                )
                            }

                            Spacer(Modifier.height(5.dp))

                            // Native Data Usage Card with Period Selector (Hoje / 7 Dias / Total)
                            DataUsageCard(
                                data = data,
                                selectedPeriod = selectedPeriod,
                                onPeriodSelect = { selectedPeriod = it },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Spacer(Modifier.height(6.dp))

                            // Tab selector row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                TabButton(
                                    title = "Memória e Processos",
                                    icon = Icons.Filled.Memory,
                                    selected = selectedTab == InspectorTab.MEMORY,
                                    onClick = { selectedTab = InspectorTab.MEMORY },
                                    modifier = Modifier.weight(1f),
                                )
                                TabButton(
                                    title = "Ações",
                                    icon = Icons.Filled.Settings,
                                    selected = selectedTab == InspectorTab.ACTIONS,
                                    onClick = { selectedTab = InspectorTab.ACTIONS },
                                    modifier = Modifier.weight(1f),
                                )
                                TabButton(
                                    title = stringResource(R.string.root_inspector_tab_network),
                                    icon = Icons.Filled.NetworkCheck,
                                    selected = selectedTab == InspectorTab.NETWORK,
                                    onClick = { selectedTab = InspectorTab.NETWORK },
                                    modifier = Modifier.weight(1f),
                                )
                                TabButton(
                                    title = stringResource(R.string.root_inspector_tab_battery),
                                    icon = Icons.Filled.BatteryChargingFull,
                                    selected = selectedTab == InspectorTab.BATTERY,
                                    onClick = { selectedTab = InspectorTab.BATTERY },
                                    modifier = Modifier.weight(1f),
                                )
                                TabButton(
                                    title = stringResource(R.string.root_inspector_tab_permissions),
                                    icon = Icons.Filled.Security,
                                    selected = selectedTab == InspectorTab.PERMISSIONS,
                                    onClick = { selectedTab = InspectorTab.PERMISSIONS },
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            // Scrollable Tab Body (significantly expanded area for processes, memory, etc.)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colorScheme.onSurface.copy(alpha = 0.04f))
                                    .border(1.dp, colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                ) {
                                    when (selectedTab) {
                                        InspectorTab.MEMORY -> {
                                            MemoryTabContent(data)
                                            Spacer(Modifier.height(16.dp))
                                            Text("Processos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
                                            Spacer(Modifier.height(8.dp))
                                            ProcessesTabContent(
                                                data = data,
                                                onKillPid = { pid ->
                                                    scope.launch {
                                                        val killed = AppRootInspector.killProcess(pid)
                                                        if (killed) {
                                                            Toast.makeText(context, context.getString(R.string.root_inspector_toast_kill_success, pid), Toast.LENGTH_SHORT).show()
                                                            refreshData(showIndicator = true)
                                                        } else {
                                                            Toast.makeText(context, context.getString(R.string.root_inspector_toast_kill_error), Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                            )
                                        }
                                        InspectorTab.ACTIONS -> {
                                            ActionsTabContent(
                                                app = app,
                                                context = context,
                                                scope = scope,
                                                onRefreshData = { refreshData(showIndicator = true) },
                                                onDismissRequest = onDismissRequest
                                            )
                                        }
                                        InspectorTab.NETWORK -> {
                                            NetworkTabContent(data)
                                        }
                                        InspectorTab.BATTERY -> {
                                            BatteryTabContent(data)
                                        }
                                        InspectorTab.PERMISSIONS -> {
                                            PermissionsTabContent(data)
                                        }
                                    }
                                }
                            }
                        }
                    }

                }
            }
        }
    }
    } else {
        // MINIMIZED FLOATING BUBBLE via non-modal Popup (does NOT block launcher or alphabet scrolling!)
        Popup(
            alignment = Alignment.TopStart,
            offset = IntOffset(bubbleOffsetX.roundToInt(), bubbleOffsetY.roundToInt()),
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                clippingEnabled = false,
            ),
        ) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .pointerInput(Unit) {
                        var hasMoved = false
                        detectDragGestures(
                            onDragStart = { hasMoved = false },
                            onDragEnd = {
                                if (!hasMoved) {
                                    isMinimized = false
                                }
                            },
                            onDragCancel = { hasMoved = false },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (kotlin.math.abs(dragAmount.x) > 1.5f || kotlin.math.abs(dragAmount.y) > 1.5f) {
                                    hasMoved = true
                                }
                                bubbleOffsetX = (bubbleOffsetX + dragAmount.x).coerceIn(0f, maxBubbleX)
                                bubbleOffsetY = (bubbleOffsetY + dragAmount.y).coerceIn(0f, maxBubbleY)
                            }
                        )
                    },
            ) {
                Surface(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .clickable {
                            isMinimized = false
                        },
                    color = dynamicSurfaceColor().copy(alpha = 0.95f),
                    tonalElevation = 10.dp,
                    shadowElevation = 14.dp,
                    border = BorderStroke(1.5.dp, dynamicBorderColor()),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        AppIcon(
                            app = app,
                            sizeDp = 34,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                        )

                        // Status dot indicator at bottom end
                        val currentStatus = inspectionData?.status ?: AppProcessStatus.STOPPED
                        val statusDotColor = when (currentStatus) {
                            AppProcessStatus.FOREGROUND -> Color(0xFF10B981)
                            AppProcessStatus.BACKGROUND -> Color(0xFF3B82F6)
                            AppProcessStatus.STOPPED -> colorScheme.onSurface.copy(alpha = 0.45f)
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                                .border(1.5.dp, dynamicSurfaceColor(), CircleShape),
                        )
                    }
                }

                // Mini close button on the top-end of the bubble
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(colorScheme.surfaceVariant)
                        .border(1.dp, colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                        .clickable { onDismissRequest() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }

    if (showLogViewer) {
        dev.viagaralauncher.ui.root.AppLogViewerDialog(
            initialApp = app,
            onDismissRequest = { showLogViewer = false },
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colorScheme.onSurface.copy(alpha = 0.05f))
            .border(1.dp, colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = title,
                    color = colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Spacer(Modifier.width(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = value,
                    color = colorScheme.onSurface,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = accentColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun RealtimeSpeedCard(
    title: String,
    speedBps: Long,
    isDownload: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val accentColor = if (isDownload) Color(0xFF10B981) else Color(0xFF06B6D4)
    val icon = if (isDownload) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward
    val isLive = speedBps > 0L
    val formattedSpeed = if (isLive) AppRootInspector.formatSpeed(speedBps) else "0 B/s"

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colorScheme.onSurface.copy(alpha = 0.05f))
            .border(
                1.dp,
                if (isLive) accentColor.copy(alpha = 0.35f) else colorScheme.outline.copy(alpha = 0.12f),
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = title,
                    color = colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                )
                if (isLive) {
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            Text(
                text = "${if (isDownload) "↓" else "↑"} $formattedSpeed",
                color = if (isLive) accentColor else colorScheme.onSurface,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun DataUsageCard(
    data: AppInspectionData,
    selectedPeriod: DataPeriod,
    onPeriodSelect: (DataPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    val (rxBytes, txBytes) = when (selectedPeriod) {
        DataPeriod.TODAY -> data.rxBytesToday to data.txBytesToday
        DataPeriod.LAST_7_DAYS -> data.rxBytes7Days to data.txBytes7Days
        DataPeriod.TOTAL -> data.rxBytes to data.txBytes
    }
    val totalBytes = rxBytes + txBytes

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colorScheme.onSurface.copy(alpha = 0.05f))
            .border(1.dp, colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Column {
            // Header: Title + Period Selector Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DataUsage,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.root_inspector_data_usage),
                        color = colorScheme.onSurface.copy(alpha = 0.65f),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.width(4.dp))

                // Selector Pills: Hoje | 7 Dias | Total
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PeriodPill(
                        label = stringResource(R.string.root_inspector_period_today),
                        selected = selectedPeriod == DataPeriod.TODAY,
                        onClick = { onPeriodSelect(DataPeriod.TODAY) },
                    )
                    PeriodPill(
                        label = stringResource(R.string.root_inspector_period_7days),
                        selected = selectedPeriod == DataPeriod.LAST_7_DAYS,
                        onClick = { onPeriodSelect(DataPeriod.LAST_7_DAYS) },
                    )
                    PeriodPill(
                        label = stringResource(R.string.root_inspector_period_total),
                        selected = selectedPeriod == DataPeriod.TOTAL,
                        onClick = { onPeriodSelect(DataPeriod.TOTAL) },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // 3-Metric Row: Download | Upload | Total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.root_inspector_download),
                        color = colorScheme.onSurface.copy(alpha = 0.55f),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = AppRootInspector.formatBytes(rxBytes),
                        color = Color(0xFF10B981),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                Column {
                    Text(
                        text = stringResource(R.string.root_inspector_upload),
                        color = colorScheme.onSurface.copy(alpha = 0.55f),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = AppRootInspector.formatBytes(txBytes),
                        color = Color(0xFF06B6D4),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.root_inspector_total_data),
                        color = colorScheme.onSurface.copy(alpha = 0.55f),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = AppRootInspector.formatBytes(totalBytes),
                        color = colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val bg = if (selected) colorScheme.primary.copy(alpha = 0.22f) else colorScheme.onSurface.copy(alpha = 0.06f)
    val textCol = if (selected) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.65f)
    val borderCol = if (selected) colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .border(1.dp, borderCol, RoundedCornerShape(5.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 5.dp, vertical = 1.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textCol,
            fontSize = 8.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun TabButton(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val bg = if (selected) colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent
    val contentColor = if (selected) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.6f)
    val borderCol = if (selected) colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, borderCol, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 5.dp, horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = title,
                color = contentColor,
                fontSize = 8.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MemoryTabContent(data: AppInspectionData) {
    val colorScheme = MaterialTheme.colorScheme
    Column {
        Text(
            text = "DISTRIBUIÇÃO DE MEMÓRIA (PSS)",
            color = colorScheme.onSurface.copy(alpha = 0.55f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(8.dp))

        val total = data.ramTotalMb.coerceAtLeast(0.1)
        val dalvikPct = ((data.ramDalvikMb / total) * 100).coerceIn(0.0, 100.0)
        val nativePct = ((data.ramNativeMb / total) * 100).coerceIn(0.0, 100.0)
        val graphicsPct = ((data.ramGraphicsMb / total) * 100).coerceIn(0.0, 100.0)

        MemoryBarItem(
            label = "Dalvik / ART Heap",
            valueMb = data.ramDalvikMb,
            percentage = dalvikPct.toFloat(),
            color = Color(0xFF3B82F6),
        )
        Spacer(Modifier.height(8.dp))
        MemoryBarItem(
            label = "Native Heap (C/C++)",
            valueMb = data.ramNativeMb,
            percentage = nativePct.toFloat(),
            color = Color(0xFF10B981),
        )
        Spacer(Modifier.height(8.dp))
        MemoryBarItem(
            label = "Gráficos / GPU (EGL)",
            valueMb = data.ramGraphicsMb,
            percentage = graphicsPct.toFloat(),
            color = Color(0xFFF59E0B),
        )

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.12f))
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "UID do Pacote:",
                color = colorScheme.onSurface.copy(alpha = 0.65f),
                fontSize = 11.sp,
            )
            Text(
                text = "${data.uid} (u0_a${data.uid % 100000})",
                color = colorScheme.onSurface,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Versão Instalada:",
                color = colorScheme.onSurface.copy(alpha = 0.65f),
                fontSize = 11.sp,
            )
            Text(
                text = "${data.versionName} (${data.versionCode})",
                color = colorScheme.onSurface,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun MemoryBarItem(
    label: String,
    valueMb: Double,
    percentage: Float,
    color: Color,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                color = colorScheme.onSurface.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${String.format(Locale.US, "%.1f", valueMb)} MB",
                color = colorScheme.onSurface,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { (percentage / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.15f),
        )
    }
}

@Composable
private fun ProcessesTabContent(
    data: AppInspectionData,
    onKillPid: (Int) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column {
        // Top Activity
        if (!data.topActivity.isNullOrBlank()) {
            Text(
                text = "ATIVIDADE EM PRIMEIRO PLANO",
                color = colorScheme.onSurface.copy(alpha = 0.55f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF10B981).copy(alpha = 0.12f))
                    .border(1.dp, Color(0xFF10B981).copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = data.topActivity,
                    color = Color(0xFF10B981),
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // Active Processes
        Text(
            text = "PROCESSOS ATIVOS (${data.processes.size})",
            color = colorScheme.onSurface.copy(alpha = 0.55f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(6.dp))

        if (data.processes.isEmpty()) {
            Text(
                text = "Nenhum processo rodando (aplicativo inativo).",
                color = colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 11.sp,
            )
        } else {
            data.processes.forEach { proc ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorScheme.onSurface.copy(alpha = 0.05f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = proc.name,
                            color = colorScheme.onSurface,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "PID: ${proc.pid} · User: ${proc.user} · CPU: ${proc.cpuPercent}%",
                            color = colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colorScheme.error.copy(alpha = 0.15f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onKillPid(proc.pid) },
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.root_inspector_action_kill_pid),
                            color = colorScheme.error,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Running Services
        Text(
            text = "SERVIÇOS EM SEGUNDO PLANO (${data.activeServices.size})",
            color = colorScheme.onSurface.copy(alpha = 0.55f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(6.dp))

        if (data.activeServices.isEmpty()) {
            Text(
                text = stringResource(R.string.root_inspector_no_services),
                color = colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 11.sp,
            )
        } else {
            data.activeServices.forEach { serviceName ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colorScheme.onSurface.copy(alpha = 0.05f))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = "⚙️ $serviceName",
                        color = colorScheme.onSurface.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkTabContent(data: AppInspectionData) {
    val colorScheme = MaterialTheme.colorScheme
    Column {
        Text(
            text = "CONSUMO DE DADOS (SISTEMA NATIVO)",
            color = colorScheme.onSurface.copy(alpha = 0.55f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colorScheme.onSurface.copy(alpha = 0.05f))
                .padding(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NetworkUsageRow(
                    label = "Hoje (00:00 - Agora)",
                    rx = data.rxBytesToday,
                    tx = data.txBytesToday,
                )
                HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.1f))
                NetworkUsageRow(
                    label = "Últimos 7 Dias",
                    rx = data.rxBytes7Days,
                    tx = data.txBytes7Days,
                )
                HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.1f))
                NetworkUsageRow(
                    label = "Histórico Total",
                    rx = data.rxBytes,
                    tx = data.txBytes,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "CONEXÕES DE REDE ATIVAS",
            color = colorScheme.onSurface.copy(alpha = 0.55f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(6.dp))

        if (data.activeConnections.isEmpty()) {
            Text(
                text = stringResource(R.string.root_inspector_no_connections),
                color = colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 11.sp,
            )
        } else {
            data.activeConnections.forEach { conn ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorScheme.onSurface.copy(alpha = 0.05f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF3B82F6).copy(alpha = 0.15f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                ) {
                                    Text(
                                        text = conn.protocol,
                                        color = Color(0xFF3B82F6),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = conn.remoteAddress,
                                    color = colorScheme.onSurface,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text(
                                text = "Local: ${conn.localAddress}",
                                color = colorScheme.onSurface.copy(alpha = 0.55f),
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }

                        Text(
                            text = conn.state,
                            color = if (conn.state == "ESTABLISHED") Color(0xFF10B981) else colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkUsageRow(label: String, rx: Long, tx: Long) {
    val colorScheme = MaterialTheme.colorScheme
    Column {
        Text(
            text = label,
            color = colorScheme.onSurface.copy(alpha = 0.85f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "↓ ${AppRootInspector.formatBytes(rx)}",
                color = Color(0xFF10B981),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "↑ ${AppRootInspector.formatBytes(tx)}",
                color = Color(0xFF06B6D4),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Total: ${AppRootInspector.formatBytes(rx + tx)}",
                color = colorScheme.primary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionsTabContent(data: AppInspectionData) {
    val colorScheme = MaterialTheme.colorScheme
    Column {
        Text(
            text = "PERMISSÕES DO APLICATIVO (${data.permissions.size})",
            color = colorScheme.onSurface.copy(alpha = 0.55f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            data.permissions.forEach { perm ->
                val bg = if (perm.isGranted) {
                    if (perm.isDangerous) Color(0xFF10B981).copy(alpha = 0.14f) else colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    colorScheme.onSurface.copy(alpha = 0.05f)
                }
                val textCol = if (perm.isGranted) {
                    if (perm.isDangerous) Color(0xFF10B981) else colorScheme.primary
                } else {
                    colorScheme.onSurface.copy(alpha = 0.45f)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .border(1.dp, textCol.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (perm.isGranted) "✓" else "✕",
                            color = textCol,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = perm.label,
                            color = textCol,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryTabContent(data: AppInspectionData) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val battery = data.batteryData ?: AppBatteryInspectionData()

    Column {
        Text(
            text = "TELEMETRIA DE BATERIA & ENERGIA",
            color = colorScheme.onSurface.copy(alpha = 0.55f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(8.dp))

        // Power Impact Banner
        val (impactBg, impactBorder, impactColor, impactLabel) = when (battery.powerImpact) {
            PowerImpactLevel.VERY_HIGH -> listOf(
                Color(0xFFEF4444).copy(alpha = 0.15f),
                Color(0xFFEF4444).copy(alpha = 0.4f),
                Color(0xFFEF4444),
                "Impacto Muito Alto — Dreno Severo",
            )
            PowerImpactLevel.HIGH -> listOf(
                Color(0xFFF97316).copy(alpha = 0.15f),
                Color(0xFFF97316).copy(alpha = 0.4f),
                Color(0xFFF97316),
                "Impacto Alto — Consumo Significativo",
            )
            PowerImpactLevel.MEDIUM -> listOf(
                Color(0xFFF59E0B).copy(alpha = 0.15f),
                Color(0xFFF59E0B).copy(alpha = 0.4f),
                Color(0xFFF59E0B),
                "Impacto Moderado",
            )
            PowerImpactLevel.LOW -> listOf(
                Color(0xFF10B981).copy(alpha = 0.15f),
                Color(0xFF10B981).copy(alpha = 0.4f),
                Color(0xFF10B981),
                "Impacto Baixo — Econômico",
            )
            PowerImpactLevel.MINIMAL -> listOf(
                colorScheme.onSurface.copy(alpha = 0.06f),
                colorScheme.outline.copy(alpha = 0.15f),
                colorScheme.onSurface.copy(alpha = 0.6f),
                "Impacto Mínimo — Ocioso",
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(impactBg as Color)
                .border(1.dp, impactBorder as Color, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Speed,
                    contentDescription = null,
                    tint = impactColor as Color,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = impactLabel as String,
                    color = impactColor,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Total Drain Metric Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Dreno Estimado",
                value = "${String.format(Locale.US, "%.1f", battery.totalMah)} mAh",
                icon = Icons.Filled.BatteryChargingFull,
                accentColor = Color(0xFF10B981),
                subtitle = "${String.format(Locale.US, "%.2f", battery.percentTotalDrain)}% da bateria",
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Wakelocks em BG",
                value = formatDurationSecLabel(battery.wakelockTimeSec),
                icon = Icons.Filled.Security,
                accentColor = if (battery.wakelockTimeSec > 60) Color(0xFFF97316) else Color(0xFF3B82F6),
                subtitle = "${battery.wakeupAlarmsCount} alarmes",
            )
        }

        Spacer(Modifier.height(12.dp))

        // Foreground vs Background Drain Bars
        Text(
            text = "DIVISÃO DE CONSUMO (PRIMEIRO PLANO vs SEGUNDO PLANO)",
            color = colorScheme.onSurface.copy(alpha = 0.55f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
        Spacer(Modifier.height(6.dp))

        val totalCalc = (battery.foregroundMah + battery.backgroundMah).coerceAtLeast(0.01)
        val fgPct = ((battery.foregroundMah / totalCalc) * 100.0).toFloat().coerceIn(0f, 100f)
        val bgPct = ((battery.backgroundMah / totalCalc) * 100.0).toFloat().coerceIn(0f, 100f)

        BatteryProgressItem(
            label = "Primeiro Plano (Tela Ligada)",
            valueMah = battery.foregroundMah,
            percentage = fgPct,
            color = Color(0xFF10B981),
        )
        Spacer(Modifier.height(6.dp))
        BatteryProgressItem(
            label = "Segundo Plano (Processos Silenciosos)",
            valueMah = battery.backgroundMah,
            percentage = bgPct,
            color = Color(0xFFF97316),
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.12f))
        Spacer(Modifier.height(10.dp))

        // Component breakdown: CPU, Wakelocks, Mobile, Wi-Fi
        Text(
            text = "CONSUMO POR COMPONENTE DE HARDWARE",
            color = colorScheme.onSurface.copy(alpha = 0.55f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Processamento (CPU):", fontSize = 11.sp, color = colorScheme.onSurface.copy(alpha = 0.7f))
            Text("${String.format(Locale.US, "%.1f", battery.cpuMah)} mAh", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface)
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("CPU Acordada (Wakelocks):", fontSize = 11.sp, color = colorScheme.onSurface.copy(alpha = 0.7f))
            Text("${String.format(Locale.US, "%.1f", battery.wakelockMah)} mAh", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface)
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Rede Móvel (4G/5G):", fontSize = 11.sp, color = colorScheme.onSurface.copy(alpha = 0.7f))
            Text("${String.format(Locale.US, "%.1f", battery.mobileRadioMah)} mAh", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface)
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Wi-Fi:", fontSize = 11.sp, color = colorScheme.onSurface.copy(alpha = 0.7f))
            Text("${String.format(Locale.US, "%.1f", battery.wifiMah)} mAh", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface)
        }

        Spacer(Modifier.height(10.dp))

        // Open Android System Battery Optimization Settings shortcut
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    runCatching {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            this.data = Uri.parse("package:${data.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                },
            color = colorScheme.primary.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.25f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.BatteryAlert,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Gerenciar Otimização de Bateria do App",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun BatteryProgressItem(
    label: String,
    valueMah: Double,
    percentage: Float,
    color: Color,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                color = colorScheme.onSurface.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${String.format(Locale.US, "%.1f", valueMah)} mAh (${String.format(Locale.US, "%.0f", percentage)}%)",
                color = colorScheme.onSurface,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { (percentage / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = colorScheme.surfaceVariant.copy(alpha = 0.5f),
        )
    }
}

private fun formatDurationSecLabel(seconds: Long): String {
    if (seconds <= 0L) return "0s"
    val h = seconds / 3600L
    val m = (seconds % 3600L) / 60L
    val s = seconds % 60L
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    backgroundColor: Color,
    textColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 7.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                color = textColor,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}


@Composable
private fun ActionsTabContent(app: AppInfo, context: android.content.Context, scope: kotlinx.coroutines.CoroutineScope, onRefreshData: () -> Unit, onDismissRequest: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ações do Aplicativo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        ActionButton(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.root_inspector_action_launch),
            icon = Icons.Filled.PlayArrow,
            backgroundColor = colorScheme.primary.copy(alpha = 0.16f),
            textColor = colorScheme.primary,
            borderColor = colorScheme.primary.copy(alpha = 0.35f),
            onClick = { AppRootInspector.launchApp(context, app.packageName); onDismissRequest() }
        )
        ActionButton(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.root_inspector_action_force_stop),
            icon = Icons.Filled.StopCircle,
            backgroundColor = colorScheme.error.copy(alpha = 0.16f),
            textColor = colorScheme.error,
            borderColor = colorScheme.error.copy(alpha = 0.35f),
            onClick = {
                scope.launch {
                    if (AppRootInspector.forceStopApp(app.packageName)) {
                        Toast.makeText(context, context.getString(R.string.root_inspector_toast_stopped), Toast.LENGTH_SHORT).show()
                        onRefreshData()
                    }
                }
            }
        )
        ActionButton(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.root_inspector_action_clear_cache),
            icon = Icons.Filled.CleaningServices,
            backgroundColor = colorScheme.onSurface.copy(alpha = 0.08f),
            textColor = colorScheme.onSurface.copy(alpha = 0.9f),
            borderColor = colorScheme.outline.copy(alpha = 0.2f),
            onClick = {
                scope.launch {
                    if (AppRootInspector.clearAppCache(app.packageName)) {
                        Toast.makeText(context, context.getString(R.string.root_inspector_toast_cache_cleared), Toast.LENGTH_SHORT).show()
                        onRefreshData()
                    }
                }
            }
        )
        ActionButton(
            modifier = Modifier.fillMaxWidth(),
            label = "Abrir informações do app",
            icon = Icons.Filled.Settings,
            backgroundColor = colorScheme.onSurface.copy(alpha = 0.08f),
            textColor = colorScheme.onSurface.copy(alpha = 0.9f),
            borderColor = colorScheme.outline.copy(alpha = 0.2f),
            onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                onDismissRequest()
            }
        )
        ActionButton(
            modifier = Modifier.fillMaxWidth(),
            label = "Desinstalar app",
            icon = Icons.Filled.Delete,
            backgroundColor = colorScheme.error.copy(alpha = 0.16f),
            textColor = colorScheme.error,
            borderColor = colorScheme.error.copy(alpha = 0.35f),
            onClick = {
                scope.launch {
                    if (AppRootInspector.uninstallApp(app.packageName)) {
                        Toast.makeText(context, "App desinstalado com sucesso", Toast.LENGTH_SHORT).show()
                        onDismissRequest()
                    } else {
                        Toast.makeText(context, "Falha ao desinstalar app", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        ActionButton(
            modifier = Modifier.fillMaxWidth(),
            label = "Impedir uso em segundo plano",
            icon = Icons.Filled.Block,
            backgroundColor = colorScheme.onSurface.copy(alpha = 0.08f),
            textColor = colorScheme.onSurface.copy(alpha = 0.9f),
            borderColor = colorScheme.outline.copy(alpha = 0.2f),
            onClick = {
                scope.launch {
                    if (AppRootInspector.restrictBackgroundUsage(app.packageName, context)) {
                        Toast.makeText(context, "Uso em segundo plano impedido", Toast.LENGTH_SHORT).show()
                        onRefreshData()
                    }
                }
            }
        )
    }
}
