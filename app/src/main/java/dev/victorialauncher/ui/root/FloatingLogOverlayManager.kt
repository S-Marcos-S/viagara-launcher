// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.root

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.root.AppLogCaptureService
import dev.victorialauncher.root.log.LogFilterEngine
import dev.victorialauncher.root.log.LogLevel
import dev.victorialauncher.root.log.LogLine
import dev.victorialauncher.root.log.RecordingState
import dev.victorialauncher.root.log.TerminalEngine
import dev.victorialauncher.root.overlay.OverlayLifecycleOwner
import dev.victorialauncher.ui.common.AppIcon
import dev.victorialauncher.ui.theme.VictoriaTheme
import dev.victorialauncher.ui.theme.dynamicBorderColor
import dev.victorialauncher.ui.theme.dynamicSurfaceColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * System-wide floating overlay manager for Victoria Launcher's Log Viewer & LogFox engine.
 * Allows the floating bubble (and the expanded floating log window) to remain visible,
 * draggable, and fully interactive above ANY application on the device.
 */
object FloatingLogOverlayManager {

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    var isShowing: Boolean = false
        private set

    val isMinimizedState = mutableStateOf(true)
    val currentAppState = mutableStateOf<AppInfo?>(null)
    val currentPackageState = mutableStateOf<String?>(null)
    val currentTabState = mutableStateOf("logs")

    var bubbleX = 24
    var bubbleY = 120
    var windowX = 0
    var windowY = 0

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun requestOverlayPermission(context: Context, onGranted: () -> Unit) {
        if (canDrawOverlays(context)) {
            onGranted()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            if (TerminalEngine.isRootAvailable()) {
                val ok = TerminalEngine.grantOverlayViaRoot(context)
                if (ok && canDrawOverlays(context)) {
                    onGranted()
                    return@launch
                }
            }

            // Fallback: Open system settings screen
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(
                context,
                "Ative 'Permitir sobreposição a outros apps' para a bolinha flutuar sobre qualquer aplicativo",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun show(
        context: Context,
        app: AppInfo? = null,
        packageName: String? = null,
        initialTab: String? = null,
        isMinimized: Boolean = true
    ) {
        val appContext = context.applicationContext
        currentAppState.value = app
        currentPackageState.value = packageName ?: app?.packageName
        if (initialTab != null) currentTabState.value = initialTab
        isMinimizedState.value = isMinimized

        // Ensure background service is actively monitoring
        AppLogCaptureService.startMonitoring(appContext)

        if (overlayView != null) {
            updateLayout(appContext, isMinimized)
            return
        }

        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm

        val owner = OverlayLifecycleOwner()
        lifecycleOwner = owner

        val density = appContext.resources.displayMetrics.density
        bubbleX = (20 * density).roundToInt()
        bubbleY = (110 * density).roundToInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            if (isMinimized) {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            } else {
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            },
            PixelFormat.TRANSLUCENT
        ).apply {
            if (isMinimized) {
                gravity = Gravity.TOP or Gravity.START
                x = bubbleX
                y = bubbleY
            } else {
                gravity = Gravity.CENTER
                val metrics = appContext.resources.displayMetrics
                width = (metrics.widthPixels * 0.94f).roundToInt()
                height = (metrics.heightPixels * 0.84f).roundToInt()
                x = windowX
                y = windowY
            }
        }
        layoutParams = params

        val view = ComposeView(appContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                VictoriaTheme {
                    FloatingLogOverlayRoot(appContext)
                }
            }
        }
        overlayView = view
        isShowing = true

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
            isShowing = false
            overlayView = null
        }
    }

    fun updateLayout(context: Context, isMinimized: Boolean) {
        val wm = windowManager ?: return
        val view = overlayView ?: return
        val params = layoutParams ?: return

        isMinimizedState.value = isMinimized

        if (isMinimized) {
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            params.gravity = Gravity.TOP or Gravity.START
            params.x = bubbleX
            params.y = bubbleY
        } else {
            val metrics = context.resources.displayMetrics
            params.width = (metrics.widthPixels * 0.94f).roundToInt()
            params.height = (metrics.heightPixels * 0.84f).roundToInt()
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            params.gravity = Gravity.CENTER
            params.x = windowX
            params.y = windowY
        }

        try {
            wm.updateViewLayout(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun moveBubble(dx: Float, dy: Float, maxX: Int, maxY: Int) {
        val wm = windowManager ?: return
        val view = overlayView ?: return
        val params = layoutParams ?: return

        bubbleX = (bubbleX + dx.roundToInt()).coerceIn(0, maxX)
        bubbleY = (bubbleY + dy.roundToInt()).coerceIn(0, maxY)
        params.x = bubbleX
        params.y = bubbleY

        try {
            wm.updateViewLayout(view, params)
        } catch (_: Exception) {}
    }

    fun moveWindow(dx: Float, dy: Float) {
        val wm = windowManager ?: return
        val view = overlayView ?: return
        val params = layoutParams ?: return

        windowX += dx.roundToInt()
        windowY += dy.roundToInt()
        params.x = windowX
        params.y = windowY

        try {
            wm.updateViewLayout(view, params)
        } catch (_: Exception) {}
    }

    fun dismiss() {
        val wm = windowManager
        val view = overlayView
        if (wm != null && view != null) {
            try {
                wm.removeView(view)
            } catch (_: Exception) {}
        }
        lifecycleOwner?.destroy()
        lifecycleOwner = null
        overlayView = null
        windowManager = null
        layoutParams = null
        isShowing = false
    }
}

@Composable
private fun FloatingLogOverlayRoot(context: Context) {
    val colorScheme = MaterialTheme.colorScheme
    val isMinimized = FloatingLogOverlayManager.isMinimizedState.value

    val targetApp = FloatingLogOverlayManager.currentAppState.value
    val targetPackage = FloatingLogOverlayManager.currentPackageState.value
    val initialTab = FloatingLogOverlayManager.currentTabState.value

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

    val sourceApp = targetApp
    val sourcePackageName = targetPackage
    val hasSourceApp = sourceApp != null || sourcePackageName != null
    var isFilteringByApp by remember { mutableStateOf(hasSourceApp) }

    val currentTargetApp = if (isFilteringByApp) sourceApp else null
    val currentTargetPackage = if (isFilteringByApp) (sourceApp?.packageName ?: sourcePackageName) else null

    var searchQuery by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    var autoScrollToBottom by remember { mutableStateOf(true) }

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

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val maxBubbleX = (screenWidthPx - with(density) { 72.dp.toPx() }).roundToInt().coerceAtLeast(0)
    val maxBubbleY = (screenHeightPx - with(density) { 80.dp.toPx() }).roundToInt().coerceAtLeast(0)

    if (isMinimized) {
        // SYSTEM OVERLAY FLOATING BUBBLE (Visible above any app)
        Box(
            modifier = Modifier
                .padding(8.dp)
                .pointerInput(Unit) {
                    var hasDragged = false
                    detectDragGestures(
                        onDragStart = { hasDragged = false },
                        onDragEnd = {
                            if (!hasDragged) {
                                FloatingLogOverlayManager.updateLayout(context, isMinimized = false)
                            }
                        },
                        onDragCancel = { hasDragged = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (kotlin.math.abs(dragAmount.x) > 1.5f || kotlin.math.abs(dragAmount.y) > 1.5f) {
                                hasDragged = true
                            }
                            FloatingLogOverlayManager.moveBubble(dragAmount.x, dragAmount.y, maxBubbleX, maxBubbleY)
                        }
                    )
                },
        ) {
            Surface(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .clickable {
                        FloatingLogOverlayManager.updateLayout(context, isMinimized = false)
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

                    // Live recording dot indicator
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

            // Mini close button on the bubble
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surfaceVariant)
                    .border(1.dp, colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                    .clickable {
                        FloatingLogOverlayManager.dismiss()
                    },
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
    } else {
        // SYSTEM OVERLAY EXPANDED FLOATING WINDOW (Visible above any app)
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .border(1.2.dp, dynamicBorderColor(), RoundedCornerShape(24.dp)),
            color = dynamicSurfaceColor().copy(alpha = 0.96f),
            tonalElevation = 8.dp,
            shadowElevation = 18.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
            ) {
                // Drag handle pill at the top (draggable!)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                FloatingLogOverlayManager.moveWindow(dragAmount.x, dragAmount.y)
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

                // Header bar (also draggable!)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                FloatingLogOverlayManager.moveWindow(dragAmount.x, dragAmount.y)
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
                                    imageVector = Icons.Filled.Terminal,
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
                                FloatingLogOverlayManager.updateLayout(context, isMinimized = true)
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Remove,
                                contentDescription = "Minimizar para bolinha",
                                tint = colorScheme.onSurface.copy(alpha = 0.75f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(2.dp))
                        IconButton(
                            onClick = {
                                FloatingLogOverlayManager.dismiss()
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Fechar janela",
                                tint = colorScheme.onSurface.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Tab Selector Navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LogViewerTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedTab = tab },
                            color = if (isSelected) colorScheme.primary else Color.Transparent,
                            border = if (!isSelected) BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.15f)) else null,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.size(13.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = tab.title,
                                    color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface.copy(alpha = 0.7f),
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                )
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
                                onDeleteCrash = { crashId -> crashManager.deleteCrash(crashId) },
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

    // Line detail dialog
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

    // Create filter dialog
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
