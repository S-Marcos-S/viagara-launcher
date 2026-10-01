// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.root

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.FileProvider
import dev.viagaralauncher.R
import dev.viagaralauncher.data.AppInfo
import dev.viagaralauncher.root.AppLogCaptureService
import dev.viagaralauncher.root.log.AppCrashRecord
import dev.viagaralauncher.root.log.CrashType
import dev.viagaralauncher.root.log.DeviceInfoProvider
import dev.viagaralauncher.root.log.LogFilterEngine
import dev.viagaralauncher.root.log.LogLevel
import dev.viagaralauncher.root.log.LogLine
import dev.viagaralauncher.root.log.RecordingState
import dev.viagaralauncher.root.log.SavedLogRecording
import dev.viagaralauncher.root.log.TerminalEngine
import dev.viagaralauncher.root.log.TerminalType
import dev.viagaralauncher.root.log.UserLogFilter
import dev.viagaralauncher.ui.common.AppIcon
import dev.viagaralauncher.ui.theme.dynamicBorderColor
import dev.viagaralauncher.ui.theme.dynamicSurfaceColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class LogViewerTab(val title: String, val icon: ImageVector) {
    LIVE_LOGS("Logs", Icons.Filled.Terminal),
    RECORDINGS("Gravação", Icons.Filled.FiberManualRecord),
    CRASHES("Crashes & ANRs", Icons.Filled.BugReport),
    FILTERS("Filtros", Icons.Filled.FilterList),
    SETTINGS("Terminal", Icons.Filled.History),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppLogViewerDialog(
    initialApp: AppInfo? = null,
    initialPackageName: String? = null,
    initialTab: String? = null,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var selectedTab by remember {
        mutableStateOf(
            when (initialTab) {
                "crashes" -> LogViewerTab.CRASHES
                "recordings" -> LogViewerTab.RECORDINGS
                "filters" -> LogViewerTab.FILTERS
                else -> LogViewerTab.LIVE_LOGS
            }
        )
    }

    val sourceApp = initialApp
    val sourcePackageName = initialPackageName
    val hasSourceApp = sourceApp != null || sourcePackageName != null
    var isFilteringByApp by remember { mutableStateOf(hasSourceApp) }

    val currentTargetApp = if (isFilteringByApp) sourceApp else null
    val currentTargetPackage = if (isFilteringByApp) (sourceApp?.packageName ?: sourcePackageName) else null

    var searchQuery by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    var autoScrollToBottom by remember { mutableStateOf(true) }

    // Window dragging offsets
    var windowOffsetX by remember { mutableFloatStateOf(0f) }
    var windowOffsetY by remember { mutableFloatStateOf(0f) }

    // Minimized floating bubble state & screen bounds
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val maxBubbleX = (screenWidthPx - with(density) { 72.dp.toPx() }).coerceAtLeast(0f)
    val maxBubbleY = (screenHeightPx - with(density) { 80.dp.toPx() }).coerceAtLeast(0f)

    val defaultBubbleX = with(density) { 20.dp.toPx() }
    val defaultBubbleY = with(density) { 110.dp.toPx() }

    var isMinimized by remember { mutableStateOf(false) }
    var bubbleOffsetX by remember { mutableFloatStateOf(defaultBubbleX) }
    var bubbleOffsetY by remember { mutableFloatStateOf(defaultBubbleY) }

    // Repositories & Managers
    val crashManager = remember { AppLogCaptureService.getCrashManager(context) }
    val recordingsManager = remember { AppLogCaptureService.getRecordingsManager(context) }
    val filterStorage = remember { AppLogCaptureService.getFilterStorage(context) }

    val rawLogs by AppLogCaptureService.liveLogs.collectAsState()
    val isStreamingPaused by AppLogCaptureService.isStreamingPaused.collectAsState()
    val recordingSession by recordingsManager.session.collectAsState()
    val crashes by crashManager.crashes.collectAsState()
    val userFilters by filterStorage.filters.collectAsState()
    val savedRecordings by recordingsManager.savedRecordings.collectAsState()
    val preferredTerminal by AppLogCaptureService.preferredTerminal.collectAsState()

    var lineDetailDialogFor by remember { mutableStateOf<LogLine?>(null) }
    var createFilterDialogOpen by remember { mutableStateOf(false) }

    // Start background monitoring/service on launch and stop when dismissed (if not recording)
    DisposableEffect(Unit) {
        AppLogCaptureService.startMonitoring(context)
        onDispose {
            AppLogCaptureService.stopMonitoring(context)
        }
    }

    // Filtered logs
    val filteredLogs = remember(rawLogs, userFilters, searchQuery, caseSensitive, selectedLevel, currentTargetPackage) {
        LogFilterEngine.filterAndSearch(
            lines = rawLogs,
            filters = userFilters,
            query = searchQuery,
            caseSensitive = caseSensitive,
            selectedLevel = selectedLevel,
            targetPackage = currentTargetPackage,
        )
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
            val dialogView = LocalView.current

            LaunchedEffect(dialogView) {
                val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
                dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                dialogWindow.setDimAmount(0f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    val params = dialogWindow.attributes
                    params.blurBehindRadius = 40
                    dialogWindow.attributes = params
                }
            }

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
                        .fillMaxWidth(0.92f)
                        .fillMaxHeight(0.82f)
                        .offset { IntOffset(windowOffsetX.roundToInt(), windowOffsetY.roundToInt()) }
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, dynamicBorderColor(), RoundedCornerShape(24.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    color = dynamicSurfaceColor().copy(alpha = 0.94f),
                    tonalElevation = 8.dp,
                    shadowElevation = 18.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                    ) {
                        // Drag Handle at the top of the floating window
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
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
                                    .width(42.dp)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(colorScheme.onSurface.copy(alpha = 0.2f))
                            )
                        }

                        // Header Bar (also draggable!)
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
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (hasSourceApp) {
                                val appLabel = sourceApp?.label ?: sourcePackageName ?: "App"
                                val pkgName = sourceApp?.packageName ?: sourcePackageName ?: ""

                                if (sourceApp != null) {
                                    AppIcon(
                                        app = sourceApp,
                                        sizeDp = 38,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(1.dp, colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(colorScheme.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.BugReport,
                                            contentDescription = null,
                                            tint = colorScheme.primary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (isFilteringByApp) appLabel else "Todos os logs",
                                            color = colorScheme.onSurface,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable { isFilteringByApp = !isFilteringByApp },
                                            color = if (isFilteringByApp) colorScheme.primary.copy(alpha = 0.12f) else colorScheme.primary.copy(alpha = 0.22f),
                                            border = BorderStroke(1.dp, if (isFilteringByApp) colorScheme.primary.copy(alpha = 0.35f) else colorScheme.primary),
                                            shape = RoundedCornerShape(6.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            ) {
                                                Icon(
                                                    imageVector = if (isFilteringByApp) Icons.Filled.Language else Icons.Filled.FilterList,
                                                    contentDescription = null,
                                                    tint = colorScheme.primary,
                                                    modifier = Modifier.size(11.dp),
                                                )
                                                Text(
                                                    text = if (isFilteringByApp) "Ver todos" else "Filtrar $appLabel",
                                                    color = colorScheme.primary,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = if (isFilteringByApp) pkgName else "Logs de todo o sistema (toque para voltar ao $appLabel)",
                                        color = colorScheme.onSurface.copy(alpha = 0.6f),
                                        fontSize = 10.sp,
                                        fontFamily = if (isFilteringByApp) FontFamily.Monospace else FontFamily.Default,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Terminal,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Central de Logs & Diagnóstico",
                                        color = colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "Logcat • Crashes • ANRs • LogFox Engine",
                                        color = colorScheme.onSurface.copy(alpha = 0.6f),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            // Header Actions (Minimize & Close)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (FloatingLogOverlayManager.canDrawOverlays(context)) {
                                            FloatingLogOverlayManager.show(
                                                context = context,
                                                app = currentTargetApp,
                                                packageName = currentTargetPackage,
                                                initialTab = selectedTab.name.lowercase(),
                                                isMinimized = true,
                                            )
                                            onDismissRequest()
                                        } else {
                                            FloatingLogOverlayManager.requestOverlayPermission(context) {
                                                FloatingLogOverlayManager.show(
                                                    context = context,
                                                    app = currentTargetApp,
                                                    packageName = currentTargetPackage,
                                                    initialTab = selectedTab.name.lowercase(),
                                                    isMinimized = true,
                                                )
                                                onDismissRequest()
                                            }
                                            isMinimized = true
                                        }
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Remove,
                                        contentDescription = "Minimizar para bolinha flutuante sobre outros apps",
                                        tint = colorScheme.onSurface.copy(alpha = 0.75f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Spacer(Modifier.width(2.dp))
                                IconButton(
                                    onClick = onDismissRequest,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.action_close),
                                        tint = colorScheme.onSurface.copy(alpha = 0.8f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }

                    Spacer(Modifier.height(8.dp))

                    // Tab Selector Navigation
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LogViewerTab.entries.forEach { tab ->
                            val isSelected = selectedTab == tab
                            val activeBadgeCount = when (tab) {
                                LogViewerTab.CRASHES -> crashes.size
                                LogViewerTab.RECORDINGS -> if (recordingSession.state != RecordingState.IDLE) 1 else savedRecordings.size
                                LogViewerTab.FILTERS -> userFilters.count { it.enabled }
                                else -> 0
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) colorScheme.primary.copy(alpha = 0.18f)
                                        else colorScheme.onSurface.copy(alpha = 0.05f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) colorScheme.primary.copy(alpha = 0.4f)
                                        else colorScheme.outline.copy(alpha = 0.1f),
                                        RoundedCornerShape(10.dp),
                                    )
                                    .clickable { selectedTab = tab }
                                    .padding(horizontal = 11.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = null,
                                        tint = if (isSelected) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = tab.title,
                                        color = if (isSelected) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.75f),
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                    )
                                    if (activeBadgeCount > 0) {
                                        Spacer(Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(if (tab == LogViewerTab.CRASHES) Color(0xFFEF4444) else colorScheme.primary)
                                                .padding(horizontal = 5.dp, vertical = 1.dp),
                                        ) {
                                            Text(
                                                text = activeBadgeCount.toString(),
                                                color = Color.White,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Tab Body
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colorScheme.onSurface.copy(alpha = 0.03f))
                            .border(1.dp, colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                    ) {
                        when (selectedTab) {
                            LogViewerTab.LIVE_LOGS -> {
                                LiveLogsTab(
                                    logs = filteredLogs,
                                    searchQuery = searchQuery,
                                    onSearchQueryChange = { searchQuery = it },
                                    caseSensitive = caseSensitive,
                                    onCaseSensitiveToggle = { caseSensitive = !caseSensitive },
                                    selectedLevel = selectedLevel,
                                    onSelectLevel = { selectedLevel = it },
                                    autoScrollToBottom = autoScrollToBottom,
                                    onToggleAutoScroll = { autoScrollToBottom = !autoScrollToBottom },
                                    isStreamingPaused = isStreamingPaused,
                                    onTogglePauseStream = { AppLogCaptureService.isStreamingPaused.value = !isStreamingPaused },
                                    onClearBuffer = { AppLogCaptureService.clearLiveLogs() },
                                    isRecording = recordingSession.state != RecordingState.IDLE,
                                    onToggleRecording = {
                                        if (recordingSession.state == RecordingState.IDLE) {
                                            AppLogCaptureService.startCapture(
                                                context,
                                                currentTargetPackage,
                                                currentTargetApp?.label ?: currentTargetPackage,
                                            )
                                        } else {
                                            AppLogCaptureService.saveLog(context)
                                        }
                                    },
                                    onSelectLogLine = { lineDetailDialogFor = it },
                                )
                            }
                            LogViewerTab.RECORDINGS -> {
                                RecordingsTab(
                                    session = recordingSession,
                                    savedRecordings = savedRecordings,
                                    targetApp = currentTargetApp,
                                    targetPackageName = currentTargetPackage,
                                    onStartRecording = { pkg, name ->
                                        AppLogCaptureService.startCapture(context, pkg, name)
                                    },
                                    onPauseRecording = { AppLogCaptureService.togglePauseResume(context) },
                                    onStopSaveRecording = { AppLogCaptureService.saveLog(context) },
                                    onDiscardRecording = { AppLogCaptureService.cancelCapture(context) },
                                    onDeleteRecording = { file -> recordingsManager.deleteRecording(file) },
                                )
                            }
                            LogViewerTab.CRASHES -> {
                                CrashesTab(
                                    crashes = crashes,
                                    onClearCrashes = { crashManager.clearCrashes() },
                                    onDeleteCrash = { id -> crashManager.deleteCrash(id) },
                                )
                            }
                            LogViewerTab.FILTERS -> {
                                FiltersTab(
                                    filters = userFilters,
                                    onAddFilterClick = { createFilterDialogOpen = true },
                                    onToggleFilter = { id, enabled -> filterStorage.toggleFilter(id, enabled) },
                                    onDeleteFilter = { id -> filterStorage.deleteFilter(id) },
                                )
                            }
                            LogViewerTab.SETTINGS -> {
                                TerminalSettingsTab(
                                    preferredTerminal = preferredTerminal,
                                    onTerminalSelected = { AppLogCaptureService.preferredTerminal.value = it },
                                )
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
                        if (sourceApp != null) {
                            AppIcon(
                                app = sourceApp,
                                sizeDp = 34,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Terminal,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                        }

                        // Live recording indicator
                        if (recordingSession.state != RecordingState.IDLE) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                                    .border(1.5.dp, dynamicSurfaceColor(), CircleShape),
                            )
                        } else if (crashes.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${crashes.size}",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
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
                        contentDescription = "Fechar bolinha",
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        }
    }

    // Line Detail Dialog
    lineDetailDialogFor?.let { line ->
        LogLineDetailDialog(
            line = line,
            onDismiss = { lineDetailDialogFor = null },
            onFilterTag = { tag ->
                searchQuery = tag
                lineDetailDialogFor = null
            },
        )
    }

    // Create Filter Dialog
    if (createFilterDialogOpen) {
        CreateFilterDialog(
            targetAppPackage = currentTargetPackage,
            onDismiss = { createFilterDialogOpen = false },
            onSave = { filter ->
                filterStorage.addFilter(filter)
                createFilterDialogOpen = false
            },
        )
    }
}

// -------------------------------------------------------------------------------------------------
// TAB 1: LIVE LOGS CONSOLE
// -------------------------------------------------------------------------------------------------

@Composable
internal fun LiveLogsTab(
    logs: List<LogLine>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    caseSensitive: Boolean,
    onCaseSensitiveToggle: () -> Unit,
    selectedLevel: LogLevel?,
    onSelectLevel: (LogLevel?) -> Unit,
    autoScrollToBottom: Boolean,
    onToggleAutoScroll: () -> Unit,
    isStreamingPaused: Boolean,
    onTogglePauseStream: () -> Unit,
    onClearBuffer: () -> Unit,
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
    onSelectLogLine: (LogLine) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()

    // Auto-scroll to bottom effect
    LaunchedEffect(logs.size, autoScrollToBottom) {
        if (autoScrollToBottom && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Control Bar: Search + Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Search Input Field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colorScheme.onSurface.copy(alpha = 0.06f))
                    .border(1.dp, colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = colorScheme.onSurface,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        cursorBrush = SolidColor(colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Pesquisar tag, mensagem, PID...",
                                        color = colorScheme.onSurface.copy(alpha = 0.4f),
                                        fontSize = 11.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Limpar",
                            tint = colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onSearchQueryChange("") },
                        )
                    }
                }
            }

            // Case sensitive toggle
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (caseSensitive) colorScheme.primary.copy(alpha = 0.2f) else colorScheme.onSurface.copy(alpha = 0.06f))
                    .border(1.dp, if (caseSensitive) colorScheme.primary else colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .clickable { onCaseSensitiveToggle() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Aa",
                    color = if (caseSensitive) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Auto-scroll toggle
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (autoScrollToBottom) colorScheme.primary.copy(alpha = 0.2f) else colorScheme.onSurface.copy(alpha = 0.06f))
                    .border(1.dp, if (autoScrollToBottom) colorScheme.primary else colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .clickable { onToggleAutoScroll() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowDownward,
                    contentDescription = "Rolar automaticamente",
                    tint = if (autoScrollToBottom) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
            }

            // Pause/Resume Stream
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isStreamingPaused) Color(0xFFF59E0B).copy(alpha = 0.2f) else colorScheme.onSurface.copy(alpha = 0.06f))
                    .border(1.dp, if (isStreamingPaused) Color(0xFFF59E0B) else colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .clickable { onTogglePauseStream() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isStreamingPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (isStreamingPaused) "Retomar" else "Pausar",
                    tint = if (isStreamingPaused) Color(0xFFF59E0B) else colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp),
                )
            }

            // Record / Save Button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isRecording) Color(0xFFEF4444).copy(alpha = 0.2f) else colorScheme.onSurface.copy(alpha = 0.06f))
                    .border(1.dp, if (isRecording) Color(0xFFEF4444) else colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .clickable { onToggleRecording() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                    contentDescription = if (isRecording) "Salvar gravação" else "Gravar logs",
                    tint = if (isRecording) Color(0xFFEF4444) else Color(0xFFEF4444).copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp),
                )
            }

            // Clear Buffer
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colorScheme.onSurface.copy(alpha = 0.06f))
                    .border(1.dp, colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .clickable { onClearBuffer() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Limpar buffer",
                    tint = colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // Quick Level Selector Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LevelFilterChip(
                label = "TODOS (${logs.size})",
                selected = selectedLevel == null,
                color = colorScheme.primary,
                onClick = { onSelectLevel(null) },
                modifier = Modifier.weight(1.3f),
            )
            LevelFilterChip(
                label = "V",
                selected = selectedLevel == LogLevel.VERBOSE,
                color = Color(0xFF9E9E9E),
                onClick = { onSelectLevel(LogLevel.VERBOSE) },
                modifier = Modifier.weight(1f),
            )
            LevelFilterChip(
                label = "D",
                selected = selectedLevel == LogLevel.DEBUG,
                color = Color(0xFF0288D1),
                onClick = { onSelectLevel(LogLevel.DEBUG) },
                modifier = Modifier.weight(1f),
            )
            LevelFilterChip(
                label = "I",
                selected = selectedLevel == LogLevel.INFO,
                color = Color(0xFF10B981),
                onClick = { onSelectLevel(LogLevel.INFO) },
                modifier = Modifier.weight(1f),
            )
            LevelFilterChip(
                label = "W",
                selected = selectedLevel == LogLevel.WARN,
                color = Color(0xFFF59E0B),
                onClick = { onSelectLevel(LogLevel.WARN) },
                modifier = Modifier.weight(1f),
            )
            LevelFilterChip(
                label = "E",
                selected = selectedLevel == LogLevel.ERROR,
                color = Color(0xFFEF4444),
                onClick = { onSelectLevel(LogLevel.ERROR) },
                modifier = Modifier.weight(1f),
            )
            LevelFilterChip(
                label = "F",
                selected = selectedLevel == LogLevel.FATAL,
                color = Color(0xFFA855F7),
                onClick = { onSelectLevel(LogLevel.FATAL) },
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 4.dp),
            color = colorScheme.outline.copy(alpha = 0.12f),
            thickness = 0.5.dp,
        )

        // Log Lines List
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isStreamingPaused) "Transmissão pausada." else "Nenhum log correspondente aos filtros atuais.",
                    color = colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
            ) {
                items(
                    items = logs,
                    key = { it.id },
                ) { line ->
                    LogLineItem(
                        line = line,
                        onClick = { onSelectLogLine(line) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelFilterChip(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) color.copy(alpha = 0.22f) else colorScheme.onSurface.copy(alpha = 0.05f))
            .border(1.dp, if (selected) color else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) color else colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun LogLineItem(
    line: LogLine,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val levelColor = when (line.level) {
        LogLevel.VERBOSE -> Color(0xFF9E9E9E)
        LogLevel.DEBUG -> Color(0xFF0288D1)
        LogLevel.INFO -> Color(0xFF10B981)
        LogLevel.WARN -> Color(0xFFF59E0B)
        LogLevel.ERROR -> Color(0xFFEF4444)
        LogLevel.FATAL -> Color(0xFFA855F7)
        LogLevel.SILENT -> Color(0xFF6B7280)
    }

    val timeString = remember(line.timestamp) {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(line.timestamp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 2.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Level Pill
        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(14.dp)
                .clip(CircleShape)
                .background(levelColor.copy(alpha = 0.2f))
                .border(0.5.dp, levelColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = line.level.letter,
                color = levelColor,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }

        Spacer(Modifier.width(6.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = timeString,
                    color = colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )

                if (line.pid.isNotBlank()) {
                    Text(
                        text = line.pid,
                        color = colorScheme.primary.copy(alpha = 0.7f),
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                Text(
                    text = line.tag,
                    color = colorScheme.onSurface.copy(alpha = 0.9f),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = line.content,
                color = if (line.level == LogLevel.ERROR || line.level == LogLevel.FATAL) levelColor else colorScheme.onSurface.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 13.sp,
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// TAB 2: RECORDINGS (SESSIONS & SAVED FILES)
// -------------------------------------------------------------------------------------------------

@Composable
internal fun RecordingsTab(
    session: dev.viagaralauncher.root.log.ActiveSessionInfo,
    savedRecordings: List<SavedLogRecording>,
    targetApp: AppInfo?,
    targetPackageName: String? = null,
    onStartRecording: (pkg: String?, name: String?) -> Unit,
    onPauseRecording: () -> Unit,
    onStopSaveRecording: () -> Unit,
    onDiscardRecording: () -> Unit,
    onDeleteRecording: (File) -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val isRecording = session.state == RecordingState.RECORDING
    val isPaused = session.state == RecordingState.PAUSED
    val isSessionActive = session.state != RecordingState.IDLE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // Active Recording Controller Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(
                    1.dp,
                    if (isSessionActive) Color(0xFFEF4444).copy(alpha = 0.5f) else colorScheme.outline.copy(alpha = 0.15f),
                    RoundedCornerShape(14.dp),
                ),
            color = if (isSessionActive) Color(0xFFEF4444).copy(alpha = 0.06f) else colorScheme.onSurface.copy(alpha = 0.04f),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isRecording) Color(0xFFEF4444) else if (isPaused) Color(0xFFF59E0B) else colorScheme.onSurface.copy(alpha = 0.3f)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isRecording) "GRAVANDO SESSÃO DE LOGS" else if (isPaused) "SESSÃO PAUSADA" else "GRAVAÇÃO PRONTA",
                            color = if (isRecording) Color(0xFFEF4444) else if (isPaused) Color(0xFFF59E0B) else colorScheme.onSurface,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        )
                    }

                    if (isSessionActive) {
                        Text(
                            text = "${session.linesRecorded} linhas",
                            color = colorScheme.primary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = if (isSessionActive) {
                        "Alvo: ${session.targetAppName ?: "Todos os apps do sistema"}\nSalva automaticamente os logs e empacota com informações de hardware do aparelho."
                    } else {
                        "Grave sessões de logs e exporte diretamente para arquivos .TXT ou arquivos .ZIP contendo telemetria completa de hardware e do sistema Android (LogFox style)."
                    },
                    color = colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )

                Spacer(Modifier.height(12.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!isSessionActive) {
                        // Start recording button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFEF4444).copy(alpha = 0.16f))
                                .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .clickable {
                                    onStartRecording(targetApp?.packageName ?: targetPackageName, targetApp?.label ?: targetPackageName)
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FiberManualRecord,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (targetApp != null) "Gravar ${targetApp.label}" else if (targetPackageName != null) "Gravar $targetPackageName" else "Iniciar Gravação Global",
                                    color = Color(0xFFEF4444),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    } else {
                        // Pause / Resume
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colorScheme.onSurface.copy(alpha = 0.08f))
                                .border(1.dp, colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .clickable { onPauseRecording() }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (isPaused) "▶ Retomar" else "⏸ Pausar",
                                color = colorScheme.onSurface,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        // Stop & Save
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF10B981).copy(alpha = 0.16f))
                                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .clickable { onStopSaveRecording() }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "💾 Salvar ZIP",
                                color = Color(0xFF10B981),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        // Discard
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colorScheme.error.copy(alpha = 0.12f))
                                .border(1.dp, colorScheme.error.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .clickable { onDiscardRecording() }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Descartar",
                                color = colorScheme.error,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "GRAVAÇÕES SALVAS (DOWNLOADS/VIAGARALOGS)",
            color = colorScheme.onSurface.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )

        Spacer(Modifier.height(8.dp))

        if (savedRecordings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nenhuma gravação salva ainda.",
                    color = colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 11.5.sp,
                )
            }
        } else {
            savedRecordings.forEach { recording ->
                SavedRecordingCard(
                    recording = recording,
                    onDelete = { onDeleteRecording(recording.file) },
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun SavedRecordingCard(
    recording: SavedLogRecording,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val formattedDate = remember(recording.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(recording.timestamp))
    }
    val sizeText = remember(recording.sizeBytes) {
        if (recording.sizeBytes < 1024) "${recording.sizeBytes} B"
        else if (recording.sizeBytes < 1024 * 1024) "${recording.sizeBytes / 1024} KB"
        else String.format(Locale.US, "%.1f MB", recording.sizeBytes / (1024.0 * 1024.0))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
        color = colorScheme.onSurface.copy(alpha = 0.04f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (recording.isZip) Icons.Filled.FolderZip else Icons.Filled.Description,
                contentDescription = null,
                tint = if (recording.isZip) Color(0xFFF59E0B) else colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recording.name,
                    color = colorScheme.onSurface,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$formattedDate • $sizeText",
                        color = colorScheme.onSurface.copy(alpha = 0.55f),
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    if (recording.isZip) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFF59E0B).copy(alpha = 0.15f))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = "ZIP + Info",
                                color = Color(0xFFF59E0B),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // Share button
            IconButton(
                onClick = {
                    shareFile(context, recording.file)
                },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "Compartilhar",
                    tint = colorScheme.primary,
                    modifier = Modifier.size(15.dp),
                )
            }

            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Excluir",
                    tint = colorScheme.error,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// TAB 3: CRASHES & ANRS
// -------------------------------------------------------------------------------------------------

@Composable
internal fun CrashesTab(
    crashes: List<AppCrashRecord>,
    onClearCrashes: () -> Unit,
    onDeleteCrash: (String) -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "DETECTOR EM TEMPO REAL DE CRASHES & ANRS",
                color = colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )

            if (crashes.isNotEmpty()) {
                Text(
                    text = "Limpar Tudo",
                    color = colorScheme.error,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onClearCrashes() }
                        .padding(4.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (crashes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.BugReport,
                        contentDescription = null,
                        tint = colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Nenhum crash ou ANR registrado.",
                        color = colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "O sistema monitora ativamente falhas Java, sinais nativos JNI e congelamentos de tela.",
                        color = colorScheme.onSurface.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                    )
                }
            }
        } else {
            crashes.forEach { crash ->
                CrashRecordCard(
                    crash = crash,
                    onDelete = { onDeleteCrash(crash.id) },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CrashRecordCard(
    crash: AppCrashRecord,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    var isExpanded by remember { mutableStateOf(false) }

    val formattedDate = remember(crash.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(crash.timestamp))
    }

    val typeColor = when (crash.crashType) {
        CrashType.JAVA -> Color(0xFFEF4444)
        CrashType.JNI -> Color(0xFFA855F7)
        CrashType.ANR -> Color(0xFFF59E0B)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, typeColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
        color = typeColor.copy(alpha = 0.05f),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(typeColor.copy(alpha = 0.2f))
                            .border(0.5.dp, typeColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = crash.crashType.title,
                            color = typeColor,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = crash.appName,
                        color = colorScheme.onSurface,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text = formattedDate,
                    color = colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = crash.packageName,
                color = colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = crash.summary,
                color = colorScheme.onSurface.copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colorScheme.surface.copy(alpha = 0.8f))
                            .border(0.5.dp, colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                    ) {
                        Text(
                            text = crash.stackTrace,
                            color = colorScheme.onSurface.copy(alpha = 0.85f),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 12.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isExpanded) "Ocultar Detalhes" else "Ver Stacktrace",
                    color = colorScheme.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { isExpanded = !isExpanded }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )

                Spacer(Modifier.width(4.dp))

                Text(
                    text = "Copiar",
                    color = colorScheme.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("Crash Stacktrace", crash.stackTrace))
                            Toast.makeText(context, "Stacktrace copiado!", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )

                Spacer(Modifier.width(4.dp))

                Text(
                    text = "Compartilhar",
                    color = colorScheme.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Crash Report: ${crash.appName} (${crash.crashType.title})\n\n${crash.stackTrace}")
                            }
                            context.startActivity(Intent.createChooser(intent, "Compartilhar Relatório"))
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )

                Spacer(Modifier.width(4.dp))

                Text(
                    text = "Excluir",
                    color = colorScheme.error,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onDelete() }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// TAB 4: FILTERS (INCLUDE & EXCLUDE RULES)
// -------------------------------------------------------------------------------------------------

@Composable
internal fun FiltersTab(
    filters: List<UserLogFilter>,
    onAddFilterClick: () -> Unit,
    onToggleFilter: (id: Long, enabled: Boolean) -> Unit,
    onDeleteFilter: (id: Long) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "FILTROS AVANÇADOS (INCLUDE / EXCLUDE)",
                color = colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colorScheme.primary.copy(alpha = 0.15f))
                    .clickable { onAddFilterClick() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Novo Filtro",
                        color = colorScheme.primary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (filters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nenhum filtro personalizado configurado.",
                    color = colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 11.5.sp,
                )
            }
        } else {
            filters.forEach { filter ->
                UserFilterCard(
                    filter = filter,
                    onToggle = { onToggleFilter(filter.id, it) },
                    onDelete = { onDeleteFilter(filter.id) },
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun UserFilterCard(
    filter: UserLogFilter,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isInclude = filter.including
    val badgeColor = if (isInclude) Color(0xFF10B981) else Color(0xFFEF4444)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
        color = colorScheme.onSurface.copy(alpha = 0.04f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColor.copy(alpha = 0.16f))
                            .border(0.5.dp, badgeColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = if (isInclude) "INCLUIR" else "EXCLUIR",
                            color = badgeColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = filter.name,
                        color = colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(3.dp))

                val criteria = buildList {
                    if (!filter.tag.isNullOrBlank()) add("Tag: ${filter.tag}")
                    if (!filter.packageName.isNullOrBlank()) add("App: ${filter.packageName}")
                    if (!filter.content.isNullOrBlank()) add("Texto: \"${filter.content}\"")
                    if (!filter.pid.isNullOrBlank()) add("PID: ${filter.pid}")
                    if (filter.allowedLevels.isNotEmpty()) add("Níveis: ${filter.allowedLevels.joinToString(",") { it.letter }}")
                }

                Text(
                    text = criteria.joinToString(" • ").ifBlank { "Sem critérios específicos" },
                    color = colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Switch(
                checked = filter.enabled,
                onCheckedChange = onToggle,
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Excluir",
                    tint = colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// TAB 5: TERMINAL & SETUP
// -------------------------------------------------------------------------------------------------

@Composable
internal fun TerminalSettingsTab(
    preferredTerminal: TerminalType,
    onTerminalSelected: (TerminalType) -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var isRootAvailable by remember { mutableStateOf(false) }
    var hasReadLogs by remember { mutableStateOf(false) }
    var isGrantingRoot by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isRootAvailable = TerminalEngine.isRootAvailable()
        hasReadLogs = TerminalEngine.hasReadLogsPermission(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "MOTOR DE EXECUÇÃO & PERMISSÕES",
            color = colorScheme.onSurface.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )

        Spacer(Modifier.height(8.dp))

        // Status Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            color = colorScheme.onSurface.copy(alpha = 0.04f),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Permissão android.permission.READ_LOGS:",
                        color = colorScheme.onSurface,
                        fontSize = 11.5.sp,
                    )
                    Text(
                        text = if (hasReadLogs) "CONCEDIDA" else "NÃO CONCEDIDA",
                        color = if (hasReadLogs) Color(0xFF10B981) else Color(0xFFF59E0B),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Acesso Root (Superusuário):",
                        color = colorScheme.onSurface,
                        fontSize = 11.5.sp,
                    )
                    Text(
                        text = if (isRootAvailable) "DISPONÍVEL" else "NÃO DETECTADO",
                        color = if (isRootAvailable) Color(0xFF10B981) else colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Grant via Root button
                if (!hasReadLogs && isRootAvailable) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colorScheme.primary.copy(alpha = 0.16f))
                            .border(1.dp, colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clickable {
                                scope.launch {
                                    isGrantingRoot = true
                                    val ok = TerminalEngine.grantReadLogsViaRoot(context)
                                    isGrantingRoot = false
                                    hasReadLogs = TerminalEngine.hasReadLogsPermission(context)
                                    if (ok) {
                                        Toast.makeText(context, "Permissão READ_LOGS concedida via Root com sucesso!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Falha ao conceder via root.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isGrantingRoot) "Concedendo..." else "Conceder READ_LOGS via Root (1-Toque)",
                            color = colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                }

                // Copy ADB Command
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorScheme.onSurface.copy(alpha = 0.06f))
                        .border(1.dp, colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .clickable {
                            val cmd = TerminalEngine.getAdbCommand(context)
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("ADB Command", cmd))
                            Toast.makeText(context, "Comando ADB copiado para a área de transferência!", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Copiar Comando ADB:",
                                color = colorScheme.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = TerminalEngine.getAdbCommand(context),
                            color = colorScheme.onSurface.copy(alpha = 0.8f),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Device Info Card (Preview of what is embedded in ZIP)
        Text(
            text = "DADOS DE HARDWARE & DISPOSITIVO EMBUTIDOS NO ZIP",
            color = colorScheme.onSurface.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )

        Spacer(Modifier.height(8.dp))

        val deviceInfoText = remember { DeviceInfoProvider.getDeviceInfoText() }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            color = colorScheme.onSurface.copy(alpha = 0.04f),
        ) {
            Text(
                text = deviceInfoText,
                color = colorScheme.onSurface.copy(alpha = 0.75f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 13.sp,
                modifier = Modifier.padding(10.dp),
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// DIALOGS: LINE DETAIL & CREATE FILTER
// -------------------------------------------------------------------------------------------------

@Composable
internal fun LogLineDetailDialog(
    line: LogLine,
    onDismiss: () -> Unit,
    onFilterTag: (String) -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, dynamicBorderColor(), RoundedCornerShape(20.dp)),
            color = dynamicSurfaceColor(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = "Detalhes da Linha de Log",
                    color = colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colorScheme.onSurface.copy(alpha = 0.05f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DetailRow(label = "Tag", value = line.tag)
                    DetailRow(label = "Nível", value = "${line.level.letter} (${line.level.title})")
                    DetailRow(label = "PID / TID", value = "${line.pid} / ${line.tid}")
                    if (line.uid.isNotBlank()) DetailRow(label = "UID", value = line.uid)
                    if (!line.packageName.isNullOrBlank()) DetailRow(label = "Pacote", value = line.packageName)
                    DetailRow(label = "Data/Hora", value = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(line.timestamp)))
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Mensagem:",
                    color = colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorScheme.onSurface.copy(alpha = 0.05f))
                        .padding(8.dp),
                ) {
                    Text(
                        text = line.content,
                        color = colorScheme.onSurface,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TextButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("Log Message", line.content))
                            Toast.makeText(context, "Mensagem copiada!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Copiar Msg", fontSize = 11.sp)
                    }

                    TextButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("Log Line", line.originalContent))
                            Toast.makeText(context, "Linha completa copiada!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Copiar Linha", fontSize = 11.sp)
                    }

                    TextButton(
                        onClick = { onFilterTag(line.tag) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Filtrar Tag", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "$label:",
            color = colorScheme.onSurface.copy(alpha = 0.5f),
            fontSize = 10.sp,
        )
        Text(
            text = value,
            color = colorScheme.onSurface,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun CreateFilterDialog(
    targetAppPackage: String?,
    onDismiss: () -> Unit,
    onSave: (UserLogFilter) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    var name by remember { mutableStateOf("") }
    var including by remember { mutableStateOf(true) }
    var tag by remember { mutableStateOf("") }
    var pkg by remember { mutableStateOf(targetAppPackage ?: "") }
    var content by remember { mutableStateOf("") }
    var pid by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, dynamicBorderColor(), RoundedCornerShape(20.dp)),
            color = dynamicSurfaceColor(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Criar Filtro Personalizado",
                    color = colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do Filtro") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                // Tipo: Incluir vs Excluir
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (including) Color(0xFF10B981).copy(alpha = 0.2f) else colorScheme.onSurface.copy(alpha = 0.05f))
                            .border(1.dp, if (including) Color(0xFF10B981) else colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .clickable { including = true }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Incluir (Whitelist)",
                            color = if (including) Color(0xFF10B981) else colorScheme.onSurface.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!including) Color(0xFFEF4444).copy(alpha = 0.2f) else colorScheme.onSurface.copy(alpha = 0.05f))
                            .border(1.dp, if (!including) Color(0xFFEF4444) else colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .clickable { including = false }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Excluir (Blacklist)",
                            color = if (!including) Color(0xFFEF4444) else colorScheme.onSurface.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    label = { Text("Tag (Opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(6.dp))

                OutlinedTextField(
                    value = pkg,
                    onValueChange = { pkg = it },
                    label = { Text("Pacote / App (Opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(6.dp))

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Palavra-chave no texto (Opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            if (name.isNotBlank()) {
                                onSave(
                                    UserLogFilter(
                                        name = name.trim(),
                                        including = including,
                                        tag = tag.trim().ifBlank { null },
                                        packageName = pkg.trim().ifBlank { null },
                                        content = content.trim().ifBlank { null },
                                        pid = pid.trim().ifBlank { null },
                                    )
                                )
                            }
                        },
                        enabled = name.isNotBlank(),
                    ) {
                        Text("Salvar Filtro")
                    }
                }
            }
        }
    }
}

private fun shareFile(context: Context, file: File) {
    val fileUri = try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (_: Throwable) {
        null
    } ?: return

    val mimeType = if (file.name.endsWith(".zip")) "application/zip" else "text/plain"
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, fileUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Compartilhar Arquivo de Log"))
}
